package com.network

import com.fortrx.services.MessagingService
import com.fortrx.services.OnboardingService
import org.junit.Assume.assumeTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.context.GlobalContext.get
import kotlin.test.BeforeTest
import kotlin.test.Test

class JvmLiveServerSmokeTest : LiveServerSmokeScenario() {
    @BeforeTest
    fun setup() {
        setupScenario()
    }

    @Test
    fun aliceAndBobFullFlow_liveServerSmoke() = runTest {
        assumeTrue(liveServerTestSkipReason(), liveServerTestsEnabled())
        runAliceAndBobFullFlow(get().get(), get().get())
    }
}
