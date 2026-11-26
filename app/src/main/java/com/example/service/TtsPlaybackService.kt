package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.example.ClinNoteApplication
import com.example.MainActivity
import com.example.R
import com.example.model.PlaybackStatus
import com.example.model.ReaderSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TtsPlaybackService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var observerJob: Job? = null

  private lateinit var audioManager: AudioManager
  private lateinit var mediaSession: MediaSession
  private var audioFocusRequest: AudioFocusRequest? = null
  private var hasAudioFocus = false
  private var pausedByTransientFocusLoss = false
  private var isForegroundActive = false

  private val noisyReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
          ClinNoteApplication.instance.speechCoordinator.pause()
        }
      }
    }

  private val audioFocusListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      val coordinator = ClinNoteApplication.instance.speechCoordinator
      when (focusChange) {
        AudioManager.AUDIOFOCUS_GAIN -> {
          hasAudioFocus = true
          if (pausedByTransientFocusLoss) {
            pausedByTransientFocusLoss = false
            coordinator.play()
          }
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          if (coordinator.sessionState.value.status == PlaybackStatus.PLAYING) {
            pausedByTransientFocusLoss = true
            coordinator.pause()
          }
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
          // Speech remains intelligible or briefly pauses on interrupt
        }
        AudioManager.AUDIOFOCUS_LOSS -> {
          hasAudioFocus = false
          pausedByTransientFocusLoss = false
          coordinator.pause()
        }
      }
    }

  override fun onCreate() {
    super.onCreate()
    audioManager = getSystemService(AudioManager::class.java)
    setupMediaSession()
    registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

    observerJob =
      serviceScope.launch {
        ClinNoteApplication.instance.speechCoordinator.sessionState.collectLatest { state ->
          updateMediaSession(state)
          if (state.status == PlaybackStatus.PLAYING || state.status == PlaybackStatus.PAUSED) {
            if (state.status == PlaybackStatus.PLAYING) {
              requestFocusIfNeeded()
            }
            val notification = buildNotification(state)
            if (!isForegroundActive) {
              startForeground(NOTIFICATION_ID, notification)
              isForegroundActive = true
            } else {
              getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, notification)
            }
          } else if (state.status == PlaybackStatus.IDLE && isForegroundActive) {
            abandonFocus()
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundActive = false
            stopSelf()
          }
        }
      }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val coordinator = ClinNoteApplication.instance.speechCoordinator
    when (intent?.action) {
      ACTION_START_PLAYBACK -> {
        requestFocusIfNeeded()
        val notification = buildNotification(coordinator.sessionState.value)
        if (!isForegroundActive) {
          startForeground(NOTIFICATION_ID, notification)
          isForegroundActive = true
        }
      }
      ACTION_PLAY_PAUSE -> coordinator.togglePlayPause()
      ACTION_NEXT -> coordinator.nextSentence()
      ACTION_PREVIOUS -> coordinator.previousSentence()
      ACTION_STOP_SERVICE -> {
        abandonFocus()
        if (coordinator.sessionState.value.status != PlaybackStatus.IDLE) {
          coordinator.stop()
        }
        if (isForegroundActive) {
          stopForeground(STOP_FOREGROUND_REMOVE)
          isForegroundActive = false
        }
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    observerJob?.cancel()
    serviceScope.cancel()
    runCatching { unregisterReceiver(noisyReceiver) }
    abandonFocus()
    runCatching {
      mediaSession.isActive = false
      mediaSession.release()
    }
    super.onDestroy()
  }

  private fun setupMediaSession() {
    val coordinator = ClinNoteApplication.instance.speechCoordinator
    mediaSession =
      MediaSession(this, "ClinNoteReaderSession").apply {
        setCallback(
          object : MediaSession.Callback() {
            override fun onPlay() {
              requestFocusIfNeeded()
              coordinator.play()
            }

            override fun onPause() {
              pausedByTransientFocusLoss = false
              coordinator.pause()
            }

            override fun onStop() {
              pausedByTransientFocusLoss = false
              coordinator.stop()
            }

            override fun onSkipToNext() {
              coordinator.nextSentence()
            }

            override fun onSkipToPrevious() {
              coordinator.previousSentence()
            }
          }
        )
        isActive = true
      }
  }

  private fun updateMediaSession(state: ReaderSessionState) {
    val playbackStateCode =
      when (state.status) {
        PlaybackStatus.PLAYING, PlaybackStatus.PREPARING -> PlaybackState.STATE_PLAYING
        PlaybackStatus.PAUSED -> PlaybackState.STATE_PAUSED
        PlaybackStatus.ERROR -> PlaybackState.STATE_ERROR
        PlaybackStatus.IDLE -> PlaybackState.STATE_STOPPED
      }

    val actions =
      PlaybackState.ACTION_PLAY or
        PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_STOP or
        PlaybackState.ACTION_SKIP_TO_NEXT or
        PlaybackState.ACTION_SKIP_TO_PREVIOUS

    val pbState =
      PlaybackState.Builder()
        .setActions(actions)
        .setState(
          playbackStateCode,
          state.currentSentenceIndex.toLong(),
          state.speechSpeed,
        )
        .build()

    mediaSession.setPlaybackState(pbState)

    val metadata =
      MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
        .putString(
          MediaMetadata.METADATA_KEY_ARTIST,
          "${state.captureSource.displayName} · ${state.activeLanguageTag.uppercase()}",
        )
        .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, state.sentences.size.toLong())
        .build()
    mediaSession.setMetadata(metadata)
  }

  private fun buildNotification(state: ReaderSessionState): Notification {
    val openAppIntent =
      PendingIntent.getActivity(
        this,
        10,
        Intent(this, MainActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
          putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_READER)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val prevIntent = serviceActionIntent(ACTION_PREVIOUS, 11)
    val playPauseIntent = serviceActionIntent(ACTION_PLAY_PAUSE, 12)
    val nextIntent = serviceActionIntent(ACTION_NEXT, 13)
    val stopIntent = serviceActionIntent(ACTION_STOP_SERVICE, 14)

    val isPlaying = state.status == PlaybackStatus.PLAYING || state.status == PlaybackStatus.PREPARING
    val playPauseIcon =
      if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
    val playPauseLabel =
      getString(if (isPlaying) R.string.action_pause else R.string.action_play)

    val sentenceCounter =
      if (state.sentences.isNotEmpty()) {
        "Sentence ${state.currentSentenceIndex + 1}/${state.sentences.size} · ${state.speechSpeed}x"
      } else {
        state.captureSource.displayName
      }

    val snippet = state.currentSentence?.rawText?.take(90) ?: sentenceCounter

    val builder =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(this, ClinNoteApplication.CHANNEL_PLAYBACK)
      } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this)
      }

    return builder
      .setSmallIcon(R.drawable.ic_floating_reader)
      .setContentTitle(state.title)
      .setContentText(snippet)
      .setSubText(sentenceCounter)
      .setContentIntent(openAppIntent)
      .setOngoing(isPlaying)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .addAction(
        Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            getString(R.string.action_previous),
            prevIntent,
          )
          .build()
      )
      .addAction(Notification.Action.Builder(playPauseIcon, playPauseLabel, playPauseIntent).build())
      .addAction(
        Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            getString(R.string.action_next),
            nextIntent,
          )
          .build()
      )
      .addAction(
        Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.action_stop),
            stopIntent,
          )
          .build()
      )
      .setStyle(
        Notification.MediaStyle()
          .setMediaSession(mediaSession.sessionToken)
          .setShowActionsInCompactView(0, 1, 2)
      )
      .build()
  }

  private fun serviceActionIntent(action: String, requestCode: Int): PendingIntent {
    val intent = Intent(this, TtsPlaybackService::class.java).apply { this.action = action }
    return PendingIntent.getService(
      this,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun requestFocusIfNeeded() {
    if (hasAudioFocus) return
    val result =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val attrs =
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req =
          AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
        audioFocusRequest = req
        audioManager.requestAudioFocus(req)
      } else {
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
          audioFocusListener,
          AudioManager.STREAM_MUSIC,
          AudioManager.AUDIOFOCUS_GAIN,
        )
      }
    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }

  private fun abandonFocus() {
    if (!hasAudioFocus) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    } else {
      @Suppress("DEPRECATION")
      audioManager.abandonAudioFocus(audioFocusListener)
    }
    hasAudioFocus = false
  }

  companion object {
    const val NOTIFICATION_ID = 4101
    const val ACTION_START_PLAYBACK = "com.example.action.START_PLAYBACK"
    const val ACTION_PLAY_PAUSE = "com.example.action.PLAY_PAUSE"
    const val ACTION_NEXT = "com.example.action.NEXT"
    const val ACTION_PREVIOUS = "com.example.action.PREVIOUS"
    const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
  }
}
