package com.lonquanzj.aireplaymate.accessibility

internal fun ReplyAccessibilityService.readCurrentInputDraft(): InputDraftReadResult {
    val steps = mutableListOf<String>()
    val root = rootInActiveWindow
    if (root == null) {
        return InputDraftReadResult(
            success = false,
            message = "当前窗口为空，无法读取输入框",
            category = AutofillFailureCategory.EMPTY_WINDOW,
            steps = steps + "窗口获取：失败，rootInActiveWindow 为空"
        )
    }
    steps += "窗口获取：通过"

    val inputNode = WeChatAccessibilityAnalyzer.findChatInputNode(root)
    if (inputNode == null) {
        return InputDraftReadResult(
            success = false,
            message = "未找到可用输入框",
            category = AutofillFailureCategory.INPUT_NOT_FOUND,
            steps = steps + "输入框定位：失败"
        )
    }
    steps += "输入框定位：通过"

    inputNode.refresh()
    val draft = inputNode.text?.toString()?.trim().orEmpty()
    if (draft.isBlank()) {
        return InputDraftReadResult(
            success = false,
            message = "输入框里还没有可润色的文字",
            category = AutofillFailureCategory.EMPTY_TEXT,
            steps = steps + "草稿读取：为空"
        )
    }

    return InputDraftReadResult(
        success = true,
        text = draft,
        message = "已读取输入框草稿",
        category = AutofillFailureCategory.NONE,
        steps = steps + "草稿读取：成功，长度 ${draft.length}"
    )
}
