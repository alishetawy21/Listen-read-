package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.FreeSpeakResult
import com.example.model.PronunciationEvaluation
import com.example.model.TranslationResult
import com.example.model.TranslationTone
import com.example.model.VocabularyBreakdownItem
import com.example.model.WordAssessment
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.ByteArrayOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

sealed interface AiCallResult<out T> {
  data class Success<T>(val data: T) : AiCallResult<T>

  data class Error(val message: String) : AiCallResult<Nothing>
}

class LinguaAiService {

  private val translationSchema =
    Schema.obj(
      properties =
        mapOf(
          "detectedSourceLanguage" to Schema.string("Detected language code or name, e.g. ar or en"),
          "targetLanguage" to Schema.string("Target language code, e.g. en or ar"),
          "translatedText" to Schema.string("Contextual, natural, accurate translation"),
          "literalVsContextualNote" to
            Schema.string("Brief explanation of nuance, tone, and why phrasing was chosen"),
          "toneAndRegister" to Schema.string("Register summary (e.g. Formal Academic, Medical, Natural)"),
          "alternativeTranslations" to
            Schema.array(Schema.string(), "Up to 3 natural alternative translations"),
          "vocabularyBreakdown" to
            Schema.array(
              Schema.obj(
                properties =
                  mapOf(
                    "term" to Schema.string("Key word, idiom, or medical/academic term"),
                    "pronunciationGuide" to
                      Schema.string("IPA or readable phonetic guide / Arabic root"),
                    "meaning" to Schema.string("Contextual meaning in target language"),
                    "usageExample" to Schema.string("Short practical example sentence"),
                  )
              ),
              "Up to 5 key idioms, difficult terms, or vocabulary notes",
            ),
        )
    )

  private val pronunciationSchema =
    Schema.obj(
      properties =
        mapOf(
          "estimatedOverallScore" to Schema.integer("Estimated score 0-100 combining accuracy and delivery"),
          "clarityAndArticulationNote" to
            Schema.string("Assessment of consonant/vowel clarity and specific sound substitutions"),
          "rhythmAndStressNote" to
            Schema.string("Feedback on word stress, syllable timing, intonation, and pacing"),
          "l1InterferenceTips" to
            Schema.string("Actionable advice addressing Arabic-to-English or English-to-Arabic sound challenges"),
          "practiceDrillTip" to
            Schema.string("Concrete mouth/tongue placement drill to repeat immediately"),
          "wordAssessments" to
            Schema.array(
              Schema.obj(
                properties =
                  mapOf(
                    "word" to Schema.string("Reference word"),
                    "status" to
                      Schema.enumeration(
                        listOf("accurate", "needs_polish", "missing_or_substituted"),
                        "Word-level assessment",
                      ),
                    "phoneticTip" to Schema.string("Short tip for how to pronounce this word clearly"),
                  )
              )
            ),
        )
    )

  private val freeSpeakSchema =
    Schema.obj(
      properties =
        mapOf(
          "conversationalReply" to Schema.string("Natural helpful reply in the target language"),
          "replyTranslation" to Schema.string("Translation of the AI reply for learner comprehension"),
          "grammarAndFluencyFeedback" to
            Schema.string("Gentle, specific corrections on grammar, word choice, and fluency"),
          "pronunciationAndAccentTips" to
            Schema.string("Estimated pronunciation/accent coaching based on speech or transcript"),
          "improvedNaturalVersion" to Schema.string("More natural native-like phrasing of what user said"),
        )
    )

