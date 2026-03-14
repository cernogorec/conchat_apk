package com.example.minimalapp.conchat

import android.app.Application
import android.speech.tts.TextToSpeech
import android.text.Html
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class MainScreen {
    LOGIN,
    CHAT,
    SETTINGS
}

data class ConchatUiState(
    val settings: ChatSettings = ChatSettings(),
    val messages: List<ChatMessage> = emptyList(),
    val inputMessage: String = "",
    val status: String = "Не подключено",
    val lastMessageId: Long = 1400000L,
    val isPolling: Boolean = false,
    val currentScreen: MainScreen = MainScreen.LOGIN,
    val loginUsername: String = "",
    val loginPassword: String = "",
    val loginError: String = "",
    val isLoggingIn: Boolean = false
)

class ConchatViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ConchatSettingsStore(application)
    private val api = ConchatApi()

    private val initialMessages = store.loadMessages()
    private val initialChatMessageId = initialMessages
        .filterNot { it.isSystem }
        .maxOfOrNull { it.id } ?: 0L
    private val initialLastMessageId = maxOf(
        1400000L,
        store.loadLastMessageId(),
        initialChatMessageId
    )

    private val _uiState = MutableStateFlow(
        ConchatUiState(
            settings = store.load(),
            messages = initialMessages,
            lastMessageId = initialLastMessageId,
            status = if (initialMessages.isEmpty()) "Не подключено" else "Загружено: ${initialMessages.size} сообщений"
        )
    )
    val uiState: StateFlow<ConchatUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var tts: TextToSpeech? = null

    private val _mentionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mentionEvent: SharedFlow<Unit> = _mentionEvent.asSharedFlow()

    init {
        initTts()
        if (hasRequiredCredentials(_uiState.value.settings)) {
            _uiState.update { it.copy(currentScreen = MainScreen.CHAT) }
            connectAndStart()
        } else {
            _uiState.update { it.copy(currentScreen = MainScreen.LOGIN) }
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(currentScreen = MainScreen.SETTINGS) }
    }

    fun openChat() {
        if (hasRequiredCredentials(_uiState.value.settings)) {
            _uiState.update { it.copy(currentScreen = MainScreen.CHAT, loginError = "") }
            connectAndStart()
        } else {
            _uiState.update { it.copy(currentScreen = MainScreen.LOGIN) }
        }
    }

    fun openLogin() {
        if (hasRequiredCredentials(_uiState.value.settings)) {
            openChat()
        } else {
            _uiState.update { it.copy(currentScreen = MainScreen.LOGIN) }
        }
    }

    fun setLoginUsername(value: String) {
        _uiState.update { it.copy(loginUsername = value, loginError = "") }
    }

    fun setLoginPassword(value: String) {
        _uiState.update { it.copy(loginPassword = value, loginError = "") }
    }

    fun login() {
        val username = _uiState.value.loginUsername.trim()
        val password = _uiState.value.loginPassword
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(loginError = "Введите логин и пароль") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = "") }
            val result = withContext(Dispatchers.IO) { api.login(username, password) }
            if (result.success) {
                updateSettings { s ->
                    s.copy(
                        uid = result.uid,
                        sid = result.sid,
                        session = result.session,
                        csrfToken = result.csrfToken,
                        name = username
                    )
                }
                _uiState.update { it.copy(isLoggingIn = false, currentScreen = MainScreen.CHAT) }
                connectAndStart()
            } else {
                _uiState.update { it.copy(isLoggingIn = false, loginError = result.errorMessage) }
            }
        }
    }

    fun setInputMessage(text: String) {
        _uiState.update { it.copy(inputMessage = text) }
    }

    fun addNicknameToInput(nickname: String) {
        val clean = nickname.trim()
        if (clean.isBlank()) return
        val mention = "$clean:"
        _uiState.update { state ->
            val current = state.inputMessage.trimEnd()
            val updated = when {
                current.isBlank() -> "$mention "
                current.contains(mention) -> "$current "
                else -> "$current $mention "
            }
            state.copy(inputMessage = updated)
        }
    }

    fun updateSettings(transform: (ChatSettings) -> ChatSettings) {
        val updated = transform(_uiState.value.settings)
        store.save(updated)
        _uiState.update { it.copy(settings = updated) }
    }

    fun connectAndStart() {
        ConchatSyncWorker.schedule(getApplication())
        if (pollingJob?.isActive == true) {
            return
        }
        pollingJob = viewModelScope.launch {
            _uiState.update { it.copy(status = "Подключение...", isPolling = true) }
            while (true) {
                try {
                    loadOnce()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    _uiState.update { it.copy(status = "Ошибка сети, повтор через 11 сек") }
                    appendSystemErrorMessage(null, e.message ?: "Неизвестная ошибка загрузки")
                }
                delay(11_000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        ConchatSyncWorker.cancel(getApplication())
        _uiState.update { it.copy(isPolling = false, status = "Остановлено") }
    }

    fun sendCurrentMessage() {
        val text = _uiState.value.inputMessage.trim()
        if (text.isEmpty()) {
            return
        }

        if (applyCommand(text)) {
            _uiState.update { it.copy(inputMessage = "") }
            return
        }

        if (_uiState.value.settings.silent) {
            _uiState.update { it.copy(inputMessage = "", status = "Тихий режим: сообщение не отправлено") }
            return
        }

        viewModelScope.launch {
            val sendResult = withContext(Dispatchers.IO) {
                api.sendMessage(_uiState.value.settings, _uiState.value.lastMessageId, text)
            }
            val sent = sendResult.success
            _uiState.update {
                it.copy(
                    inputMessage = "",
                    status = if (sent) "Сообщение отправлено" else "Ошибка отправки"
                )
            }
            if (sent) {
                loadOnce()
            } else {
                appendSystemErrorMessage(sendResult.errorCode, sendResult.errorMessage)
            }
        }
    }

    private suspend fun loadOnce() {
        val state = _uiState.value
        if (!hasRequiredCredentials(state.settings)) {
            _uiState.update { it.copy(status = "Заполните uid/sid/csrf") }
            return
        }

        val requestLastMessageId = maxOf(0L, state.lastMessageId - MESSAGE_ID_LOOKBACK)

        val loadResult = withContext(Dispatchers.IO) {
            api.loadMessages(state.settings, requestLastMessageId)
        }

        if (loadResult.errorCode != null || loadResult.errorMessage.isNotBlank()) {
            _uiState.update { it.copy(status = formatRetryStatus(loadResult.errorCode, loadResult.errorMessage)) }
            appendSystemErrorMessage(loadResult.errorCode, loadResult.errorMessage)
            return
        }

        val cleanedCurrent = state.messages.filterNot { isNetworkSystemError(it) }
        val shouldPersistCleanedCurrent = cleanedCurrent.size != state.messages.size

        val loaded = loadResult.messages

        if (loaded.isEmpty()) {
            if (shouldPersistCleanedCurrent) {
                store.saveMessages(cleanedCurrent)
                _uiState.update { it.copy(messages = cleanedCurrent, status = "") }
            } else {
                _uiState.update { it.copy(status = "") }
            }
            return
        }

        val myNameLower = state.settings.name.trim().lowercase()
        val decoded = loaded.map { msg ->
            val parsedBody = if (state.settings.plaintext) {
                Html.fromHtml(msg.body, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                msg.body
            }
            val decodedText = decodeHtmlEntities(parsedBody)
            val isMention = myNameLower.isNotBlank() &&
                msg.login.lowercase() != myNameLower &&
                decodedText.lowercase().contains(myNameLower)
            msg.copy(body = decodedText, isMention = isMention)
        }

        val existingIds = cleanedCurrent.asSequence().map { it.id }.toHashSet()
        val onlyNew = decoded.filterNot { it.id in existingIds }

        if (onlyNew.isEmpty()) {
            if (shouldPersistCleanedCurrent) {
                store.saveMessages(cleanedCurrent)
                _uiState.update { it.copy(messages = cleanedCurrent, status = "") }
            } else {
                _uiState.update { it.copy(status = "") }
            }
            return
        }

        val newMentions = onlyNew.filter { it.id > state.lastMessageId && it.isMention }
        if (newMentions.isNotEmpty()) {
            speakMentions(newMentions)
            _mentionEvent.tryEmit(Unit)
        }

        val merged = (cleanedCurrent + onlyNew)
            .distinctBy { it.id }
            .sortedBy { it.id }
            .takeLast(300)

        val newLastMessageId = merged.filterNot { it.isSystem }.maxOfOrNull { m -> m.id } ?: state.lastMessageId
        store.saveMessages(merged)
        store.saveLastMessageId(newLastMessageId)

        _uiState.update {
            it.copy(
                messages = merged,
                lastMessageId = newLastMessageId,
                status = "Подключено: ${merged.size} сообщений"
            )
        }
    }

    private fun hasRequiredCredentials(settings: ChatSettings): Boolean {
        return settings.uid.isNotBlank() &&
            settings.sid.isNotBlank() &&
            settings.csrfToken.isNotBlank()
    }

    private fun isNetworkSystemError(message: ChatMessage): Boolean {
        return message.isSystem && message.body.startsWith("Ошибка сети")
    }

    private fun applyCommand(text: String): Boolean {
        return when (text.lowercase()) {
            "#silent" -> {
                updateSettings { it.copy(silent = !it.silent) }
                _uiState.update { it.copy(status = "silent=${_uiState.value.settings.silent}") }
                true
            }
            "#plaintext" -> {
                updateSettings { it.copy(plaintext = !it.plaintext) }
                _uiState.update { it.copy(status = "plaintext=${_uiState.value.settings.plaintext}") }
                true
            }
            "#say" -> {
                updateSettings { it.copy(say = !it.say) }
                _uiState.update { it.copy(status = "say=${_uiState.value.settings.say}") }
                true
            }
            "#tor" -> {
                updateSettings { it.copy(useTor = !it.useTor) }
                _uiState.update { it.copy(status = "useTor=${_uiState.value.settings.useTor}") }
                true
            }
            "#yinfo" -> {
                updateSettings { it.copy(yinfo = !it.yinfo) }
                _uiState.update { it.copy(status = "yinfo=${_uiState.value.settings.yinfo}") }
                true
            }
            "#ydownload" -> {
                updateSettings { it.copy(ydownload = !it.ydownload) }
                _uiState.update { it.copy(status = "ydownload=${_uiState.value.settings.ydownload}") }
                true
            }
            "#spell" -> {
                updateSettings { it.copy(spellCheck = !it.spellCheck) }
                _uiState.update { it.copy(status = "spellCheck=${_uiState.value.settings.spellCheck}") }
                true
            }
            "#exit" -> {
                stopPolling()
                _uiState.update { it.copy(status = "Остановлено (#exit)") }
                true
            }
            "#help" -> {
                _uiState.update {
                    it.copy(
                        status = "Команды: #silent #plaintext #say #tor #yinfo #ydownload #spell #exit"
                    )
                }
                true
            }
            else -> false
        }
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("–", "-")
    }

    private fun initTts() {
        tts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
            }
        }
    }

    private fun appendSystemErrorMessage(errorCode: Int?, errorMessage: String) {
        val codeText = errorCode?.toString() ?: "NETWORK"
        val details = errorMessage.ifBlank { "Ошибка сети" }
        val messageBody = "Ошибка сети (код $codeText): $details"
        val systemMessage = ChatMessage(
            id = System.currentTimeMillis(),
            login = "system",
            body = messageBody,
            createdUnix = System.currentTimeMillis() / 1000,
            isSystem = true
        )

        val merged = _uiState.value.messages
            .filterNot { it.isSystem && it.login == "system" && it.body.startsWith("Ошибка сети") }
            .plus(systemMessage)
            .sortedBy { it.id }
            .takeLast(300)

        store.saveMessages(merged)

        _uiState.update {
            it.copy(messages = merged)
        }
    }

    private fun formatRetryStatus(errorCode: Int?, errorMessage: String): String {
        return when {
            errorCode != null -> "Ошибка сети: HTTP $errorCode, повтор через 11 сек"
            errorMessage.contains("Unable to resolve host", ignoreCase = true) -> {
                "Ошибка сети: не удаётся определить адрес, повтор через 11 сек"
            }
            else -> "Ошибка сети, повтор через 11 сек"
        }
    }

    private fun speakMentions(mentions: List<ChatMessage>) {
        val engine = tts ?: return
        mentions.take(3).forEach { message ->
            engine.speak(
                "Вам пишет ${message.login}",
                TextToSpeech.QUEUE_ADD,
                null,
                "mention_${message.id}"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }

    private companion object {
        const val MESSAGE_ID_LOOKBACK = 50L
    }
}
