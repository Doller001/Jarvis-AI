# Jarvis-1.0 (Offline) — Separate APK Build Plan

> **Version:** 1.0.0 | **Build Target:** Android 26–34 | **Namespace:** `com.jarvis.assistant.offline`
> **APK Name:** `jarvis-1.0-offline.apk`
> **Core Principle:** 100% on-device. Zero network dependencies by default. User can manually add API/DB/connectivity via in-app configuration panel.

---

## 1. 무엇이 다른가 — jarvis-1.0 vs 기존 단일 앱

| 항목 | 기존 단일 앱 | jarvis-1.0 (오프라인) |
|---|---|---|
| applicationId | `com.jarvis.assistant` | `com.jarvis.assistant.offline` |
| versionName | 1.0.0 | 1.0.0 |
| INTERNET 권한 | 있음 | **없음** (기본) |
| ACCESS_NETWORK_STATE | 있음 | **없음** |
| WS 클라이언트 | 포함 | **제외** (빌드 시) |
| API 클라이언트 (OkHttp) | 포함 | **제외** (빌드 시) — 또는 비활성화 |
| BackendHealthManager | 온라인 기본 | **완전히 제거** 또는 스텁 |
| 클라우드 LLM 제공자 | 5개 활성화 | **제외** — 로컬 메모리만 |
| 오프라인 모드 토글 | 설정에서 전환 | **불필요** — 항상 오프라인 |
| API/DB 수동 추가 | 없음 | **설정 화면에 옵션 추가** |

---

## 2. 아키텍처 접근법: 두 앱을 만드는 방법

### 2.1 권장 방식: Gradle 소스 세트 + Manifest 교체 (가장 깔끔)
기존 코드를 최대한 재사용하면서 네트워크 관련 코드만 빌드 변종으로 제외.

**디렉토리 구조:**
```
jarvis/android/
├── app/
│   ├── src/main/           # 공통 코드 (모든 것)
│   ├── src/offline/        # 오프라인 전용 오버라이드와 리소스
│   │   ├── AndroidManifest.xml   # INTERNET 권한 제거
│   │   └── kotlin/.../NetworkModule.kt  # 스텁 구현
│   └── src/online/         # 온라인 전용 오버라이드 (jarvis-1.1 용)
│       ├── AndroidManifest.xml   # 모든 권한 포함
│       └── kotlin/.../
├── build.gradle.kts        # productFlavors 설정
```

**build.gradle.kts 에 productFlavors 추가:**
```kotlin
android {
    flavorDimensions += "connectivity"
    productFlavors {
        create("offline") {
            dimension = "connectivity"
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
            // 네트워크 권한 없음
            manifestPlaceholders += mapOf("NETWORK_PERMISSIONS" to "")
        }
        create("online") {
            dimension = "connectivity"
            applicationIdSuffix = ".online"
            versionNameSuffix = "-online"
            manifestPlaceholders += mapOf("NETWORK_PERMISSIONS" to """
                <uses-permission android:name="android.permission.INTERNET" />
                <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
                <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
            """.trimIndent())
        }
    }
}
```

### 2.2 대안: 완전한 두 개의 별도 모듈
`app-offline/` 과 `app-online/` 을 별도로 만들고 공통 코드를 `library/` 모듈로 빼서 공유. 더 깔끔하지만 리팩토링 필요.

**이 계획에서는 2.1 방식을 채택** — 변경 최소화, 기존 코드 최대 재사용.

---

## 3. jarvis-1.0 에서 제거하거나 비활성화할 컴포넌트

