package io.github.pradyumnadp.blobface.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private fun prefersReducedMotion(): Boolean =
    js("typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches")

@Composable
internal actual fun rememberReducedMotion(): Boolean = remember { prefersReducedMotion() }
