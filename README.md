# JARVIS — Cognitive Voice Assistant for Android

A hybrid on-device & cloud-connected voice assistant built with Jetpack Compose. By default, JARVIS operates in **Online Mode** connected to Cloud LLM providers (NVIDIA Nemotron, Groq, OpenRouter, Gemini, Ollama) for rich conversational AI, while retaining full on-device hardware automation. Users can switch to **Offline Mode** at any time for 100% on-device local execution without internet. Supports **Hinglish and English** natively.

---

## What JARVIS Does (Features at a Glance)

| Feature | What it does | Mode |
|---------|-------------|------|
| **Connectivity** | Online by default with cloud LLM reasoning; explicit Offline mode toggle | 🌐 Online Default / 📴 Offline Switchable |
| **Wake Word** | "Hey JARVIS", "Jarvis", "ജാര്‍വിസ്" — microphone listens continuously | ✅ Offline (ONNX) |
| **Voice Commands** | Control phone from voice — open apps, adjust settings, play music | ✅ On-Device Deterministic |
| **Context Awareness** | Remembers past conversations, knows your routine | ✅ On-device SQLite + Vector Search |
| **Cloud Brain** | Deep reasoning via NVIDIA, Groq, OpenRouter, Gemini, Ollama | 🌐 Cloud LLM Gateway |
| **Device Control** | Torch, WiFi, Bluetooth, volume, brightness, DND, rotation lock | ✅ Offline |
| **Smart Calling** | Call contacts with speakerphone: "Jarvis, Papa ko call lagao speaker par" | ✅ Offline |
| **Auto Messaging** | WhatsApp messages, SMS — send by voice, no typing | ✅ Offline (intent-based) |
| **WhatsApp Voice Notes** | "Jarvis, WhatsApp par Papa ko voice note bhejo" | ✅ Offline |
| **App Automation** | "YouTube par tech videos chalao", "Chrome kholo" | ✅ Offline |
| **System Toggles** | WiFi, Bluetooth, Torch, DND, Rotation lock, Ringer mode | ✅ Offline |
| **Tasker Integration** | Geofence-based routines — arrive home → WiFi + Bluetooth auto-toggle | ✅ Offline |
| **Battery Alerts** | "Battery 15% se kam hai" — proactive voice warning | ✅ Offline |
| **Routine Management** | Morning routine, night routine — auto-run on schedule | ✅ Offline |
| **Media Control** | Play/pause/next/prev, volume control, open music apps | ✅ Offline |
| **Screen Reading** | "Screen padho" — reads on-screen content, suggests replies | ✅ Offline |
| **Live Translation** | Translate Hinglish↔English or any language in real-time | ✅ Cloud API |
| **Location-Based Reminders** | "Jab main market jaun, dhoodh lena yaad dilana" | ✅ Offline (GPS geofence) |
| **Offline Mode** | 100% on-device operation switchable via Home Screen & Settings | ✅ Manual Toggle |
| **Hands-Free Mode** | Always listening, no button press needed | ✅ Always On |
| **UI Customization** | Futuristic neon hologram widget with voice status visualization | ✅ On-device |

---

## Quick Start

### Prerequisites
- Android 8.0+ (API 26+)
- Microphone permission
- Accessibility permission (for screen reading + app automation)
- Internet **optional** — JARVIS works 100% offline

### Setup (First Time)
1. Install the APK
2. Grant **Microphone** permission
3. Grant **Accessibility** permission (for screen reading + automation)
4. Speak: "Hey JARVIS" — assistant activates
5. Try: "YouTube kholo" or "Torch on karo"

### Permissions Explained
| Permission | Why JARVIS needs it |
|------------|---------------------|
| Microphone | Wake word detection + voice commands |
| Accessibility | Screen reading, app automation, UI interaction |
| Phone | Make calls (speakerphone option) |
| Contacts | Lookup contact names for calls/messages |
| SMS | Send SMS, read inbox |
| Calendar (optional) | Calendar reminders, daily briefing |
| Location (optional) | Geofence routines, location reminders |
| Notifications (optional) | Read incoming notifications |

