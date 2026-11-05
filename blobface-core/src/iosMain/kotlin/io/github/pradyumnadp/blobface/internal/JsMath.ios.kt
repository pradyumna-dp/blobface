package io.github.pradyumnadp.blobface.internal

internal actual object JsMath {
    actual fun sin(x: Double): Double = kotlin.math.sin(x)
    actual fun cos(x: Double): Double = kotlin.math.cos(x)
    actual fun atan2(y: Double, x: Double): Double = kotlin.math.atan2(y, x)
    actual fun asin(x: Double): Double = kotlin.math.asin(x)
    actual fun cbrt(x: Double): Double = platform.posix.cbrt(x)
    actual fun pow(x: Double, y: Double): Double = platform.posix.pow(x, y)
    actual fun exp(x: Double): Double = kotlin.math.exp(x)
}
