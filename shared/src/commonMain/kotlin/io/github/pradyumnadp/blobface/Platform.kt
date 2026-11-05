package io.github.pradyumnadp.blobface

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform