package com.invokeil.shinigami.feature.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.R
import com.invokeil.shinigami.core.ui.theme.RiskLow
import com.invokeil.shinigami.core.ui.theme.RiskMedium
import com.invokeil.shinigami.core.ui.theme.RiskHigh
import com.invokeil.shinigami.core.ui.theme.RiskCritical
import com.invokeil.shinigami.core.data.db.AuditDao
import com.invokeil.shinigami.core.data.db.AuditEventEntity
import com.invokeil.shinigami.core.data.db.RiskLevel
import com.invokeil.shinigami.core.ui.components.EmptyState
import com.invokeil.shinigami.core.ui.components.ShiniCard
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AuditViewModel @Inject constructor(
    private val auditDao: AuditDao,
) : ViewModel() {

    val events: StateFlow<List<AuditEventEntity>> = auditDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clear() {
        viewModelScope.launch { auditDao.clear() }
    }
}

@Composable
fun AuditScreen(onBack: () -> Unit, viewModel: AuditViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsState()

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
                stringResource(R.string.audit_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (events.isNotEmpty()) {
                IconButton(onClick = { viewModel.clear() }) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.audit_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (events.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Receipt,
                    title = stringResource(R.string.audit_empty_title),
                    body = stringResource(R.string.audit_empty_body),
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(events, key = { it.id }) { event ->
                    AuditRow(event)
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun AuditRow(event: AuditEventEntity) {
    val time = remember(event.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date(event.timestamp))
    }
    val riskColor = when (event.riskLevel) {
        RiskLevel.LOW -> RiskLow
        RiskLevel.MEDIUM -> RiskMedium
        RiskLevel.HIGH -> RiskHigh
        RiskLevel.CRITICAL -> RiskCritical
    }
    ShiniCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(riskColor)
                    .padding(top = 4.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(event.action, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    event.detail ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$time · ${event.source.name.lowercase().replace('_', ' ')} · " +
                        event.result.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
