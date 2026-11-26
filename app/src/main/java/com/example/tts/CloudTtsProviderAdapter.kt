package com.example.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.model.SpeechSegment
import com.example.model.VoiceInfoModel
import com.example.model.VoiceQualityTier
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Optional modular Cloud TTS backend adapter.
 *
 * Security & privacy guarantee:
 * - No API keys are stored or embedded in this APK.
 * - Never sends user text to any remote endpoint unless the user explicitly enables Cloud TTS and
 *   configures their own authenticated HTTPS backend proxy URL.
 */
class CloudTtsProviderAdapter(
  private val context: Context,
  private val scope: CoroutineScope,
) : SpeechProvider {

  override val providerId: String = "cloud_backend"
  override val displayName: String = "Configured Cloud TTS Backend"

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .build()

  private var endpointUrl: String = ""
  private var mediaPlayer: MediaPlayer? = null
  private var activeJob: Job? = null

  private val _events = MutableSharedFlow<SpeechPlaybackEvent>(extraBufferCapacity = 32)
  val events: SharedFlow<SpeechPlaybackEvent> = _events.asSharedFlow()

  fun configureEndpoint(url: String) {
    endpointUrl = url.trim()
  }

  fun isConfigured(): Boolean =
    endpointUrl.startsWith("https://", ignoreCase = true)

  override suspend fun initialize(): Boolean = isConfigured()

  override fun isReady(): Boolean = isConfigured()

  override fun getAvailableVoices(favoriteIds: Set<String>): List<VoiceInfoModel> {
    if (!isConfigured()) return emptyList()
    return listOf(
      VoiceInfoModel(
        id = "cloud_neural_auto",
        name = "cloud_neural_auto",
        displayName = "Cloud Neural (Configured Backend)",
        localeTag = "auto",
        languageCode = "auto",
        qualityTier = VoiceQualityTier.CLOUD_BACKEND,
        isOfflineAvailable = false,
        isFavorite = favoriteIds.contains("cloud_neural_auto"),
        providerId = providerId,
      )
    )
  }

  override fun isLanguageOfflineAvailable(languageTag: String): Boolean = false

  override fun speakSegment(
    segment: SpeechSegment,
    utteranceId: String,
    preferredVoiceId: String?,
    speed: Float,
    pitch: Float,
    volume: Float,
    pauseAfterMs: Long,
  ): Boolean {
    if (!isConfigured()) {
      _events.tryEmit(
        SpeechPlaybackEvent.SegmentError(
          utteranceId,
          "Cloud TTS endpoint not configured. Falling back to on-device engine.",
        )
      )
      return false
    }

    stop()
    activeJob =
      scope.launch(Dispatchers.IO) {
        try {
          val payload =
            JSONObject()
              .put("text", segment.spokenText)
              .put("language", segment.languageTag)
              .put("speed", speed.toDouble())
              .put("pitch", pitch.toDouble())
              .toString()

          val request =
            Request.Builder()
              .url(endpointUrl)
              .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
              .build()

          client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
              _events.tryEmit(
                SpeechPlaybackEvent.SegmentError(
                  utteranceId,
                  "Cloud TTS HTTP ${response.code}",
                )
              )
              return@launch
            }
            val bytes = response.body?.bytes()
            if (bytes == null || bytes.isEmpty()) {
              _events.tryEmit(
                SpeechPlaybackEvent.SegmentError(utteranceId, "Empty audio response")
              )
              return@launch
            }

            val tempFile = File.createTempFile("clinnote_cloud_", ".mp3", context.cacheDir)
            tempFile.writeBytes(bytes)
            playAudioFile(tempFile, utteranceId)
          }
        } catch (t: Throwable) {
          _events.tryEmit(
            SpeechPlaybackEvent.SegmentError(
              utteranceId,
              t.message ?: "Cloud TTS request failed",
            )
          )
        }
      }
    return true
  }

  private fun playAudioFile(file: File, utteranceId: String) {
    val player =
      MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        setDataSource(file.absolutePath)
        setOnPreparedListener {
          _events.tryEmit(SpeechPlaybackEvent.SegmentStarted(utteranceId))
          start()
        }
        setOnCompletionListener {
          file.delete()
          _events.tryEmit(SpeechPlaybackEvent.SegmentCompleted(utteranceId))
        }
        setOnErrorListener { _, what, extra ->
          file.delete()
          _events.tryEmit(
            SpeechPlaybackEvent.SegmentError(utteranceId, "Audio decode error ($what/$extra)")
          )
          true
        }
        prepareAsync()
      }
    mediaPlayer = player
  }

  override fun stop() {
    activeJob?.cancel()
    activeJob = null
    runCatching {
      mediaPlayer?.stop()
      mediaPlayer?.release()
    }
    mediaPlayer = null
  }

  override fun shutdown() {
    stop()
  }
}
