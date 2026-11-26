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
import com.example.capture.AccessibilityCaptureResult
import com.example.capture.ClinNoteAccessibilityService
import com.example.model.PlaybackStatus
import com.example.model.ReaderSessionState
import com.example.model.ReaderSettings
import com.example.model.SupportedLanguages
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Persistent movable floating shortcut + expandable system-wide mini-player overlay.
 *
 * Gestures on the floating button:
 * - Drag: move anywhere on screen and snap cleanly to nearest screen edge.
 * - Single tap: activate reading mode (captures selected/visible text via AccessibilityService if
 *   enabled, or toggles the compact floating mini-player with capture hints).
 * - Double tap (configurable): start/pause reading immediately.
 * - Long press: open ClinNote Reader Settings directly.
 */
class FloatingShortcutService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private lateinit var windowManager: WindowManager
  private var bubbleView: FrameLayout? = null
  private var bubbleBg: GradientDrawable? = null
  private var bubbleIcon: ImageView? = null
  private var bubblePulseAnimator: ObjectAnimator? = null
  private var bubbleParams: WindowManager.LayoutParams? = null

  private var miniPlayerView: LinearLayout? = null
  private var miniPlayerParams: WindowManager.LayoutParams? = null
  private var titleView: TextView? = null
  private var langBadgeView: TextView? = null
  private var sentenceView: TextView? = null
  private var speedBtn: TextView? = null
  private var playPauseBtn: ImageButton? = null

  private var currentSettings = ReaderSettings()
  private var isMiniPlayerExpanded = false

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
        updateMiniPlayerContent(state)
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
          showMiniPlayerOverlay()
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
        setColor(Color.parseColor("#0B4F5C"))
        setStroke(dpToPx(2f), Color.parseColor("#5CD6C8"))
      }
    bubbleBg = bg

    val icon =
      ImageView(this).apply {
        setImageResource(R.drawable.ic_floating_reader)
        setColorFilter(Color.WHITE)
        val pad = dpToPx(13f)
        setPadding(pad, pad, pad, pad)
        contentDescription = "ClinNote floating smart reader shortcut"
      }
    bubbleIcon = icon

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
            openSettingsScreen()
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
    val clampedY = params.y.coerceIn(dpToPx(48f), (screenHeight - params.height - dpToPx(72f)).coerceAtLeast(100))

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
          val title =
            result.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(42)
              ?: "Captured Text"
          app.speechCoordinator.loadText(
            title = title,
            rawText = result.text,
            captureSource = result.source,
            autoPlay = true,
          )
          showMiniPlayerOverlay()
          return
        }
        is AccessibilityCaptureResult.Unavailable -> {
          Toast.makeText(this, result.reason, Toast.LENGTH_LONG).show()
          toggleMiniPlayerOverlay()
          return
        }
      }
    } else {
      if (!app.speechCoordinator.sessionState.value.hasContent) {
        Toast.makeText(
            this,
            "Enable ClinNote Accessibility in Settings to capture screen text, or select text and choose 'Read with ClinNote'.",
            Toast.LENGTH_LONG,
          )
          .show()
      }
      toggleMiniPlayerOverlay()
    }
  }

  private fun toggleMiniPlayerOverlay() {
    if (isMiniPlayerExpanded) {
      hideMiniPlayerOverlay()
    } else {
      showMiniPlayerOverlay()
    }
  }

  private fun showMiniPlayerOverlay() {
    if (miniPlayerView != null) return
    val coordinator = ClinNoteApplication.instance.speechCoordinator
    val state = coordinator.sessionState.value

    val panelBg =
      GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(22f).toFloat()
        setColor(Color.parseColor("#FA0E1E22"))
        setStroke(dpToPx(1.5f), Color.parseColor("#3B8D86"))
      }

    val root =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = panelBg
        elevation = dpToPx(12f).toFloat()
        val pad = dpToPx(14f)
        setPadding(pad, pad, pad, pad)
      }

    // Header Row: Title + Language Pill + Open App + Close
    val header =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }

    val tvTitle =
      TextView(this).apply {
        text = state.title
        setTextColor(Color.WHITE)
        textSize = 14f
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
        val badgeBg =
          GradientDrawable().apply {
            cornerRadius = dpToPx(10f).toFloat()
            setColor(Color.parseColor("#1B3F46"))
          }
        background = badgeBg
        setPadding(dpToPx(8f), dpToPx(3f), dpToPx(8f), dpToPx(3f))
      }
    langBadgeView = tvLang

    val openBtn =
      ImageButton(this).apply {
        setImageResource(android.R.drawable.ic_menu_view)
        setColorFilter(Color.parseColor("#B6D5D1"))
        background = null
        contentDescription = "Open in ClinNote"
        setPadding(dpToPx(8f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
        setOnClickListener {
          hideMiniPlayerOverlay()
          val intent =
            Intent(this@FloatingShortcutService, MainActivity::class.java).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
              putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_READER)
            }
          startActivity(intent)
        }
      }

    val closeBtn =
      ImageButton(this).apply {
        setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        setColorFilter(Color.parseColor("#B6D5D1"))
        background = null
        contentDescription = "Collapse mini-player"
        setPadding(dpToPx(6f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
        setOnClickListener { hideMiniPlayerOverlay() }
      }

    header.addView(tvTitle)
    header.addView(tvLang)
    header.addView(openBtn)
    header.addView(closeBtn)

    // Current Sentence Highlight View
    val tvSentence =
      TextView(this).apply {
        text =
          state.currentSentence?.rawText
            ?: "Select text in any app and tap 'Read with ClinNote', or tap the shortcut to capture visible text."
        setTextColor(Color.parseColor("#E3F3F1"))
        textSize = 13f
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dpToPx(8f), 0, dpToPx(10f))
      }
    sentenceView = tvSentence

    // Controls Row: Prev | Play/Pause | Next | Stop | Speed
    val controls =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }

    val prevBtn =
      createOverlayControlButton(android.R.drawable.ic_media_previous, "Previous sentence") {
        coordinator.previousSentence()
      }

    val playBtn =
      createOverlayControlButton(android.R.drawable.ic_media_play, "Play or pause") {
        coordinator.togglePlayPause()
      }
    playPauseBtn = playBtn

    val nextBtn =
      createOverlayControlButton(android.R.drawable.ic_media_next, "Next sentence") {
        coordinator.nextSentence()
      }

    val stopBtn =
      createOverlayControlButton(android.R.drawable.ic_delete, "Stop reading") {
        coordinator.stop()
      }

    val tvSpeed =
      TextView(this).apply {
        text = "${state.speechSpeed}x"
        setTextColor(Color.WHITE)
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        val speedBg =
          GradientDrawable().apply {
            cornerRadius = dpToPx(14f).toFloat()
            setColor(Color.parseColor("#1D5B66"))
          }
        background = speedBg
        setPadding(dpToPx(12f), dpToPx(7f), dpToPx(12f), dpToPx(7f))
        setOnClickListener {
          val nextSpeed = coordinator.cycleSpeedPreset()
          text = "${nextSpeed}x"
        }
      }
    speedBtn = tvSpeed

    val spacer =
      View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
      }

    controls.addView(prevBtn)
    controls.addView(playBtn)
    controls.addView(nextBtn)
    controls.addView(stopBtn)
    controls.addView(spacer)
    controls.addView(tvSpeed)

    root.addView(header)
    root.addView(tvSentence)
    root.addView(controls)

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
          y = dpToPx(84f)
        }

    miniPlayerView = root
    miniPlayerParams = params
    isMiniPlayerExpanded = true
    runCatching { windowManager.addView(root, params) }
    updateMiniPlayerContent(state)
  }

  private fun createOverlayControlButton(
    iconRes: Int,
    desc: String,
    onClick: () -> Unit,
  ): ImageButton {
    val size = dpToPx(44f)
    return ImageButton(this).apply {
      setImageResource(iconRes)
      setColorFilter(Color.WHITE)
      val btnBg =
        GradientDrawable().apply {
          shape = GradientDrawable.OVAL
          setColor(Color.parseColor("#183E46"))
        }
      background = btnBg
      contentDescription = desc
      layoutParams =
        LinearLayout.LayoutParams(size, size).apply { marginEnd = dpToPx(8f) }
      setOnClickListener { onClick() }
    }
  }

  private fun hideMiniPlayerOverlay() {
    miniPlayerView?.let { runCatching { windowManager.removeView(it) } }
    miniPlayerView = null
    miniPlayerParams = null
    isMiniPlayerExpanded = false
  }

  private fun removeAllOverlays() {
    hideMiniPlayerOverlay()
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
        bg.setColor(Color.parseColor("#1D7A72"))
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
        bg.setColor(Color.parseColor("#B05B3B"))
        bg.setStroke(dpToPx(2f), Color.parseColor("#FFD1B8"))
        bubblePulseAnimator?.cancel()
        bubblePulseAnimator = null
        view.scaleX = 1f
      }
      else -> {
        bg.setColor(Color.parseColor("#0B4F5C"))
        bg.setStroke(dpToPx(2f), Color.parseColor("#5CD6C8"))
        bubblePulseAnimator?.cancel()
        bubblePulseAnimator = null
        view.scaleX = 1f
      }
    }
  }

  private fun updateMiniPlayerContent(state: ReaderSessionState) {
    if (!isMiniPlayerExpanded) return
    titleView?.text = state.title
    langBadgeView?.text = SupportedLanguages.findByTag(state.activeLanguageTag).tag.uppercase()
    speedBtn?.text = "${state.speechSpeed}x"
    sentenceView?.apply {
      text =
        state.currentSentence?.rawText
          ?: "Ready. Select text in any app or tap the shortcut to capture screen text."
      textDirection =
        if (state.currentSentence?.isRtl == true || state.isRtlDocument) {
          View.TEXT_DIRECTION_RTL
        } else {
          View.TEXT_DIRECTION_LTR
        }
    }
    val isPlaying = state.status == PlaybackStatus.PLAYING || state.status == PlaybackStatus.PREPARING
    playPauseBtn?.setImageResource(
      if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
    )
  }

  private fun openSettingsScreen() {
    val intent =
      Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_SETTINGS)
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
          putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_SETTINGS)
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
      .setContentTitle("ClinNote Floating Shortcut Active")
      .setContentText("Tap bubble to read • Long-press for settings")
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
