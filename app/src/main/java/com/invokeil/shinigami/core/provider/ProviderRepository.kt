package com.invokeil.shinigami.core.provider

import com.invokeil.shinigami.core.data.PrefsRepository
import com.invokeil.shinigami.core.data.db.ProviderProfileDao
import com.invokeil.shinigami.core.data.db.ProviderProfileEntity
import com.invokeil.shinigami.core.data.db.ProviderType
import com.invokeil.shinigami.core.security.SecretVault
import com.invokeil.shinigami.core.security.providerApiKeyRef
import com.invokeil.shinigami.core.security.providerPasswordRef
import com.invokeil.shinigami.core.util.AppError
import com.invokeil.shinigami.core.util.AppResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Repository over provider profiles: CRUD, secret handling and the active
 * profile. Secrets only ever travel profile ⇄ vault (MASTER SPEC §66).
 */
@Singleton
class ProviderRepository @Inject constructor(
    private val dao: ProviderProfileDao,
    private val prefs: PrefsRepository,
    private val vault: SecretVault,
    private val factory: ProviderFactory,
    private val httpClient: com.invokeil.shinigami.core.network.HttpClientFactory,
) {
    val profiles: Flow<List<ProviderProfileEntity>> = dao.observeAll()

    suspend fun byId(id: Long): ProviderProfileEntity? = dao.byId(id)

    suspend fun activeProfile(): ProviderProfileEntity? {
        val activeId = prefs.activeProviderId.firstOrNull()
        if (activeId != null) dao.byId(activeId)?.let { return it }
        return dao.defaultProfile() ?: dao.all().firstOrNull()
    }

    suspend fun provider(profileId: Long): AiProvider? {
        val profile = dao.byId(profileId) ?: return null
        return factory.build(profile)
    }

    /** Active provider, or null when nothing is configured (offline mode). */
    suspend fun activeProvider(): AiProvider? {
        val profile = activeProfile() ?: return null
        return factory.build(profile)
    }

    suspend fun save(profile: ProviderProfileEntity, apiKey: String?, password: String?): Long {
        val id = if (profile.id == 0L) {
            val first = dao.count() == 0
            dao.insert(profile.copy(isDefault = profile.isDefault || first))
        } else {
            dao.update(profile)
            profile.id
        }
        if (apiKey != null) {
            if (apiKey.isBlank()) {
                vault.remove(providerApiKeyRef(id))
                dao.byId(id)?.let { dao.update(it.copy(hasApiKey = false)) }
            } else {
                vault.put(providerApiKeyRef(id), apiKey.trim())
                dao.byId(id)?.let { dao.update(it.copy(hasApiKey = true)) }
            }
        }
        if (password != null) {
            if (password.isBlank()) {
                vault.remove(providerPasswordRef(id))
                dao.byId(id)?.let { dao.update(it.copy(hasPassword = false)) }
            } else {
                vault.put(providerPasswordRef(id), password)
                dao.byId(id)?.let { dao.update(it.copy(hasPassword = true)) }
            }
        }
        val currentActive = prefs.activeProviderId.firstOrNull()
        if (currentActive == null && dao.count() > 0) {
            prefs.setActiveProvider(id)
        }
        return id
    }

    suspend fun delete(profile: ProviderProfileEntity) {
        vault.remove(providerApiKeyRef(profile.id))
        vault.remove(providerPasswordRef(profile.id))
        dao.delete(profile)
        if (prefs.activeProviderId.firstOrNull() == profile.id) {
            prefs.setActiveProvider(null)
        }
    }

    suspend fun setActive(profileId: Long) = prefs.setActiveProvider(profileId)

    /**
     * Tests a (possibly unsaved) profile. Any API key typed into the editor is
     * used for the probe only — nothing is persisted here.
     */
    suspend fun test(
        profile: ProviderProfileEntity,
        apiKey: String?,
        password: String?,
    ): ProviderTestResult {
        return try {
            val baseConfig = factory.configFor(
                profile.copy(id = profile.id.takeIf { it != 0L } ?: 1L),
            )
            val config = baseConfig.copy(
                apiKey = apiKey?.takeIf { it.isNotBlank() } ?: baseConfig.apiKey,
                password = password?.takeIf { it.isNotBlank() } ?: baseConfig.password,
            )
            val provider: AiProvider = if (profile.type == ProviderType.GEMINI) {
                GeminiInstance(httpClient, config)
            } else {
                OpenAiInstance(httpClient, config)
            }
            provider.testConnection()
        } catch (t: Throwable) {
            ProviderTestResult.Failure(
                AppError.Unknown(t.message ?: "unknown"),
                t.toString(),
            )
        }
    }

    suspend fun fetchModels(profileId: Long): AppResult<List<AiModel>> {
        val provider = provider(profileId)
            ?: return AppResult.Failure(AppError.InvalidConfig("Provider not found."))
        return withContext(Dispatchers.IO) {
            try {
                AppResult.Success(provider.getModels())
            } catch (e: AppError) {
                AppResult.Failure(e)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Unknown(t.message ?: "unknown"))
            }
        }
    }
}

/**
 * Stateless wrappers that carry an *edited* (not yet persisted) config, so
 * test/fetch never depends on — or mutates — the active singleton state.
 */
private class OpenAiInstance(
    httpClientFactory: com.invokeil.shinigami.core.network.HttpClientFactory,
    config: ProviderConfig,
) : AiProvider {
    private val delegate = OpenAiCompatibleProvider(httpClientFactory).apply { this.config = config }
    override val capabilities get() = delegate.capabilities
    override suspend fun testConnection() = delegate.testConnection()
    override suspend fun getModels() = delegate.getModels()
    override suspend fun complete(request: AiRequest) = delegate.complete(request)
    override fun stream(request: AiRequest) = delegate.stream(request)
}

private class GeminiInstance(
    httpClientFactory: com.invokeil.shinigami.core.network.HttpClientFactory,
    config: ProviderConfig,
) : AiProvider {
    private val delegate = GeminiProvider(httpClientFactory).apply { this.config = config }
    override val capabilities get() = delegate.capabilities
    override suspend fun testConnection() = delegate.testConnection()
    override suspend fun getModels() = delegate.getModels()
    override suspend fun complete(request: AiRequest) = delegate.complete(request)
    override fun stream(request: AiRequest) = delegate.stream(request)
}
