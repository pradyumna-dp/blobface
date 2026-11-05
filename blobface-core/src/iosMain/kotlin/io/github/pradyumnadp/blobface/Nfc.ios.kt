package io.github.pradyumnadp.blobface

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun nfc(s: String): String = (s as NSString).precomposedStringWithCanonicalMapping
