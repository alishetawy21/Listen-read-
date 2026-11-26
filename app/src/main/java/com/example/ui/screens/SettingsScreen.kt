package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.PLAYBACK_SPEED_PRESETS
import com.example.model.ReaderSettings
import com.example.model.SupportedLanguages
import com.example.model.VoiceGender
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  settings: ReaderSettings,
  onOpenFloatingSettings: () -> Unit,
  onOpenVoicesStudio: () -> Unit,
  onSetDefaultLanguage: (String) -> Unit,
  onSetEnglishAccent: (String) -> Unit,
  onSetGenderFilter: (VoiceGender) -> Unit,
  onSetAutoDetect: (Boolean) -> Unit,
  onSetMixedArabicEnglish: (Boolean) -> Unit,
  onSetSpeed: (Float) -> Unit,
  onSetPitch: (Float) -> Unit,
  onSetVolume: (Float) -> Unit,
  onToggleSkipHeadings: () -> Unit,
  onSetOnlineAiEnabled: (Boolean) -> Unit,
  onSetReadingHistoryEnabled: (Boolean) -> Unit,
  onSetSaveVoiceRecordingsConsent: (Boolean) -> Unit,
  onClearHistory: () -> Unit,
  onClearTranslations: () -> Unit,
  onClearVoiceData: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedGender =
    runCatching { VoiceGender.valueOf(settings.preferredGender) }.getOrDefault(VoiceGender.ANY)

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Floating Assistant & Voices Studio Quick Launchers
    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("System & Voice Configuration", style = MaterialTheme.typography.titleMedium)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = onOpenFloatingSettings,
              modifier = Modifier.weight(1f).testTag("settings_open_floating"),
            ) {
              Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Floating Setup")
            }
            OutlinedButton(
              onClick = onOpenVoicesStudio,
              modifier = Modifier.weight(1f).testTag("settings_open_studio"),
            ) {
              Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Voices & Rules")
            }
          }
        }
      }
    }

    // 2. Language, Accent & Voice Preferences
    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Language & Pronunciation Preferences", style = MaterialTheme.typography.titleMedium)
          }

          Text("Preferred English Accent", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = settings.englishAccentTag == "en-US",
              onClick = { onSetEnglishAccent("en-US") },
              label = { Text("American (en-US)") },
            )
            FilterChip(
              selected = settings.englishAccentTag == "en-GB",
              onClick = { onSetEnglishAccent("en-GB") },
              label = { Text("British (en-GB)") },
            )
          }

          Text("Voice Gender Preference", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceGender.entries.forEach { gender ->
              FilterChip(
                selected = selectedGender == gender,
                onClick = { onSetGenderFilter(gender) },
                label = { Text(gender.label) },
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Auto-Detect Language", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Automatically switches between Arabic, English & supported languages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(checked = settings.autoDetectLanguage, onCheckedChange = onSetAutoDetect)
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Mixed Arabic-English Switching", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Switches voice smoothly mid-sentence for bilingual texts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = settings.mixArabicEnglishSwitching,
              onCheckedChange = onSetMixedArabicEnglish,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Skip Headings & References", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Skips section headers and citation numbers when reading books",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = settings.skipHeadingsAndReferences,
              onCheckedChange = { onToggleSkipHeadings() },
            )
          }

          Text("Default Fallback Language", style = MaterialTheme.typography.labelLarge)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            FilterChip(
              selected = settings.defaultLanguageTag == "auto",
              onClick = { onSetDefaultLanguage("auto") },
              label = { Text("Auto") },
            )
            SupportedLanguages.ALL.take(6).forEach { lang ->
              FilterChip(
                selected = settings.defaultLanguageTag == lang.tag,
                onClick = { onSetDefaultLanguage(lang.tag) },
                label = { Text(lang.displayName) },
              )
            }
          }

          Text("Speech Speed (${settings.speechSpeed}x)", style = MaterialTheme.typography.labelLarge)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            PLAYBACK_SPEED_PRESETS.forEach { preset ->
              FilterChip(
                selected = kotlin.math.abs(settings.speechSpeed - preset) < 0.01f,
                onClick = { onSetSpeed(preset) },
                label = { Text("${preset}x") },
              )
            }
          }

          Column {
            Text(
              "Voice Pitch: ${String.format("%.2f", settings.speechPitch)}",
              style = MaterialTheme.typography.labelLarge,
            )
            Slider(
              value = settings.speechPitch,
              onValueChange = onSetPitch,
              valueRange = 0.65f..1.45f,
            )
          }

          Column {
            Text(
              "Speech Volume: ${(settings.speechVolume * 100).roundToInt()}%",
              style = MaterialTheme.typography.labelLarge,
            )
            Slider(
              value = settings.speechVolume,
              onValueChange = onSetVolume,
              valueRange = 0.1f..1.0f,
            )
          }
        }
      }
    }

    // 3. Privacy, Voice Data & Offline vs Online AI Transparency
    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Privacy & AI Transparency", style = MaterialTheme.typography.titleMedium)
          }

          Text(
            "• Offline Features: Text-to-Speech reading, PDF/EPUB/TXT parsing, Latin OCR, pronunciation rules, and on-device SpeechRecognizer word match.\n" +
              "• Online AI (Firebase AI Logic / Gemini): Contextual translation, idiom/passage explanation, Arabic Vision OCR fallback, and multimodal pronunciation coaching.\n" +
              "• Security: Password fields and secure windows are strictly ignored; audio recordings are deleted right after analysis unless you opt in below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Enable Online Gemini AI Coaching", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Disable for strictly offline TTS + on-device ASR mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(checked = settings.onlineAiEnabled, onCheckedChange = onSetOnlineAiEnabled)
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Save Voice Recordings Locally", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Opt-in to keep audio clips for replay in Progress history",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = settings.saveVoiceRecordingsConsent,
              onCheckedChange = onSetSaveVoiceRecordingsConsent,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Save Local Reading History", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Disable for ephemeral reading sessions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = settings.readingHistoryEnabled,
              onCheckedChange = onSetReadingHistoryEnabled,
            )
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(onClick = onClearHistory) { Text("Clear Reading History") }
            OutlinedButton(onClick = onClearTranslations) { Text("Clear Translations") }
            OutlinedButton(onClick = onClearVoiceData) { Text("Delete Voice Data") }
          }
        }
      }
    }

    item { Spacer(Modifier.height(80.dp)) }
  }
}
