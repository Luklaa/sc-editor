package dev.donutquine.math

import kotlin.math.pow
import kotlin.math.round

object MathHelper {
    @JvmStatic
    fun clamp(value: Int, min: Int, max: Int): Int {
        return kotlin.math.max(min, kotlin.math.min(max, value))
    }

    @JvmStatic
    fun clamp(value: Float, min: Float, max: Float): Float {
        return kotlin.math.max(min, kotlin.math.min(max, value))
    }

    @JvmStatic
    fun round(value: Float, digits: Int): Float {
        if (digits == 0) return kotlin.math.round(value)

        val pow = 10.0.pow(digits.toDouble())
        return (kotlin.math.round(value * pow) / pow).toFloat()
    }
}
