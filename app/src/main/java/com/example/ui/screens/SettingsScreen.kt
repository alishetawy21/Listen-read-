package com.example.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.capture.SystemIntegrationHelper
import com.example.model.PLAYBACK_SPEED_PRESETS
import com.example.model.ReaderSettings
import com.example.model.SupportedLanguages
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  settings: ReaderSettings,
  onToggleFloatingShortcut: (Boolean) -> Unit,
  onUpdateButtonSize: (Float) -> Unit,
  onUpdateButtonAlpha: (Float) -> Unit,
  onResetButtonPosition: () -> Unit,
  onSetDoubleTapToggle: (Boolean) -> Unit,
  onSetDefaultLanguage: (String) -> Unit,
  onSetAutoDetect: (Boolean) -> Unit,
  onSetMixedArabicEnglish: (Boolean) -> Unit,
  onSetSpeed: (Float) -> Unit,
  onSetPitch: (Float) -> Unit,
  onToggleSkipHeadings: () -> Unit,
  onSetReadingHistoryEnabled: (Boolean) -> Unit,
  onClearHistory: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var canOverlay by remember { mutableStateOf(SystemIntegrationHelper.canDrawOverlays(context)) }
  var accessibilityEnabled by remember {
    mutableStateOf(SystemIntegrationHelper.isAccessibilityServiceEnabled(context))
  }
  var notificationsAllowed by remember {
    mutableStateOf(SystemIntegrationHelper.hasNotificationPermission(context))
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        canOverlay = SystemIntegrationHelper.canDrawOverlays(context)
        accessibilityEnabled = SystemIntegrationHelper.isAccessibilityServiceEnabled(context)
        notificationsAllowed = SystemIntegrationHelper.hasNotificationPermission(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      notificationsAllowed = granted
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Section 1: Universal Floating Shortcut & System Permissions
    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("Universal Floating Shortcut", style = MaterialTheme.typography.titleMedium)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Floating Reader Button", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Tap to read • Double-tap to play/pause • Long-press for settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = settings.floatingShortcutEnabled && canOverlay,
              onCheckedChange = onToggleFloatingShortcut,
              modifier = Modifier.testTag("settings_floating_switch"),
            )
          }

          PermissionStatusRow(
            icon = Icons.Rounded.Layers,
            title = "Display Over Other Apps",
            subtitle =
              if (canOverlay) "Overlay permission granted"
              else "Required to show floating shortcut across Chrome & PDF apps",
            granted = canOverlay,
            actionLabel = if (canOverlay) "Settings" else "Grant",
            onAction = { SystemIntegrationHelper.openOverlayPermissionSettings(context) },
          )

          PermissionStatusRow(
            icon = Icons.Rounded.AccessibilityNew,
            title = "ClinNote Accessibility Capture",
            subtitle =
              if (accessibilityEnabled) "Active (captures only on explicit tap)"
              else "Optional: enables reading selected/visible text in browsers & apps",
            granted = accessibilityEnabled,
            actionLabel = if (accessibilityEnabled) "Manage" else "Enable",
            onAction = { SystemIntegrationHelper.openAccessibilitySettings(context) },
          )

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionStatusRow(
              icon = Icons.Rounded.NotificationsActive,
              title = "Playback & Shortcut Notifications",
              subtitle =
                if (notificationsAllowed) "Granted for lock-screen & background controls"
                else "Allows persistent media controls & easy shutdown",
              granted = notificationsAllowed,
              actionLabel = if (notificationsAllowed) "Granted" else "Allow",
              onAction = {
                if (!notificationsAllowed) {
                  notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
              },
            )
          }

          // Size & Transparency
          Column {
            Text(
              "Button Size: ${settings.floatingButtonSizeDp.roundToInt()} dp",
              style = MaterialTheme.typography.labelLarge,
            )
            Slider(
              value = settings.floatingButtonSizeDp,
              onValueChange = onUpdateButtonSize,
              valueRange = 44f..72f,
            )
          }

          Column {
            Text(
              "Button Opacity: ${(settings.floatingButtonAlpha * 100).roundToInt()}%",
              style = MaterialTheme.typography.labelLarge,
            )
            Slider(
              value = settings.floatingButtonAlpha,
              onValueChange = onUpdateButtonAlpha,
              valueRange = 0.35f..1.0f,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Double-tap button to Play/Pause", style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = settings.doubleTapToTogglePlay,
              onCheckedChange = onSetDoubleTapToggle,
            )
          }

          OutlinedButton(onClick = onResetButtonPosition) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reset Floating Button Position")
          }
        }
      }
    }

    // Section 2: Speech, Speed, Pitch & Language Intelligence
    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("Speech & Language Intelligence", style = MaterialTheme.typography.titleMedium)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Auto-Detect Language", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Automatically selects Arabic, English, French, German, Spanish & more",
                style = MaterialTheme.typography.bodyMedium,
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
              Text("Mixed Arabic-English Voice Switching", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Switches smoothly mid-sentence for bilingual medical terms",
                style = MaterialTheme.typography.bodyMedium,
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
                "Skips section headers and numbered bibliographic citations",
                style = MaterialTheme.typography.bodyMedium,
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
            SupportedLanguages.ALL.take(7).forEach { lang ->
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
        }
      }
    }

    // Section 3: Privacy & Security Controls
    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Privacy & Capture Safeguards", style = MaterialTheme.typography.titleMedium)
          }

          Text(
            "• Zero background screen recording or keystroke logging.\n" +
              "• Password fields and secure Android windows are strictly ignored.\n" +
              "• Selected text stays on-device unless you explicitly configure a Cloud TTS backend.\n" +
              "• No microphone permission required or requested.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Save Local Reading History", style = MaterialTheme.typography.bodyLarge)
              Text(
                "Disable for strictly ephemeral reading sessions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = settings.readingHistoryEnabled,
              onCheckedChange = onSetReadingHistoryEnabled,
            )
          }

          OutlinedButton(onClick = onClearHistory) { Text("Clear Reading History") }
        }
      }
    }

    item { Spacer(Modifier.height(80.dp)) }
  }
}

@Composable
private fun PermissionStatusRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  granted: Boolean,
  actionLabel: String,
  onAction: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(22.dp),
    )
    Spacer(Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(6.dp))
        Icon(
          imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
          contentDescription = if (granted) "Enabled" else "Action needed",
          tint =
            if (granted) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(15.dp),
        )
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.width(8.dp))
    Button(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
      Text(actionLabel)
    }
  }
}
