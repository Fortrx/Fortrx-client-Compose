package com.fortrx.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object AuthApi {
    suspend fun register(username: String, email: String, password: String): JsonObject {
        val res = Api.postJson("/auth/register", buildJsonObject {
            put("username", username); put("email", email); put("password", password)
        })
        Api.raiseForStatus(res, "register"); return Api.jsonObject(res)
    }

    suspend fun login(username: String, password: String): String {
        val res = Api.postForm("/auth/login", mapOf("username" to username, "password" to password))
        Api.raiseForStatus(res, "login")
        val body = Api.jsonObject(res)
        val token = body["access_token"]?.jsonPrimitive?.content
            ?: throw FortrxApiError(500, "missing access_token", "login")
        Api.setToken(token); return token
    }

    suspend fun getMe(): JsonObject {
        val res = Api.getRequest("/auth/me")
        Api.raiseForStatus(res, "get_me"); return Api.jsonObject(res)
    }

    suspend fun getUser(userId: Long): JsonObject {
        val res = Api.getRequest("/auth/users/$userId")
        Api.raiseForStatus(res, "get_user"); return Api.jsonObject(res)
    }

    suspend fun getUserByUsername(username: String): JsonObject {
        val res = Api.getRequest("/auth/users/by-username/${username.encodeURLParam()}")
        Api.raiseForStatus(res, "get_user_by_username"); return Api.jsonObject(res)
    }
}
