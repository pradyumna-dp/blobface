package io.github.pradyumnadp.blobface.internal

internal actual object JsMath {
    actual fun sin(x: Double): Double = js("Math.sin(x)").unsafeCast<Double>()
    actual fun cos(x: Double): Double = js("Math.cos(x)").unsafeCast<Double>()
    actual fun atan2(y: Double, x: Double): Double = js("Math.atan2(y, x)").unsafeCast<Double>()
    actual fun asin(x: Double): Double = js("Math.asin(x)").unsafeCast<Double>()
    actual fun cbrt(x: Double): Double = js("Math.cbrt(x)").unsafeCast<Double>()
    actual fun pow(x: Double, y: Double): Double = js("Math.pow(x, y)").unsafeCast<Double>()
    actual fun exp(x: Double): Double = js("Math.exp(x)").unsafeCast<Double>()
}
