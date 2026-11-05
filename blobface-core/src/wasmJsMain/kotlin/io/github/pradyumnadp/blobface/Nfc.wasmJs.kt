package io.github.pradyumnadp.blobface

private fun jsNfc(s: String): String = js("s.normalize('NFC')")

internal actual fun nfc(s: String): String = jsNfc(s)