---

## Voice Commands — Complete Reference

### 🎤 Wake & Activation
| Command | Action |
|---------|--------|
| "Hey JARVIS" | Wakes up the assistant (always listening) |
| "Jarvis, suno" | Activates listening mode |
| "Jarvis, offline mode chalao" | Switches to full offline mode |
| "Jarvis, mic off" | Disables microphone listening |

### 📱 Device Control
| Command | Action |
|---------|--------|
| "Torch on karo" / "Flashlight on" | Turn on torch |
| "Torch off karo" | Turn off torch |
| "WiFi on kar" / "WiFi chalu" | Turn on WiFi |
| "WiFi band kar" | Turn off WiFi |
| "Bluetooth on" / "Bluetooth chalu" | Turn on Bluetooth |
| "Bluetooth band" | Turn off Bluetooth |
| "Volume badhao" / "Volume up" | Increase volume |
| "Volume kam kar" / "Volume down" | Decrease volume |
| "Volume 50% kar" | Set volume to 50% |
| "Brightness badhao" | Increase brightness |
| "Brightness kam kar" | Decrease brightness |
| "Brightness half kar" | Set brightness to 50% |
| "Brightness full kar" | Set brightness to 100% |
| "Screen rotate band karo" / "Rotation lock on" | Lock screen rotation |
| "Rotation lock kholo" / "Auto rotate on" | Unlock screen rotation |
| "Silent mode on karo" | Enable silent mode |
| "Vibrate mode chalu karo" | Enable vibrate mode |
| "DND on kar" / "Do Not Disturb on" | Enable Do Not Disturb |
| "DND off kar" | Disable Do Not Disturb |

### 📞 Smart Calling
| Command | Action |
|---------|--------|
| "Jarvis, Papa ko call lagao" | Call Papa (speakerphone default) |
| "Papa ko call lagao speaker par" | Call Papa on speaker |
| "Papa ko call lagao normal mode mein" | Call Papa without speaker |
| "Mummy ko phone kar" | Call Mummy |
| "Phone kar [contact name]" | Call any contact by name |
| "Phone kiya hai?" / "Recent calls dikhao" | Show recent call history |

### 💬 Auto Messaging
| Command | Action |
|---------|--------|
| "Jarvis, Papa ko WhatsApp kar 'Main aa raha hoon'" | Send WhatsApp message |
| "Papa ko SMS bhejo 'Main aa raha hoon'" | Send SMS |
| "Papa ko WhatsApp par voice note bhejo" | Send WhatsApp voice note |
| "Papa ko SMS karo 'Seat pe hai'" | Send SMS |
| "Papa ko WhatsApp message bhejo 'Kal aana hai'" | WhatsApp message |
| "Papa ko SMS karo 'Kal meeting hai'" | SMS message |

### 🎵 Media Control
| Command | Action |
|---------|--------|
| "Music chalao" / "Gaana bajao" | Play music |
| "Music pause kar" / "Gaana ruko" | Pause music |
| "Aage ka gaana chalao" / "Next song" | Next track |
| "Piche ka gaana" / "Previous song" | Previous track |
| "Music band kar" / "Stop music" | Stop music |
| "YouTube kholo" / "YouTube kholo aur tech videos chalao" | Open YouTube, search for tech videos |
| "Chrome kholo" / "Chrome kholo aur Google search karo" | Open Chrome, search |
| "Spotify kholo" / "Spotify par music chalao" | Open Spotify |
| "YouTube kholo aur 'cricket highlights' search karo" | Open YouTube + search |
| "Music apps dikhao" | List all music apps |
| "Apps list dikhao" | List all installed apps |
| "Social apps dikhao" | List social media apps |

