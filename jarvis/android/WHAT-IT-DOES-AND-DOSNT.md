# Jarvis Assistant — What It Does & Doesn't Do

---

## What It CAN Do

### Voice & Wake Word
- Detects "Hey Jarvis" offline using 3 ONNX neural network models (no internet needed)
- 3 sensitivity levels: Low, Balanced, High
- Speech-to-Text via Android SpeechRecognizer (English + Hindi)
- Text-to-Speech with locale cascade (en-IN → device default → en-US)
- Push-to-Talk mode when wake word is disabled
- 8-state voice FSM: DISABLED → WAKE_LISTENING → ACKNOWLEDGING → COMMAND_LISTENING → PROCESSING → SPEAKING → RECOVERING → IDLE

### Voice Commands (50+ intents)
| Category | Supported Actions |
|---|---|
| **Torch** | On/Off |
| **Volume** | Set %, Up/Down, Max/Min, Mute |
| **Brightness** | Set %, Up/Down, Full, Half |
| **Wi-Fi / Bluetooth** | Opens settings (Android restriction) |
| **Ringer Mode** | Silent, Vibrate, Normal |
| **DND** | On/Off |
| **Screen Rotation** | Lock/Unlock |
| **Apps** | Open/Close any of 70+ named apps |
| **Media** | Play/Pause/Next/Prev/Stop |
| **YouTube** | Search and play by name |
| **Phone Calls** | Call contact by name (with confirmation) |
| **SMS** | Send SMS to contact (with confirmation) |
| **WhatsApp** | Send message (with confirmation), read unread notifications |
| **Alarms** | Set alarm at time |
| **Reminders** | Set reminder with delay + message |
| **Timers** | Set countdown timer |
| **Calendar** | Read next 5 upcoming events |
| **Location** | Get current location, open Maps navigation |
| **Screenshots** | Capture screen |
| **Clipboard** | Copy/Read clipboard |
| **Battery** | Level, charging status, health |
| **Storage** | Free/total space |
| **Daily Briefing** | Time + battery + storage + calendar |
| **Lock Screen** | Via Device Admin permission |
| **Emergency SOS** | Dials 112 |
| **Web Search** | Opens Google search in Chrome |
| **Lists** | List installed apps by category |

### Multi-Step Task Planning
- Decomposes compound commands ("do X and Y") into step-by-step TaskPlan
- 12 pre-built multi-step flows (morning routine, movie mode, meeting mode, etc.)
- Confirmation gate for calls/SMS/WhatsApp
- Retry logic with step verification

### 7 Preset Routines
| Routine | Actions |
|---|---|
| **Morning** | Brightness 80%, Volume 50%, DND off, ringer normal, status report |
| **Night** | Brightness 15%, Volume 20%, DND on, silent |
| **Movie** | Brightness 100%, Volume 80%, DND on, auto-rotate on |
| **Meeting** | DND on, vibrate, volume 0%, brightness 60% |
| **Driving** | Volume 100%, brightness 100%, auto-rotate on, opens Maps |
| **Gym** | Volume 100%, DND on, music play |
| **Reading** | Brightness 50%, DND on, silent, rotation locked |

### Three-Tier Memory Engine
- **CAG (Cache):** Exact + near-match query lookup via SHA-256 hash + Jaccard similarity
- **RAG (Retrieval):** Multi-chunk local knowledge retrieval from SQLite
- **MAG (Long-term):** User facts, conversation history, profile key-value store
- **Auto-learning:** Every cloud LLM response is cached for instant offline reuse

### Cloud LLM Backend
- 5 providers: NVIDIA Nemotron, Groq, OpenRouter, Gemini, Ollama
- REST API + WebSocket with auto-reconnect
- Health monitoring (30s periodic checks)
- Falls back to cloud only for open-ended conversational queries

### Accessibility-Based UI Automation
- Find and tap UI elements by text, content description, or view ID
- Scroll, global actions (Back/Home/Recents)
- Type text into input fields
- Read all visible screen text (masks password fields)
- YouTube first-result detection and tap

### System Integration
- Foreground service for persistent background operation
- Quick Settings tile toggle
- Notification listener for reading app notifications
- Boot recovery (restores state after reboot)
- OEM-aware background survival for 7 brands (Xiaomi, Samsung, Oppo/Realme, Vivo/iQoo, Huawei/Honor, OnePlus)
- Floating overlay widget with holographic UI

### Settings & Connectivity
- Online Connectivity Mode (Default) with auto-reconnecting backend health engine
- Explicit Offline Mode toggle (switches JARVIS to 100% on-device local execution)
- Configurable backend URL with live ping tests and presets
- TTS on/off + speech rate controls
- Wake word on/off + 3 sensitivity levels
- Auto-generated device ID with JWT device authentication

---

## What It CAN'T Do

