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
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.model.PlaybackStatus
import com.example.model.ReaderSentence
import com.example.model.ReaderSessionState
import com.example.model.SupportedLanguages
import com.example.ui.theme.NewsreaderFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
  state: ReaderSessionState,
  onJumpToSentence: (Int) -> Unit,
  onReadParagraph: (Int) -> Unit,
  onToggleSkipHeadings: () -> Unit,
  onToggleRepeat: () -> Unit,
  onBookmark: () -> Unit,
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
      Text("Smart Reader Ready", style = MaterialTheme.typography.headlineMedium)
      Spacer(Modifier.height(8.dp))
      Text(
        "Open any clinical note, PDF, scanned OCR document, or highlight text in another app and select 'Read with ClinNote'.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(18.dp))
      Button(onClick = onNavigateToLibrary) { Text("Browse Documents") }
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
              label = { Text("Skip Headings/Refs") },
              modifier = Modifier.testTag("skip_headings_chip"),
            )
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

    // Sentences grouped visually with paragraph markers & live highlight
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
        onTapSentence = { onJumpToSentence(sentence.index) },
        onTapParagraph = { onReadParagraph(sentence.paragraphIndex) },
      )
    }

    item { Spacer(Modifier.height(88.dp)) }
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
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
                  if (sentence.isHeadingOrReference) append(" · HEADING/REF")
                  if (isSkipped) append(" (SKIPPED)")
                },
              style = MaterialTheme.typography.labelSmall,
              color =
                if (isActive) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isSpeaking) {
              Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = "Currently speaking",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
              )
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
