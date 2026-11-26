package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.capture.SystemIntegrationHelper
import com.example.coach.CoachCaptureState
import com.example.ui.FreeSpeakUiState

private data class ListenDrill(
  val title: String,
  val original: String,
  val translation: String,
  val originalLang: String,
  val translationLang: String,
)

private val LISTEN_DRILLS =
  listOf(
    ListenDrill(
      title = "Clinical Follow-up (EN → AR)",
      original = "Take this medication twice daily after meals and monitor your blood pressure each morning.",
      translation = "تَنَاوَلْ هَذَا الدَّوَاءَ مَرَّتَيْنِ يَوْمِيًّا بَعْدَ الوَجَبَاتِ وَرَاقِبْ ضَغْطَ الدَّمِ كُلَّ صَبَاحٍ.",
      originalLang = "en-US",
      translationLang = "ar",
    ),
    ListenDrill(
      title = "Literary Arabic (AR → EN)",
      original = "مَنْ جَدَّ وَجَدَ، وَمَنْ سَارَ عَلَى الدَّرْبِ وَصَلَ إِلَى مَا يَطْمَحُ إِلَيْهِ.",
      translation = "He who strives earnestly finds success, and whoever stays the course reaches their aspiration.",
      originalLang = "ar",
      translationLang = "en-GB",
    ),
    ListenDrill(
      title = "Natural Idiom (EN-US)",
      original = "Let's touch base tomorrow morning so we are on the same page before the presentation.",
      translation = "دَعْنَا نَتَوَاصَلُ غَدًا صَبَاحًا لِنَكُونَ عَلَى وَفَاقٍ قَبْلَ العَرْضِ التَّقْدِيمِيِّ.",
      originalLang = "en-US",
      translationLang = "ar",
    ),
  )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(
  captureState: CoachCaptureState,
  freeSpeakTranscript: String,
  freeSpeakUiState: FreeSpeakUiState,
  onBack: () -> Unit,
  onListenText: (String, String, Float) -> Unit,
  onTranscriptChange: (String) -> Unit,
  onStartSpeakAsr: (String) -> Unit,
  onStartSpeakAudio: () -> Unit,
  onStopSpeakAudio: (String) -> Unit,
  onSubmitFreeSpeak: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var selectedTab by remember { mutableIntStateOf(0) }
  var selectedLang by remember { mutableStateOf("en-US") }
  var revealTranslation by remember { mutableStateOf(true) }
  var listenSpeed by remember { mutableStateOf(1.0f) }

  var hasMic by remember {
    mutableStateOf(SystemIntegrationHelper.hasMicrophonePermission(context))
  }
  val micLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMic = granted
      if (granted) onStartSpeakAudio()
    }

  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Listening & Speaking Practice") },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      },
    )

    PrimaryTabRow(selectedTabIndex = selectedTab) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Listen Mode") },
        modifier = Modifier.testTag("practice_tab_listen"),
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Speak Mode (AI)") },
        modifier = Modifier.testTag("practice_tab_speak"),
      )
    }

    if (selectedTab == 0) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf(0.75f, 1.0f, 1.25f).forEach { spd ->
                FilterChip(
                  selected = listenSpeed == spd,
                  onClick = { listenSpeed = spd },
                  label = { Text("${spd}x") },
                )
              }
            }
            FilterChip(
              selected = revealTranslation,
              onClick = { revealTranslation = !revealTranslation },
              label = { Text(if (revealTranslation) "Text Shown" else "Audio Only") },
            )
          }
        }

        LISTEN_DRILLS.forEach { drill ->
          item {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(drill.title, style = MaterialTheme.typography.titleMedium)
                if (revealTranslation) {
                  Text(drill.original, style = MaterialTheme.typography.bodyLarge)
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = drill.translation,
                      style = MaterialTheme.typography.bodyMedium,
                      modifier = Modifier.padding(10.dp),
                    )
                  }
                } else {
                  Text(
                    "Listen first, then tap 'Text Shown' above to check your comprehension.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(onClick = { onListenText(drill.original, drill.originalLang, listenSpeed) }) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Original")
                  }
                  FilledTonalButton(
                    onClick = { onListenText(drill.translation, drill.translationLang, listenSpeed) }
                  ) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Translated")
                  }
                }
              }
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Free Speaking Partner", style = MaterialTheme.typography.titleMedium)
              Text(
                "Speak freely in Arabic or English — LinguaFloat replies naturally, corrects grammar, and offers accent tips.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en-US" to "English (US)", "en-GB" to "English (UK)", "ar" to "Arabic").forEach { (tag, label) ->
                  FilterChip(
                    selected = selectedLang == tag,
                    onClick = { selectedLang = tag },
                    label = { Text(label) },
                  )
                }
              }

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!captureState.isRecording) {
                  Button(
                    onClick = {
                      if (!hasMic) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                      else onStartSpeakAudio()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Record Voice")
                  }
                  OutlinedButton(
                    onClick = {
                      if (!hasMic) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                      else onStartSpeakAsr(selectedLang)
                    }
                  ) {
                    Text("Live ASR")
                  }
                } else {
                  Button(
                    onClick = { onStopSpeakAudio(selectedLang) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Stop & Send")
                  }
                }
              }

              OutlinedTextField(
                value = freeSpeakTranscript,
                onValueChange = onTranscriptChange,
                label = { Text("Or type / edit what you want to say") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
              )

              FilledTonalButton(
                onClick = { onSubmitFreeSpeak(selectedLang) },
                enabled = freeSpeakTranscript.isNotBlank() && freeSpeakUiState !is FreeSpeakUiState.Analyzing,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Get Conversation & Accent Feedback")
              }
            }
          }
        }

        when (freeSpeakUiState) {
          is FreeSpeakUiState.Idle -> Unit
          is FreeSpeakUiState.Analyzing -> {
            item {
              Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(10.dp))
                Text("Preparing conversational response & coaching...")
              }
            }
          }
          is FreeSpeakUiState.Error -> {
            item {
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                  text = freeSpeakUiState.message,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(14.dp),
                )
              }
            }
          }
          is FreeSpeakUiState.Replied -> {
            val reply = freeSpeakUiState.result
            item {
              Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("Partner Reply", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                  Text(reply.conversationalReply, style = MaterialTheme.typography.titleMedium)
                  Text(reply.replyTranslation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  FilledTonalButton(onClick = { onListenText(reply.conversationalReply, selectedLang, 1.0f) }) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Listen to Reply")
                  }
                  if (reply.improvedNaturalVersion.isNotBlank()) {
                    Text("More Natural Phrasing", style = MaterialTheme.typography.titleSmall)
                    Text(reply.improvedNaturalVersion, style = MaterialTheme.typography.bodyMedium)
                  }
                  if (reply.grammarAndFluencyFeedback.isNotBlank()) {
                    Text("Grammar & Fluency Note", style = MaterialTheme.typography.titleSmall)
                    Text(reply.grammarAndFluencyFeedback, style = MaterialTheme.typography.bodyMedium)
                  }
                  if (reply.pronunciationAndAccentTips.isNotBlank()) {
                    Text("Pronunciation Tip", style = MaterialTheme.typography.titleSmall)
                    Text(reply.pronunciationAndAccentTips, style = MaterialTheme.typography.bodyMedium)
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
