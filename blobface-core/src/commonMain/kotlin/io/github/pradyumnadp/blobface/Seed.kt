package io.github.pradyumnadp.blobface

import io.github.pradyumnadp.blobface.internal.jsTrim

/**
 * Unicode NFC normalization, provided per platform (Kotlin common has none).
 *
 * The result depends on the platform's Unicode tables, exactly as the upstream
 * JavaScript depends on the engine's.
 */
internal expect fun nfc(s: String): String

/**
 * Normalizes a name so that inputs a human considers equal hash equally:
 * NFC, then JavaScript-style trim, then locale-independent lowercase.
 *
 * `"Alain@Example.com "` and `"alain@example.com"` normalize to the same seed, as
 * do the precomposed and decomposed spellings of `café`.
 */
public fun normalizeSeed(seed: String): String = jsTrim(nfc(seed)).lowercase()
