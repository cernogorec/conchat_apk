package com.example.minimalapp.conchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConchatScreen(vm: ConchatViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.mentionEvent.collect {
            FrogSoundPlayer.play()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(52.dp),
                title = { },
                actions = {
                    if (state.currentScreen == MainScreen.CHAT) {
                        val isDisconnectedOrError = !state.isPolling || state.status.contains("ошибка", ignoreCase = true)
                        val connectTint = if (isDisconnectedOrError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            androidx.compose.ui.graphics.Color(0xFF2E7D32)
                        }

                        IconButton(onClick = vm::connectAndStart) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Подключить",
                                tint = connectTint
                            )
                        }

                        IconButton(onClick = vm::stopPolling) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Стоп"
                            )
                        }
                    }

                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Меню"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Чат") },
                            onClick = { vm.openChat(); menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Настройки") },
                            onClick = { vm.openSettings(); menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Войти как другой") },
                            onClick = { vm.openLogin(); menuExpanded = false }
                        )
                    }
                }
            )
        }
    ) { padding ->
        when (state.currentScreen) {
            MainScreen.LOGIN -> LoginContent(
                state = state,
                onUsernameChange = vm::setLoginUsername,
                onPasswordChange = vm::setLoginPassword,
                onLogin = vm::login,
                onOpenSettings = vm::openSettings,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            )

            MainScreen.CHAT -> ChatContent(
                state = state,
                onInputChange = vm::setInputMessage,
                onNicknameClick = vm::addNicknameToInput,
                onSend = vm::sendCurrentMessage,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp)
            )

            MainScreen.SETTINGS -> SettingsContent(
                settings = state.settings,
                onChanged = vm::updateSettings,
                onBack = vm::openChat,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun LoginContent(
    state: ConchatUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("leprosorium.ru", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.loginUsername,
            onValueChange = onUsernameChange,
            label = { Text("Логин") },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.loginPassword,
            onValueChange = onPasswordChange,
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        if (state.loginError.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.loginError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onLogin,
            enabled = !state.isLoggingIn,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoggingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Войти")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings) {
            Text("Ввести данные вручную")
        }
    }
}

@Composable
private fun ChatContent(
    state: ConchatUiState,
    onInputChange: (String) -> Unit,
    onNicknameClick: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageCard(
                    message = message,
                    myName = state.settings.name,
                    onNicknameClick = onNicknameClick
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.inputMessage,
                onValueChange = onInputChange,
                label = { Text("Сообщение или команда") },
                singleLine = true
            )
            Button(onClick = onSend) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun SettingsContent(
    settings: ChatSettings,
    onChanged: ((ChatSettings) -> ChatSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Настройки подключения", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = settings.subLepra,
            onValueChange = { onChanged { s -> s.copy(subLepra = it) } },
            label = { Text("sublepra (optional)") },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = settings.uid,
            onValueChange = { onChanged { s -> s.copy(uid = it) } },
            label = { Text("uid") },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = settings.sid,
            onValueChange = { onChanged { s -> s.copy(sid = it) } },
            label = { Text("sid") },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = settings.session,
            onValueChange = { onChanged { s -> s.copy(session = it) } },
            label = { Text("wikilepro_session") },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = settings.csrfToken,
            onValueChange = { onChanged { s -> s.copy(csrfToken = it) } },
            label = { Text("csrf_token") },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = settings.name,
            onValueChange = { onChanged { s -> s.copy(name = it) } },
            label = { Text("name") },
            singleLine = true
        )
        Button(onClick = onBack) {
            Text("Сохранить и в чат")
        }
    }
}

@Composable
private fun MessageCard(
    message: ChatMessage,
    myName: String,
    onNicknameClick: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val isMine = myName.isNotBlank() && message.login == myName
    val stamp = if (message.createdUnix > 0) {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        format.format(Date(message.createdUnix * 1000))
    } else {
        "--:--"
    }

    val cardColors = if (message.isSystem) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    } else if (message.isMention) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stamp,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (message.isSystem) {
                    Text(
                        text = message.login,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        text = message.login,
                        modifier = Modifier.clickable { onNicknameClick(message.login) },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isMine) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
            LinkifiedMessageText(
                text = message.body,
                onLinkClick = { rawUrl ->
                    val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                        rawUrl
                    } else {
                        "https://$rawUrl"
                    }
                    uriHandler.openUri(normalized)
                }
            )
            if (message.isMention) {
                Text("Упоминание", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LinkifiedMessageText(
    text: String,
    onLinkClick: (String) -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            URL_REGEX.findAll(text).forEach { match ->
                val start = match.range.first
                val endExclusive = match.range.last + 1

                if (start > cursor) {
                    append(text.substring(cursor, start))
                }

                val url = match.value
                pushStringAnnotation(tag = URL_TAG, annotation = url)
                pushStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                )
                append(url)
                pop()
                pop()

                cursor = endExclusive
            }

            if (cursor < text.length) {
                append(text.substring(cursor))
            }
        }
    }

    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        onClick = { offset ->
            annotated
                .getStringAnnotations(tag = URL_TAG, start = offset, end = offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        }
    )
}

private const val URL_TAG = "URL"
private val URL_REGEX = Regex("(https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
