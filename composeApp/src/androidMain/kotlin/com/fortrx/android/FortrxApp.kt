package com.fortrx.android

import android.app.Application
import com.fortrx.di.appModule
import com.fortrx.platform.AndroidContextHolder
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class FortrxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContextHolder.appContext = this

        startKoin {
            androidLogger()
            androidContext(this@FortrxApp)
            modules(appModule)
        }
    }
}
