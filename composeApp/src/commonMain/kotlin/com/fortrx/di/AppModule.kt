package com.fortrx.di

import com.fortrx.services.OnboardingService
import com.fortrx.services.MessagingService
import com.fortrx.services.SyncEngine
import org.koin.dsl.module

val appModule = module {
    single { OnboardingService() }
    single { MessagingService() }
    // SyncEngine might need different handling as it's started/stopped
}