### 3.1 완전 제거 대상
| 파일/클래스 | 이유 |
|---|---|
| `network/ApiClient.kt` | HTTP 호출 — 오프라인에서 불필요 |
| `network/WebSocketClient.kt` | WebSocket — 불필요 |
| `network/BackendHealthManager.kt` | 백엔드 헬스 체크 — 불필요 |
| `network/AuthRepository.kt` | 인증 — 불필요 |
| `network/AuthTokenManager.kt` | 토큰 관리 — 불필요 |
| `network/ConnectionManager.kt` | 연결 관리 — 불필요 |
| `network/ProtocolModels.kt` | 프로토콜 모델 — 불필요 |
| `llm/ProviderManager.kt` | 클라우드 제공자 관리 — 불필요 |
| `llm/ProviderRegistry.kt` | 제공자 레지스트리 — 불필요 |
| `llm/providers/*` | NVIDIA, Groq, OpenRouter, Gemini, Ollama — 불필요 |

### 3.2 유지할 것 (오프라인에서 작동)
- **Voice**: wake word (ONNX), STT (Android SpeechRecognizer), TTS — 모두 로컬
- **Memory**: SQLite CAG/RAG/MAG — 완전 로컬
- **Device Controllers**: torch, volume, brightness, apps, media, calls, SMS, WhatsApp, location, alarms, reminders, timers, calendar, clipboard, screenshots, battery, storage — 모두 로컬
- **ActionEngine**: LocalTaskPlanner, ActionExecutor, 모든 어댑터 — 로컬
- **Routines**: 7개 프리셋 루틴 — 로컬
- **Accessibility**: UI 자동화 — 로컬
- **Overlay**: 플로팅 위젯 — 로컬
- **Services**: ForegroundService, BootReceiver, NotificationListener, QuickTile — 로컬
- **UI**: 모든 Compose 화면 — 로컬

---

## 4. jarvis-1.0 에서 남길 "수동 추가" 기능

사용자가 원하면 API/DB/커넥티비티를 수동으로 추가할 수 있는 옵션 패널.

### 4.1 설정 화면에 추가할 섹션: "연결 추가 (수동)"

**UI 위치:** SettingsScreen.kt 에 새 섹션 추가

**옵션:**
1. **백엔드 API URL 수동 입력**
   - 텍스트 필드: `https://your-backend.com`
   - 활성화하면 ApiClient 가 동적으로 초기화됨
   - 비활성화하면 네트워크 코드 아예 실행 안 함

2. **로컬 데이터베이스 연결 (선택)**
   - SQLite 경로 지정 옵션
   - 또는 외부 DB (PostgreSQL/Supabase) 연결 문자열 입력
   - 기본은 앱 내부 SQLite

3. **WebSocket 주소**
   - URL 입력 필드
   - 활성화 시 WebSocketClient 동적 시작

4. **클라우드 LLM 제공자 설정**
   - 제공자 선택 (NVIDIA/Groq/OpenRouter/Gemini/Ollama)
   - API 키 입력
   - 활성화 시 ProviderManager 초기화

### 4.2 구현 방법: 조건부 초기화

```kotlin
// SettingsManager 에 새 플래그 추가
const val KEY_MANUAL_API_ENABLED = "key_manual_api_enabled"
const val KEY_MANUAL_BACKEND_URL = "key_manual_backend_url"
const val KEY_MANUAL_WS_ENABLED = "key_manual_ws_enabled"
const val KEY_MANUAL_WS_URL = "key_manual_ws_url"
const val KEY_MANUAL_LLM_ENABLED = "key_manual_llm_enabled"

// Network 모듈은 초기화 시 이 플래그 확인
// 모두 false 면 네트워크 코드 완전 스킵
// 하나라도 true 면 해당 컴포넌트만 동적 초기화
```

---

## 5. 권한 자동화 — jarvis-1.0

기존 PermissionManager.kt 그대로 사용 + 오프라인 앱 전용 권한 확인 UI.

### 5.1 jarvis-1.0 매니페스트 권한 (최소)
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FLASHLIGHT" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />
```

**제거할 권한:** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`

### 5.2 권한 자동 표시 UI
기존 PermissionManager.checkPermissionState() 로 상태 확인 → SettingsScreen 또는 OnboardingScreen 에서 권한별 상태 표시 + 권한 부여 버튼.

