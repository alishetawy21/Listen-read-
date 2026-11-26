package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ListenAudioMode
import com.example.model.PlaybackStatus
import com.example.model.ReaderSentence
import com.example.model.ReaderSessionState
import com.example.model.SupportedLanguages
import com.example.model.VoiceGender
import com.example.ui.InlineSentenceSheetState
import com.example.ui.theme.NewsreaderFontFamily

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
  state: ReaderSessionState,
  preferredGender: VoiceGender,
  inlineSheetState: InlineSentenceSheetState?,
  onJumpToSentence: (Int) -> Unit,
  onReadParagraph: (Int) -> Unit,
  onToggleSkipHeadings: () -> Unit,
  onToggleRepeat: () -> Unit,
  onBookmark: () -> Unit,
  onSelectVolume: (Float) -> Unit,
  onSelectGender: (VoiceGender) -> Unit,
  onSelectListenAudioMode: (ListenAudioMode) -> Unit,
  onOpenSentenceAssist: (String, String) -> Unit,
  onExplainSheetPassage: () -> Unit,
  onDismissSentenceAssist: () -> Unit,
  onSpeakTextSlowly: (String, String) -> Unit,
  onPracticeInCoach: (String, String) -> Unit,
  onSavePhrase: (String, String, String, String) -> Unit,
  onNavigateToLibrary: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!state.hasContent) {
    Column(
      modifier = modifier.fillMaxSize().padding(28.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = Icons.Rounded.MenuBook,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
      )
      Spacer(Modifier.height(14.dp))
      Text("Smart Book & PDF Reader", style = MaterialTheme.typography.headlineMedium)
      Spacer(Modifier.height(8.dp))
      Text(
        "Open any PDF, EPUB, TXT book, scanned OCR page, or select text in another app and tap 'LinguaFloat: Read / Translate'.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(18.dp))
      Button(onClick = onNavigateToLibrary) { Text("Browse Books & PDFs") }
    }
    return
  }

  val listState = rememberLazyListState()
  LaunchedEffect(state.currentSentenceIndex, state.status) {
    if (state.status == PlaybackStatus.PLAYING && state.currentSentenceIndex in state.sentences.indices) {
      listState.animateScrollToItem((state.currentSentenceIndex + 1).coerceAtMost(state.sentences.size))
    }
  }

  val langDescriptor = SupportedLanguages.findByTag(state.activeLanguageTag)

  LazyColumn(
    state = listState,
    modifier = modifier.fillMaxSize().testTag("reader_sentence_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Document Header & Controls
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(10.dp),
            ) {
              Text(
                text = state.captureSource.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = onToggleRepeat,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("reader_repeat_button"),
              ) {
                Icon(
                  Icons.Rounded.Repeat,
                  contentDescription = "Repeat current sentence",
                  tint =
                    if (state.repeatCurrentSentence) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = onBookmark,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("reader_bookmark_button"),
              ) {
                Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Bookmark reading position")
              }
            }
          }

          Spacer(Modifier.height(6.dp))
          Text(text = state.title, style = MaterialTheme.typography.titleLarge)
          Spacer(Modifier.height(6.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Rounded.GraphicEq,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "${state.activeVoiceLabel} · ${state.activeQualityTier.label}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Spacer(Modifier.height(10.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            AssistChip(
              onClick = {},
              leadingIcon = {
                Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              label = { Text("${langDescriptor.displayName} (${langDescriptor.nativeName})") },
            )
            FilterChip(
              selected = state.skipHeadingsAndRefs,
              onClick = onToggleSkipHeadings,
              leadingIcon = {
                Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              label = { Text("Skip Headings") },
              modifier = Modifier.testTag("skip_headings_chip"),
            )
            ListenAudioMode.entries.forEach { mode ->
              FilterChip(
                selected = state.listenAudioMode == mode,
                onClick = { onSelectListenAudioMode(mode) },
                leadingIcon = {
                  Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(15.dp))
                },
                label = { Text("Listen: ${mode.label}") },
              )
            }
          }

          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.VolumeUp,
              contentDescription = "Volume",
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Slider(
              value = state.speechVolume,
              onValueChange = onSelectVolume,
              valueRange = 0.1f..1.0f,
              modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            VoiceGender.entries.forEach { gender ->
              FilterChip(
                selected = preferredGender == gender,
                onClick = { onSelectGender(gender) },
                label = { Text(gender.label) },
                modifier = Modifier.padding(start = 4.dp),
              )
            }
          }

          if (!state.statusMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
              text = state.statusMessage,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.tertiary,
            )
          }
          if (!state.errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
              text = state.errorMessage,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }

    // Sentences grouped visually with paragraph markers, live highlight & inline AI assist actions
    itemsIndexed(state.sentences, key = { _, s -> s.index }) { idx, sentence ->
      val prevParagraph = state.sentences.getOrNull(idx - 1)?.paragraphIndex
      val isParagraphStart = prevParagraph != sentence.paragraphIndex
      val isActive = idx == state.currentSentenceIndex
      val isSkipped = state.skipHeadingsAndRefs && sentence.isHeadingOrReference

      SentenceReaderRow(
        sentence = sentence,
        isActive = isActive,
        isSkipped = isSkipped,
        isParagraphStart = isParagraphStart,
        isSpeaking = isActive && state.status == PlaybackStatus.PLAYING,
        onTapSentence = {
          onJumpToSentence(sentence.index)
          if (state.listenAudioMode == ListenAudioMode.TRANSLATED) {
            onOpenSentenceAssist(sentence.rawText, sentence.detectedLanguageTag)
          }
        },
        onTapParagraph = { onReadParagraph(sentence.paragraphIndex) },
        onTapTranslateSentence = {
          onOpenSentenceAssist(sentence.rawText, sentence.detectedLanguageTag)
        },
        onTapPracticeSentence = {
          onPracticeInCoach(sentence.rawText, sentence.detectedLanguageTag)
        },
      )
    }

    item { Spacer(Modifier.height(88.dp)) }
  }

  if (inlineSheetState != null) {
    ModalBottomSheet(onDismissRequest = onDismissSentenceAssist) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Sentence Translation & Coach", style = MaterialTheme.typography.titleLarge)
        Text(
          text = inlineSheetState.sentenceText,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilledTonalButton(
            onClick = { onSpeakTextSlowly(inlineSheetState.sentenceText, inlineSheetState.sentenceLang) }
          ) {
            Icon(Icons.Rounded.SlowMotionVideo, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pronounce 0.75x")
          }
          OutlinedButton(
            onClick = {
              onDismissSentenceAssist()
              onPracticeInCoach(inlineSheetState.sentenceText, inlineSheetState.sentenceLang)
            }
          ) {
            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Practice")
          }
        }

        HorizontalDivider()

        if (inlineSheetState.isLoading) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Fetching contextual Arabic ↔ English insight...", style = MaterialTheme.typography.bodyMedium)
          }
        }

        if (!inlineSheetState.errorMessage.isNullOrBlank()) {
          Text(
            text = inlineSheetState.errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }

        inlineSheetState.translation?.let { tr ->
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = tr.translatedText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              if (tr.literalVsContextualNote.isNotBlank()) {
                Text(
                  text = tr.literalVsContextualNote,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                  onClick = { onSpeakTextSlowly(tr.translatedText, tr.targetLanguage) }
                ) {
                  Text("Listen Translation")
                }
                OutlinedButton(
                  onClick = {
                    onSavePhrase(
                      tr.sourceText,
                      tr.translatedText,
                      inlineSheetState.sentenceLang,
                      tr.vocabularyBreakdown.firstOrNull()?.pronunciationGuide.orEmpty(),
                    )
                  }
                ) {
                  Text("Save Phrase")
                }
              }
            }
          }
        }

        if (inlineSheetState.explanation != null) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = inlineSheetState.explanation,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(14.dp),
            )
          }
        } else if (!inlineSheetState.isLoading) {
          OutlinedButton(onClick = onExplainSheetPassage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Explain Grammar, Idioms & Pronunciation")
          }
        }

        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun SentenceReaderRow(
  sentence: ReaderSentence,
  isActive: Boolean,
  isSkipped: Boolean,
  isParagraphStart: Boolean,
  isSpeaking: Boolean,
  onTapSentence: () -> Unit,
  onTapParagraph: () -> Unit,
  onTapTranslateSentence: () -> Unit,
  onTapPracticeSentence: () -> Unit,
) {
  val containerColor by
    animateColorAsState(
      targetValue =
        when {
          isActive -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
          sentence.isHeadingOrReference -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          else -> MaterialTheme.colorScheme.surface
        },
      label = "sentence_highlight",
    )

  val layoutDir = if (sentence.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

  Column(modifier = Modifier.fillMaxWidth()) {
    if (isParagraphStart) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "PARAGRAPH ${sentence.paragraphIndex + 1}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
          modifier = Modifier.clickable(onClick = onTapParagraph),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.PlayArrow,
              contentDescription = "Read paragraph",
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
              text = "Read Paragraph",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(14.dp),
      color = containerColor,
      border =
        if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
        else null,
      modifier =
        Modifier.fillMaxWidth()
          .clickable(onClick = onTapSentence)
          .testTag("reader_sentence_${sentence.index}"),
    ) {
      CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                buildString {
                  append("#${sentence.index + 1} · ${sentence.detectedLanguageTag.uppercase()}")
                  if (sentence.segments.size > 1) append(" · MIXED")
                  if (sentence.isHeadingOrReference) append(" · HEADING")
                  if (isSkipped) append(" (SKIPPED)")
                },
              style = MaterialTheme.typography.labelSmall,
              color =
                if (isActive) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (isSpeaking) {
                Icon(
                  Icons.Rounded.GraphicEq,
                  contentDescription = "Currently speaking",
                  tint = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.size(16.dp),
                )
              }
              IconButton(
                onClick = onTapTranslateSentence,
                modifier = Modifier.size(32.dp),
              ) {
                Icon(
                  Icons.Rounded.Translate,
                  contentDescription = "Translate & explain sentence",
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(17.dp),
                )
              }
              IconButton(
                onClick = onTapPracticeSentence,
                modifier = Modifier.size(32.dp),
              ) {
                Icon(
                  Icons.Rounded.Mic,
                  contentDescription = "Practice speaking sentence",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(17.dp),
                )
              }
            }
          }

          Spacer(Modifier.height(4.dp))
          Text(
            text = sentence.rawText,
            style =
              if (sentence.isRtl) {
                MaterialTheme.typography.bodyLarge.copy(
                  fontSize = 18.sp,
                  lineHeight = 29.sp,
                  fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                )
              } else {
                MaterialTheme.typography.bodyLarge.copy(
                  fontFamily =
                    if (sentence.isHeadingOrReference) MaterialTheme.typography.titleMedium.fontFamily
                    else NewsreaderFontFamily,
                  fontSize = if (sentence.isHeadingOrReference) 15.sp else 17.sp,
                  lineHeight = 26.sp,
                  fontWeight =
                    if (isActive || sentence.isHeadingOrReference) FontWeight.SemiBold
                    else FontWeight.Normal,
                )
              },
            color =
              if (isSkipped) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
              else MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}
