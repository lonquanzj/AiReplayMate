package com.lonquanzj.aireplaymate.settings

import android.content.Context
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy

object AppSettingsStore {
    private const val PREFS_NAME = "ai_replay_mate_settings"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL = "model"
    private const val KEY_CONTEXT_SEND_POLICY = "context_send_policy"

    fun load(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = AppSettings()
        return AppSettings(
            apiKey = prefs.getString(KEY_API_KEY, defaults.apiKey).orEmpty(),
            baseUrl = prefs.getString(KEY_BASE_URL, defaults.baseUrl).orEmpty()
                .ifBlank { defaults.baseUrl },
            model = prefs.getString(KEY_MODEL, defaults.model).orEmpty()
                .ifBlank { defaults.model },
            temperature = defaults.temperature,
            maxTokens = defaults.maxTokens,
            customSystemPrompt = defaults.customSystemPrompt,
            candidateCount = defaults.candidateCount,
            contextSendPolicy = prefs.getString(KEY_CONTEXT_SEND_POLICY, defaults.contextSendPolicy.name)
                .toContextSendPolicy(defaults.contextSendPolicy)
        )
    }

    fun save(
        context: Context,
        settings: AppSettings
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(KEY_BASE_URL, settings.baseUrl.trim())
            .putString(KEY_MODEL, settings.model.trim())
            .putString(KEY_CONTEXT_SEND_POLICY, settings.contextSendPolicy.name)
            .apply()
    }

    private fun String?.toContextSendPolicy(default: ContextSendPolicy): ContextSendPolicy {
        return runCatching { ContextSendPolicy.valueOf(orEmpty()) }.getOrDefault(default)
    }
}
