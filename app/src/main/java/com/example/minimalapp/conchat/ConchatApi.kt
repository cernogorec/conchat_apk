package com.example.minimalapp.conchat

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ConchatApi(
    private val client: OkHttpClient = OkHttpClient()
) {
    fun loadMessages(settings: ChatSettings, lastMessageId: Long): LoadMessagesResult {
        val primaryHost = resolveChatHost(settings.subLepra)

        fun execute(host: String): LoadMessagesResult {
            val url = "https://$host/ajax/chat/load/"

            val formBody = FormBody.Builder()
                .add("last", lastMessageId.toString())
                .add("last_message_id", lastMessageId.toString())
                .add("csrf_token", settings.csrfToken)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .addHeader("Cookie", cookieHeader(settings))
                .addHeader("User-Agent", browserUserAgent())
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("Origin", baseOrigin(host))
                .addHeader("Referer", chatReferer(host))
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            return try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return LoadMessagesResult(
                            errorCode = response.code,
                            errorMessage = "HTTP ${response.code}"
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return LoadMessagesResult(messages = emptyList())
                    }

                    val root = JSONObject(body)
                    if (!root.has("messages")) {
                        return LoadMessagesResult(messages = emptyList())
                    }

                    val messagesArray = root.getJSONArray("messages")
                    val result = mutableListOf<ChatMessage>()
                    for (i in 0 until messagesArray.length()) {
                        val item = messagesArray.getJSONObject(i)
                        val user = item.optJSONObject("user")
                        result.add(
                            ChatMessage(
                                id = item.optLong("id", 0L),
                                login = user?.optString("login").orEmpty(),
                                body = item.optString("body", ""),
                                createdUnix = item.optLong("created", 0L)
                            )
                        )
                    }
                    LoadMessagesResult(messages = result)
                }
            } catch (e: Exception) {
                LoadMessagesResult(errorMessage = humanReadableNetworkError(e, host))
            }
        }

        return execute(primaryHost)
    }

    fun sendMessage(settings: ChatSettings, lastMessageId: Long, message: String): SendMessageResult {
        val primaryHost = resolveChatHost(settings.subLepra)

        fun execute(host: String): SendMessageResult {
            val url = "https://$host/ajax/chat/add/"

            val formBody = FormBody.Builder()
                .add("last", lastMessageId.toString())
                .add("csrf_token", settings.csrfToken)
                .add("body", message)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .addHeader("Cookie", cookieHeader(settings))
                .addHeader("User-Agent", browserUserAgent())
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("Origin", baseOrigin(host))
                .addHeader("Referer", chatReferer(host))
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            return try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        SendMessageResult(success = true)
                    } else {
                        SendMessageResult(
                            success = false,
                            errorCode = response.code,
                            errorMessage = "HTTP ${response.code}"
                        )
                    }
                }
            } catch (e: Exception) {
                SendMessageResult(success = false, errorMessage = humanReadableNetworkError(e, host))
            }
        }

        return execute(primaryHost)
    }

    fun login(username: String, password: String): LoginResult {
        val url = "https://leprosorium.ru/ajax/auth/login/"
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("g-recaptcha-response", "")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.75 Safari/537.36")
            .addHeader("Referer", "https://leprosorium.ru/login/")
            .addHeader("Host", "leprosorium.ru")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                if (root.optString("status") == "OK") {
                    val cookieMap = mutableMapOf<String, String>()
                    response.headers.values("Set-Cookie").forEach { cookie ->
                        val pair = cookie.split(";")[0].split("=", limit = 2)
                        if (pair.size == 2) cookieMap[pair[0].trim()] = pair[1].trim()
                    }
                    LoginResult(
                        success = true,
                        uid = cookieMap["uid"].orEmpty(),
                        sid = cookieMap["sid"].orEmpty(),
                        session = cookieMap["wikilepro_session"].orEmpty(),
                        csrfToken = root.optString("csrf_token", "")
                    )
                } else {
                    LoginResult(success = false, errorMessage = root.optString("message", "Неверный логин или пароль"))
                }
            }
        } catch (e: Exception) {
            LoginResult(success = false, errorMessage = e.message ?: "Ошибка сети")
        }
    }

    private fun resolveChatHost(subLepra: String): String {
        val name = subLepra.trim()
        return if (name.isBlank()) DEFAULT_HOST else "$name.$DEFAULT_HOST"
    }

    private fun cookieHeader(settings: ChatSettings): String {
        return "wikilepro_session=${settings.session}; uid=${settings.uid}; sid=${settings.sid}"
    }

    private fun browserUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }

    private fun baseOrigin(host: String): String {
        return "https://$host"
    }

    private fun chatReferer(host: String): String {
        return "${baseOrigin(host)}/chat/"
    }

    private fun humanReadableNetworkError(error: Exception, host: String): String {
        return when (error) {
            is UnknownHostException -> "Не удаётся определить адрес $host"
            is SocketTimeoutException -> "Таймаут сети"
            is ConnectException -> "Ошибка подключения"
            else -> error.message ?: "Ошибка сети"
        }
    }

    private companion object {
        const val DEFAULT_HOST = "leprosorium.ru"
    }
}
