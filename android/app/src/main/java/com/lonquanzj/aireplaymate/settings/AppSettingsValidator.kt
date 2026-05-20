package com.lonquanzj.aireplaymate.settings

import com.lonquanzj.aireplaymate.prompt.AppSettings
import java.net.URI

data class AppSettingsValidation(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val canRequest: Boolean
        get() = errors.isEmpty()
}

object AppSettingsValidator {
    fun validate(settings: AppSettings): AppSettingsValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (settings.apiKey.isBlank()) {
            errors += "API Key 不能为空"
        }

        val baseUrl = settings.baseUrl.trim()
        if (baseUrl.isBlank()) {
            errors += "Base URL 不能为空"
        } else {
            val uri = runCatching { URI(baseUrl) }.getOrNull()
            when {
                uri == null || uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank() -> {
                    errors += "Base URL 需要是完整地址，例如 https://api.openai.com"
                }

                uri.scheme !in setOf("https", "http") -> {
                    errors += "Base URL 仅支持 http 或 https"
                }

                uri.scheme == "http" -> {
                    warnings += "当前使用 http，仅建议本地代理或内网调试时使用"
                }
            }
        }

        if (settings.model.isBlank()) {
            errors += "Model 不能为空"
        }

        if (settings.candidateCount !in 1..5) {
            errors += "候选数量建议保持在 1 到 5 条"
        }

        return AppSettingsValidation(
            errors = errors,
            warnings = warnings
        )
    }
}
