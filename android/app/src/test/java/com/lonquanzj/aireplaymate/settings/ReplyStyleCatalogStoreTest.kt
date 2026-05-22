package com.lonquanzj.aireplaymate.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lonquanzj.aireplaymate.prompt.PolishGoalConfig
import com.lonquanzj.aireplaymate.prompt.ReplyPersonaConfig
import com.lonquanzj.aireplaymate.prompt.ReplyPlaybookConfig
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReplyStyleCatalogStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun load_returns_default_builtin_catalog_when_empty() {
        ReplyStyleCatalogStore.save(context, ReplyStyleCatalog.defaultCatalogState)

        val catalog = ReplyStyleCatalogStore.load(context)

        assertTrue(catalog.personas.any { it.isBuiltin })
        assertTrue(catalog.playbooks.any { it.isBuiltin })
        assertTrue(catalog.polishGoals.any { it.isBuiltin })
    }

    @Test
    fun save_and_load_custom_persona() {
        val custom = ReplyPersonaConfig(
            id = "persona_custom",
            label = "自定义",
            identityPrompt = "自定义身份",
            promptGuide = "自定义提示词",
            isBuiltin = false
        )
        val catalog = ReplyStyleCatalog.defaultCatalogState.copy(
            personas = ReplyStyleCatalog.defaultCatalogState.personas + custom
        )

        ReplyStyleCatalogStore.save(context, catalog)

        val loaded = ReplyStyleCatalogStore.load(context)
        assertEquals("自定义身份", loaded.resolvePersona("persona_custom").identityPrompt)
        assertFalse(loaded.resolvePersona("persona_custom").isBuiltin)
    }

    @Test
    fun resolver_falls_back_when_selected_id_missing() {
        val catalog = ReplyStyleCatalog.defaultCatalogState

        assertEquals(
            ReplyStyleCatalog.defaultPersonaConfig.id,
            catalog.resolvePersona("missing").id
        )
    }

    @Test
    fun load_returns_default_catalog_when_saved_json_is_broken() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CATALOG_JSON, "{broken")
            .commit()

        val catalog = ReplyStyleCatalogStore.load(context)

        assertEquals(ReplyStyleCatalog.defaultCatalogState.personas.size, catalog.personas.size)
        assertEquals(ReplyStyleCatalog.defaultCatalogState.playbooks.size, catalog.playbooks.size)
        assertEquals(ReplyStyleCatalog.defaultCatalogState.polishGoals.size, catalog.polishGoals.size)
    }

    @Test
    fun resetBuiltins_keeps_custom_items_and_restores_builtin_defaults() {
        val customPersona = ReplyPersonaConfig(
            id = "persona_custom",
            label = "Custom Persona",
            identityPrompt = "custom identity",
            promptGuide = "custom guide",
            isBuiltin = false
        )
        val customPlaybook = ReplyPlaybookConfig(
            id = "playbook_custom",
            categoryLabel = "Custom Category",
            label = "Custom Playbook",
            identityPrompt = "custom playbook identity",
            promptGuide = "custom playbook guide",
            isBuiltin = false
        )
        val customPolish = PolishGoalConfig(
            id = "polish_custom",
            label = "Custom Polish",
            identityPrompt = "custom polish identity",
            promptGuide = "custom polish guide",
            isBuiltin = false
        )
        val staleBuiltin = ReplyStyleCatalog.defaultPersonaConfig.copy(
            label = "stale builtin",
            promptGuide = "stale guide"
        )
        val current = ReplyStyleCatalog.defaultCatalogState.copy(
            personas = listOf(staleBuiltin, customPersona),
            playbooks = ReplyStyleCatalog.defaultCatalogState.playbooks + customPlaybook,
            polishGoals = ReplyStyleCatalog.defaultCatalogState.polishGoals + customPolish
        )

        val reset = ReplyStyleCatalogStore.resetBuiltins(context, current)

        assertEquals(
            ReplyStyleCatalog.defaultPersonaConfig.label,
            reset.resolvePersona(ReplyStyleCatalog.defaultPersonaConfig.id).label
        )
        assertEquals("custom identity", reset.resolvePersona("persona_custom").identityPrompt)
        assertEquals("custom playbook identity", reset.resolvePlaybook("playbook_custom").identityPrompt)
        assertEquals("custom polish identity", reset.resolvePolishGoal("polish_custom").identityPrompt)
    }

    private companion object {
        const val PREFS_NAME = "ai_replay_mate_reply_style_catalog"
        const val KEY_CATALOG_JSON = "catalog_json"
    }
}