**UI 흐름:**
```
설정 > 권한 관리
├── ✅ 마이크 (RECORD_AUDIO)
├── ❌ 알림 (POST_NOTIFICATIONS)  → [설정으로 이동] 버튼
├── ❌ 접근성 (AccessibilityService) → [설정으로 이동] 버튼
├── ❌ 배터리 최적화 무시 → [설정으로 이동]
├── ❌ 카메라 → [설정으로 이동]
├── ❌ 전화 걸기 → [설정으로 이동]
├── ❌ 연락처 → [설정으로 이동]
├── ❌ SMS → [설정으로 이동]
└── ❌ 디지털 어시스턴트 → [설정으로 이동]
```

기존 PermissionManager 의 `allRequiredGranted` 확인 → 거짓이면 OnboardingScreen 으로 리디렉션하여 권한 부여 유도.

---

## 6. jarvis-1.0 빌드 구성 상세

### 6.1 build.gradle.kts 변경

```kotlin
android {
    // ...
    flavorDimensions += "mode"
    
    productFlavors {
        create("offline") {
            dimension = "mode"
            applicationId = "com.jarvis.assistant.offline"
            versionName = "1.0.0"
            
            // 오프라인: 네트워크 관련 의존성 제외
            // 소스 세트에서 네트워크 파일 제외
        }
        create("online") {
            dimension = "mode"
            applicationId = "com.jarvis.assistant.online"
            versionName = "1.1.0"
        }
    }
    
    // 소스 세트 구성
    sourceSets {
        getByName("offline") {
            java.srcDirs("src/offline/kotlin")
            res.srcDirs("src/offline/res")
        }
        getByName("online") {
            java.srcDirs("src/online/kotlin")
            res.srcDirs("src/online/res")
        }
    }
}

dependencies {
    // 공통 의존성 (두 플레버 모두)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    
    // 오프라인 전용 — OkHttp 제외 (또는 선택적)
    // offlineFlavor 에서만 제외하려면:
    // offlineImplementation 은 없고, onlineImplementation 으로 추가
    
    // 온라인 전용 의존성
    onlineImplementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### 6.2 네트워크 코드 조건부 컴파일

방법 A: 소스 세트 오버라이드로 네트워크 파일 대체

`src/offline/kotlin/com/jarvis/assistant/network/` 에 스텁 파일 생성:
- `ApiClient.kt` — 빈 클래스 또는 로깅만
- `WebSocketClient.kt` — 빈 클래스
- `BackendHealthManager.kt` — `start()` 호출 시 즉시 OFFLINE 반환
- `AuthRepository.kt` — 스텁
- `AuthTokenManager.kt` — 스텁

방법 B: 빌드 상수 사용

```kotlin
// BuildConfig 에 IS_OFFLINE 플래그 추가
buildFeatures {
    buildConfig = true
}

// 코드에서 사용
if (BuildConfig.IS_OFFLINE) {
    // 네트워크 코드 스킵
}
```

**권장:** 방법 A — 소스 세트 오버라이드가 더 깔끔하고 Gradle 이 알아서 처리.

### 6.3 매니페스트 교체

`src/offline/AndroidManifest.xml`:
- `<uses-permission android:name="android.permission.INTERNET" />` **제거**
- `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` **제거**
- `<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />` **제거**
- `<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />` **제거**
- 나머지 모든 권한은 그대로

`src/online/AndroidManifest.xml`:
- 기존 매니페스트 그대로 (모든 권한 포함)
- 필요 시 applicationId 도 `com.jarvis.assistant.online` 로

---

## 7. jarvis-1.0 에서 변경할 코드 목록

### 7.1 BackendHealthManager.kt (오프라인 스텁)
```kotlin
// src/offline/.../network/BackendHealthManager.kt
class BackendHealthManager(...) {
    fun start(isOfflineMode: Boolean = true) {
        // 항상 오프라인 모드로 시작 — 네트워크 콜백 등록 안 함
        setOfflineMode(true)
    }
    fun setOfflineMode(enabled: Boolean) {
        // enabled=false 호출도 무시 — 항상 오프라인
    }
    //나머지 메서드는 호출되지 않음
}
```

### 7.2 ApiClient.kt (오프라인 스텁)
```kotlin
// src/offline/.../network/ApiClient.kt
class ApiClient {
    val baseUrl: String
        get() = ""
    
