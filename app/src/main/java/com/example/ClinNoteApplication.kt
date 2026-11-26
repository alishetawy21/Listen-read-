package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.ai.LinguaAiService
import com.example.coach.SpeechCoachManager
import com.example.data.ClinNoteDatabase
import com.example.data.ClinNoteRepository
import com.example.data.ReaderSettingsStore
import com.example.pdf.PdfDocumentProcessor
import com.example.tts.SpeechCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClinNoteApplication : Application() {

  lateinit var database: ClinNoteDatabase
    private set

  lateinit var repository: ClinNoteRepository
    private set

  lateinit var settingsStore: ReaderSettingsStore
    private set

  lateinit var linguaAiService: LinguaAiService
    private set

  lateinit var pdfProcessor: PdfDocumentProcessor
    private set

  lateinit var speechCoordinator: SpeechCoordinator
    private set

  lateinit var speechCoachManager: SpeechCoachManager
    private set

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    instance = this
    createNotificationChannels()

    database = ClinNoteDatabase.getInstance(this)
    repository = ClinNoteRepository(database)
    settingsStore = ReaderSettingsStore(this)
    linguaAiService = LinguaAiService()
    pdfProcessor = PdfDocumentProcessor(this, linguaAiService)
    speechCoordinator = SpeechCoordinator(this, repository, settingsStore)
    speechCoachManager = SpeechCoachManager(this)

    appScope.launch { repository.seedInitialDataIfEmpty() }
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(NotificationManager::class.java) ?: return

      val playbackChannel =
        NotificationChannel(
            CHANNEL_PLAYBACK,
            getString(R.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply {
            description = getString(R.string.notification_channel_playback_desc)
            setShowBadge(false)
          }

      val shortcutChannel =
        NotificationChannel(
            CHANNEL_SHORTCUT,
            getString(R.string.notification_channel_shortcut),
            NotificationManager.IMPORTANCE_MIN,
          )
          .apply {
            description = getString(R.string.notification_channel_shortcut_desc)
            setShowBadge(false)
          }

      manager.createNotificationChannels(listOf(playbackChannel, shortcutChannel))
    }
  }

  companion object {
    const val CHANNEL_PLAYBACK = "clinnote_tts_playback"
    const val CHANNEL_SHORTCUT = "clinnote_floating_shortcut"

    @Volatile
    lateinit var instance: ClinNoteApplication
      private set
  }
}
