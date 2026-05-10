package com.fortrx.platform

expect object NotificationBridge {
    fun showIncomingMessage(title: String, body: String)
}
