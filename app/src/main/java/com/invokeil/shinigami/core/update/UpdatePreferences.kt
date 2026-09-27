package com.invokeil.shinigami.core.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "shinigami_update")

/**
 * Update-checker persistence (research §2): ETag cache so 304 responses cost
 * zero GitHub API quota, last-seen version, snooze and skip-version state.
 */
@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ETAG = stringPreferencesKey("releases_etag")
        val LAST_CHECK = longPreferencesKey("last_check_ms")
        val LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
        val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
        val SNOOZED_UNTIL = longPreferencesKey("snoozed_until_ms")
        val AUTO_CHECK = androidx.datastore.preferences.core.booleanPreferencesKey("auto_check")
    }

    data class State(
        val lastCheckMs: Long = 0,
        val lastSeenVersion: String = "",
        val skippedVersion: String = "",
        val snoozedUntilMs: Long = 0,
        val autoCheck: Boolean = true,
    )

    val state: Flow<State> = context.updateDataStore.data.map { p ->
        State(
            lastCheckMs = p[Keys.LAST_CHECK] ?: 0,
            lastSeenVersion = p[Keys.LAST_SEEN_VERSION] ?: "",
            skippedVersion = p[Keys.SKIPPED_VERSION] ?: "",
            snoozedUntilMs = p[Keys.SNOOZED_UNTIL] ?: 0,
            autoCheck = p[Keys.AUTO_CHECK] ?: true,
        )
    }

    suspend fun etag(): String? =
        context.updateDataStore.data.first()[Keys.ETAG]

    suspend fun setEtag(etag: String?) = context.updateDataStore.edit {
        if (etag == null) it.remove(Keys.ETAG) else it[Keys.ETAG] = etag
    }

    suspend fun setLastCheck(version: String) = context.updateDataStore.edit {
        it[Keys.LAST_CHECK] = System.currentTimeMillis()
        it[Keys.LAST_SEEN_VERSION] = version
    }

    suspend fun skip(version: String) = context.updateDataStore.edit {
        it[Keys.SKIPPED_VERSION] = version
        it.remove(Keys.SNOOZED_UNTIL)
    }

    suspend fun snooze(hours: Int = 48) = context.updateDataStore.edit {
        it[Keys.SNOOZED_UNTIL] = System.currentTimeMillis() + hours * 3_600_000L
    }

    suspend fun setAutoCheck(v: Boolean) = context.updateDataStore.edit { it[Keys.AUTO_CHECK] = v }
}
