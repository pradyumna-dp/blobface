package io.github.pradyumnadp.blobface.internal

import io.github.pradyumnadp.blobface.normalizeSeed

/*
 * Seed hashing, ported from blobatar's `hash.ts`.
 *
 * A murmur3-style byte mixer folds the seed into a 32-bit state once; every trait
 * key then continues from that state through the murmur3 finalizer, so trait
 * values are independent of one another.
 */

private const val SEP: Int = 0xFF
private const val C1: Int = 0xCC9E2D51.toInt() // 3432918353
private const val F1: Int = 0x85EBCA6B.toInt() // 2246822507
private const val F2: Int = 0xC2B2AE35.toInt() // 3266489909
private const val SEED: Int = 1779033703

private val SEP_BYTES = byteArrayOf(SEP.toByte())

/** Mixes bytes into a 32-bit state (`Math.imul` ≡ wrapping `Int` multiply). */
internal fun feed(state: Int, bytes: ByteArray): Int {
    var h = state
    for (b in bytes) {
        h = (h xor (b.toInt() and 0xFF)) * C1
        h = (h shl 13) or (h ushr 19)
    }
    return h
}

/** murmur3 fmix32, returned as the unsigned value JS's `>>> 0` produces. */
internal fun finalize(state: Int): Long {
    var h = state
    h = (h xor (h ushr 16)) * F1
    h = (h xor (h ushr 13)) * F2
    return (h xor (h ushr 16)).toLong() and 0xFFFFFFFFL
}

/** Hashes the (optionally normalized) seed once. `length` is UTF-16 units, as in JS. */
internal fun seedState(seed: String, normalize: Boolean = true): Int {
    val s = if (normalize) normalizeSeed(seed) else seed
    return feed(SEED xor s.length, utf8(s))
}

/** One uniform double in [0, 1) for `key`, independent of every other key. */
internal fun stream(state: Int, key: String): Double =
    finalize(feed(feed(state, SEP_BYTES), utf8(key))).toDouble() / 4294967296.0
