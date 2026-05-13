package com.network

import com.fortrx.platform.initPlatform
import com.fortrx.services.MessagingService
import com.fortrx.services.OnboardingService
import kotlinx.coroutines.test.runTest
import org.koin.core.context.GlobalContext.get
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.random.Random

open class MultiUserTest : LiveServerSmokeScenario() {

    @Test
    fun testHimanshuAndPegasusInteraction() = runTest {
        // Use unique suffixes to avoid collisions when running on multiple devices simultaneously
        val deviceSuffix = Random.nextInt(100, 999)
        runNamedUsersFlow(
            user1Name = "himanshu_$deviceSuffix",
            user1Email = "himanshu_$deviceSuffix@example.com",
            user2Name = "pegasus_$deviceSuffix",
            user2Email = "pegasus_$deviceSuffix@example.com",
            onboardingService = get().get<OnboardingService>(),
            messagingService = get().get<MessagingService>(),
            shouldRegister = true // Register them for the test run
        )
    }
}
