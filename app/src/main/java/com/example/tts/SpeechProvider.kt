package com.example.tts

import com.example.model.SpeechSegment
import com.example.model.VoiceInfoModel

sealed interface SpeechPlaybackEvent {
  data class SegmentStarted(val utteranceId: String) : SpeechPlaybackEvent

  data class SegmentCompleted(val utteranceId: String) : SpeechPlaybackEvent

  data class SegmentError(val utteranceId: String, val message: String) : SpeechPlaybackEvent

  data object EngineReady : SpeechPlaybackEvent
}

interface SpeechProvider {
  val providerId: String
  val displayName: String

  suspend fun initialize(): Boolean

  fun isReady(): Boolean

  fun getAvailableVoices(favoriteIds: Set<String>): List<VoiceInfoModel>

  fun isLanguageOfflineAvailable(languageTag: String): Boolean

  fun speakSegment(
    segment: SpeechSegment,
    utteranceId: String,
    preferredVoiceId: String?,
    speed: Float,
    pitch: Float,
    volume: Float = 1.0f,
    pauseAfterMs: Long = 0L,
  ): Boolean

  fun stop()

  fun shutdown()
}
