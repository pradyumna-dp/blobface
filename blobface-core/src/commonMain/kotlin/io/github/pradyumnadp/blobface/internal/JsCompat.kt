package io.github.pradyumnadp.blobface.internal

import kotlin.math.floor

/*
 * Small re-implementations of JavaScript semantics that blobatar's output depends
 * on byte-for-byte. Kotlin's own equivalents differ in edge cases (rounding mode,
 * number formatting, UTF-8 replacement, whitespace set), so these are used
 * everywhere the upstream source relied on the JS behavior.
 */

/** ECMAScript `Math.round`: halves round toward +∞ (Kotlin's `round` is half-even). */
internal fun jsRound(x: Double): Double {
    if (x.isNaN() || x.isInfinite()) return x
    val f = floor(x)
    // `x - f` is exact for every double, so this has none of the
    // `floor(x + 0.5)` error at 0.49999999999999994.
    return if (x - f >= 0.5) f + 1.0 else f
}

/** `Math.round(v * 100) / 100`, as a number. */
internal fun r2n(v: Double): Double = jsRound(v * 100) / 100

/** `Math.round(v * 1000) / 1000`, as a number. */
internal fun r3n(v: Double): Double = jsRound(v * 1000) / 1000

/** `String(Math.round(v * 100) / 100)`. `-0` prints as `"0"`, as it does in JS. */
internal fun r2(v: Double): String = fixed(jsRound(v * 100), 2)

/** `String(Math.round(v * 1000) / 1000)`. */
internal fun r3(v: Double): String = fixed(jsRound(v * 1000), 3)

/**
 * Formats `n / 10^digits` the way JS `String(number)` would.
 *
 * Every number blobatar emits is one of these quotients, and for the magnitudes
 * involved the shortest round-trip representation of the double nearest
 * `n / 10^digits` is exactly that decimal with trailing zeros removed. So the
 * output is derived from the integer `n` and never from platform float printing.
 */
internal fun fixed(n: Double, digits: Int): String {
    if (n.isNaN()) return "NaN"
    if (n.isInfinite()) return if (n > 0) "Infinity" else "-Infinity"
    val whole = n.toLong()
    if (whole == 0L) return "0"
    val neg = whole < 0
    val abs = if (neg) -whole else whole
    var scale = 1L
    repeat(digits) { scale *= 10 }
    val int = abs / scale
    val frac = if (digits == 0) "" else (abs % scale).toString().padStart(digits, '0').trimEnd('0')
    val body = if (frac.isEmpty()) int.toString() else "$int.$frac"
    return if (neg) "-$body" else body
}

/** JS `String(n)` for a value known to be an integer (e.g. `Math.round(x)`). */
internal fun jsInt(n: Double): String = fixed(n, 0)

/** ECMAScript `String.prototype.trim` whitespace: WhiteSpace ∪ LineTerminator. */
internal fun isJsWhitespace(c: Char): Boolean = when (c) {
    '\u0009', '\u000A', '\u000B', '\u000C', '\u000D', ' ', ' ',
    ' ', ' ', ' ', ' ', ' ', '　', '﻿',
    -> true
    in ' '..' ' -> true
    else -> false
}

internal fun jsTrim(s: String): String {
    var start = 0
    var end = s.length
    while (start < end && isJsWhitespace(s[start])) start++
    while (end > start && isJsWhitespace(s[end - 1])) end--
    return s.substring(start, end)
}

/**
 * UTF-8 exactly as `TextEncoder` produces it: surrogate pairs become one 4-byte
 * sequence and a lone surrogate becomes U+FFFD (`EF BF BD`). The JVM's encoder
 * would emit `?` for the lone surrogate instead.
 */
internal fun utf8(s: String): ByteArray {
    val out = ArrayList<Byte>(s.length * 2)
    fun put(b: Int) { out.add(b.toByte()) }
    var i = 0
    while (i < s.length) {
        val c = s[i].code
        var cp = c
        if (c in 0xD800..0xDBFF) {
            if (i + 1 < s.length && s[i + 1].code in 0xDC00..0xDFFF) {
                cp = 0x10000 + ((c - 0xD800) shl 10) + (s[i + 1].code - 0xDC00)
                i++
            } else {
                cp = 0xFFFD
            }
        } else if (c in 0xDC00..0xDFFF) {
            cp = 0xFFFD
        }
        when {
            cp < 0x80 -> put(cp)
            cp < 0x800 -> { put(0xC0 or (cp shr 6)); put(0x80 or (cp and 0x3F)) }
            cp < 0x10000 -> {
                put(0xE0 or (cp shr 12)); put(0x80 or ((cp shr 6) and 0x3F)); put(0x80 or (cp and 0x3F))
            }
            else -> {
                put(0xF0 or (cp shr 18)); put(0x80 or ((cp shr 12) and 0x3F))
                put(0x80 or ((cp shr 6) and 0x3F)); put(0x80 or (cp and 0x3F))
            }
        }
        i++
    }
    return out.toByteArray()
}

/** `parseInt(s, 16)`: leading whitespace, optional sign and `0x`, then hex digits; NaN if none. */
internal fun jsParseHex(input: String): Double {
    var s = jsTrim(input)
    var sign = 1.0
    if (s.startsWith("-")) { sign = -1.0; s = s.substring(1) } else if (s.startsWith("+")) s = s.substring(1)
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2)
    var value = 0.0
    var any = false
    for (ch in s) {
        val d = ch.digitToIntOrNull(16) ?: break
        value = value * 16 + d
        any = true
    }
    return if (any) sign * value else Double.NaN
}

/** ECMAScript `ToInt32`. */
internal fun jsToInt32(d: Double): Int {
    if (d.isNaN() || d.isInfinite()) return 0
    val t = if (d < 0) -floor(-d) else floor(d)
    val m = t.mod(4294967296.0)
    return m.toLong().toInt()
}

/** `Number.prototype.toString(16)` for integral values (NaN → "NaN"). */
internal fun jsHex(n: Double): String {
    if (n.isNaN()) return "NaN"
    val v = n.toLong()
    return if (v < 0) "-" + (-v).toString(16) else v.toString(16)
}
