package com.network

import com.fortrx.Settings
import com.fortrx.services.MessagingService
import com.fortrx.services.OnboardingService
import com.fortrx.storage.Db
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.*

class RealServerIntegrationTest {

    private val password = "password"
    
    @BeforeTest
    fun setup() {
        Settings.serverUrl = Settings.DEFAULT_SERVER_URL
    }

    @Test
    fun testAliceAndBobFullFlow() = runTest {
        val randomSuffix = Random.nextInt(1000, 9999)
        val aliceUsername = "alice_$randomSuffix"
        val bobUsername = "bob_$randomSuffix"
        val aliceEmail = "alice_$randomSuffix@gmail.com"
        val bobEmail = "bob_$randomSuffix@gmail.com"

        println("--- STARTING REAL SERVER INTEGRATION TEST ---")
        println("Alice: $aliceUsername, Bob: $bobUsername")

        // 1. Register Bob
        println("Registering Bob...")
        OnboardingService.register(bobUsername, bobEmail, password)
        val bobId = Settings.myId ?: error("Bob registration failed")
        println("Bob registered with ID: $bobId")

        // 2. Register Alice
        println("Registering Alice...")
        // We need to clear local DB/state to simulate a second client on the same machine
        Db.deleteDatabase(bobId) 
        OnboardingService.register(aliceUsername, aliceEmail, password)
        val aliceId = Settings.myId ?: error("Alice registration failed")
        println("Alice registered with ID: $aliceId")

        // 3. Alice sends message to Bob
        println("Alice sending message to Bob...")
        val msg1 = "Hello Bob! This is Alice from the integration test."
        MessagingService.sendText(recipientId = bobId, plaintext = msg1)
        println("Message sent.")

        // 4. Switch to Bob and receive message
        println("Switching to Bob...")
        // Simulate Bob logging in on his device
        Settings.myId = bobId
        Settings.myUsername = bobUsername
        Api.setToken(null) // Reset token to force login/refresh
        Db.deleteDatabase(aliceId)
        OnboardingService.login(bobUsername, password) 
        
        println("Bob fetching inbox...")
        val bobInbox = MessagingService.fetchAndStoreInbox(password, bobId)
        assertTrue(bobInbox.isNotEmpty(), "Bob's inbox should not be empty")
        
        val receivedMsg = bobInbox.find { it["sender_id"]?.toString()?.toLongOrNull() == aliceId }
        assertNotNull(receivedMsg, "Bob should have received a message from Alice")
        assertEquals(msg1, receivedMsg["body"]?.toString()?.removeSurrounding("\""), "Message content mismatch")
        println("Bob received: ${receivedMsg["body"]}")

        // 5. Bob replies to Alice
        println("Bob replying to Alice...")
        val msg2 = "Hi Alice! I received your message on the real server."
        MessagingService.sendText(recipientId = aliceId, plaintext = msg2)
        println("Reply sent.")

        // 6. Switch back to Alice and receive reply
        println("Switching back to Alice...")
        Settings.myId = aliceId
        Settings.myUsername = aliceUsername
        Api.setToken(null)
        Db.deleteDatabase(bobId)
        OnboardingService.login(aliceUsername, password)

        println("Alice fetching inbox...")
        val aliceInbox = MessagingService.fetchAndStoreInbox(password, aliceId)
        assertTrue(aliceInbox.isNotEmpty(), "Alice's inbox should not be empty")

        val receivedReply = aliceInbox.find { it["sender_id"]?.toString()?.toLongOrNull() == bobId }
        assertNotNull(receivedReply, "Alice should have received a message from Bob")
        assertEquals(msg2, receivedReply["body"]?.toString()?.removeSurrounding("\""), "Reply content mismatch")
        println("Alice received: ${receivedReply["body"]}")

        println("--- REAL SERVER INTEGRATION TEST SUCCESSFUL ---")
    }
}
