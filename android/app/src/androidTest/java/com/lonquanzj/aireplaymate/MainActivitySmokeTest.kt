package com.lonquanzj.aireplaymate

import android.util.Log
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    private val tag = "MainActivitySmokeTest"

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 45_000)
    fun mainScreen_renders_key_entry_points() {
        assertTextVisibleWithin("AiReplayMate 基于当前微信上下文生成候选回复。")
        assertTextVisibleWithin("权限状态")

        openTab("LLM 设置")
        assertTextVisibleWithin("测试连接")

        openTab("回复风格")
        assertTextVisibleWithin("LLM 回复风格")
        assertTextVisibleWithin("Prompt 预览")

        openTab("高级调试")
        assertTextVisibleWithin("悬浮窗诊断")
        assertTextVisibleWithin("诊断日志")
    }

    private fun openTab(label: String) {
        Log.i(tag, "open tab: $label")
        composeRule.onNodeWithText(label).assertExists().performClick()
    }

    private fun assertTextVisibleWithin(text: String, timeoutMs: Long = 8_000L) {
        Log.i(tag, "wait text: $text")
        try {
            composeRule.waitUntil(timeoutMs) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(text).fetchSemanticsNode()
        } catch (t: Throwable) {
            composeRule.onRoot().printToLog(tag)
            fail("Smoke assertion timeout for text: $text, cause=${t.message}")
        }
    }
}
