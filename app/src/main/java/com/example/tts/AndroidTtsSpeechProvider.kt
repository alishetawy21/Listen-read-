package com.example.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.model.SpeechSegment
import com.example.model.VoiceInfoModel
import com.example.model.VoiceQualityTier
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidTtsSpeechProvider(private val context: Context) : SpeechProvider {

  override val providerId: String = "android_tts"
  override val displayName: String = "Android System Speech Engine"

  private var tts: TextToSpeech? = null
  private val initialized = AtomicBoolean(false)
  private var initDeferred: CompletableDeferred<Boolean>? = null

  private val _events = MutableSharedFlow<SpeechPlaybackEvent>(extraBufferCapacity = 64)
  val events: SharedFlow<SpeechPlaybackEvent> = _events.asSharedFlow()

  override suspend fun initialize(): Boolean {
    if (initialized.get() && tts != null) return true
    initDeferred?.let { return it.await() }

    val deferred = CompletableDeferred<Boolean>()
    initDeferred = deferred

    val engine =
      TextToSpeech(context.applicationContext) { status ->
        val ok = status == TextToSpeech.SUCCESS
        initialized.set(ok)
        if (ok) {
          tts?.setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
              override fun onStart(utteranceId: String?) {
                if (utteranceId != null && !utteranceId.startsWith(SILENCE_PREFIX)) {
                  _events.tryEmit(SpeechPlaybackEvent.SegmentStarted(utteranceId))
                }
              }

              override fun onDone(utteranceId: String?) {
                if (utteranceId != null && !utteranceId.startsWith(SILENCE_PREFIX)) {
                  _events.tryEmit(SpeechPlaybackEvent.SegmentCompleted(utteranceId))
                }
              }

              @Deprecated("Deprecated in Java")
              override fun onError(utteranceId: String?) {
                if (utteranceId != null) {
                  _events.tryEmit(
                    SpeechPlaybackEvent.SegmentError(utteranceId, "Speech synthesis error")
                  )
                }
              }

              override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != null) {
                  _events.tryEmit(
                    SpeechPlaybackEvent.SegmentError(
                      utteranceId,
                      "Speech synthesis error (code $errorCode)",
                    )
                  )
                }
              }
            }
          )
          _events.tryEmit(SpeechPlaybackEvent.EngineReady)
        }
        if (!deferred.isCompleted) {
          deferred.complete(ok)
        }
      }
    tts = engine
    return deferred.await()
  }

  override fun isReady(): Boolean = initialized.get() && tts != null

  override fun getAvailableVoices(favoriteIds: Set<String>): List<VoiceInfoModel> {
    val engine = tts ?: return emptyList()
    val rawVoices = runCatching { engine.voices }.getOrNull().orEmpty()
    return rawVoices
      .map { voice ->
        val localeTag = voice.locale.toLanguageTag()
        val qualityTier = classifyVoiceQuality(voice)
        VoiceInfoModel(
          id = voice.name,
          name = voice.name,
          displayName = buildFriendlyVoiceName(voice),
          localeTag = localeTag,
          languageCode = voice.locale.language,
          qualityTier = qualityTier,
          isOfflineAvailable = !voice.isNetworkConnectionRequired,
          isFavorite = favoriteIds.contains(voice.name),
          providerId = providerId,
        )
      }
      .sortedWith(
        compareByDescending<VoiceInfoModel> { it.isFavorite }
          .thenBy { it.qualityTier.ordinal }
          .thenByDescending { it.isOfflineAvailable }
          .thenBy { it.localeTag }
      )
  }

  override fun isLanguageOfflineAvailable(languageTag: String): Boolean {
    val engine = tts ?: return false
    val voices = runCatching { engine.voices }.getOrNull().orEmpty()
    val targetLocale = Locale.forLanguageTag(languageTag.replace('_', '-'))
    val hasOfflineVoice =
      voices.any { voice ->
        !voice.isNetworkConnectionRequired &&
          voice.locale.language.equals(targetLocale.language, ignoreCase = true)
      }
    if (hasOfflineVoice) return true
    val avail = runCatching { engine.isLanguageAvailable(targetLocale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
    return avail >= TextToSpeech.LANG_AVAILABLE
  }

  override fun speakSegment(
    segment: SpeechSegment,
    utteranceId: String,
    preferredVoiceId: String?,
    speed: Float,
    pitch: Float,
    pauseAfterMs: Long,
  ): Boolean {
    val engine = tts ?: return false
    if (!initialized.get()) return false

    engine.setSpeechRate(speed.coerceIn(0.5f, 3.0f))
    engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

    val targetLocale = Locale.forLanguageTag(segment.languageTag.replace('_', '-'))
    val matchedVoice = selectBestVoice(engine, targetLocale, preferredVoiceId)
    if (matchedVoice != null) {
      engine.voice = matchedVoice
    } else {
      engine.language = targetLocale
    }

    val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
    val spoken = segment.spokenText.ifBlank { segment.text }
    val code = engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    if (code == TextToSpeech.SUCCESS && pauseAfterMs > 0L) {
      engine.playSilentUtterance(
        pauseAfterMs,
        TextToSpeech.QUEUE_ADD,
        "$SILENCE_PREFIX$utteranceId",
      )
    }
    return code == TextToSpeech.SUCCESS
  }

  override fun stop() {
    runCatching { tts?.stop() }
  }

  override fun shutdown() {
    runCatching {
      tts?.stop()
      tts?.shutdown()
    }
    tts = null
    initialized.set(false)
    initDeferred = null
  }

  fun openOfflineVoiceDataInstaller(): Boolean {
    return runCatching {
        val intent =
          Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        context.startActivity(intent)
        true
      }
      .getOrDefault(false)
  }

  private fun selectBestVoice(
    engine: TextToSpeech,
    targetLocale: Locale,
    preferredVoiceId: String?,
  ): Voice? {
    val voices = runCatching { engine.voices }.getOrNull().orEmpty()
    if (voices.isEmpty()) return null

    if (!preferredVoiceId.isNullOrBlank()) {
      voices
        .firstOrNull {
          it.name == preferredVoiceId &&
            it.locale.language.equals(targetLocale.language, ignoreCase = true)
        }
        ?.let { return it }
    }

    val exactLocaleMatches =
      voices.filter {
        it.locale.language.equals(targetLocale.language, ignoreCase = true) &&
          (targetLocale.country.isEmpty() ||
            it.locale.country.equals(targetLocale.country, ignoreCase = true))
      }

    val candidates =
      exactLocaleMatches.ifEmpty {
        voices.filter { it.locale.language.equals(targetLocale.language, ignoreCase = true) }
      }

    return candidates.maxByOrNull { voice ->
      var score = 0
      if (!voice.isNetworkConnectionRequired) score += 100
      if (voice.locale.country.equals(targetLocale.country, ignoreCase = true)) score += 30
      score += voice.quality
      score -= voice.latency / 100
      score
    }
  }

  private fun classifyVoiceQuality(voice: Voice): VoiceQualityTier {
    return when {
      voice.isNetworkConnectionRequired -> VoiceQualityTier.NETWORK_SYSTEM
      voice.quality >= Voice.QUALITY_HIGH -> VoiceQualityTier.ON_DEVICE_ENHANCED
      else -> VoiceQualityTier.ON_DEVICE_BASIC
    }
  }

  private fun buildFriendlyVoiceName(voice: Voice): String {
    val lang = voice.locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { voice.locale.language }
    val country = voice.locale.country
    val region = if (country.isNotBlank()) " ($country)" else ""
    val shortCode = voice.name.substringAfterLast('-').take(14)
    val offlineTag = if (voice.isNetworkConnectionRequired) "Network" else "Offline"
    return "$lang$region · $shortCode [$offlineTag]"
  }

  companion object {
    private const val SILENCE_PREFIX = "clinnote_pause_"
  }
}
