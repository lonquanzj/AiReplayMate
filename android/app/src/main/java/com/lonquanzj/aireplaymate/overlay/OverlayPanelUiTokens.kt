package com.lonquanzj.aireplaymate.overlay

/**
 * Overlay 面板 UI Token 常量。
 *
 * 这里集中维护尺寸、字号、颜色和间距，避免各面板文件散落魔法值。
 */
// Shared panel container
internal const val PANEL_ELEVATION = 14f

// Shared text sizes
internal const val TEXT_SIZE_10 = 10f
internal const val TEXT_SIZE_10_5 = 10.5f
internal const val TEXT_SIZE_11 = 11f
internal const val TEXT_SIZE_12 = 12f
internal const val TEXT_SIZE_13 = 13f
internal const val TEXT_SIZE_14 = 14f
internal const val TEXT_SIZE_15 = 15f
internal const val TEXT_SIZE_16 = 16f

// Shared panel colors
internal const val COLOR_TEXT_PRIMARY = 0xFF3F2B78.toInt()
internal const val COLOR_TEXT_SECONDARY = 0xFF7A659C.toInt()
internal const val COLOR_TEXT_MUTED = 0xFF8B7AA3.toInt()
internal const val COLOR_TEXT_ACCENT = 0xFF5B3DC8.toInt()
internal const val COLOR_TEXT_GROUP_IDLE = 0xFF9488A0.toInt()
internal const val COLOR_TEXT_INVERSE = 0xFFFFFFFF.toInt()
internal const val COLOR_PROGRESS_DOT_ACTIVE = 0xFF8E63FF.toInt()
internal const val COLOR_PROGRESS_DOT_IDLE = 0xFFD7C7FF.toInt()

// Candidate panel: container
internal const val CANDIDATE_PANEL_PADDING_H = 12
internal const val CANDIDATE_PANEL_PADDING_TOP = 9
internal const val CANDIDATE_PANEL_PADDING_BOTTOM = 12

// Candidate panel: header
internal const val CANDIDATE_SUBTITLE_TOP_PADDING = 2
internal const val CANDIDATE_MODE_LABEL_MARGIN_START = 4
internal const val CANDIDATE_ACTION_ICON_MIN_WIDTH = 32
internal const val CANDIDATE_ACTION_ICON_MIN_HEIGHT = 28
internal const val CANDIDATE_ACTION_ICON_MARGIN_START = 8
internal const val CANDIDATE_CLOSE_ICON_MARGIN_START = 5

// Candidate panel: list
internal const val CANDIDATE_STACK_TOP_MARGIN = 10
internal const val CANDIDATE_STACK_ITEM_GAP = 8
internal const val CANDIDATE_ITEM_PADDING = 10

// Style menu panel: container
internal const val STYLE_PANEL_PADDING_H = 10
internal const val STYLE_PANEL_PADDING_TOP = 7
internal const val STYLE_PANEL_PADDING_BOTTOM = 8

// Style menu panel: segmented tabs
internal const val STYLE_SEGMENT_PADDING = 3
internal const val STYLE_TAB_ROW_TOP_MARGIN = 2
internal const val STYLE_TAB_ITEM_GAP = 2

// Style menu panel: hint row
internal const val STYLE_HINT_CLOSE_PADDING_H = 6
internal const val STYLE_HINT_CLOSE_MIN_WIDTH = 24
internal const val STYLE_HINT_CLOSE_MIN_HEIGHT = 20

// Style menu panel: tab item
internal const val STYLE_TAB_MIN_HEIGHT = 30
internal const val STYLE_TAB_PADDING_H = 5
internal const val STYLE_TAB_PADDING_V = 4

// Style menu panel: group chips
internal const val STYLE_GROUP_GAP = 8
internal const val STYLE_GROUP_TOP_MARGIN = 8
internal const val STYLE_GROUP_MIN_HEIGHT = 22
internal const val STYLE_GROUP_PADDING_H = 8
internal const val STYLE_GROUP_PADDING_TOP = 2
internal const val STYLE_GROUP_PADDING_BOTTOM = 3

// Style menu panel: grid
internal const val STYLE_GRID_TOP_WITH_GROUP = 8
internal const val STYLE_GRID_TOP_DEFAULT = 10
internal const val STYLE_ITEM_MIN_HEIGHT = 33
internal const val STYLE_ITEM_PADDING_H = 3
internal const val STYLE_ITEM_PADDING_V = 5
internal const val STYLE_GRID_GAP = 8
