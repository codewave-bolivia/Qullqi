package com.codewave.qullqi

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform