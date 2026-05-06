package com.fortrx.storage

import com.fortrx.Settings
import com.fortrx.network.Api

object TokenStore {
    fun saveToken(token: String, password: String? = null) {
        val pw = password ?: Settings.storagePassword
            ?: throw StorageError("A storage password is required to save the token securely.")
        Db.saveToken(pw, token)
    }
    fun loadToken(password: String? = null): String? {
        val pw = password ?: Settings.storagePassword ?: return null
        return Db.loadToken(pw)
    }
    fun deleteToken() = Db.deleteToken()
    fun loadAndSetToken(password: String? = null): Boolean {
        val token = loadToken(password) ?: return false
        Api.setToken(token); return true
    }
}
