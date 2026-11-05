package io.github.pradyumnadp.blobface

internal actual fun readFixtureFile(path: String): String = java.io.File(path).readText(Charsets.UTF_8)

internal actual fun parseFixtureDouble(s: String): Double = s.toDouble()
