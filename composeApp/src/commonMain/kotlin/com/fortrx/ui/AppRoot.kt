package com.fortrx.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import com.fortrx.ui.screens.MainScreen

@Composable
fun AppRoot() {
    Navigator(MainScreen())
}
