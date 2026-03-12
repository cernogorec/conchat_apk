package com.example.minimalapp.conchat

import android.content.Context
import androidx.core.content.edit

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
    }
}
