# Jarvis-1.1 (Online) — Separate APK Build Plan

> **Version:** 1.1.0 | **Build Target:** Android 26–34 | **Namespace:** `com.jarvis.assistant.online`
> **APK Name:** `jarvis-1.1-online.apk`
> **Core Principle:** 항상 클라우드 백엔드에 연결. 온라인 기본값, 네트워크 상태 모니터링, WebSocket 연동, 클라우드 LLM 제공자 사용. 오프라인 앱은 아님.

---

## 1. 무엇이 다른가 — jarvis-1.1 vs jarvis-1.0 vs 기존 단일 앱

| 항목 | 기존 단일 앱 | jarvis-1.0 (오프라인) | jarvis-1.1 (온라인) |
|---|---|---|---|
| applicationId | `com.jarvis.assistant` | `com.jarvis.assistant.offline` | `com.jarvis.assistant.online` |
| versionName | 1.0.0 | 1.0.0 | 1.1.0 |
| INTERNET 권한 | 있음 | **없음** | **있음** |
| 오프라인 모드 토글 | 있음 | 불필요 | 선택적 (백업용) |
| 백엔드 URL | 설정에서 변경 가능 | 설정에서 수동 추가 가능 | 기본 설정 + 자동 연결 |
| 클라우드 LLM | 5개 제공자 | 사용 불가 | 5개 제공자 활성화 |
| WebSocket | 온라인/오프라인 전환 | 스텁 (비활성화) | 항상 활성화 + 자동 재연결 |
| BackendHealthManager | 온라인 기본, 오프라인 토글 | 항상 오프라인 | 항상 온라인 + 헬스체크 |

---

## 2. jarvis-1.1 이 jarvis-1.0 과 공유하는 것 (공통 코드 100%)

두 앱은 **동일한 기능, 동일한 메서드, 동일한 백엔드 액션, 동일한 데이터 모델**을 가짐:

- 모든 voice engine 클래스 (wake word, STT, TTS, VAD, DSP)
- 모든 memory 클래스 (SQLite CAG/RAG/MAG, MemoryEngine, DecisionRouter)
- 모든 device controller 클래스 (16개)
- 모든 action engine 클래스 (LocalTaskPlanner, ActionExecutor, 모든 어댑터)
- 모든 routine 클래스 (7개 루틴 + RoutineEngine)
- 모든 accessibility 클래스 (AccessibilityController, GestureController, NodeFinder, ScreenInspector)
- 모든 overlay 클래스 (FloatingAssistantState, OverlayController, OverlayWindowManager, JarvisFloatingOverlay, JarvisHologram, JarvisMicButton)
- 모든 UI 화면 (HomeScreen, ConversationScreen, MemoryScreen, OnboardingScreen, ProvidersScreen, RoutinesScreen, SettingsScreen)
- 모든 services (JarvisForegroundService, JarvisOverlayService, JarvisQuickTileService, JarvisAccessibilityService, BootRecoveryReceiver, ReminderReceiver, TimerReceiver, JarvisDeviceAdminReceiver, JarvisNotificationListenerService)
- 모든 모델 클래스 (ActionModels, ExecutionModels, CanonicalProtocol, PermissionModels, FloatingAssistantState)

**차이점:** 네트워크 관련 클래스만 다름 (jarvis-1.0 은 스텁, jarvis-1.1 은 실제 구현).

---

## 3. jarvis-1.1 의 핵심 특징

### 3.1 항상 온라인 기본 동작

앱 시작 시:
1. `BackendHealthManager.start(isOfflineMode = false)` 호출
2. ConnectivityManager.NetworkCallback 등록 → 네트워크 상태 변경 즉시 감지
3. 백엔드 HTTP ping 실행 (기본 엔드포인트: `https://jarvis-ai-59qd.onrender.com`)
4. WebSocket 연결 시도 (백엔드 `/ws` 엔드포인트)
5. 30초 주기 헬스 체크 시작

### 3.2 백엔드 연결 관리

**기본 백엔드 URL:**
```
https://jarvis-ai-59qd.onrender.com
```

**커스텀 백엔드 URL 설정:** SettingsScreen 에서 변경 가능. 변경 즉시:
- `ApiClient.baseUrl` 업데이트
- `WebSocketClient` URL 업데이트
- 즉시 헬스체크 재실행

