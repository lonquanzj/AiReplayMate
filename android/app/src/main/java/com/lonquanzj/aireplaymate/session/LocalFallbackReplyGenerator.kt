package com.lonquanzj.aireplaymate.session

import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.context.ChatContext
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import com.lonquanzj.aireplaymate.prompt.ReplyPersona
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile

object LocalFallbackReplyGenerator {
    fun generate(
        context: ChatContext,
        styleProfile: ReplyStyleProfile = ReplyStyleProfile(),
        seed: String = context.messages.joinToString("|") { it.content },
        draftText: String? = null
    ): List<ReplyCandidate> {
        val lastFriendMessage = context.messages.lastOrNull { it.role == ChatRole.FRIEND }?.content
        val message = lastFriendMessage.orEmpty()
        val texts = buildTexts(message, styleProfile, draftText)

        val requestHash = "$seed:${styleProfile.displayLabel}".hashCode().toUInt().toString(16)
        return texts.mapIndexed { index, text ->
            ReplyCandidate(
                id = "local_${requestHash}_$index",
                text = text,
                tone = buildTone(styleProfile, index),
                sourceModel = null,
                rank = index + 1
            )
        }
    }

    private fun buildTexts(
        message: String,
        styleProfile: ReplyStyleProfile,
        draftText: String?
    ): List<String> {
        return when (styleProfile.mode) {
            ReplyStyleMode.PLAYBOOK -> buildPlaybookTexts(styleProfile)
            ReplyStyleMode.POLISH -> buildPolishTexts(styleProfile, draftText.orEmpty())
            ReplyStyleMode.QUICK_REPLY -> buildQuickReplyTexts(message, styleProfile.persona)
        }
    }

    private fun buildPlaybookTexts(styleProfile: ReplyStyleProfile): List<String> {
        return when (styleProfile.playbookScene?.sceneId) {
            "blind_date_warmup" -> listOf(
                "刚好也想多了解你一点，你平时周末更喜欢宅着还是出去走走？",
                "感觉你说话挺舒服的，我们可以慢慢聊，不用太正式。",
                "那我先认真报个到：今天心情不错，因为遇到个挺想了解的人。"
            )

            "flirty_probe" -> listOf(
                "你这么说，我会合理怀疑你是在给我机会靠近一点。",
                "这句话有点犯规，我差点就当成你在撩我了。",
                "那我浅浅理解一下，你是不是也有一点想继续聊我？"
            )

            "good_morning" -> listOf(
                "早呀，今天也要顺顺利利，记得先好好吃点东西。",
                "早安，看到你的消息，今天开局还挺不错。",
                "醒了吗？希望你今天遇到的都是让人舒服的小事。"
            )

            "good_night" -> listOf(
                "晚安，今天辛苦啦，剩下的事交给明天也没关系。",
                "早点休息，梦里如果路过我，记得打个招呼。",
                "晚安，愿你今晚睡得踏实一点，明天轻松一点。"
            )

            "daily_greeting" -> listOf(
                "今天过得怎么样？有没有哪一刻是让你觉得还不错的？",
                "忙完了吗？突然想看看你今天有没有好好照顾自己。",
                "来查收一个不打扰版关心：今天还顺利吗？"
            )

            "push_pull" -> listOf(
                "你这句话挺会的，不过我先不夸太多，免得你骄傲。",
                "有点想接近你，但我得保持一点神秘感。",
                "你再这样聊下去，我可能就没那么好哄自己冷静了。"
            )

            "sweet_flirty" -> listOf(
                "你知道吗，你刚刚那句话有点让我心软。",
                "和你聊天有个问题，就是时间会过得太快。",
                "我本来只是想回消息，结果又多想了你一会儿。"
            )

            "personality" -> listOf(
                "感觉你是那种外表挺淡定，其实心里很有自己节奏的人。",
                "你这个反应还挺可爱的，有点认真，又不失有趣。",
                "我发现你说话有自己的小逻辑，这点还挺吸引人的。"
            )

            "life_share" -> listOf(
                "我还挺喜欢听你讲这些日常的，有种慢慢靠近真实生活的感觉。",
                "那你今天这个片段我记下了，感觉还挺有画面。",
                "听起来你的生活还挺有意思的，下次多给我讲一点。"
            )

            "hobbies" -> listOf(
                "这个兴趣还挺加分的，你一般是自己玩，还是会拉朋友一起？",
                "听起来你是真的喜欢，不是随便说说的那种。",
                "那我有点好奇了，你入坑这个是因为什么？"
            )

            "food_preference" -> listOf(
                "这个口味我记下了，以后选吃的可以少踩一个雷。",
                "你这个偏好还挺鲜明的，下次聊吃的我得认真做笔记。",
                "那看来你对好吃的挺有判断力，我有点想听你的推荐。"
            )

            else -> buildQuickReplyTexts("", styleProfile.persona)
        }.adjustForPersona(styleProfile.persona)
    }