    fun pingBackend(url: String, callback: (Result) -> Unit) {
        callback(Result.failure(Exception("Offline mode — no network")))
    }
    
    fun get(...): Response {
        throw UnsupportedOperationException("Offline mode")
    }
    
    fun post(...): Response {
        throw UnsupportedOperationException("Offline mode")
    }
}
```

### 7.3 WebSocketClient.kt (오프라인 스텁)
```kotlin
// src/offline/.../network/WebSocketClient.kt
class WebSocketClient {
    var sessionId: String = ""
    fun connect() { /* no-op */ }
    fun disconnect() { /* no-op */ }
    fun updateUrl(url: String) { /* no-op */ }
    // connectionManager 는 항상 Disconnected 상태 반환
}
```

### 7.4 ProviderManager.kt / ProviderRegistry.kt
오프라인에서는 아예 클래스 비우기 또는 존재만 하고 기능 없음.

### 7.5 JarvisViewModel.kt / JarvisBrain.kt

음성 명령이 클라우드 LLM 호출이 필요한 경우 ("모르는 것 질문"), 오프라인에서는:

```kotlin
// 오프라인: 클라우드 호출 불가 → 로컬 메모리(CAG/RAG) 검색 → 없으면 "인터넷 연결 없이 답변할 수 없습니다" 응답
fun handleQuery(query: String): String {
    if (BuildConfig.IS_OFFLINE || settings.isOfflineMode) {
        val localAnswer = memoryEngine.searchLocal(query)
        return if (localAnswer != null) localAnswer else "아ぬ아아... 지금은 인터넷 연결이 필요해서 답변할 수 없어요. 나중에 다시 물어봐 주세요."
    }
    // 온라인 경로
}
```

### 7.6 SettingsScreen.kt

"연결 추가 (수동)" 섹션 추가:
- 백엔드 URL 입력
- WebSocket URL 입력
- LLM 제공자 선택 + API 키
- 활성화 토글 스위치

활성화하면 런타임에 ApiClient/WebSocketClient/ProviderManager 동적 초기화.

---

## 8. 빌드 명령어

```bash
# 오프라인 APK 빌드
cd jarvis/android
./gradlew assembleOfflineRelease

# 출력: app/build/outputs/apk/offline/release/com.jarvis.assistant.offline-1.0.0.apk

# 온라인 APK 빌드
./gradlew assembleOnlineRelease

