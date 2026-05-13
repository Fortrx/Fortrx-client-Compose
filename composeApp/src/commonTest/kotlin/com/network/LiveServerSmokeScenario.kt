package com.network

import com.fortrx.Settings
import com.fortrx.network.Api
import com.fortrx.network.MessagesApi
import com.fortrx.platform.debugLog
import com.fortrx.platform.readRuntimeEnv
import com.fortrx.services.MessagingService
import com.fortrx.services.OnboardingService
import com.fortrx.storage.Db
import com.fortrx.storage.SettingsStore
import com.fortrx.storage.TokenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

open class LiveServerSmokeScenario {

    protected val password = "password"
    
    fun liveServerTestsEnabled(): Boolean =
        readRuntimeEnv("FORTRX_RUN_LIVE_SERVER_TESTS")?.equals("true", ignoreCase = true) == true

    fun liveServerTestSkipReason(): String =
        "Live server smoke tests require FORTRX_RUN_LIVE_SERVER_TESTS=true"

    private fun configuredServerUrl(): String =
        readRuntimeEnv("FORTRX_TEST_SERVER_URL")?.trim()?.takeIf { it.isNotEmpty() }
            ?: Settings.DEFAULT_SERVER_URL

    open fun setupScenario() {
        com.fortrx.platform.initPlatform()
        Settings.serverUrl = configuredServerUrl()
        Db.close()
    }

    suspend fun runAliceAndBobFullFlow(onboardingService: OnboardingService, messagingService: MessagingService) {
        val randomSuffix = Random.nextInt(1000, 9999)
        runNamedUsersFlow(
            "alice_$randomSuffix", "alice_$randomSuffix@example.com",
            "bob_$randomSuffix", "bob_$randomSuffix@example.com",
            onboardingService, messagingService,
            shouldRegister = true
        )
    }

    suspend fun runNamedUsersFlow(
        user1Name: String, user1Email: String,
        user2Name: String, user2Email: String,
        onboardingService: OnboardingService,
        messagingService: MessagingService,
        shouldRegister: Boolean = false
    ) {
        debugLog("Starting NamedUsersFlow: $user1Name -> $user2Name")
        
        // 1. Setup User 2
        switchToUser(user2Name, user2Email, onboardingService, messagingService, shouldRegister)
        val user2Id = Settings.myId ?: error("$user2Name setup failed")
        
        // 2. Setup User 1
        switchToUser(user1Name, user1Email, onboardingService, messagingService, shouldRegister)
        val user1Id = Settings.myId ?: error("$user1Name setup failed")

        // 3. User 1 sends message to User 2
        val msg1 = "Hello $user2Name! This is $user1Name from test [${Random.nextInt(1000)}]."
        debugLog("User 1 ($user1Id) sending message to User 2 ($user2Id)...")
        messagingService.sendText(password, user1Id, user2Id, msg1)

        // 4. Switch to User 2 and receive message
        switchToUser(user2Name, user2Email, onboardingService, messagingService, shouldRegister = false)
        val receivedMsg = waitForMessage(messagingService, password, user2Id, user1Id, timeoutMillis = 20000)
        
        assertNotNull(receivedMsg, "$user2Name should have received a message from $user1Name")
        assertTrue(receivedMsg.contains(msg1), "Message content mismatch. Expected: $msg1, Got: $receivedMsg")
        debugLog("User 2 received message successfully.")

        // 5. User 2 replies to User 1
        val msg2 = "Hi $user1Name! Reply from test [${Random.nextInt(1000)}]."
        debugLog("User 2 ($user2Id) sending reply to User 1 ($user1Id)...")
        messagingService.sendText(password, user2Id, user1Id, msg2)

        // 6. Switch back to User 1 and receive reply
        switchToUser(user1Name, user1Email, onboardingService, messagingService, shouldRegister = false)
        val receivedReply = waitForMessage(messagingService, password, user1Id, user2Id, timeoutMillis = 20000)

        assertNotNull(receivedReply, "$user1Name should have received a message from $user2Name")
        assertTrue(receivedReply.contains(msg2), "Reply content mismatch. Expected: $msg2, Got: $receivedReply")
        debugLog("User 1 received reply successfully. Flow complete.")
    }

    private suspend fun waitForMessage(
        messagingService: MessagingService,
        password: String,
        myId: Long,
        senderId: Long,
        timeoutMillis: Long
    ): String? {
        var elapsed = 0L
        while (elapsed < timeoutMillis) {
            // First try inbox (consumes message)
            val inbox = messagingService.fetchAndStoreInbox(password, myId)
            val inboxMsg = inbox.find { it["sender_id"]?.jsonPrimitive?.longOrNull == senderId }
            if (inboxMsg != null) return inboxMsg["body"]?.jsonPrimitive?.content

            // If not in inbox, it might have been consumed by another client. Try local conversation history.
            val localMsgs = Db.listConversation(password, senderId)
            val localMsg = localMsgs.find { it.senderId == senderId && it.plaintext != null }
            if (localMsg != null) return localMsg.plaintext

            delay(2000)
            elapsed += 2000
        }
        return null
    }

    private suspend fun switchToUser(
        username: String,
        email: String,
        onboardingService: OnboardingService,
        messagingService: MessagingService,
        shouldRegister: Boolean
    ) {
        Api.setToken(null)
        messagingService.resetCaches()
        if (runCatching { Db.database }.isSuccess) {
            TokenStore.deleteToken()
        }
        Db.close()
        Settings.myId = null
        Settings.myUsername = null
        Settings.storagePassword = null
        SettingsStore.clear()

        if (shouldRegister) {
            onboardingService.register(username, email, password)
        } else {
            onboardingService.login(username, password)
        }
    }
}
