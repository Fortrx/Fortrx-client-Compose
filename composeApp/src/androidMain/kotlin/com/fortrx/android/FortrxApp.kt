package com.fortrx.android

import android.app.Application
import com.fortrx.platform.AndroidContextHolder

class FortrxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContextHolder.appContext = this
    }
}
