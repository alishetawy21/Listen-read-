package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.capture.SystemIntegrationHelper
import com.example.model.ReaderSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingSettingsScreen(
  settings: ReaderSettings,
  onBack: () -> Unit,
  onToggleFloatingShortcut: (Boolean) -> Unit,
  onUpdateButtonSize: (Float) -> Unit,
  onUpdateButtonAlpha: (Float) -> Unit,
  onResetButtonPosition: () -> Unit,
  onSetDoubleTapToggle: (Boolean) -> Unit,
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

  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Floating Assistant Settings") },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      },
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Floating Language Bubble", style = MaterialTheme.typography.titleMedium)
                Text(
                  "Show movable shortcut over books, PDFs, Chrome & supported apps",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = settings.floatingShortcutEnabled && canOverlay,
                onCheckedChange = onToggleFloatingShortcut,
                modifier = Modifier.testTag("floating_settings_switch"),
              )
            }

            if (canOverlay && settings.floatingShortcutEnabled) {
              OutlinedButton(
                onClick = { SystemIntegrationHelper.startFloatingShortcut(context, expandToolbar = true) }
              ) {
                Text("Preview Floating Icon Toolbar")
              }
            }
          }
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text("Required Android Permissions", style = MaterialTheme.typography.titleMedium)

            FloatingPermissionRow(
              icon = Icons.Rounded.Layers,
              title = "Display Over Other Apps",
              subtitle =
                if (canOverlay) "Granted — floating bubble can appear over apps"
                else "Required to display the movable floating button",
              granted = canOverlay,
              actionLabel = if (canOverlay) "Manage" else "Grant",
              onAction = { SystemIntegrationHelper.openOverlayPermissionSettings(context) },
            )

            FloatingPermissionRow(
              icon = Icons.Rounded.AccessibilityNew,
              title = "Accessibility Text Capture",
              subtitle =
                if (accessibilityEnabled) "Active — reads selected text when you tap the bubble"
                else "Optional: captures selectable text on tap; ignores password fields",
              granted = accessibilityEnabled,
              actionLabel = if (accessibilityEnabled) "Manage" else "Enable",
              onAction = { SystemIntegrationHelper.openAccessibilitySettings(context) },
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              FloatingPermissionRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Foreground Notification",
                subtitle =
                  if (notificationsAllowed) "Granted for quick Stop & settings notification"
                  else "Recommended so you can pause or turn off the overlay anytime",
                granted = notificationsAllowed,
                actionLabel = if (notificationsAllowed) "Granted" else "Allow",
                onAction = {
                  if (!notificationsAllowed) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                },
              )
            }
          }
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text("Size, Transparency & Gestures", style = MaterialTheme.typography.titleMedium)

            Column {
              Text(
                "Bubble Size: ${settings.floatingButtonSizeDp.roundToInt()} dp",
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
                "Opacity: ${(settings.floatingButtonAlpha * 100).roundToInt()}%",
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
              Text("Double-tap bubble to Play/Pause", style = MaterialTheme.typography.bodyMedium)
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

      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("6-Action Icon Toolbar & Security Notes", style = MaterialTheme.typography.titleSmall)
            Text(
              "• Tap the bubble to capture text and open the icon-only toolbar: Read Aloud, Translate, Listen Pronunciation (0.75x), Practice Speaking, Accent Coach, and Open App.\n" +
                "• Use 'LinguaFloat: Read / Translate' in Android's text-selection menu or Share Sheet when an app uses custom rendering.\n" +
                "• Protected apps (banking, DRM readers with FLAG_SECURE) and password fields cannot be read by design.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item { Spacer(Modifier.height(76.dp)) }
    }
  }
}

@Composable
private fun FloatingPermissionRow(
  icon: ImageVector,
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
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(6.dp))
        Icon(
          imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
          contentDescription = if (granted) "Granted" else "Needs attention",
          tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(15.dp),
        )
      }
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.width(8.dp))
    Button(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
      Text(actionLabel)
    }
  }
}
