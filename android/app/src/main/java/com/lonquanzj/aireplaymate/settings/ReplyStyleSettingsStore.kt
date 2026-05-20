package com.lonquanzj.aireplaymate.settings

import android.content.Context
import com.lonquanzj.aireplaymate.prompt.PolishGoal
import com.lonquanzj.aireplaymate.prompt.ReplyPersona
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
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
        return ReplyStyleCatalog.profile(
            modeId = ReplyStyleMode.QUICK_REPLY.id,
            personaId = prefs.getString(KEY_PERSONA, ReplyPersona.default.id),
            sceneId = prefs.getString(KEY_SCENE, ReplyStyleCatalog.defaultScene.sceneId),
            polishGoalId = prefs.getString(KEY_POLISH_GOAL, PolishGoal.default.id)
        )
    }

    fun save(
        context: Context,
        profile: ReplyStyleProfile
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, ReplyStyleMode.QUICK_REPLY.id)
            .putString(KEY_PERSONA, profile.persona.id)
            .putString(KEY_SCENE, profile.playbookScene?.sceneId ?: ReplyStyleCatalog.defaultScene.sceneId)
            .putString(KEY_POLISH_GOAL, profile.polishGoal.id)
            .apply()
    }
}
