package com.example.model

import kotlinx.serialization.Serializable

enum class VoiceQualityTier(val label: String) {
  ON_DEVICE_ENHANCED("On-Device Enhanced"),
  ON_DEVICE_BASIC("On-Device Basic"),
  NETWORK_SYSTEM("System Network Voice"),
  CLOUD_BACKEND("Cloud Backend Adapter"),
}

enum class VoiceGender(val label: String) {
  ANY("All"),
  FEMALE("Female"),
  MALE("Male"),
}

enum class TranslationTone(val label: String) {
  NATURAL("Natural"),
  ACADEMIC("Academic"),
  MEDICAL("Medical"),
  CONVERSATIONAL("Conversational"),
}

enum class ListenAudioMode(val label: String) {
  ORIGINAL("Original"),
  TRANSLATED("Translated"),
}

enum class PlaybackStatus {
  IDLE,
  PREPARING,
  PLAYING,
  PAUSED,
  ERROR,
}

enum class CaptureSource(val displayName: String) {
  INTERNAL_NOTE("Library Document"),
  PDF_TEXT("Digital PDF"),
  PDF_OCR("Scanned PDF (OCR)"),
  EPUB_BOOK("EPUB Book"),
  IMAGE_OCR("Image Scan (OCR)"),
  PROCESS_TEXT("Text Selection Menu"),
  SHARE_INTENT("Shared Text"),
  ACCESSIBILITY_SELECTION("Selected Screen Text"),
  ACCESSIBILITY_SCREEN("Screen Reader Capture"),
}

data class LanguageDescriptor(
  val tag: String,
  val displayName: String,
  val nativeName: String,
  val isRtl: Boolean = false,
)

object SupportedLanguages {
  val AUTO = LanguageDescriptor("auto", "Auto-Detect", "Auto")
  val ALL: List<LanguageDescriptor> =
    listOf(
      LanguageDescriptor("ar", "Arabic (MSA)", "العربية", isRtl = true),
      LanguageDescriptor("en-US", "English (US)", "English (US)"),
      LanguageDescriptor("en-GB", "English (UK)", "English (UK)"),
      LanguageDescriptor("fr", "French", "Français"),
      LanguageDescriptor("de", "German", "Deutsch"),
      LanguageDescriptor("es", "Spanish", "Español"),
      LanguageDescriptor("it", "Italian", "Italiano"),
      LanguageDescriptor("pt", "Portuguese", "Português"),
      LanguageDescriptor("tr", "Turkish", "Türkçe"),
      LanguageDescriptor("zh", "Chinese", "中文"),
      LanguageDescriptor("ja", "Japanese", "日本語"),
      LanguageDescriptor("ko", "Korean", "한국어"),
      LanguageDescriptor("hi", "Hindi", "हिन्दी"),
    )

  fun findByTag(tag: String): LanguageDescriptor {
    val normalized = tag.replace('_', '-')
    return ALL.firstOrNull { it.tag.equals(normalized, ignoreCase = true) }
      ?: ALL.firstOrNull { normalized.startsWith(it.tag.substringBefore('-'), ignoreCase = true) }
      ?: LanguageDescriptor(
        tag = normalized,
        displayName = normalized.uppercase(),
        nativeName = normalized,
        isRtl = normalized.startsWith("ar", ignoreCase = true),
      )
  }
}

val PLAYBACK_SPEED_PRESETS: List<Float> =
  listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

data class VoiceInfoModel(
  val id: String,
  val name: String,
  val displayName: String,
  val localeTag: String,
  val languageCode: String,
  val qualityTier: VoiceQualityTier,
  val isOfflineAvailable: Boolean,
  val gender: VoiceGender = VoiceGender.ANY,
  val isFavorite: Boolean = false,
  val providerId: String = "android_tts",
)

data class SpeechSegment(
  val text: String,
  val spokenText: String,
  val languageTag: String,
  val isRtl: Boolean,
)

data class ReaderSentence(
  val index: Int,
  val paragraphIndex: Int,
  val rawText: String,
  val spokenText: String,
  val isHeadingOrReference: Boolean,
  val detectedLanguageTag: String,
  val isRtl: Boolean,
  val segments: List<SpeechSegment>,
)

