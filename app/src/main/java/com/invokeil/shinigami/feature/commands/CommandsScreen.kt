package com.invokeil.shinigami.feature.commands

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.R
import com.invokeil.shinigami.core.actions.ToolRegistry
import com.invokeil.shinigami.core.data.db.CustomCommandDao
import com.invokeil.shinigami.core.data.db.CustomCommandEntity
import com.invokeil.shinigami.core.ui.components.EmptyState
import com.invokeil.shinigami.core.ui.components.ShiniButton
import com.invokeil.shinigami.core.ui.components.ShiniCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

@HiltViewModel
class CommandsViewModel @Inject constructor(
    private val dao: CustomCommandDao,
    val registry: ToolRegistry,
) : ViewModel() {

    val commands: StateFlow<List<CustomCommandEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(phrase: String, toolId: String) {
        viewModelScope.launch {
            dao.insert(CustomCommandEntity(phrase = phrase.lowercase().trim(), toolId = toolId))
        }
    }

    fun delete(command: CustomCommandEntity) {
        viewModelScope.launch { dao.delete(command) }
    }
}

@Composable
fun CommandsScreen(
    onBack: () -> Unit,
    viewModel: CommandsViewModel = hiltViewModel(),
) {
    val commands by viewModel.commands.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
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
                stringResource(R.string.commands_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showAdd = true }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.commands_add),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (commands.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Bolt,
                    title = stringResource(R.string.commands_empty_title),
                    body = stringResource(R.string.commands_empty_body),
                    ctaText = stringResource(R.string.commands_add),
                    onCta = { showAdd = true },
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(commands, key = { it.id }) { cmd ->
                    ShiniCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("“${cmd.phrase}”", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    cmd.toolId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.delete(cmd) }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showAdd) {
        AddCommandDialog(
            tools = viewModel.registry.all(),
            onAdd = { phrase, toolId ->
                viewModel.add(phrase, toolId)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun AddCommandDialog(
    tools: List<com.invokeil.shinigami.core.actions.ToolDefinition>,
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var phrase by remember { mutableStateOf("") }
    var selectedTool by remember { mutableStateOf(tools.firstOrNull()?.id ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.commands_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text(stringResource(R.string.commands_phrase)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.commands_tool), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(tools, key = { it.id }) { tool ->
                        FilterChip(
                            selected = selectedTool == tool.id,
                            onClick = { selectedTool = tool.id },
                            label = { Text("${tool.displayName} (${tool.id})") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = phrase.isNotBlank() && selectedTool.isNotBlank(),
                onClick = { onAdd(phrase, selectedTool) },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
