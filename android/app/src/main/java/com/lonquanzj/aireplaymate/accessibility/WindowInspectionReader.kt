package com.lonquanzj.aireplaymate.accessibility

internal fun ReplyAccessibilityService.inspectCurrentWindowSnapshot(): WindowInspectionSnapshot {
    val root = rootInActiveWindow
    if (root == null) {
        return WindowInspectionSnapshot(
            success = false,
            state = AccessibilityDebugStore.state.value.copy(
                serviceConnected = true,
                updatedAtMillis = System.currentTimeMillis()
            ),
            message = "当前窗口为空，暂时无法刷新"
        )
    }

    val packageName = root.packageName?.toString().orEmpty()
    val className = root.className?.toString().orEmpty()
    val snapshot = WeChatAccessibilityAnalyzer.inspectWindow(
        packageName = packageName,
        root = root
    )
    val inspection = snapshot.inspection
    val now = System.currentTimeMillis()
    val state = AccessibilityDebugState(
        serviceConnected = true,
        lastEventName = "主动刷新",
        packageName = packageName,
        className = className,
        editableNodeCount = snapshot.editableNodeCount,
        visibleTextSample = snapshot.visibleTextSample,
        isWechatPackage = packageName == WECHAT_PACKAGE_NAME,
        looksLikeChatPage = inspection.looksLikeChatPage,
        chatDetectionReason = inspection.reason,
        conversationTitle = inspection.conversationTitle,
        inputNodeFound = inspection.inputNodeFound,
        inputNodeHint = inspection.inputNodeHint,
        extractedMessages = inspection.extractedMessages,
        lastAutofillStatus = AccessibilityDebugStore.state.value.lastAutofillStatus,
        lastAutofillCategory = AccessibilityDebugStore.state.value.lastAutofillCategory,
        lastAutofillSteps = AccessibilityDebugStore.state.value.lastAutofillSteps,
        lastAutofillPreview = AccessibilityDebugStore.state.value.lastAutofillPreview,
        lastFullInspectionAtMillis = now,
        updatedAtMillis = now
    )

    AccessibilityDebugStore.setState(state)
    return WindowInspectionSnapshot(
        success = true,
        state = state,
        message = "已刷新当前窗口"
    )
}
