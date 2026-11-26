package com.example.coach

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CoachCaptureMode {
  AUDIO_COACHING,
  LIVE_ASR_CHECK,
}

data class CoachCaptureState(
  val isRecording: Boolean = false,
  val mode: CoachCaptureMode = CoachCaptureMode.AUDIO_COACHING,
  val activeDurationMs: Long = 0L,
  val lastAudioFilePath: String? = null,
  val liveTranscript: String = "",
  val rmsDb: Float = 0f,
  val isPlayingUserAudio: Boolean = false,
  val errorMessage: String? = null,
)

data class VerifiedSpeechMetrics(
  val transcript: String,
  val wordAccuracyPct: Int,
  val measuredWpm: Int,
  val matchedWordCount: Int,
  val totalTargetWords: Int,
)

class SpeechCoachManager(private val context: Context) {

  private val _state = MutableStateFlow(CoachCaptureState())
  val state: StateFlow<CoachCaptureState> = _state.asStateFlow()

  private var mediaRecorder: MediaRecorder? = null
  private var speechRecognizer: SpeechRecognizer? = null
  private var userAudioPlayer: MediaPlayer? = null
  private var recordStartMs: Long = 0L
  private var currentAudioFile: File? = null

  fun startAudioRecording(): Boolean {
    stopPlayback()
    stopInternal()

    return try {
      val dir = File(context.cacheDir, "coach_recordings").apply { mkdirs() }
      val outFile = File(dir, "attempt_${System.currentTimeMillis()}.m4a")
      val recorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          MediaRecorder(context)
        } else {
          @Suppress("DEPRECATION")
          MediaRecorder()
        }

      recorder.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(64000)
        setAudioSamplingRate(16000)
        setOutputFile(outFile.absolutePath)
        prepare()
        start()
      }

