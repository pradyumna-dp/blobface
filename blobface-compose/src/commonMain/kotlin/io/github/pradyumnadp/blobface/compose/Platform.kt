package io.github.pradyumnadp.blobface.compose

import androidx.compose.runtime.Composable

/**
 * Whether the user asked the platform to reduce motion (Android: animator
 * duration scale 0; iOS: Reduce Motion; web: `prefers-reduced-motion`). Desktop
 * JVM has no such setting and reports false.
 */
@Composable
internal expect fun rememberReducedMotion(): Boolean
