package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.lonquanzj.aireplaymate.R
import com.lonquanzj.aireplaymate.prompt.ReplyPlaybookConfig
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile

// 按当前 tab 渲染对应内容页。
internal fun renderStyleMenuContent(
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

