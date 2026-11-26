package com.example.nlp

import com.example.model.SpeechSegment

object LanguageDetector {

  private val arabicRegex = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF]")
  private val cjkRegex = Regex("[\\u4E00-\\u9FFF\\u3400-\\u4DBF]")
  private val hiraganaKatakanaRegex = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF]")
  private val hangulRegex = Regex("[\\uAC00-\\uD7AF\\u1100-\\u11FF]")
  private val devanagariRegex = Regex("[\\u0900-\\u097F]")
  private val latinLetterRegex = Regex("[A-Za-zÀ-ÖØ-öø-ÿĞğİıŞşÇçÖöÜü]")

  private val frenchMarkers =
    setOf("le", "la", "les", "des", "une", "est", "avec", "pour", "sans", "dans", "chez", "patient")
  private val germanMarkers =
    setOf("der", "die", "das", "und", "mit", "nach", "bei", "keine", "eine", "einer", "patient")
  private val spanishMarkers =
    setOf("el", "la", "los", "las", "con", "para", "sin", "del", "una", "paciente", "refiere")
  private val italianMarkers =
    setOf("il", "lo", "gli", "con", "per", "senza", "della", "nella", "paziente", "che", "sono")
  private val portugueseMarkers =
    setOf("os", "as", "com", "para", "sem", "uma", "não", "são", "paciente", "pelo", "pela")
  private val turkishMarkers =
    setOf("ve", "ile", "bir", "için", "olan", "hasta", "olarak", "ancak", "gibi", "daha")
  private val ukMarkers =
    setOf("haemoglobin", "anaemia", "oedema", "paracetamol", "paediatric", "aetiology", "colour", "flavour")

  fun isRtlLanguage(languageTag: String): Boolean =
    languageTag.startsWith("ar", ignoreCase = true)

  fun containsArabic(text: String): Boolean = arabicRegex.containsMatchIn(text)

  fun detectLanguage(text: String, fallbackTag: String = "en-US"): String {
    if (text.isBlank()) return fallbackTag

    val arabicCount = arabicRegex.findAll(text).count()
    val kanaCount = hiraganaKatakanaRegex.findAll(text).count()
    val hangulCount = hangulRegex.findAll(text).count()
    val cjkCount = cjkRegex.findAll(text).count()
    val devanagariCount = devanagariRegex.findAll(text).count()
    val latinCount = latinLetterRegex.findAll(text).count()

    if (kanaCount >= 2 || (kanaCount > 0 && cjkCount > 0)) return "ja"
    if (hangulCount >= 2) return "ko"
    if (cjkCount >= 2) return "zh"
    if (devanagariCount >= 2) return "hi"
    if (arabicCount >= 2 && arabicCount >= latinCount / 2) return "ar"
    if (arabicCount > 0 && latinCount == 0) return "ar"

    if (latinCount > 0) {
      return detectLatinLanguage(text, fallbackTag)
    }

    return if (arabicCount > 0) "ar" else fallbackTag
  }

  private fun detectLatinLanguage(text: String, fallbackTag: String): String {
    val lower = text.lowercase()

    // Strong diacritic cues first
    if (Regex("[ğışçöü]").containsMatchIn(lower) && containsAnyWord(lower, turkishMarkers)) {
      return "tr"
    }
    if (lower.contains("ğ") || lower.contains("ş") || lower.contains("ı")) return "tr"
    if (lower.contains("ã") || lower.contains("õ") || lower.contains("çã")) return "pt"
    if (lower.contains("ß") || (lower.contains("ä") && containsAnyWord(lower, germanMarkers))) {
      return "de"
    }
    if (lower.contains("ñ") || lower.contains("¿") || lower.contains("¡")) return "es"

    val words =
      lower
        .split(Regex("[^a-zà-öø-ÿğışç]+"))
        .filter { it.isNotBlank() }

    if (words.isEmpty()) return if (fallbackTag.startsWith("en")) fallbackTag else "en-US"

    val frScore =
      words.count { it in frenchMarkers } +
        if (lower.contains("œ") || lower.contains("é") || lower.contains("è") || lower.contains("à ")) 2 else 0
    val deScore =
      words.count { it in germanMarkers } +
        if (lower.contains("ü") || lower.contains("ö") || lower.contains("ä")) 1 else 0
    val esScore =
      words.count { it in spanishMarkers } +
        if (lower.contains("ó") || lower.contains("á") || lower.contains("í")) 1 else 0
    val itScore =
      words.count { it in italianMarkers } +
        if (lower.contains("è ") || lower.contains("più") || lower.contains("perché")) 2 else 0
    val ptScore =
      words.count { it in portugueseMarkers } +
        if (lower.contains("ção") || lower.contains("ões")) 2 else 0
    val trScore = words.count { it in turkishMarkers }

    val scores =
      listOf(
        "fr" to frScore,
        "de" to deScore,
        "es" to esScore,
        "it" to itScore,
        "pt" to ptScore,
        "tr" to trScore,
      )
    val best = scores.maxByOrNull { it.second }
    if (best != null && best.second >= 2) {
      return best.first
    }

    if (words.any { it in ukMarkers }) return "en-GB"
    return if (fallbackTag.equals("en-GB", ignoreCase = true)) "en-GB" else "en-US"
  }

  private fun containsAnyWord(lowerText: String, markers: Set<String>): Boolean {
    return markers.any { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(lowerText) }
  }

  /**
   * Splits mixed Arabic-English (or mixed-script) text into contiguous language runs so the TTS
   * engine can switch voices mid-sentence cleanly without stuttering on individual spaces/numbers.
   */
  fun segmentMixedLanguageRuns(
    rawText: String,
    primaryLanguageTag: String,
    enableMixedSwitching: Boolean,
  ): List<SpeechSegment> {
    if (rawText.isBlank()) return emptyList()
    if (!enableMixedSwitching) {
      return listOf(
        SpeechSegment(
          text = rawText,
          spokenText = rawText,
          languageTag = primaryLanguageTag,
          isRtl = isRtlLanguage(primaryLanguageTag),
        )
      )
    }

    val hasArabic = containsArabic(rawText)
    val hasLatin = latinLetterRegex.containsMatchIn(rawText)
    if (!hasArabic || !hasLatin) {
      return listOf(
        SpeechSegment(
          text = rawText,
          spokenText = rawText,
          languageTag = primaryLanguageTag,
          isRtl = isRtlLanguage(primaryLanguageTag),
        )
      )
    }

    // Tokenize into words/punctuation while grouping same-script tokens + attached numbers/units
    val tokenRegex = Regex("""\S+\s*""")
    val tokens = tokenRegex.findAll(rawText).map { it.value }.toList()
    if (tokens.isEmpty()) return emptyList()

    val runs = mutableListOf<Pair<String, StringBuilder>>()
    var currentLang = classifyTokenLanguage(tokens.first(), primaryLanguageTag)
    var buffer = StringBuilder()

    for (token in tokens) {
      val tokenLang = classifyTokenLanguage(token, currentLang)
      if (tokenLang == currentLang) {
        buffer.append(token)
      } else {
        if (buffer.isNotEmpty()) {
          runs.add(currentLang to buffer)
        }
        currentLang = tokenLang
        buffer = StringBuilder(token)
      }
    }
    if (buffer.isNotEmpty()) {
      runs.add(currentLang to buffer)
    }

    return runs
      .map { (lang, sb) ->
        val chunk = sb.toString().trim()
        SpeechSegment(
          text = chunk,
          spokenText = chunk,
          languageTag = lang,
          isRtl = isRtlLanguage(lang),
        )
      }
      .filter { it.text.isNotBlank() }
  }

  private fun classifyTokenLanguage(token: String, fallbackLang: String): String {
    val ar = arabicRegex.containsMatchIn(token)
    val lat = latinLetterRegex.containsMatchIn(token)
    return when {
      ar && !lat -> "ar"
      lat && !ar -> if (fallbackLang.equals("en-GB", ignoreCase = true)) "en-GB" else "en-US"
      else -> fallbackLang
    }
  }
}
