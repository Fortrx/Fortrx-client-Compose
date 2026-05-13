package com.network

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fortrx.di.appModule
import com.fortrx.platform.initPlatform
import com.fortrx.services.MessagingService
import com.fortrx.services.OnboardingService
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.get
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class AndroidMultiUserTest : LiveServerSmokeScenario() {
    
    @BeforeTest
    fun androidSetup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initPlatform(context)
        
        stopKoin()
        startKoin {
            androidContext(context)
            modules(appModule)
        }
        
        setupScenario()
    }

    @Test
    fun testHimanshuAndPegasusInteraction() = runTest {
        // Use unique suffixes to avoid collisions when running on multiple devices simultaneously
        // This ensures that Device A and Device B don't try to use the same accounts on the server.
        val deviceSuffix = Random.nextInt(1000, 9999)
        runNamedUsersFlow(
            user1Name = "himanshu_$deviceSuffix",
            user1Email = "himanshu_$deviceSuffix@example.com",
            user2Name = "pegasus_$deviceSuffix",
            user2Email = "pegasus_$deviceSuffix@example.com",
            onboardingService = get().get<OnboardingService>(),
            messagingService = get().get<MessagingService>(),
            shouldRegister = true
        )
    }
}
