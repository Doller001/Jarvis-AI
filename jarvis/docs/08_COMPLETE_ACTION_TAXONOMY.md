# JARVIS — COMPLETE Features, Functions & Actions Master Taxonomy

> **Date:** 2026-08-27
> **Purpose:** EXHAUSTIVE list of every action a phone AI assistant (Tiny Stark JARVIS)
> should be able to do, mapped against current implementation. Closes the gap from
> earlier reports (which missed entire action DOMAINS).
> **Method:** Source-read of Android tree + Android platform capability survey.
> **Status:** RESEARCH ONLY — no code.

Legend: ✅ Implemented · 🟡 Partial · ❌ Missing · [N]=Necessary · [O]=Optional

---

## DOMAIN 1: VOICE & CONVERSATION
| Action | State | Notes |
|---|---|---|
| Wake word (Jarvis/Hinglish) | ✅ | ONNX + matcher |
| Push-to-talk | ✅ | Floating mic button |
| VAD | ✅ | Energy-based |
| STT (cloud) | ✅ | SpeechRecognizer |
| TTS | ✅ | Android TTS |
| Conversational chat | ✅ | LocalConversational + cloud LLM |
| Barge-in (interrupt) | ❌[O] | Full-duplex |
| Offline STT (Vosk) | ❌[O] | No network needed |
| Multi-turn context memory | ✅ | MAG episodes |
| Persona / butler voice | 🟡[N] | Basic now, needs Stark wit (S3) |
| Language switch (Hinglish/EN) | ✅ | Mixed resolver |

## DOMAIN 2: DISPLAY & SCREEN
| Action | State | Notes |
|---|---|---|
| Brightness up/down/set % | ❌[N] | Settings.System.SCREEN_BRIGHTNESS |
| Auto-brightness toggle | ❌[O] | |
| Screen rotation lock | ❌[N] | ACCELEROMETER_ROTATION |
| Screen timeout set | ❌[O] | Settings.System.SCREEN_OFF_TIMEOUT |
| Dark/Light theme | ❌[O] | App-level only |
| Screenshot | ❌[O] | MediaProjection |
| Read screen (accessibility) | ✅ | Password-masked |
| Tap/scroll/type on screen | ✅ | AccessibilityController |
| Wallpaper change | ❌[O] | WallpaperManager |
| Show/Hide floating HUD | ✅ | Overlay service |
| Screen record | ❌[O] | MediaProjection |

## DOMAIN 3: AUDIO & SOUND
| Action | State | Notes |
|---|---|---|
| Volume set (media) | ✅ | |
| Volume per-stream (alarm/ring/notification) | ❌[N] | STREAM_ALARM/RING/NOTIFICATION |
| Ringer mode (silent/vibrate/normal) | ❌[N] | AudioManager.setRingerMode |
| Do Not Disturb toggle | ❌[N] | NotificationManager interruption filter |
| Mute/unmute | ✅ | Volume 0 |
| Media play/pause/next/prev/stop | ✅ | Key events |
| Open & play YouTube/Spotify | ✅ | Adapters |
| Read aloud any text | ✅ | TTS |
| Text-to-speech rate/pitch | ✅ | setSpeechRate |
| Sound profile presets (movie/sleep) | ❌[O] | Routine |
| Vibration pattern (custom) | ❌[O] | Vibrator |

## DOMAIN 4: CONNECTIVITY
| Action | State | Notes |
|---|---|---|
| Wi-Fi on/off (panel) | 🟡 | Settings only (platform-limited) |
| Wi-Fi on/off (programmatic) | ❌ | Restricted on Android 10+ |
| Bluetooth on/off (panel) | 🟡 | Settings only |
| Bluetooth connect to device | ❌[N] | getBondedDevices + profile |
| Airplane mode toggle | ❌[O] | Settings panel |
| Mobile data on/off | ❌[O] | Restricted |
| Hotspot on/off | ❌[O] | Tethering API (restricted) |
| Check network type (4G/5G/WiFi) | ❌[N] | ConnectivityManager |
| Check IP / public IP | ❌[O] | |
| Ping / speed test | ❌[O] | |

## DOMAIN 5: POWER & BATTERY
| Action | State | Notes |
|---|---|---|
| Battery % | ✅ | |
| Battery charging state | ❌[N] | BatteryManager.isCharging |
| Battery health/temp | ❌[O] | |
| Battery saver toggle | ❌[O] | |
| Power off / reboot | ❌[O] | Requires root/sys |
| Low battery alert (proactive) | ❌[N] | |
| Screen-on time / usage stats | ❌[O] | UsageStatsManager |

