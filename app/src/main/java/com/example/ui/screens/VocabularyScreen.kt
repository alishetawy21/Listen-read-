package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.SavedPhraseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
  phrases: List<SavedPhraseEntity>,
  onBack: () -> Unit,
  onSavePhrase: (String, String, String, String, String) -> Unit,
  onCycleMastery: (SavedPhraseEntity) -> Unit,
  onDeletePhrase: (Long) -> Unit,
  onListenPhrase: (String, String, Float) -> Unit,
  onPracticePhrase: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showAddDialog by remember { mutableStateOf(false) }
  var filter by remember { mutableStateOf("All") }

  val filtered =
    remember(phrases, filter) {
      when (filter) {
        "Arabic" -> phrases.filter { it.languageTag.startsWith("ar", true) }
        "English" -> phrases.filter { it.languageTag.startsWith("en", true) }
        "Mastered" -> phrases.filter { it.masteryLevel >= 3 }
        else -> phrases
      }
    }

  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Vocabulary & Saved Phrases") },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      },
      actions = {
        IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("vocab_add_button")) {
          Icon(Icons.Rounded.Add, contentDescription = "Add phrase")
        }
      },
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("All", "Arabic", "English", "Mastered").forEach { f ->
            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
          }
        }
      }

      if (filtered.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              "No saved phrases here yet. Save words from Reader or Translator, or tap + to add one.",
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(18.dp),
            )
          }
        }
      } else {
        items(filtered, key = { it.id }) { item ->
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                  Text(
                    text = item.languageTag.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
                TextButton(onClick = { onCycleMastery(item) }) {
                  Icon(
                    Icons.Rounded.Star,
                    contentDescription = "Mastery level",
                    tint = if (item.masteryLevel >= 3) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text("Mastery ${item.masteryLevel}/3")
                }
              }

              Text(item.phrase, style = MaterialTheme.typography.titleMedium)
              Text(item.translation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
              if (item.pronunciationGuide.isNotBlank()) {
                Text(item.pronunciationGuide, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              if (item.notes.isNotBlank()) {
                Text(item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(
                  onClick = { onListenPhrase(item.phrase, item.languageTag, 1.0f) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Rounded.VolumeUp, contentDescription = "Listen")
                }
                IconButton(
                  onClick = { onListenPhrase(item.phrase, item.languageTag, 0.75f) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Rounded.SlowMotionVideo, contentDescription = "Listen slowly")
                }
                IconButton(
                  onClick = { onPracticePhrase(item.phrase, item.languageTag) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Rounded.Mic, contentDescription = "Practice pronunciation")
                }
                IconButton(
                  onClick = { onDeletePhrase(item.id) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete phrase")
                }
              }
            }
          }
        }
      }

      item { Spacer(Modifier.height(76.dp)) }
    }
  }

  if (showAddDialog) {
    var phrase by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("en-US") }
    var guide by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Add Vocabulary Phrase") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = phrase, onValueChange = { phrase = it }, label = { Text("Phrase or Word") })
          OutlinedTextField(value = translation, onValueChange = { translation = it }, label = { Text("Translation") })
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("en-US", "en-GB", "ar").forEach { tag ->
              FilterChip(selected = lang == tag, onClick = { lang = tag }, label = { Text(tag) })
            }
          }
          OutlinedTextField(value = guide, onValueChange = { guide = it }, label = { Text("Pronunciation / IPA (optional)") })
          OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Context notes (optional)") })
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSavePhrase(phrase, translation, lang, guide, notes)
            showAddDialog = false
          },
          enabled = phrase.isNotBlank() && translation.isNotBlank(),
        ) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } },
    )
  }
}
