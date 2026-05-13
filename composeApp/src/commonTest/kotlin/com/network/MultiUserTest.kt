package com.network

import com.fortrx.platform.initPlatform
import com.fortrx.services.MessagingService
import com.fortrx.services.OnboardingService
import kotlinx.coroutines.test.runTest
import org.koin.core.context.GlobalContext.get
import kotlin.test.BeforeTest
import kotlin.test.Test

open class MultiUserTest : LiveServerSmokeScenario() {

    @BeforeTest
    fun setup() {
        // Basic setup, will be overridden in platform-specific tests if needed
        setupScenario()
    }

    @Test
    fun testHimanshuAndPegasusInteraction() = runTest {
        // We use password "password" as specified by the user
        runNamedUsersFlow(
            user1Name = "himanshu",
            user1Email = "himanshu@example.com",
            user2Name = "pegasus",
            user2Email = "pegasus@example.com",
            onboardingService = get().get<OnboardingService>(),
            messagingService = get().get<MessagingService>(),
            shouldRegister = false // User said they are already logged in/exist
        )
    }
}
