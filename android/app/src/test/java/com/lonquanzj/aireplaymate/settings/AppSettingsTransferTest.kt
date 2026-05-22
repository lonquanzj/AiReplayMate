package com.lonquanzj.aireplaymate.settings

import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy
import com.lonquanzj.aireplaymate.prompt.PolishGoalConfig
import com.lonquanzj.aireplaymate.prompt.ReplyPersonaConfig
import com.lonquanzj.aireplaymate.prompt.ReplyPlaybookConfig
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTransferTest {
    @Test
    fun encode_and_decode_round_trips_llm_settings() {
        val settings = AppSettings(
            apiKey = "sk-test",
            baseUrl = "https://example.test/v1/",
            model = "gpt-test",
            temperature = 1.1f,
            maxTokens = 640,
            contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE
        )

        val decoded = AppSettingsTransfer.decode(AppSettingsTransfer.encode(settings)).getOrThrow()

        assertEquals(settings.apiKey, decoded.apiKey)
        assertEquals(settings.baseUrl, decoded.baseUrl)
        assertEquals(settings.model, decoded.model)
        assertEquals(settings.temperature, decoded.temperature, 0.0001f)
        assertEquals(settings.maxTokens, decoded.maxTokens)
        assertEquals(settings.contextSendPolicy, decoded.contextSendPolicy)
    }

    @Test
    fun encode_exports_temperature_with_one_decimal_place() {
        val encoded = AppSettingsTransfer.encode(AppSettings(temperature = 0.699999988079071f))

        assertTrue(encoded.contains("\"temperature\": 0.7"))
        assertFalse(encoded.contains("0.699999"))
    }

    @Test
    fun decode_clamps_numeric_values_and_defaults_unknown_policy() {
        val decoded = AppSettingsTransfer.decode(
            """
            {
              "apiKey": "sk-test",
              "baseUrl": "",
              "model": "",
              "temperature": 9.0,
              "maxTokens": 40,
              "contextSendPolicy": "REMOVED"
            }
            """.trimIndent()
        ).getOrThrow()
        val defaults = AppSettings()

        assertEquals("sk-test", decoded.apiKey)
        assertEquals(defaults.baseUrl, decoded.baseUrl)
        assertEquals(defaults.model, decoded.model)
        assertEquals(2f, decoded.temperature, 0.0001f)
        assertEquals(120, decoded.maxTokens)
        assertEquals(defaults.contextSendPolicy, decoded.contextSendPolicy)
    }

    @Test
    fun decode_returns_failure_for_invalid_json() {
        assertTrue(AppSettingsTransfer.decode("not-json").isFailure)
    }

    @Test
    fun encode_and_decode_full_config_round_trips_style_catalog_and_selection() {
        val settings = AppSettings(
            apiKey = "sk-test",
            baseUrl = "https://example.test/v1/",
            model = "gpt-test",
            temperature = 0.8f,
            maxTokens = 512,
            contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE
        )
        val catalog = ReplyStyleCatalogState(
            personas = ReplyStyleCatalog.defaultCatalogState.personas + ReplyPersonaConfig(
                id = "custom_persona",
                label = "Custom Persona",
                identityPrompt = "identity",
                promptGuide = "guide",
                isBuiltin = false
            ),
            playbooks = ReplyStyleCatalog.defaultCatalogState.playbooks + ReplyPlaybookConfig(
                id = "custom_scene",
                categoryLabel = "Custom",
                label = "Custom Scene",
                identityPrompt = "scene identity",
                promptGuide = "scene guide",
                isBuiltin = false
            ),
            polishGoals = ReplyStyleCatalog.defaultCatalogState.polishGoals + PolishGoalConfig(
                id = "custom_goal",
                label = "Custom Goal",
                identityPrompt = "goal identity",
                promptGuide = "goal guide",
                isBuiltin = false
            )
        )
        val profile = ReplyStyleSettingsStore.profileFromIds(
            modeId = ReplyStyleMode.PLAYBOOK.id,
            personaId = "custom_persona",
            sceneId = "custom_scene",
            polishGoalId = "custom_goal",
            catalog = catalog
        )

        val decoded = AppSettingsTransfer.decodeFull(
            AppSettingsTransfer.encode(settings, profile, catalog)
        ).getOrThrow()

        assertEquals(settings.apiKey, decoded.appSettings.apiKey)
        assertEquals(settings.model, decoded.appSettings.model)
        assertEquals("custom_persona", decoded.replyStyleProfile.personaConfig.id)
        assertEquals("custom_scene", decoded.replyStyleProfile.playbookConfig.id)
        assertEquals("custom_goal", decoded.replyStyleProfile.polishGoalConfig.id)
        assertEquals(ReplyStyleMode.PLAYBOOK, decoded.replyStyleProfile.mode)
        assertTrue(decoded.replyStyleCatalog.personas.any { it.id == "custom_persona" })
        assertTrue(decoded.replyStyleCatalog.playbooks.any { it.id == "custom_scene" })
        assertTrue(decoded.replyStyleCatalog.polishGoals.any { it.id == "custom_goal" })
    }

    @Test
    fun decode_full_accepts_legacy_llm_only_config() {
        val decoded = AppSettingsTransfer.decodeFull(
            """
            {
              "apiKey": "sk-legacy",
              "baseUrl": "https://legacy.test/",
              "model": "legacy-model"
            }
            """.trimIndent()
        ).getOrThrow()

        assertEquals("sk-legacy", decoded.appSettings.apiKey)
        assertEquals("legacy-model", decoded.appSettings.model)
        assertEquals(ReplyStyleCatalog.defaultPersonaConfig.id, decoded.replyStyleProfile.personaConfig.id)
    }
}