**백엔드 엔드포인트:**
| 엔드포인트 | 용도 |
|---|---|
| `GET /health` | 헬스 체크 (HTTP ping) |
| `GET /v1/chat/completions` | LLM 채팅 (OpenAI 호환) |
| `POST /chat` | 범용 채팅 alias |
| `POST /ask` | 질문 alias |
| `POST /query` | 쿼리 alias |
| `POST /generate` | 생성 alias |
| `POST /completions` | 완료 alias |
| `POST /message` | 메시지 alias |
| `WS /ws` | WebSocket 실시간 연결 |

### 3.3 클라우드 LLM 제공자 (5개)

| 제공자 | 모델 예시 | 특징 |
|---|---|---|
| **NVIDIA Nemotron** | Nemotron-4-340B | Render 배포, 우선 제공자 |
| **Groq** | Llama-3.1-70B, Gemma-7B | 빠른 추론 |
| **OpenRouter** | 다양한 모델 라우팅 | 다중 모델 접근 |
| **Gemini** | Gemini 1.5 Pro/Flash | Google 모델 |
| **Ollama** | 로컬/원격 Ollama 서버 | 자체 호스팅 |

**제공자 전환:** ProvidersScreen 에서 선택, 실시간 health ping 으로 상태가 좋은 제공자 자동 선호.

### 3.4 WebSocket 실시간 연결

- 8초 keep-alive ping
- 자동 재연결 (exponential backoff + jitter)
- 연결 상태: CONNECTED / CONNECTING / DISCONNECTED
- 장치 ID (`device_${UUID}`) 로 세션 식별
- JWT 토큰 인증 (Supabase/PostgreSQL 기반)

### 3.5 온라인 기능 목록

jarvis-1.1 이 추가로 제공하는 클라우드 의존 기능:

1. **오픈 엔딩 질문 답변** — "오늘 뉴스 알려줘", "양자역학 설명해줘" 등 로컬 메모리에 없는 질문
2. **웹 검색 결과 통합** — DuckDuckGo 검색 백엔드 연동
3. **다중 제공자 자동 라우팅** — 한 제공자 다운 시 다른 제공자로 자동 전환
4. **백엔드 도구 실행** — 20개+ 백엔드 도구 (brightness, DND, ringer, screenshot, alarm, timer, reminder, location, calendar, daily briefing, lock screen 등) — 백엔드에서 처리 후 디바이스로 dispatch
5. **실시간 WebSocket 메시지** — 백엔드에서 푸시 알림/메시지 수신
6. **인증/세션 관리** — JWT 토큰 자동 갱신, PostgreSQL 세션 저장

---

## 4. jarvis-1.1 빌드 구성 상세

### 4.1 build.gradle.kts (온라인 플레버)

```kotlin
android {
    flavorDimensions += "mode"
    
    productFlavors {
        create("offline") { ... }  // jarvis-1.0 용
        
        create("online") {
            dimension = "mode"
            applicationId = "com.jarvis.assistant.online"
            versionName = "1.1.0"
            
            // 온라인: 모든 네트워크 의존성 포함
        }
    }
    
    sourceSets {
        getByName("online") {
            java.srcDirs("src/online/kotlin")
            res.srcDirs("src/online/res")
        }
    }
}

dependencies {
    // 공통 의존성
    
    // 온라인 전용 의존성
    onlineImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    // WebSocket 은 OkHttp 에 포함
}
```

### 4.2 jarvis-1.1 전용 소스 세트 오버라이드

`src/online/kotlin/` 에는 백엔드 관련 실제 구현 포함 (이미 `src/main/` 에 있음 — 온라인 플레버는 메인 소스 그대로 사용).

실제 오버라이드 필요한 경우:
- 네트워크 관련 커스터마이징 (예: 온라인 전용 백엔드 URL 기본값)
- 온라인 전용 UI 요소 (예: "현재 백엔드 상태" 표시)

### 4.3 매니페스트 (온라인)

`src/online/AndroidManifest.xml` = 기존 `src/main/AndroidManifest.xml` 과 동일 (모든 권한 포함).

---

## 5. jarvis-1.1 에서 사용할 백엔드 정보

