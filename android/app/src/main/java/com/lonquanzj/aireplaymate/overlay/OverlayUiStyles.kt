package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * Overlay UI 背景与卡片样式工厂。
 */
internal fun roundedBackground(
    color: Int,
    radius: Float
): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }
}

// Shared panel containers
internal fun Context.overlayPanelBackground(): GradientDrawable {
    return GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xF2F2ECFF.toInt(), 0xEEE5D9FF.toInt())
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpPx(22).toFloat()
        setStroke(dpPx(1), 0x33A886FF)
    }
}

internal fun Context.softPurpleCardBackground(): GradientDrawable {
    return GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFFF3EEFF.toInt(), 0xFFE9E0FF.toInt())
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpPx(16).toFloat()
        setStroke(dpPx(1), 0x26A07CFF)
    }
}

// Candidate panel styles
internal fun Context.candidatePanelBackground(): GradientDrawable {
    return GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFEFFFCFF.toInt(), 0xFEF5EEFF.toInt())
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpPx(20).toFloat()
        setStroke(dpPx(1), 0x2AA886FF)
    }
}

internal fun Context.candidatePanelIconButtonBackground(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(0x167A57E8)
        cornerRadius = dpPx(14).toFloat()
        setStroke(dpPx(1), 0x2A7A57E8)
    }
}

internal fun Context.candidateReplyBackground(): GradientDrawable {
    return GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFFF3EEFF.toInt(), 0xFFEDE5FF.toInt())
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpPx(14).toFloat()
        setStroke(dpPx(1), 0x2AA07CFF)
    }
}

// Style menu panel styles
internal fun Context.styleMenuPanelBackground(): GradientDrawable {
    return GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFEFFFCFF.toInt(), 0xFEF8F4FF.toInt())
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpPx(20).toFloat()
        setStroke(dpPx(1), 0x26A886FF)
    }
}

internal fun Context.styleMenuSegmentBackground(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(0xFFEFEAF8.toInt())
        cornerRadius = dpPx(12).toFloat()
        setStroke(dpPx(1), 0x12A886FF)
    }
}

internal fun Context.styleMenuTabIdleBackground(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        cornerRadius = dpPx(11).toFloat()
    }
}

internal fun Context.styleMenuTabSelectedBackground(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(0x247A57E8)
        cornerRadius = dpPx(10).toFloat()
        setStroke(dpPx(1), 0x337A57E8)
    }
}

internal fun Context.compactMenuButtonBackground(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(0xF2FFFFFF.toInt())
        cornerRadius = dpPx(11).toFloat()
        setStroke(dpPx(1), 0x18A07CFF)
    }
}

internal fun Context.compactSelectedMenuButtonBackground(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(0x1A7A57E8)
        cornerRadius = dpPx(11).toFloat()
        setStroke(dpPx(1), 0x807A57E8.toInt())
    }
}

internal fun Context.styleMenuGroupBackground(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(0x00FFFFFF)
        cornerRadius = dpPx(9).toFloat()
        setStroke(dpPx(1), 0x0D7A57E8)
    }
}

internal fun Context.selectedStyleMenuGroupBackground(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(0x147A57E8)
        cornerRadius = dpPx(9).toFloat()
        setStroke(dpPx(1), 0x337A57E8)
    }
}

// Shared action button style
internal fun Context.selectedMenuButtonBackground(): GradientDrawable {
    return GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFF7A57E8.toInt(), 0xFF5B3DC8.toInt())
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpPx(16).toFloat()
        setStroke(dpPx(1), 0x667A57E8)
    }
}

