package com.lonquanzj.aireplaymate

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    private val tag = "MainActivitySmokeTest"

    private val smokeModeRule = object : ExternalResource() {
        override fun before() {
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            targetContext
                .getSharedPreferences("smoke_test_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("enabled", true)
                .commit()
        }

        override fun after() {
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            targetContext
                .getSharedPreferences("smoke_test_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(smokeModeRule)
        .around(composeRule as TestRule)

    @Test(timeout = 45_000)
    fun mainScreen_renders_key_entry_points() {
        assertTextVisibleWithin("AiReplayMate")
        assertTextVisibleWithin("权限状态")
        assertTextVisibleWithin("测试连接")
        assertTextVisibleWithin("LLM 回复风格")
        assertTextVisibleWithin("测试 Prompt")
        assertTextVisibleWithin("编辑当前")
        assertTextVisibleWithin("悬浮窗诊断")
        assertTextVisibleWithin("诊断日志")
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
