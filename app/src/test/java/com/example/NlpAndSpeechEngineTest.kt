package com.example

import com.example.data.PronunciationRuleEntity
import com.example.nlp.LanguageDetector
import com.example.nlp.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NlpAndSpeechEngineTest {

  @Test
  fun `detects all required multilingual scripts and European languages`() {
    assertEquals("ar", LanguageDetector.detectLanguage("المريض يعاني من ارتفاع ضغط الدم الشرياني"))
    assertEquals("en-US", LanguageDetector.detectLanguage("The patient has normal blood pressure."))
    assertEquals("en-GB", LanguageDetector.detectLanguage("Normal haemoglobin without anaemia or oedema; continue paracetamol."))
    assertEquals("fr", LanguageDetector.detectLanguage("Le patient présente une tension artérielle stable avec un traitement."))
    assertEquals("de", LanguageDetector.detectLanguage("Der Patient zeigt eine deutliche Besserung und hat keine Beschwerden."))
    assertEquals("es", LanguageDetector.detectLanguage("El paciente refiere mejoría clínica para el tratamiento médico."))
    assertEquals("it", LanguageDetector.detectLanguage("Il paziente non presenta febbre e respira senza difficoltà."))
    assertEquals("pt", LanguageDetector.detectLanguage("O paciente não apresenta febre com a medicação prescrita."))
    assertEquals("tr", LanguageDetector.detectLanguage("Hasta tedavi için düzenli olarak kontrol edildi ve değerler normal."))
    assertEquals("zh", LanguageDetector.detectLanguage("患者血压稳定，建议继续按时服药并复查。"))
    assertEquals("ja", LanguageDetector.detectLanguage("患者の血圧は安定しており、経過は良好です。"))
    assertEquals("ko", LanguageDetector.detectLanguage("환자의 혈압은 안정적이며 추가 검사가 필요합니다."))
    assertEquals("hi", LanguageDetector.detectLanguage("रोगी का रक्तचाप सामान्य है और स्थिति स्थिर है।"))
  }

  @Test
  fun `segments mixed Arabic and English medical text cleanly`() {
    val mixed = "المريض يأخذ Metformin 500 mg يومياً بعد الطعام."
    val segments =
      LanguageDetector.segmentMixedLanguageRuns(
        rawText = mixed,
        primaryLanguageTag = "ar",
        enableMixedSwitching = true,
      )

    assertTrue("Expected at least 3 segments, got ${segments.size}", segments.size >= 3)
    assertEquals("ar", segments.first().languageTag)
    assertTrue(segments.any { it.languageTag.startsWith("en") && it.text.contains("Metformin") })
    assertTrue(segments.last().languageTag == "ar")
  }

  @Test
  fun `preserves medical abbreviations and decimals during sentence segmentation`() {
    val text =
      "Dr. Alvarez checked HbA1c 7.4% and BP 148/92 mmHg. Continue Metformin 500 mg b.i.d. Follow up tomorrow."
    val sentences = TextNormalizer.splitIntoSentences(text)
    assertEquals(3, sentences.size)
    assertTrue(sentences[0].startsWith("Dr. Alvarez"))
    assertTrue(sentences[0].contains("7.4%"))
    assertTrue(sentences[1].contains("b.i.d."))
    assertTrue(sentences[2].startsWith("Follow up"))
  }

  @Test
  fun `preserves Arabic diacritics and strips Tatweel while applying custom dictionary`() {
    val arabicWithDiacritics = "مُلَخَّصُ الحَالَةِ السَّـرِيرِيَّةِ: HbA1c 7.4%"
    val rules =
      listOf(
        PronunciationRuleEntity(
          term = "HbA1c",
          spokenReplacement = "hemoglobin A 1 C",
          languageTag = "all",
        )
      )
    val parsed =
      TextNormalizer.parseDocument(
        rawText = arabicWithDiacritics,
        manualLanguageTag = null,
        autoDetect = true,
        defaultLanguageTag = "auto",
        enableMixedArabicEnglish = true,
        customRules = rules,
      )

    assertEquals(1, parsed.size)
    val sentence = parsed.first()
    assertTrue(sentence.rawText.contains("مُلَخَّصُ"))
    assertFalse(sentence.spokenText.contains("\u0640"))
    assertTrue(sentence.spokenText.contains("hemoglobin A 1 C"))
  }

  @Test
  fun `detects clinical headings and citations for optional skipping`() {
    val doc =
      """
      HISTORY OF PRESENT ILLNESS:
      Patient is stable on current regimen.

      References:
      [1] Smith J et al. Cardiology Journal 2024.
      """
        .trimIndent()

    val sentences =
      TextNormalizer.parseDocument(
        rawText = doc,
        manualLanguageTag = "en-US",
        autoDetect = false,
        defaultLanguageTag = "en-US",
        enableMixedArabicEnglish = false,
        customRules = emptyList(),
      )

    assertTrue(sentences[0].isHeadingOrReference)
    assertFalse(sentences[1].isHeadingOrReference)
    assertTrue(sentences[2].isHeadingOrReference)
    assertTrue(sentences[3].isHeadingOrReference)
  }
}
