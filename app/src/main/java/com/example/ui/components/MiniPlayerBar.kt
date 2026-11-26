package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.PLAYBACK_SPEED_PRESETS
import com.example.model.PlaybackStatus
import com.example.model.ReaderSessionState
import com.example.model.SupportedLanguages

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MiniPlayerBar(
  state: ReaderSessionState,
  onPlayPause: () -> Unit,
  onStop: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onRepeatToggle: () -> Unit,
  onSelectSpeed: (Float) -> Unit,
  onSelectLanguage: (String) -> Unit,
  onBookmark: () -> Unit,
  onOpenReader: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!state.hasContent) return

  var showSpeedSheet by remember { mutableStateOf(false) }
  var showLanguageSheet by remember { mutableStateOf(false) }
  var expandedExtraControls by remember { mutableStateOf(false) }

  val isPlaying = state.status == PlaybackStatus.PLAYING || state.status == PlaybackStatus.PREPARING
  val progress =
    if (state.sentences.isNotEmpty()) {
      (state.currentSentenceIndex + 1).toFloat() / state.sentences.size.toFloat()
    } else 0f
  val langDesc = SupportedLanguages.findByTag(state.activeLanguageTag)

  Surface(
    modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shadowElevation = 6.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(3.dp),
        color = MaterialTheme.colorScheme.tertiary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
      )

      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        // Top row: Title, Language chip, Expand toggle
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier =
              Modifier.weight(1f).clickable(onClick = onOpenReader).padding(end = 8.dp)
          ) {
            Text(
              text = state.title,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val currentSentence = state.currentSentence
            val sentenceDir =
              if (currentSentence?.isRtl == true) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides sentenceDir) {
              Text(
                text = currentSentence?.rawText ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Surface(
            modifier =
              Modifier.clip(RoundedCornerShape(10.dp))
                .clickable { showLanguageSheet = true }
                .testTag("mini_player_language_button"),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = "Select reading language",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = langDesc.tag.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }

          IconButton(
            onClick = { expandedExtraControls = !expandedExtraControls },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector =
                if (expandedExtraControls) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
              contentDescription = "More player controls",
            )
          }
        }

        Spacer(Modifier.height(6.dp))

        // Main transport controls row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
              onClick = onPrevious,
              modifier = Modifier.size(44.dp).testTag("mini_player_prev_button"),
            ) {
              Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous sentence")
            }

            Spacer(Modifier.width(8.dp))

            FilledIconButton(
              onClick = onPlayPause,
              modifier = Modifier.size(48.dp).testTag("mini_player_play_pause_button"),
              colors =
                IconButtonDefaults.filledIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.primary
                ),
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
              )
            }

            Spacer(Modifier.width(8.dp))

            FilledTonalIconButton(
              onClick = onNext,
              modifier = Modifier.size(44.dp).testTag("mini_player_next_button"),
            ) {
              Icon(Icons.Rounded.SkipNext, contentDescription = "Next sentence")
            }

            Spacer(Modifier.width(6.dp))

            IconButton(
              onClick = onStop,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("mini_player_stop_button"),
            ) {
              Icon(Icons.Rounded.Stop, contentDescription = "Stop playback")
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "${state.currentSentenceIndex + 1}/${state.sentences.size}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.width(8.dp))

            Surface(
              modifier =
                Modifier.clip(RoundedCornerShape(12.dp))
                  .clickable { showSpeedSheet = true }
                  .testTag("mini_player_speed_button"),
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Speed,
                  contentDescription = "Playback speed",
                  tint = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                  text = "${state.speechSpeed}x",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }
        }

        AnimatedVisibility(
          visible = expandedExtraControls,
          enter = expandVertically(),
          exit = shrinkVertically(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier =
                  Modifier.size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "${state.activeVoiceLabel} · ${state.activeQualityTier.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = onRepeatToggle,
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Repeat,
                  contentDescription = "Repeat current sentence",
                  tint =
                    if (state.repeatCurrentSentence) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = onBookmark,
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Rounded.BookmarkAdd,
                  contentDescription = "Bookmark reading position",
                )
              }
            }
          }
        }
      }
    }
  }

  if (showSpeedSheet) {
    ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Playback Speed", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
          "Select reading cadence from 0.5x to 3.0x",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          PLAYBACK_SPEED_PRESETS.forEach { preset ->
            FilterChip(
              selected = kotlin.math.abs(state.speechSpeed - preset) < 0.01f,
              onClick = {
                onSelectSpeed(preset)
                showSpeedSheet = false
              },
              label = { Text("${preset}x", style = MaterialTheme.typography.labelMedium) },
            )
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }

  if (showLanguageSheet) {
    ModalBottomSheet(onDismissRequest = { showLanguageSheet = false }) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Reading Language Override", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
          "Auto-detection works automatically, or pick a language manually.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = state.manualLanguageOverride == null,
            onClick = {
              onSelectLanguage("auto")
              showLanguageSheet = false
            },
            label = { Text("Auto-Detect") },
          )
          SupportedLanguages.ALL.forEach { lang ->
            FilterChip(
              selected = state.manualLanguageOverride.equals(lang.tag, ignoreCase = true),
              onClick = {
                onSelectLanguage(lang.tag)
                showLanguageSheet = false
              },
              label = { Text("${lang.displayName} (${lang.nativeName})") },
            )
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}
