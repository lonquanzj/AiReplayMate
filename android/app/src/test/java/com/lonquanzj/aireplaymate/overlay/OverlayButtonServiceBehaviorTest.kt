package com.lonquanzj.aireplaymate.overlay

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class OverlayButtonServiceBehaviorTest {
    @Test
    fun showProgressPanel_whenStatusViewExists_onlyUpdatesText() {
        val service = Robolectric.buildService(OverlayButtonService::class.java).get()
        val existingStatusView = TextView(service).apply { text = "old" }
        val existingPanel = View(service)

        setPrivateField(service, "progressStatusView", existingStatusView)
        setPrivateField(service, "candidatePanelView", existingPanel)

        invokePrivateNoArg(service, "showProgressPanel", "refreshing")

        assertEquals("refreshing", existingStatusView.text.toString())
        assertSame(existingStatusView, getPrivateField(service, "progressStatusView"))
        assertSame(existingPanel, getPrivateField(service, "candidatePanelView"))
    }

    @Test
    fun showStyleMenuPanel_whenGenerating_showsToastAndSkipsPanelCreation() {
        val service = Robolectric.buildService(OverlayButtonService::class.java).get()
        setPrivateField(service, "isGeneratingCandidates", true)

        invokePrivateNoArg(service, "showStyleMenuPanel")

        val latestToastText = ShadowToast.getTextOfLatestToast()
        assertEquals("正在生成候选回复，请稍等", latestToastText)
        assertNull(getPrivateField(service, "candidatePanelView"))
    }

    @Test
    fun showStyleMenuPanel_whenIdle_createsCandidatePanelView() {
        val service = Robolectric.buildService(OverlayButtonService::class.java).get()
        setPrivateField(service, "isGeneratingCandidates", false)

        invokePrivateNoArg(service, "showStyleMenuPanel")

        assertNotNull(getPrivateField(service, "candidatePanelView"))
    }

    @Test
    fun removeCandidatePanel_canBeCalledRepeatedlyAndKeepsStateCleared() {
        val service = Robolectric.buildService(OverlayButtonService::class.java).get()
        setPrivateField(service, "candidatePanelView", View(service))
        setPrivateField(service, "progressStatusView", TextView(service).apply { text = "loading" })
        setPrivateField(service, "progressIndicatorView", LinearLayout(service))

        invokePrivateNoArg(service, "removeCandidatePanel")
        invokePrivateNoArg(service, "removeCandidatePanel")

        assertNull(getPrivateField(service, "candidatePanelView"))
        assertNull(getPrivateField(service, "progressStatusView"))
        assertNull(getPrivateField(service, "progressIndicatorView"))
    }

    @Test
    fun onDestroy_canBeCalledRepeatedlyAndClearsOverlayReferences() {
        val service = Robolectric.buildService(OverlayButtonService::class.java).get()
        setPrivateField(service, "overlayView", View(service))
        setPrivateField(service, "floatingButtonView", FrameLayout(service))
        setPrivateField(service, "candidatePanelView", View(service))
        setPrivateField(service, "progressStatusView", TextView(service).apply { text = "working" })
        setPrivateField(service, "progressIndicatorView", LinearLayout(service))

        assertNoThrow("Expected onDestroy first call not to throw") {
            service.onDestroy()
        }
        assertNoThrow("Expected onDestroy second call not to throw") {
            service.onDestroy()
        }

        assertNull(getPrivateField(service, "overlayView"))
        assertNull(getPrivateField(service, "floatingButtonView"))
        assertNull(getPrivateField(service, "candidatePanelView"))
        assertNull(getPrivateField(service, "progressStatusView"))
        assertNull(getPrivateField(service, "progressIndicatorView"))
        assertNull(getPrivateField(service, "windowManager"))
    }

    private fun invokePrivateNoArg(service: OverlayButtonService, methodName: String) {
        val method = OverlayButtonService::class.java.getDeclaredMethod(methodName)
        method.isAccessible = true
        method.invoke(service)
    }

    private fun invokePrivateNoArg(service: OverlayButtonService, methodName: String, arg: String) {
        val method = OverlayButtonService::class.java.getDeclaredMethod(methodName, String::class.java)
        method.isAccessible = true
        method.invoke(service, arg)
    }

    private fun setPrivateField(service: OverlayButtonService, fieldName: String, value: Any?) {
        val field = OverlayButtonService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(service, value)
    }

    private fun getPrivateField(service: OverlayButtonService, fieldName: String): Any? {
        val field = OverlayButtonService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(service)
    }

    private fun assertNoThrow(message: String, block: () -> Unit) {
        try {
            block()
        } catch (throwable: Throwable) {
            fail("$message: ${throwable::class.java.simpleName} ${throwable.message}")
        }
    }
}