package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PronunciationAttemptEntity
import com.example.data.SavedPhraseEntity
import com.example.data.TranslationHistoryEntity
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
  attempts: List<PronunciationAttemptEntity>,
  savedPhrases: List<SavedPhraseEntity>,
  translations: List<TranslationHistoryEntity>,
  onBack: () -> Unit,
  onPracticeSentence: (String, String) -> Unit,
  onPlayAttemptAudio: (String?) -> Unit,
  onClearVoiceData: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val avgScore = if (attempts.isNotEmpty()) attempts.map { it.overallScore }.average().roundToInt() else 0
  val avgWpm = if (attempts.isNotEmpty()) attempts.map { it.measuredWpm }.average().roundToInt() else 0
  val masteredCount = savedPhrases.count { it.masteryLevel >= 3 }

  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Learning Progress Dashboard") },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      },
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          ProgressMetricCard("Coach Avg", if (attempts.isEmpty()) "—" else "$avgScore%", Modifier.weight(1f))
          ProgressMetricCard("Pace Avg", if (attempts.isEmpty()) "—" else "$avgWpm WPM", Modifier.weight(1f))
          ProgressMetricCard("Mastered", "$masteredCount/${savedPhrases.size}", Modifier.weight(1f))
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pronunciation Score Trend", style = MaterialTheme.typography.titleMedium)
            if (attempts.size < 2) {
              Text(
                "Complete at least 2 pronunciation checks to visualize your speaking score trend.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              val chartPoints = attempts.take(12).reversed().map { it.overallScore.toFloat() }
              val lineColor = MaterialTheme.colorScheme.secondary
              val dotColor = MaterialTheme.colorScheme.primary
              Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                val pad = 16f
                val w = size.width - pad * 2
                val h = size.height - pad * 2
                val step = w / (chartPoints.size - 1).coerceAtLeast(1)
                val path = Path()
                chartPoints.forEachIndexed { idx, score ->
                  val x = pad + idx * step
                  val y = pad + h * (1f - (score / 100f).coerceIn(0f, 1f))
                  if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                  drawCircle(color = dotColor, radius = 7f, center = Offset(x, y))
                }
                drawPath(path = path, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
              }
            }
            Text(
              text = "Total Translations Logged: ${translations.size} · Speaking Checks: ${attempts.size}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (attempts.isNotEmpty()) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Recent Speaking Attempts", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onClearVoiceData) {
              Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(5.dp))
              Text("Clear Voice Data")
            }
          }
        }

        items(attempts, key = { it.id }) { attempt ->
          Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                  Text(
                    text = "Score ${attempt.overallScore}% · ${attempt.measuredWpm} WPM",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
                Text(
                  text = if (attempt.audioAnalyzed) "Audio Analyzed" else "ASR ${attempt.verifiedAccuracyPct}%",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }

              Text(
                text = attempt.targetSentence,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              if (attempt.coachingSummary.isNotBlank()) {
                Text(
                  text = attempt.coachingSummary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (attempt.recordingFilePath != null) {
                  IconButton(
                    onClick = { onPlayAttemptAudio(attempt.recordingFilePath) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play saved recording")
                  }
                }
                IconButton(
                  onClick = { onPracticeSentence(attempt.targetSentence, attempt.targetLanguage) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Rounded.Mic, contentDescription = "Practice again")
                }
              }
            }
          }
        }
      }

      item { Spacer(Modifier.height(76.dp)) }
    }
  }
}

@Composable
private fun ProgressMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
