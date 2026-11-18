# JARVIS VIP – Advanced Personal AI Assistant (Android)

## Key Capabilities
- **AI Brain (OpenAI ChatGPT via Secure Backend Proxy)**: Connects to `/backend` (`POST /api/jarvis/chat`) authenticated with Firebase Auth ID tokens. Zero OpenAI API keys are packaged inside the APK.
- **Bilingual Voice Control (Hindi + English)**: Female AI voice (`TextToSpeech`), Hindi (`hi-IN`) & English (`en-IN`) speech recognition (`SpeechRecognizer`), animated holographic core + waveform, and opt-in hands-free `"Hey JARVIS"` foreground microphone service (`JarvisVoiceService`).
- **Phone Control & Social Studio**: Contacts search, confirmed phone calls, confirmed SMS/WhatsApp drafts, Facebook Social Studio (zero-permission Android Photo Picker + confirmed sharing), app launcher, alarms/timers, calendar events, volume/Wi-Fi/Bluetooth/Settings shortcuts, opt-in Notification Reader, opt-in Accessibility navigation, and live Battery/Storage/RAM telemetry.
- **Cloud Persistence (Firestore + Google Sign-In)**: Tasks, smart reminders, daily schedules, birthday reminders, approved AI preferences, conversation logs, and automation audit trails scoped strictly per authenticated user.

---

## 1. Secure ChatGPT Backend Proxy Setup (`/backend`)
1. `cd backend && npm install`
2. Copy `.env.example` to `.env` and set `OPENAI_API_KEY` (and `JARVIS_SHARED_TOKEN` or `GOOGLE_APPLICATION_CREDENTIALS` for Firebase ID token verification).
3. Start locally (`npm start`) or deploy to Cloud Run / Render / Railway over HTTPS.
4. In the Android app **Settings > OpenAI ChatGPT Secure Backend**, paste your deployed HTTPS backend URL.

---

## 2. Building & Downloading APKs (`app-debug.apk` & `app-release.apk`)
### Option A: GitHub Actions CI
Push to `main` (or trigger `Actions > Android CI Build > Run workflow`). Download `jarvis-vip-debug-apk` (`app-debug.apk`) or `jarvis-vip-release-apk` from the workflow run Artifacts.

### Option B: Local / Android Studio
- **Debug APK**: `gradle :app:assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK** (with upload keystore env vars `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`):
  `gradle :app:assembleRelease` -> `app/build/outputs/apk/release/app-release.apk`

---

## 3. Samsung Galaxy A51 Installation & Optimization Guide
1. Transfer `app-debug.apk` (or signed `app-release.apk`) to your Galaxy A51.
2. Tap the APK in **My Files**, allow **Install unknown apps** for My Files when prompted, and tap **Install**.
3. Open **Jarvis VIP** and sign in with Google.
4. For reliable **Hey JARVIS** hands-free mode on Samsung One UI:
   - Open **Settings > Galaxy A51 Battery Optimization** inside Jarvis VIP -> set Jarvis VIP to **Unrestricted**.
   - In Samsung **Device Care > Battery > Background usage limits**, ensure Jarvis VIP is not in **Sleeping apps**.
   - Optional permissions (Microphone, Contacts, Phone, SMS, Camera, Notification Access, Accessibility) are only requested when you explicitly enable or trigger those features.