      mediaRecorder = recorder
      currentAudioFile = outFile
      recordStartMs = System.currentTimeMillis()
      _state.update {
        it.copy(
          isRecording = true,
          mode = CoachCaptureMode.AUDIO_COACHING,
          lastAudioFilePath = null,
          liveTranscript = "",
          errorMessage = null,
        )
      }
      true
    } catch (e: Exception) {
      _state.update {
        it.copy(
          isRecording = false,
          errorMessage = "Microphone error: ${e.localizedMessage ?: "Unable to record audio"}",
        )
      }
      false
    }
  }

  fun stopAudioRecording(): Pair<File?, Long> {
    val elapsed = (System.currentTimeMillis() - recordStartMs).coerceAtLeast(350L)
    runCatching {
      mediaRecorder?.stop()
      mediaRecorder?.release()
    }
    mediaRecorder = null
    val file = currentAudioFile?.takeIf { it.exists() && it.length() > 0L }
    _state.update {
      it.copy(
        isRecording = false,
        activeDurationMs = elapsed,
        lastAudioFilePath = file?.absolutePath,
      )
    }
    return file to elapsed
  }

  fun startLiveSpeechRecognition(languageTag: String, onFinalTranscript: (String, Long) -> Unit): Boolean {
    stopPlayback()
    stopInternal()

    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      _state.update {
        it.copy(
          errorMessage = "On-device SpeechRecognizer service is unavailable on this device. Use Audio Recording mode instead."
        )
      }
      return false
    }

    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    speechRecognizer = recognizer
    recordStartMs = System.currentTimeMillis()

    recognizer.setRecognitionListener(
      object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
          _state.update {
            it.copy(
              isRecording = true,
              mode = CoachCaptureMode.LIVE_ASR_CHECK,
              liveTranscript = "",
              errorMessage = null,
            )
          }
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
          _state.update { it.copy(rmsDb = rmsdB) }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
          _state.update { it.copy(isRecording = false) }
        }

        override fun onError(error: Int) {
          val msg =
            when (error) {
              SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Speak clearly closer to the mic."
              SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Try speaking immediately."
              SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
              else -> "Speech recognition error (code $error)."
            }
          _state.update { it.copy(isRecording = false, errorMessage = msg) }
        }

        override fun onResults(results: Bundle?) {
          val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
          val best = matches.firstOrNull().orEmpty()
          val elapsed = (System.currentTimeMillis() - recordStartMs).coerceAtLeast(400L)
          _state.update {
            it.copy(isRecording = false, activeDurationMs = elapsed, liveTranscript = best)
          }
          if (best.isNotBlank()) {
            onFinalTranscript(best, elapsed)
          }
        }

        override fun onPartialResults(partialResults: Bundle?) {
          val partial =
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
          if (!partial.isNullOrBlank()) {
            _state.update { it.copy(liveTranscript = partial) }
          }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
      }
    )

    val localeTag =
      if (languageTag.startsWith("ar", ignoreCase = true)) "ar-SA"
      else if (languageTag.equals("en-GB", ignoreCase = true)) "en-GB"
      else "en-US"

    val intent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      }

    return runCatching {
        recognizer.startListening(intent)
        true
      }
      .getOrElse { exc ->
        _state.update { it.copy(errorMessage = exc.localizedMessage ?: "Failed to start recognizer") }
        false
      }
  }

  fun stopLiveSpeechRecognition() {
    runCatching { speechRecognizer?.stopListening() }
    _state.update { it.copy(isRecording = false) }
  }

  fun playUserRecording(filePath: String?) {
    if (filePath.isNullOrBlank()) return
    val file = File(filePath)
    if (!file.exists()) return
    stopPlayback()
    runCatching {
      val player =
        MediaPlayer().apply {
          setDataSource(file.absolutePath)
          setOnCompletionListener {
            _state.update { it.copy(isPlayingUserAudio = false) }
            release()
            userAudioPlayer = null
          }
          prepare()
          start()
        }
      userAudioPlayer = player
      _state.update { it.copy(isPlayingUserAudio = true) }
    }
  }

  fun stopPlayback() {
    runCatching {
      userAudioPlayer?.stop()
      userAudioPlayer?.release()
    }
    userAudioPlayer = null
    _state.update { it.copy(isPlayingUserAudio = false) }
  }

  fun deleteCachedAudioIfUnconsented(file: File?, keepRecording: Boolean) {
    if (!keepRecording && file != null && file.exists()) {
      runCatching { file.delete() }
      _state.update { it.copy(lastAudioFilePath = null) }
    }
  }

  private fun stopInternal() {
    runCatching {
      mediaRecorder?.stop()
      mediaRecorder?.release()
    }
    mediaRecorder = null
    runCatching {
      speechRecognizer?.cancel()
      speechRecognizer?.destroy()
    }
    speechRecognizer = null
  }

  fun release() {
    stopPlayback()
    stopInternal()
  }

  companion object {
    fun computeVerifiedMetrics(
      targetSentence: String,
      recognizedTranscript: String,
      durationMs: Long,
    ): VerifiedSpeechMetrics {
      val targetTokens = normalizeWords(targetSentence)
      val spokenTokens = normalizeWords(recognizedTranscript)

      if (targetTokens.isEmpty()) {
        return VerifiedSpeechMetrics(recognizedTranscript, 0, 0, 0, 0)
      }

      val spokenCounts = spokenTokens.groupingBy { it }.eachCount().toMutableMap()
      var matched = 0
      for (word in targetTokens) {
        val remaining = spokenCounts[word] ?: 0
        if (remaining > 0) {
          matched++
          spokenCounts[word] = remaining - 1
        }
      }

      val accuracy = ((matched.toFloat() / targetTokens.size.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
      val minutes = (durationMs / 60_000.0).coerceAtLeast(0.02)
      val wpmSource = if (spokenTokens.isNotEmpty()) spokenTokens.size else targetTokens.size
      val wpm = (wpmSource / minutes).roundToInt().coerceIn(20, 260)

      return VerifiedSpeechMetrics(
        transcript = recognizedTranscript,
        wordAccuracyPct = accuracy,
        measuredWpm = wpm,
        matchedWordCount = matched,
        totalTargetWords = targetTokens.size,
      )
    }

    private fun normalizeWords(input: String): List<String> {
      // Strip Arabic diacritics and punctuation for fair lexical matching
      val noDiacritics = input.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
      return noDiacritics
        .lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{Nd}]+"))
        .filter { it.isNotBlank() }
    }
  }
}