### 5.1 Render.com 배포 백엔드

**현재 배포 엔드포인트:**
```
https://jarvis-ai-59qd.onrender.com
```

**배포 설정:** `jarvis/render.yaml`
- Docker 이미지 빌드
- 포트: `${PORT:-8000}`
- Health check: `/health/live`
- 환경 변수: LLM 제공자 API 키, Supabase URL/키, WS_AUTH_TOKEN 등

**Dockerfile:** `jarvis/backend/Dockerfile` — multi-stage, Python 3.12, FastAPI + Uvicorn

### 5.2 백엔드 구조 요약

```
jarvis/backend/
├── app/
│   ├── main.py                 # FastAPI 진입점
│   ├── api/
│   │   ├── routes.py           # 일반 API 라우트
│   │   ├── auth_routes.py      # 인증 라우트
│   │   ├── openai_compat.py    # OpenAI 호환 엔드포인트
│   │   └── providers_api.py    # 제공자 관리 API
│   ├── agent/
│   │   ├── orchestrator.py     # L1→L3 의도 해석 파이프라인
│   │   ├── intent_resolver.py  # 의도 해결
│   │   ├── planner.py          # 작업 계획
│   │   ├── normalizer.py       # 정규화
│   │   ├── execution_models.py # 실행 모델
│   │   └── execution_orchestrator.py
│   ├── llm/
│   │   ├── gateway.py          # LLM 게이트웨이
│   │   ├── router.py           # 제공자 라우팅
│   │   ├── registry.py         # 제공자 레지스트리
│   │   ├── circuit_breaker.py  # 회로 차단기
│   │   ├── retry_policy.py     # 재시도 정책
│   │   └── providers/          # NVIDIA, Groq, OpenRouter, Gemini, Ollama
│   ├── tools/
│   │   ├── registry.py         # 도구 레지스트리 (20+ 도구)
│   │   └── executor.py         # 도구 실행
│   ├── memory/
│   │   ├── memory_manager.py   # 메모리 관리
│   │   └── persistent_store.py # 영구 저장 (PostgreSQL)
│   ├── db/
│   │   └── supabase_client.py  # Supabase 클라이언트
│   ├── realtime/
│   │   ├── ws.py               # WebSocket 핸들러
│   │   ├── protocol.py         # 프로토콜 정의
│   │   ├── canonical_protocol.py
│   │   ├── connection_manager.py
│   │   ├── message_router.py
│   │   └── command_registry.py
│   ├── security/
│   │   ├── auth.py             # 인증
│   │   ├── jwt_manager.py      # JWT 관리
│   │   ├── token_manager.py    # 토큰 관리
│   │   ├── device_registry.py  # 장치 등록
│   │   ├── capability.py       # 기능 관리
│   │   ├── redaction.py        # 정보 제거
│   │   └── exceptions.py
│   └── retrieval/
│       └── music_index.py      # 음악 검색 인덱스
├── tests/                      # 전체 테스트 스위트
├── pyproject.toml
├── requirements.txt
├── .env.example
├── render.yaml
└── Dockerfile
```

### 5.3 데이터베이스

- **Supabase (PostgreSQL):** 장치 등록, 세션 토큰, 사용자 데이터
- **SQLite (로컬):** 백엔드 자체 메모리 (jarvis_memory.db) — 빠른 캐시

### 5.4 보안

- CORS 설정: 허용된 출처만
- JWT 인증: HMAC 기반 토큰, 2분 전 프로액티브 갱신
- 토큰 재사용 감지: PostgreSQL 에 해시 저장, 재사용 시 세션 회전
- 정보 제거 (redaction): 민감 정보 필터링

---

## 6. jarvis-1.1 Android 네트워크 구성

### 6.1 network_security_config.xml

기존 파일 유지 (`app/src/main/res/xml/network_security_config.xml`). 커스텀 백엔드_URL 이 HTTP 인 경우 네트워크 보안 구성 업데이트 필요할 수 있음.

### 6.2 OkHttp 설정 (ApiClient.kt)

```kotlin
class ApiClient {
    var baseUrl: String = "https://jarvis-ai-59qd.onrender.com"
    
    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // HTTP/2 multiplexing, TLS 사전 연결
}
```

