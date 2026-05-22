package com.lonquanzj.aireplaymate.prompt

import kotlin.math.roundToInt

object LlmSampling {
    fun normalizeTemperature(value: Float): Float {
        return ((value.coerceIn(0f, 2f) * 10f).roundToInt() / 10f)
    }

    fun normalizedTemperatureDouble(value: Float): Double {
        return normalizeTemperature(value).toString().toDouble()
    }
}