### 📊 System Tasks & Automation
| Command | Action |
|---------|--------|
| "Battery status batao" / "Battery kitni hai" | Battery level + charging status |
| "Battery 15% se kam hai?" | Check if battery is low (proactive alert) |
| "Apps list dikhao" | List installed apps |
| "System check karo" | Show full system status |
| "Morning routine chalao" | Run morning routine (brightess up, volume up, read notifications) |
| "Night mode on karo" | Night routine (brightness down, DND on, silent mode) |
| "Meeting mode chalao" | Meeting mode (DND on, silent) |
| "Movie mode chalao" | Movie mode (brightness up, volume up, notifications off) |
| "Geofence set karo office par" | Set location-based routine at office location |
| "Geofence list dikhao" | List all active geofences |
| "Geofence delete karo [location]" | Delete a geofence |
| "Routines list dikhao" | List all configured routines |

### 📅 Reminders & Calendar
| Command | Action |
|---------|--------|
| "Jarvis, mujhe yaad dilao [kuch bhi] ko yaad dilana" | Set a reminder |
| "Bhoolne mat yaad dilana" | Set reminder (Hinglish) |
| "Market jaane par dhoodh lena yaad dilao" | Location-based reminder |
| "Kal subah 8 baje yaad dilana" | Time-based reminder |
| "Yaad dilao 10 minute baad" | Reminder after 10 minutes |
| "Reminder list dikhao" | List all pending reminders |
| "Reminder cancel karo [reminder]" | Cancel a reminder |
| "Alarm lagao subah 7 baje" | Set alarm |
| "Alarm band kar" | Cancel alarm |
| "Calendar check karo" | Read upcoming calendar events |
| "Daily briefing sunao" | Full morning briefing: time, weather, battery, calendar, news |
| "Weather batao" | Current weather for location |
| "Weather forecast sunao" | Weather forecast |
| "Location batao" | Current GPS location |
| "Next song play karo" | Play next song |
| "Aage wala gaana chalao" | Skip to next track |
| "Piche wala gaana" | Previous track |
| "Volume mute kar" | Mute system |
| "Speaker on karo call mein" | Ensure speakerphone for calls |

### 🌐 Translation & AI
| Command | Action |
|---------|--------|
| "Live translate karo English se Hindi" | Enable live translation from English to Hindi |
| "Hinglish translate karo English mein" | Translate Hinglish to English |
| "Yeh sentence translate karo Hindi mein" | Translate current text |
| "Live translation band kar" | Turn off translation mode |
| "Screen padho aur reply suggest karo" | Read screen + suggest replies |
| "Screen par kya hai?" | Read current screen content aloud |
| "Screen ke content ko padhkar samjhaao" | Explain screen content |
| "Reply suggest karo 'Hello'" | Suggest reply based on context |

### ⚙️ Settings & Ongoing
| Command | Action |
|---------|--------|
| "Jarvis, settings kholo" | Open settings |
| "Battery saving mode on karo" | Enable battery saver |
| "Headphone mode check karo" | Check Bluetooth/audio routing |
| "Kya chal raha hai?" / "Status dikhao" | Show everything JARVIS is doing |
| "Jarvis band kar" / "Stop JARVIS" | Stop everything |
| "Offline mode chalao" | Full offline mode |
| "Hands-free mode on karo" | Always listening mode |
| "Online mode chalao" | Enable online services |
| "Settings padho" | Read current settings aloud |
| "Log dikhao" | View internal logs (developer mode) |

---

## Architecture

### How Keywords Trigger Actions

```
User speaks → Microphone → Wake Word Detection (ONNX) 
→ Voice Intent Parser → Action Planner → Execute Action
```

#### Keyword → Intent Mapping

