package com.invokeil.shinigami.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.invokeil.shinigami.R
import com.invokeil.shinigami.core.ui.components.OrbState
import com.invokeil.shinigami.core.ui.components.ShiniButton
import com.invokeil.shinigami.core.ui.components.ShiniCard
import com.invokeil.shinigami.core.ui.components.ShiniOrb
import com.invokeil.shinigami.core.ui.components.StatusChip
import com.invokeil.shinigami.core.ui.components.ToolExecutionCard
import java.util.Calendar

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    openProviders: () -> Unit,
    reduceAnimations: Boolean,
    autoStartVoice: Boolean,
    onAutoStartConsumed: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AssistantContent(
            viewModel = viewModel,
            openProviders = openProviders,
            reduceAnimations = reduceAnimations,
            autoStartVoice = autoStartVoice,
            onAutoStartConsumed = onAutoStartConsumed,
        )
        val pendingConfirm by viewModel.pendingConfirmation.collectAsState()
        pendingConfirm?.let { pending ->
            ConfirmationSheet(
                summary = pending.summary,
                onConfirm = { viewModel.answerConfirmation(true) },
                onCancel = { viewModel.answerConfirmation(false) },
            )
        }
    }
}

@Composable
private fun AssistantContent(
    viewModel: AssistantViewModel,
    openProviders: () -> Unit,
    reduceAnimations: Boolean,
    autoStartVoice: Boolean,
    onAutoStartConsumed: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val pendingConfirm by viewModel.pendingConfirmation.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(autoStartVoice) {
        if (autoStartVoice) {
            viewModel.onMicPressed()
            onAutoStartConsumed()
        }
    }

    // Follow the tail of the conversation
    LaunchedEffect(ui.rows.size, ui.rows.lastOrNull()) {
        if (ui.rows.isNotEmpty()) {
            listState.animateScrollToItem(ui.rows.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                ),
            )
            .statusBarsPadding()
            .imePadding(),
    ) {
        // ---------- Header ----------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "SHINIGAMI · by Invokeil",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.hasProvider) {
                StatusChip(text = ui.providerLabel ?: "", tint = MaterialTheme.colorScheme.primary)
            } else {
                StatusChip(
                    text = stringResource(R.string.home_provider_offline),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.clickableChip(openProviders),
                )
            }
            IconButton(onClick = { viewModel.newChat() }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.home_new_chat_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------- Conversation ----------
        Box(Modifier.weight(1f)) {
            if (ui.rows.isEmpty() && ui.partialSpeech == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp),
                ) {
                    Text(
                        stringResource(R.string.home_empty_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.home_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(26.dp))
                    SuggestionChips(onSuggestion = { viewModel.sendText(it) })
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    items(ui.rows, key = { row ->
                        when (row) {
                            is ChatRow.Message -> "msg${row.id}"
                            is ChatRow.ToolCard -> "tool${row.uid}"
                            ChatRow.Thinking -> "thinking"
                        }
                    }) { row ->
                        when (row) {
                            is ChatRow.Message -> ChatBubble(row)
                            is ChatRow.ToolCard -> ToolExecutionCard(
                                label = row.label,
                                state = when (row.state) {
                                    ToolCardUiState.RUNNING -> com.invokeil.shinigami.core.ui.components.ToolCardState.RUNNING
                                    ToolCardUiState.DONE -> com.invokeil.shinigami.core.ui.components.ToolCardState.DONE
                                    ToolCardUiState.FAILED -> com.invokeil.shinigami.core.ui.components.ToolCardState.FAILED
                                },
                            )
                            ChatRow.Thinking -> ThinkingBubble()
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            // Live partial speech overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = ui.partialSpeech != null,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            ) {
                ShiniCard(Modifier.padding(horizontal = 30.dp)) {
                    Text(
                        ui.partialSpeech.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }

        // ---------- Status line ----------
        androidx.compose.animation.AnimatedVisibility(visible = ui.statusText != null) {
            Text(
                ui.statusText.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            )
        }

        // ---------- Error banner ----------
        androidx.compose.animation.AnimatedVisibility(visible = ui.error != null) {
            ShiniCard(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Text(
                        ui.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.close),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { viewModel.clearError() }
                            .padding(6.dp),
                    )
                }
            }
        }

        // ---------- Orb ----------
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            ShiniOrb(
                state = when (ui.orbState) {
                    OrbStateUi.IDLE -> OrbState.IDLE
                    OrbStateUi.LISTENING -> OrbState.LISTENING
                    OrbStateUi.THINKING -> OrbState.THINKING
                    OrbStateUi.SPEAKING -> OrbState.SPEAKING
                    OrbStateUi.EXECUTING -> OrbState.EXECUTING
                    OrbStateUi.ERROR -> OrbState.ERROR
                },
                reduceAnimations = reduceAnimations,
                onClick = {
                    if (ui.orbState == OrbStateUi.LISTENING) viewModel.stopVoice()
                    else if (ui.orbState == OrbStateUi.SPEAKING) viewModel.stopTts()
                    else viewModel.onMicPressed()
                },
                contentDescriptionText = stringResource(R.string.home_mic_cd),
            )
        }

        // ---------- Input bar ----------
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        stringResource(R.string.home_placeholder_ask),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                ),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.sendText(input)
                    input = ""
                    keyboard?.hide()
                }),
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            val isBusy = !ui.canSend
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .shadow(
                        10.dp,
                        CircleShape,
                        spotColor = MaterialTheme.colorScheme.primary,
                    )
                    .clip(CircleShape)
                    .background(
                        if (isBusy) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                    .clickableSend {
                        if (isBusy) {
                            viewModel.interruptAll()
                        } else {
                            viewModel.sendText(input)
                            input = ""
                            keyboard?.hide()
                        }
                    },
            ) {
                Icon(
                    if (isBusy) Icons.Rounded.Stop else Icons.Rounded.Send,
                    contentDescription = if (isBusy) stringResource(R.string.home_stop_cd)
                    else stringResource(R.string.home_send_cd),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SuggestionChips(onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        Triple(Icons.Rounded.PlayArrow, "Play music", "play music"),
        Triple(Icons.Rounded.Alarm, "Alarm for 7 AM", "set an alarm for 7 AM"),
        Triple(Icons.Rounded.FlashlightOn, "Flashlight on", "flashlight on"),
        Triple(Icons.Rounded.Battery5Bar, "Battery level", "what's my battery"),
        Triple(Icons.Rounded.Search, "Search the web", "search for today's news"),
        Triple(Icons.Rounded.Bolt, "Open YouTube", "open youtube"),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        suggestions.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { (icon, label, phrase) ->
                    SuggestionChipInternal(icon, label) { onSuggestion(phrase) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SuggestionChipInternal(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    ShiniCard(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ChatBubble(row: ChatRow.Message) {
    val isUser = row.role == com.invokeil.shinigami.core.data.db.MessageRole.USER
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .widthIn(max = 310.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 20.dp,
                    ),
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                ),
        ) {
            Text(
                row.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.invokeil.shinigami.core.ui.components.PulsingDot(
            color = MaterialTheme.colorScheme.primary,
            size = 9.dp,
        )
        Spacer(Modifier.width(6.dp))
        com.invokeil.shinigami.core.ui.components.PulsingDot(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            size = 9.dp,
        )
        Spacer(Modifier.width(6.dp))
        com.invokeil.shinigami.core.ui.components.PulsingDot(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            size = 9.dp,
        )
    }
}

@Composable
private fun ConfirmationSheet(
    summary: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onCancel),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
        ) {
            Text(
                stringResource(R.string.tool_confirm_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ShiniButton(
                    stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                ShiniButton(
                    stringResource(R.string.action_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..22 -> "Good evening"
        else -> "Up late?"
    }
}

// Small composable-safe click helpers (avoid importing clickable twice)
private fun Modifier.clickableChip(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(onClick = onClick),
)

private fun Modifier.clickableSend(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(onClick = onClick),
)