## DOMAIN 6: STORAGE & FILES
| Action | State | Notes |
|---|---|---|
| Storage free/total | ✅ | |
| List files in folder | ❌[N] | File API / DocumentsUI |
| Open file (pdf/doc/photo) | ❌[N] | Intent ACTION_VIEW |
| Delete file | ❌[O] | Needs confirmation |
| Share file | ❌[O] | Intent ACTION_SEND |
| Download file from URL | ❌[O] | DownloadManager |
| Take photo / selfie | ✅ | CameraController |
| Record video | ❌[O] | MediaStore intent |
| Read clipboard | ❌[N] | ClipboardManager |
| Copy to clipboard | ❌[N] | ClipboardManager |
| Clear app cache | ❌[O] | |

## DOMAIN 7: APPS & TASKS
| Action | State | Notes |
|---|---|---|
| Open app by name/alias | ✅ | 70+ aliases |
| Close app / go home | ✅ | |
| List installed apps | ✅ | By category |
| Force stop app | ❌[O] | |
| App info / uninstall | ❌[O] | |
| App usage time | ❌[O] | UsageStats |
| Lock screen | ❌[N] | DevicePolicyManager / keyguard |
| Split screen | ❌[O] | |
| Quick settings panel | ❌[O] | |
| Notification panel | ❌[O] | |
| Recent apps | ✅ | GLOBAL_ACTION_RECENTS |

## DOMAIN 8: COMMUNICATION
| Action | State | Notes |
|---|---|---|
| Call by name/number | ✅ | |
| Call log read | ✅ | |
| Missed call alert | ❌[N] | |
| SMS send | ✅ | |
| SMS read inbox | ❌[N] | Telephony.Sms inbox |
| SMS reply | ❌[N] | Accessibility type |
| WhatsApp send | ✅ | |
| WhatsApp read unread | 🟡 | Notification-based |
| WhatsApp reply | ❌[N] | |
| Email (Gmail) read/send | ❌[O] | |
| Telegram send | ❌[O] | |
| Read all notifications | ✅ | Listener |
| Spam/unknown caller ID | ❌[O] | |
| Auto-reply (driving) | ❌[O] | |

## DOMAIN 9: TIME, CALENDAR & PRODUCTIVITY
| Action | State | Notes |
|---|---|---|
| Time/date | ✅ | |
| Set alarm | ❌[N] | AlarmManager + intent |
| Set reminder | ❌[N] | AlarmManager + TTS |
| Timer / stopwatch | ❌[N] | CountDownTimer |
| Calendar read (today/week) | ❌[N] | CalendarContract read |
| Calendar create event | ❌[O] | CalendarContract write |
| Calendar reminder alert | ❌[N] | Proactive |
| Notes (create/read) | ❌[O] | |
| To-do / task list | ❌[O] | |
| Calculator | ✅ | App launch |
| Unit/currency convert | ❌[O] | Cloud LLM / API |

## DOMAIN 10: LOCATION & TRAVEL
| Action | State | Notes |
|---|---|---|
| Current location | ❌[N] | FusedLocation (perm) |
| Weather now | ❌[N] | API / backend |
| Weather forecast | ❌[O] | |
| Navigate to place | ❌[N] | Maps intent |
| ETA / traffic | ❌[O] | |
| Nearby (ATM/restaurant) | ❌[O] | Places API |
| Distance between | ❌[O] | |

## DOMAIN 11: WEB & KNOWLEDGE
| Action | State | Notes |
|---|---|---|
| Web search | 🟡 | Backend tool real; Android intent missing [N] |
| Open URL | ✅ | ChromeAdapter |
| News headlines | ❌[O] | RSS/API |
| Wikipedia summary | ❌[O] | API |
| Translate text | ❌[O] | Cloud LLM |
| Factual Q&A | ✅ | Cloud LLM |
| Math/calculation | ✅ | Cloud LLM |

## DOMAIN 12: SMART HOME & IOT
| Action | State | Notes |
|---|---|---|
| Lights on/off | ❌[O] | Backend bridge |
| AC/Thermostat | ❌[O] | |
| Smart plug | ❌[O] | |
| Door/camera | ❌[O] | |
| Scene/routine trigger | ❌[O] | Home Assistant |

## DOMAIN 13: CAMERA & VISION (multimodal)
| Action | State | Notes |
|---|---|---|
| Photo / selfie | ✅ | |
| Video record | ❌[O] | |
| Describe what camera sees | ❌[O] | Vision LLM |
| Read QR / barcode | ❌[O] | ML Kit |
| OCR text from image | ❌[O] | ML Kit |
| Face/object recognition | ❌[O] | |
| Screen understanding (vision) | ❌[O] | |

