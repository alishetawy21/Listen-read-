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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.capture.SystemIntegrationHelper
import com.example.coach.CoachCaptureState
import com.example.ui.CoachUiState
import com.example.ui.theme.AmberNoticeContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer

private data class AccentDrill(val label: String, val sentence: String, val languageTag: String)

private val STARTER_DRILLS =
  listOf(
    AccentDrill("EN /p/ vs /b/", "Please bring both heavy paper bags before five o'clock.", "en-US"),
    AccentDrill("EN /v/ & /θ/", "The very vivid voice verified thirty-three thoughtful theories.", "en-US"),
    AccentDrill("EN-GB Clinical", "Normal haemoglobin levels with no peripheral oedema were noted.", "en-GB"),
    AccentDrill("AR Emphatics", "عَلِمَ العَالِمُ حَقِيقَةَ القَرَارِ الصَّائِبِ فِي ضَوْءِ الخِبْرَةِ العَمِيقَةِ.", "ar"),
    AccentDrill("AR Articulation", "حَافِظْ عَلَى هُدُوءِ الصَّوْتِ وَوُضُوحِ المَخْرَجِ عِنْدَ القِرَاءَةِ.", "ar"),
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(
  targetSentence: String,
  targetLanguage: String,
  captureState: CoachCaptureState,
  coachUiState: CoachUiState,
  onTargetChange: (String) -> Unit,
  onSelectTargetDrill: (String, String) -> Unit,
  onSelectLanguage: (String) -> Unit,
  onListenReference: (String, String, Float) -> Unit,
  onStartAudioRecord: () -> Unit,
  onStopAudioRecordAndAnalyze: () -> Unit,
  onStartLiveAsr: () -> Unit,
  onStopLiveAsr: () -> Unit,
  onPlayUserRecording: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var hasMicPermission by remember {
    mutableStateOf(SystemIntegrationHelper.hasMicrophonePermission(context))
  }
  var useLiveAsrMode by remember { mutableStateOf(false) }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
      if (granted) {
        if (useLiveAsrMode) onStartLiveAsr() else onStartAudioRecord()
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 1. Practice Sentence & Accent Drills
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("AI Pronunciation & Accent Coach", style = MaterialTheme.typography.titleLarge)
          Text(
            "Select a guided Arabic/English drill or enter any sentence, listen to the reference, and record your voice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            STARTER_DRILLS.forEach { drill ->
              FilterChip(
                selected = targetSentence == drill.sentence,
                onClick = { onSelectTargetDrill(drill.sentence, drill.languageTag) },
                label = { Text(drill.label) },
              )
            }
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            listOf("en-US" to "English (US)", "en-GB" to "English (UK)", "ar" to "Arabic (MSA)").forEach { (tag, label) ->
              FilterChip(
                selected = targetLanguage == tag,
                onClick = { onSelectLanguage(tag) },
                label = { Text(label) },
              )
            }
          }

          OutlinedTextField(
            value = targetSentence,
            onValueChange = onTargetChange,
            label = { Text("Sentence to practice") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().testTag("coach_target_input"),
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
              onClick = { onListenReference(targetSentence, targetLanguage, 1.0f) },
              enabled = targetSentence.isNotBlank(),
            ) {
              Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(5.dp))
              Text("Reference 1.0x")
            }
            OutlinedButton(
              onClick = { onListenReference(targetSentence, targetLanguage, 0.75f) },
              enabled = targetSentence.isNotBlank(),
            ) {
              Icon(Icons.Rounded.SlowMotionVideo, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(5.dp))
              Text("Slow 0.75x")
            }
          }
        }
      }
    }

    // 2. Recording Mode & Microphone Controls
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Capture Mode", style = MaterialTheme.typography.titleMedium)

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = !useLiveAsrMode,
              onClick = { useLiveAsrMode = false },
              label = { Text("Audio Recording (Gemini)") },
            )
            FilterChip(
              selected = useLiveAsrMode,
              onClick = { useLiveAsrMode = true },
              label = { Text("Live Speech Check (ASR)") },
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!captureState.isRecording) {
              Button(
                onClick = {
                  if (!hasMicPermission) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  } else if (useLiveAsrMode) {
                    onStartLiveAsr()
                  } else {
                    onStartAudioRecord()
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.weight(1f).testTag("coach_record_button"),
              ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (useLiveAsrMode) "Speak Live Check" else "Record Pronunciation")
              }
            } else {
              Button(
                onClick = {
                  if (useLiveAsrMode) onStopLiveAsr() else onStopAudioRecordAndAnalyze()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f).testTag("coach_stop_button"),
              ) {
                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop & Evaluate")
              }
            }

            if (captureState.lastAudioFilePath != null && !captureState.isRecording) {
              FilledTonalButton(
                onClick = { onPlayUserRecording(captureState.lastAudioFilePath) },
              ) {
                Icon(
                  if (captureState.isPlayingUserAudio) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                  contentDescription = "Play my recording",
                  modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("My Audio")
              }
            }
          }

          if (captureState.liveTranscript.isNotBlank()) {
            Text(
              text = "Recognized speech: \"${captureState.liveTranscript}\"",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }

          if (!captureState.errorMessage.isNullOrBlank()) {
            Text(
              text = captureState.errorMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }

    // 3. Evaluation Results (Verified Metrics vs Estimated AI Coaching)
    when (coachUiState) {
      is CoachUiState.Idle -> {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(10.dp))
              Text(
                "Tap Record Pronunciation to capture your voice for Gemini multimodal accent coaching, or use Live Speech Check for instant ASR word matching.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
      is CoachUiState.Analyzing -> {
        item {
          Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
              Spacer(Modifier.width(12.dp))
              Text("Evaluating pronunciation, rhythm, and accent...", style = MaterialTheme.typography.bodyLarge)
            }
          }
        }
      }
      is CoachUiState.Error -> {
        item {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = coachUiState.message,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      }
      is CoachUiState.Evaluated -> {
        val eval = coachUiState.evaluation
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("coach_evaluation_card"),
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text("Estimated Coaching Score", style = MaterialTheme.typography.labelMedium)
                  Text(
                    text = "${eval.estimatedOverallScore}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      Icons.Rounded.Verified,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                      text = if (eval.audioAnalyzed) "Audio Analyzed" else "ASR Verified",
                      style = MaterialTheme.typography.labelSmall,
                    )
                  }
                }
              }

              Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = eval.verifiedMetricsSummary,
                  style = MaterialTheme.typography.labelMedium,
                  modifier = Modifier.padding(10.dp),
                )
              }

              if (eval.wordAssessments.isNotEmpty()) {
                Text("Word-by-Word Coaching", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  eval.wordAssessments.forEach { w ->
                    val bgColor =
                      when (w.status) {
                        "accurate" -> SuccessGreenContainer
                        "needs_polish" -> AmberNoticeContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                      }
                    val txtColor =
                      when (w.status) {
                        "accurate" -> SuccessGreen
                        "needs_polish" -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onErrorContainer
                      }
                    Surface(shape = RoundedCornerShape(10.dp), color = bgColor) {
                      Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(w.word, style = MaterialTheme.typography.labelLarge, color = txtColor)
                        if (w.phoneticTip.isNotBlank() && w.status != "accurate") {
                          Text(w.phoneticTip, style = MaterialTheme.typography.labelSmall, color = txtColor)
                        }
                      }
                    }
                  }
                }
              }

              CoachingSection("Clarity & Articulation (AI Estimate)", eval.clarityAndArticulationNote)
              CoachingSection("Rhythm, Stress & Pace", eval.rhythmAndStressNote)
              CoachingSection("Arabic ↔ English Accent Tip", eval.l1InterferenceTips)
              CoachingSection("Immediate Practice Drill", eval.practiceDrillTip)
            }
          }
        }
      }
    }

    item { Spacer(Modifier.height(76.dp)) }
  }
}

@Composable
private fun CoachingSection(title: String, body: String) {
  if (body.isBlank()) return
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Text(body, style = MaterialTheme.typography.bodyMedium)
  }
}
