package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lonquanzj.aireplaymate.R
import com.lonquanzj.aireplaymate.prompt.ReplyPlaybookConfig
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
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

// 按当前 tab 渲染对应内容页。
private fun renderStyleMenuContent(
    context: Context,
    parent: LinearLayout,
    tab: StyleMenuTab,
    current: ReplyStyleProfile,
    catalog: ReplyStyleCatalogState,
    playbooksByCategory: Map<String, List<ReplyPlaybookConfig>>,
    selectedGroupId: String?,
    onGroupSelected: (StyleMenuTab, String) -> Unit,
    onProfileChosen: (profile: ReplyStyleProfile, persistAsDefault: Boolean, requiresDraft: Boolean) -> Unit
) {
    when (tab) {
        StyleMenuTab.PERSONA -> {
            renderStyleMenuPage(
                context = context,
                parent = parent,
                groups = listOf(
                    StyleMenuGroup(
                        id = STYLE_MENU_ALL_GROUP_ID,
                        label = context.getString(R.string.overlay_label_all),
                        items = catalog.personas
                    )
                ),
                selectedGroupId = selectedGroupId,
                onGroupSelected = { groupId -> onGroupSelected(tab, groupId) }
            ) { personaConfig ->
                val profile = current.copy(
                    mode = ReplyStyleMode.QUICK_REPLY,
                    persona = ReplyStyleCatalog.personaFromConfig(personaConfig),
                    personaConfig = personaConfig
                )
                styleMenuItemButton(
                    context = context,
                    label = personaConfig.label,
                    isSelected = personaConfig.id == current.personaConfig.id
                ) {
                    onProfileChosen(profile, true, false)
                }
            }
        }

        StyleMenuTab.PLAYBOOK -> {
            renderStyleMenuPage(
                context = context,
                parent = parent,
                groups = playbooksByCategory.map { (category, playbooks) ->
                    StyleMenuGroup(
                        id = category,
                        label = category,
                        items = playbooks
                    )
                },
                selectedGroupId = selectedGroupId,
                onGroupSelected = { groupId -> onGroupSelected(tab, groupId) }
            ) { playbook ->
                val profile = current.copy(
                    mode = ReplyStyleMode.PLAYBOOK,
                    playbookScene = ReplyStyleCatalog.sceneFromConfig(playbook),
                    playbookConfig = playbook
                )
                styleMenuItemButton(
                    context = context,
                    label = playbook.label,
                    isSelected = playbook.id == current.playbookConfig.id
                ) {
                    onProfileChosen(profile, false, false)
                }
            }
        }

        StyleMenuTab.POLISH -> {
            renderStyleMenuPage(
                context = context,
                parent = parent,
                groups = listOf(
                    StyleMenuGroup(
                        id = STYLE_MENU_ALL_GROUP_ID,
                        label = context.getString(R.string.overlay_label_all),
                        items = catalog.polishGoals
                    )
                ),
                selectedGroupId = selectedGroupId,
                onGroupSelected = { groupId -> onGroupSelected(tab, groupId) }
            ) { goal ->
                val profile = current.copy(
                    mode = ReplyStyleMode.POLISH,
                    polishGoal = ReplyStyleCatalog.polishGoalFromConfig(goal),
                    polishGoalConfig = goal
                )
                styleMenuItemButton(
                    context = context,
                    label = goal.label,
                    isSelected = goal.id == current.polishGoalConfig.id
                ) {
                    onProfileChosen(profile, false, true)
                }
            }
        }
    }
}

