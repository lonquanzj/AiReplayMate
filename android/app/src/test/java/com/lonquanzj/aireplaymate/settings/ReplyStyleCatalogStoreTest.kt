package com.lonquanzj.aireplaymate.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lonquanzj.aireplaymate.prompt.ReplyPersonaConfig
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
}
