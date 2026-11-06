package io.github.pradyumnadp.blobface

// Kotlin/Wasm tests run as an ES module, where `require` does not exist.
// Kotlin 2.2 bundles Node 22.0.0, which predates process.getBuiltinModule,
// so import the built-in module through @JsModule instead.
@JsModule("node:fs")
private external object NodeFs {
    fun readFileSync(path: String, encoding: String): String
}

internal actual fun readFixtureFile(path: String): String = NodeFs.readFileSync(path, "utf8")

private fun jsParseFloat(s: String): Double = js("parseFloat(s)")

internal actual fun parseFixtureDouble(s: String): Double = jsParseFloat(s)
