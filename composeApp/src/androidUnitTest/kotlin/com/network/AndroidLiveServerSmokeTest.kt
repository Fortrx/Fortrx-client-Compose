package com.network

import androidx.test.core.app.ApplicationProvider
import com.fortrx.platform.initPlatform
import com.fortrx.services.MessagingService
import com.fortrx.services.OnboardingService
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.koin.core.context.GlobalContext.get

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidLiveServerSmokeTest : LiveServerSmokeScenario() {
    @BeforeTest
    fun setup() {
        initPlatform(ApplicationProvider.getApplicationContext())
        setupScenario()
    }

    @Test
    fun aliceAndBobFullFlow_liveServerSmoke() = runTest {
        assumeTrue(liveServerTestSkipReason(), liveServerTestsEnabled())
        runAliceAndBobFullFlow(get().get(), get().get())
    }
}
