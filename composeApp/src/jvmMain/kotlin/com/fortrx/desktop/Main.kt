package com.fortrx.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.fortrx.desktop.theme.FortrxTheme
import com.fortrx.di.appModule
import com.fortrx.ui.AppRoot
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(appModule)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = androidx.compose.ui.unit.Dp(1280f), height = androidx.compose.ui.unit.Dp(820f)),
            title = "Fortrx"
        ) {
            FortrxTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AppRoot() }
            }
        }
    }
}
