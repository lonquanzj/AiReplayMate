package com.lonquanzj.aireplaymate.settings

import android.app.backup.BackupManager
import android.content.Context
import com.lonquanzj.aireplaymate.prompt.PolishGoal
import com.lonquanzj.aireplaymate.prompt.ReplyPersona
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile

object ReplyStyleSettingsStore {
    private const val PREFS_NAME = "ai_replay_mate_reply_style"
    private const val KEY_MODE = "mode"
    private const val KEY_PERSONA = "persona"
    private const val KEY_SCENE = "scene"
    private const val KEY_POLISH_GOAL = "polish_goal"

    fun load(context: Context): ReplyStyleProfile {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val catalog = ReplyStyleCatalogStore.load(context)
        return ReplyStyleCatalog.profile(
            modeId = ReplyStyleMode.QUICK_REPLY.id,
            personaId = prefs.getString(KEY_PERSONA, ReplyPersona.default.id),
            sceneId = prefs.getString(KEY_SCENE, ReplyStyleCatalog.defaultScene.sceneId),
            polishGoalId = prefs.getString(KEY_POLISH_GOAL, PolishGoal.default.id),
            catalog = catalog
        )
    }

    fun save(
        context: Context,
        profile: ReplyStyleProfile
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, ReplyStyleMode.QUICK_REPLY.id)
            .putString(KEY_PERSONA, profile.personaConfig.id)
            .putString(KEY_SCENE, profile.playbookConfig.id)
            .putString(KEY_POLISH_GOAL, profile.polishGoalConfig.id)
            .apply()
        BackupManager(context.applicationContext).dataChanged()
    }

    fun defaultProfile(catalog: ReplyStyleCatalogState): ReplyStyleProfile {
        return profileFromIds(
            modeId = ReplyStyleMode.QUICK_REPLY.id,
            personaId = ReplyPersona.default.id,
            sceneId = ReplyStyleCatalog.defaultScene.sceneId,
            polishGoalId = PolishGoal.default.id,
            catalog = catalog
        )
    }

    fun profileFromIds(
        modeId: String?,
        personaId: String?,
        sceneId: String?,
        polishGoalId: String?,
        catalog: ReplyStyleCatalogState
    ): ReplyStyleProfile {
        return ReplyStyleCatalog.profile(
            modeId = modeId ?: ReplyStyleMode.QUICK_REPLY.id,
            personaId = personaId ?: ReplyPersona.default.id,
            sceneId = sceneId ?: ReplyStyleCatalog.defaultScene.sceneId,
            polishGoalId = polishGoalId ?: PolishGoal.default.id,
            catalog = catalog
        )
    }
}
