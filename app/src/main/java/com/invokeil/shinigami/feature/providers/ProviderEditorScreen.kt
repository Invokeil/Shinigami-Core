package com.invokeil.shinigami.feature.providers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.R
import com.invokeil.shinigami.core.data.PrefsRepository
import com.invokeil.shinigami.core.data.db.AuthType
import com.invokeil.shinigami.core.data.db.ProviderProfileEntity
import com.invokeil.shinigami.core.data.db.ProviderType
import com.invokeil.shinigami.core.provider.AiModel
import com.invokeil.shinigami.core.provider.ProviderHttp
import com.invokeil.shinigami.core.provider.ProviderRepository
import com.invokeil.shinigami.core.provider.ProviderTemplate
import com.invokeil.shinigami.core.provider.ProviderTemplates
import com.invokeil.shinigami.core.provider.ProviderTestResult
import com.invokeil.shinigami.core.ui.components.SectionHeader
import com.invokeil.shinigami.core.ui.components.ShiniButton
import com.invokeil.shinigami.core.ui.components.StatusChip
import com.invokeil.shinigami.core.util.Redactor
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditorState(
    val profile: ProviderProfileEntity = ProviderProfileEntity(
        id = 0,
        name = "",
        type = ProviderType.OPENAI,
        baseUrl = ProviderTemplates.forType(ProviderType.OPENAI).defaultBaseUrl,
        model = "",
    ),
    val apiKey: String = "",
    val password: String = "",
    val testing: Boolean = false,
    val testResult: ProviderTestResult? = null,
    val models: List<AiModel> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    val modelQuery: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val advanced: Boolean = false,
)

