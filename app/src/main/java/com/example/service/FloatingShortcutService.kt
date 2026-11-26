package com.example.service

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.ClinNoteApplication
import com.example.MainActivity
import com.example.R
import com.example.ai.AiCallResult
import com.example.capture.AccessibilityCaptureResult
import com.example.capture.ClinNoteAccessibilityService
import com.example.model.PlaybackStatus
import com.example.model.ReaderSessionState
import com.example.model.ReaderSettings
import com.example.model.SupportedLanguages
import com.example.model.TranslationTone
import com.example.nlp.LanguageDetector
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Persistent movable floating LinguaFloat button + icon-only compact floating toolbar overlay.
 *
 * Toolbar actions (icon-only with accessibility labels & tooltips):
 * 1. Read selected/captured text aloud (Play/Pause)
 * 2. Translate selected text inline (Arabic <-> English via Firebase AI Logic)
 * 3. Listen to slow pronunciation (0.75x)
 * 4. Practice speaking (deep-links to Speak Mode)
 * 5. Correct pronunciation & accent (deep-links to Accent Coach)
 * 6. Open full LinguaFloat workspace
 * Plus Minimize/Collapse and Hide Shortcut controls.
 */
class FloatingShortcutService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private lateinit var windowManager: WindowManager
  private var bubbleView: FrameLayout? = null
  private var bubbleBg: GradientDrawable? = null
  private var bubblePulseAnimator: ObjectAnimator? = null
  private var bubbleParams: WindowManager.LayoutParams? = null

  private var toolbarView: LinearLayout? = null
  private var titleView: TextView? = null
  private var langBadgeView: TextView? = null
  private var sentenceView: TextView? = null
  private var translationBoxView: TextView? = null
  private var speedBtn: TextView? = null
  private var readActionBtn: ImageButton? = null

  private var currentSettings = ReaderSettings()
  private var isToolbarExpanded = false
  private var lastInlineTranslation: String? = null

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(WindowManager::class.java)
    startForeground(NOTIFICATION_ID, buildShortcutNotification())

    val app = ClinNoteApplication.instance
    scope.launch {
      app.settingsStore.settingsFlow.collectLatest { settings ->
        currentSettings = settings
        applySettingsToBubble(settings)
      }
    }
    scope.launch {
      app.speechCoordinator.sessionState.collectLatest { state ->
        updateBubblePlaybackVisual(state.status)
        updateToolbarContent(state)
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_HIDE -> {
        scope.launch {
          ClinNoteApplication.instance.settingsStore.updateSettings {
            it.copy(floatingShortcutEnabled = false)
          }
        }
        removeAllOverlays()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
      ACTION_EXPAND_MINI_PLAYER -> {
        if (Settings.canDrawOverlays(this)) {
          ensureBubbleShown()
          showFloatingToolbarOverlay()
        }
      }
      else -> {
        if (Settings.canDrawOverlays(this)) {
          ensureBubbleShown()
        } else {
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        }
      }
    }
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    scope.cancel()
    removeAllOverlays()
    super.onDestroy()
  }

  private fun overlayLayoutType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }
  }

  private fun dpToPx(dp: Float): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
      .roundToInt()
  }

  private fun ensureBubbleShown() {
    if (bubbleView != null) return

    val sizePx = dpToPx(currentSettings.floatingButtonSizeDp)
    val bg =
      GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#0A3D62"))
        setStroke(dpToPx(2f), Color.parseColor("#4DD0E1"))
      }
    bubbleBg = bg

    val icon =
      ImageView(this).apply {
        setImageResource(R.drawable.ic_floating_reader)
        setColorFilter(Color.WHITE)
        val pad = dpToPx(13f)
        setPadding(pad, pad, pad, pad)
        contentDescription = "LinguaFloat floating language assistant"
      }

    val container =
      FrameLayout(this).apply {
        background = bg
        elevation = dpToPx(8f).toFloat()
        alpha = currentSettings.floatingButtonAlpha
        addView(
          icon,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
          ),
        )
      }

    val params =
      WindowManager.LayoutParams(
          sizePx,
          sizePx,
          overlayLayoutType(),
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT,
        )
        .apply {
          gravity = Gravity.TOP or Gravity.START
          x = currentSettings.floatingButtonX
          y = currentSettings.floatingButtonY
        }

    attachBubbleTouchHandlers(container, params)

    bubbleView = container
    bubbleParams = params
    runCatching { windowManager.addView(container, params) }
    updateBubblePlaybackVisual(ClinNoteApplication.instance.speechCoordinator.sessionState.value.status)
  }

  private fun attachBubbleTouchHandlers(
    view: View,
    params: WindowManager.LayoutParams,
  ) {
    val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
    var initialX = 0
    var initialY = 0
    var initialTouchX = 0f
    var initialTouchY = 0f
    var isDragging = false

    val gestureDetector =
      GestureDetector(
        this,
        object : GestureDetector.SimpleOnGestureListener() {
          override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onBubbleSingleTap()
            return true
          }

          override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentSettings.doubleTapToTogglePlay) {
              ClinNoteApplication.instance.speechCoordinator.togglePlayPause()
              return true
            }
            return false
          }

          override fun onLongPress(e: MotionEvent) {
            openAppTab(MainActivity.TAB_FLOATING_SETTINGS)
          }
        },
      )

    view.setOnTouchListener { v, event ->
      gestureDetector.onTouchEvent(event)
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          initialX = params.x
          initialY = params.y
          initialTouchX = event.rawX
          initialTouchY = event.rawY
          isDragging = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = (event.rawX - initialTouchX).roundToInt()
          val dy = (event.rawY - initialTouchY).roundToInt()
          if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
            isDragging = true
          }
          if (isDragging) {
            params.x = initialX + dx
            params.y = initialY + dy
            runCatching { windowManager.updateViewLayout(v, params) }
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          if (isDragging) {
            snapBubbleToEdge(v, params)
          }
          true
        }
        else -> false
      }
    }
  }

  private fun snapBubbleToEdge(view: View, params: WindowManager.LayoutParams) {
    val screenWidth = resources.displayMetrics.widthPixels
    val screenHeight = resources.displayMetrics.heightPixels
    val margin = dpToPx(12f)
    val targetX =
      if (params.x + params.width / 2 < screenWidth / 2) {
        margin
      } else {
        (screenWidth - params.width - margin).coerceAtLeast(margin)
      }
    val clampedY =
      params.y.coerceIn(dpToPx(48f), (screenHeight - params.height - dpToPx(72f)).coerceAtLeast(100))

    ValueAnimator.ofInt(params.x, targetX).apply {
      duration = 180L
      addUpdateListener { anim ->
        params.x = anim.animatedValue as Int
        params.y = clampedY
        runCatching { windowManager.updateViewLayout(view, params) }
      }
      start()
    }

    scope.launch {
      ClinNoteApplication.instance.settingsStore.updateSettings {
        it.copy(floatingButtonX = targetX, floatingButtonY = clampedY)
      }
    }
  }

  private fun onBubbleSingleTap() {
    val app = ClinNoteApplication.instance
    val accessibilityService = ClinNoteAccessibilityService.activeInstance

    if (accessibilityService != null) {
      when (val result = accessibilityService.captureOnUserRequest(packageName)) {
        is AccessibilityCaptureResult.Success -> {
          lastInlineTranslation = null
          val title =
            result.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(42)
              ?: "Captured Text"
          app.speechCoordinator.loadText(
            title = title,
            rawText = result.text,
            captureSource = result.source,
            autoPlay = false,
          )
          showFloatingToolbarOverlay()
          return
        }
        is AccessibilityCaptureResult.Unavailable -> {
          Toast.makeText(this, result.reason, Toast.LENGTH_SHORT).show()
          toggleToolbarOverlay()
          return
        }
      }
    } else {
      if (!app.speechCoordinator.sessionState.value.hasContent) {
        Toast.makeText(
            this,
            "Select text in any app & tap 'LinguaFloat', or enable Accessibility in Floating Settings.",
            Toast.LENGTH_LONG,
          )
          .show()
      }
      toggleToolbarOverlay()
    }
  }

  private fun toggleToolbarOverlay() {
    if (isToolbarExpanded) {
      hideToolbarOverlay()
    } else {
      showFloatingToolbarOverlay()
    }
  }

  private fun showFloatingToolbarOverlay() {
    if (toolbarView != null) {
      updateToolbarContent(ClinNoteApplication.instance.speechCoordinator.sessionState.value)
      return
    }
    val app = ClinNoteApplication.instance
    val coordinator = app.speechCoordinator
    val state = coordinator.sessionState.value

    val panelBg =
      GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(22f).toFloat()
        setColor(Color.parseColor("#FA082032"))
        setStroke(dpToPx(1.5f), Color.parseColor("#0E8A9E"))
      }

    val root =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = panelBg
        elevation = dpToPx(14f).toFloat()
        val pad = dpToPx(12f)
        setPadding(pad, pad, pad, pad)
      }

    // Header Row: Title + Language Pill + Speed + Minimize + Close Service
    val header =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }

    val tvTitle =
      TextView(this).apply {
        text = state.title
        setTextColor(Color.WHITE)
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }
    titleView = tvTitle

    val tvLang =
      TextView(this).apply {
        text = SupportedLanguages.findByTag(state.activeLanguageTag).tag.uppercase()
        setTextColor(Color.parseColor("#8CF0E3"))
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        background =
          GradientDrawable().apply {
            cornerRadius = dpToPx(8f).toFloat()
            setColor(Color.parseColor("#133A54"))
          }
        setPadding(dpToPx(7f), dpToPx(3f), dpToPx(7f), dpToPx(3f))
      }
    langBadgeView = tvLang

    val tvSpeed =
      TextView(this).apply {
        text = "${state.speechSpeed}x"
        setTextColor(Color.WHITE)
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        background =
          GradientDrawable().apply {
            cornerRadius = dpToPx(8f).toFloat()
            setColor(Color.parseColor("#0E8A9E"))
          }
        setPadding(dpToPx(8f), dpToPx(3f), dpToPx(8f), dpToPx(3f))
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          ).apply { marginStart = dpToPx(6f) }
        setOnClickListener {
          val nextSpeed = coordinator.cycleSpeedPreset()
          text = "${nextSpeed}x"
        }
      }
    speedBtn = tvSpeed

    val minimizeBtn =
      createHeaderIconBtn(android.R.drawable.arrow_down_float, "Minimize toolbar") {
        hideToolbarOverlay()
      }

    val closeServiceBtn =
      createHeaderIconBtn(android.R.drawable.ic_menu_close_clear_cancel, "Close floating assistant") {
        scope.launch {
          app.settingsStore.updateSettings { it.copy(floatingShortcutEnabled = false) }
        }
        removeAllOverlays()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }

    header.addView(tvTitle)
    header.addView(tvLang)
    header.addView(tvSpeed)
    header.addView(minimizeBtn)
    header.addView(closeServiceBtn)

    // Captured / active sentence preview
    val tvSentence =
      TextView(this).apply {
        text =
          state.currentSentence?.rawText
            ?: "Select text in any app and tap 'LinguaFloat', or tap the bubble to capture text."
        setTextColor(Color.parseColor("#E3F3F1"))
        textSize = 13f
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dpToPx(8f), 0, dpToPx(6f))
      }
    sentenceView = tvSentence

    // Inline translation output box (visible when user taps Translate)
    val tvTranslation =
      TextView(this).apply {
        visibility = View.GONE
        setTextColor(Color.parseColor("#B2EBF2"))
        textSize = 12.5f
        maxLines = 4
        ellipsize = TextUtils.TruncateAt.END
        background =
          GradientDrawable().apply {
            cornerRadius = dpToPx(10f).toFloat()
            setColor(Color.parseColor("#103047"))
          }
        setPadding(dpToPx(10f), dpToPx(8f), dpToPx(10f), dpToPx(8f))
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          ).apply { bottomMargin = dpToPx(8f) }
      }
    translationBoxView = tvTranslation

    // 6-Action Icon-Only Floating Toolbar (with tooltips + contentDescriptions)
    val toolbarRow =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
      }

    // 1. Read selected text aloud
    val readBtn =
      createToolbarIconButton(
        iconRes = R.drawable.ic_tb_read,
        tooltip = "Read aloud",
        accentHex = "#0E8A9E",
      ) {
        coordinator.togglePlayPause()
      }
    readActionBtn = readBtn

    // 2. Translate selected text inline
    val translateBtn =
      createToolbarIconButton(
        iconRes = R.drawable.ic_tb_translate,
        tooltip = "Translate text",
        accentHex = "#145374",
      ) {
        translateActiveSentenceInline()
      }

    // 3. Listen to slow clear pronunciation (0.75x)
    val pronounceBtn =
      createToolbarIconButton(
        iconRes = R.drawable.ic_tb_pronounce,
        tooltip = "Listen to pronunciation (0.75x)",
        accentHex = "#145374",
      ) {
        val currentText = coordinator.sessionState.value.currentSentence?.rawText
        if (currentText.isNullOrBlank()) {
          Toast.makeText(this, "Capture or select text first", Toast.LENGTH_SHORT).show()
        } else {
          coordinator.speakOneShot(
            text = currentText,
            languageTag = coordinator.sessionState.value.activeLanguageTag,
            speed = 0.75f,
          )
        }
      }

    // 4. Practice speaking
    val speakBtn =
      createToolbarIconButton(
        iconRes = R.drawable.ic_tb_speak,
        tooltip = "Practice speaking",
        accentHex = "#145374",
      ) {
        val text = coordinator.sessionState.value.currentSentence?.rawText.orEmpty()
        hideToolbarOverlay()
        openAppTab(MainActivity.TAB_COACH, text)
      }

    // 5. Correct pronunciation and accent
    val accentBtn =
      createToolbarIconButton(
        iconRes = R.drawable.ic_tb_accent,
        tooltip = "Correct accent & pronunciation",
        accentHex = "#D95F39",
      ) {
        val text = coordinator.sessionState.value.currentSentence?.rawText.orEmpty()
        hideToolbarOverlay()
        openAppTab(MainActivity.TAB_COACH, text)
      }

    // 6. Open full LinguaFloat assistant
    val openAppBtn =
      createToolbarIconButton(
        iconRes = R.drawable.ic_tb_open_app,
        tooltip = "Open LinguaFloat",
        accentHex = "#1A4D6E",
      ) {
        hideToolbarOverlay()
        openAppTab(MainActivity.TAB_READER)
      }

    toolbarRow.addView(readBtn)
    toolbarRow.addView(translateBtn)
    toolbarRow.addView(pronounceBtn)
    toolbarRow.addView(speakBtn)
    toolbarRow.addView(accentBtn)
    toolbarRow.addView(openAppBtn)

    root.addView(header)
    root.addView(tvSentence)
    root.addView(tvTranslation)
    root.addView(toolbarRow)

    val widthPx = (resources.displayMetrics.widthPixels - dpToPx(24f)).coerceAtMost(dpToPx(420f))
    val params =
      WindowManager.LayoutParams(
          widthPx,
          WindowManager.LayoutParams.WRAP_CONTENT,
          overlayLayoutType(),
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
          PixelFormat.TRANSLUCENT,
        )
        .apply {
          gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
          y = dpToPx(76f)
        }

    toolbarView = root
    isToolbarExpanded = true
    runCatching { windowManager.addView(root, params) }
    updateToolbarContent(state)
  }

  private fun translateActiveSentenceInline() {
    val app = ClinNoteApplication.instance
    val state = app.speechCoordinator.sessionState.value
    val sentence = state.currentSentence?.rawText?.takeIf { it.isNotBlank() }
    if (sentence == null) {
      Toast.makeText(this, "No text loaded to translate yet.", Toast.LENGTH_SHORT).show()
      return
    }

    val detectedSource = LanguageDetector.detectLanguage(sentence)
    val targetLang = if (detectedSource.startsWith("ar", true)) "en-US" else "ar"

    translationBoxView?.apply {
      visibility = View.VISIBLE
      text = "Translating ($detectedSource → $targetLang)..."
    }

    scope.launch {
      when (
        val res =
          app.linguaAiService.translateWithContext(
            text = sentence,
            sourceLangHint = detectedSource,
            targetLang = targetLang,
            tone = TranslationTone.NATURAL,
          )
      ) {
        is AiCallResult.Success -> {
          lastInlineTranslation = res.data.translatedText
          translationBoxView?.apply {
            visibility = View.VISIBLE
            text = "${res.data.translatedText}\n(${res.data.literalVsContextualNote})"
            textDirection =
              if (targetLang.startsWith("ar", true)) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
          }
          app.speechCoordinator.speakOneShot(res.data.translatedText, targetLang)
        }
        is AiCallResult.Error -> {
          translationBoxView?.apply {
            visibility = View.VISIBLE
            text = "Translation unavailable: ${res.message}"
          }
        }
      }
    }
  }

  private fun createHeaderIconBtn(iconRes: Int, desc: String, onClick: () -> Unit): ImageButton {
    return ImageButton(this).apply {
      setImageResource(iconRes)
      setColorFilter(Color.parseColor("#B2D8E6"))
      background = null
      contentDescription = desc
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        tooltipText = desc
      }
      setPadding(dpToPx(7f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
      setOnClickListener { onClick() }
    }
  }

  private fun createToolbarIconButton(
    iconRes: Int,
    tooltip: String,
    accentHex: String,
    onClick: () -> Unit,
  ): ImageButton {
    val size = dpToPx(46f)
    return ImageButton(this).apply {
      setImageResource(iconRes)
      setColorFilter(Color.WHITE)
      background =
        GradientDrawable().apply {
          shape = GradientDrawable.OVAL
          setColor(Color.parseColor(accentHex))
        }
      contentDescription = tooltip
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        tooltipText = tooltip
      }
      val pad = dpToPx(11f)
      setPadding(pad, pad, pad, pad)
      layoutParams =
        LinearLayout.LayoutParams(size, size).apply {
          marginStart = dpToPx(4f)
          marginEnd = dpToPx(4f)
        }
      setOnClickListener { onClick() }
    }
  }

  private fun hideToolbarOverlay() {
    toolbarView?.let { runCatching { windowManager.removeView(it) } }
    toolbarView = null
    isToolbarExpanded = false
  }

  private fun removeAllOverlays() {
    hideToolbarOverlay()
    bubblePulseAnimator?.cancel()
    bubbleView?.let { runCatching { windowManager.removeView(it) } }
    bubbleView = null
    bubbleParams = null
  }

  private fun applySettingsToBubble(settings: ReaderSettings) {
    val view = bubbleView ?: return
    val params = bubbleParams ?: return
    val newSize = dpToPx(settings.floatingButtonSizeDp)
    params.width = newSize
    params.height = newSize
    view.alpha = settings.floatingButtonAlpha
    runCatching { windowManager.updateViewLayout(view, params) }
  }

  private fun updateBubblePlaybackVisual(status: PlaybackStatus) {
    val bg = bubbleBg ?: return
    val view = bubbleView ?: return
    when (status) {
      PlaybackStatus.PLAYING, PlaybackStatus.PREPARING -> {
        bg.setColor(Color.parseColor("#0E8A9E"))
        bg.setStroke(dpToPx(2.5f), Color.parseColor("#FF9E6D"))
        if (bubblePulseAnimator == null) {
          bubblePulseAnimator =
            ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.08f, 1f).apply {
              duration = 1100L
              repeatCount = ValueAnimator.INFINITE
              start()
            }
        }
      }
      PlaybackStatus.PAUSED -> {
        bg.setColor(Color.parseColor("#D95F39"))
        bg.setStroke(dpToPx(2f), Color.parseColor("#FFD1B8"))
        bubblePulseAnimator?.cancel()
        bubblePulseAnimator = null
        view.scaleX = 1f
      }
      else -> {
        bg.setColor(Color.parseColor("#0A3D62"))
        bg.setStroke(dpToPx(2f), Color.parseColor("#4DD0E1"))
        bubblePulseAnimator?.cancel()
        bubblePulseAnimator = null
        view.scaleX = 1f
      }
    }
  }

  private fun updateToolbarContent(state: ReaderSessionState) {
    if (!isToolbarExpanded) return
    titleView?.text = state.title
    langBadgeView?.text = SupportedLanguages.findByTag(state.activeLanguageTag).tag.uppercase()
    speedBtn?.text = "${state.speechSpeed}x"
    sentenceView?.apply {
      text =
        state.currentSentence?.rawText
          ?: "Ready. Select text in any app or tap the floating icon to capture text."
      textDirection =
        if (state.currentSentence?.isRtl == true || state.isRtlDocument) {
          View.TEXT_DIRECTION_RTL
        } else {
          View.TEXT_DIRECTION_LTR
        }
    }
    val isPlaying = state.status == PlaybackStatus.PLAYING || state.status == PlaybackStatus.PREPARING
    readActionBtn?.setImageResource(
      if (isPlaying) android.R.drawable.ic_media_pause else R.drawable.ic_tb_read
    )
    if (lastInlineTranslation == null) {
      translationBoxView?.visibility = View.GONE
    }
  }

  private fun openAppTab(tab: String, actionText: String? = null) {
    val intent =
      Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
        if (!actionText.isNullOrBlank()) {
          putExtra(MainActivity.EXTRA_ACTION_TEXT, actionText)
        }
      }
    startActivity(intent)
  }

  private fun buildShortcutNotification(): Notification {
    val openIntent =
      PendingIntent.getActivity(
        this,
        20,
        Intent(this, MainActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
          putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_FLOATING_SETTINGS)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      PendingIntent.getService(
        this,
        21,
        Intent(this, FloatingShortcutService::class.java).apply { action = ACTION_HIDE },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val builder =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(this, ClinNoteApplication.CHANNEL_SHORTCUT)
      } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this)
      }

    return builder
      .setSmallIcon(R.drawable.ic_floating_reader)
      .setContentTitle("LinguaFloat Assistant Active")
      .setContentText("Tap bubble for Read, Translate & Accent Coach")
      .setContentIntent(openIntent)
      .setOngoing(true)
      .addAction(
        Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.action_stop_shortcut),
            stopIntent,
          )
          .build()
      )
      .build()
  }

  companion object {
    const val NOTIFICATION_ID = 4102
    const val ACTION_SHOW = "com.example.floating.SHOW"
    const val ACTION_HIDE = "com.example.floating.HIDE"
    const val ACTION_EXPAND_MINI_PLAYER = "com.example.floating.EXPAND_MINI"
  }
}