### 6.3 WebSocket 설정 (WebSocketClient.kt)

```kotlin
class WebSocketClient {
    private var webSocket: WebSocket? = null
    private var keepAliveJob: Job? = null
    
    fun connect() {
        // OkHttp WebSocket 연결
        // 8초 주기 keep-alive ping
        // exponential backoff 재연결 (1s, 2s, 4s, 8s, 최대 30s)
    }
    
    fun disconnect() {
        keepAliveJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }
}
```

### 6.4 BackendHealthManager.kt (온라인 버전 = 기존 구현 그대로)

기존 구현 유지 — 온라인 모드에서:
- NetworkCallback 등록
- HTTP ping (30초 주기)
- WebSocket 연결/재연결
- 상태: CONNECTED / CONNECTING / DEGRADED / OFFLINE

---

## 7. jarvis-1.1 에서 비활성화할 수 있는 것 (선택적)

### 7.1 오프라인 모드 토글 (백업용)

SettingsScreen 에 "오프라인 모드" 토글 유지 가능 — 백엔드 연결 불가 시 장치 컨트롤 등 로컬 기능은 계속 작동.

활성화 시:
- BackendHealthManager.setOfflineMode(true)
- WebSocket 연결 해제
- HTTP 요청 중단
- 로컬 기능만 작동 (wake word, 기억, 장치 컨트롤, 루틴 등)

### 7.2 클라우드 LLM 제공자 비활성화

각 제공자별 토글 가능 (ProvidersScreen). 모두 비활성화하면:
- 오픈 엔딩 질문은 로컬 메모리(CAG/RAG)로만 답변
- 없으면 "인터넷 연결이 필요합니다" 메시지

---

## 8. jarvis-1.1 APK 서명

기존 keystore 사용:
```
keystore/jarvis-release.jks
alias: jarvis-release
password: jarvis123 (기본)
```

build.gradle.kts signingConfigs:
```kotlin
signingConfigs {
    create("release") {
        val keystorePath = project.findProperty("KEYSTORE_PATH") 
            ?: System.getenv("JARVIS_KEYSTORE_PATH")
            ?: "${rootProject.projectDir}/keystore/jarvis-release.jks"
        // ...
    }
}
```

APK 빌드 시 환경변수 설정:
```bash
export JARVIS_KEYSTORE_PATH=/path/to/keystore.jks
export JARVIS_KEYSTORE_PASSWORD=secret
export JARVIS_KEY_ALIAS=jarvis-release
export JARVIS_KEY_PASSWORD=secret
```

또는 `gradle.properties` 에 설정.

---

## 9. 빌드 명령어

```bash
cd jarvis/android

# 환경변수 설정 (키스토어)
export JARVIS_KEYSTORE_PATH=$(pwd)/keystore/jarvis-release.jks
export JARVIS_KEYSTORE_PASSWORD=jarvis123
export JARVIS_KEY_ALIAS=jarvis-release
export JARVIS_KEY_PASSWORD=jarvis123

# 온라인 APK 빌드
./gradlew assembleOnlineRelease

# 출력: app/build/outputs/apk/online/release/com.jarvis.assistant.online-1.1.0.apk

# 디버그 빌드 (서명 없음, 빠른)
./gradlew assembleOnlineDebug
# 출력: app/build/outputs/apk/online/debug/com.jarvis.assistant.online-1.1.0-debug.apk
```

---

## 10. jarvis-1.1 APK 크기 예상

jarvis-1.0 과 거의 동일 + OkHttp (+의존성) 추가:

- ONNX 모델: ~10MB
- Compose + 의존성: ~5–8MB
- OkHttp + WebSocket: 추가 ~300KB
- 전체 예상: 약 15–20MB (기존 release APK 와 유사)

---

## 11. jarvis-1.1 기능 검증 체크리스트

