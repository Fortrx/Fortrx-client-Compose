package com.fortrx.network

import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String? = null,
    val deviceId: String? = null,
    val accessExpiresAt: Long? = null,
    val refreshExpiresAt: Long? = null,
)
