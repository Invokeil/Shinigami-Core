package com.invokeil.shinigami.feature.settings

import android.content.Intent
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
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FrontHand
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invokeil.shinigami.BuildConfig
import com.invokeil.shinigami.R
import com.invokeil.shinigami.ThemeViewModel
import com.invokeil.shinigami.core.data.ConfirmationPolicy
import com.invokeil.shinigami.core.data.HistoryRetention
import com.invokeil.shinigami.core.data.PrefsRepository
import com.invokeil.shinigami.core.data.ThemeMode
import com.invokeil.shinigami.core.permissions.PermissionCatalog
import com.invokeil.shinigami.core.ui.components.SectionHeader
import com.invokeil.shinigami.core.ui.components.ShiniCard
import com.invokeil.shinigami.core.ui.components.SoftDivider
import com.invokeil.shinigami.core.ui.components.StatusChip
import com.invokeil.shinigami.core.voice.TtsManager
import com.invokeil.shinigami.core.voice.VoicePrint
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    themeViewModel: ThemeViewModel,
    onOpenPermissions: () -> Unit,
    onOpenAudit: () -> Unit,
    onOpenCommands: () -> Unit,
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tts = remember { TtsManager(context.applicationContext) }
    val isDefaultAssistant = remember { mutableStateOf(PermissionCatalog.isDefaultAssistant(context)) }

    val themeState by themeViewModel.themeState.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()
    val ttsPitch by viewModel.ttsPitch.collectAsState()
    val wakeEnabled by viewModel.wakeEnabled.collectAsState()
    val wakePhrase by viewModel.wakePhrase.collectAsState()
    val voiceprint by viewModel.wakeVoiceprint.collectAsState()
    val incognito by viewModel.incognito.collectAsState()
    val memory by viewModel.memoryEnabled.collectAsState()
    val historyRetention by viewModel.historyRetention.collectAsState()
    val confirmationPolicy by viewModel.confirmationPolicy.collectAsState()
    val biometric by viewModel.biometricHighRisk.collectAsState()
    val showLicenses = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        // ---- Appearance ----
        SectionHeader(stringResource(R.string.settings_appearance), Modifier)
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = themeState.mode == ThemeMode.SYSTEM,
                onClick = { themeViewModel.setTheme(ThemeMode.SYSTEM) },
                label = { Text(stringResource(R.string.settings_theme_system)) },
            )
            FilterChip(
                selected = themeState.mode == ThemeMode.DARK,
                onClick = { themeViewModel.setTheme(ThemeMode.DARK) },
                label = { Text(stringResource(R.string.settings_theme_dark)) },
            )
            FilterChip(
                selected = themeState.mode == ThemeMode.LIGHT,
                onClick = { themeViewModel.setTheme(ThemeMode.LIGHT) },
                label = { Text(stringResource(R.string.settings_theme_light)) },
            )
        }
        SettingSwitch(
            icon = Icons.Rounded.Palette,
            title = stringResource(R.string.settings_dynamic_color),
            subtitle = stringResource(R.string.settings_dynamic_color_desc),
            checked = themeState.dynamicColor,
            onChange = { themeViewModel.setDynamicColor(it) },
        )
        SettingSwitch(
            icon = Icons.Rounded.Widgets,
            title = stringResource(R.string.settings_animations),
            subtitle = null,
            checked = themeState.reduceAnimations,
            onChange = { themeViewModel.setReduceAnimations(it) },
        )

        // ---- Voice ----
        SectionHeader(stringResource(R.string.settings_voice))
        SettingSwitch(
            icon = Icons.Rounded.Mic,
            title = stringResource(R.string.settings_tts_enabled),
            subtitle = null,
            checked = ttsEnabled,
            onChange = { viewModel.setTtsEnabled(it) },
        )
        Text(
            stringResource(R.string.settings_tts_speed) + ": ${"%.1f".format(ttsSpeed)}×",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Slider(
            value = ttsSpeed,
            onValueChange = { viewModel.setTtsSpeed(it) },
            valueRange = 0.5f..2f,
        )
        Text(
            stringResource(R.string.settings_tts_pitch) + ": ${"%.1f".format(ttsPitch)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Slider(
            value = ttsPitch,
            onValueChange = { viewModel.setTtsPitch(it) },
            valueRange = 0.5f..2f,
        )
        TextButton(onClick = {
            scope.launch {
                tts.speed = ttsSpeed
                tts.pitch = ttsPitch
                tts.test()
            }
        }) {
            Text(stringResource(R.string.settings_tts_test))
        }

        // ---- Wake word ----
        SectionHeader(stringResource(R.string.settings_wake_word))
        ShiniCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_wake_word),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = wakeEnabled, onCheckedChange = { viewModel.setWakeEnabled(it) })
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_wake_word_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = wakePhrase,
                    onValueChange = { viewModel.setWakePhrase(it) },
                    label = { Text(stringResource(R.string.settings_wake_phrase)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Same-voice check (experimental)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = voiceprint, onCheckedChange = { viewModel.setWakeVoiceprint(it) })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_wake_battery_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Assistant role ----
        SectionHeader(stringResource(R.string.settings_default_assistant))
        ShiniCard(
            Modifier.fillMaxWidth(),
            onClick = {
                context.startActivity(PermissionCatalog.assistantPickerIntent())
                isDefaultAssistant.value = PermissionCatalog.isDefaultAssistant(context)
            },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isDefaultAssistant.value) {
                            stringResource(R.string.settings_default_assistant_enabled)
                        } else {
                            stringResource(R.string.settings_default_assistant_disabled)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                StatusChip(
                    if (isDefaultAssistant.value) "ON" else "OFF",
                    if (isDefaultAssistant.value) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Privacy ----
        SectionHeader(stringResource(R.string.settings_privacy))
        SettingSwitch(
            icon = Icons.Rounded.FrontHand,
            title = stringResource(R.string.settings_incognito),
            subtitle = stringResource(R.string.settings_incognito_desc),
            checked = incognito,
            onChange = { viewModel.setIncognito(it) },
        )
        SettingSwitch(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.settings_memory_enabled),
            subtitle = stringResource(R.string.settings_memory_desc),
            checked = memory,
            onChange = { viewModel.setMemory(it) },
        )
        SettingSwitch(
            icon = Icons.Rounded.Fingerprint,
            title = stringResource(R.string.settings_biometric),
            subtitle = stringResource(R.string.settings_biometric_desc),
            checked = biometric,
            onChange = { viewModel.setBiometric(it) },
        )

        // ---- Confirmations ----
        SectionHeader(stringResource(R.string.settings_confirmation))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = confirmationPolicy == ConfirmationPolicy.ALWAYS,
                onClick = { viewModel.setConfirmationPolicy(ConfirmationPolicy.ALWAYS) },
                label = { Text(stringResource(R.string.settings_confirm_always)) },
            )
            FilterChip(
                selected = confirmationPolicy == ConfirmationPolicy.SENSITIVE,
                onClick = { viewModel.setConfirmationPolicy(ConfirmationPolicy.SENSITIVE) },
                label = { Text(stringResource(R.string.settings_confirm_sensitive)) },
            )
            FilterChip(
                selected = confirmationPolicy == ConfirmationPolicy.MINIMAL,
                onClick = { viewModel.setConfirmationPolicy(ConfirmationPolicy.MINIMAL) },
                label = { Text(stringResource(R.string.settings_confirm_minimal)) },
            )
        }

        // ---- History retention ----
        SectionHeader(stringResource(R.string.settings_history_retention))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(
                HistoryRetention.FOREVER to stringResource(R.string.settings_history_forever),
                HistoryRetention.DAYS_30 to stringResource(R.string.settings_history_30d),
                HistoryRetention.DAYS_7 to stringResource(R.string.settings_history_7d),
            ).forEach { (value, label) ->
                FilterChip(
                    selected = historyRetention == value,
                    onClick = { viewModel.setHistoryRetention(value) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(
                HistoryRetention.DAYS_1 to stringResource(R.string.settings_history_1d),
                HistoryRetention.OFF to stringResource(R.string.settings_history_off),
            ).forEach { (value, label) ->
                FilterChip(
                    selected = historyRetention == value,
                    onClick = { viewModel.setHistoryRetention(value) },
                    label = { Text(label) },
                )
            }
        }

        // ---- Navigation ----
        SectionHeader(stringResource(R.string.settings_data))
        LinkRow(stringResource(R.string.permissions_title), Icons.Rounded.Tune, onOpenPermissions)
        LinkRow(stringResource(R.string.audit_title), Icons.Rounded.Receipt, onOpenAudit)
        LinkRow(stringResource(R.string.commands_title), Icons.Rounded.Widgets, onOpenCommands)

        // ---- About ----
        SectionHeader(stringResource(R.string.settings_about))
        Text(
            stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        TextButton(onClick = { showLicenses.value = true }) {
            Text(stringResource(R.string.settings_licenses))
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showLicenses.value) {
        AlertDialog(
            onDismissRequest = { showLicenses.value = false },
            confirmButton = {
                TextButton(onClick = { showLicenses.value = false }) { Text(stringResource(R.string.ok)) }
            },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Text(
                    "• Nunito — SIL Open Font License 1.1 (bundled)\n" +
                        "• Jetpack Compose, Material 3, Room, Hilt, OkHttp, kotlinx.serialization — Apache License 2.0\n" +
                        "• Shinigami Core itself — Apache License 2.0",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ShiniCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LinkRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    ShiniCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
    Spacer(Modifier.height(8.dp))
}
