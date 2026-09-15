# wassndis 🔍

[![Android](https://img.shields.io/badge/Platform-Android%20(API%2037%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Google Gemini](https://img.shields.io/badge/AI-Google%20Gemini%20Flash%20%2F%20Pro-8E75B2?logo=google&logoColor=white)](https://ai.google.dev)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> **"Was'n dis?"** *(German slang for "What is this?")* — An intelligent, high-precision visual recognition and inspection Android app powered by Google Gemini.

---

## 📖 Overview

**wassndis** transforms your smartphone camera into an expert appraiser and technical encyclopedia. Point your camera at an unknown tool, vintage device, biological specimen, electronic component, or household object, and the app instantly identifies what it is, how it works, and every key technical attribute.

Unlike standard image captioning tools that state the obvious (e.g., *"A photo of an object on a table"*), **wassndis** strictly zeroes in on the **focal object**, delivering structured, expert-level assessments and letting you conduct an interactive follow-up conversation about anything in the frame.

---

## ✨ Features

### 🔍 Deep Object Identification & Appraisal
- **Specific Identification**: Uncovers exact manufacturer, model name, series number, and technical taxonomy (e.g., *De'Longhi Dedica EC 685*, *Bosch Professional GSR 18V-55*, *Monstera Deliciosa*).
- **Comprehensive Technical Breakdown**:
  - Exact designation & category
  - Primary function & operating principle
  - Visible components, controls, and mechanics
  - Materials, finish, and build quality
  - Visible wear, condition, and notable markings / rating plates
- **Actionable Summary**: Compact overview plus in-depth expert report.

### 💬 Interactive Visual Q&A
- **Ask Anything About the Photo**: Wondering how to operate a machine, whether an antique is authentic, what a specific switch does, or how to clean a tool? Ask directly in the detail view.
- **Context-Aware Multi-Turn Chat**: Follow-up questions maintain memory of earlier queries and inspect the original high-resolution image.
- **One-Tap Suggestion Chips**: Quick prompts for valuation, maintenance, worn parts, or rating-plate locations.

### ⚡ Lightning-Fast System Integration
- **Quick Settings Tile**: Add the **wassndis Scan** tile to your Android Quick Settings panel to launch the camera from anywhere in one tap.
- **Home Screen App Widget**: Dedicated 1x1 quick-action widget for instant capture.
- **App Shortcuts**: Long-press the app icon on your launcher for direct scan access.
- **Camera & Gallery Support**: Snap pictures directly or import existing photos from your device library.
- **Standard Intent Handlers**: Responds to `IMAGE_CAPTURE` and custom `ACTION_SCAN` intents.

### 🛠️ Configurable AI Engine
- **Model Switching**: Switch between `gemini-3.8-flash`, `gemini-2.5-flash`, `gemini-2.5-pro`, or enter custom model IDs on the fly.
- **Bring Your Own Key (BYOK)**: Connect securely using your own Google AI Studio API key without intermediary servers or subscriptions.

### 🔒 Privacy-Centric & Fully Local Storage
- **On-Device Database**: Scans, metadata, and Q&A threads are stored locally in the private app sandbox (`analyses.json` and internal image cache).
- **Direct REST Communication**: Calls Google's Generative Language API directly from your phone over HTTPS; no third-party proxies or analytics tracking.
- **Smart Image Preprocessing**: Automatic EXIF orientation normalization, capture timestamp preservation, and adaptive image scaling (up to 1568px) to optimize bandwidth.

### 🎨 Modern Material 3 Design
- Built from the ground up with **100% Jetpack Compose**.
- Full **Material You (Dynamic Color)** support on Android 12+.
- Smooth enter/exit transition animations, edge-to-edge layout, and comprehensive search filtering.
- One-click copy and native Android share sheet integration.

---

## 🏗️ Architecture & Tech Stack

```
com.example.wassndis
├── data/
│   ├── AnalysisItem.kt           # Data models (AnalysisItem, QaItem, JSON serialization)
│   └── AnalysisRepository.kt     # Local file persistence, SharedPreferences & EXIF handling
├── service/
│   ├── GeminiService.kt          # OkHttp REST client communicating with Google Gemini API
│   └── WassndisTileService.kt    # Android Quick Settings tile service
├── ui/
│   ├── MainViewModel.kt          # UI state management & business logic (StateFlow)
│   ├── screens/
│   │   ├── OverviewScreen.kt     # Gallery feed, search bar, photo capture triggers
│   │   ├── DetailScreen.kt       # Full technical report, zoomable image, and Q&A chat
│   │   └── ApiKeyDialog.kt       # API key and model selection dialog
│   └── theme/                    # Material 3 typography, color schemes & themes
├── widget/
│   └── WassndisScanWidgetProvider.kt # Home screen 1-tap scan launcher widget
└── MainActivity.kt               # Single-activity navigation & quick capture intent routing
```

| Layer / Component | Technology | Description |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.2.10 | Modern, expressive, and null-safe |
| **UI Toolkit** | Jetpack Compose + Material 3 | Declarative UI with Dynamic Color / Material You |
| **Asynchrony** | Kotlin Coroutines & StateFlow | Reactive and lifecycle-aware state management |
| **Networking** | OkHttp 4.12.0 | Lightweight direct HTTPS client for Gemini REST API |
| **Image Loading** | Coil Compose 2.7.0 | High-performance asynchronous image loader |
| **Target SDK** | Android 15 / 16 (API 37) | Latest Android platform capabilities and edge-to-edge support |

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** Ladybug (or newer recommended)
- **JDK 11** or higher
- An Android device or emulator running **Android API 37+**
- A **Google Gemini API Key** (Free tier available at [Google AI Studio](https://aistudio.google.com/))

### Installation & Build

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Carcophan/wassndis.git
   cd wassndis
   ```

2. **Open in Android Studio**:
   - Open Android Studio, choose **File > Open**, and select the project directory.
   - Wait for Gradle to sync dependencies.

3. **Build via Command Line**:
   ```bash
   # Assemble debug APK
   ./gradlew assembleDebug

   # Install directly onto a connected device or emulator
   ./gradlew installDebug
   ```

---

## ⚙️ Configuration & Usage

### 1. Configure Your Gemini API Key
1. Launch **wassndis** on your device.
2. If no key is configured, the **Gemini Einstellungen** dialog appears automatically (or tap the key icon in the top app bar).
3. Paste your API key from [Google AI Studio](https://aistudio.google.com/).
4. Choose your preferred model (e.g., `gemini-3.8-flash` for high speed and accuracy, or `gemini-2.5-pro` for complex reasoning).
5. Tap **Speichern**.

### 2. Scanning an Object
- **Camera Scan**: Tap the floating camera button to snap an object in good lighting.
- **Gallery Import**: Tap the gallery button to select an existing photo.
- Within seconds, the analysis report will appear, classifying the item and describing its functions and components.

### 3. Asking Follow-Up Questions
- Scroll to the bottom of any item's detail view to access **Fragen an Gemini stellen**.
- Tap a quick-suggestion chip (e.g., *"Wie viel ist das ungefähr wert?"*, *"Wie reinigt und pflegt man das?"*) or type a custom question.
- Responses will be saved under the item and can be revisited or deleted at any time.

### 4. Adding Quick Settings Tile & Widget
- **Quick Settings**: Swipe down twice from the top of your screen, tap the edit (pencil) icon, find **Что это / wassndis Scan**, and drag it into your active tiles.
- **Widget**: Long-press on your home screen, choose **Widgets**, scroll to **wassndis**, and place the 1-tap scan button on your home screen.

---

## 🛡️ Privacy & Security

- **No Remote Backend**: wassndis has no proprietary backend or intermediary servers.
- **Direct Encryption**: Network requests go straight to Google's official Gemini endpoint (`generativelanguage.googleapis.com`) using HTTPS.
- **Safe API Key Storage**: The API key is stored securely in private app preferences on your device.

---

## 📄 License

This project is licensed under the terms specified in the repository. See [LICENSE](LICENSE) for details.
