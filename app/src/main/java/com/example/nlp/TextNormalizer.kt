package com.example.nlp

import com.example.data.PronunciationRuleEntity
import com.example.model.ReaderSentence
import com.example.model.SpeechSegment

object TextNormalizer {

  private val titleAbbreviations =
    setOf(
      "dr.",
      "mr.",
      "mrs.",
      "ms.",
      "prof.",
      "st.",
      "fig.",
      "no.",
      "vol.",
      "د.",
      "أ.د.",
      "م.",
    )

  private val inlineAbbreviations =
    setOf(
      "vs.",
      "e.g.",
      "i.e.",
      "etc.",
      "approx.",
      "b.i.d.",
      "t.i.d.",
      "q.i.d.",
      "q.d.",
      "p.r.n.",
      "q.h.s.",
      "a.c.",
      "p.c.",
      "o.d.",
      "o.s.",
      "o.u.",
      "et al.",
    )

  private val referenceLineRegex =
    Regex("""^(\[\d+\]|\d+\.\s+[A-Z].*et al\.|references:?|bibliography:?|المراجع:?|المصادر:?)$""", RegexOption.IGNORE_CASE)

  private val numberedCitationRegex = Regex("""^\[\d+\]\s+.*""")

  fun cleanArabicTatweel(text: String): String = text.replace("\u0640", "")

