package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lonquanzj.aireplaymate.prompt.ReplyPlaybookConfig
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile

/**
 * 风格菜单面板 UI 构建器。
 *
 * 提供 Persona/Playbook/Polish 三类入口，所有业务动作通过回调回传给 Service。
 */
internal fun buildStyleMenuPanelView(
    context: Context,
    current: ReplyStyleProfile,
    catalog: ReplyStyleCatalogState,
    onClose: () -> Unit,
    onProfileChosen: (profile: ReplyStyleProfile, persistAsDefault: Boolean, requiresDraft: Boolean) -> Unit,
    onLayoutRefreshRequested: () -> Unit
): ScrollView {
    val playbooksByCategory = catalog.playbooks.groupBy(ReplyPlaybookConfig::categoryLabel)
    var selectedTab = StyleMenuTab.PERSONA
    val selectedGroupIds = mutableMapOf(
        StyleMenuTab.PERSONA to STYLE_MENU_ALL_GROUP_ID,
        StyleMenuTab.PLAYBOOK to playbooksByCategory.keys.firstOrNull().orEmpty(),
        StyleMenuTab.POLISH to STYLE_MENU_ALL_GROUP_ID
    )

    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            context.dpPx(STYLE_PANEL_PADDING_H),
            context.dpPx(STYLE_PANEL_PADDING_TOP),
            context.dpPx(STYLE_PANEL_PADDING_H),
            context.dpPx(STYLE_PANEL_PADDING_BOTTOM)
        )
        background = context.styleMenuPanelBackground()
        elevation = PANEL_ELEVATION
    }

    val hintRow = styleMenuHintRowView(
        context = context,
        tab = selectedTab,
        current = current,
        onClose = onClose
    )
    content.addView(hintRow)

    val tabRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(
            context.dpPx(STYLE_SEGMENT_PADDING),
            context.dpPx(STYLE_SEGMENT_PADDING),
            context.dpPx(STYLE_SEGMENT_PADDING),
            context.dpPx(STYLE_SEGMENT_PADDING)
        )
        background = context.styleMenuSegmentBackground()
    }
    val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    content.addView(
        tabRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = context.dpPx(STYLE_TAB_ROW_TOP_MARGIN)
        }
    )
    content.addView(
        body,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    // Tab 或分组变化时重建 body，并通知外层刷新 panel 布局。
    fun updateMenu() {
        tabRow.removeAllViews()
        updateStyleMenuHintRow(hintRow, selectedTab, current)
        StyleMenuTab.entries.forEachIndexed { index, tab ->
            tabRow.addView(
                styleMenuTabButtonView(context, tab.label, tab == selectedTab) {
                    selectedTab = tab
                    updateMenu()
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = context.dpPx(STYLE_TAB_ITEM_GAP)
                }
            )
        }

        body.removeAllViews()
        renderStyleMenuContent(
            context = context,
            parent = body,
            tab = selectedTab,
            current = current,
            catalog = catalog,
            playbooksByCategory = playbooksByCategory,
            selectedGroupId = selectedGroupIds[selectedTab],
            onGroupSelected = { tab, groupId ->
                selectedGroupIds[tab] = groupId
                updateMenu()
            },
            onProfileChosen = onProfileChosen
        )

        onLayoutRefreshRequested()
    }

    updateMenu()
    return ScrollView(context).apply { addView(content) }
}
