package io.github.pradyumnadp.blobface

internal actual fun nfc(s: String): String = s.asDynamic().normalize("NFC").unsafeCast<String>()
