package com.fortrx

object Settings {
    const val DEFAULT_SERVER_URL = "https://fortrx-server.duckdns.org"
    var serverUrl: String = DEFAULT_SERVER_URL
    var dbFilePath: String = "fortrx.db"
    var requestTimeoutSeconds: Long = 30
    var storagePassword: String? = null
    var myId: Long? = null
    var myUsername: String? = null
    var myDeviceId: String? = null

    /**
     * The ID of the contact whose chat is currently visible to the user.
     * Used to suppress redundant notifications.
     */
    var currentlyOpenContactId: Long? = null
}
