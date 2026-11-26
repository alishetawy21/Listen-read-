package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.TranslationHistoryEntity
import com.example.model.TranslationTone
import com.example.nlp.LanguageDetector
import com.example.ui.TranslationUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslationScreen(
  inputText: String,
  sourceLang: String,
  targetLang: String,
  tone: TranslationTone,
  uiState: TranslationUiState,
  history: List<TranslationHistoryEntity>,
  onInputChange: (String) -> Unit,
  onSelectLanguages: (String, String) -> Unit,
  onSwapLanguages: () -> Unit,
  onSelectTone: (TranslationTone) -> Unit,
  onTranslate: () -> Unit,
  onListenText: (String, String) -> Unit,
  onPracticeInCoach: (String, String) -> Unit,
  onSavePhrase: (String, String, String, String, String) -> Unit,
  onDeleteHistoryItem: (Long) -> Unit,
  onClearHistory: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val isInputRtl = LanguageDetector.isRtlLanguage(
    if (sourceLang == "auto") LanguageDetector.detectLanguage(inputText) else sourceLang
  )

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 1. Header & Language Pair Selector
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Contextual Arabic ↔ English Translator", style = MaterialTheme.typography.titleLarge)

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            AssistChip(
              onClick = {
                val nextSrc =
                  when (sourceLang) {
                    "auto" -> "ar"
                    "ar" -> "en-US"
                    else -> "auto"
                  }
                onSelectLanguages(nextSrc, targetLang)
              },
              label = { Text("From: ${formatLangBadge(sourceLang)}") },
              modifier = Modifier.testTag("translation_source_chip"),
            )

            IconButton(
              onClick = onSwapLanguages,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("translation_swap_button"),
            ) {
              Icon(Icons.Rounded.SwapHoriz, contentDescription = "Swap languages")
            }

            AssistChip(
              onClick = {
                val nextTgt =
                  when (targetLang) {
                    "ar" -> "en-US"
                    "en-US" -> "en-GB"
                    else -> "ar"
                  }
                onSelectLanguages(sourceLang, nextTgt)
              },
              label = { Text("To: ${formatLangBadge(targetLang)}") },
              modifier = Modifier.testTag("translation_target_chip"),
            )
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            TranslationTone.entries.forEach { t ->
              FilterChip(
                selected = tone == t,
                onClick = { onSelectTone(t) },
                label = { Text(t.label) },
              )
            }
          }

          CompositionLocalProvider(
            LocalLayoutDirection provides if (isInputRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
          ) {
            OutlinedTextField(
              value = inputText,
              onValueChange = onInputChange,
              label = { Text("Enter Arabic or English sentence, idiom, or paragraph") },
              minLines = 3,
              maxLines = 7,
              modifier = Modifier.fillMaxWidth().testTag("translation_input"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = onTranslate,
              enabled = inputText.isNotBlank() && uiState !is TranslationUiState.Loading,
              modifier = Modifier.weight(1f).testTag("translate_submit_button"),
            ) {
              Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Translate")
            }

            FilledTonalButton(
              onClick = {
                val lang = if (sourceLang == "auto") LanguageDetector.detectLanguage(inputText) else sourceLang
                onListenText(inputText, lang)
              },
              enabled = inputText.isNotBlank(),
            ) {
              Icon(Icons.Rounded.VolumeUp, contentDescription = "Listen to original text", modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Original")
            }
          }
        }
      }
    }

    // 2. Loading / Error / Result Side-by-Side Workspace
    when (uiState) {
      is TranslationUiState.Idle -> {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                "Try medical, academic, or idiomatic phrases (e.g., \"Break the ice\" or \"على قدم وساق\") to see contextual nuance, alternatives, and vocabulary notes.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      }
      is TranslationUiState.Loading -> {
        item {
          Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
              Spacer(Modifier.width(12.dp))
              Text("Translating contextually via Gemini...", style = MaterialTheme.typography.bodyLarge)
            }
          }
        }
      }
      is TranslationUiState.Error -> {
        item {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = uiState.message,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      }
      is TranslationUiState.Success -> {
        val result = uiState.result
        val outRtl = LanguageDetector.isRtlLanguage(result.targetLanguage)

        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().testTag("translation_result_card"),
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
                  Text(
                    text = "${result.detectedSourceLanguage.uppercase()} → ${result.targetLanguage.uppercase()} · ${result.toneAndRegister}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }

                Row {
                  IconButton(
                    onClick = {
                      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      cm.setPrimaryClip(ClipData.newPlainText("LinguaFloat Translation", result.translatedText))
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy translation")
                  }
                  IconButton(
                    onClick = {
                      val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${result.sourceText}\n\n${result.translatedText}")
                      }
                      context.startActivity(Intent.createChooser(share, "Share Translation"))
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share translation")
                  }
                }
              }

              CompositionLocalProvider(
                LocalLayoutDirection provides if (outRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
              ) {
                Text(
                  text = result.translatedText,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.SemiBold,
                )
              }

              if (result.literalVsContextualNote.isNotBlank()) {
                Text(
                  text = result.literalVsContextualNote,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                FilledTonalButton(onClick = { onListenText(result.translatedText, result.targetLanguage) }) {
                  Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(5.dp))
                  Text("Listen Translation")
                }
                OutlinedButton(
                  onClick = {
                    onSavePhrase(
                      result.sourceText,
                      result.translatedText,
                      result.detectedSourceLanguage,
                      result.vocabularyBreakdown.firstOrNull()?.pronunciationGuide.orEmpty(),
                      result.literalVsContextualNote,
                    )
                  }
                ) {
                  Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(5.dp))
                  Text("Save Phrase")
                }
                OutlinedButton(onClick = { onPracticeInCoach(result.translatedText, result.targetLanguage) }) {
                  Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(5.dp))
                  Text("Pronounce")
                }
              }
            }
          }
        }

        if (result.alternativeTranslations.isNotEmpty()) {
          item {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Natural Alternatives", style = MaterialTheme.typography.titleSmall)
                result.alternativeTranslations.forEach { alt ->
                  Text("• $alt", style = MaterialTheme.typography.bodyMedium)
                }
              }
            }
          }
        }

        if (result.vocabularyBreakdown.isNotEmpty()) {
          item {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Key Vocabulary & Idioms Breakdown", style = MaterialTheme.typography.titleSmall)
                result.vocabularyBreakdown.forEach { item ->
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Text(item.term, style = MaterialTheme.typography.titleSmall)
                        if (item.pronunciationGuide.isNotBlank()) {
                          Text(
                            item.pronunciationGuide,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                          )
                        }
                      }
                      Text(item.meaning, style = MaterialTheme.typography.bodyMedium)
                      if (item.usageExample.isNotBlank()) {
                        Text(
                          text = "Example: ${item.usageExample}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // 3. Translation History
    if (history.isNotEmpty()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Recent Translations (${history.size})", style = MaterialTheme.typography.titleMedium)
          TextButton(onClick = onClearHistory) { Text("Clear") }
        }
      }

      items(history.take(15), key = { it.id }) { entry ->
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = entry.sourceText,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = entry.translatedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${entry.sourceLanguage.uppercase()} → ${entry.targetLanguage.uppercase()} · ${entry.tone}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(
              onClick = { onListenText(entry.translatedText, entry.targetLanguage) },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Rounded.VolumeUp, contentDescription = "Listen")
            }
            IconButton(
              onClick = { onDeleteHistoryItem(entry.id) },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete translation")
            }
          }
        }
      }
    }

    item { Spacer(Modifier.height(76.dp)) }
  }
}

private fun formatLangBadge(tag: String): String =
  when {
    tag == "auto" -> "Auto"
    tag.startsWith("ar", true) -> "Arabic"
    tag.equals("en-GB", true) -> "English (UK)"
    else -> "English (US)"
  }
