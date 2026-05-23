package com.lonquanzj.aireplaymate.overlay

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayLongPressStyleMenuProbeTest {
    @Test(timeout = 10_000)
    fun longPress_onFloatingBubble_triggersStyleMenuCallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val longPressTriggered = AtomicBoolean(false)

        val bubble = ProbeBubbleView(context)
        val controller = OverlayFloatingBubbleController(
            context = context,
            mainHandler = Handler(Looper.getMainLooper()),
            windowManagerProvider = { null },
            onLongPress = { longPressTriggered.set(true) },
            isGeneratingCandidates = { false }
        )
        controller.attachDragHandler(
            view = bubble,
            params = WindowManager.LayoutParams().apply {
                x = 0
                y = 0
            }
        )

        instrumentation.runOnMainSync {
            val downAt = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(
                downAt,
                downAt,
                MotionEvent.ACTION_DOWN,
                40f,
                40f,
                0
            )
            bubble.dispatchTouchEvent(downEvent)
            downEvent.recycle()

            bubble.runScheduledLongPress()

            val upAt = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                upAt,
                upAt,
                MotionEvent.ACTION_UP,
                40f,
                40f,
                0
            )
            bubble.dispatchTouchEvent(upEvent)
            upEvent.recycle()
        }

        assertTrue("Expected long-press to trigger style menu callback", longPressTriggered.get())
    }

    private class ProbeBubbleView(context: android.content.Context) : View(context) {
        private var scheduledLongPress: Runnable? = null

        override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
            scheduledLongPress = action
            return true
        }

        override fun removeCallbacks(action: Runnable): Boolean {
            if (scheduledLongPress === action) {
                scheduledLongPress = null
                return true
            }
            return false
        }

        fun runScheduledLongPress() {
            val callback = scheduledLongPress
            scheduledLongPress = null
            callback?.run()
        }
    }
}
