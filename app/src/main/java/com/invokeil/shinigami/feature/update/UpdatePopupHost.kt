package com.invokeil.shinigami.feature.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invokeil.shinigami.core.update.UpdateInstaller

/**
 * Update-available popup (bottom sheet, non-blocking). Shown when a newer
 * GitHub Release exists; user can update in place (PackageInstaller flow),
 * be reminded later, or skip this version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatePopupHost(vm: UpdateViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Kick off the check shortly after first composition (app open).
    LaunchedEffect(Unit) { vm.checkOnLaunch() }

    if (state.needUnknownAppsPermission) {
        AlertDialog(
            onDismissRequest = { vm.unknownAppsPermissionHandled() },
            title = { Text("Allow installing updates") },
            text = {
                Text(
                    "Android needs your one-time permission so Shinigami can install its own updates. " +
                        "Enable \"Install unknown apps\" for Shinigami, then tap Update again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.unknownAppsPermissionHandled()
                    // the installer helper builds the exact settings intent
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { vm.unknownAppsPermissionHandled() }) { Text("Not now") }
            },
        )
    }

    if (state.sheetVisible && state.release != null) {
        ModalBottomSheet(onDismissRequest = { vm.dismissSheet() }) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp).fillMaxWidth()) {
                Text("Update available", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${state.release?.tagName} is available.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Changelog",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    state.release?.changelog?.take(600) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                when (val s = state.installState) {
                    is UpdateInstaller.InstallState.Downloading -> {
                        Text("Downloading… ${s.progressPct}%")
                        LinearProgressIndicator(
                            progress = { s.progressPct / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    UpdateInstaller.InstallState.Verifying -> {
                        Text("Verifying SHA-256…")
                    }
                    is UpdateInstaller.InstallState.Failed -> {
                        Text(s.reason, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Unit
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { vm.skipVersion() }) { Text("Skip this version") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { vm.remindLater() }) { Text("Remind me") }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { vm.startInstall() },
                        enabled = state.installState !is UpdateInstaller.InstallState.Downloading,
                    ) {
                        if (state.checking) {
                            CircularProgressIndicator(Modifier.height(18.dp).width(18.dp))
                        } else {
                            Text("Update now")
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}
