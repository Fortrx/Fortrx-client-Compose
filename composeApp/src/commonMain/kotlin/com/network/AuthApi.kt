package com.fortrx.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object AuthApi {
    suspend fun register(username: String, email: String, password: String): JsonObject {
        val res = Api.postJson("/auth/register", buildJsonObject {
            put("username", username); put("email", email); put("password", password)
        }, allowAuthRetry = false)
        Api.raiseForStatus(res, "register"); return Api.jsonObject(res)
    }

    suspend fun login(username: String, password: String): String {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotEmpty()) { "Username is required" }
        val res = Api.postForm(
            "/auth/login",
            mapOf("username" to normalizedUsername, "password" to password),
            allowAuthRetry = false,
        )
        Api.raiseForStatus(res, "login")
        val body = Api.jsonObject(res)
        val token = body["access_token"]?.jsonPrimitive?.content
            ?: throw FortrxApiError(500, "missing access_token", "login")
        Api.setToken(token)
        return token
    }

    suspend fun reauth(password: String): String {
        val res = Api.postJson("/auth/reauth", buildJsonObject {
            put("password", password)
        })
        Api.raiseForStatus(res, "reauth")
        return Api.jsonObject(res)["reauth_token"]?.jsonPrimitive?.content
            ?: throw FortrxApiError(500, "missing reauth_token", "reauth")
    }

    suspend fun getMe(): JsonObject {
        val res = Api.getRequest("/auth/me")
        Api.raiseForStatus(res, "get_me"); return Api.jsonObject(res)
    }

    suspend fun getUser(userId: Long): JsonObject {
        require(userId > 0) { "User id must be positive" }
        val res = Api.getRequest("/auth/users/$userId")
        Api.raiseForStatus(res, "get_user"); return Api.jsonObject(res)
    }

    suspend fun getUserByUsername(username: String): JsonObject {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotEmpty()) { "Username is required" }
        require(normalizedUsername.length <= 64) { "Username is too long" }
        val res = Api.getRequest("/auth/users/by-username/${normalizedUsername.encodeURLParam()}")
        Api.raiseForStatus(res, "get_user_by_username"); return Api.jsonObject(res)
    }
}
