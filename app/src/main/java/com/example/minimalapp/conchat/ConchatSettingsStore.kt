package com.example.minimalapp.conchat

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class ConchatSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ChatSettings {
        return ChatSettings(
            uid = prefs.getString(KEY_UID, "") ?: "",
            sid = prefs.getString(KEY_SID, "") ?: "",
            session = prefs.getString(KEY_SESSION, "") ?: "",
            csrfToken = prefs.getString(KEY_CSRF, "") ?: "",
            name = prefs.getString(KEY_NAME, "") ?: "",
            subLepra = prefs.getString(KEY_SUBLEPRA, "") ?: "",
            useTor = prefs.getBoolean(KEY_USETOR, false),
            spellCheck = prefs.getBoolean(KEY_SPELLCHECK, false),
            plaintext = prefs.getBoolean(KEY_PLAINTEXT, true),
            yinfo = prefs.getBoolean(KEY_YINFO, false),
            ydownload = prefs.getBoolean(KEY_YDOWNLOAD, false),
            say = prefs.getBoolean(KEY_SAY, false),
            silent = prefs.getBoolean(KEY_SILENT, false)
        )
    }

    fun save(settings: ChatSettings) {
        prefs.edit {
            putString(KEY_UID, settings.uid)
            putString(KEY_SID, settings.sid)
            putString(KEY_SESSION, settings.session)
            putString(KEY_CSRF, settings.csrfToken)
            putString(KEY_NAME, settings.name)
            putString(KEY_SUBLEPRA, settings.subLepra)
            putBoolean(KEY_USETOR, settings.useTor)
            putBoolean(KEY_SPELLCHECK, settings.spellCheck)
            putBoolean(KEY_PLAINTEXT, settings.plaintext)
            putBoolean(KEY_YINFO, settings.yinfo)
            putBoolean(KEY_YDOWNLOAD, settings.ydownload)
            putBoolean(KEY_SAY, settings.say)
            putBoolean(KEY_SILENT, settings.silent)
        }
    }

    fun loadMessages(): List<ChatMessage> {
        val raw = prefs.getString(KEY_MESSAGES, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }

        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        ChatMessage(
                            id = item.optLong("id", 0L),
                            login = item.optString("login", ""),
                            body = item.optString("body", ""),
                            createdUnix = item.optLong("createdUnix", 0L),
                            isMention = item.optBoolean("isMention", false),
                            isSystem = item.optBoolean("isSystem", false)
                        )
                    )
                }
            }
                .filter { it.id > 0L }
                .sortedBy { it.id }
                .takeLast(MAX_STORED_MESSAGES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(messages: List<ChatMessage>) {
        val normalized = messages
            .distinctBy { it.id }
            .sortedBy { it.id }
            .takeLast(MAX_STORED_MESSAGES)

        val array = JSONArray()
        normalized.forEach { message ->
            array.put(
                JSONObject().apply {
                    put("id", message.id)
                    put("login", message.login)
                    put("body", message.body)
                    put("createdUnix", message.createdUnix)
                    put("isMention", message.isMention)
                    put("isSystem", message.isSystem)
                }
            )
        }

        prefs.edit {
            putString(KEY_MESSAGES, array.toString())
        }
    }

    fun loadLastMessageId(): Long {
        return prefs.getLong(KEY_LAST_MESSAGE_ID, 1400000L)
    }

    fun saveLastMessageId(id: Long) {
        prefs.edit {
            putLong(KEY_LAST_MESSAGE_ID, id)
        }
    }

    private companion object {
        const val PREFS_NAME = "conchat_settings"
        const val KEY_UID = "uid"
        const val KEY_SID = "sid"
        const val KEY_SESSION = "session"
        const val KEY_CSRF = "csrf_token"
        const val KEY_NAME = "name"
        const val KEY_SUBLEPRA = "sublepra"
        const val KEY_USETOR = "use_tor"
        const val KEY_SPELLCHECK = "spell_check"
        const val KEY_PLAINTEXT = "plaintext"
        const val KEY_YINFO = "yinfo"
        const val KEY_YDOWNLOAD = "ydownload"
        const val KEY_SAY = "say"
        const val KEY_SILENT = "silent"
        const val KEY_MESSAGES = "messages"
        const val KEY_LAST_MESSAGE_ID = "last_message_id"
        const val MAX_STORED_MESSAGES = 300
    }
}
