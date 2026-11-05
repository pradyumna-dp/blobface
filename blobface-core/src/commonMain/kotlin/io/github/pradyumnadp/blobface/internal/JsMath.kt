package io.github.pradyumnadp.blobface.internal

import kotlin.math.abs
import kotlin.math.max

/**
 * The transcendental functions blobatar calls, on every platform routed to the
 * pure-Kotlin fdlibm port ([Fdlibm]) rather than the platform's own math.
 *
 * Platform libms (Apple's, and the LLVM libm newer V8 versions use) differ from
 * each other in the last bit for some inputs. One implementation everywhere makes
 * blobface's output identical on every target and bit-identical to blobatar
 * 2.7.0 running on Node 22, which produced the parity fixtures.
 */
internal object JsMath {
    fun sin(x: Double): Double = Fdlibm.sin(x)
    fun cos(x: Double): Double = Fdlibm.cos(x)
    fun atan2(y: Double, x: Double): Double = Fdlibm.atan2(y, x)
    fun asin(x: Double): Double = Fdlibm.asin(x)
    fun cbrt(x: Double): Double = Fdlibm.cbrt(x)
    fun pow(x: Double, y: Double): Double = Fdlibm.pow(x, y)
    fun exp(x: Double): Double = Fdlibm.exp(x)
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
