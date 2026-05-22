package com.lonquanzj.aireplaymate.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun save_and_load_persists_trimmed_llm_settings_and_context_policy() {
        AppSettingsStore.save(
            context = context,
            settings = AppSettings(
                apiKey = "  sk-test  ",
                baseUrl = "  https://example.test/v1/  ",
                model = "  gpt-test  ",
                contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE
            )
        )

        val loaded = AppSettingsStore.load(context)

        assertEquals("sk-test", loaded.apiKey)
        assertEquals("https://example.test/v1/", loaded.baseUrl)
        assertEquals("gpt-test", loaded.model)
        assertEquals(ContextSendPolicy.LATEST_FRIEND_MESSAGE, loaded.contextSendPolicy)
    }

    @Test
    fun load_uses_defaults_for_blank_base_url_model_and_unknown_context_policy() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, "sk-test")
            .putString(KEY_BASE_URL, " ")
            .putString(KEY_MODEL, " ")
            .putString(KEY_CONTEXT_SEND_POLICY, "removed_policy")
            .commit()

        val loaded = AppSettingsStore.load(context)
        val defaults = AppSettings()

        assertEquals("sk-test", loaded.apiKey)
        assertEquals(defaults.baseUrl, loaded.baseUrl)
        assertEquals(defaults.model, loaded.model)
        assertEquals(defaults.contextSendPolicy, loaded.contextSendPolicy)
    }

    private companion object {
        const val PREFS_NAME = "ai_replay_mate_settings"
        const val KEY_API_KEY = "api_key"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_CONTEXT_SEND_POLICY = "context_send_policy"
    }
}
