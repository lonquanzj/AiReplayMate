package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayStyleMenuPanelInitSequenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun buildPanel_layoutRefreshCallbackCanRunBeforeConsumerStoresViewReference() {
        var callbackCount = 0
        var observedNullReferenceInCallback = false
        var panelRef: ScrollView? = null

        val builtPanel = buildStyleMenuPanelView(
            context = context,
            current = ReplyStyleProfile(),
            catalog = ReplyStyleCatalog.defaultCatalogState,
            onClose = {},
            onProfileChosen = { _, _, _ -> },
            onLayoutRefreshRequested = {
                callbackCount += 1
                if (panelRef == null) {
                    observedNullReferenceInCallback = true
                } else {
                    // Keep the callback path touching the same data as production code.
                    panelRef?.parent
                }
            }
        )

        panelRef = builtPanel

        assertTrue("Expected at least one layout refresh callback during panel initialization", callbackCount > 0)
        assertTrue("Expected callback to run before panel reference assignment", observedNullReferenceInCallback)
    }

    @Test
    fun buildPanel_layoutRefreshCallbackCanRunAfterPanelAttachedAndTabChanged() {
        var callbackCount = 0
        var observedAttachedParent = false
        var panelRef: ScrollView? = null

        val builtPanel = buildStyleMenuPanelView(
            context = context,
            current = ReplyStyleProfile(),
            catalog = ReplyStyleCatalog.defaultCatalogState,
            onClose = {},
            onProfileChosen = { _, _, _ -> },
            onLayoutRefreshRequested = {
                callbackCount += 1
                if (panelRef?.parent != null) {
                    observedAttachedParent = true
                }
            }
        )

        panelRef = builtPanel
        FrameLayout(context).addView(builtPanel)
        val callbackCountBeforeTabSwitch = callbackCount

        val playbookTab = findTextViewByText(builtPanel, "话术")
        assertNotNull("Expected to find playbook tab in style menu", playbookTab)
        playbookTab?.performClick()

        assertTrue(
            "Expected layout refresh callback count to increase after tab switch",
            callbackCount > callbackCountBeforeTabSwitch
        )
        assertTrue("Expected callback to observe attached panel parent", observedAttachedParent)
    }

    @Test
    fun buildPanel_polishSelectionInvokesProfileCallbackWithDraftFlag() {
        val currentProfile = ReplyStyleProfile()
        var selectedProfile: ReplyStyleProfile? = null
        var persistAsDefault: Boolean? = null
        var requiresDraft: Boolean? = null

        val panel = buildStyleMenuPanelView(
            context = context,
            current = currentProfile,
            catalog = ReplyStyleCatalog.defaultCatalogState,
            onClose = {},
            onProfileChosen = { profile, persist, requires ->
                selectedProfile = profile
                persistAsDefault = persist
                requiresDraft = requires
            },
            onLayoutRefreshRequested = {}
        )

        val root = FrameLayout(context)
        root.addView(panel)
        val polishTab = findTextViewByText(panel, "润色")
        assertNotNull("Expected to find polish tab", polishTab)
        polishTab?.performClick()

        val targetLabel = currentProfile.polishGoalConfig.label
        val polishItem = findClickableTextViewContaining(panel, targetLabel)
        assertNotNull("Expected to find clickable polish item", polishItem)
        polishItem?.performClick()

        assertNotNull("Expected polish selection callback to be invoked", selectedProfile)
        assertEquals(ReplyStyleMode.POLISH, selectedProfile?.mode)
        assertEquals(false, persistAsDefault)
        assertEquals(true, requiresDraft)
    }

    @Test
    fun buildPanel_personaSelectionInvokesProfileCallbackWithPersistFlag() {
        val currentProfile = ReplyStyleProfile()
        val targetPersona = ReplyStyleCatalog.defaultCatalogState.personas
            .firstOrNull { it.id != currentProfile.personaConfig.id }
            ?: ReplyStyleCatalog.defaultCatalogState.personas.first()
        var selectedProfile: ReplyStyleProfile? = null
        var persistAsDefault: Boolean? = null
        var requiresDraft: Boolean? = null

        val panel = buildStyleMenuPanelView(
            context = context,
            current = currentProfile,
            catalog = ReplyStyleCatalog.defaultCatalogState,
            onClose = {},
            onProfileChosen = { profile, persist, requires ->
                selectedProfile = profile
                persistAsDefault = persist
                requiresDraft = requires
            },
            onLayoutRefreshRequested = {}
        )

        FrameLayout(context).addView(panel)
        val personaItem = findClickableTextViewByExactText(panel, targetPersona.label)
        assertNotNull("Expected to find clickable persona item", personaItem)
        personaItem?.performClick()

        assertNotNull("Expected persona selection callback to be invoked", selectedProfile)
        assertEquals(ReplyStyleMode.QUICK_REPLY, selectedProfile?.mode)
        assertEquals(targetPersona.id, selectedProfile?.personaConfig?.id)
        assertEquals(true, persistAsDefault)
        assertEquals(false, requiresDraft)
    }

    @Test
    fun buildPanel_playbookSelectionInvokesProfileCallbackWithoutDraftFlag() {
        val currentProfile = ReplyStyleProfile()
        val targetPlaybook = ReplyStyleCatalog.defaultCatalogState.playbooks
            .firstOrNull { it.id != currentProfile.playbookConfig.id }
            ?: ReplyStyleCatalog.defaultCatalogState.playbooks.first()
        var selectedProfile: ReplyStyleProfile? = null
        var persistAsDefault: Boolean? = null
        var requiresDraft: Boolean? = null

        val panel = buildStyleMenuPanelView(
            context = context,
            current = currentProfile,
            catalog = ReplyStyleCatalog.defaultCatalogState,
            onClose = {},
            onProfileChosen = { profile, persist, requires ->
                selectedProfile = profile
                persistAsDefault = persist
                requiresDraft = requires
            },
            onLayoutRefreshRequested = {}
        )

        FrameLayout(context).addView(panel)
        val playbookTab = findTextViewByText(panel, "话术")
        assertNotNull("Expected to find playbook tab", playbookTab)
        playbookTab?.performClick()

        val playbookItem = findClickableTextViewByExactText(panel, targetPlaybook.label)
        assertNotNull("Expected to find clickable playbook item", playbookItem)
        playbookItem?.performClick()

        assertNotNull("Expected playbook selection callback to be invoked", selectedProfile)
        assertEquals(ReplyStyleMode.PLAYBOOK, selectedProfile?.mode)
        assertEquals(targetPlaybook.id, selectedProfile?.playbookConfig?.id)
        assertEquals(false, persistAsDefault)
        assertEquals(false, requiresDraft)
    }

    private fun findTextViewByText(root: View, expectedText: String): TextView? {
        if (root is TextView && root.text?.toString() == expectedText) {
            return root
        }
        if (root is android.view.ViewGroup) {
            for (index in 0 until root.childCount) {
                val found = findTextViewByText(root.getChildAt(index), expectedText)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun findClickableTextViewContaining(root: View, expectedTextPart: String): TextView? {
        if (root is TextView) {
            val text = root.text?.toString().orEmpty()
            if (
                text.contains(expectedTextPart) &&
                root.hasOnClickListeners() &&
                text != "角色" &&
                text != "话术" &&
                text != "润色"
            ) {
                return root
            }
        }
        if (root is android.view.ViewGroup) {
            for (index in 0 until root.childCount) {
                val found = findClickableTextViewContaining(root.getChildAt(index), expectedTextPart)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun findClickableTextViewByExactText(root: View, expectedText: String): TextView? {
        if (root is TextView) {
            val text = root.text?.toString().orEmpty()
            if (
                text == expectedText &&
                root.hasOnClickListeners() &&
                text != "角色" &&
                text != "话术" &&
                text != "润色"
            ) {
                return root
            }
        }
        if (root is android.view.ViewGroup) {
            for (index in 0 until root.childCount) {
                val found = findClickableTextViewByExactText(root.getChildAt(index), expectedText)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }
}