  fun isHeadingOrReference(line: String, paragraphLinesCount: Int): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    if (referenceLineRegex.matches(trimmed) || numberedCitationRegex.matches(trimmed)) return true
    if (trimmed.startsWith("#")) return true
    // All-uppercase clinical section header like "HISTORY OF PRESENT ILLNESS:"
    val letters = trimmed.filter { it.isLetter() }
    if (
      letters.length >= 4 &&
        letters.all { it.isUpperCase() } &&
        trimmed.length <= 72 &&
        paragraphLinesCount <= 2
    ) {
      return true
    }
    if (trimmed.endsWith(":") && trimmed.split(Regex("\\s+")).size <= 6 && !trimmed.contains(".")) {
      return true
    }
    return false
  }

  fun parseDocument(
    rawText: String,
    manualLanguageTag: String?,
    autoDetect: Boolean,
    defaultLanguageTag: String,
    enableMixedArabicEnglish: Boolean,
    customRules: List<PronunciationRuleEntity>,
  ): List<ReaderSentence> {
    if (rawText.isBlank()) return emptyList()

    val normalizedNewlines = rawText.replace("\r\n", "\n").replace('\r', '\n')
    val paragraphs =
      normalizedNewlines
        .split(Regex("\n\\s*\n+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val result = mutableListOf<ReaderSentence>()
    var sentenceIdx = 0

    paragraphs.forEachIndexed { paragraphIdx, paragraph ->
      // Preserve single-line headings inside paragraphs as distinct lines if followed by body text
      val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
      lines.forEach { line ->
        val isHeading = isHeadingOrReference(line, lines.size)
        val splitSentences =
          if (isHeading) listOf(line) else splitIntoSentences(line)

        splitSentences.forEach { rawSentence ->
          val cleaned = rawSentence.trim()
          if (cleaned.isNotEmpty()) {
            val detectedLang =
              when {
                !manualLanguageTag.isNullOrBlank() && manualLanguageTag != "auto" -> manualLanguageTag
                autoDetect ->
                  LanguageDetector.detectLanguage(
                    cleaned,
                    fallbackTag = if (defaultLanguageTag == "auto") "en-US" else defaultLanguageTag,
                  )
                defaultLanguageTag != "auto" -> defaultLanguageTag
                else -> LanguageDetector.detectLanguage(cleaned, "en-US")
              }

            val segments =
              LanguageDetector.segmentMixedLanguageRuns(
                  rawText = cleaned,
                  primaryLanguageTag = detectedLang,
                  enableMixedSwitching = enableMixedArabicEnglish,
                )
                .map { seg ->
                  val spoken = applyMedicalNormalization(seg.spokenText, seg.languageTag, customRules)
                  seg.copy(spokenText = spoken)
                }

            val spokenFull = segments.joinToString(" ") { it.spokenText }

            result.add(
              ReaderSentence(
                index = sentenceIdx++,
                paragraphIndex = paragraphIdx,
                rawText = cleaned,
                spokenText = spokenFull,
                isHeadingOrReference = isHeading,
                detectedLanguageTag = detectedLang,
                isRtl = LanguageDetector.isRtlLanguage(detectedLang),
                segments = segments,
              )
            )
          }
        }
      }
    }

    return result
  }

  fun splitIntoSentences(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val sentences = mutableListOf<String>()
    val current = StringBuilder()

    var i = 0
    while (i < text.length) {
      val ch = text[i]
      current.append(ch)

      val isSentenceTerminator = ch == '.' || ch == '!' || ch == '?' || ch == '؟' || ch == '。' || ch == '！' || ch == '？'
      if (isSentenceTerminator) {
        val nextChar = text.getOrNull(i + 1)
        val prevChar = text.getOrNull(i - 1)

        val isDecimalOrRatio =
          ch == '.' && prevChar?.isDigit() == true && nextChar?.isDigit() == true

        val currentLower = current.toString().trim().lowercase()
        val lastToken = currentLower.substringAfterLast(' ').trim()
        val nextNonSpaceChar = text.drop(i + 1).firstOrNull { !it.isWhitespace() }
        val isTitleAbbr =
          ch == '.' &&
            (titleAbbreviations.any { currentLower.endsWith(it) } ||
              titleAbbreviations.contains(lastToken) ||
              isSingleInitial(current.toString()))
        val isInlineAbbrMidSentence =
          ch == '.' &&
            (inlineAbbreviations.any { currentLower.endsWith(it) } ||
              inlineAbbreviations.contains(lastToken)) &&
            (nextNonSpaceChar != null && !nextNonSpaceChar.isUpperCase())
        val isAbbreviation = isTitleAbbr || isInlineAbbrMidSentence

        val atEndOrWhitespace = nextChar == null || nextChar.isWhitespace() || nextChar == '"' || nextChar == '\'' || nextChar == ')' || nextChar == '»'

        if (!isDecimalOrRatio && !isAbbreviation && atEndOrWhitespace) {
          // Consume trailing closing quotes/punctuation
          while (i + 1 < text.length && text[i + 1] in listOf('"', '\'', ')', '»', '”')) {
            i++
            current.append(text[i])
          }
          val trimmed = current.toString().trim()
          if (trimmed.isNotEmpty()) {
            sentences.add(trimmed)
          }
          current.clear()
        }
      }
      i++
    }

    val remainder = current.toString().trim()
    if (remainder.isNotEmpty()) {
      sentences.add(remainder)
    }
    return sentences
  }

  private fun isSingleInitial(buffer: String): Boolean {
    val trimmed = buffer.trimEnd()
    if (trimmed.length < 2) return false
    val secondLast = trimmed[trimmed.length - 2]
    val thirdLast = trimmed.getOrNull(trimmed.length - 3)
    return secondLast.isUpperCase() && (thirdLast == null || thirdLast.isWhitespace())
  }

  fun applyMedicalNormalization(
    text: String,
    languageTag: String,
    customRules: List<PronunciationRuleEntity>,
  ): String {
    var out = cleanArabicTatweel(text)

    // Remove markdown heading hashes cleanly
    out = out.replace(Regex("^#+\\s*"), "")

    // Apply user custom pronunciation dictionary first (case-insensitive word/phrase match)
    for (rule in customRules) {
      if (!rule.isEnabled) continue
      val matchesLang =
        rule.languageTag == "all" ||
          languageTag.startsWith(rule.languageTag, ignoreCase = true)
      if (matchesLang && rule.term.isNotBlank()) {
        val pattern = Regex("(?i)(?<!\\w)${Regex.escape(rule.term)}(?!\\w)")
        out = out.replace(pattern, rule.spokenReplacement)
      }
    }

    val isArabic = languageTag.startsWith("ar", ignoreCase = true)

    // Clinical symbols & ratios normalization
    out =
      out
        .replace(Regex("""(\d+)\s*/\s*(\d+)\s*mmHg""", RegexOption.IGNORE_CASE), "$1 over $2 millimeters of mercury")
        .replace("°C", if (isArabic) " درجة مئوية" else " degrees Celsius")
        .replace("°F", if (isArabic) " درجة فهرنهايت" else " degrees Fahrenheit")
        .replace("µg", if (isArabic) " ميكروغرام" else " micrograms")
        .replace("mcg", if (isArabic) " ميكروغرام" else " micrograms")
        .replace("≥", if (isArabic) " أكبر من أو يساوي " else " greater than or equal to ")
        .replace("≤", if (isArabic) " أصغر من أو يساوي " else " less than or equal to ")
        .replace("↑", if (isArabic) " مرتفع " else " elevated ")
        .replace("↓", if (isArabic) " منخفض " else " decreased ")

    if (!isArabic) {
      val builtinEnglishClinical =
        listOf(
          Regex("""\bBP\b""") to "blood pressure",
          Regex("""\bHR\b""") to "heart rate",
          Regex("""\bRR\b""") to "respiratory rate",
          Regex("""\bBPM\b""") to "beats per minute",
          Regex("""\bIV\b""") to "intravenous",
          Regex("""\bIM\b""") to "intramuscular",
          Regex("""\bPO\b""") to "by mouth",
          Regex("""\bECG\b""") to "E C G",
          Regex("""\bCBC\b""") to "C B C",
          Regex("""\bMRI\b""") to "M R I",
          Regex("""\bCT\b""") to "C T",
          Regex("""\bmg\b""") to "milligrams",
          Regex("""\bmL\b""") to "milliliters",
          Regex("""\bmEq/L\b""") to "milliequivalents per liter",
          Regex("""\bpg/mL\b""") to "picograms per milliliter",
          Regex("""\bmL/min\b""") to "milliliters per minute",
          Regex("""\bDr\.""") to "Doctor",
        )
      for ((regex, replacement) in builtinEnglishClinical) {
        out = out.replace(regex, replacement)
      }
    } else {
      // Arabic clinical abbreviations while preserving Arabic diacritics
      out =
        out
          .replace(Regex("""\bأ\.د\."""), "الأستاذ الدكتور")
          .replace(Regex("""\bد\."""), "الدكتور")
          .replace(Regex("""\bمجم\b"""), "ملليغرام")
          .replace(Regex("""\bمل\b"""), "ملليلتر")
    }

    // Clean excessive whitespace
    return out.replace(Regex("\\s+"), " ").trim()
  }
}