- [ ] 백엔드 헬스체크 (HTTP ping) 정상 동작 ✅
- [ ] WebSocket 연결 및 keep-alive ✅
- [ ] 네트워크 변경 시 자동 재연결 (ConnectivityManager 콜백) ✅
- [ ] 클라우드 LLM 제공자 5개 중 선택 가능 ✅
- [ ] 오픈 엔딩 질문 → 백엔드 LLM 호출 → 응답 ✅
- [ ] 백엔드 도구 20개+ 실행 가능 ✅
- [ ] 백엔드 다운 시 graceful 오류 + 로컬 대체 ✅
- [ ] 백엔드 URL 변경 즉시 적용 ✅
- [ ] JWT 토큰 자동 갱신 ✅
- [ ] WebSocket 연결 상태 UI 표시 ✅
- [ ] 오프라인 모드 토글 (백업용) ✅
- [ ] INTERNET 권한 포함 (Manifest 확인) ✅
- [ ] 모든 로컬 기능 (wake word, 메모리, 장치 컨트롤, 루틴 등) 오프라인 모드에서도 작동 ✅

---

## 12. jarvis-1.1 배포 고려사항

### 12.1 Render.com 무료 티어

- 콜드 스타트: 30–60초 (첫 요청 시)
- 유휴 타임아웃: 일정 시간 비활성 시 서버 절전
- 주기적 헬스체크로 연결 유지 필요

### 12.2 백업/대안 백엔드

사용자가 자체 hosting 할 수 있도록:
- `jarvis/backend/` 전체를 다른 서버/VPS 에 배포 가능
- `.env` 파일에 API 키, Supabase URL 등 설정
- Docker 로 실행: `docker build -t jarvis-backend . && docker run -p 8000:8000 jarvis-backend`
- Android 앱 SettingsScreen 에서 새 백엔드 URL 입력

### 12.3 로컬 백엔드 테스트

개발 중 로컬 백엔드 사용:
```bash
cd jarvis/backend
./scripts/run_backend.sh
# 또는 직접: uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Android 앱에서 백엔드 URL: `http://10.0.2.2:8000` (Android 에뮬레이터) 또는 `http://<컴퓨터_IP>:8000` (실물 기기).

---

## 13. jarvis-1.0 ↔ jarvis-1.1 데이터 호환성

두 앱은 별도 applicationId 이므로:
- SharedPreferences 공유 안 됨
- SQLite 데이터베이스 공유 안 됨
- 설치/제거 독립적

**데이터 마이그레이션 옵션 (향후 추가 가능):**
1. jarvis-1.0 → jarvis-1.1: SettingsScreen 에서 "데이터 내보내기" → JSON 파일로 내보내기 → jarvis-1.1 에서 "가져오기"
2. 백엔드 통한 동기화: 두 앱이 동일한 백엔드 계정을 사용하면 메모리가 백엔드에 저장되고 다른 기기에서도 접근 가능 (Supabase)

**현재 상태:** 데이터 마이그레이션 기능 없음 — 사용자가 두 앱을 독립적으로 사용.

---

## 14. 파일 수정/생성 요약

### jarvis-1.1 에 필요한 변경 (주: 대부분 공통 코드 그대로 사용, 빌드 설정만 추가)

| 파일 | 변경 내용 |
|---|---|
| `app/build.gradle.kts` | productFlavors 에 online 추가 (offline 과 쌍) |
| `app/src/online/AndroidManifest.xml` | 기존 매니페스트 복사 (모든 권한 유지, applicationId 확인) |
| `app/src/online/kotlin/` | 필요 시 오버라이드 파일 (대부분 main 과 동일) |

**jarvis-1.1 은 사실상 기존 메인 코드의 온라인 플레버이므로 추가 코드 변경 거의 없음.** 기존 `src/main/` 코드가 이미 온라인 기능을 모두 포함.

---

## 15. 두 앱 동시 설치

Android 는 동일 패키지의 다른 versionCode 는 업그레이드, 다른 applicationId 는 별도 설치 허용.

jarvis-1.0 (`com.jarvis.assistant.offline`) 과 jarvis-1.1 (`com.jarvis.assistant.online`) 은:
- 동일 기기에 동시 설치 가능 ✅
- 각각 독립 실행 ✅
- 아이콘 이름도 구분 가능 (strings.xml 리소스 별도):

`src/offline/res/values/strings.xml`:
```xml
<string name="app_name">Jarvis Offline</string>
```

`src/online/res/values/strings.xml`:
```xml
<string name="app_name">Jarvis Online</string>
```

또는 둘 다 "Jarvis AI"로 하고 버전 표시로 구분.

