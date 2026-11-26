package com.example.capture

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.example.service.FloatingShortcutService

object SystemIntegrationHelper {

  fun canDrawOverlays(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
  }

  fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, ClinNoteAccessibilityService::class.java).flattenToString()
    val enabledServices =
      Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
      val component = splitter.next()
      if (component.equals(expected, ignoreCase = true)) {
        return true
      }
    }
    return false
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun hasMicrophonePermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
  }

  fun openOverlayPermissionSettings(context: Context) {
    val intent =
      Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${context.packageName}"),
        )
        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { context.startActivity(intent) }
  }

  fun openAccessibilitySettings(context: Context) {
    val intent =
      Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { context.startActivity(intent) }
  }

  fun startFloatingShortcut(context: Context, expandToolbar: Boolean = false) {
    if (!canDrawOverlays(context)) return
    val intent =
      Intent(context, FloatingShortcutService::class.java).apply {
        action =
          if (expandToolbar) FloatingShortcutService.ACTION_EXPAND_MINI_PLAYER
          else FloatingShortcutService.ACTION_SHOW
      }
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }
  }

  fun stopFloatingShortcut(context: Context) {
    val intent =
      Intent(context, FloatingShortcutService::class.java).apply {
        action = FloatingShortcutService.ACTION_HIDE
      }
    runCatching { context.startService(intent) }
  }
}
