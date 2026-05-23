package com.lonquanzj.aireplaymate.overlay

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val panelHost = createPanelHost(service)
        val existingStatusView = TextView(service).apply { text = "old" }
        val existingPanel = View(service)

        setPrivateField(service, "panelHost", panelHost)
        setPrivateField(panelHost, "progressStatusView", existingStatusView)
        setPrivateField(panelHost, "candidatePanelView", existingPanel)

        invokePrivateOneStringArg(service, "showProgressPanel", "refreshing")

        assertEquals("refreshing", existingStatusView.text.toString())
        assertEquals(existingStatusView, getPrivateField(panelHost, "progressStatusView"))
        assertEquals(existingPanel, getPrivateField(panelHost, "candidatePanelView"))
    }

    @Test
    fun triggerCandidateGeneration_whenAlreadyGenerating_showsToastAndSkipsWork() {
        val service = Robolectric.buildService(OverlayButtonService::class.java).get()
        setPrivateField(service, "isGeneratingCandidates", true)
        val panelHost = createPanelHost(service)
        val existingPanel = View(service)
        setPrivateField(service, "panelHost", panelHost)
        setPrivateField(panelHost, "candidatePanelView", existingPanel)

        invokePrivateGeneration(service, ReplyStyleProfile(), null)

        val latestToastText = ShadowToast.getTextOfLatestToast()
        assertEquals("正在生成候选回复，请稍等", latestToastText)
        assertEquals(existingPanel, getPrivateField(panelHost, "candidatePanelView"))
    }

    @Test
    fun removeCandidatePanel_canBeCalledRepeatedlyAndKeepsStateCleared() {
        val service = Robolectric.buildService(OverlayButtonService::class.java).get()
        val panelHost = createPanelHost(service)
        setPrivateField(service, "panelHost", panelHost)
        setPrivateField(panelHost, "candidatePanelView", View(service))
        setPrivateField(panelHost, "progressStatusView", TextView(service).apply { text = "loading" })
        setPrivateField(panelHost, "progressIndicatorView", LinearLayout(service))

        invokePrivateNoArg(service, "removeCandidatePanel")
        invokePrivateNoArg(service, "removeCandidatePanel")

        assertNull(getPrivateField(panelHost, "candidatePanelView"))
        assertNull(getPrivateField(panelHost, "progressStatusView"))
        assertNull(getPrivateField(panelHost, "progressIndicatorView"))
    }

    @Test
    fun removeCandidatePanel_whenPanelHostUninitialized_doesNotThrow() {
        val service = Robolectric.buildService(OverlayButtonService::class.java).get()

        assertNoThrow("Expected removeCandidatePanel to be safe when panelHost is uninitialized") {
            invokePrivateNoArg(service, "removeCandidatePanel")
            invokePrivateNoArg(service, "removeCandidatePanel")
        }
    }

    @Test
    fun onDestroy_canBeCalledRepeatedlyAndClearsOverlayReferences() {
        val service = Robolectric.buildService(OverlayButtonService::class.java).get()
        setPrivateField(service, "overlayView", View(service))
        setPrivateField(service, "floatingButtonView", FrameLayout(service))

        assertNoThrow("Expected onDestroy first call not to throw") {
            service.onDestroy()
        }
        assertNoThrow("Expected onDestroy second call not to throw") {
            service.onDestroy()
        }

        assertNull(getPrivateField(service, "overlayView"))
        assertNull(getPrivateField(service, "floatingButtonView"))
        assertNull(getPrivateField(service, "windowManager"))
    }

    private fun invokePrivateNoArg(service: OverlayButtonService, methodName: String) {
        val method = OverlayButtonService::class.java.getDeclaredMethod(methodName)
        method.isAccessible = true
        method.invoke(service)
    }

    private fun invokePrivateOneStringArg(service: OverlayButtonService, methodName: String, arg: String) {
        val method = OverlayButtonService::class.java.getDeclaredMethod(methodName, String::class.java)
        method.isAccessible = true
        method.invoke(service, arg)
    }

    private fun invokePrivateGeneration(
        service: OverlayButtonService,
        profile: ReplyStyleProfile,
        draftText: String?
    ) {
        val method = OverlayButtonService::class.java.getDeclaredMethod(
            "triggerCandidateGeneration",
            ReplyStyleProfile::class.java,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(service, profile, draftText)
    }

    private fun createPanelHost(service: OverlayButtonService): OverlayPanelHost {
        return OverlayPanelHost(
            context = service,
            windowManagerProvider = { null },
            buttonLayoutParamsProvider = { null },
            stopProgressIndicatorAnimation = {},
            startProgressIndicatorAnimation = {},
            animatePanelIn = {}
        )
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun getPrivateField(target: Any, fieldName: String): Any? {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target)
    }

    private fun assertNoThrow(message: String, block: () -> Unit) {
        try {
            block()
        } catch (throwable: Throwable) {
            fail("$message: ${throwable::class.java.simpleName} ${throwable.message}")
        }
    }
}