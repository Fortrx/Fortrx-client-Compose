package com.fortrx.platform

actual object NotificationBridge {
    actual fun showIncomingMessage(title: String, body: String) {
        // Desktop notifications are intentionally left as a no-op for now.
    }
}