    private fun buildPolishTexts(
        styleProfile: ReplyStyleProfile,
        draftText: String
    ): List<String> {
        if (draftText.isNotBlank()) {
            return when (styleProfile.polishGoal.id) {
                "flirty" -> listOf(
                    "$draftText，我还挺想认真听你继续说的。",
                    "$draftText，说真的，和你这样聊我会有点心动。",
                    "$draftText，感觉这句话由你说出来还挺犯规。"
                )

                "safe" -> listOf(
                    "$draftText，我想表达得稳一点，也想尊重你的感受。",
                    "$draftText，我们可以按舒服的节奏来，不急着下结论。",
                    "$draftText，我先把话说清楚，也给彼此留点空间。"
                )

                else -> listOf(
                    draftText,
                    draftText.replace("吗？", "呀？").replace("吗", "呀"),
                    "$draftText，我是认真想这样表达的。"
                )
            }.map { it.trim() }.adjustForPersona(styleProfile.persona)
        }
        return when (styleProfile.polishGoal.id) {
            "flirty" -> listOf(
                "你这么说，我会有点忍不住想多靠近你一点。",
                "本来想正常回复你的，但看到你消息还是会嘴角上扬。",
                "那我就不装淡定了，和你聊天确实挺容易开心。"
            )

            "safe" -> listOf(
                "我明白你的意思，我们可以慢慢来，不急着下结论。",
                "这样说我会更放心一点，也想听听你真实的想法。",
                "我先认真接住你的话，后面我们再自然一点聊。"
            )

            else -> listOf(
                "我懂你的意思，这样聊起来还挺舒服的。",
                "你这么一说，我也挺想继续听你讲下去。",
                "那我们就按舒服的节奏慢慢聊，不用太刻意。"
            )
        }.adjustForPersona(styleProfile.persona)
    }

    private fun buildQuickReplyTexts(
        message: String,
        persona: ReplyPersona
    ): List<String> {
        val texts = when {
            message.looksLikeServiceRequest() -> listOf(
                "收到，我先帮你确认一下，晚点给你准话。",
                "好，我这边先记下，处理完第一时间跟你说。",
                "没问题，我先看下情况，稍后回复你。"
            )

            message.looksLikeInvite() -> listOf(
                "可以啊，我这边忙完就过去，到时候跟你说。",
                "好呀，你先看看地方，我晚点跟你确认。",
                "没问题，晚点见，到时候联系。"
            )

            message.containsTimeIntent() -> listOf(
                "我晚点确认下时间，再跟你说具体安排。",
                "可以，我先看下安排，稍后告诉你。",
                "收到，我确认好时间就马上回复你。"
            )

            message.containsQuestionIntent() -> listOf(
                "可以的，我先确认一下，再给你明确答复。",
                "我看一下，晚点认真回你。",
                "没问题，我处理完第一时间告诉你。"
            )

            else -> listOf(
                "嗯嗯，我懂你的意思，想继续听你说。",
                "这样啊，那我还挺好奇你当时怎么想的。",
                "收到，那我们慢慢聊，不用太赶。"
            )
        }
        return texts.adjustForPersona(persona)
    }

    private fun List<String>.adjustForPersona(persona: ReplyPersona): List<String> {
        return when (persona) {
            ReplyPersona.ROMANCE_MASTER -> map { it.replace("我先", "我倒想先").replace("收到，", "行，") }
            ReplyPersona.DOMINANT_CEO -> map { it.replace("可以啊", "可以").replace("好呀", "好").replace("慢慢", "按舒服的节奏") }
            ReplyPersona.MATURE_UNCLE -> map { it.replace("有点", "还挺").replace("晚点", "稍晚一点") }
            ReplyPersona.WARM_GENTLE -> this
            ReplyPersona.ZUOAN_MODE -> map { it.replace("可以啊", "可以啊").replace("不好意思", "").replace("麻烦", "").replace("谢谢", "") }
        }
    }

    private fun buildTone(
        styleProfile: ReplyStyleProfile,
        index: Int
    ): String {
        val suffix = DEFAULT_TONES[index % DEFAULT_TONES.size]
        return "${styleProfile.shortLabel} · $suffix"
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
