package com.invokeil.shinigami.feature.providers

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
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.R
import com.invokeil.shinigami.core.data.db.ProviderProfileEntity
import com.invokeil.shinigami.core.provider.ProviderRepository
import com.invokeil.shinigami.core.ui.components.EmptyState
import com.invokeil.shinigami.core.ui.components.ShiniButton
import com.invokeil.shinigami.core.ui.components.ShiniCard
import com.invokeil.shinigami.core.ui.components.StatusChip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val repository: ProviderRepository,
    private val prefs: com.invokeil.shinigami.core.data.PrefsRepository,
) : ViewModel() {

    val profiles: StateFlow<List<ProviderProfileEntity>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeId: StateFlow<Long?> = prefs.activeProviderId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setActive(id: Long) = viewModelScope.launch { repository.setActive(id) }

    fun delete(profile: ProviderProfileEntity) = viewModelScope.launch { repository.delete(profile) }
}

@Composable
fun ProvidersScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeId by viewModel.activeId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Text(
            stringResource(R.string.providers_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        if (profiles.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.providers_empty_title),
                    body = stringResource(R.string.providers_empty_body),
                    ctaText = stringResource(R.string.provider_add),
                    onCta = onAdd,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ShiniCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (profile.id == activeId) {
                                    StatusChip(
                                        text = stringResource(R.string.provider_active_badge),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${profile.model} · ${profile.baseUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (profile.id != activeId) {
                                    ShiniButton(
                                        stringResource(R.string.provider_set_active),
                                        onClick = { viewModel.setActive(profile.id) },
                                        modifier = Modifier.padding(end = 10.dp),
                                    )
                                }
                                IconButton(onClick = { onEdit(profile.id) }) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.provider_save),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.delete(profile) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.provider_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(70.dp)) }
            }
        }

        ShiniButton(
            stringResource(R.string.provider_add),
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
        )
    }
}
