package com.lonquanzj.aireplaymate.session

import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.chatContext
import com.lonquanzj.aireplaymate.chatMessage
import com.lonquanzj.aireplaymate.prompt.PolishGoal
import com.lonquanzj.aireplaymate.prompt.ReplyPersona
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFallbackReplyGeneratorTest {
    @Test
    fun generate_forQuickReply_applies_persona_adjustment_and_stable_ids() {
        val context = chatContext(
            listOf(
                chatMessage(
                    id = "m1",
                    role = ChatRole.FRIEND,
                    content = "麻烦帮我安排一下"
                )
            )
        )
        val style = ReplyStyleProfile(
            mode = ReplyStyleMode.QUICK_REPLY,
            persona = ReplyPersona.ROMANCE_MASTER
        )

        val first = LocalFallbackReplyGenerator.generate(context, styleProfile = style, seed = "stable-seed")
        val second = LocalFallbackReplyGenerator.generate(context, styleProfile = style, seed = "stable-seed")

        assertEquals(first.map { it.id }, second.map { it.id })
        assertTrue(first.first().text.startsWith("行，我倒想先帮你确认一下"))
    }

    @Test
    fun generate_forPlaybook_uses_scene_specific_texts() {
        val candidates = LocalFallbackReplyGenerator.generate(
            context = chatContext(emptyList()),
            styleProfile = ReplyStyleProfile(
                mode = ReplyStyleMode.PLAYBOOK,
                persona = ReplyPersona.WARM_GENTLE,
                playbookScene = ReplyStyleCatalog.sceneFromId("good_night")
            )
        )

        assertEquals(3, candidates.size)
        assertTrue(candidates.any { it.text.contains("晚安") })
    }

    @Test
    fun generate_forPolish_prefers_draft_when_available() {
        val candidates = LocalFallbackReplyGenerator.generate(
            context = chatContext(emptyList()),
            styleProfile = ReplyStyleProfile(
                mode = ReplyStyleMode.POLISH,
                persona = ReplyPersona.WARM_GENTLE,
                polishGoal = PolishGoal.SAFE
            ),
            draftText = "我到家了"
        )

        assertEquals(3, candidates.size)
        assertTrue(candidates.all { it.text.contains("我到家了") })
    }
}
