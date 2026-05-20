package com.lonquanzj.aireplaymate

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreen_renders_key_entry_points() {
        composeRule.onNodeWithText("AiReplayMate Demo").fetchSemanticsNode()
        composeRule.onNodeWithText("权限状态").fetchSemanticsNode()
        composeRule.onNodeWithText("测试连接").fetchSemanticsNode()
        composeRule.onNodeWithText("LLM 回复风格").fetchSemanticsNode()
        composeRule.onNodeWithText("悬浮窗诊断").fetchSemanticsNode()
        composeRule.onNodeWithText("诊断日志").fetchSemanticsNode()
    }
}
