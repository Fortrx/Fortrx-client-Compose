package com.fortrx.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.fortrx.services.ErrorService
import com.fortrx.ui.screens.MainScreen
import com.fortrx.ui.theme.FortrxTheme
import org.koin.compose.koinInject

@Composable
fun AppRoot() {
    val errorService = koinInject<ErrorService>()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        errorService.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    FortrxTheme {
        Box(Modifier.fillMaxSize()) {
            Navigator(MainScreen()) { navigator ->
                SlideTransition(navigator)
            }
            
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
