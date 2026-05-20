package com.lonquanzj.aireplaymate.settings

import com.lonquanzj.aireplaymate.prompt.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsValidatorTest {
    @Test
    fun validate_reports_required_field_and_candidate_count_errors() {
        val validation = AppSettingsValidator.validate(
            AppSettings(
                apiKey = " ",
                baseUrl = " ",
                model = "",
                candidateCount = 0
            )
        )

        assertFalse(validation.canRequest)
        assertTrue(validation.errors.contains("API Key 不能为空"))
        assertTrue(validation.errors.contains("Base URL 不能为空"))
        assertTrue(validation.errors.contains("Model 不能为空"))
        assertTrue(validation.errors.contains("候选数量建议保持在 1 到 5 条"))
    }

    @Test
    fun validate_rejects_invalid_base_url() {
        val validation = AppSettingsValidator.validate(
            AppSettings(
                apiKey = "sk-test",
                baseUrl = "openai",
                model = "gpt-4o-mini"
            )
        )

        assertFalse(validation.canRequest)
        assertTrue(validation.errors.contains("Base URL 需要是完整地址，例如 https://api.openai.com"))
    }

    @Test
    fun validate_warns_for_http_without_blocking_request() {
        val validation = AppSettingsValidator.validate(
            AppSettings(
                apiKey = "sk-test",
                baseUrl = "http://127.0.0.1:8080",
                model = "gpt-4o-mini",
                candidateCount = 5
            )
        )

        assertTrue(validation.canRequest)
        assertTrue(validation.warnings.contains("当前使用 http，仅建议本地代理或内网调试时使用"))
    }
}
