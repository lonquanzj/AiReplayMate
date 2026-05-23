package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class OverlayFloatingBubbleControllerTouchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun dragBeyondSlop_cancelsLongPressCallback() {
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

        dispatchEvent(bubble, MotionEvent.ACTION_DOWN, 10f, 10f)
        dispatchEvent(bubble, MotionEvent.ACTION_MOVE, 40f, 10f)
        bubble.runScheduledLongPress()

        assertFalse("Expected drag gesture to cancel long-press callback", longPressTriggered.get())
    }

    @Test
    fun tapWithoutMove_triggersClickWithoutLongPress() {
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

        dispatchEvent(bubble, MotionEvent.ACTION_DOWN, 24f, 24f)
        dispatchEvent(bubble, MotionEvent.ACTION_UP, 24f, 24f)

        assertFalse("Expected tap not to trigger long-press callback", longPressTriggered.get())
        assertEquals("Expected tap to trigger click callback exactly once", 1, bubble.clickCount)
    }

    @Test
    fun actionCancel_removesScheduledLongPress() {
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

        dispatchEvent(bubble, MotionEvent.ACTION_DOWN, 18f, 18f)
        dispatchEvent(bubble, MotionEvent.ACTION_CANCEL, 18f, 18f)
        bubble.runScheduledLongPress()

        assertFalse("Expected cancel event to remove pending long-press callback", longPressTriggered.get())
    }

    @Test
    fun dragRelease_docksWhenNotGenerating() {
        val bubble = ProbeBubbleView(context)
        val controller = OverlayFloatingBubbleController(
            context = context,
            mainHandler = Handler(Looper.getMainLooper()),
            windowManagerProvider = { null },
            onLongPress = {},
            isGeneratingCandidates = { false }
        )
        controller.attachDragHandler(
            view = bubble,
            params = WindowManager.LayoutParams().apply {
                width = 96
                height = 96
                x = 0
                y = 0
            }
        )

        dispatchEvent(bubble, MotionEvent.ACTION_DOWN, 20f, 20f)
        dispatchEvent(bubble, MotionEvent.ACTION_MOVE, 80f, 20f)
        dispatchEvent(bubble, MotionEvent.ACTION_UP, 80f, 20f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))

        assertTrue("Expected drag release to dock bubble when generation is idle", controller.isDocked)
    }

    @Test
    fun dragRelease_doesNotDockWhenGenerating() {
        val bubble = ProbeBubbleView(context)
        val controller = OverlayFloatingBubbleController(
            context = context,
            mainHandler = Handler(Looper.getMainLooper()),
            windowManagerProvider = { null },
            onLongPress = {},
            isGeneratingCandidates = { true }
        )
        controller.attachDragHandler(
            view = bubble,
            params = WindowManager.LayoutParams().apply {
                width = 96
                height = 96
                x = 0
                y = 0
            }
        )

        dispatchEvent(bubble, MotionEvent.ACTION_DOWN, 20f, 20f)
        dispatchEvent(bubble, MotionEvent.ACTION_MOVE, 80f, 20f)
        dispatchEvent(bubble, MotionEvent.ACTION_UP, 80f, 20f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))

        assertFalse("Expected drag release not to dock bubble while generating", controller.isDocked)
    }

    @Test
    fun longPressThenRelease_doesNotTriggerClick() {
        val longPressCount = AtomicInteger(0)
        val bubble = ProbeBubbleView(context)
        val controller = OverlayFloatingBubbleController(
            context = context,
            mainHandler = Handler(Looper.getMainLooper()),
            windowManagerProvider = { null },
            onLongPress = { longPressCount.incrementAndGet() },
            isGeneratingCandidates = { false }
        )
        controller.attachDragHandler(
            view = bubble,
            params = WindowManager.LayoutParams().apply {
                x = 0
                y = 0
            }
        )

        dispatchEvent(bubble, MotionEvent.ACTION_DOWN, 30f, 30f)
        bubble.runScheduledLongPress()
        dispatchEvent(bubble, MotionEvent.ACTION_UP, 30f, 30f)

        assertEquals("Expected long press callback to run once", 1, longPressCount.get())
        assertEquals("Expected long-press path not to trigger click", 0, bubble.clickCount)
    }

    @Test
    fun moveWithinSlop_stillAllowsLongPress() {
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

        dispatchEvent(bubble, MotionEvent.ACTION_DOWN, 50f, 50f)
        dispatchEvent(bubble, MotionEvent.ACTION_MOVE, 55f, 55f)
        bubble.runScheduledLongPress()

        assertTrue("Expected small move within slop not to cancel long-press", longPressTriggered.get())
    }

    private fun dispatchEvent(view: View, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private class ProbeBubbleView(context: Context) : View(context) {
        private var scheduledLongPress: Runnable? = null
        var clickCount: Int = 0
            private set

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

        override fun performClick(): Boolean {
            clickCount += 1
            return true
        }

        fun runScheduledLongPress() {
            val callback = scheduledLongPress
            scheduledLongPress = null
            callback?.run()
        }
    }
}