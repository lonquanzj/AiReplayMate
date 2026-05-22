package com.lonquanzj.aireplaymate

import android.util.Log
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
class MainActivityUiEntryTest {
    private val tag = "MainActivityUiEntryTest"
    private val stepTimeoutMs = 5_000L

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 30_000)
    fun mainScreen_renders_key_entry_points() {
        val startedAt = System.currentTimeMillis()
        val finishedStages = mutableListOf<String>()

        fun runStage(name: String, block: () -> Unit) {
            Log.i(tag, "stage begin: $name")
            try {
                block()
                finishedStages += name
                val elapsed = System.currentTimeMillis() - startedAt
                Log.i(tag, "stage done: $name elapsed=${elapsed}ms")
            } catch (t: Throwable) {
                val elapsed = System.currentTimeMillis() - startedAt
                composeRule.onRoot().printToLog(tag)
                fail(
                    "UI entry diagnostic failed at stage '$name' after ${elapsed}ms; " +
                        "finished=${finishedStages.joinToString(",")}; cause=${t.message}"
                )
            }
        }

        runStage("main-title") {
            assertTextVisibleWithin("AiReplayMate 基于当前微信上下文生成候选回复。")
        }
        runStage("main-permission") {
            assertTextVisibleWithin("权限状态")
        }

        runStage("tab-llm-open") {
            openTab("LLM 设置")
        }
        runStage("tab-llm-check") {
            assertTextVisibleWithin("测试连接")
        }

        runStage("tab-style-open") {
            openTab("回复风格")
        }
        runStage("tab-style-check") {
            assertTextVisibleWithin("LLM 回复风格")
            assertTextVisibleWithin("Prompt 预览")
        }

        runStage("tab-advanced-open") {
            openTab("高级调试")
        }
        runStage("tab-advanced-check") {
            assertTextVisibleWithin("悬浮窗诊断")
            assertTextVisibleWithin("诊断日志")
        }
    }

    private fun openTab(label: String) {
        Log.i(tag, "open tab: $label")
        assertTextVisibleWithin(label)
        composeRule.onNodeWithText(label).performClick()
    }

    private fun assertTextVisibleWithin(text: String, timeoutMs: Long = stepTimeoutMs) {
        Log.i(tag, "wait text: $text")
        try {
            composeRule.waitUntil(timeoutMs) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(text).fetchSemanticsNode()
        } catch (t: Throwable) {
            throw AssertionError("Text not visible within ${timeoutMs}ms: '$text'", t)
        }
    }
}
