package com.lonquanzj.aireplaymate.ocr

internal val chromeTexts = setOf(
    "微信",
    "发送",
    "收起",
    "全部",
    "按住 说话",
    "按住说话",
    "语音输入",
    "表情",
    "更多",
    "相册",
    "拍摄",
    "位置",
    "红包",
    "转账",
    "收藏",
    "聊天信息"
)
internal val chromeTextFragments = listOf(
    "角色·",
    "话术·",
    "润色·",
    "悬浮窗诊断",
    "AI 正在工作",
    "OCR Filter Summary",
    "OCR Messages",
    "打开无障碍设置",
    "正在输入",
    "条新消息",
    "以上是打招呼",
    "以下是新消息",
    "轻触输入",
    "切换到键盘"
)
internal val systemNoticeFragments = listOf(
    "撤回了一条消息",
    "撒回了一条消息"
)
internal val whitespaceRegex = Regex("\\s+")
internal val timeRegex = Regex("""^((凌晨|早上|上午|中午|下午|晚上)\s*)?\d{1,2}[:：]\d{2}$""")
internal val shortTimestampWithSymbolRegex = Regex("""^((凌晨|早上|上午|中午|下午|晚上)\s*)?\d{1,2}[:：]\d{2}[\s\p{P}\p{S}]+$""")
internal val dateRegex = Regex("""^(\d{1,2}月\d{1,2}日|星期[一二三四五六日天]|昨天|今天|周[一二三四五六日天]).*$""")
internal val allPunctuationRegex = Regex("""^[\p{P}\p{S}\s]+$""")
internal val unreadBadgeRegex = Regex("""^\d{1,3}$""")
internal val withdrawNoticeRegex = Regex("""[你我他她它对方]?.?[撤撒]回了?一条消息""")
internal val cjkRegex = Regex("""[\u4E00-\u9FFF]""")

internal const val MIN_TEXT_LENGTH = 2
internal const val MAX_TEXT_LENGTH = 120
internal const val MAX_SHORT_NOISE_LENGTH = 12
internal const val MAX_OCR_MESSAGES = 20
internal const val MAX_FILTER_SAMPLES_PER_REASON = 3
internal const val MAX_FILTER_SAMPLE_LENGTH = 24
internal const val TOP_CHROME_RATIO = 0.10f
internal const val BOTTOM_INPUT_RATIO = 0.84f
internal const val MAX_LINE_WIDTH_RATIO = 0.86f
internal const val FRIEND_CENTER_RATIO = 0.46f
internal const val ME_CENTER_RATIO = 0.54f
internal const val MAX_BUBBLE_LINE_GAP_RATIO = 0.018f
internal const val ROLE_CONFIDENCE = 0.62f
internal const val UNKNOWN_ROLE_CONFIDENCE = 0.48f