- **Torch/Falshlight** → `ToggleTorch` action
- **WiFi/Internet/WLAN** → `ToggleWifi` action
- **Bluetooth/BT** → `ToggleBluetooth` action
- **Volume/Awaz/Sound** → `VolumeControl` action
- **Brightness/Screen Brightness** → `BrightnessControl` action
- **Rotation/Auto-Rotate/Screen Lock** → `RotationLock` action
- **Silent/DND/Do Not Disturb** → `DNDToggle` action
- **Call/Phone/Telephone** → `MakeCall` action (+ speaker toggle)
- **WhatsApp/SMS/Send** → `SendMessage` action (WhatsApp/SMS)
- **Music/Gaana/Song/Play** → `MediaControl` action
- **Open/Launch/Start [app]** → `AppLauncher` action
- **Battery/Power/Charging** → `BatteryMonitor` action
- **Weather/Mausam/Temperature** → `WeatherService` action
- **Reminder/Yaad/Routine** → `ReminderManager` action
- **Screen padho/Read Screen** → `ScreenReader` action
- **Translate/Translation/Live** → `TranslationService` action
- **Geofence/Location reminder** → `GeofenceManager` action
- **Morning/Night/Meeting/Movie mode** → `RoutineManager` action
- **Status/Kya chal raha hai** → `SpeechService` (read current action)

### Offline Architecture

```
┌─────────────────────────────────────────┐
│           JARVIS Offline Engine          │
├─────────────────────────────────────────┤
│  Wake Word Engine (ONNX)                │ ← Always On
│  Voice Intent Parser (Keyword + Rules)  │ ← Rule-based, no ML needed
│  Action Executor (Rule-based)           │ ← Every action maps to Android API
│  Memory Engine (SQLite)                 │ ← Conversational memory
│  Battery Monitor (BatteryManager API)   │ ← Proactive alerts
│  Geofence Monitor (FusedLocation)       │ ← Location triggers
│  Screen Reader (Accessibility API)      │ ← Screen content extraction
│  Holographic UI (Compose)               │ ← Voice status visualization
└─────────────────────────────────────────┘
```

All actions map to Android system APIs — no network calls required.

### Connectivity

| Mode | What works | What doesn't |
|------|-----------|--------------|
| **Offline** (default) | Everything except live translation | Google Translate API, web lookup |
| **Online** (optional) | Live translation, web search, weather, calendar sync | Nothing — offline still works |

---

## Holographic Widget (UI)

JARVIS features a **neon-glowing hologram** widget that visualizes voice state:

- **Idle**: Pulsing, waiting for wake word
- **Listening**: Expands, microphone animation
- **Processing**: Rotating ring, thinking animation
- **Speaking**: Voice-wave emission animation
- **Confirmed**: Success glow

Widget is **always visible** on screen (semi-transparent overlay). Tap to interact, voice to control.

### UI Customization
| Option | What it does |
|--------|---------------|
| **Theme** | Default neon, Dark AMOLED, Cyberpunk, Minimal |
| **Widget size** | Small, medium, full-screen |
| **Opacity** | 30%–100% visible |
| **Position** | Top-left, top-right, bottom-left, bottom-right, center |
| **Color scheme** | Cyan, purple, green, orange, custom hex |

---

## Geofence & Tasker Integration

JARVIS supports **location-based automation** — geofences that trigger actions when you arrive or leave a location.

### Set Up a Geofence
```
"Geofence set karo office par"    → Office geofence active
"Geofence set karo ghar par"     → Home geofence active
"Geofence list dikhao"           → All geofences shown
"Geofence delete karo office"    → Delete office geofence
```

### Geofence Actions (automatic)
- **Arrive at home** → WiFi on, Bluetooth on, ringer normal
- **Leave home** → WiFi off, DND on
- **Arrive at office** → Silent mode, DND on
- **Leave office** → Normal ringer, open maps

---

## Battery Alerts

JARVIS monitors battery continuously:
- At **15%**: Voice alert "Battery 15% hai, charging lagao, Sir"
- At **5%**: Urgent alert "Battery 5% hai, abhi charge karo!"
- At **80% charging**: "Battery full ho rahi hai, charger hatao"
- At **100%**: "Charging complete, battery 100%"