// 通用页面渲染器：可选分组条 + 固定列数网格。
private fun <T> renderStyleMenuPage(
    context: Context,
    parent: LinearLayout,
    groups: List<StyleMenuGroup<T>>,
    selectedGroupId: String?,
    onGroupSelected: (String) -> Unit,
    viewFactory: (T) -> View
) {
    if (groups.isEmpty()) return
    val activeGroup = groups.firstOrNull { it.id == selectedGroupId } ?: groups.first()
    if (groups.size > 1) {
        addStyleMenuGroupStrip(
            context = context,
            parent = parent,
            groups = groups,
            selectedGroupId = activeGroup.id,
            onGroupSelected = onGroupSelected
        )
    }
    addCompactGrid(
        context = context,
        parent = parent,
        items = activeGroup.items,
        columns = 4,
        topMarginDp = if (groups.size > 1) STYLE_GRID_TOP_WITH_GROUP else STYLE_GRID_TOP_DEFAULT,
        gapDp = STYLE_GRID_GAP,
        viewFactory = viewFactory
    )
}

private fun <T> addStyleMenuGroupStrip(
    context: Context,
    parent: LinearLayout,
    groups: List<StyleMenuGroup<T>>,
    selectedGroupId: String,
    onGroupSelected: (String) -> Unit
) {
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }
    groups.forEachIndexed { index, group ->
        row.addView(
            styleMenuGroupButtonView(context, group.label, group.id == selectedGroupId) {
                onGroupSelected(group.id)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    marginStart = context.dpPx(STYLE_GROUP_GAP)
                }
            }
        )
    }
    parent.addView(
        HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = context.dpPx(STYLE_GROUP_TOP_MARGIN)
        }
    )
}

