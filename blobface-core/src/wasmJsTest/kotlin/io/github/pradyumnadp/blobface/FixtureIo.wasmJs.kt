package io.github.pradyumnadp.blobface

// Kotlin/Wasm tests run as an ES module, where `require` does not exist.
private fun nodeRead(path: String): String =
    js("globalThis.process.getBuiltinModule('fs').readFileSync(path, 'utf8')")

internal actual fun readFixtureFile(path: String): String = nodeRead(path)

private fun jsParseFloat(s: String): Double = js("parseFloat(s)")

internal actual fun parseFixtureDouble(s: String): Double = jsParseFloat(s)
