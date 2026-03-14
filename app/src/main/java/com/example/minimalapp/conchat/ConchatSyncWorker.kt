package com.example.minimalapp.conchat

import android.content.Context
import android.text.Html
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ConchatSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val store = ConchatSettingsStore(appContext)
    private val api = ConchatApi()

    override suspend fun doWork(): Result {
        return try {
            val settings = store.load()
            if (!hasRequiredCredentials(settings)) {
                return Result.success()
            }

            val storedMessages = store.loadMessages()
            val lastMessageId = maxOf(
                1400000L,
                store.loadLastMessageId(),
                storedMessages.maxOfOrNull { it.id } ?: 0L
            )

            val loadedResult = api.loadMessages(settings, lastMessageId)
            if (loadedResult.errorCode != null || loadedResult.errorMessage.isNotBlank()) {
                return Result.retry()
            }

            val loaded = loadedResult.messages
            if (loaded.isEmpty()) {
                return Result.success()
            }

            val myNameLower = settings.name.trim().lowercase()
            val decoded = loaded.map { msg ->
                val parsedBody = if (settings.plaintext) {
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

            val existingIds = storedMessages.asSequence().map { it.id }.toHashSet()
            val onlyNew = decoded.filterNot { it.id in existingIds }
            if (onlyNew.isEmpty()) {
                return Result.success()
            }

            val merged = (storedMessages + onlyNew)
                .distinctBy { it.id }
                .sortedBy { it.id }
                .takeLast(300)

            store.saveMessages(merged)
            store.saveLastMessageId(merged.maxOfOrNull { it.id } ?: lastMessageId)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun hasRequiredCredentials(settings: ChatSettings): Boolean {
        return settings.uid.isNotBlank() &&
            settings.sid.isNotBlank() &&
            settings.csrfToken.isNotBlank()
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

    companion object {
        private const val UNIQUE_WORK_NAME = "conchat_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ConchatSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
