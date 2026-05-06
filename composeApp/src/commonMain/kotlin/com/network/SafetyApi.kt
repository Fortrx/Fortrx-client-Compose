package com.fortrx.network

import kotlinx.serialization.json.JsonObject

object SafetyApi {
    suspend fun fetchSafetyNumber(otherUserId: Long): JsonObject {
        val res = Api.getRequest("/safety/numbers/$otherUserId")
        Api.raiseForStatus(res, "fetch_safety_numbers"); return Api.jsonObject(res)
    }
    suspend fun fetchUserInfo(userId: Long): JsonObject {
        val res = Api.getRequest("/auth/users/$userId")
        Api.raiseForStatus(res, "fetch_user_info"); return Api.jsonObject(res)
    }
}