Settings → "Battery alert disable karo" to turn off.

---

## Morning & Night Routines

### Morning Routine (7:00 AM default)
- Brightness up to 80%
- Volume up to 50%
- Read notifications aloud
- Weather briefing
- Calendar events summary
- Battery status

### Night Routine (10:00 PM default)
- Brightness down to 20%
- DND on
- Silent mode
- "Good night, Sir" farewell

Customize via: "Morning routine time badlao 8 baje" or "Night routine on 9 baje karo"

---

## Hands-Free Design

JARVIS is designed for **hands-free use**:
1. **Always listening** — wake word detection runs continuously
2. **No button press needed** — everything is voice-activated
3. **Speakerphone by default** for calls — "Papa ko call lagao" automatically uses speaker
4. **Voice feedback** for every action — "Alright, calling Papa on speaker"
5. **Proactive alerts** — battery, reminders, notifications, geofence triggers

---

## Offline Capability Deep Dive

| Feature | Offline support |
|---------|-----------------|
| Wake word detection | ✅ 100% offline (ONNX on-device) |
| Keyword command parsing | ✅ 100% offline (rule-based) |
| App launching | ✅ Native Android intents (offline) |
| System settings toggle | ✅ Android system APIs (offline) |
| Call/SMS/WhatsApp | ✅ Android intents (offline) |
| Media control | ✅ Media button broadcast (offline) |
| Screen reading | ✅ Accessibility API (offline) |
| Battery monitoring | ✅ BatteryManager API (offline) |
| Geofence trigger | ✅ FusedLocation API (offline) |
| Conversational memory | ✅ SQLite (offline) |
| Live translation | ⚠️ Requires Google Translate API (optional) |
| Weather lookup | ⚠️ Requires internet or location service |
| Calendar sync | ⚠️ Requires Google Calendar API (optional) |

**When offline:**
- All basic commands work
- Battery alerts work
- Geofence routines work
- Memory and context work
- Screen reading works

**Online features (optional):**
- Live translation
- Weather forecast from API
- Calendar sync
- Web search (if enabled)

---

## Manifest Permissions (Transparent)

```xml
<!-- Essential for JARVIS to function -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.NOTIFICATIONS" />

<!-- For calling / messaging -->
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.CAMERA" />  <!-- For QR, contacts photos -->

<!-- For location-based features -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- For geofence / Tasker integration -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Accessibility -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />

<!-- Optional (online features) -->
<uses-permission android:name="android.permission.INTERNET" /> <!-- Only if online features enabled -->
```

**No internet permission by default** — JARVIS runs offline unless user explicitly enables online features.

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| "Jarvis doesn't respond" | Check microphone permission, speak clearly "Hey JARVIS" |
| "Call not going through" | Check phone permission, ensure contact exists |
| "WhatsApp message not sending" | Check WhatsApp is installed, contact has WhatsApp |
| "Brightness not changing" | Some OEMs restrict this — use Settings app |
| "Geofence not triggering" | Check location permission, ensure location is ON |
| "Battery alert not working" | Ensure battery monitoring is enabled in settings |
| "Screen reading not working" | Check Accessibility permission granted |
| "Wake word not detecting" | Speak "Hey JARVIS" clearly, reduce ambient noise |

---

## Coming Soon

- [ ] Smart plug / IoT device control
- [ ] AI-powered summarization of long messages
- [ ] Voice biometric authentication
- [ ] Multi-language voice commands (Spanish, French, German)
- [ ] Home screen widgets for quick actions
- [ ] Integration with wearables (smartwatch)
- [ ] Cloud backup of JARVIS settings (optional)

---

## License

JARVIS is provided as-is. Built for the Android platform.

---

## Author

Built by the JARVIS team — a voice-first, offline-first cognitive assistant.
