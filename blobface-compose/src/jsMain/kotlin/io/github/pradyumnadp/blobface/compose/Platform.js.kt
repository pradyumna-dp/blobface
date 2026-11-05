package io.github.pradyumnadp.blobface.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberReducedMotion(): Boolean = remember {
    js("typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches")
        .unsafeCast<Boolean>()
}