@HiltViewModel
class ProviderEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProviderRepository,
    private val prefs: PrefsRepository,
) : ViewModel() {

    private val providerId: Long = savedStateHandle.get<String>("providerId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (providerId > 0) {
                repository.byId(providerId)?.let { loaded ->
                    _state.value = _state.value.copy(profile = loaded)
                }
            }
        }
    }

    fun update(transform: (ProviderProfileEntity) -> ProviderProfileEntity) {
        _state.value = _state.value.copy(profile = transform(_state.value.profile), testResult = null)
    }

    fun setApiKey(v: String) {
        _state.value = _state.value.copy(apiKey = v, testResult = null)
    }

    fun setPassword(v: String) {
        _state.value = _state.value.copy(password = v, testResult = null)
    }

    fun applyTemplate(type: ProviderType) {
        val template = ProviderTemplates.forType(type)
        _state.value = _state.value.copy(
            profile = _state.value.profile.copy(
                type = type,
                baseUrl = if (providerId == 0L || _state.value.profile.baseUrl.isBlank()) template.defaultBaseUrl
                else _state.value.profile.baseUrl,
                model = if (type == ProviderType.OPENAI_COMPATIBLE) _state.value.profile.model else template.suggestedModel,
                authType = template.authType,
                apiKeyHeader = template.apiKeyHeader,
            ),
            testResult = null,
        )
    }

    fun toggleAdvanced() {
        _state.value = _state.value.copy(advanced = !_state.value.advanced)
    }

    fun test() {
        viewModelScope.launch {
            _state.value = _state.value.copy(testing = true, testResult = null)
            val s = _state.value
            val result = repository.test(s.profile, s.apiKey.takeIf { it.isNotBlank() }, s.password.takeIf { it.isNotBlank() })
            _state.value = _state.value.copy(testing = false, testResult = result)
        }
    }

    fun fetchModels() {
        viewModelScope.launch {
            _state.value = _state.value.copy(modelsLoading = true, modelsError = null)
            // Save first so the provider has an id + current secrets
            val id = saveInternal()
            val result = repository.fetchModels(id)
            result.onSuccess { models ->
                _state.value = _state.value.copy(modelsLoading = false, models = models)
            }.onFailure { error ->
                _state.value = _state.value.copy(modelsLoading = false, modelsError = error.userMessage)
            }
        }
    }

    fun selectModel(modelId: String) {
        update { it.copy(model = modelId) }
    }

    fun setModelQuery(q: String) {
        _state.value = _state.value.copy(modelQuery = q)
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            saveInternal()
            _state.value = _state.value.copy(saving = false, saved = true)
            onDone()
        }
    }

    private suspend fun saveInternal(): Long {
        val s = _state.value
        val id = repository.save(s.profile, s.apiKey.takeIf { it.isNotBlank() }, s.password.takeIf { it.isNotBlank() })
        val refreshed = repository.byId(id) ?: s.profile
        _state.value = _state.value.copy(profile = refreshed.copy(id = id))
        return id
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditorScreen(
    providerId: Long,
    onDone: () -> Unit,
    viewModel: ProviderEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val showModelsDialog = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    val template = ProviderTemplates.forType(state.profile.type)
    val insecure = ProviderHttp.validateBaseUrl(state.profile.baseUrl, state.profile.allowCleartext)
        ?.let { it is com.invokeil.shinigami.core.util.AppError.InvalidConfig && it.userMessage.contains("HTTPS") } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.ob_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                stringResource(
                    if (providerId > 0L) R.string.providers_title else R.string.provider_add,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Spacer(Modifier.height(8.dp))

        // Provider type chips
        Text(stringResource(R.string.provider_type), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            ProviderTemplates.ALL.forEach { t ->
                FilterChip(
                    selected = state.profile.type == t.type,
                    onClick = { viewModel.applyTemplate(t.type) },
                    label = { Text(t.displayName) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            template.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = state.profile.name,
            onValueChange = { v -> viewModel.update { it.copy(name = v) } },
            label = { Text(stringResource(R.string.provider_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.profile.baseUrl,
            onValueChange = { v -> viewModel.update { it.copy(baseUrl = v) } },
            label = { Text(stringResource(R.string.provider_base_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (insecure) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.provider_https_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.profile.model,
            onValueChange = { v -> viewModel.update { it.copy(model = v) } },
            label = { Text(stringResource(R.string.provider_model)) },
            placeholder = { Text(stringResource(R.string.provider_model_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(10.dp))
        // Auth
        Text(stringResource(R.string.provider_auth_none), style = MaterialTheme.typography.titleSmall)
        listOf(
            AuthType.NONE to stringResource(R.string.provider_auth_none),
            AuthType.BEARER to stringResource(R.string.provider_auth_bearer),
            AuthType.API_KEY_HEADER to stringResource(R.string.provider_auth_api_key),
            AuthType.BASIC to stringResource(R.string.provider_auth_basic),
        ).forEach { (auth, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = state.profile.authType == auth,
                    onClick = { viewModel.update { it.copy(authType = auth) } },
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        when (state.profile.authType) {
            AuthType.BEARER, AuthType.API_KEY_HEADER -> {
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = viewModel::setApiKey,
                    label = {
                        Text(
                            stringResource(
                                if (state.profile.hasApiKey) R.string.provider_api_key
                                else R.string.provider_api_key_optional,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                if (state.profile.authType == AuthType.API_KEY_HEADER) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.profile.apiKeyHeader,
                        onValueChange = { v -> viewModel.update { it.copy(apiKeyHeader = v) } },
                        label = { Text(stringResource(R.string.provider_header_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            AuthType.BASIC -> {
                OutlinedTextField(
                    value = state.profile.username.orEmpty(),
                    onValueChange = { v -> viewModel.update { it.copy(username = v) } },
                    label = { Text(stringResource(R.string.provider_username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::setPassword,
                    label = { Text(stringResource(R.string.provider_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            }

            AuthType.NONE -> Unit
        }

        SectionHeader(stringResource(R.string.provider_advanced))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.advanced, onCheckedChange = { viewModel.toggleAdvanced() })
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.provider_advanced), style = MaterialTheme.typography.bodyMedium)
        }
        AnimatedVisibility(visible = state.advanced) {
            Column {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.profile.extraHeadersJson,
                    onValueChange = { v -> viewModel.update { it.copy(extraHeadersJson = v) } },
                    label = { Text(stringResource(R.string.provider_extra_headers) + " (JSON)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.provider_temperature) + ": ${"%.1f".format(state.profile.temperature)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.Slider(
                    value = state.profile.temperature,
                    onValueChange = { v -> viewModel.update { it.copy(temperature = v) } },
                    valueRange = 0f..2f,
                )
                Text(
                    stringResource(R.string.provider_max_tokens) + ": ${state.profile.maxTokens}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.Slider(
                    value = state.profile.maxTokens.toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(maxTokens = v.toInt()) } },
                    valueRange = 128f..8192f,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.profile.customInstructions,
                    onValueChange = { v -> viewModel.update { it.copy(customInstructions = v) } },
                    label = { Text(stringResource(R.string.provider_system_prompt_extra)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Test + fetch
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShiniButton(
                stringResource(R.string.provider_test),
                onClick = { viewModel.test() },
                enabled = !state.testing,
            )
            ShiniButton(
                stringResource(R.string.provider_fetch_models),
                onClick = {
                    showModelsDialog.value = true
                    viewModel.fetchModels()
                },
                enabled = !state.modelsLoading,
            )
        }
        if (state.testing) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.width(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.home_thinking), style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.testResult?.let { result ->
            Spacer(Modifier.height(14.dp))
            when (result) {
                is ProviderTestResult.Success -> {
                    StatusChip(
                        text = stringResource(R.string.provider_test_ok, result.latencyMs),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.provider_test_ok_detail, result.modelChecked ?: "-"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        result.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is ProviderTestResult.Failure -> {
                    StatusChip(
                        text = stringResource(R.string.provider_test_network),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        result.error.userMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.provider_test_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Redactor.redact(result.technicalDetails).take(400),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        ShiniButton(
            stringResource(R.string.provider_save),
            onClick = { viewModel.save(onDone) },
            enabled = !state.saving && state.profile.name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(30.dp))
    }

    if (showModelsDialog.value) {
        ModelsDialog(
            state = state,
            onDismiss = { showModelsDialog.value = false },
            onQueryChange = viewModel::setModelQuery,
            onSelect = {
                viewModel.selectModel(it)
                showModelsDialog.value = false
            },
            onRetry = { viewModel.fetchModels() },
        )
    }
}

@Composable
private fun ModelsDialog(
    state: EditorState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        title = { Text(stringResource(R.string.provider_fetch_models)) },
        text = {
            Column {
                if (state.modelsLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.home_thinking))
                    }
                } else if (state.modelsError != null) {
                    Text(
                        state.modelsError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                } else {
                    val filtered = state.models.filter {
                        it.id.contains(state.modelQuery, ignoreCase = true)
                    }
                    OutlinedTextField(
                        value = state.modelQuery,
                        onValueChange = onQueryChange,
                        label = { Text("Filter") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filtered, key = { it.id }) { model ->
                            TextButton(onClick = { onSelect(model.id) }) {
                                Text(model.id, maxLines = 1)
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            stringResource(R.string.provider_model_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}
