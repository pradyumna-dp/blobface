package io.github.pradyumnadp.blobface.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
internal actual fun rememberReducedMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
