# LinguaFloat — AI Floating Language Reader, Translator & Pronunciation Coach

**LinguaFloat** is a native Kotlin + Jetpack Compose Android application designed for bilingual **Arabic ↔ English** (American `en-US` and British `en-GB`) reading, contextual translation, listening, and pronunciation coaching across books, PDFs, EPUBs, scanned documents, and other Android apps.

---

## Core Features Implemented

1. **System-Wide Floating Assistant (`FloatingShortcutService` + `AccessibilityService` + `ACTION_PROCESS_TEXT` + `ACTION_SEND`)**
   - Movable, resizable floating bubble (`SYSTEM_ALERT_WINDOW`) with customizable size, transparency, edge-snapping, and persistent coordinates.
   - Expands into a compact **6-action icon-only floating toolbar** with tooltips and accessibility descriptions:
     1. **Read Aloud** (play/pause sentence-synchronized TTS)
     2. **Translate** (inline Arabic ↔ English contextual translation card + spoken audio)
     3. **Listen to Pronunciation** (0.75x slow clear pronunciation)
     4. **Practice Speaking** (deep-links to Pronunciation Coach)
     5. **Correct Accent** (deep-links to Word-by-Word Accent Evaluation)
     6. **Open Full LinguaFloat**
     Plus Minimize, Hide, and Close controls.
   - Captures selected text via **Android AccessibilityService** (explicit tap only, strictly ignores `isPassword` / sensitive fields), **ACTION_PROCESS_TEXT** (`"LinguaFloat: Read / Translate"`), and **Share Sheet (`ACTION_SEND`)** for text, PDFs, EPUBs, and images.

2. **Smart Book, PDF, EPUB & OCR Reader**
   - Imports **PDF**, **EPUB**, and **TXT** files via Android Storage Access Framework (`OpenDocument`), plus scanned images via the zero-permission Android Photo Picker (`PickVisualMedia`).
   - Hybrid OCR pipeline: on-device ML Kit Latin OCR (`TextRecognition`) with **Gemini Vision OCR** fallback (`LinguaAiService.extractArabicAndEnglishOcr`) for Arabic and bilingual scanned pages.
   - Live sentence-by-sentence highlighting, RTL/LTR layout handling, paragraph jumping, speed presets (`0.5x–3.0x`), pitch & volume controls, voice gender filtering, **Listen Mode (Original vs Translated)**, inline sentence **Translate / Explain** bottom sheet, bookmarks, and automatic resume-from-last-sentence persistence.

3. **Contextual Translation Engine (`TranslationScreen` + `LinguaAiService`)**
   - Powered by **Firebase AI Logic (`gemini-3.8-flash`)** with structured JSON schemas.
   - Auto-detects Arabic/English source text, supports tone selection (`Natural`, `Academic`, `Medical`, `Conversational`), displays side-by-side RTL/LTR results, literal vs contextual nuance notes, natural alternatives, and key vocabulary/idioms breakdowns.
   - Playback for both original and translated text, clipboard copy, system share, Save to Vocabulary, and persistent translation history.

4. **AI Pronunciation & Accent Coach (`CoachScreen` + `PracticeScreen` + `SpeechCoachManager`)**
   - Supports **Audio Recording (`.m4a` via `MediaRecorder`)** analyzed multimodally by Gemini and **Live Speech Check (on-device `SpeechRecognizer` ASR)**.
   - Transparently separates **Verified Measurements** (on-device ASR word match percentage, measured WPM, audio analyzed indicator) from **Estimated AI Coaching** (overall score, word-by-word status & phonetic tips, clarity/rhythm feedback, and Arabic ↔ English L1 interference tips such as `/p/` vs `/b/`, `/v/` vs `/f/`, `/θ/` vs `/s/`, and Arabic ع/غ/ح/خ/ق/ض).
   - **Listen Mode** & **Speak Mode (Free Speaking AI Partner)** for conversational Arabic/English practice.

5. **Vocabulary, Learning Progress & Privacy Controls**
   - Saved phrases with mastery tracking (`0–3`), slow pronunciation replay, and quick practice launch.
   - Progress dashboard with Compose `Canvas` score trend chart and speaking attempt history.
   - Granular privacy controls: Online AI toggle, Reading History toggle, opt-in local voice recording consent (cached recordings are deleted immediately after analysis when disabled), and one-tap Clear History / Clear Translations / Delete All Voice Data actions.

---

## Architecture & Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material Design 3, Navigation Compose (`@Serializable` type-safe routes), Adaptive `NavigationBar` / `NavigationRail`
- **AI & Security**: Firebase AI Logic (`com.google.firebase:firebase-ai`) with `gemini-3.8-flash` and **Firebase App Check** (`DebugAppCheckProviderFactory` in debug, `ReCaptchaEnterpriseProviderFactory` in release) — **no hardcoded or user-supplied Gemini API key required**
- **Local Storage**: Room (`linguafloat_database.db` v2) + Jetpack DataStore (`ReaderSettingsStore`)
- **Speech & Capture**: Android `TextToSpeech` + `CloudTtsProviderAdapter` + `MediaRecorder` / `SpeechRecognizer` + ML Kit Text Recognition + Android `PdfRenderer` + ZIP/OPF EPUB parser

---

## Build & Installation Instructions

### Prerequisites
- Android Studio Ladybug / Koala+ or Gradle CLI with JDK 17 & Android SDK 36
- Configured `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` (already provisioned for this workspace)

### Build Debug APK & Run Tests
```bash
# Run unit and Robolectric tests
./gradlew :app:testDebugUnitTest

# Build debug APK
./gradlew :app:assembleDebug
```
*(In headless environments without `gradlew`, use `gradle :app:testDebugUnitTest` and `gradle :app:assembleDebug`.)*

Output APK path:
`app/build/outputs/apk/debug/app-debug.apk`

Install onto a connected Android device or emulator:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Known Limitations & Honest Technical Boundaries

- **Third-Party App Capture Restrictions**: Android apps that set `FLAG_SECURE`, render text onto custom WebGL/Canvas surfaces without accessibility nodes, or mark fields as passwords/sensitive data cannot be read via `AccessibilityService`. Use Android's `"LinguaFloat: Read / Translate"` text-selection menu or Share Sheet (`ACTION_SEND`) in those cases.
- **Offline vs Online Capabilities**: Text-to-Speech, PDF/EPUB/TXT extraction, Latin OCR, pronunciation dictionary rules, and on-device `SpeechRecognizer` word matching work offline; contextual AI translation, passage explanation, Arabic Vision OCR fallback, and multimodal Gemini audio pronunciation coaching require internet access and Firebase AI Logic + App Check verification.
- **Pronunciation Scoring Transparency**: Word-match percentage and WPM from `SpeechRecognizer` are measured metrics; phoneme articulation, stress, and accent scores from Gemini are AI coaching estimates rather than clinical acoustic instrumentation.
- **EPUB Support**: Supports standard DRM-free `.epub` archives (OPF spine + XHTML/HTML chapters). DRM-protected `.epub` files cannot be decrypted.
