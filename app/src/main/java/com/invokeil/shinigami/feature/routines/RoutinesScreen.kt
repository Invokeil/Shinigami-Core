package com.invokeil.shinigami.feature.routines

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.core.actions.ToolRegistry
import com.invokeil.shinigami.core.data.db.RoutineDao
import com.invokeil.shinigami.core.data.db.RoutineEntity
import com.invokeil.shinigami.core.data.db.RoutineStepEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    private val runner: RoutineRunner,
    val registry: ToolRegistry,
) : ViewModel() {

    val routines = routineDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val progress = runner.progress
    val running = runner.running

    fun toggle(routine: RoutineEntity, enabled: Boolean) = viewModelScope.launch {
        routineDao.update(routine.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    fun delete(routine: RoutineEntity) = viewModelScope.launch {
        routineDao.delete(routine.id)
    }

    fun save(routine: RoutineEntity, steps: List<RoutineStepEntity>) = viewModelScope.launch {
        val id = routineDao.insert(routine)
        routineDao.deleteSteps(id)
        steps.forEachIndexed { index, step ->
            routineDao.insertStep(step.copy(routineId = id, position = index))
        }
    }

    fun run(routine: RoutineEntity) = viewModelScope.launch { runner.run(routine.id) }

    fun stop() = runner.stop()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    vm: RoutinesViewModel = hiltViewModel(),
) {
    val routines by vm.routines.collectAsState()
    val running by vm.running.collectAsState()
    val progress by vm.progress.collectAsState()
    var editing by remember { mutableStateOf<RoutineEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routines") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            OutlinedButton(onClick = {
                editing = RoutineEntity(name = "")
                showEditor = true
            }) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("New routine")
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (running && progress != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Running “${progress?.routineName}” — step ${progress?.step} of ${progress?.total}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        progress?.error?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { vm.stop() }) { Text("STOP") }
                        }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(routines, key = { it.id }) { routine ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        onClick = {
                            editing = routine
                            showEditor = true
                        },
                    ) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(routine.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when (routine.triggerType) {
                                        "TRIGGER_PHRASE" -> "Phrase: “${routine.triggerPhrase}”"
                                        "TRIGGER_TIME" -> "Time: %02d:%02d".format(
                                            routine.triggerHour ?: 0,
                                            routine.triggerMinute ?: 0,
                                        )
                                        else -> "Manual"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { vm.run(routine) },
                                enabled = !running,
                            ) { Icon(Icons.Filled.PlayArrow, "Run") }
                            Switch(
                                checked = routine.enabled,
                                onCheckedChange = { vm.toggle(routine, it) },
                            )
                            IconButton(onClick = { vm.delete(routine) }) {
                                Icon(Icons.Filled.Delete, "Delete")
                            }
                        }
                    }
                }
                if (routines.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No routines yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "A routine chains steps — each step is still checked by the same permission and safety rules.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor && editing != null) {
        RoutineEditorDialog(
            initial = editing!!,
            registry = vm.registry,
            onDismiss = { showEditor = false },
            onSave = { routine, steps ->
                vm.save(routine, steps)
                showEditor = false
            },
        )
    }
}

@Composable
private fun RoutineEditorDialog(
    initial: RoutineEntity,
    registry: ToolRegistry,
    onDismiss: () -> Unit,
    onSave: (RoutineEntity, List<RoutineStepEntity>) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var triggerType by remember { mutableStateOf(initial.triggerType) }
    var phrase by remember { mutableStateOf(initial.triggerPhrase ?: "") }
    var hour by remember { mutableStateOf((initial.triggerHour ?: 7).toString()) }
    var minute by remember { mutableStateOf((initial.triggerMinute ?: 0).toString()) }

    var steps by remember {
        mutableStateOf(
            List(0) { RoutineStepEntity(routineId = 0, position = 0, toolId = "open_app") },
        )
    }

    val selectable = registry.all()
        .filter { it.risk != com.invokeil.shinigami.core.data.db.RiskLevel.CRITICAL }
        .filter { it.id != "run_routine" }
    var newToolId by remember { mutableStateOf(selectable.firstOrNull()?.id ?: "open_app") }
    var newArgs by remember { mutableStateOf("{}") }
    var newLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 7
                    val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    onSave(
                        initial.copy(
                            name = name.ifBlank { "Untitled routine" },
                            triggerType = triggerType,
                            triggerPhrase = phrase.ifBlank { null },
                            triggerHour = if (triggerType == "TRIGGER_TIME") h else null,
                            triggerMinute = if (triggerType == "TRIGGER_TIME") m else null,
                            updatedAt = System.currentTimeMillis(),
                        ),
                        steps,
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Routine") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    FilterChip(
                        selected = triggerType == "TRIGGER_MANUAL",
                        onClick = { triggerType = "TRIGGER_MANUAL" },
                        label = { Text("Manual") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = triggerType == "TRIGGER_PHRASE",
                        onClick = { triggerType = "TRIGGER_PHRASE" },
                        label = { Text("Phrase") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = triggerType == "TRIGGER_TIME",
                        onClick = { triggerType = "TRIGGER_TIME" },
                        label = { Text("Time") },
                    )
                }
                when (triggerType) {
                    "TRIGGER_PHRASE" -> OutlinedTextField(
                        value = phrase,
                        onValueChange = { phrase = it },
                        label = { Text("Say: “Hey Shini, <phrase>”") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "TRIGGER_TIME" -> Row {
                        OutlinedTextField(
                            value = hour,
                            onValueChange = { hour = it.take(2) },
                            label = { Text("Hour (0-23)") },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = minute,
                            onValueChange = { minute = it.take(2) },
                            label = { Text("Minute") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Steps", fontWeight = FontWeight.SemiBold)
                steps.forEachIndexed { i, step ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}. ${step.toolId}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { steps = steps.filterIndexed { j, _ -> j != i } }) {
                            Icon(Icons.Filled.Delete, "Remove step")
                        }
                    }
                }
                if (steps.isEmpty()) {
                    Text(
                        "No steps yet — add the actions below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Add step", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            newToolId,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(newLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    selectable.take(12).forEach { tool ->
                        FilterChip(
                            selected = newToolId == tool.id,
                            onClick = {
                                newToolId = tool.id
                                newLabel = tool.displayName
                            },
                            label = { Text(tool.displayName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = newArgs,
                    onValueChange = { newArgs = it },
                    label = { Text("Arguments JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        // basic JSON validation — never save garbage
                        val ok = try { JSONObject(newArgs.ifBlank { "{}" }); true } catch (_: Throwable) { false }
                        if (ok) {
                            steps = steps + RoutineStepEntity(
                                routineId = 0,
                                position = steps.size,
                                toolId = newToolId,
                                argumentsJson = newArgs.ifBlank { "{}" },
                            )
                            newArgs = "{}"
                        }
                    },
                ) { Text("Add step") }
            }
        },
    )
}
