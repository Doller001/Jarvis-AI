---
name: apk-studio
description: Build Android APKs and backends; debug full-stack apps.
type: orchestrator
version: "1.0.0"
author: saif
license: MIT
whenToUse: >
  When the user wants to build an Android app / APK (production or personal
  use), needs a backend/API for it, or needs to debug an app or backend.
metadata:
  hermes:
    tags: [android, apk, mobile, backend, fullstack, debugging, pocketbase]
    related_skills: [pocketbase, ponytail, debug-issue, minimal-fix, review-delta, review-pr, code-review-graph, gitmcp-docs, loop-engineering, developer-roadmap]
---

# APK Studio

## When to Use
- User wants a **production-level** Android APK (signed AAB/APK, real backend, tests, review gate).
- User wants a **personal-use** APK (minimal effort, sideload, maybe no backend).
- User needs a **backend/API** for an app (auth, storage, realtime).
- User needs to **debug** an app or backend (trace, root-cause, minimal fix, review).
- Anything that needs a full-stack Android deliverable composed from the sub-skills below.

Do NOT use for: general knowledge questions, prose, translation, or non-Android coding with no app/backend/debug intent.

One front door for: **production-level full-stack APK**, **personal-use APK**,
**backend/API**, and **debugging**. It does not reinvent tooling — it routes
each concern to the right sub-skill already installed in this repo, then
verifies with a real build/run.

## Capability map (intent -> sub-skill)

| You need to… | Load / apply | Why |
|---|---|---|
| Stand up a backend fast (auth, storage, realtime, REST) | `pocketbase` | Single Go binary or Go lib; SQLite + admin UI; perfect for personal *and* production |
| Write the smallest correct code | `ponytail` (full) | YAGNI, stdlib-first, root-cause fixes, shortest diff |
| Fix a specific bug / CI failure | `minimal-fix` | One problem, smallest diff, verify before claiming done |
| Trace a bug across the codebase | `debug-issue` + `code-review-graph` | Knowledge-graph call chains + blast radius; read only what changed |
| Review a PR / diff / untested change | `review-delta` / `review-pr` | Token-efficient, risk-scored, impacted-tests aware |
| Look up a library/framework/language API | `gitmcp-docs` (context7/playwright/mcp-servers) | Current docs instead of guessing versions |
| Schedule recurring maintenance (CI sweep, issue triage, dependency sweep) | `loop-engineering` + `install-loop` | Designed system that runs the agent on a schedule |
| Pick a learning path for a role/tech | `developer-roadmap` | 82 community roadmaps, topic-level content |

> Note on external repos: several sub-skills reference a `/root/skill-repos/...`
> clone that exists on the author's machine. On this box that path is absent —
> fall back to the live docs/CLI (GitHub URL + `npm`/`go install`/download).
> Use the clone only if it is present; never block on a missing path.

## Four workflows

### A. Production-level full-stack APK
Goal: a signed release artifact (`.aab` for Play Store, `.apk` for sideload/Direct)
wired to a real backend, with tests and a review gate.

1. **Requirements** — pin: target audience, must-have features, offline/online,
   auth model, platforms (phone only? tablet?), min SDK. Clarify if missing.
2. **Frontend stack** (pick one, lazy default first):
   - **Expo / React Native + EAS Build** — fastest path to signed AAB + APK,
     OTA updates, Play/TestFlight upload. Best default for production.
   - **React Native CLI** — more native control, heavier setup.
   - **Flutter** — single codebase for Android + iOS + web; great if UI is rich.
   - **Native (Kotlin + Gradle)** — only when you need deep platform integration.
3. **Backend** — load `pocketbase`:
   - Standalone: `./pocketbase serve`; admin at `:8090/_/`, API at `:8090/api`.
   - Or Go framework: `go mod init app && go mod tidy && go run main.go serve`.
   - Define **collections** (tables), **access rules** per collection, OAuth2/JWT
     auth, realtime SSE, file storage. See the `pocketbase` skill for exact API.
4. **Wire client↔backend** — use PocketBase JS SDK (browser/Node/React Native)
   or Dart SDK; else plain REST. Never hardcode secrets — use env + a config file
   outside the binary.
5. **Build release** (Expo example):
   ```bash
   npx expo prebuild          # optional, generates native project
   npx eas build -p android --profile production   # AAB + APK
   # signing: `keytool -genkeypair` once; EAS stores the key securely
   ```
   Native/Flutter: `./gradlew assembleRelease` / `flutter build appbundle`.
6. **Review gate** — run `review-delta` (or `review-pr`) on the diff; fix
   high-risk findings with `minimal-fix` before shipping.
7. **Verify** — install the APK on a device/emulator, exercise the core flow
   end-to-end against the backend; confirm auth + a write + a read.

### B. Personal-use APK
Goal: a working app on your own phone with the least effort. Apply `ponytail`
ultra/lite; skip store signing, skip most tests, single device.

1. Minimal stack: **Expo Go** or a one-screen Flutter/Kotlin app.
2. Backend: PocketBase **standalone** on a home server / the dev machine; or
   even local-only (no backend) if the app is offline-first.
3. Build: `npx eas build -p android --profile preview` (or `flutter build apk
   --debug`) → sideload. No keystore ceremony for personal use.
4. Lazy rule: if it can be a shortcut/bookmark/PWA, question whether the APK
   needs to exist at all (YAGNI).

### C. Backend only
Load `pocketbase` and follow its skill. Quickest production-ready API:
```bash
# standalone
./pocketbase serve          # :8090/_ admin UI, :8090/api REST
# custom Go
go mod init app && go mod tidy && CGO_ENABLED=0 go build && ./app serve
```
Define collections + rules + auth; expose via SDK. For non-PocketBase needs
(heavy custom logic, ML inference, specific DB), scaffold a Python FastAPI or
Node/Go service and reuse `ponytail` for minimal handlers.

### D. Debugging
1. **Reproduce / confirm** the failure (command + observed vs expected).
2. **Trace** with `debug-issue` + `code-review-graph` (blast radius of the
   suspected file/function) — read only what the change touches.
3. **Root cause, not symptom** (ponytail rule): grep every caller of the
   function you're about to touch; fix once where all callers route through.
4. **Minimal fix** via `minimal-fix`: smallest diff, run relevant tests/lint,
   summarize what changed + verification.
5. If a PR/diff is involved, run `review-delta`/`review-pr` to catch untested
   callers the fix may have broken.

## Cross-cutting principles
- **Ponytail always on** for code: shortest working diff, stdlib/native first,
  no unrequested abstractions, root-cause fixes. Off only when user says so.
- **Token efficiency**: when a code-review-graph MCP server is wired, start any
  review/debug with `get_minimal_context` and `detail_level="minimal"`; escalate
  only when insufficient.
- **Verify, don't claim.** A build that compiles is not "done"; run it, hit the
  endpoint, install the APK. Let the verifier decide if work is complete.
- **Secrets**: never hardcode; never edit `.env`/auth/payments/secret paths
  (denylist). Escalate instead.
- **Docs over guessing**: for any library/framework API, use `gitmcp-docs`
  (context7) to fetch current docs before writing code.

## Deliverable checklist (production)
- [ ] Signed `.aab` (Play) + `.apk` (sideload) built
- [ ] Backend live with auth + at least one collection + access rules
- [ ] Core flow verified end-to-end on a real device/emulator
- [ ] `review-delta` passed (no high-risk unaddressed findings)
- [ ] Secrets externalized; denylist paths untouched
- [ ] (optional) `loop-engineering` scheduled for CI/issue/dependency sweep
