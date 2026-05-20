package com.lonquanzj.aireplaymate.demo

enum class DemoAuthor {
    ME,
    FRIEND,
    SYSTEM
}

data class DemoMessage(
    val author: DemoAuthor,
    val content: String
)

data class DemoCandidate(
    val id: String,
    val text: String,
    val tone: String
)

enum class DemoStage(
    val title: String,
    val detail: String
) {
    IDLE("待机", "等待你触发一次完整演示"),
    VALIDATING_TARGET("校验目标页面", "确认当前处于微信单聊场景"),
    COLLECTING_ACCESSIBILITY("提取聊天上下文", "模拟通过 Accessibility 抽取可见消息"),
    REQUESTING_LLM("生成候选回复", "根据上下文生成 3 条可直接发送的回复"),
    CANDIDATE_READY("候选已就绪", "底部面板展示候选，等待用户选择"),
    AUTOFILLING("自动填入", "把选中的回复写入输入框"),
    DONE("演示完成", "主链路已经跑通，用户保留最终发送权")
}

data class DemoScenario(
    val id: String,
    val title: String,
    val subtitle: String,
    val note: String,
    val messages: List<DemoMessage>
) {
    fun candidates(round: Int): List<DemoCandidate> {
        val variant = round % 3
        return when (id) {
            "business_follow_up" -> businessCandidates(variant)
            "friend_invite" -> friendInviteCandidates(variant)
            "customer_confirm" -> customerConfirmCandidates(variant)
            else -> emptyList()
        }
    }

    private fun businessCandidates(variant: Int): List<DemoCandidate> {
        val texts = when (variant) {
            1 -> listOf(
                "收到，我这边今天下午把确认版发你，晚一点同步具体时间。",
                "没问题，我先整理一下内容，今天之内给你回复。",
                "可以的，我这边再核对一次，确认后第一时间回你。"
            )

            2 -> listOf(
                "好的，我这边继续推进，今天晚些时候给你一个明确答复。",
                "收到，我先把细节过一遍，确认后马上同步给你。",
                "可以，我这边尽快处理，处理完第一时间回复你。"
            )

            else -> listOf(
                "收到，我这边确认后今天内回复你。",
                "好的，我先核对一下，稍后给你明确时间。",
                "可以，我这边整理完马上同步给你。"
            )
        }

        return texts.mapIndexed { index, text ->
            DemoCandidate(
                id = "business_${variant}_${index}",
                text = text,
                tone = listOf("稳妥", "高效", "自然")[index]
            )
        }
    }

    private fun friendInviteCandidates(variant: Int): List<DemoCandidate> {
        val texts = when (variant) {
            1 -> listOf(
                "可以啊，我这边六点后差不多有空，到时候见。",
                "行，那我下班过去，到了给你发消息。",
                "没问题，晚上一起吃，你先定地方也行。"
            )

            2 -> listOf(
                "好呀，我今晚可以，晚点把时间对一下。",
                "可以，我这边忙完就过去，你先别等我点菜。",
                "行，今晚约起来，到时候我提前跟你说一声。"
            )

            else -> listOf(
                "可以，晚上我这边忙完过去。",
                "好啊，你定地方，我到了跟你说。",
                "没问题，晚点见，到时候联系。"
            )
        }

        return texts.mapIndexed { index, text ->
            DemoCandidate(
                id = "friend_${variant}_${index}",
                text = text,
                tone = listOf("轻松", "熟络", "干脆")[index]
            )
        }
    }

    private fun customerConfirmCandidates(variant: Int): List<DemoCandidate> {
        val texts = when (variant) {
            1 -> listOf(
                "已经收到，我这边帮您安排，稍后把确认信息发给您。",
                "好的，这边记录好了，处理完成后第一时间通知您。",
                "没问题，我先帮您确认一下，稍后回复您结果。"
            )

            2 -> listOf(
                "收到，这边马上为您处理，稍后同步进度给您。",
                "好的，您的需求已经记下，我确认后尽快回复您。",
                "可以的，我先核实安排，稍后把结果发您。"
            )

            else -> listOf(
                "收到，我这边先帮您确认，稍后回复您。",
                "好的，已经记下了，处理完第一时间通知您。",
                "没问题，我先安排一下，稍后同步给您。"
            )
        }

        return texts.mapIndexed { index, text ->
            DemoCandidate(
                id = "customer_${variant}_${index}",
                text = text,
                tone = listOf("礼貌", "专业", "亲和")[index]
            )
        }
    }
}

fun demoScenarios(): List<DemoScenario> = listOf(
    DemoScenario(
        id = "business_follow_up",
        title = "商务跟进",
        subtitle = "适合演示稳妥、职业的回复风格",
        note = "对方催确认进度，系统生成简洁但不失分寸的回复。",
        messages = listOf(
            DemoMessage(DemoAuthor.ME, "我先看一下你那边的方案，晚点给你答复。"),
            DemoMessage(DemoAuthor.FRIEND, "好，那你看完告诉我，我们今天最好定下来。"),
            DemoMessage(DemoAuthor.FRIEND, "你那边现在方便确认下时间吗？")
        )
    ),
    DemoScenario(
        id = "friend_invite",
        title = "朋友邀约",
        subtitle = "适合演示自然、口语化的回复",
        note = "朋友约晚饭，候选会偏轻松一些，不会太正式。",
        messages = listOf(
            DemoMessage(DemoAuthor.FRIEND, "今晚有空吗？一起吃个饭。"),
            DemoMessage(DemoAuthor.ME, "应该可以，我下午看看安排。"),
            DemoMessage(DemoAuthor.FRIEND, "那你忙完跟我说，我先看看去哪。")
        )
    ),
    DemoScenario(
        id = "customer_confirm",
        title = "客户确认",
        subtitle = "适合演示客服/运营场景",
        note = "用户在确认需求，候选会偏礼貌与可执行。",
        messages = listOf(
            DemoMessage(DemoAuthor.FRIEND, "好的，那麻烦帮我登记一下，明天能安排吗？"),
            DemoMessage(DemoAuthor.ME, "可以，我这边先核对一下库存。"),
            DemoMessage(DemoAuthor.FRIEND, "行，确认后直接告诉我就好。")
        )
    )
)
