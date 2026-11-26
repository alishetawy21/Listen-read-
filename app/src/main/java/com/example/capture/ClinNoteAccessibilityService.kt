package com.example.capture

import android.accessibilityservice.AccessibilityService
import android.text.InputType
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.CaptureSource

sealed interface AccessibilityCaptureResult {
  data class Success(
    val text: String,
    val source: CaptureSource,
    val packageName: String,
  ) : AccessibilityCaptureResult

  data class Unavailable(val reason: String) : AccessibilityCaptureResult
}

/**
 * Privacy-preserving AccessibilityService for ClinNote Universal Smart Text Reader.
 *
 * Security & privacy design:
 * - Does NOT continuously capture or log text in the background.
 * - Only inspects the active window when the user explicitly taps the floating shortcut.
 * - Strictly excludes password fields and hidden/sensitive nodes.
 */
class ClinNoteAccessibilityService : AccessibilityService() {

  @Volatile private var lastObservedSelection: String? = null
  @Volatile private var lastObservedPackage: String = ""

  override fun onServiceConnected() {
    super.onServiceConnected()
    activeInstance = this
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    // Only cache lightweight active text selection if not a password field
    if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
      val src = event.source ?: return
      try {
        if (isSensitiveNode(src)) return
        val selected = extractSelectedSubstring(src)
        if (!selected.isNullOrBlank()) {
          lastObservedSelection = selected
          lastObservedPackage = event.packageName?.toString().orEmpty()
        }
      } finally {
        src.recycle()
      }
    } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      val pkg = event.packageName?.toString().orEmpty()
      if (pkg != lastObservedPackage) {
        lastObservedSelection = null
        lastObservedPackage = pkg
      }
    }
  }

  override fun onInterrupt() = Unit

  override fun onDestroy() {
    if (activeInstance === this) {
      activeInstance = null
    }
    super.onDestroy()
  }

  fun captureOnUserRequest(ownPackageName: String): AccessibilityCaptureResult {
    val root =
      rootInActiveWindow
        ?: return AccessibilityCaptureResult.Unavailable(
          "This screen blocks accessibility reading. Try selecting text and tapping 'Read with ClinNote' or Share to ClinNote."
        )

    val pkg = root.packageName?.toString().orEmpty()
    if (pkg == ownPackageName) {
      root.recycle()
      return AccessibilityCaptureResult.Unavailable(
        "Already inside ClinNote — tap any note or sentence to read aloud."
      )
    }

    return try {
      val activeSelection = findSelectedTextInTree(root) ?: lastObservedSelection
      if (!activeSelection.isNullOrBlank()) {
        AccessibilityCaptureResult.Success(
          text = activeSelection.trim(),
          source = CaptureSource.ACCESSIBILITY_SELECTION,
          packageName = pkg,
        )
      } else {
        val visibleParagraphs = collectReadableParagraphs(root)
        if (visibleParagraphs.isNotEmpty()) {
          AccessibilityCaptureResult.Success(
            text = visibleParagraphs.joinToString("\n\n"),
            source = CaptureSource.ACCESSIBILITY_SCREEN,
            packageName = pkg,
          )
        } else {
          AccessibilityCaptureResult.Unavailable(
            "No readable text exposed by this app. Use Share or 'Read with ClinNote' from the text selection menu."
          )
        }
      }
    } finally {
      root.recycle()
    }
  }

  companion object {
    @Volatile
    var activeInstance: ClinNoteAccessibilityService? = null
      private set

    internal fun isSensitiveNode(node: AccessibilityNodeInfo): Boolean {
      if (node.isPassword) return true
      val inputType = node.inputType
      val variation = inputType and InputType.TYPE_MASK_VARIATION
      if (
        variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
          variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
          variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
          variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
      ) {
        return true
      }
      val viewId = node.viewIdResourceName?.lowercase().orEmpty()
      if (viewId.contains("password") || viewId.contains("passcode") || viewId.contains("pin_input")) {
        return true
      }
      return false
    }

    internal fun extractSelectedSubstring(node: AccessibilityNodeInfo): String? {
      if (isSensitiveNode(node)) return null
      val text = node.text?.toString() ?: return null
      val start = node.textSelectionStart
      val end = node.textSelectionEnd
      if (start >= 0 && end > start && end <= text.length) {
        return text.substring(start, end)
      }
      return null
    }

    internal fun findSelectedTextInTree(node: AccessibilityNodeInfo): String? {
      if (isSensitiveNode(node)) return null
      extractSelectedSubstring(node)?.takeIf { it.isNotBlank() }?.let { return it }

      for (i in 0 until node.childCount) {
        val child = node.getChild(i) ?: continue
        try {
          val found = findSelectedTextInTree(child)
          if (!found.isNullOrBlank()) return found
        } finally {
          child.recycle()
        }
      }
      return null
    }

    internal fun collectReadableParagraphs(root: AccessibilityNodeInfo): List<String> {
      val collected = LinkedHashSet<String>()
      traverseReadableNodes(root, collected)
      return collected.toList()
    }

    private fun traverseReadableNodes(
      node: AccessibilityNodeInfo,
      collected: LinkedHashSet<String>,
    ) {
      if (!node.isVisibleToUser || isSensitiveNode(node)) return

      val className = node.className?.toString().orEmpty()
      val isInteractiveChromeOnly =
        className.endsWith("Button") ||
          className.endsWith("ImageButton") ||
          className.endsWith("CheckBox") ||
          className.endsWith("Switch")

      val text = node.text?.toString()?.trim()
      if (!text.isNullOrBlank() && !isInteractiveChromeOnly && text.length >= 3) {
        collected.add(text)
      } else {
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrBlank() && !isInteractiveChromeOnly && desc.length >= 20) {
          collected.add(desc)
        }
      }

      for (i in 0 until node.childCount) {
        val child = node.getChild(i) ?: continue
        try {
          traverseReadableNodes(child, collected)
        } finally {
          child.recycle()
        }
      }
    }
  }
}