data class VocabularyBreakdownItem(
  val term: String,
  val pronunciationGuide: String,
  val meaning: String,
  val usageExample: String,
)

data class TranslationResult(
  val sourceText: String,
  val detectedSourceLanguage: String,
  val targetLanguage: String,
  val translatedText: String,
  val literalVsContextualNote: String,
  val toneAndRegister: String,
  val alternativeTranslations: List<String>,
  val vocabularyBreakdown: List<VocabularyBreakdownItem>,
)

data class WordAssessment(
  val word: String,
  val status: String, // "accurate", "needs_polish", "missing_or_substituted"
  val phoneticTip: String,
)

data class PronunciationEvaluation(
  val targetSentence: String,
  val recognizedTranscript: String,
  val estimatedOverallScore: Int,
  val verifiedWordAccuracyPct: Int,
  val measuredWpm: Int,
  val audioAnalyzed: Boolean,
  val verifiedMetricsSummary: String,
  val clarityAndArticulationNote: String,
  val rhythmAndStressNote: String,
  val l1InterferenceTips: String,
  val practiceDrillTip: String,
  val wordAssessments: List<WordAssessment>,
)

data class FreeSpeakResult(
  val userTranscript: String,
  val conversationalReply: String,
  val replyTranslation: String,
  val grammarAndFluencyFeedback: String,
  val pronunciationAndAccentTips: String,
  val improvedNaturalVersion: String,
  val audioAnalyzed: Boolean,
)

data class ReaderSessionState(
  val documentId: Long? = null,
  val title: String = "No Document Loaded",
  val captureSource: CaptureSource = CaptureSource.INTERNAL_NOTE,
  val rawText: String = "",
  val sentences: List<ReaderSentence> = emptyList(),
  val currentSentenceIndex: Int = 0,
  val currentParagraphIndex: Int = 0,
  val status: PlaybackStatus = PlaybackStatus.IDLE,
  val activeLanguageTag: String = "en-US",
  val manualLanguageOverride: String? = null,
  val activeVoiceId: String? = null,
  val activeVoiceLabel: String = "Default System Voice",
  val activeQualityTier: VoiceQualityTier = VoiceQualityTier.ON_DEVICE_BASIC,
  val speechSpeed: Float = 1.0f,
  val speechPitch: Float = 1.0f,
  val speechVolume: Float = 1.0f,
  val skipHeadingsAndRefs: Boolean = false,
  val repeatCurrentSentence: Boolean = false,
  val isRtlDocument: Boolean = false,
  val availableVoices: List<VoiceInfoModel> = emptyList(),
  val listenAudioMode: ListenAudioMode = ListenAudioMode.ORIGINAL,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
) {
  val currentSentence: ReaderSentence?
    get() = sentences.getOrNull(currentSentenceIndex)

  val hasContent: Boolean
    get() = sentences.isNotEmpty()
}

@Serializable
data class ReaderSettings(
  val defaultLanguageTag: String = "auto",
  val autoDetectLanguage: Boolean = true,
  val mixArabicEnglishSwitching: Boolean = true,
  val preferredVoicesByLang: Map<String, String> = emptyMap(),
  val favoriteVoiceIds: Set<String> = emptySet(),
  val preferredGender: String = "ANY",
  val englishAccentTag: String = "en-US",
  val preferredTargetTranslationLang: String = "ar",
  val preferredTranslationTone: String = "NATURAL",
  val speechSpeed: Float = 1.0f,
  val speechPitch: Float = 1.0f,
  val speechVolume: Float = 1.0f,
  val skipHeadingsAndReferences: Boolean = false,
  val floatingShortcutEnabled: Boolean = false,
  val floatingButtonSizeDp: Float = 56f,
  val floatingButtonAlpha: Float = 0.92f,
  val floatingButtonX: Int = 24,
  val floatingButtonY: Int = 340,
  val doubleTapToTogglePlay: Boolean = true,
  val readingHistoryEnabled: Boolean = true,
  val saveVoiceRecordingsConsent: Boolean = false,
  val onlineAiEnabled: Boolean = true,
  val cloudTtsEnabled: Boolean = false,
  val cloudTtsEndpointUrl: String = "",
)
