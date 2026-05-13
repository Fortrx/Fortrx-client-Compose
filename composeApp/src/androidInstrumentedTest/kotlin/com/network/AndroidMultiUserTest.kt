package com.network

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fortrx.platform.initPlatform
import com.fortrx.services.MessagingService
import com.fortrx.services.OnboardingService
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext.get
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

@RunWith(AndroidJUnit4::class)
class AndroidMultiUserTest : LiveServerSmokeScenario() {
    
    @BeforeTest
    fun androidSetup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initPlatform(context)
        setupScenario()
    }

    @Test
    fun testHimanshuAndPegasusInteraction() = runTest {
        // Run the interaction flow with specified users
        runNamedUsersFlow(
            user1Name = "himanshu",
            user1Email = "himanshu@example.com",
            user2Name = "pegasus",
            user2Email = "pegasus@example.com",
            onboardingService = get().get<OnboardingService>(),
            messagingService = get().get<MessagingService>(),
            shouldRegister = false
        )
    }
}
