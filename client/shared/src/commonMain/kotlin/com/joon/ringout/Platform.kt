package com.joon.ringout

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform