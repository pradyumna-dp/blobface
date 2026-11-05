package io.github.pradyumnadp.blobface

import java.text.Normalizer

internal actual fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)
