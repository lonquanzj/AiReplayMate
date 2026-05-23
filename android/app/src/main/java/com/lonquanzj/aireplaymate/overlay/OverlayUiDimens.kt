package com.lonquanzj.aireplaymate.overlay

import android.content.Context

/**
 * Overlay UI 通用尺寸工具。
 */
internal fun Context.dpPx(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}
