package io.github.pradyumnadp.blobface.internal

internal actual object JsMath {
    actual fun sin(x: Double): Double = StrictMath.sin(x)
    actual fun cos(x: Double): Double = StrictMath.cos(x)
    actual fun atan2(y: Double, x: Double): Double = StrictMath.atan2(y, x)
    actual fun asin(x: Double): Double = StrictMath.asin(x)
    actual fun cbrt(x: Double): Double = StrictMath.cbrt(x)
    actual fun pow(x: Double, y: Double): Double = StrictMath.pow(x, y)
    actual fun exp(x: Double): Double = StrictMath.exp(x)
}
