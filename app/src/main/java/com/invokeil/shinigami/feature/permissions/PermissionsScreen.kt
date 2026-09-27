package com.invokeil.shinigami.feature.permissions

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.R
import com.invokeil.shinigami.core.data.PermissionMode
import com.invokeil.shinigami.core.data.PrefsRepository
import kotlinx.coroutines.flow.firstOrNull
import com.invokeil.shinigami.core.permissions.PermissionCard
import com.invokeil.shinigami.core.permissions.PermissionCatalog
import com.invokeil.shinigami.core.ui.components.SectionHeader
import com.invokeil.shinigami.core.ui.components.ShiniButton
import com.invokeil.shinigami.core.ui.components.ShiniCard
import com.invokeil.shinigami.core.ui.components.StatusChip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val prefs: PrefsRepository,
) : ViewModel() {

    val mode = prefs.permissionMode

    fun setMode(mode: PermissionMode, onNeedWarning: () -> Unit) {
        viewModelScope.launch {
            if (mode == PermissionMode.FULL) {
                val ack = prefs.fullControlAck.firstOrNull() ?: false
                if (!ack) {
                    onNeedWarning()
                    return@launch
                }
            }
            prefs.setPermissionMode(mode)
        }
    }

    fun acknowledgeFullControl() {
        viewModelScope.launch {
            prefs.setFullControlAck(true)
            prefs.setPermissionMode(PermissionMode.FULL)
        }
    }
}

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val mode by viewModel.mode.collectAsState(initial = PermissionMode.STANDARD)
    var showFullWarning by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val cards = remember(refresh) { PermissionCatalog.cards(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.ob_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            stringResource(R.string.permissions_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        SectionHeader(stringResource(R.string.perm_mode_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == PermissionMode.STANDARD,
                onClick = { viewModel.setMode(PermissionMode.STANDARD) {} },
                label = { Text(stringResource(R.string.perm_mode_standard)) },
            )
            FilterChip(
                selected = mode == PermissionMode.ENHANCED,
                onClick = { viewModel.setMode(PermissionMode.ENHANCED) {} },
                label = { Text(stringResource(R.string.perm_mode_enhanced)) },
            )
            FilterChip(
                selected = mode == PermissionMode.FULL,
                onClick = {
                    viewModel.setMode(PermissionMode.FULL) {
                        showFullWarning = true
                    }
                },
                label = { Text(stringResource(R.string.perm_mode_full)) },
            )
        }
        Text(
            text = when (mode) {
                PermissionMode.STANDARD -> stringResource(R.string.perm_mode_standard_desc)
                PermissionMode.ENHANCED -> stringResource(R.string.perm_mode_enhanced_desc)
                PermissionMode.FULL -> stringResource(R.string.perm_mode_full_desc)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 2.dp),
        )

        SectionHeader(stringResource(R.string.permissions_title))
        cards.forEach { card ->
            PermissionCardItem(card) {
                refresh++
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showFullWarning) {
        FullControlWarningDialog(
            onEnable = {
                viewModel.acknowledgeFullControl()
                showFullWarning = false
            },
            onDismiss = {
                showFullWarning = false
            },
        )
    }
}

@Composable
private fun PermissionCardItem(card: PermissionCard, onStateChanged: () -> Unit) {
    val context = LocalContext.current
    ShiniCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val (chipText, tint) = when (card.state) {
                    PermissionCard.State.ENABLED -> stringResource(R.string.perm_state_enabled) to MaterialTheme.colorScheme.tertiary
                    PermissionCard.State.DISABLED -> stringResource(R.string.perm_state_disabled) to MaterialTheme.colorScheme.error
                    PermissionCard.State.UNAVAILABLE -> stringResource(R.string.perm_state_unavailable) to MaterialTheme.colorScheme.onSurfaceVariant
                }
                StatusChip(chipText, tint)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                card.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (card.action != PermissionCard.Action.NONE && card.state != PermissionCard.State.ENABLED) {
                Spacer(Modifier.height(10.dp))
                when (card.action) {
                    PermissionCard.Action.RUNTIME -> {
                        val perms = PermissionCatalog.runtimeCards[card.id]
                        if (perms != null) {
                            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                                androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
                            ) { onStateChanged() }
                            ShiniButton(
                                stringResource(R.string.perm_action_grant),
                                onClick = { launcher.launch(perms) },
                            )
                        }
                    }

                    PermissionCard.Action.SETTINGS_SCREEN -> {
                        ShiniButton(
                            stringResource(R.string.perm_action_settings),
                            onClick = {
                                PermissionCatalog.settingsIntent(context, card.id)?.let {
                                    context.startActivity(it)
                                    onStateChanged()
                                }
                            },
                        )
                    }

                    PermissionCard.Action.ASSISTANT_PICKER -> {
                        ShiniButton(
                            stringResource(R.string.settings_set_assistant),
                            onClick = {
                                context.startActivity(PermissionCatalog.assistantPickerIntent())
                                onStateChanged()
                            },
                        )
                    }

                    PermissionCard.Action.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun FullControlWarningDialog(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fullcontrol_warning_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.fullcontrol_warning_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.fullcontrol_checkbox), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = checked, onClick = onEnable) {
                Text(stringResource(R.string.fullcontrol_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
