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
        assertTrue(validation.errors.any { it.contains("API Key") })
        assertTrue(validation.errors.any { it.contains("Base URL") })
        assertTrue(validation.errors.any { it.contains("Model") })
        assertTrue(validation.errors.any { it.contains("1") && it.contains("5") })
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
        assertTrue(validation.errors.any { it.contains("Base URL") && it.contains("https://api.openai.com") })
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
        assertTrue(validation.warnings.any { it.contains("http") })
    }

    @Test
    fun validate_rejects_unsupported_scheme_and_too_many_candidates() {
        val validation = AppSettingsValidator.validate(
            AppSettings(
                apiKey = "sk-test",
                baseUrl = "ftp://example.test",
                model = "gpt-4o-mini",
                candidateCount = 6
            )
        )

        assertFalse(validation.canRequest)
        assertTrue(validation.errors.any { it.contains("Base URL") && it.contains("http") && it.contains("https") })
        assertTrue(validation.errors.any { it.contains("1") && it.contains("5") })
    }
}
