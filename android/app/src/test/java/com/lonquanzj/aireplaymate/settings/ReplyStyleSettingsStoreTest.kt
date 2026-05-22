package com.lonquanzj.aireplaymate.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lonquanzj.aireplaymate.prompt.PolishGoal
import com.lonquanzj.aireplaymate.prompt.ReplyPersona
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReplyStyleSettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ReplyStyleCatalogStore.save(context, ReplyStyleCatalog.defaultCatalogState)
    }

    @Test
    fun save_and_load_keeps_selected_persona_playbook_and_polish_goal() {
        val profile = ReplyStyleProfile(
            mode = ReplyStyleMode.PLAYBOOK,
            persona = ReplyPersona.TIEBA_MODE,
            playbookScene = ReplyStyleCatalog.sceneFromId("good_night"),
            polishGoal = PolishGoal.FLIRTY,
            personaConfig = ReplyStyleCatalog.defaultCatalogState.resolvePersona(ReplyPersona.TIEBA_MODE.id),
            playbookConfig = ReplyStyleCatalog.defaultCatalogState.resolvePlaybook("good_night"),
            polishGoalConfig = ReplyStyleCatalog.defaultCatalogState.resolvePolishGoal(PolishGoal.FLIRTY.id)
        )

        ReplyStyleSettingsStore.save(context, profile)

        val loaded = ReplyStyleSettingsStore.load(context)

        assertEquals(ReplyStyleMode.QUICK_REPLY, loaded.mode)
        assertEquals(profile.personaConfig.id, loaded.personaConfig.id)
        assertEquals(profile.playbookConfig.id, loaded.playbookConfig.id)
        assertEquals(profile.polishGoalConfig.id, loaded.polishGoalConfig.id)
    }

    @Test
    fun load_falls_back_to_default_configs_when_saved_ids_are_missing() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PERSONA, "missing_persona")
            .putString(KEY_SCENE, "missing_scene")
            .putString(KEY_POLISH_GOAL, "missing_goal")
            .commit()

        val loaded = ReplyStyleSettingsStore.load(context)

        assertEquals(ReplyStyleCatalog.defaultPersonaConfig.id, loaded.personaConfig.id)
        assertEquals(ReplyStyleCatalog.defaultPlaybookConfig.id, loaded.playbookConfig.id)
        assertEquals(ReplyStyleCatalog.defaultPolishGoalConfig.id, loaded.polishGoalConfig.id)
    }

    private companion object {
        const val PREFS_NAME = "ai_replay_mate_reply_style"
        const val KEY_PERSONA = "persona"
        const val KEY_SCENE = "scene"
        const val KEY_POLISH_GOAL = "polish_goal"
    }
}
