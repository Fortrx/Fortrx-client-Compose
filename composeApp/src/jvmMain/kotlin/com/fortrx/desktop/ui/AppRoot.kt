package com.fortrx.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fortrx.FortrxClient

enum class AppRoute { Onboarding, Chat, Settings }

@Composable
fun AppRoot() {
    var route by remember { mutableStateOf(AppRoute.Onboarding) }
    var selectedConversation by remember { mutableStateOf<String?>(null) }

    // Using the flatter package structure
    when (route) {
        AppRoute.Onboarding -> OnboardingScreen(onDone = { route = AppRoute.Chat })
        AppRoute.Settings -> com.fortrx.desktop.ui.settings.SettingsScreen(onClose = { route = AppRoute.Chat })
        AppRoute.Chat -> Row(Modifier.fillMaxSize()) {
            com.fortrx.desktop.ui.chat.LeftRail(
                onSettings = { route = AppRoute.Settings },
                onLogout = { 
                    FortrxClient.logout()
                    route = AppRoute.Onboarding 
                }
            )
            com.fortrx.desktop.ui.chat.ConversationList(
                selected = selectedConversation,
                onSelect = { selectedConversation = it },
                modifier = Modifier.width(320.dp).fillMaxHeight()
            )
            Divider(Modifier.fillMaxHeight().width(1.dp))
            com.fortrx.desktop.ui.chat.ChatPane(conversationId = selectedConversation, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}
