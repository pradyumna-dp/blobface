package io.github.pradyumnadp.blobface

internal actual fun readFixtureFile(path: String): String =
    js("require('fs')").readFileSync(path, "utf8").unsafeCast<String>()

internal actual fun parseFixtureDouble(s: String): Double = s.toDouble()
