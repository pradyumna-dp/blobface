package io.github.pradyumnadp.blobface.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import io.github.pradyumnadp.blobface.GazeMath

/** What a gazing blobface looks at. Mirrors upstream `GazeTarget`. */
public sealed interface GazeTarget {
    /** Look at nothing: eyes ease home and the idle glance comes back. */
    public data object None : GazeTarget

    /** Hold the eyes at center, deliberately not looking, with the idle glance stood down. */
    public data object Rest : GazeTarget

    /** Follow the pointer (mouse hover; on touch screens, a finger while it is down). */
    public data object Pointer : GazeTarget

    /** Look at a point in window coordinates (px). */
    public data class Point(public val position: Offset) : GazeTarget

    /** Look at the center of the element carrying [Modifier.gazeAnchor] for [anchor]. */
    public data class Anchor(public val anchor: GazeAnchor) : GazeTarget
}

/** A handle to an element the eyes can look at. Attach it with [Modifier.gazeAnchor]. */
@Stable
public class GazeAnchor {
    internal var center: Offset? by mutableStateOf(null)
}

/** Creates and remembers a [GazeAnchor]. */
@Composable
public fun rememberGazeAnchor(): GazeAnchor = remember { GazeAnchor() }

/** Records this element's window-space center as [anchor]'s position. */
public fun Modifier.gazeAnchor(anchor: GazeAnchor): Modifier = onGloballyPositioned {
    anchor.center = it.boundsInWindow().center
}

/**
 * Drives a blobface's eyes toward a [target]. Pass it as `Blobface(gaze = …)`.
 *
 * @param travel how far the eyes may turn, in viewBox units (upstream
 *   `--mo-track-travel` / `useGaze({ travel })`). 0 disables the excursion.
 * @param settle pursuit time constant in ms (default 110).
 * @param snap aim distance past which the eyes jump instead of easing (default 1.6).
 */
@Stable
public class BlobfaceGazeState(
    target: GazeTarget = GazeTarget.None,
    travel: Float = 3f,
    public val settle: Double = GazeMath.SETTLE,
    public val snap: Double = GazeMath.SNAP,
) {
    /** The current target. */
    public var target: GazeTarget by mutableStateOf(target)

    /** The current excursion, viewBox units. */
    public var travel: Float by mutableStateOf(travel)

    /** Points the eyes at [target]. */
    public fun lookAt(target: GazeTarget) {
        this.target = target
    }
}

/** Creates and remembers a [BlobfaceGazeState]. */
@Composable
public fun rememberBlobfaceGaze(
    target: GazeTarget = GazeTarget.None,
    travel: Float = 3f,
    settle: Double = GazeMath.SETTLE,
    snap: Double = GazeMath.SNAP,
): BlobfaceGazeState = remember(settle, snap) { BlobfaceGazeState(target, travel, settle, snap) }

/** Window-space pointer position shared with every gazing blobface underneath. */
@Stable
public class BlobfacePointer internal constructor() {
    /** Last known pointer position in window px, or null when it left. */
    public var position: Offset? by mutableStateOf(null)
        internal set
}

internal val LocalBlobfacePointer = staticCompositionLocalOf<BlobfacePointer?> { null }

/**
 * Makes the pointer anywhere inside [content] visible to every blobface using
 * [GazeTarget.Pointer], the way upstream's driver listens on the whole window.
 * Without it, a blobface only sees the pointer while it is over itself.
 */
@Composable
public fun BlobfacePointerArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pointer = remember { BlobfacePointer() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    androidx.compose.foundation.layout.Box(
        modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(pointer) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val c = e.changes.firstOrNull() ?: continue
                        val touch = c.type == PointerType.Touch
                        pointer.position = when {
                            e.type == PointerEventType.Exit -> null
                            touch && !c.pressed -> null
                            else -> origin + c.position
                        }
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalBlobfacePointer provides pointer) { content() }
    }
}

internal fun LayoutCoordinates.centerInWindow(): Offset = boundsInWindow().center
