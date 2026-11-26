package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ClinicalNoteEntity
import com.example.data.PronunciationAttemptEntity
import com.example.data.SavedPhraseEntity
import com.example.data.TranslationHistoryEntity
import com.example.model.ReaderSettings
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
  settings: ReaderSettings,
  notes: List<ClinicalNoteEntity>,
  savedPhrases: List<SavedPhraseEntity>,
  attempts: List<PronunciationAttemptEntity>,
  translations: List<TranslationHistoryEntity>,
  onToggleFloatingShortcut: (Boolean) -> Unit,
  onResumeDocument: (ClinicalNoteEntity) -> Unit,
  onOpenLibrary: () -> Unit,
  onOpenReader: () -> Unit,
  onOpenTranslation: () -> Unit,
  onOpenCoach: () -> Unit,
  onOpenPractice: () -> Unit,
  onOpenVocabulary: () -> Unit,
  onOpenProgress: () -> Unit,
  onOpenFloatingSettings: () -> Unit,
  onOpenStudio: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val latestDocument = notes.firstOrNull()
  val avgCoachScore =
    if (attempts.isNotEmpty()) attempts.map { it.overallScore }.average().roundToInt() else 0

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. LinguaFloat Hero Header Card
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(modifier = Modifier.fillMaxWidth()) {
          Image(
            painter = painterResource(id = R.drawable.img_reader_hero),
            contentDescription = "LinguaFloat bilingual language studio banner",
            contentScale = ContentScale.Crop,
            alpha = 0.25f,
            modifier = Modifier.matchParentSize(),
          )
          Box(
            modifier =
              Modifier.matchParentSize()
                .background(
                  Brush.horizontalGradient(
                    listOf(
                      MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
                      MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
                    )
                  )
                )
          )
          Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = "ARABIC ↔ ENGLISH",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }

              Surface(
                modifier =
                  Modifier.clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleFloatingShortcut(!settings.floatingShortcutEnabled) }
                    .testTag("home_floating_toggle"),
                color =
                  if (settings.floatingShortcutEnabled) MaterialTheme.colorScheme.tertiary
                  else Color.White.copy(alpha = 0.22f),
              ) {
                Text(
                  text =
                    if (settings.floatingShortcutEnabled) "Floating ON" else "Enable Floating",
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
              }
            }

            Spacer(Modifier.height(12.dp))
            Text(
              text = "LinguaFloat Studio",
              style = MaterialTheme.typography.headlineMedium,
              color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                "Read books & PDFs, translate Arabic ↔ English in context, and train clear pronunciation & accent.",
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White.copy(alpha = 0.92f),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilledTonalButton(
                onClick = onOpenTranslation,
                modifier = Modifier.testTag("home_quick_translate"),
              ) {
                Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Translate")
              }
              OutlinedButton(
                onClick = onOpenCoach,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.testTag("home_quick_coach"),
              ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Accent Coach")
              }
            }
          }
        }
      }
    }

    // 2. Bilingual Learning Metrics Strip
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        HomeStatPill(
          label = "Books & Docs",
          value = notes.size.toString(),
          modifier = Modifier.weight(1f),
          onClick = onOpenLibrary,
        )
        HomeStatPill(
          label = "Phrases",
          value = savedPhrases.size.toString(),
          modifier = Modifier.weight(1f),
          onClick = onOpenVocabulary,
        )
        HomeStatPill(
          label = "Coach Avg",
          value = if (attempts.isEmpty()) "—" else "$avgCoachScore%",
          modifier = Modifier.weight(1f),
          onClick = onOpenProgress,
        )
      }
    }

    // 3. Resume Reading Card
    if (latestDocument != null) {
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text =
                  if (latestDocument.lastSentenceIndex > 0)
                    "RESUME AT SENTENCE #${latestDocument.lastSentenceIndex + 1}"
                  else "FEATURED READING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text = latestDocument.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${latestDocument.category} · ${latestDocument.languageTag.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(Modifier.width(10.dp))
            Button(
              onClick = { onResumeDocument(latestDocument) },
              modifier = Modifier.testTag("home_resume_button"),
            ) {
              Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text(if (latestDocument.lastSentenceIndex > 0) "Resume" else "Read")
            }
          }
        }
      }
    }

    // 4. All LinguaFloat Hubs (2-Column Feature Tiles covering all 9 screens)
    item {
      Text(
        text = "Language Tools & Hubs",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 2.dp),
      )
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          HubTile(
            title = "Books & PDFs",
            subtitle = "PDF, EPUB, TXT & OCR",
            icon = Icons.Rounded.LibraryBooks,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).testTag("hub_library"),
            onClick = onOpenLibrary,
          )
          HubTile(
            title = "Smart Reader",
            subtitle = "Aloud & sentence assist",
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f).testTag("hub_reader"),
            onClick = onOpenReader,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          HubTile(
            title = "Translate AI",
            subtitle = "${translations.size} saved translations",
            icon = Icons.Rounded.Translate,
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f).testTag("hub_translate"),
            onClick = onOpenTranslation,
          )
          HubTile(
            title = "Pronunciation",
            subtitle = "Word-by-word coach",
            icon = Icons.Rounded.Mic,
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f).testTag("hub_coach"),
            onClick = onOpenCoach,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          HubTile(
            title = "Listen & Speak",
            subtitle = "Guided + free conversation",
            icon = Icons.Rounded.Headphones,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).testTag("hub_practice"),
            onClick = onOpenPractice,
          )
          HubTile(
            title = "Vocabulary",
            subtitle = "${savedPhrases.size} saved phrases",
            icon = Icons.Rounded.Bookmarks,
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f).testTag("hub_vocabulary"),
            onClick = onOpenVocabulary,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          HubTile(
            title = "Progress",
            subtitle = "${attempts.size} speaking checks",
            icon = Icons.Rounded.Insights,
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f).testTag("hub_progress"),
            onClick = onOpenProgress,
          )
          HubTile(
            title = "Floating Setup",
            subtitle = "Overlay & capture control",
            icon = Icons.Rounded.Layers,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).testTag("hub_floating_settings"),
            onClick = onOpenFloatingSettings,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          HubTile(
            title = "Voices & Rules",
            subtitle = "US/UK/Arabic + dictionary",
            icon = Icons.Rounded.GraphicEq,
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f).testTag("hub_studio"),
            onClick = onOpenStudio,
          )
          HubTile(
            title = "Preferences",
            subtitle = "Privacy, audio & AI mode",
            icon = Icons.Rounded.Tune,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).testTag("hub_settings"),
            onClick = onOpenSettings,
          )
        }
      }
    }

    item {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
          )
          Spacer(Modifier.width(10.dp))
          Text(
            text =
              "Offline Text-to-Speech works everywhere; contextual translation, OCR fallback & multimodal pronunciation coaching use Firebase AI Logic when online.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
    }

    item { Spacer(Modifier.height(76.dp)) }
  }
}

@Composable
private fun HomeStatPill(
  label: String,
  value: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun HubTile(
  title: String,
  subtitle: String,
  icon: ImageVector,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.14f),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accentColor,
            modifier = Modifier.size(22.dp),
          )
        }
      }
      Spacer(Modifier.height(10.dp))
      Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
      Spacer(Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
