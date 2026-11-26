package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PronunciationRuleEntity
import com.example.data.ReadingBookmarkEntity
import com.example.data.ReadingHistoryEntity
import com.example.model.ReaderSessionState
import com.example.model.ReaderSettings
import com.example.model.SupportedLanguages
import com.example.model.VoiceInfoModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
  sessionState: ReaderSessionState,
  settings: ReaderSettings,
  rules: List<PronunciationRuleEntity>,
  bookmarks: List<ReadingBookmarkEntity>,
  history: List<ReadingHistoryEntity>,
  isLanguageOffline: (String) -> Boolean,
  onInstallOfflineVoices: () -> Unit,
  onSelectVoice: (VoiceInfoModel) -> Unit,
  onToggleFavoriteVoice: (String) -> Unit,
  onTestVoice: (VoiceInfoModel) -> Unit,
  onSaveCloudConfig: (Boolean, String) -> Unit,
  onSaveRule: (String, String, String) -> Unit,
  onToggleRule: (PronunciationRuleEntity) -> Unit,
  onDeleteRule: (Long) -> Unit,
  onOpenBookmark: (ReadingBookmarkEntity) -> Unit,
  onDeleteBookmark: (Long) -> Unit,
  onOpenHistory: (ReadingHistoryEntity) -> Unit,
  onDeleteHistory: (Long) -> Unit,
  onClearHistory: () -> Unit,
  onBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var selectedLangFilter by remember { mutableStateOf("ar") }
  var showAddRuleDialog by remember { mutableStateOf(false) }
  var showCloudDialog by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxSize()) {
    if (onBack != null) {
      TopAppBar(
        title = { Text("Voices, Dictionary & Bookmarks") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
    PrimaryTabRow(selectedTabIndex = selectedTab) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Voices") },
        modifier = Modifier.testTag("studio_tab_voices"),
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Dictionary") },
        modifier = Modifier.testTag("studio_tab_dictionary"),
      )
      Tab(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        text = { Text("Saved") },
        modifier = Modifier.testTag("studio_tab_saved"),
      )
    }

    when (selectedTab) {
      0 -> {
        val matchingVoices =
          remember(sessionState.availableVoices, selectedLangFilter) {
            val prefix = selectedLangFilter.substringBefore('-')
            sessionState.availableVoices.filter {
              it.languageCode.equals(prefix, ignoreCase = true) ||
                it.localeTag.equals(selectedLangFilter, ignoreCase = true) ||
                it.providerId == "cloud_backend"
            }
          }

        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                  "Multilingual Speech Studio",
                  style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  "Primary engine runs offline using Android TextToSpeech enhanced voices. Install additional offline voice packs or configure an optional private cloud backend.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilledTonalButton(onClick = onInstallOfflineVoices) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Offline Voice Packs")
                  }
                  OutlinedButton(onClick = { showCloudDialog = true }) {
                    Icon(Icons.Rounded.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (settings.cloudTtsEnabled) "Cloud: ON" else "Cloud Adapter")
                  }
                }
              }
            }
          }

          item {
            Text("Supported Languages (13+)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              SupportedLanguages.ALL.forEach { lang ->
                val offlineOk = isLanguageOffline(lang.tag)
                val isSelected = selectedLangFilter == lang.tag
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                  tonalElevation = 1.dp,
                  modifier =
                    Modifier.clickable { selectedLangFilter = lang.tag }
                      .testTag("lang_chip_${lang.tag}"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "${lang.displayName} (${lang.nativeName})",
                      style = MaterialTheme.typography.labelLarge,
                      color =
                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    if (offlineOk) {
                      Spacer(Modifier.width(5.dp))
                      Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Offline available",
                        tint =
                          if (isSelected) MaterialTheme.colorScheme.onPrimary
                          else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                      )
                    }
                  }
                }
              }
            }
          }

          item {
            Text(
              text =
                "Installed Voices for ${SupportedLanguages.findByTag(selectedLangFilter).displayName}",
              style = MaterialTheme.typography.titleMedium,
            )
          }

          if (matchingVoices.isEmpty()) {
            item {
              Card(
                shape = RoundedCornerShape(16.dp),
                colors =
                  CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                  ),
              ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                  Text(
                    "No dedicated system voices detected for this language yet.",
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Spacer(Modifier.height(4.dp))
                  Text(
                    "Tap 'Offline Voice Packs' above to download high-quality local voices from Android Speech Services.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            items(matchingVoices, key = { it.id }) { voice ->
              val preferredId =
                settings.preferredVoicesByLang[voice.localeTag]
                  ?: settings.preferredVoicesByLang[voice.languageCode]
              val isPreferred = preferredId == voice.id
              val isFav = settings.favoriteVoiceIds.contains(voice.id)

              Card(
                shape = RoundedCornerShape(16.dp),
                colors =
                  CardDefaults.cardColors(
                    containerColor =
                      if (isPreferred) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                      else MaterialTheme.colorScheme.surface
                  ),
                modifier = Modifier.fillMaxWidth().clickable { onSelectVoice(voice) },
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = voice.displayName,
                      style = MaterialTheme.typography.titleSmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                      text =
                        "${voice.qualityTier.label} · ${if (voice.isOfflineAvailable) "Offline Ready" else "Requires Network"}",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  IconButton(
                    onClick = { onToggleFavoriteVoice(voice.id) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(
                      imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                      contentDescription = "Favorite voice",
                      tint =
                        if (isFav) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  IconButton(
                    onClick = { onTestVoice(voice) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Test voice")
                  }
                }
              }
            }
          }
        }
      }

      1 -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Medical Pronunciation Dictionary", style = MaterialTheme.typography.titleMedium)
                Text(
                  "Customize how clinical abbreviations and terms are spoken aloud.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = { showAddRuleDialog = true },
                modifier = Modifier.testTag("add_pronunciation_rule_button"),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
              }
            }
          }

          items(rules, key = { it.id }) { rule ->
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${rule.term}  →  ${rule.spokenReplacement}",
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Text(
                    text = "Language: ${rule.languageTag.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(checked = rule.isEnabled, onCheckedChange = { onToggleRule(rule) })
                IconButton(
                  onClick = { onDeleteRule(rule.id) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete rule")
                }
              }
            }
          }
        }
      }

      else -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(6.dp))
              Text("Bookmarks (${bookmarks.size})", style = MaterialTheme.typography.titleMedium)
            }
          }

          if (bookmarks.isEmpty()) {
            item {
              Text(
                "No bookmarks yet. Tap the bookmark icon inside Reader or Mini-Player to save your spot.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            items(bookmarks, key = { it.id }) { bm ->
              Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpenBookmark(bm) },
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(bm.documentTitle, style = MaterialTheme.typography.titleSmall)
                    Text(
                      "Sentence #${bm.sentenceIndex + 1} · ${bm.sentencePreview}",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  IconButton(
                    onClick = { onDeleteBookmark(bm.id) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete bookmark")
                  }
                }
              }
            }
          }

          item {
            Spacer(Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Reading History (${history.size})", style = MaterialTheme.typography.titleMedium)
              }
              if (history.isNotEmpty()) {
                TextButton(onClick = onClearHistory) { Text("Clear All") }
              }
            }
          }

          if (history.isEmpty()) {
            item {
              Text(
                if (settings.readingHistoryEnabled) "No reading history recorded yet."
                else "Reading history is turned OFF in Privacy Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            items(history, key = { it.id }) { entry ->
              Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpenHistory(entry) },
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                      "${entry.sourceLabel} · ${entry.languageTag.uppercase()} · ${entry.sentenceCount} sentences",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  IconButton(
                    onClick = { onDeleteHistory(entry.id) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete history item")
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showAddRuleDialog) {
    AddPronunciationRuleDialog(
      onDismiss = { showAddRuleDialog = false },
      onSave = { term, replacement, lang ->
        onSaveRule(term, replacement, lang)
        showAddRuleDialog = false
      },
    )
  }

  if (showCloudDialog) {
    CloudBackendAdapterDialog(
      initialEnabled = settings.cloudTtsEnabled,
      initialUrl = settings.cloudTtsEndpointUrl,
      onDismiss = { showCloudDialog = false },
      onSave = { enabled, url ->
        onSaveCloudConfig(enabled, url)
        showCloudDialog = false
      },
    )
  }
}

@Composable
private fun AddPronunciationRuleDialog(
  onDismiss: () -> Unit,
  onSave: (String, String, String) -> Unit,
) {
  var term by remember { mutableStateOf("") }
  var replacement by remember { mutableStateOf("") }
  var lang by remember { mutableStateOf("all") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Custom Pronunciation Rule") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = term,
          onValueChange = { term = it },
          label = { Text("Medical Term / Abbreviation (e.g. q.h.s.)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = replacement,
          onValueChange = { replacement = it },
          label = { Text("Spoken Pronunciation (e.g. every night at bedtime)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = lang,
          onValueChange = { lang = it },
          label = { Text("Language code (all, en, ar, fr...)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(term, replacement, lang) },
        enabled = term.isNotBlank() && replacement.isNotBlank(),
      ) {
        Text("Save Rule")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun CloudBackendAdapterDialog(
  initialEnabled: Boolean,
  initialUrl: String,
  onDismiss: () -> Unit,
  onSave: (Boolean, String) -> Unit,
) {
  var enabled by remember { mutableStateOf(initialEnabled) }
  var url by remember { mutableStateOf(initialUrl) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Optional Cloud TTS Adapter") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "By default, LinguaFloat processes speech locally on your device. Only enable this if you host your own authenticated HTTPS TTS proxy. No API keys are ever stored in this app.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Enable Cloud Adapter", style = MaterialTheme.typography.titleSmall)
          Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text("HTTPS Backend Synthesis URL") },
          placeholder = { Text("https://tts-proxy.yourdomain.com/speak") },
          singleLine = true,
          enabled = enabled,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(onClick = { onSave(enabled, url) }) { Text("Apply") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
