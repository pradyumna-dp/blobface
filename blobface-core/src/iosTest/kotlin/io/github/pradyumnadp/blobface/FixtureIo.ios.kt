package io.github.pradyumnadp.blobface

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
internal actual fun readFixtureFile(path: String): String =
    NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: error("cannot read $path")

internal actual fun parseFixtureDouble(s: String): Double = s.toDouble()