---

## 16. 권장 배포 시나리오

### 시나리오 A: 별도 앱스토어 제출
- jarvis-1.0 과 jarvis-1.1 을 별도 앱으로 Play Store 제출
- 각각 다른 아이콘/라벨
- 사용자는 필요에 따라 둘 중 하나 또는 둘 다 설치

### 시나리오 B: 단일 앱에 두 가지 빌드 변종 (사이드로딩)
- jarvis-1.0.apk + jarvis-1.1.apk 파일 제공
- 사용자가 직접 설치
- 사이드로딩 시 `REQUEST_INSTALL_PACKAGES` 권한 필요 (별도 앱에 추가)

### 시나리오 C: 하나의 APK 로 통합 (권장하지 않음)
- productFlavors 대신 단일 앱에서 네트워크 연결 상태에 따라 동작 변경
- 기존 구현과 동일 — 사용자가 원한 "분리된 APK" 요구사항에 부합하지 않음

---

## 17. jarvis-1.1 과 관련된 기존 문서

| 문서 | 위치 | 관련 내용 |
|---|---|---|
| WHAT-IT-DOES-AND-DOSNT.md | `jarvis/android/` | 전체 기능/제한 사항 |
| FEATURES_AND_FUNCTIONS.md | 루트 | 모든 기능 상세 목록 |
| NEW_FEATURES_REPORT.md | 루트 | 신규 기능 제안 및 우선순위 |
| BACKEND_DEPLOYMENT.md | `jarvis/docs/deployment/` | 백엔드 배포 상세 |
| docs/architecture/API_CONTRACT.md | `jarvis/docs/architecture/` | API 계약 |
| docs/architecture/VOICE_ARCHITECTURE.md | `jarvis/docs/architecture/` | 음성 아키텍처 |
| docs/architecture/AUTH_ARCHITECTURE.md | `jarvis/docs/architecture/` | 인증 아키텍처 |
| jarvis/render.yaml | `jarvis/` | Render 배포 설정 |
| jarvis/backend/Dockerfile | `jarvis/backend/` | Docker 빌드 설정 |
| jarvis/backend/.env.example | `jarvis/backend/` | 환경 변수 템플릿 |
| docs/roadmap.md | `jarvis/docs/` | 로드맵 |
| JARVIS_STRUCTURE.png | 루트 | 구조 다이어그램 |
| JARVIS_STRUCTURE.png | 루트 | 구조 다이어그램 |

---

## 18. Troubleshooting

### 18.1 온라인 APK 에서 "백엔드에 연결할 수 없음" 오류

1. 인터넷 연결 확인
2. 백엔드 URL 이 올바른지 확인 (SettingsScreen)
3. 백엔드가 실행 중인지 확인 (Render 대시보드 또는 직접 curl)
4. 방화벽/프록시가 443 포트 차단하는 지 확인
5. `BackendHealthManager` 로그 확인 (Logcat: `BackendHealthManager`)

### 18.2 WebSocket 연결 끊김 후 재연결 안 됨

1. `WebSocketClient` 로그 확인
2. 백엔드 `/ws` 엔드포인트 확인
3. `JARVIS_WS_AUTH_TOKEN` 환경변수 설정 확인 (없으면 기본 오픈 — 보안 이슈)
4. 네트워크 변경 시 `ConnectivityManager` 콜백이 재연결 트리거하는지 확인

### 18.3 LLM 제공자 오류

1. 제공자 API 키 확인 (백엔드 `.env`)
2. 백엔드 로그 확인
3. `circuit_breaker` 가 트리거되었는지 확인 (일시적 차단)
4. 다른 제공자로 전환 시도 (ProvidersScreen)

### 18.4 빌드시 서명 오류

1. keystore 파일 경로가 올바른지 확인
2. `JARVIS_KEYSTORE_PATH` 환경변수 또는 `gradle.properties` 설정 확인
3. 디버그 빌드로 테스트: `./gradlew assembleOnlineDebug`

---

*이 계획은 JARVIS_OFFLINE_BUILD_PLAN.md 와 쌍을 이룹니다. 두 앱은 동일한 코드 기반을 공유하며, network connectivity 관련 부분만 플레버별로 다르게 구성됩니다.*
