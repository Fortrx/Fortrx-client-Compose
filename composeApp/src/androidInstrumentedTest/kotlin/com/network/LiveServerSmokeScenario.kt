package com.network

import com.fortrx.Settings
import com.fortrx.network.Api
import com.fortrx.platform.readRuntimeEnv
import com.fortrx.services.MessagingService
import com.fortrx.services.OnboardingService
import com.fortrx.storage.Db
import com.fortrx.storage.SettingsStore
import com.fortrx.storage.TokenStore
import kotlinx.coroutines.test.runTest
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
        // 1. Setup User 2
        switchToUser(user2Name, user2Email, onboardingService, messagingService, shouldRegister)
        val user2Id = Settings.myId ?: error("$user2Name setup failed")
        
        // 2. Setup User 1
        switchToUser(user1Name, user1Email, onboardingService, messagingService, shouldRegister)
        val user1Id = Settings.myId ?: error("$user1Name setup failed")

        // 3. User 1 sends message to User 2
        val msg1 = "Hello $user2Name! This is $user1Name from the integration test."
        messagingService.sendText(password, user1Id, user2Id, msg1)

        // 4. Switch to User 2 and receive message
        switchToUser(user2Name, user2Email, onboardingService, messagingService, shouldRegister = false)
        val bobInbox = messagingService.fetchAndStoreInbox(password, user2Id)
        assertTrue(bobInbox.isNotEmpty(), "$user2Name's inbox should not be empty")
        
        val receivedMsg = bobInbox.find { it["sender_id"]?.jsonPrimitive?.longOrNull == user1Id }
        assertNotNull(receivedMsg, "$user2Name should have received a message from $user1Name")
        assertEquals(msg1, receivedMsg["body"]?.jsonPrimitive?.content, "Message content mismatch")

        // 5. User 2 replies to User 1
        val msg2 = "Hi $user1Name! I received your message on the real server."
        messagingService.sendText(password, user2Id, user1Id, msg2)

        // 6. Switch back to User 1 and receive reply
        switchToUser(user1Name, user1Email, onboardingService, messagingService, shouldRegister = false)
        val aliceInbox = messagingService.fetchAndStoreInbox(password, user1Id)
        assertTrue(aliceInbox.isNotEmpty(), "$user1Name's inbox should not be empty")

        val receivedReply = aliceInbox.find { it["sender_id"]?.jsonPrimitive?.longOrNull == user2Id }
        assertNotNull(receivedReply, "$user1Name should have received a message from $user2Name")
        assertEquals(msg2, receivedReply["body"]?.jsonPrimitive?.content, "Reply content mismatch")
    }

    private suspend fun switchToUser(
        username: String,
        email: String,
        onboardingService: OnboardingService,
        messagingService: MessagingService,
        shouldRegister: Boolean
    ) {
        Db.close()
        Api.setToken(null)
        Settings.myId = null
        Settings.myUsername = null
        Settings.storagePassword = null
        SettingsStore.clear()
        TokenStore.deleteToken()
        messagingService.resetCaches()

        if (shouldRegister) {
            onboardingService.register(username, email, password)
        } else {
            onboardingService.login(username, password)
        }
    }
}