private fun styleMenuHintRowView(
    context: Context,
    tab: StyleMenuTab,
    current: ReplyStyleProfile,
    onClose: () -> Unit
): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(context).apply {
                        tag = STYLE_MENU_TITLE_TAG
                        text = styleMenuSelectedLabel(tab, current)
                        textSize = TEXT_SIZE_12
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(COLOR_TEXT_ACCENT)
                    }
                )
                addView(
                    TextView(context).apply {
                        tag = STYLE_MENU_HINT_TAG
                        text = styleMenuSelectionHint(context, tab, current)
                        textSize = TEXT_SIZE_10_5
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setTextColor(COLOR_TEXT_MUTED)
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = context.dpPx(STYLE_HINT_SUBTITLE_TOP_MARGIN)
                    }
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(
            TextView(context).apply {
                text = context.getString(R.string.overlay_icon_close)
                textSize = TEXT_SIZE_15
                gravity = Gravity.CENTER
                setTextColor(COLOR_TEXT_MUTED)
                setPadding(context.dpPx(STYLE_HINT_CLOSE_PADDING_H), 0, context.dpPx(STYLE_HINT_CLOSE_PADDING_H), 0)
                minWidth = context.dpPx(STYLE_HINT_CLOSE_MIN_WIDTH)
                minHeight = context.dpPx(STYLE_HINT_CLOSE_MIN_HEIGHT)
                applyPressedFeedback(this)
                setOnClickListener { onClose() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
}

private fun updateStyleMenuHintRow(
    row: LinearLayout,
    tab: StyleMenuTab,
    current: ReplyStyleProfile
) {
    val titleView = row.findViewWithTag<TextView>(STYLE_MENU_TITLE_TAG)
    val hintView = row.findViewWithTag<TextView>(STYLE_MENU_HINT_TAG)
    titleView?.text = styleMenuSelectedLabel(tab, current)
    hintView?.text = styleMenuSelectionHint(row.context, tab, current)
}

private fun styleMenuSelectedLabel(
    tab: StyleMenuTab,
    current: ReplyStyleProfile
): String {
    return when (tab) {
        StyleMenuTab.PERSONA -> current.personaConfig.label
        StyleMenuTab.PLAYBOOK -> current.playbookConfig.label
        StyleMenuTab.POLISH -> current.polishGoalConfig.label
    }
}

private fun styleMenuSelectionHint(
    context: Context,
    tab: StyleMenuTab,
    current: ReplyStyleProfile
): String {
    return when (tab) {
        StyleMenuTab.PERSONA -> context.getString(
            R.string.overlay_prefix_persona,
            current.personaConfig.label
        )
        StyleMenuTab.PLAYBOOK -> context.getString(
            R.string.overlay_prefix_playbook,
            current.playbookConfig.label
        )
        StyleMenuTab.POLISH -> context.getString(
            R.string.overlay_prefix_polish,
            current.polishGoalConfig.label
        )
    }
}

private fun styleMenuTabButtonView(
    context: Context,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
): TextView {
    return TextView(context).apply {
        text = label
        textSize = TEXT_SIZE_12
        setTextColor(if (isSelected) COLOR_TEXT_ACCENT else COLOR_TEXT_PRIMARY)
        typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        minHeight = context.dpPx(STYLE_TAB_MIN_HEIGHT)
        setPadding(
            context.dpPx(STYLE_TAB_PADDING_H),
            context.dpPx(STYLE_TAB_PADDING_V),
            context.dpPx(STYLE_TAB_PADDING_H),
            context.dpPx(STYLE_TAB_PADDING_V)
        )
        elevation = if (isSelected) context.dpPx(1).toFloat() else 0f
        background = if (isSelected) {
            context.styleMenuTabSelectedBackground()
        } else {
            context.styleMenuTabIdleBackground()
        }
        applyPressedFeedback(this)
        setOnClickListener { onClick() }
    }
}

private fun styleMenuGroupButtonView(
    context: Context,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
): TextView {
    return TextView(context).apply {
        text = label
        textSize = TEXT_SIZE_10
        setTextColor(if (isSelected) COLOR_TEXT_ACCENT else COLOR_TEXT_GROUP_IDLE)
        typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        minHeight = context.dpPx(STYLE_GROUP_MIN_HEIGHT)
        setPadding(
            context.dpPx(STYLE_GROUP_PADDING_H),
            context.dpPx(STYLE_GROUP_PADDING_TOP),
            context.dpPx(STYLE_GROUP_PADDING_H),
            context.dpPx(STYLE_GROUP_PADDING_BOTTOM)
        )
        background = if (isSelected) {
            context.selectedStyleMenuGroupBackground()
        } else {
            context.styleMenuGroupBackground()
        }
        applyPressedFeedback(this)
        setOnClickListener { onClick() }
    }
}

private fun styleMenuItemButton(
    context: Context,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
): TextView {
    return TextView(context).apply {
        text = if (isSelected) {
            context.getString(R.string.overlay_selected_item, label)
        } else {
            label
        }
        textSize = TEXT_SIZE_10_5
        setTextColor(if (isSelected) COLOR_TEXT_ACCENT else COLOR_TEXT_PRIMARY)
        typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        minHeight = context.dpPx(STYLE_ITEM_MIN_HEIGHT)
        setPadding(
            context.dpPx(STYLE_ITEM_PADDING_H),
            context.dpPx(STYLE_ITEM_PADDING_V),
            context.dpPx(STYLE_ITEM_PADDING_H),
            context.dpPx(STYLE_ITEM_PADDING_V)
        )
        background = if (isSelected) {
            context.compactSelectedMenuButtonBackground()
        } else {
            context.compactMenuButtonBackground()
        }
        applyPressedFeedback(this)
        setOnClickListener { onClick() }
    }
}

private fun <T> addCompactGrid(
    context: Context,
    parent: LinearLayout,
    items: List<T>,
    columns: Int,
    topMarginDp: Int,
    gapDp: Int = 8,
    viewFactory: (T) -> View
) {
    if (items.isEmpty()) return
    val horizontalGap = context.dpPx(gapDp)
    val verticalGap = context.dpPx(gapDp)
    items.chunked(columns).forEachIndexed { rowIndex, rowItems ->
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        rowItems.forEachIndexed { itemIndex, item ->
            row.addView(
                viewFactory(item),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (itemIndex > 0) {
                        marginStart = horizontalGap
                    }
                }
            )
        }
        repeat(columns - rowItems.size) {
            row.addView(
                View(context),
                LinearLayout.LayoutParams(0, 0, 1f).apply {
                    if (row.childCount > 0) {
                        marginStart = horizontalGap
                    }
                }
            )
        }
        parent.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (rowIndex == 0) context.dpPx(topMarginDp) else verticalGap
            }
        )
    }
}

