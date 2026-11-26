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
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.capture.SystemIntegrationHelper
import com.example.ui.ClinNoteViewModel
import com.example.ui.components.MiniPlayerBar
import com.example.ui.navigation.LibraryRoute
import com.example.ui.navigation.ReaderRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.StudioRoute
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.ClinNoteTheme

class MainActivity : ComponentActivity() {

  private var pendingTab by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    pendingTab = intent?.getStringExtra(EXTRA_OPEN_TAB)

    val app = application as ClinNoteApplication
    if (SystemIntegrationHelper.canDrawOverlays(this)) {
      // Restore floating shortcut if user left it enabled
      // Checked safely in background via viewmodel/settings
    }

    setContent {
      ClinNoteTheme {
        val vm: ClinNoteViewModel = viewModel()
        ClinNoteRootScreen(
          viewModel = vm,
          pendingTab = pendingTab,
          onPendingTabConsumed = { pendingTab = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingTab = intent.getStringExtra(EXTRA_OPEN_TAB)
  }

  companion object {
    const val EXTRA_OPEN_TAB = "extra_open_tab"
    const val TAB_LIBRARY = "library"
    const val TAB_READER = "reader"
    const val TAB_STUDIO = "studio"
    const val TAB_SETTINGS = "settings"
  }
}

@Composable
fun ClinNoteRootScreen(
  viewModel: ClinNoteViewModel,
  pendingTab: String?,
  onPendingTabConsumed: () -> Unit,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val notes by viewModel.notes.collectAsStateWithLifecycle()
  val rules by viewModel.pronunciationRules.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val history by viewModel.history.collectAsStateWithLifecycle()
  val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
  val importBanner by viewModel.importBanner.collectAsStateWithLifecycle()

  val context = androidx.compose.ui.platform.LocalContext.current
  LaunchedEffect(settings.floatingShortcutEnabled) {
    if (settings.floatingShortcutEnabled && SystemIntegrationHelper.canDrawOverlays(context)) {
      SystemIntegrationHelper.startFloatingShortcut(context)
    }
  }

  LaunchedEffect(pendingTab) {
    when (pendingTab) {
      MainActivity.TAB_READER -> {
        navController.navigate(ReaderRoute) { launchSingleTop = true }
        onPendingTabConsumed()
      }
      MainActivity.TAB_SETTINGS -> {
        navController.navigate(SettingsRoute) { launchSingleTop = true }
        onPendingTabConsumed()
      }
      MainActivity.TAB_STUDIO -> {
        navController.navigate(StudioRoute) { launchSingleTop = true }
        onPendingTabConsumed()
      }
      MainActivity.TAB_LIBRARY -> {
        navController.navigate(LibraryRoute) { launchSingleTop = true }
        onPendingTabConsumed()
      }
    }
  }

  val navigateToTab: (Any) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(LibraryRoute) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 700.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        if (!useNavRail) {
          Column(
            modifier =
              Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
          ) {
            MiniPlayerBar(
              state = sessionState,
              onPlayPause = { viewModel.speechCoordinator.togglePlayPause() },
              onStop = { viewModel.speechCoordinator.stop() },
              onPrevious = { viewModel.speechCoordinator.previousSentence() },
              onNext = { viewModel.speechCoordinator.nextSentence() },
              onRepeatToggle = { viewModel.speechCoordinator.toggleRepeatCurrentSentence() },
              onSelectSpeed = { viewModel.speechCoordinator.setSpeed(it) },
              onSelectLanguage = { viewModel.speechCoordinator.selectLanguageOverride(it) },
              onBookmark = { viewModel.speechCoordinator.bookmarkCurrentPosition() },
              onOpenReader = { navigateToTab(ReaderRoute) },
            )
            NavigationBar {
              NavigationBarItem(
                selected = currentDestination?.hasRoute<LibraryRoute>() == true,
                onClick = { navigateToTab(LibraryRoute) },
                icon = {
                  Icon(
                    if (currentDestination?.hasRoute<LibraryRoute>() == true)
                      Icons.Rounded.FolderCopy
                    else Icons.Outlined.FolderCopy,
                    contentDescription = "Notes & PDFs",
                  )
                },
                label = { Text("Notes") },
                modifier = Modifier.testTag("nav_library"),
              )
              NavigationBarItem(
                selected = currentDestination?.hasRoute<ReaderRoute>() == true,
                onClick = { navigateToTab(ReaderRoute) },
                icon = {
                  Icon(
                    if (currentDestination?.hasRoute<ReaderRoute>() == true)
                      Icons.AutoMirrored.Rounded.MenuBook
                    else Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = "Smart Reader",
                  )
                },
                label = { Text("Reader") },
                modifier = Modifier.testTag("nav_reader"),
              )
              NavigationBarItem(
                selected = currentDestination?.hasRoute<StudioRoute>() == true,
                onClick = { navigateToTab(StudioRoute) },
                icon = {
                  Icon(
                    if (currentDestination?.hasRoute<StudioRoute>() == true)
                      Icons.Rounded.GraphicEq
                    else Icons.Outlined.GraphicEq,
                    contentDescription = "Voices & Dictionary",
                  )
                },
                label = { Text("Voices") },
                modifier = Modifier.testTag("nav_studio"),
              )
              NavigationBarItem(
                selected = currentDestination?.hasRoute<SettingsRoute>() == true,
                onClick = { navigateToTab(SettingsRoute) },
                icon = {
                  Icon(
                    if (currentDestination?.hasRoute<SettingsRoute>() == true)
                      Icons.Rounded.Tune
                    else Icons.Outlined.Tune,
                    contentDescription = "Reader Settings",
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
              selected = currentDestination?.hasRoute<LibraryRoute>() == true,
              onClick = { navigateToTab(LibraryRoute) },
              icon = { Icon(Icons.Rounded.FolderCopy, contentDescription = "Notes") },
              label = { Text("Notes") },
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<ReaderRoute>() == true,
              onClick = { navigateToTab(ReaderRoute) },
              icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Reader") },
              label = { Text("Reader") },
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<StudioRoute>() == true,
              onClick = { navigateToTab(StudioRoute) },
              icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = "Voices") },
              label = { Text("Voices") },
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<SettingsRoute>() == true,
              onClick = { navigateToTab(SettingsRoute) },
              icon = { Icon(Icons.Rounded.Tune, contentDescription = "Settings") },
              label = { Text("Settings") },
            )
          }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
          Box(modifier = Modifier.weight(1f).widthIn(max = 860.dp).fillMaxWidth()) {
            NavHost(navController = navController, startDestination = LibraryRoute) {
              composable<LibraryRoute> {
                LibraryScreen(
                  notes = notes,
                  settings = settings,
                  isImporting = isImporting,
                  importBanner = importBanner,
                  onDismissImportBanner = { viewModel.clearImportBanner() },
                  onOpenNote = { note, autoPlay ->
                    viewModel.openNoteInReader(note, autoPlay)
                    navigateToTab(ReaderRoute)
                  },
                  onSaveNote = { id, title, content, cat, lang ->
                    viewModel.saveNote(id, title, content, cat, lang)
                  },
                  onDeleteNote = { viewModel.deleteNote(it) },
                  onImportDocumentUri = { uri ->
                    viewModel.importDocumentUri(uri) { navigateToTab(ReaderRoute) }
                  },
                  onImportImageUri = { uri ->
                    viewModel.importImageForOcr(uri) { navigateToTab(ReaderRoute) }
                  },
                  onQuickReadText = { title, content ->
                    viewModel.readAdHocText(title, content, autoPlay = true)
                    navigateToTab(ReaderRoute)
                  },
                  onToggleFloatingShortcut = { viewModel.setFloatingShortcutEnabled(it) },
                  onOpenSettings = { navigateToTab(SettingsRoute) },
                )
              }

              composable<ReaderRoute> {
                ReaderScreen(
                  state = sessionState,
                  onJumpToSentence = { viewModel.speechCoordinator.jumpToSentence(it, autoPlay = true) },
                  onReadParagraph = { viewModel.speechCoordinator.readParagraph(it) },
                  onToggleSkipHeadings = { viewModel.speechCoordinator.toggleSkipHeadingsAndRefs() },
                  onToggleRepeat = { viewModel.speechCoordinator.toggleRepeatCurrentSentence() },
                  onBookmark = { viewModel.speechCoordinator.bookmarkCurrentPosition() },
                  onNavigateToLibrary = { navigateToTab(LibraryRoute) },
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
                    navigateToTab(ReaderRoute)
                  },
                  onDeleteBookmark = { viewModel.deleteBookmark(it) },
                  onOpenHistory = { entry ->
                    viewModel.openHistoryInReader(entry, autoPlay = true)
                    navigateToTab(ReaderRoute)
                  },
                  onDeleteHistory = { viewModel.deleteHistoryEntry(it) },
                  onClearHistory = { viewModel.clearAllHistory() },
                )
              }

              composable<SettingsRoute> {
                SettingsScreen(
                  settings = settings,
                  onToggleFloatingShortcut = { viewModel.setFloatingShortcutEnabled(it) },
                  onUpdateButtonSize = { viewModel.updateFloatingButtonSize(it) },
                  onUpdateButtonAlpha = { viewModel.updateFloatingButtonAlpha(it) },
                  onResetButtonPosition = { viewModel.resetFloatingButtonPosition() },
                  onSetDoubleTapToggle = { viewModel.setDoubleTapToTogglePlay(it) },
                  onSetDefaultLanguage = { viewModel.setDefaultLanguage(it) },
                  onSetAutoDetect = { viewModel.setAutoDetectLanguage(it) },
                  onSetMixedArabicEnglish = { viewModel.setMixArabicEnglishSwitching(it) },
                  onSetSpeed = { viewModel.speechCoordinator.setSpeed(it) },
                  onSetPitch = { viewModel.speechCoordinator.setPitch(it) },
                  onToggleSkipHeadings = { viewModel.speechCoordinator.toggleSkipHeadingsAndRefs() },
                  onSetReadingHistoryEnabled = { viewModel.setReadingHistoryEnabled(it) },
                  onClearHistory = { viewModel.clearAllHistory() },
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
                onSelectLanguage = { viewModel.speechCoordinator.selectLanguageOverride(it) },
                onBookmark = { viewModel.speechCoordinator.bookmarkCurrentPosition() },
                onOpenReader = { navigateToTab(ReaderRoute) },
              )
            }
          }
        }
      }
    }
  }
}