## DOMAIN 14: SECURITY & PRIVACY
| Action | State | Notes |
|---|---|---|
| Lock screen | ❌[N] | |
| App lock | ❌[O] | |
| Toggle privacy mode | ❌[O] | |
| Find my phone (ring) | ❌[O] | |
| Wipe / remote | ❌[O] | |
| Permission audit | 🟡 | PermissionManager exists |
| Biometric auth prompt | ❌[O] | BiometricPrompt |

## DOMAIN 15: AUTOMATION & ROUTINES
| Action | State | Notes |
|---|---|---|
| Multi-step compound task | ✅ | LocalTaskPlanner (fixed flows) |
| Scheduled task (time-based) | ❌[N] | AlarmManager |
| Location-based trigger | ❌[O] | Geofence |
| Routine / macro | ❌[O] | "Movie mode" |
| Skill learning (replay) | ❌[O] | SkillDroid |
| Conditional ("if X then Y") | ❌[O] | IFTTT-style |

## DOMAIN 16: SYSTEM DIAGNOSTICS & PROACTIVE
| Action | State | Notes |
|---|---|---|
| Systems check | ✅ | Battery/time/storage |
| Enriched diagnostic | ❌[N] | Net security, app scan, temp |
| Proactive briefing (morning) | ❌[N] | Notifications+battery+calendar |
| Anomaly alert (battery drain) | ❌[O] | |
| Daily digest | ❌[N] | |
| "What's my status?" | ✅ | SystemsCheck variant |

## DOMAIN 17: MEMORY & LEARNING
| Action | State | Notes |
|---|---|---|
| CAG instant cache | ✅ | |
| RAG retrieve | 🟡 | Keyword only |
| MAG episodes/facts | ✅ | |
| Cross-device sync | ✅ | Supabase |
| User preference learn | 🟡 | 3 regex patterns only |
| Vector semantic memory | ❌[O] | sqlite-vec |
| Forget / clear memory | ❌[N] | User command |
| Personalized routines | ❌[O] | |

## DOMAIN 18: ENTERTAINMENT & MEDIA
| Action | State | Notes |
|---|---|---|
| Play music (app) | ✅ | |
| Play specific song | ✅ | YouTube/Spotify |
| Podcast / audiobook | ❌[O] | |
| Tell joke / fun fact | ✅ | Cloud LLM |
| Story / bedtime | ❌[O] | |
| Quiz / game | ❌[O] | |

## DOMAIN 19: ACCESSIBILITY & ASSISTIVE
| Action | State | Notes |
|---|---|---|
| Read screen aloud | ✅ | |
| Describe image for blind | ❌[O] | Vision |
| Magnify / zoom | ❌[O] | |
| Voice control replacement | ✅ | Core |
| Emergency SOS | ❌[N] | Call emergency contact |

## DOMAIN 20: DEVELOPER / POWER USER
| Action | State | Notes |
|---|---|---|
| Run shell command | ❌[O] | Root |
| Toggle dev options | ❌[O] | |
| App backup | ❌[O] | |
| Export memory/log | ❌[N] | DiagnosticEventBus → file |

---

## SUMMARY COUNT
- ✅ Implemented domains fully/partially: Voice, Audio(media), Apps, Comms(send), Storage(stats), Camera(photo), Memory, Diagnostics, Web(Q&A), Entertainment(joke)
- ❌ Entirely MISSING domains: **Location/Travel, Smart Home, Vision, Security(lock), Automation(scheduled), Files(full), Calendar, Clipboard, Screenshot, Contacts proactive**
- 🟡 Partial: WiFi/BT (panel only), Comms(read), Web(search intent), RAG(keyword), User learning

## THE "KAMI" (what was missing in earlier reports):
Earlier docs covered Voice/Device/Apps/Comms/Media/Memory but **SKIPPED**:
1. Files & clipboard management
2. Calendar & productivity
3. Location & weather & navigation
4. Web search grounding (Android side)
5. Security (lock screen, SOS)
6. Scheduled automation & routines
7. Vision/multimodal
8. Smart home
9. Accessibility assistive (beyond screen read)
10. Developer/power-user export

This taxonomy now covers **all 20 domains / ~160 distinct actions**.

---

*Research only. Companion: `06_FEATURES_FUNCTIONS_ACTIONS_REPORT.md` (state + necessary/optional),
`07_TINY_STARK_JARVIS_RESEARCH.md` (Stark mapping), `05_ANDROID_FEATURE_PLAN.md` (plan).*
