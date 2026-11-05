package io.github.pradyumnadp.blobface.internal

private fun mSin(x: Double): Double = js("Math.sin(x)")
private fun mCos(x: Double): Double = js("Math.cos(x)")
private fun mAtan2(y: Double, x: Double): Double = js("Math.atan2(y, x)")
private fun mAsin(x: Double): Double = js("Math.asin(x)")
private fun mCbrt(x: Double): Double = js("Math.cbrt(x)")
private fun mPow(x: Double, y: Double): Double = js("Math.pow(x, y)")
private fun mExp(x: Double): Double = js("Math.exp(x)")

internal actual object JsMath {
    actual fun sin(x: Double): Double = mSin(x)
    actual fun cos(x: Double): Double = mCos(x)
    actual fun atan2(y: Double, x: Double): Double = mAtan2(y, x)
    actual fun asin(x: Double): Double = mAsin(x)
    actual fun cbrt(x: Double): Double = mCbrt(x)
    actual fun pow(x: Double, y: Double): Double = mPow(x, y)
    actual fun exp(x: Double): Double = mExp(x)
}
