package com.network

import androidx.test.core.app.ApplicationProvider
import com.fortrx.platform.initPlatform
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest

@RunWith(RobolectricTestRunner::class)
class AndroidRealServerIntegrationTest : RealServerIntegrationTest() {
    @BeforeTest
    override fun setup() {
        initPlatform(ApplicationProvider.getApplicationContext())
        super.setup()
    }
}