### Hardware / System Restrictions
| Limitation | Reason |
|---|---|
| Toggle Wi-Fi programmatically | Android 10+ restriction; opens settings instead |
| Toggle Bluetooth programmatically | Same; opens settings instead |
| Toggle Airplane Mode | Restricted on modern Android; opens settings |
| Take silent screenshots on Android 10+ | Requires MediaProjection user consent |
| Set brightness without WRITE_SETTINGS | Falls back to Display settings |
| Toggle DND without Notification Policy Access | Guides user to grant permission |
| Read battery temperature | API not available on modern Android |
| Control smart home / IoT devices | No integration; torch is placeholder only |

### Voice System
| Limitation | Detail |
|---|---|
| Only "Hey Jarvis" wake phrase | Hardcoded ONNX model; no custom hotword training |
| STT uses Google cloud by default | Audio sent to Google servers; on-device not guaranteed |
| No real-time streaming STT | Batch recognition only |
| No speaker diarization | Cannot distinguish multiple speakers |
| No emotion/sentiment analysis | Voice treated as raw text |
| Single-mic architecture | Wake word and command STT cannot run simultaneously |

### Memory Engine
| Limitation | Detail |
|---|---|
| No semantic embeddings | Uses Jaccard + Levenshtein, not neural embeddings |
| No vector database | Brute-force SQLite search, not FAISS/Pinecone |
| No automatic fact extraction beyond simple patterns | Only detects "my name is...", "I live in..." etc. |
| No cross-device sync | Local SQLite only |
| No conversation summarization | Raw episodic storage, no compression |
| No memory expiration/TTL | Facts and episodes never auto-expire |

### Communication
| Limitation | Detail |
|---|---|
| Cannot read WhatsApp message content directly | Only reads notifications (title + text) |
| Cannot reply within WhatsApp UI | Opens WhatsApp via URL for user confirmation |
| Cannot send MMS or media messages | Plain text SMS only |
| Cannot schedule recurring alarms | One-time alarms only |
| Cannot create/update/delete calendar events | Read-only calendar access |
| Cannot send emails | No email integration |

### App Control
| Limitation | Detail |
|---|---|
| Cannot force-stop foreground apps | `killBackgroundProcesses()` only works on background apps |
| App aliases hardcoded | 70+ aliases maintained manually; new apps must fuzzy-match |
| Cannot navigate within apps | Only AccessibilityService-based text matching |
| Cannot install or uninstall apps | No Play Store integration |

### Network / Backend
| Limitation | Detail |
|---|---|
| Render.com free tier cold starts | Backend may take 30-60s to wake up |
| No offline LLM capability | Model inference runs on remote server |
| Intent resolution is keyword-based | Not ML-based; complex commands fall to `Unknown` |
| No streaming responses | Synchronous request/response only |
| No API key authentication | Provider configs have `isAuthenticated = true` hardcoded |

### UI / UX
| Limitation | Detail |
|---|---|
| No dark/light theme toggle | Fixed cosmic/space theme |
| No conversation export (user-friendly) | Log export is raw text, not JSON/PDF |
| No home screen widget | No quick-access widget |
| No notification action buttons | Foreground service notification has no controls |
| No multi-language UI | English UI only; Hindi only in voice commands |

### Security & Privacy
| Limitation | Detail |
|---|---|
| No end-to-end encryption for cloud chat | HTTPS only, not E2E |
| No user authentication | No login, PIN, or biometric lock |
| No data encryption at rest | SQLite stored unencrypted |
| Screen reading exposes sensitive content | Masks passwords but not other sensitive text |
| Contact lookup is broad | `LIKE %name%` matching can return wrong contacts |
| Emergency SOS only dials 112 | Cannot send SMS with location or share with emergency contacts |

### Routines & Multi-Step
| Limitation | Detail |
|---|---|
| No user-created routines | Only 7 hardcoded routines |
| No conditional logic | Fixed action sequences, no if/then branching |
| No time-based triggers | Cannot schedule routines (e.g., "every 7am") |
| No routine chaining | Cannot compose routines from other routines |
| Confirmation-required steps abort entire plan | Plan cancelled, not paused |
| No parallel step execution | Sequential only, even for independent steps |
| No undo/rollback | Partial failures leave completed actions intact |
| Hardcoded multi-step flows | Only ~12 specific patterns recognized |

---

## Summary

**Design Principle:** 100% functional device control when backend is offline. Cloud only for open-ended conversation.

**Core Strengths:** Offline wake word, 50+ voice commands, three-tier memory with auto-learning, OEM-aware background survival, accessibility-based UI automation.

**Main Weaknesses:** Android system restrictions (Wi-Fi/BT toggle), no semantic embeddings for memory, keyword-based intent resolution, no custom wake words, Render.com cold start delays.
