package io.github.pradyumnadp.blobface.internal

import kotlin.math.abs
import kotlin.math.max

/*
 * The transcendental functions blobatar calls, routed to the implementation that
 * matches V8 most closely on each platform. V8's Math.sin/cos/atan2/asin/cbrt/
 * pow/exp are ports of fdlibm, which is what java.lang.StrictMath is, so JVM and
 * Android use StrictMath; JS and Wasm call the engine's Math directly; Apple
 * platforms use the system libm.
 */
internal expect object JsMath {
    fun sin(x: Double): Double
    fun cos(x: Double): Double
    fun atan2(y: Double, x: Double): Double
    fun asin(x: Double): Double
    fun cbrt(x: Double): Double
    fun pow(x: Double, y: Double): Double
    fun exp(x: Double): Double
}

/** V8's `Math.hypot`: max-scaled Kahan sum, then `sqrt(sum) * max`. */
internal fun jsHypot(a: Double, b: Double): Double {
    if (a.isInfinite() || b.isInfinite()) return Double.POSITIVE_INFINITY
    if (a.isNaN() || b.isNaN()) return Double.NaN
    val x = abs(a)
    val y = abs(b)
    val m = max(x, y)
    if (m == 0.0) return 0.0
    var sum = 0.0
    var comp = 0.0
    for (v in doubleArrayOf(x, y)) {
        val n = v / m
        val summand = n * n - comp
        val prelim = sum + summand
        comp = (prelim - sum) - summand
        sum = prelim
    }
    return kotlin.math.sqrt(sum) * m
}
