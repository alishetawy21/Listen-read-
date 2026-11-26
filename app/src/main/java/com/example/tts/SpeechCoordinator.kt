package com.example.tts

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.ClinNoteRepository
import com.example.data.PronunciationRuleEntity
import com.example.data.ReaderSettingsStore
import com.example.model.CaptureSource
import com.example.model.ListenAudioMode
import com.example.model.PLAYBACK_SPEED_PRESETS
import com.example.model.PlaybackStatus
import com.example.model.ReaderSessionState
import com.example.model.ReaderSettings
import com.example.model.SpeechSegment
import com.example.model.SupportedLanguages
import com.example.model.VoiceGender
import com.example.model.VoiceInfoModel
import com.example.model.VoiceQualityTier
import com.example.nlp.LanguageDetector
import com.example.nlp.TextNormalizer
import com.example.service.TtsPlaybackService
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpeechCoordinator(
  private val context: Context,
  private val repository: ClinNoteRepository,
  private val settingsStore: ReaderSettingsStore,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  val androidProvider = AndroidTtsSpeechProvider(context)
  val cloudProvider = CloudTtsProviderAdapter(context, scope)

  private val _sessionState = MutableStateFlow(ReaderSessionState())
  val sessionState: StateFlow<ReaderSessionState> = _sessionState.asStateFlow()

  private var latestSettings = ReaderSettings()
  private var cachedRules: List<PronunciationRuleEntity> = emptyList()

  private var currentUtteranceId: String? = null
  private var currentSegmentIndex = 0
  private var pauseAfterSegmentJob: Job? = null

  init {
    scope.launch {
      androidProvider.initialize()
      refreshAvailableVoices()
    }

    scope.launch {
      settingsStore.settingsFlow.collectLatest { settings ->
        latestSettings = settings
        cloudProvider.configureEndpoint(settings.cloudTtsEndpointUrl)
        _sessionState.update { state ->
          state.copy(
            speechSpeed = settings.speechSpeed,
            speechPitch = settings.speechPitch,
            speechVolume = settings.speechVolume,
            skipHeadingsAndRefs = settings.skipHeadingsAndReferences,
          )
        }
        refreshAvailableVoices()
      }
    }

    scope.launch {
      repository.pronunciationRulesFlow.collectLatest { rules ->
        cachedRules = rules
        val current = _sessionState.value
        if (current.rawText.isNotBlank()) {
          reparseCurrentText(preserveSentenceIndex = current.currentSentenceIndex)
        }
      }
    }

    scope.launch { androidProvider.events.collect { event -> handleProviderEvent(event) } }
    scope.launch { cloudProvider.events.collect { event -> handleProviderEvent(event) } }
  }

  private fun activeProvider(): SpeechProvider {
    return if (latestSettings.cloudTtsEnabled && cloudProvider.isConfigured()) {
      cloudProvider
    } else {
      androidProvider
    }
  }

  fun refreshAvailableVoices() {
    val voices =
      androidProvider.getAvailableVoices(latestSettings.favoriteVoiceIds) +
        if (latestSettings.cloudTtsEnabled && cloudProvider.isConfigured()) {
          cloudProvider.getAvailableVoices(latestSettings.favoriteVoiceIds)
        } else {
          emptyList()
        }

    val currentLang = _sessionState.value.activeLanguageTag
    val selectedVoice = resolvePreferredVoice(currentLang, voices)

    _sessionState.update {
      it.copy(
        availableVoices = voices,
        activeVoiceId = selectedVoice?.id,
        activeVoiceLabel = selectedVoice?.displayName ?: "System Default Voice",
        activeQualityTier = selectedVoice?.qualityTier ?: VoiceQualityTier.ON_DEVICE_BASIC,
      )
    }
  }

  private fun resolvePreferredVoice(
    languageTag: String,
    voices: List<VoiceInfoModel>,
  ): VoiceInfoModel? {
    if (latestSettings.cloudTtsEnabled && cloudProvider.isConfigured()) {
      voices.firstOrNull { it.providerId == cloudProvider.providerId }?.let { return it }
    }
    val preferredId =
      latestSettings.preferredVoicesByLang[languageTag]
        ?: latestSettings.preferredVoicesByLang[languageTag.substringBefore('-')]
    if (preferredId != null) {
      voices.firstOrNull { it.id == preferredId }?.let { return it }
    }

    val targetGender = runCatching { VoiceGender.valueOf(latestSettings.preferredGender) }.getOrDefault(VoiceGender.ANY)
    val matchingLocale =
      voices.filter { it.localeTag.equals(languageTag, ignoreCase = true) }
    val matchingLang =
      voices.filter { it.languageCode.equals(languageTag.substringBefore('-'), ignoreCase = true) }

    if (targetGender != VoiceGender.ANY) {
      matchingLocale.firstOrNull { it.gender == targetGender }?.let { return it }
      matchingLang.firstOrNull { it.gender == targetGender }?.let { return it }
    }

    return matchingLocale.firstOrNull() ?: matchingLang.firstOrNull()
  }

  fun loadText(
    title: String,
    rawText: String,
    captureSource: CaptureSource,
    documentId: Long? = null,
    languageHint: String? = null,
    startSentenceIndex: Int = 0,
    autoPlay: Boolean = false,
  ) {
    stopPlaybackInternal(resetToStart = false)
    if (rawText.isBlank()) {
      _sessionState.update {
        it.copy(
          statusMessage = "No readable text found in selection.",
          status = PlaybackStatus.IDLE,
        )
      }
      return
    }

    val manualOverride =
      if (!languageHint.isNullOrBlank() && languageHint != "auto") languageHint else null

    val sentences =
      TextNormalizer.parseDocument(
        rawText = rawText,
        manualLanguageTag = manualOverride,
        autoDetect = latestSettings.autoDetectLanguage,
        defaultLanguageTag = latestSettings.defaultLanguageTag,
        enableMixedArabicEnglish = latestSettings.mixArabicEnglishSwitching,
        customRules = cachedRules,
      )

    val safeIndex = startSentenceIndex.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
    val firstLang =
      sentences.getOrNull(safeIndex)?.detectedLanguageTag
        ?: manualOverride
        ?: LanguageDetector.detectLanguage(rawText)

    val voices = _sessionState.value.availableVoices
    val voice = resolvePreferredVoice(firstLang, voices)

    _sessionState.update {
      it.copy(
        documentId = documentId,
        title = title.ifBlank { "Captured Selection" },
        captureSource = captureSource,
        rawText = rawText,
        sentences = sentences,
        currentSentenceIndex = safeIndex,
        currentParagraphIndex = sentences.getOrNull(safeIndex)?.paragraphIndex ?: 0,
        status = if (autoPlay) PlaybackStatus.PREPARING else PlaybackStatus.IDLE,
        activeLanguageTag = firstLang,
        manualLanguageOverride = manualOverride,
        activeVoiceId = voice?.id,
        activeVoiceLabel = voice?.displayName ?: "System Default Voice",
        activeQualityTier = voice?.qualityTier ?: VoiceQualityTier.ON_DEVICE_BASIC,
        isRtlDocument = LanguageDetector.isRtlLanguage(firstLang),
        statusMessage =
          if (captureSource != CaptureSource.INTERNAL_NOTE) {
            "Loaded from ${captureSource.displayName} (${sentences.size} sentences)"
          } else null,
        errorMessage = null,
      )
    }

    if (latestSettings.readingHistoryEnabled) {
      scope.launch(Dispatchers.IO) {
        repository.recordHistory(
          documentId = documentId,
          title = title.ifBlank { "Captured Selection" },
          sourceLabel = captureSource.displayName,
          fullText = rawText,
          languageTag = firstLang,
          sentenceCount = sentences.size,
          lastSentenceIndex = safeIndex,
        )
      }
    }

    if (autoPlay && sentences.isNotEmpty()) {
      play()
    }
  }

  private fun reparseCurrentText(preserveSentenceIndex: Int) {
    val state = _sessionState.value
    if (state.rawText.isBlank()) return
    val sentences =
      TextNormalizer.parseDocument(
        rawText = state.rawText,
        manualLanguageTag = state.manualLanguageOverride,
        autoDetect = latestSettings.autoDetectLanguage,
        defaultLanguageTag = latestSettings.defaultLanguageTag,
        enableMixedArabicEnglish = latestSettings.mixArabicEnglishSwitching,
        customRules = cachedRules,
      )
    val safeIdx = preserveSentenceIndex.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
    _sessionState.update {
      it.copy(
        sentences = sentences,
        currentSentenceIndex = safeIdx,
        currentParagraphIndex = sentences.getOrNull(safeIdx)?.paragraphIndex ?: 0,
      )
    }
  }

  fun play() {
    val state = _sessionState.value
    if (state.sentences.isEmpty()) return

    val targetIndex = findNextSpeakableIndex(state.currentSentenceIndex, forward = true)
    if (targetIndex < 0) {
      stop()
      return
    }

    ensurePlaybackServiceRunning()
    scope.launch {
      if (!androidProvider.isReady()) {
        androidProvider.initialize()
        refreshAvailableVoices()
      }
      currentSegmentIndex = 0
      speakSentenceAt(targetIndex)
    }
  }

  fun pause() {
    pauseAfterSegmentJob?.cancel()
    currentUtteranceId = null
    androidProvider.stop()
    cloudProvider.stop()
    _sessionState.update { it.copy(status = PlaybackStatus.PAUSED) }
  }

  fun togglePlayPause() {
    when (_sessionState.value.status) {
      PlaybackStatus.PLAYING, PlaybackStatus.PREPARING -> pause()
      PlaybackStatus.PAUSED, PlaybackStatus.IDLE, PlaybackStatus.ERROR -> play()
    }
  }

  fun stop() {
    stopPlaybackInternal(resetToStart = true)
    val stopIntent =
      Intent(context, TtsPlaybackService::class.java).apply {
        action = TtsPlaybackService.ACTION_STOP_SERVICE
      }
    runCatching { context.startService(stopIntent) }
  }

  private fun stopPlaybackInternal(resetToStart: Boolean) {
    pauseAfterSegmentJob?.cancel()
    currentUtteranceId = null
    currentSegmentIndex = 0
    androidProvider.stop()
    cloudProvider.stop()
    _sessionState.update {
      it.copy(
        status = PlaybackStatus.IDLE,
        currentSentenceIndex = if (resetToStart) 0 else it.currentSentenceIndex,
        currentParagraphIndex =
          if (resetToStart) (it.sentences.firstOrNull()?.paragraphIndex ?: 0)
          else it.currentParagraphIndex,
      )
    }
  }

  fun nextSentence() {
    val state = _sessionState.value
    if (state.sentences.isEmpty()) return
    val nextIdx = findNextSpeakableIndex(state.currentSentenceIndex + 1, forward = true)
    if (nextIdx in state.sentences.indices) {
      jumpToSentence(nextIdx, autoPlay = state.status == PlaybackStatus.PLAYING)
    } else {
      stop()
    }
  }

  fun previousSentence() {
    val state = _sessionState.value
    if (state.sentences.isEmpty()) return
    val prevIdx = findNextSpeakableIndex(state.currentSentenceIndex - 1, forward = false)
    val target = if (prevIdx >= 0) prevIdx else 0
    jumpToSentence(target, autoPlay = state.status == PlaybackStatus.PLAYING)
  }

  fun repeatSentence() {
    val current = _sessionState.value.currentSentenceIndex
    jumpToSentence(current, autoPlay = true)
  }

  fun toggleRepeatCurrentSentence() {
    _sessionState.update { it.copy(repeatCurrentSentence = !it.repeatCurrentSentence) }
  }

  fun jumpToSentence(index: Int, autoPlay: Boolean = true) {
    val state = _sessionState.value
    if (index !in state.sentences.indices) return
    pauseAfterSegmentJob?.cancel()
    currentUtteranceId = null
    currentSegmentIndex = 0
    androidProvider.stop()
    cloudProvider.stop()

    val targetSentence = state.sentences[index]
    _sessionState.update {
      it.copy(
        currentSentenceIndex = index,
        currentParagraphIndex = targetSentence.paragraphIndex,
        activeLanguageTag = targetSentence.detectedLanguageTag,
      )
    }
    persistDocumentProgress(state.documentId, index)
    if (autoPlay) {
      ensurePlaybackServiceRunning()
      scope.launch {
        if (!androidProvider.isReady()) androidProvider.initialize()
        speakSentenceAt(index)
      }
    }
  }

  fun readParagraph(paragraphIndex: Int) {
    val firstSentenceIdx =
      _sessionState.value.sentences.indexOfFirst { it.paragraphIndex == paragraphIndex }
    if (firstSentenceIdx >= 0) {
      jumpToSentence(firstSentenceIdx, autoPlay = true)
    }
  }

  fun speakOneShot(
    text: String,
    languageTag: String = "auto",
    speed: Float = _sessionState.value.speechSpeed,
  ) {
    if (text.isBlank()) return
    pauseAfterSegmentJob?.cancel()
    currentUtteranceId = null
    androidProvider.stop()
    cloudProvider.stop()

    val resolvedLang =
      if (languageTag == "auto") LanguageDetector.detectLanguage(text) else languageTag
    val voices = _sessionState.value.availableVoices
    val preferredVoice = resolvePreferredVoice(resolvedLang, voices)?.id

    scope.launch {
      if (!androidProvider.isReady()) {
        androidProvider.initialize()
        refreshAvailableVoices()
      }
      val normalized =
        TextNormalizer.parseDocument(
            rawText = text,
            manualLanguageTag = resolvedLang,
            autoDetect = false,
            defaultLanguageTag = resolvedLang,
            enableMixedArabicEnglish = latestSettings.mixArabicEnglishSwitching,
            customRules = cachedRules,
          )
          .firstOrNull()
          ?.segments
          ?.firstOrNull()
          ?: SpeechSegment(
            text = text,
            spokenText = text,
            languageTag = resolvedLang,
            isRtl = LanguageDetector.isRtlLanguage(resolvedLang),
          )

      activeProvider().speakSegment(
        segment = normalized,
        utteranceId = "oneshot_${UUID.randomUUID()}",
        preferredVoiceId = preferredVoice,
        speed = speed.coerceIn(0.5f, 3.0f),
        pitch = _sessionState.value.speechPitch,
        volume = _sessionState.value.speechVolume,
        pauseAfterMs = 0L,
      )
    }
  }

  fun setSpeed(speed: Float) {
    val clamped = speed.coerceIn(0.5f, 3.0f)
    _sessionState.update { it.copy(speechSpeed = clamped) }
    scope.launch { settingsStore.updateSettings { it.copy(speechSpeed = clamped) } }
    if (_sessionState.value.status == PlaybackStatus.PLAYING) {
      speakCurrentSegment()
    }
  }

  fun cycleSpeedPreset(): Float {
    val current = _sessionState.value.speechSpeed
    val next = PLAYBACK_SPEED_PRESETS.firstOrNull { it > current + 0.01f } ?: PLAYBACK_SPEED_PRESETS.first()
    setSpeed(next)
    return next
  }

  fun setPitch(pitch: Float) {
    val clamped = pitch.coerceIn(0.5f, 2.0f)
    _sessionState.update { it.copy(speechPitch = clamped) }
    scope.launch { settingsStore.updateSettings { it.copy(speechPitch = clamped) } }
  }

  fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0.1f, 1.0f)
    _sessionState.update { it.copy(speechVolume = clamped) }
    scope.launch { settingsStore.updateSettings { it.copy(speechVolume = clamped) } }
    if (_sessionState.value.status == PlaybackStatus.PLAYING) {
      speakCurrentSegment()
    }
  }

  fun setGenderFilter(gender: VoiceGender) {
    scope.launch {
      settingsStore.updateSettings { it.copy(preferredGender = gender.name) }
    }
  }

  fun setListenAudioMode(mode: ListenAudioMode) {
    _sessionState.update { it.copy(listenAudioMode = mode) }
  }

  fun selectLanguageOverride(languageTag: String) {
    val override = if (languageTag == "auto") null else languageTag
    val state = _sessionState.value
    val updatedSentences =
      if (state.rawText.isNotBlank()) {
        TextNormalizer.parseDocument(
          rawText = state.rawText,
          manualLanguageTag = override,
          autoDetect = latestSettings.autoDetectLanguage,
          defaultLanguageTag = latestSettings.defaultLanguageTag,
          enableMixedArabicEnglish = latestSettings.mixArabicEnglishSwitching,
          customRules = cachedRules,
        )
      } else state.sentences

    val effectiveLang =
      override
        ?: updatedSentences.getOrNull(state.currentSentenceIndex)?.detectedLanguageTag
        ?: SupportedLanguages.ALL.first().tag
    val preferredVoice = resolvePreferredVoice(effectiveLang, state.availableVoices)

    _sessionState.update {
      it.copy(
        sentences = updatedSentences,
        manualLanguageOverride = override,
        activeLanguageTag = effectiveLang,
        isRtlDocument = LanguageDetector.isRtlLanguage(effectiveLang),
        activeVoiceId = preferredVoice?.id,
        activeVoiceLabel = preferredVoice?.displayName ?: "System Default Voice",
        activeQualityTier = preferredVoice?.qualityTier ?: VoiceQualityTier.ON_DEVICE_BASIC,
      )
    }

    if (_sessionState.value.status == PlaybackStatus.PLAYING) {
      speakSentenceAt(_sessionState.value.currentSentenceIndex)
    }
  }

  fun selectVoiceForLanguage(voice: VoiceInfoModel) {
    val langKey = voice.localeTag
    scope.launch {
      settingsStore.updateSettings { settings ->
        val updatedMap = settings.preferredVoicesByLang.toMutableMap()
        updatedMap[langKey] = voice.id
        updatedMap[voice.languageCode] = voice.id
        settings.copy(preferredVoicesByLang = updatedMap)
      }
    }
    _sessionState.update {
      it.copy(
        activeVoiceId = voice.id,
        activeVoiceLabel = voice.displayName,
        activeQualityTier = voice.qualityTier,
      )
    }
  }

  fun toggleFavoriteVoice(voiceId: String) {
    scope.launch {
      settingsStore.updateSettings { settings ->
        val updated = settings.favoriteVoiceIds.toMutableSet()
        if (!updated.add(voiceId)) updated.remove(voiceId)
        settings.copy(favoriteVoiceIds = updated)
      }
    }
  }

  fun toggleSkipHeadingsAndRefs() {
    val next = !_sessionState.value.skipHeadingsAndRefs
    _sessionState.update { it.copy(skipHeadingsAndRefs = next) }
    scope.launch { settingsStore.updateSettings { it.copy(skipHeadingsAndReferences = next) } }
  }

  fun testVoiceSample(voice: VoiceInfoModel) {
    val sampleText =
      if (voice.languageCode.startsWith("ar", ignoreCase = true)) {
        "مرحباً، هذا نموذجُ قراءةٍ واضحةٍ باللغة العربية الفصحى من تطبيق لينغوا فلوت."
      } else {
        "LinguaFloat voice preview. Clear, natural bilingual pronunciation for reading and speaking."
      }
    scope.launch {
      if (!androidProvider.isReady()) androidProvider.initialize()
      val seg =
        SpeechSegment(
          text = sampleText,
          spokenText = sampleText,
          languageTag = voice.localeTag,
          isRtl = LanguageDetector.isRtlLanguage(voice.localeTag),
        )
      androidProvider.speakSegment(
        segment = seg,
        utteranceId = "sample_${UUID.randomUUID()}",
        preferredVoiceId = voice.id,
        speed = _sessionState.value.speechSpeed,
        pitch = _sessionState.value.speechPitch,
        volume = _sessionState.value.speechVolume,
      )
    }
  }

  fun bookmarkCurrentPosition() {
    val state = _sessionState.value
    val sentence = state.currentSentence ?: return
    scope.launch(Dispatchers.IO) {
      repository.addBookmark(
        documentId = state.documentId,
        documentTitle = state.title,
        sentenceIndex = sentence.index,
        paragraphIndex = sentence.paragraphIndex,
        sentencePreview = sentence.rawText,
        fullTextSnapshot = state.rawText,
        languageTag = sentence.detectedLanguageTag,
      )
    }
    _sessionState.update { it.copy(statusMessage = "Bookmarked sentence ${sentence.index + 1}") }
  }

  fun clearStatusMessage() {
    _sessionState.update { it.copy(statusMessage = null, errorMessage = null) }
  }

  fun notifyCaptureFeedback(message: String) {
    _sessionState.update { it.copy(statusMessage = message) }
  }

  fun isLanguageOfflineAvailable(languageTag: String): Boolean =
    androidProvider.isLanguageOfflineAvailable(languageTag)

  fun launchOfflineVoiceInstaller(): Boolean =
    androidProvider.openOfflineVoiceDataInstaller()

  private fun persistDocumentProgress(documentId: Long?, sentenceIndex: Int) {
    if (documentId == null || documentId <= 0L) return
    scope.launch(Dispatchers.IO) {
      repository.updateDocumentReadingProgress(documentId, sentenceIndex)
    }
  }

  private fun findNextSpeakableIndex(startFrom: Int, forward: Boolean): Int {
    val state = _sessionState.value
    val sentences = state.sentences
    if (sentences.isEmpty()) return -1
    var idx = startFrom
    while (idx in sentences.indices) {
      val s = sentences[idx]
      if (!state.skipHeadingsAndRefs || !s.isHeadingOrReference) {
        return idx
      }
      idx += if (forward) 1 else -1
    }
    return -1
  }

  private fun speakSentenceAt(sentenceIndex: Int) {
    val state = _sessionState.value
    val sentence = state.sentences.getOrNull(sentenceIndex) ?: return
    currentSegmentIndex = 0

    val voice = resolvePreferredVoice(sentence.detectedLanguageTag, state.availableVoices)
    _sessionState.update {
      it.copy(
        currentSentenceIndex = sentenceIndex,
        currentParagraphIndex = sentence.paragraphIndex,
        activeLanguageTag = sentence.detectedLanguageTag,
        activeVoiceId = voice?.id ?: it.activeVoiceId,
        activeVoiceLabel = voice?.displayName ?: it.activeVoiceLabel,
        activeQualityTier = voice?.qualityTier ?: it.activeQualityTier,
        status = PlaybackStatus.PLAYING,
        errorMessage = null,
      )
    }
    persistDocumentProgress(state.documentId, sentenceIndex)

    speakCurrentSegment()
  }

  private fun speakCurrentSegment() {
    val state = _sessionState.value
    val sentence = state.sentences.getOrNull(state.currentSentenceIndex) ?: return
    val segment = sentence.segments.getOrNull(currentSegmentIndex) ?: return

    val utteranceId = "utt_${sentence.index}_${currentSegmentIndex}_${UUID.randomUUID()}"
    currentUtteranceId = utteranceId

    val isLastSegmentOfSentence = currentSegmentIndex == sentence.segments.lastIndex
    val nextSentence = state.sentences.getOrNull(state.currentSentenceIndex + 1)
    val isParagraphEnd =
      isLastSegmentOfSentence &&
        (nextSentence == null || nextSentence.paragraphIndex != sentence.paragraphIndex)

    val pauseMs =
      when {
        isParagraphEnd -> 420L
        isLastSegmentOfSentence -> 180L
        else -> 60L
      }

    val preferredVoice =
      if (segment.languageTag == state.activeLanguageTag && state.activeVoiceId != null) {
        state.activeVoiceId
      } else {
        resolvePreferredVoice(segment.languageTag, state.availableVoices)?.id
      }

    val provider = activeProvider()
    val ok =
      provider.speakSegment(
        segment = segment,
        utteranceId = utteranceId,
        preferredVoiceId = preferredVoice,
        speed = state.speechSpeed,
        pitch = state.speechPitch,
        volume = state.speechVolume,
        pauseAfterMs = pauseMs,
      )

    if (!ok && provider !== androidProvider) {
      androidProvider.speakSegment(
        segment = segment,
        utteranceId = utteranceId,
        preferredVoiceId = preferredVoice,
        speed = state.speechSpeed,
        pitch = state.speechPitch,
        volume = state.speechVolume,
        pauseAfterMs = pauseMs,
      )
    }
  }

  private fun handleProviderEvent(event: SpeechPlaybackEvent) {
    when (event) {
      is SpeechPlaybackEvent.EngineReady -> {
        refreshAvailableVoices()
      }
      is SpeechPlaybackEvent.SegmentStarted -> Unit
      is SpeechPlaybackEvent.SegmentCompleted -> {
        if (event.utteranceId != currentUtteranceId) return
        val state = _sessionState.value
        if (state.status != PlaybackStatus.PLAYING) return

        val sentence = state.sentences.getOrNull(state.currentSentenceIndex) ?: return
        if (currentSegmentIndex + 1 < sentence.segments.size) {
          currentSegmentIndex++
          speakCurrentSegment()
        } else if (state.repeatCurrentSentence) {
          pauseAfterSegmentJob?.cancel()
          pauseAfterSegmentJob =
            scope.launch {
              delay(220L)
              if (_sessionState.value.status == PlaybackStatus.PLAYING) {
                speakSentenceAt(state.currentSentenceIndex)
              }
            }
        } else {
          val nextIdx = findNextSpeakableIndex(state.currentSentenceIndex + 1, forward = true)
          if (nextIdx in state.sentences.indices) {
            val nextSentence = state.sentences[nextIdx]
            val paragraphBoundary = nextSentence.paragraphIndex != sentence.paragraphIndex
            pauseAfterSegmentJob?.cancel()
            pauseAfterSegmentJob =
              scope.launch {
                delay(if (paragraphBoundary) 320L else 120L)
                if (_sessionState.value.status == PlaybackStatus.PLAYING) {
                  speakSentenceAt(nextIdx)
                }
              }
          } else {
            stop()
          }
        }
      }
      is SpeechPlaybackEvent.SegmentError -> {
        if (event.utteranceId == currentUtteranceId) {
          _sessionState.update {
            it.copy(
              status = PlaybackStatus.PAUSED,
              errorMessage = event.message,
            )
          }
        }
      }
    }
  }

  private fun ensurePlaybackServiceRunning() {
    val intent =
      Intent(context, TtsPlaybackService::class.java).apply {
        action = TtsPlaybackService.ACTION_START_PLAYBACK
      }
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }
  }
}
