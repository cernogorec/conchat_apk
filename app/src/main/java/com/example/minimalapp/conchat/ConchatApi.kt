package com.example.minimalapp.conchat

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ConchatApi(
    private val client: OkHttpClient = OkHttpClient()
) {
    fun loadMessages(settings: ChatSettings, lastMessageId: Long): List<ChatMessage> {
        val hostPrefix = normalizedSublepra(settings.subLepra)
        val url = "https://${hostPrefix}leprosorium.ru/ajax/chat/load/"

        val formBody = FormBody.Builder()
            .add("last_message_id", lastMessageId.toString())
            .add("csrf_token", settings.csrfToken)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .addHeader("Cookie", cookieHeader(settings))
            .addHeader("User-Agent", "ConchatAndroid/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return emptyList()
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return emptyList()
            }

            val root = JSONObject(body)
            if (!root.has("messages")) {
                return emptyList()
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
            return result
        }
    }

    fun sendMessage(settings: ChatSettings, lastMessageId: Long, message: String): Boolean {
        val hostPrefix = normalizedSublepra(settings.subLepra)
        val url = "https://${hostPrefix}leprosorium.ru/ajax/chat/add/"

        val formBody = FormBody.Builder()
            .add("last", lastMessageId.toString())
            .add("csrf_token", settings.csrfToken)
            .add("body", message)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .addHeader("Cookie", cookieHeader(settings))
            .addHeader("User-Agent", "ConchatAndroid/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
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

    private fun normalizedSublepra(subLepra: String): String {
        val value = subLepra.trim().trimEnd('.')
        return if (value.isBlank()) "" else "$value."
    }

    private fun cookieHeader(settings: ChatSettings): String {
        return "wikilepro_session=${settings.session}; uid=${settings.uid}; sid=${settings.sid}"
    }
}
