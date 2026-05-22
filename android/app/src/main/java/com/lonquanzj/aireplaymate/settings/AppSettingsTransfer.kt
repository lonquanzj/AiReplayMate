package com.lonquanzj.aireplaymate.settings

import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy
import org.json.JSONObject

object AppSettingsTransfer {
    private const val VERSION = 1

    fun encode(settings: AppSettings): String {
        return JSONObject()
            .put("version", VERSION)
            .put("apiKey", settings.apiKey)
            .put("baseUrl", settings.baseUrl)
            .put("model", settings.model)
            .put("temperature", settings.temperature.toDouble())
            .put("maxTokens", settings.maxTokens)
            .put("contextSendPolicy", settings.contextSendPolicy.name)
            .toString(2)
    }

    fun decode(raw: String): Result<AppSettings> {
        return runCatching {
            val root = JSONObject(raw)
            val defaults = AppSettings()
            AppSettings(
                apiKey = root.optString("apiKey", defaults.apiKey),
                baseUrl = root.optString("baseUrl", defaults.baseUrl).ifBlank { defaults.baseUrl },
                model = root.optString("model", defaults.model).ifBlank { defaults.model },
                temperature = root.optDouble("temperature", defaults.temperature.toDouble())
                    .toFloat()
                    .coerceIn(0f, 2f),
                maxTokens = root.optInt("maxTokens", defaults.maxTokens).coerceIn(120, 2000),
                customSystemPrompt = defaults.customSystemPrompt,
                candidateCount = defaults.candidateCount,
                contextSendPolicy = root.optString("contextSendPolicy", defaults.contextSendPolicy.name)
                    .toContextSendPolicy(defaults.contextSendPolicy)
            )
        }
    }

    private fun String.toContextSendPolicy(default: ContextSendPolicy): ContextSendPolicy {
        return runCatching { ContextSendPolicy.valueOf(this) }.getOrDefault(default)
    }
}
