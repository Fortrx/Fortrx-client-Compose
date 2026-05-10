package com.fortrx.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fortrx.FortrxClient

enum class AppRoute { Onboarding, Chat, Settings }

private enum class ChatShellLayout { Compact, Medium, Expanded }

private fun resolveChatShellLayout(width: Dp): ChatShellLayout = when {
    width < 900.dp -> ChatShellLayout.Compact
    width < 1280.dp -> ChatShellLayout.Medium
    else -> ChatShellLayout.Expanded
}

@Composable
fun AppRoot() {
    var route by remember { mutableStateOf(AppRoute.Onboarding) }
    var selectedConversation by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (FortrxClient.tryAutoLogin()) {
            route = AppRoute.Chat
        }
    }
    when (route) {
        AppRoute.Onboarding -> OnboardingScreen(onDone = { route = AppRoute.Chat })
        AppRoute.Settings -> com.fortrx.desktop.ui.settings.SettingsScreen(onClose = { route = AppRoute.Chat })
        AppRoute.Chat -> Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                when (resolveChatShellLayout(maxWidth)) {
                    ChatShellLayout.Compact -> {
                        if (selectedConversation == null) {
                            com.fortrx.desktop.ui.chat.ConversationList(
                                selected = null,
                                onSelect = { selectedConversation = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            com.fortrx.desktop.ui.chat.ChatPane(
                                conversationId = selectedConversation,
                                modifier = Modifier.fillMaxSize(),
                                onBack = { selectedConversation = null }
                            )
                        }
                    }

                    ChatShellLayout.Medium, ChatShellLayout.Expanded -> {
                        val listWidth = if (maxWidth >= 1280.dp) 360.dp else 300.dp
                        Row(Modifier.fillMaxSize()) {
                            com.fortrx.desktop.ui.chat.LeftRail(
                                onSettings = { route = AppRoute.Settings },
                                onLogout = {
                                    FortrxClient.logout()
                                    selectedConversation = null
                                    route = AppRoute.Onboarding
                                }
                            )
                            com.fortrx.desktop.ui.chat.ConversationList(
                                selected = selectedConversation,
                                onSelect = { selectedConversation = it },
                                modifier = Modifier.width(listWidth).fillMaxHeight()
                            )
                            VerticalDivider(Modifier.fillMaxHeight().width(1.dp))
                            com.fortrx.desktop.ui.chat.ChatPane(
                                conversationId = selectedConversation,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}
