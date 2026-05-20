package com.lonquanzj.aireplaymate.session

import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.context.ChatContext
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate

object LocalFallbackReplyGenerator {
    fun generate(
        context: ChatContext,
        seed: String = context.messages.joinToString("|") { it.content }
    ): List<ReplyCandidate> {
        val lastFriendMessage = context.messages.lastOrNull { it.role == ChatRole.FRIEND }?.content
        val message = lastFriendMessage.orEmpty()
        val texts = when {
            message.looksLikeServiceRequest() -> listOf(
                "收到，我这边先帮您确认，稍后回复您。",
                "好的，已经记下了，处理完第一时间通知您。",
                "没问题，我先核实安排，稍后把结果发您。"
            )

            message.looksLikeInvite() -> listOf(
                "可以，晚上我这边忙完过去。",
                "好啊，你定地方，我到了跟你说。",
                "没问题，晚点见，到时候联系。"
            )

            message.containsTimeIntent() -> listOf(
                "我这边晚点确认时间后同步你。",
                "可以，我先看下安排，稍后告诉你具体时间。",
                "收到，我确认好时间马上回复。"
            )

            message.containsQuestionIntent() -> listOf(
                "可以的，我这边确认后马上回复你。",
                "我先看一下，稍后给你明确答复。",
                "没问题，我处理完第一时间告诉你。"
            )

            else -> listOf(
                "收到，我这边先确认一下，稍后回复你。",
                "好的，我看完后马上同步给你。",
                "没问题，我这边处理完第一时间说。"
            )
        }

        val requestHash = seed.hashCode().toUInt().toString(16)
        return texts.mapIndexed { index, text ->
            ReplyCandidate(
                id = "local_${requestHash}_$index",
                text = text,
                tone = DEFAULT_TONES[index],
                sourceModel = null,
                rank = index + 1
            )
        }
    }

    private fun String.containsQuestionIntent(): Boolean {
        return contains("吗") || contains("?") || contains("？") || contains("能不能") || contains("可以")
    }

    private fun String.containsTimeIntent(): Boolean {
        return contains("时间") || contains("几点") || contains("今晚") || contains("明天") || contains("下午")
    }

    private fun String.looksLikeInvite(): Boolean {
        return contains("吃饭") || contains("约") || contains("有空") || contains("见")
    }

    private fun String.looksLikeServiceRequest(): Boolean {
        return contains("您") || contains("麻烦") || contains("登记") || contains("安排") || contains("需求")
    }

    private val DEFAULT_TONES = listOf("稳妥", "自然", "简洁")
}
