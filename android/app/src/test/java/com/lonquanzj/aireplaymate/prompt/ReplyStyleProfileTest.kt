package com.lonquanzj.aireplaymate.prompt

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyStyleProfileTest {
    @Test
    fun candidatePanelLabel_usesPersonaAndCurrentQuickReplyPersona() {
        val profile = ReplyStyleProfile(
            mode = ReplyStyleMode.QUICK_REPLY,
            personaConfig = persona("Flirt Expert")
        )

        assertEquals("Flirt Expert · 快速回复", profile.candidatePanelLabel)
    }

    @Test
    fun candidatePanelLabel_usesPersonaAndPlaybookLabel() {
        val profile = ReplyStyleProfile(
            mode = ReplyStyleMode.PLAYBOOK,
            personaConfig = persona("Flirt Expert"),
            playbookConfig = playbook("Good Morning")
        )

        assertEquals("Flirt Expert · Good Morning", profile.candidatePanelLabel)
    }

    @Test
    fun candidatePanelLabel_usesPersonaAndPolishGoalLabel() {
        val profile = ReplyStyleProfile(
            mode = ReplyStyleMode.POLISH,
            personaConfig = persona("Flirt Expert"),
            polishGoalConfig = polishGoal("Natural")
        )

        assertEquals("Flirt Expert · Natural", profile.candidatePanelLabel)
    }

    private fun persona(label: String) = ReplyPersonaConfig(
        id = label.lowercase().replace(" ", "_"),
        label = label,
        identityPrompt = "",
        promptGuide = "",
        isBuiltin = false
    )

    private fun playbook(label: String) = ReplyPlaybookConfig(
        id = label.lowercase().replace(" ", "_"),
        categoryLabel = "Category",
        label = label,
        identityPrompt = "",
        promptGuide = "",
        isBuiltin = false
    )

    private fun polishGoal(label: String) = PolishGoalConfig(
        id = label.lowercase().replace(" ", "_"),
        label = label,
        identityPrompt = "",
        promptGuide = "",
        isBuiltin = false
    )
}