  private val translationModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = translationSchema
        },
    )
  }

  private val pronunciationModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = pronunciationSchema
        },
    )
  }

  private val freeSpeakModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = freeSpeakSchema
        },
    )
  }

  private val generalModel by lazy { Firebase.ai.generativeModel("gemini-3.8-flash") }

  suspend fun translateWithContext(
    text: String,
    sourceLangHint: String,
    targetLang: String,
    tone: TranslationTone,
  ): AiCallResult<TranslationResult> {
    if (text.isBlank()) return AiCallResult.Error("Enter or select text to translate.")
    return try {
      val prompt =
        """
        You are LinguaFloat, an expert bilingual Arabic <-> English translator, linguist, and medical/academic terminology coach.
        Translate the text accurately and naturally into $targetLang (source hint: $sourceLangHint, requested register/tone: ${tone.label}).
        Preserve technical, medical, and literary meaning rather than translating literally word-for-word.
        Include helpful alternative translations and break down up to 5 difficult expressions, idioms, or key terms.

        TEXT:
        $text
        """
          .trimIndent()

      val response = withTimeout(60_000L) { translationModel.generateContent(prompt) }
      val jsonText = response.text ?: throw IllegalStateException("Model returned empty response.")
      val obj = JSONObject(jsonText)

      val alternatives =
        parseStringArray(obj.optJSONArray("alternativeTranslations"))
      val vocabArr = obj.optJSONArray("vocabularyBreakdown") ?: JSONArray()
      val vocab =
        (0 until vocabArr.length()).mapNotNull { idx ->
          val item = vocabArr.optJSONObject(idx) ?: return@mapNotNull null
          VocabularyBreakdownItem(
            term = item.optString("term"),
            pronunciationGuide = item.optString("pronunciationGuide"),
            meaning = item.optString("meaning"),
            usageExample = item.optString("usageExample"),
          )
        }

      AiCallResult.Success(
        TranslationResult(
          sourceText = text,
          detectedSourceLanguage = obj.optString("detectedSourceLanguage", sourceLangHint),
          targetLanguage = obj.optString("targetLanguage", targetLang),
          translatedText = obj.optString("translatedText"),
          literalVsContextualNote = obj.optString("literalVsContextualNote"),
          toneAndRegister = obj.optString("toneAndRegister", tone.label),
          alternativeTranslations = alternatives,
          vocabularyBreakdown = vocab,
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Translation timed out", e)
      AiCallResult.Error("Translation timed out after 60 seconds. Check connection and retry.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Translation failed", e)
      AiCallResult.Error(e.localizedMessage ?: "Translation failed.")
    }
  }

  suspend fun explainPassage(
    text: String,
    learnerTargetLanguage: String,
  ): AiCallResult<String> {
    if (text.isBlank()) return AiCallResult.Error("No text selected to explain.")
    return try {
      val prompt =
        """
        Explain this passage concisely for an Arabic/English learner.
        1. Core meaning in context.
        2. Difficult idioms, grammar structures, or medical/academic terms.
        3. Pronunciation tips for tricky words (including syllable stress or Arabic emphatics if applicable).
        Respond clearly with short sections. Target explanation language: $learnerTargetLanguage.

        PASSAGE:
        $text
        """
          .trimIndent()

      val response = withTimeout(60_000L) { generalModel.generateContent(prompt) }
      val out = response.text?.trim()
      if (out.isNullOrBlank()) {
        AiCallResult.Error("Model returned an empty explanation.")
      } else {
        AiCallResult.Success(out)
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Explanation timed out", e)
      AiCallResult.Error("Explanation request timed out.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Explanation failed", e)
      AiCallResult.Error(e.localizedMessage ?: "Failed to generate explanation.")
    }
  }

  suspend fun evaluatePronunciation(
    targetSentence: String,
    targetLanguage: String,
    audioBytes: ByteArray?,
    audioMimeType: String,
    recognizedTranscript: String,
    measuredWordAccuracyPct: Int,
    measuredWpm: Int,
  ): AiCallResult<PronunciationEvaluation> {
    return try {
      val hasAudio = audioBytes != null && audioBytes.isNotEmpty() && audioBytes.size < 18_000_000
      val instruction =
        """
        You are LinguaFloat's AI Pronunciation & Accent Coach specializing in Arabic and English speech coaching.
        Reference target sentence ($targetLanguage): "$targetSentence"
        On-device SpeechRecognizer transcript: "$recognizedTranscript"
        Verified measured word match: $measuredWordAccuracyPct% | Verified speaking speed: $measuredWpm WPM.
        ${if (hasAudio) "The learner's actual voice recording is attached. Listen carefully to vowels, consonants, word stress, rhythm, and L1 interference." else "No raw audio clip was attached; base phonetic feedback strictly on the recognized transcript and known Arabic/English challenges while noting that audio was unavailable."}

        Provide honest, constructive word-by-word coaching:
        - Identify mispronounced, missing, or substituted sounds (e.g., Arabic speakers on English /p/ vs /b/, /v/ vs /f/, /θ/ vs /s/, short vs long vowels; English speakers on Arabic ع, غ, ح, خ, ق, ط, ص, ض, gemination).
        - Do not claim lab-grade phoneme instrumentation; give transparent coaching estimates alongside the verified measurements.
        """
          .trimIndent()

      val response =
        withTimeout(60_000L) {
          if (hasAudio && audioBytes != null) {
            pronunciationModel.generateContent(
              content {
                inlineData(audioBytes, audioMimeType)
                text(instruction)
              }
            )
          } else {
            pronunciationModel.generateContent(instruction)
          }
        }

      val jsonText = response.text ?: throw IllegalStateException("Empty pronunciation response.")
      val obj = JSONObject(jsonText)

      val wordArr = obj.optJSONArray("wordAssessments") ?: JSONArray()
      val words =
        (0 until wordArr.length()).mapNotNull { idx ->
          val w = wordArr.optJSONObject(idx) ?: return@mapNotNull null
          WordAssessment(
            word = w.optString("word"),
            status = w.optString("status", "needs_polish"),
            phoneticTip = w.optString("phoneticTip"),
          )
        }

      val metricsLabel =
        buildString {
          append("Verified ASR match: $measuredWordAccuracyPct% · Pace: $measuredWpm WPM")
          append(
            if (hasAudio) " · Multimodal audio analyzed by Gemini"
            else " · Transcript-only estimate (no audio analyzed)"
          )
        }

      AiCallResult.Success(
        PronunciationEvaluation(
          targetSentence = targetSentence,
          recognizedTranscript = recognizedTranscript,
          estimatedOverallScore = obj.optInt("estimatedOverallScore", measuredWordAccuracyPct).coerceIn(0, 100),
          verifiedWordAccuracyPct = measuredWordAccuracyPct,
          measuredWpm = measuredWpm,
          audioAnalyzed = hasAudio,
          verifiedMetricsSummary = metricsLabel,
          clarityAndArticulationNote = obj.optString("clarityAndArticulationNote"),
          rhythmAndStressNote = obj.optString("rhythmAndStressNote"),
          l1InterferenceTips = obj.optString("l1InterferenceTips"),
          practiceDrillTip = obj.optString("practiceDrillTip"),
          wordAssessments = words,
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Pronunciation coaching timed out", e)
      AiCallResult.Error("Pronunciation analysis timed out after 60 seconds.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Pronunciation analysis failed", e)
      AiCallResult.Error(e.localizedMessage ?: "Pronunciation coaching failed.")
    }
  }

  suspend fun evaluateFreeSpeaking(
    userTranscript: String,
    audioBytes: ByteArray?,
    audioMimeType: String,
    targetLanguage: String,
  ): AiCallResult<FreeSpeakResult> {
    return try {
      val hasAudio = audioBytes != null && audioBytes.isNotEmpty() && audioBytes.size < 18_000_000
      val prompt =
        """
        You are LinguaFloat's bilingual conversation and pronunciation partner ($targetLanguage).
        The user spoke freely. Transcript: "$userTranscript".
        Respond naturally in $targetLanguage, translate your reply so the learner understands, gently correct grammar/fluency, suggest a more natural phrasing, and give brief pronunciation feedback.
        """
          .trimIndent()

      val response =
        withTimeout(60_000L) {
          if (hasAudio && audioBytes != null) {
            freeSpeakModel.generateContent(
              content {
                inlineData(audioBytes, audioMimeType)
                text(prompt)
              }
            )
          } else {
            freeSpeakModel.generateContent(prompt)
          }
        }

      val obj = JSONObject(response.text ?: throw IllegalStateException("Empty conversation reply."))
      AiCallResult.Success(
        FreeSpeakResult(
          userTranscript = userTranscript,
          conversationalReply = obj.optString("conversationalReply"),
          replyTranslation = obj.optString("replyTranslation"),
          grammarAndFluencyFeedback = obj.optString("grammarAndFluencyFeedback"),
          pronunciationAndAccentTips = obj.optString("pronunciationAndAccentTips"),
          improvedNaturalVersion = obj.optString("improvedNaturalVersion"),
          audioAnalyzed = hasAudio,
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Free speaking feedback timed out", e)
      AiCallResult.Error("Conversation coaching timed out.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Free speaking feedback failed", e)
      AiCallResult.Error(e.localizedMessage ?: "Conversation feedback failed.")
    }
  }

  suspend fun extractArabicAndEnglishOcr(bitmap: Bitmap): AiCallResult<String> {
    return try {
      val stream = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
      val jpegBytes = stream.toByteArray()
      if (jpegBytes.size >= 18_000_000) {
        return AiCallResult.Error("Rendered page image exceeds 18 MB limit.")
      }

      val response =
        withTimeout(60_000L) {
          generalModel.generateContent(
            content {
              image(bitmap)
              text(
                "Perform accurate OCR on this document page. Extract all Arabic and English text in natural reading order, preserving paragraphs and diacritics where present. Return only the extracted text without extra commentary."
              )
            }
          )
        }
      val text = response.text?.trim().orEmpty()
      if (text.isBlank()) AiCallResult.Error("No text detected in page image.")
      else AiCallResult.Success(text)
    } catch (e: TimeoutCancellationException) {
      AiCallResult.Error("Vision OCR timed out.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini Vision OCR failed", e)
      AiCallResult.Error(e.localizedMessage ?: "Vision OCR failed.")
    }
  }

  private fun parseStringArray(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
  }

  companion object {
    private const val TAG = "LinguaAiService"
  }
}