# 출력: app/build/outputs/apk/online/release/com.jarvis.assistant.online-1.1.0.apk
```

서명: 기존 keystore (`keystore/jarvis-release.jks`) 사용. 빌드 시 `KEYSTORE_PATH` 환경변수 또는 gradle.properties 설정.

---

## 9. jarvis-1.0 APK 크기 예상

기존 APK에서 다음 제거:
- OkHttp + 의존성 (~300KB 절감)
- WebSocket 클라이언트 코드 (~50KB)
- LLM 제공자 코드 (~200KB)
- 네트워크 관련 클래스 (~100KB)

예상 크기: 기존 release APK (~15–20MB) 에서 약 2–3MB 절감 → 약 13–17MB

ONNX 모델 (hey_jarvis.onnx, embedding_model.onnx, melspectrogram.onnx) 은 그대로 포함 — 약 10MB+.

---

## 10. jarvis-1.0 기능 검증 체크리스트

- [ ] wake word "Hey Jarvis" 탐지 (ONNX 로컬) ✅
- [ ] 음성 명령 50개+ 로컬 실행 ✅
- [ ] 메모리 엔진 (CAG/RAG/MAG) 로컬 작동 ✅
- [ ] 7개 루틴 로컬 실행 ✅
- [ ] 장치 컨트롤 (torch, volume, brightness, apps, media 등) ✅
- [ ] 전화/SMS/WhatsApp 로컬 ✅
- [ ] 접근성 UI 자동화 ✅
- [ ] 플로팅 오버레이 ✅
- [ ] 포그라운드 서비스 ✅
- [ ] 부팅 후 복구 ✅
- [ ] 알림 리스너 ✅
- [ ] 퀵 타일 ✅
- [ ] 권한 자동 표시 UI ✅
- [ ] 설정에서 "연결 추가" 옵션으로 API/DB/WebSocket 수동 활성화 ✅
- [ ] INTERNET 권한 없음 (Manifest 확인) ✅
- [ ] 오프라인 상태에서 클라우드 LLM 호출 시도 시 graceful 오류 메시지 ✅

---

## 11. 주의사항 및 리스크

1. **Android SpeechRecognizer**: 기본 Android STT 는 구글 서버에 오디오를 보낼 수 있음 (오프라인 보장 안 됨). `RecognizerIntent.EXTRA_PREFER_OFFLINE_RESULTS` 옵션 추가 고려. 또는 Sherpa-ONNX 로컬 STT 통합 (현재 스텁 상태 — NEW_FEATURES_REPORT.md 참조).

2. **TTS**: Android TTS 엔진은 대부분 디바이스에 사전 설치된 언어를 사용. 완전한 오프라인 TTS(Piper 등)는 아직 통합되지 않음.

3. **위치 정보**: GPS 위치도 결국 네트워크 보조(GPS 단독으로는 느림) — 완전한 오프라인 위치 획득은제한적.

4. **WhatsApp/SMS**: 메시지 전송 자체는 로컬이지만, WhatsApp 이 설치되어 있어야 함.

5. **접근성 서비스**: 사용자가 직접 설정 > 접근성에서 수동으로 활성화해야 함 — 앱에서 자동 활성화 불가 (Android 보안 정책).

---

## 12. 마이그레이션 경로: jarvis-1.0 → jarvis-1.1

사용자가 jarvis-1.0(오프라인)을 쓰다가 온라인 기능을 원하면:
1. jarvis-1.1(온라인) APK 설치
2. 또는 jarvis-1.0 설정에서 "연결 추가"로 API/WS/LLM 수동 활성화 (제한적 온라인 기능)
3. jarvis-1.0과 jarvis-1.1은 별도 앱이므로 데이터 공유 안 됨 — 설정/메모리 마이그레이션 필요 시 별도 export/import 기능 추가 고려

---

## 파일 수정 요약

| 파일 | 변경 내용 |
|---|---|
| `app/build.gradle.kts` | productFlavors (offline/online) 추가, 온라인 전용 의존성 분리 |
| `app/src/main/AndroidManifest.xml` | 기준 매니페스트 (모든 권한) |
| `app/src/offline/AndroidManifest.xml` | 네트워크 권한 4개 제거 |
| `app/src/online/AndroidManifest.xml` | 기존 매니페스트 복사 (모든 권한 유지) |
| `app/src/offline/kotlin/.../network/` | ApiClient, WebSocketClient, BackendHealthManager, AuthRepository, AuthTokenManager, ConnectionManager 스텁 생성 |
| `app/src/offline/kotlin/.../llm/` | ProviderManager, ProviderRegistry 스텁 생성 |
| `ui/screens/SettingsScreen.kt` | "연결 추가 (수동)" 섹션 추가 |
| `settings/SettingsManager.kt` | 수동 연결 관련 SharedPreferences 키 추가 |
| `brain/JarvisBrain.kt` | 오프라인 모드에서 클라우드 호출 불가 처리 |
| `network/BackendHealthManager.kt` | 오프라인 소스 세트에서 스텁으로 대체 |

총 변경 파일 수: 약 15–20개 (대부분 스텁 생성 + 빌드 설정 + UI 추가)

---

*이 계획은 jarvis-1.1(온라인) 계획과 쌍을 이룹니다. 두 앱은 동일한 공통 코드 기반을 공유하며, 네트워크 관련 코드만 플레버별로 포함/제외됩니다.*
