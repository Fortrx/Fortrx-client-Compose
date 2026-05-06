package com.example.fortrx_client

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform