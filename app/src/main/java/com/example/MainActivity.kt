package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.capture.SystemIntegrationHelper
import com.example.model.VoiceGender
import com.example.nlp.LanguageDetector
import com.example.ui.ClinNoteViewModel
import com.example.ui.components.MiniPlayerBar
import com.example.ui.navigation.CoachRoute
import com.example.ui.navigation.FloatingSettingsRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.LibraryRoute
import com.example.ui.navigation.PracticeRoute
import com.example.ui.navigation.ProgressRoute
import com.example.ui.navigation.ReaderRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.StudioRoute
import com.example.ui.navigation.TranslationRoute
import com.example.ui.navigation.VocabularyRoute
import com.example.ui.screens.CoachScreen
import com.example.ui.screens.FloatingSettingsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PracticeScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.TranslationScreen
import com.example.ui.screens.VocabularyScreen
import com.example.ui.theme.LinguaFloatTheme

class MainActivity : ComponentActivity() {

  private var pendingTab by mutableStateOf<String?>(null)
  private var pendingActionText by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    pendingTab = intent?.getStringExtra(EXTRA_OPEN_TAB)
    pendingActionText = intent?.getStringExtra(EXTRA_ACTION_TEXT)

    setContent {
      LinguaFloatTheme {
        val vm: ClinNoteViewModel = viewModel()
        LinguaFloatRootScreen(
          viewModel = vm,
          pendingTab = pendingTab,
          pendingActionText = pendingActionText,
          onPendingActionConsumed = {
            pendingTab = null
            pendingActionText = null
          },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingTab = intent.getStringExtra(EXTRA_OPEN_TAB)
    pendingActionText = intent.getStringExtra(EXTRA_ACTION_TEXT)
  }

  companion object {
    const val EXTRA_OPEN_TAB = "extra_open_tab"
    const val EXTRA_ACTION_TEXT = "extra_action_text"
    const val TAB_HOME = "home"
    const val TAB_LIBRARY = "library"
    const val TAB_READER = "reader"
    const val TAB_TRANSLATION = "translation"
    const val TAB_COACH = "coach"
    const val TAB_PRACTICE = "practice"
    const val TAB_VOCABULARY = "vocabulary"
    const val TAB_PROGRESS = "progress"
    const val TAB_FLOATING_SETTINGS = "floating_settings"
    const val TAB_STUDIO = "studio"
    const val TAB_SETTINGS = "settings"
  }
}

@Composable
fun LinguaFloatRootScreen(
  viewModel: ClinNoteViewModel,
  pendingTab: String?,
  pendingActionText: String?,
  onPendingActionConsumed: () -> Unit,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
  val captureState by viewModel.captureState.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val notes by viewModel.notes.collectAsStateWithLifecycle()
  val rules by viewModel.pronunciationRules.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val history by viewModel.history.collectAsStateWithLifecycle()
  val translations by viewModel.translationHistory.collectAsStateWithLifecycle()
  val savedPhrases by viewModel.savedPhrases.collectAsStateWithLifecycle()
  val attempts by viewModel.pronunciationAttempts.collectAsStateWithLifecycle()
  val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
  val importBanner by viewModel.importBanner.collectAsStateWithLifecycle()

  val translationInput by viewModel.translationInput.collectAsStateWithLifecycle()
  val translationSourceLang by viewModel.translationSourceLang.collectAsStateWithLifecycle()
  val translationTargetLang by viewModel.translationTargetLang.collectAsStateWithLifecycle()
  val translationTone by viewModel.translationTone.collectAsStateWithLifecycle()
  val translationUiState by viewModel.translationUiState.collectAsStateWithLifecycle()
  val inlineSheetState by viewModel.inlineSheetState.collectAsStateWithLifecycle()

  val coachTargetSentence by viewModel.coachTargetSentence.collectAsStateWithLifecycle()
  val coachTargetLang by viewModel.coachTargetLang.collectAsStateWithLifecycle()
  val coachUiState by viewModel.coachUiState.collectAsStateWithLifecycle()
  val freeSpeakTranscript by viewModel.freeSpeakTranscript.collectAsStateWithLifecycle()
  val freeSpeakUiState by viewModel.freeSpeakUiState.collectAsStateWithLifecycle()

  val context = LocalContext.current
  LaunchedEffect(settings.floatingShortcutEnabled) {
    if (settings.floatingShortcutEnabled && SystemIntegrationHelper.canDrawOverlays(context)) {
      SystemIntegrationHelper.startFloatingShortcut(context)
    }
  }

  LaunchedEffect(pendingTab, pendingActionText) {
    val tab = pendingTab ?: return@LaunchedEffect
    if (!pendingActionText.isNullOrBlank()) {
      when (tab) {
        MainActivity.TAB_COACH -> {
          viewModel.setCoachTarget(pendingActionText, LanguageDetector.detectLanguage(pendingActionText))
        }
        MainActivity.TAB_TRANSLATION -> {
          viewModel.updateTranslationInput(pendingActionText)
          viewModel.runTranslation(pendingActionText)
        }
      }
    }
    val route: Any =
      when (tab) {
        MainActivity.TAB_LIBRARY -> LibraryRoute
        MainActivity.TAB_READER -> ReaderRoute
        MainActivity.TAB_TRANSLATION -> TranslationRoute
        MainActivity.TAB_COACH -> CoachRoute
        MainActivity.TAB_PRACTICE -> PracticeRoute
        MainActivity.TAB_VOCABULARY -> VocabularyRoute
        MainActivity.TAB_PROGRESS -> ProgressRoute
        MainActivity.TAB_FLOATING_SETTINGS -> FloatingSettingsRoute
        MainActivity.TAB_STUDIO -> StudioRoute
        MainActivity.TAB_SETTINGS -> SettingsRoute
        else -> HomeRoute
      }
    navController.navigate(route) { launchSingleTop = true }
    onPendingActionConsumed()
  }

  val navigateToTopTab: (Any) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(HomeRoute) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  val preferredGender =
    runCatching { VoiceGender.valueOf(settings.preferredGender) }.getOrDefault(VoiceGender.ANY)

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 700.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        if (!useNavRail) {
          Column(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
          ) {
            MiniPlayerBar(
              state = sessionState,
              onPlayPause = { viewModel.speechCoordinator.togglePlayPause() },
              onStop = { viewModel.speechCoordinator.stop() },
              onPrevious = { viewModel.speechCoordinator.previousSentence() },
              onNext = { viewModel.speechCoordinator.nextSentence() },
              onRepeatToggle = { viewModel.speechCoordinator.toggleRepeatCurrentSentence() },
              onSelectSpeed = { viewModel.speechCoordinator.setSpeed(it) },
              onSelectVolume = { viewModel.speechCoordinator.setVolume(it) },
              onSelectLanguage = { viewModel.speechCoordinator.selectLanguageOverride(it) },
              onBookmark = { viewModel.speechCoordinator.bookmarkCurrentPosition() },
              onTranslateCurrent = {
                val sentence = sessionState.currentSentence
                if (sentence != null) {
                  viewModel.openSentenceAssistSheet(sentence.rawText, sentence.detectedLanguageTag)
                  navigateToTopTab(ReaderRoute)
                }
              },
              onOpenReader = { navigateToTopTab(ReaderRoute) },
            )
            NavigationBar {
              NavigationBarItem(
                selected = currentDestination?.hasRoute<HomeRoute>() == true,
                onClick = { navigateToTopTab(HomeRoute) },
                icon = {
                  Icon(
                    if (currentDestination?.hasRoute<HomeRoute>() == true) Icons.Rounded.Home
                    else Icons.Outlined.Home,
                    contentDescription = "Home Dashboard",
                  )
                },
                label = { Text("Home") },
                modifier = Modifier.testTag("nav_home"),
              )
              NavigationBarItem(
                selected =
                  currentDestination?.hasRoute<ReaderRoute>() == true ||
                    currentDestination?.hasRoute<LibraryRoute>() == true,
                onClick = { navigateToTopTab(ReaderRoute) },
                icon = {
                  Icon(
                    if (currentDestination?.hasRoute<ReaderRoute>() == true)
                      Icons.AutoMirrored.Rounded.MenuBook
                    else Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = "Book & PDF Reader",
                  )
                },
                label = { Text("Reader") },
                modifier = Modifier.testTag("nav_reader"),
              )
              NavigationBarItem(
                selected = currentDestination?.hasRoute<TranslationRoute>() == true,
                onClick = { navigateToTopTab(TranslationRoute) },
                icon = {
                  Icon(
                    if (currentDestination?.hasRoute<TranslationRoute>() == true) Icons.Rounded.Translate
                    else Icons.Outlined.Translate,
                    contentDescription = "Translation Workspace",
                  )
                },
                label = { Text("Translate") },
                modifier = Modifier.testTag("nav_translate"),
              )
              NavigationBarItem(
                selected = currentDestination?.hasRoute<CoachRoute>() == true,
                onClick = { navigateToTopTab(CoachRoute) },
                icon = {
                  Icon(
                    if (currentDestination?.hasRoute<CoachRoute>() == true) Icons.Rounded.Mic
                    else Icons.Outlined.Mic,
                    contentDescription = "Pronunciation Coach",
                  )
                },
                label = { Text("Coach") },
                modifier = Modifier.testTag("nav_coach"),
              )
              NavigationBarItem(
                selected = currentDestination?.hasRoute<SettingsRoute>() == true,
                onClick = { navigateToTopTab(SettingsRoute) },
                icon = {
                  Icon(
                    if (currentDestination?.hasRoute<SettingsRoute>() == true) Icons.Rounded.Tune
                    else Icons.Outlined.Tune,
                    contentDescription = "Settings & Preferences",
                  )
                },
                label = { Text("Settings") },
                modifier = Modifier.testTag("nav_settings"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            NavigationRailItem(
              selected = currentDestination?.hasRoute<HomeRoute>() == true,
              onClick = { navigateToTopTab(HomeRoute) },
              icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
              label = { Text("Home") },
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<ReaderRoute>() == true,
              onClick = { navigateToTopTab(ReaderRoute) },
              icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Reader") },
              label = { Text("Reader") },
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<TranslationRoute>() == true,
              onClick = { navigateToTopTab(TranslationRoute) },
              icon = { Icon(Icons.Rounded.Translate, contentDescription = "Translate") },
              label = { Text("Translate") },
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<CoachRoute>() == true,
              onClick = { navigateToTopTab(CoachRoute) },
              icon = { Icon(Icons.Rounded.Mic, contentDescription = "Coach") },
              label = { Text("Coach") },
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<SettingsRoute>() == true,
              onClick = { navigateToTopTab(SettingsRoute) },
              icon = { Icon(Icons.Rounded.Tune, contentDescription = "Settings") },
              label = { Text("Settings") },
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(modifier = Modifier.weight(1f).widthIn(max = 860.dp).fillMaxWidth()) {
            NavHost(navController = navController, startDestination = HomeRoute) {
              composable<HomeRoute> {
                HomeScreen(
                  settings = settings,
                  notes = notes,
                  savedPhrases = savedPhrases,
                  attempts = attempts,
                  translations = translations,
                  onToggleFloatingShortcut = { viewModel.setFloatingShortcutEnabled(it) },
                  onResumeDocument = { doc ->
                    viewModel.openNoteInReader(doc, autoPlay = true, resumeLastPosition = true)
                    navigateToTopTab(ReaderRoute)
                  },
                  onOpenLibrary = { navController.navigate(LibraryRoute) },
                  onOpenReader = { navigateToTopTab(ReaderRoute) },
                  onOpenTranslation = { navigateToTopTab(TranslationRoute) },
                  onOpenCoach = { navigateToTopTab(CoachRoute) },
                  onOpenPractice = { navController.navigate(PracticeRoute) },
                  onOpenVocabulary = { navController.navigate(VocabularyRoute) },
                  onOpenProgress = { navController.navigate(ProgressRoute) },
                  onOpenFloatingSettings = { navController.navigate(FloatingSettingsRoute) },
                  onOpenStudio = { navController.navigate(StudioRoute) },
                  onOpenSettings = { navigateToTopTab(SettingsRoute) },
                )
              }

              composable<LibraryRoute> {
                LibraryScreen(
                  notes = notes,
                  settings = settings,
                  isImporting = isImporting,
                  importBanner = importBanner,
                  onBack = { navController.popBackStack() },
                  onDismissImportBanner = { viewModel.clearImportBanner() },
                  onOpenNote = { note, autoPlay ->
                    viewModel.openNoteInReader(note, autoPlay, resumeLastPosition = true)
                    navigateToTopTab(ReaderRoute)
                  },
                  onSaveNote = { id, title, content, cat, lang ->
                    viewModel.saveNote(id, title, content, cat, lang)
                  },
                  onDeleteNote = { viewModel.deleteNote(it) },
                  onImportDocumentUri = { uri ->
                    viewModel.importDocumentUri(uri) { navigateToTopTab(ReaderRoute) }
                  },
                  onImportImageUri = { uri ->
                    viewModel.importImageForOcr(uri) { navigateToTopTab(ReaderRoute) }
                  },
                  onQuickReadText = { title, content ->
                    viewModel.readAdHocText(title, content, autoPlay = true)
                    navigateToTopTab(ReaderRoute)
                  },
                  onToggleFloatingShortcut = { viewModel.setFloatingShortcutEnabled(it) },
                  onOpenSettings = { navController.navigate(FloatingSettingsRoute) },
                )
              }

              composable<ReaderRoute> {
                ReaderScreen(
                  state = sessionState,
                  preferredGender = preferredGender,
                  inlineSheetState = inlineSheetState,
                  onJumpToSentence = { viewModel.speechCoordinator.jumpToSentence(it, autoPlay = true) },
                  onReadParagraph = { viewModel.speechCoordinator.readParagraph(it) },
                  onToggleSkipHeadings = { viewModel.speechCoordinator.toggleSkipHeadingsAndRefs() },
                  onToggleRepeat = { viewModel.speechCoordinator.toggleRepeatCurrentSentence() },
                  onBookmark = { viewModel.speechCoordinator.bookmarkCurrentPosition() },
                  onSelectVolume = { viewModel.speechCoordinator.setVolume(it) },
                  onSelectGender = { viewModel.speechCoordinator.setGenderFilter(it) },
                  onSelectListenAudioMode = { viewModel.speechCoordinator.setListenAudioMode(it) },
                  onOpenSentenceAssist = { text, lang ->
                    viewModel.openSentenceAssistSheet(text, lang)
                  },
                  onExplainSheetPassage = { viewModel.explainCurrentSheetPassage() },
                  onDismissSentenceAssist = { viewModel.dismissSentenceAssistSheet() },
                  onSpeakTextSlowly = { text, lang ->
                    viewModel.speechCoordinator.speakOneShot(text, lang, speed = 0.75f)
                  },
                  onPracticeInCoach = { text, lang ->
                    viewModel.setCoachTarget(text, lang)
                    navigateToTopTab(CoachRoute)
                  },
                  onSavePhrase = { phrase, translation, lang, guide ->
                    viewModel.savePhraseToVocabulary(phrase, translation, lang, guide)
                  },
                  onNavigateToLibrary = { navController.navigate(LibraryRoute) },
                )
              }

              composable<TranslationRoute> {
                TranslationScreen(
                  inputText = translationInput,
                  sourceLang = translationSourceLang,
                  targetLang = translationTargetLang,
                  tone = translationTone,
                  uiState = translationUiState,
                  history = translations,
                  onInputChange = { viewModel.updateTranslationInput(it) },
                  onSelectLanguages = { src, tgt -> viewModel.setTranslationLanguages(src, tgt) },
                  onSwapLanguages = { viewModel.swapTranslationLanguages() },
                  onSelectTone = { viewModel.setTranslationTone(it) },
                  onTranslate = { viewModel.runTranslation() },
                  onListenText = { text, lang -> viewModel.speechCoordinator.speakOneShot(text, lang) },
                  onPracticeInCoach = { text, lang ->
                    viewModel.setCoachTarget(text, lang)
                    navigateToTopTab(CoachRoute)
                  },
                  onSavePhrase = { phrase, translation, lang, guide, notesText ->
                    viewModel.savePhraseToVocabulary(phrase, translation, lang, guide, notesText)
                  },
                  onDeleteHistoryItem = { viewModel.deleteTranslationHistoryItem(it) },
                  onClearHistory = { viewModel.clearAllTranslations() },
                )
              }

              composable<CoachRoute> {
                CoachScreen(
                  targetSentence = coachTargetSentence,
                  targetLanguage = coachTargetLang,
                  captureState = captureState,
                  coachUiState = coachUiState,
                  onTargetChange = { viewModel.setCoachTarget(it, coachTargetLang) },
                  onSelectTargetDrill = { text, lang -> viewModel.setCoachTarget(text, lang) },
                  onSelectLanguage = { viewModel.setCoachTargetLanguage(it) },
                  onListenReference = { text, lang, speed ->
                    viewModel.speechCoordinator.speakOneShot(text, lang, speed)
                  },
                  onStartAudioRecord = { viewModel.startCoachAudioRecording() },
                  onStopAudioRecordAndAnalyze = { viewModel.stopCoachAudioRecordingAndAnalyze() },
                  onStartLiveAsr = { viewModel.startCoachLiveAsr() },
                  onStopLiveAsr = { viewModel.stopCoachLiveAsr() },
                  onPlayUserRecording = { path -> viewModel.speechCoachManager.playUserRecording(path) },
                )
              }

              composable<PracticeRoute> {
                PracticeScreen(
                  captureState = captureState,
                  freeSpeakTranscript = freeSpeakTranscript,
                  freeSpeakUiState = freeSpeakUiState,
                  onBack = { navController.popBackStack() },
                  onListenText = { text, lang, speed ->
                    viewModel.speechCoordinator.speakOneShot(text, lang, speed)
                  },
                  onTranscriptChange = { viewModel.updateFreeSpeakTranscript(it) },
                  onStartSpeakAsr = { lang -> viewModel.startFreeSpeakAsr(lang) },
                  onStartSpeakAudio = { viewModel.startFreeSpeakAudioRecording() },
                  onStopSpeakAudio = { lang -> viewModel.stopFreeSpeakAudioAndAnalyze(lang) },
                  onSubmitFreeSpeak = { lang -> viewModel.submitFreeSpeakForFeedback(targetLanguage = lang) },
                )
              }

              composable<VocabularyRoute> {
                VocabularyScreen(
                  phrases = savedPhrases,
                  onBack = { navController.popBackStack() },
                  onSavePhrase = { p, t, l, g, n ->
                    viewModel.savePhraseToVocabulary(p, t, l, g, n)
                  },
                  onCycleMastery = { item ->
                    val next = (item.masteryLevel + 1) % 4
                    viewModel.updatePhraseMastery(item.id, next)
                  },
                  onDeletePhrase = { viewModel.deletePhrase(it) },
                  onListenPhrase = { text, lang, speed ->
                    viewModel.speechCoordinator.speakOneShot(text, lang, speed)
                  },
                  onPracticePhrase = { text, lang ->
                    viewModel.setCoachTarget(text, lang)
                    navigateToTopTab(CoachRoute)
                  },
                )
              }

              composable<ProgressRoute> {
                ProgressScreen(
                  attempts = attempts,
                  savedPhrases = savedPhrases,
                  translations = translations,
                  onBack = { navController.popBackStack() },
                  onPracticeSentence = { sentence, lang ->
                    viewModel.setCoachTarget(sentence, lang)
                    navigateToTopTab(CoachRoute)
                  },
                  onPlayAttemptAudio = { path -> viewModel.speechCoachManager.playUserRecording(path) },
                  onClearVoiceData = { viewModel.clearAllVoiceData() },
                )
              }

              composable<FloatingSettingsRoute> {
                FloatingSettingsScreen(
                  settings = settings,
                  onBack = { navController.popBackStack() },
                  onToggleFloatingShortcut = { viewModel.setFloatingShortcutEnabled(it) },
                  onUpdateButtonSize = { viewModel.updateFloatingButtonSize(it) },
                  onUpdateButtonAlpha = { viewModel.updateFloatingButtonAlpha(it) },
                  onResetButtonPosition = { viewModel.resetFloatingButtonPosition() },
                  onSetDoubleTapToggle = { viewModel.setDoubleTapToTogglePlay(it) },
                )
              }

              composable<StudioRoute> {
                StudioScreen(
                  sessionState = sessionState,
                  settings = settings,
                  rules = rules,
                  bookmarks = bookmarks,
                  history = history,
                  isLanguageOffline = { viewModel.speechCoordinator.isLanguageOfflineAvailable(it) },
                  onInstallOfflineVoices = { viewModel.speechCoordinator.launchOfflineVoiceInstaller() },
                  onSelectVoice = { viewModel.selectVoice(it) },
                  onToggleFavoriteVoice = { viewModel.toggleFavoriteVoice(it) },
                  onTestVoice = { viewModel.testVoice(it) },
                  onSaveCloudConfig = { enabled, url -> viewModel.setCloudTtsConfig(enabled, url) },
                  onSaveRule = { term, repl, lang -> viewModel.savePronunciationRule(term, repl, lang) },
                  onToggleRule = { viewModel.togglePronunciationRule(it) },
                  onDeleteRule = { viewModel.deletePronunciationRule(it) },
                  onOpenBookmark = { bm ->
                    viewModel.openBookmarkInReader(bm, autoPlay = true)
                    navigateToTopTab(ReaderRoute)
                  },
                  onDeleteBookmark = { viewModel.deleteBookmark(it) },
                  onOpenHistory = { entry ->
                    viewModel.openHistoryInReader(entry, autoPlay = true)
                    navigateToTopTab(ReaderRoute)
                  },
                  onDeleteHistory = { viewModel.deleteHistoryEntry(it) },
                  onClearHistory = { viewModel.clearAllHistory() },
                  onBack = { navController.popBackStack() },
                )
              }

              composable<SettingsRoute> {
                SettingsScreen(
                  settings = settings,
                  onOpenFloatingSettings = { navController.navigate(FloatingSettingsRoute) },
                  onOpenVoicesStudio = { navController.navigate(StudioRoute) },
                  onSetDefaultLanguage = { viewModel.setDefaultLanguage(it) },
                  onSetEnglishAccent = { viewModel.setEnglishAccent(it) },
                  onSetGenderFilter = { viewModel.speechCoordinator.setGenderFilter(it) },
                  onSetAutoDetect = { viewModel.setAutoDetectLanguage(it) },
                  onSetMixedArabicEnglish = { viewModel.setMixArabicEnglishSwitching(it) },
                  onSetSpeed = { viewModel.speechCoordinator.setSpeed(it) },
                  onSetPitch = { viewModel.speechCoordinator.setPitch(it) },
                  onSetVolume = { viewModel.speechCoordinator.setVolume(it) },
                  onToggleSkipHeadings = { viewModel.speechCoordinator.toggleSkipHeadingsAndRefs() },
                  onSetOnlineAiEnabled = { viewModel.setOnlineAiEnabled(it) },
                  onSetReadingHistoryEnabled = { viewModel.setReadingHistoryEnabled(it) },
                  onSetSaveVoiceRecordingsConsent = { viewModel.setSaveVoiceRecordingsConsent(it) },
                  onClearHistory = { viewModel.clearAllHistory() },
                  onClearTranslations = { viewModel.clearAllTranslations() },
                  onClearVoiceData = { viewModel.clearAllVoiceData() },
                )
              }
            }
          }

          if (useNavRail) {
            Box(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
              MiniPlayerBar(
                state = sessionState,
                onPlayPause = { viewModel.speechCoordinator.togglePlayPause() },
                onStop = { viewModel.speechCoordinator.stop() },
                onPrevious = { viewModel.speechCoordinator.previousSentence() },
                onNext = { viewModel.speechCoordinator.nextSentence() },
                onRepeatToggle = { viewModel.speechCoordinator.toggleRepeatCurrentSentence() },
                onSelectSpeed = { viewModel.speechCoordinator.setSpeed(it) },
                onSelectVolume = { viewModel.speechCoordinator.setVolume(it) },
                onSelectLanguage = { viewModel.speechCoordinator.selectLanguageOverride(it) },
                onBookmark = { viewModel.speechCoordinator.bookmarkCurrentPosition() },
                onTranslateCurrent = {
                  val sentence = sessionState.currentSentence
                  if (sentence != null) {
                    viewModel.openSentenceAssistSheet(sentence.rawText, sentence.detectedLanguageTag)
                    navigateToTopTab(ReaderRoute)
                  }
                },
                onOpenReader = { navigateToTopTab(ReaderRoute) },
              )
            }
          }
        }
      }
    }
  }
}
