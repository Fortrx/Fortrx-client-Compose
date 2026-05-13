package com.fortrx.di

import com.fortrx.FortrxClient
import com.fortrx.services.OnboardingService
import com.fortrx.services.MessagingService
import com.fortrx.services.SyncEngine
import com.fortrx.services.ErrorService
import com.fortrx.ui.screens.ChatScreenModel
import com.fortrx.ui.screens.ChatListScreenModel
import com.fortrx.ui.screens.MainScreenModel
import com.fortrx.ui.screens.OnboardingScreenModel
import com.fortrx.ui.screens.SettingsScreenModel
import com.fortrx.ui.screens.RatchetDashboardScreenModel
import org.koin.dsl.module

val appModule = module {
    single { ErrorService() }
    single { MessagingService(get()) }
    single { OnboardingService(get(), get()) }
    single { FortrxClient(get(), get()) }
    
    factory { SettingsScreenModel(get(), get()) }
    factory { MainScreenModel(get()) }
    factory { OnboardingScreenModel(get(), get()) }
    factory { ChatListScreenModel(get(), get()) }
    factory<ChatScreenModel> { (contactId: Long) -> ChatScreenModel(contactId, get(), get()) }
    factory<RatchetDashboardScreenModel> { (contactId: Long) -> RatchetDashboardScreenModel(contactId) }
}
