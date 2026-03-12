package com.example.minimalapp.conchat

data class ChatSettings(
    val uid: String = "",
    val sid: String = "",
    val session: String = "",
    val csrfToken: String = "",
    val name: String = "",
    val subLepra: String = "",
    val useTor: Boolean = false,
    val spellCheck: Boolean = false,
    val plaintext: Boolean = true,
    val yinfo: Boolean = false,
    val ydownload: Boolean = false,
    val say: Boolean = false,
    val silent: Boolean = false
)

data class ChatMessage(
    val id: Long,
    val login: String,
    val body: String,
    val createdUnix: Long,
    val isMention: Boolean = false
)

data class LoginResult(
    val success: Boolean,
    val uid: String = "",
    val sid: String = "",
    val csrfToken: String = "",
    val errorMessage: String = ""
)
