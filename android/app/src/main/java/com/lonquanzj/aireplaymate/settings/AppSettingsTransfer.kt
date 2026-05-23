package com.lonquanzj.aireplaymate.settings

import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy
import com.lonquanzj.aireplaymate.prompt.LlmSampling
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import org.json.JSONObject

data class ExportedAppConfig(
    val appSettings: AppSettings,
    val replyStyleProfile: ReplyStyleProfile,
    val replyStyleCatalog: ReplyStyleCatalogState
)

object AppSettingsTransfer {
    private const val VERSION = 2

    fun encode(settings: AppSettings): String {
        return encodeAppSettings(settings).put("version", VERSION).toString(2)
    }

    fun encode(
        settings: AppSettings,
        replyStyleProfile: ReplyStyleProfile,
        replyStyleCatalog: ReplyStyleCatalogState
    ): String {
        return JSONObject()
            .put("version", VERSION)
            .put("llm", encodeAppSettings(settings))
            .put("replyStyle", encodeReplyStyle(replyStyleProfile))
            .put("replyStyleCatalog", ReplyStyleCatalogStore.encodeToJson(replyStyleCatalog))
            .toString(2)
    }

    fun decode(raw: String): Result<AppSettings> {
        return runCatching {
            val root = JSONObject(raw)
            decodeAppSettings(
                root = root.optJSONObject("llm") ?: root,
                version = root.optInt("version", 1)
            )
        }
    }

    fun decodeFull(raw: String): Result<ExportedAppConfig> {
        return runCatching {
            val root = JSONObject(raw)
            val version = root.optInt("version", 1)
            val settings = decodeAppSettings(
                root = root.optJSONObject("llm") ?: root,
                version = version
            )
            val catalog = root.optJSONObject("replyStyleCatalog")
                ?.let { ReplyStyleCatalogStore.decodeFromJson(it) }
                ?: ReplyStyleCatalogStore.defaultCatalog()
            val profile = root.optJSONObject("replyStyle")
                ?.let { decodeReplyStyle(it, catalog) }
                ?: ReplyStyleSettingsStore.defaultProfile(catalog)
            ExportedAppConfig(
                appSettings = settings,
                replyStyleProfile = profile,
                replyStyleCatalog = catalog
            )
        }
    }

    private fun encodeAppSettings(settings: AppSettings): JSONObject {
        return JSONObject()
            .put("apiKey", settings.apiKey)
            .put("baseUrl", settings.baseUrl)
            .put("model", settings.model)
            .put("temperature", LlmSampling.normalizedTemperatureDouble(settings.temperature))
            .put("maxTokens", settings.maxTokens)
            .put("customSystemPrompt", settings.customSystemPrompt)
            .put("candidateCount", settings.candidateCount)
            .put("contextSendPolicy", settings.contextSendPolicy.name)
    }

    private fun decodeAppSettings(root: JSONObject, version: Int): AppSettings {
        val defaults = AppSettings()
        val decodedCustomSystemPrompt = root.optNullableString("customSystemPrompt")
            ?: if (version >= 2) null else defaults.customSystemPrompt
        val decodedCandidateCount = if (version >= 2) {
            root.optInt("candidateCount", defaults.candidateCount)
        } else {
            defaults.candidateCount
        }
        return AppSettings(
            apiKey = root.optString("apiKey", defaults.apiKey),
            baseUrl = root.optString("baseUrl", defaults.baseUrl).ifBlank { defaults.baseUrl },
            model = root.optString("model", defaults.model).ifBlank { defaults.model },
            temperature = LlmSampling.normalizeTemperature(
                root.optDouble("temperature", defaults.temperature.toDouble()).toFloat()
            ),
            maxTokens = root.optInt("maxTokens", defaults.maxTokens).coerceIn(120, 2000),
            customSystemPrompt = decodedCustomSystemPrompt,
            candidateCount = decodedCandidateCount.coerceIn(1, 5),
            contextSendPolicy = root.optString("contextSendPolicy", defaults.contextSendPolicy.name)
                .toContextSendPolicy(defaults.contextSendPolicy)
        )
    }

    private fun encodeReplyStyle(profile: ReplyStyleProfile): JSONObject {
        return JSONObject()
            .put("mode", profile.mode.id)
            .put("persona", profile.personaConfig.id)
            .put("scene", profile.playbookConfig.id)
            .put("polishGoal", profile.polishGoalConfig.id)
    }

    private fun decodeReplyStyle(root: JSONObject, catalog: ReplyStyleCatalogState): ReplyStyleProfile {
        return ReplyStyleSettingsStore.profileFromIds(
            modeId = root.optNullableString("mode"),
            personaId = root.optNullableString("persona"),
            sceneId = root.optNullableString("scene"),
            polishGoalId = root.optNullableString("polishGoal"),
            catalog = catalog
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun String.toContextSendPolicy(default: ContextSendPolicy): ContextSendPolicy {
        return runCatching { ContextSendPolicy.valueOf(this) }.getOrDefault(default)
    }
}
