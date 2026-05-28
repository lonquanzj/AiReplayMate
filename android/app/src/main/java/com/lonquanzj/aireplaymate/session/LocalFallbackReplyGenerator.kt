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
            ReplyPersona.FLIRT_EXPERT -> map { it.toFlirtExpertReply() }
            ReplyPersona.RELAXED_GUY -> map { it.toRelaxedGuyReply() }
            ReplyPersona.PUSH_PULL -> map { it.toPushPullReply() }
            ReplyPersona.GOOD_LISTENER -> map { it.toGoodListenerReply() }
            ReplyPersona.DATE_PLANNER -> map { it.toDatePlannerReply() }
            ReplyPersona.CONTRAST_FUN -> map { it.toContrastFunReply() }
            ReplyPersona.DOMINANT_CEO -> map { it.replace("可以啊", "可以").replace("好呀", "好").replace("慢慢", "按舒服的节奏") }
            ReplyPersona.MATURE_UNCLE -> map { it.replace("有点", "还挺").replace("晚点", "稍晚一点") }
            ReplyPersona.WARM_GENTLE -> this
            ReplyPersona.ABSTRACT_MODE -> map { it.toAbstractReply() }
            ReplyPersona.ZUOAN_MODE -> map { it.replace("可以啊", "可以啊").replace("不好意思", "").replace("麻烦", "").replace("谢谢", "") }
            ReplyPersona.TIEBA_MODE -> map { it.toTiebaReply() }
            ReplyPersona.LUXUN_MODE -> map { it.toLuxunReply() }
        }
    }

    private fun String.toRelaxedGuyReply(): String {
        return when {
            contains("确认") || contains("安排") -> "我先看下安排，不急，确认好了跟你说。"
            contains("晚点") || contains("稍后") -> "我晚点回你，先把手头这点收一下。"
            contains("可以") || contains("没问题") -> "可以啊，听起来还挺顺的，就这么来。"
            contains("好奇") || contains("想继续") -> "这我还真有点好奇了，你继续讲。"
            contains("舒服") || contains("慢慢") -> "那就慢慢聊，节奏舒服比啥都重要。"
            else -> "嗯，我懂你意思，这事可以慢慢展开。"
        }
    }

    private fun String.toPushPullReply(): String {
        return when {
            contains("确认") || contains("安排") -> "我先确认一下，别急，靠谱这事我还是在线的。"
            contains("晚点") || contains("稍后") -> "晚点回你，留点悬念也不是不行。"
            contains("可以") || contains("没问题") -> "可以，不过你这么说，我会合理怀疑你也有点期待。"
            contains("好奇") || contains("想继续") -> "想听可以，但你得继续说，这钩子放得有点会。"
            contains("舒服") || contains("慢慢") -> "那就慢慢来，太快反而少点意思。"
            else -> "你这句话有点会，我先接住，但不夸太多。"
        }
    }

    private fun String.toGoodListenerReply(): String {
        return when {
            contains("确认") || contains("安排") -> "我先认真看一下，确认清楚再回你，免得随便应付。"
            contains("晚点") || contains("稍后") -> "我晚点认真回你，不想随便糊弄过去。"
            contains("可以") || contains("没问题") -> "可以，我先接住你的意思，也想听听你怎么想。"
            contains("好奇") || contains("想继续") -> "我在听，你慢慢说，我还挺想知道后面怎么了。"
            contains("舒服") || contains("慢慢") -> "那就按你舒服的节奏来，不用急着说完。"
            else -> "我听懂一点了，你如果愿意，可以再多跟我说说。"
        }
    }

    private fun String.toDatePlannerReply(): String {
        return when {
            contains("确认") || contains("安排") -> "我先确认下安排，合适的话我们就定个轻松点的时间。"
            contains("晚点") || contains("稍后") -> "晚点我看下时间，要是方便，我们可以顺手把计划定了。"
            contains("可以") || contains("没问题") -> "可以，那我们找个都不赶的时间，轻松一点。"
            contains("好奇") || contains("想继续") -> "那下次可以边聊边试试，感觉会比只打字有意思。"
            contains("舒服") || contains("慢慢") -> "那就不急，等节奏舒服了再约也刚好。"
            else -> "这个可以继续聊聊，合适的话我们找个轻松点的机会见。"
        }
    }

    private fun String.toContrastFunReply(): String {
        return when {
            contains("确认") || contains("安排") -> "我先确认一下，表面冷静，内心已经开始排队处理了。"
            contains("晚点") || contains("稍后") -> "晚点回你，我现在像个加载中的进度条。"
            contains("可以") || contains("没问题") -> "可以，表面淡定，其实已经默默点头了。"
            contains("好奇") || contains("想继续") -> "你这话说一半，成功让我这个路人开始蹲后续。"
            contains("舒服") || contains("慢慢") -> "那就慢慢来，我主打一个看起来随意其实很认真。"
            else -> "懂了，我先假装淡定一下，其实已经有点想听后续。"
        }
    }

    private fun String.toFlirtExpertReply(): String {
        return when {
            contains("确认") || contains("安排") -> "我先确认一下，别急着催我，给你准话这件事我还是挺认真的。"
            contains("晚点") || contains("稍后") -> "晚点回你，但不是敷衍，是想把话说得更像我一点。"
            contains("可以") || contains("没问题") -> "可以啊，刚好我也想看看，和你碰上会不会比聊天更有意思。"
            contains("好奇") || contains("想继续") -> "你这么一说，我倒更想听下去了，别只说一半吊我胃口。"
            contains("舒服") || contains("慢慢") -> "那就慢慢聊，反正有意思的人，不急着一次聊完。"
            else -> "我懂你的意思，不过你这样说，我会忍不住想多问一句。"
        }
    }

    private fun String.toAbstractReply(): String {
        return when {
            contains("确认") || contains("安排") -> "收到，我先看一下安排，这波先不乱开香槟，确认好再回你。"
            contains("晚点") || contains("稍后") -> "我晚点回你，现在大脑还在加载，等它转完这个圈。"
            contains("可以") || contains("没问题") -> "可以，这事问题不大，先让它稳稳落地。"
            contains("好奇") || contains("想继续") -> "你这话题有点东西，已经把我的好奇心钓起来了。"
            contains("舒服") || contains("慢慢") -> "那就慢慢来，节奏对了，比硬冲有用多了。"
            else -> "懂了，这个展开有点意思，你继续说，我先把瓜子摆好。"
        }
    }

    private fun String.toTiebaReply(): String {
        return when {
            contains("确认") || contains("安排") -> "不是哥们，这事先别急，我看完给你个准话，别搁这儿先开香槟。"
            contains("晚点") || contains("稍后") -> "绷不住了，晚点我看完再回你，现在硬聊容易把事聊歪。"
            contains("可以") || contains("没问题") -> "可以，问题不大。有一说一，先按这个来，别整太复杂。"
            contains("好奇") || contains("想继续") -> "这话说的，我还真有点想听你接着展开，别只放个钩子就跑。"
            contains("舒服") || contains("慢慢") -> "有一说一，这节奏还行，先这么聊，别一上来就整高强度。"
            else -> "不是哥们，你这句我先接住了，但咱得讲点道理，别把天聊死。"
        }
    }

    private fun String.toLuxunReply(): String {
        return when {
            contains("确认") || contains("安排") -> "事情尚未看清，便急着要定论，像未点灯先称天亮。待我看完，再说准话。"
            contains("晚点") || contains("稍后") -> "急话常像冷茶，端得快，入口却淡。稍后看明白了，再回你。"
            contains("可以") || contains("没问题") -> "可以。若事情本来简单，倒不必再给它披一件复杂的外衣。"
            contains("好奇") || contains("想继续") -> "这话像半扇门，既然开了，就不必又站在门外装作路过。"
            contains("舒服") || contains("慢慢") -> "慢些也好。走得太急的人，常把路上的坑当成自己的方向。"
            else -> "这话我听见了。若要继续说，最好先把道理放在桌上，别只把声响敲得很亮。"
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
