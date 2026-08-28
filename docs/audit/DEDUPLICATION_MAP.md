# JARVIS AI — DEDUPLICATION & CONSOLIDATION MAP

> **Purpose:** Explicit mapping of old, duplicated, or overlapping files to their single canonical implementations.

---

## 1. Deployment & Containerization Deduplication

| Old / Duplicate File | Replaced By (Canonical) | Action | Reason |
|---|---|---|---|
| `/render.yaml` | `jarvis/render.yaml` | **DELETE** | Duplicated root configuration with outdated wildcards (`ALLOWED_ORIGINS=*`). |
| `jarvis/backend/render.yaml` | `jarvis/render.yaml` | **DELETE** | Subdirectory duplicate with hardcoded database path. |
| `/Dockerfile` | `jarvis/backend/Dockerfile` | **DELETE** | Root Dockerfile copying full workspace instead of clean backend build. |
| `jarvis/backend/Dockerfile` | `jarvis/backend/Dockerfile` | **MODIFY (Canonical)** | Multi-stage build with non-root user, dynamic `${PORT}` handling, and lightweight health check. |
| `jarvis/render.yaml` | `jarvis/render.yaml` | **MODIFY (Canonical)** | Single source of truth for Render blueprint, deploying `backend/Dockerfile` with `dockerContext: backend`. |

---

## 2. Authentication & Network Layer Deduplication

| Old / Fragmented Logic | Replaced By (Canonical) | Action | Reason |
|---|---|---|---|
| Direct registration in `JarvisViewModel.init` | `AuthRepository.kt` | **CONSOLIDATE** | Eliminates race condition between ViewModel and BackendHealthManager. |
| Token refresh in `ApiClient.refreshAccessToken` | `AuthRepository.kt` | **CONSOLIDATE** | Single manager for token rotation, 401 retry, and device re-registration. |
| `device_registry.json` local file storage | Supabase PostgreSQL `devices` table | **UPGRADE** | Prevents device unregistration on ephemeral Render container restarts. |
| Plaintext JWT refresh tokens | `auth_sessions` table with SHA-256 hashes | **UPGRADE** | Secure refresh token rotation and reuse detection. |

---

## 3. Voice & Acoustic Engine Deduplication

| Old / Fragmented Logic | Replaced By (Canonical) | Action | Reason |
|---|---|---|---|
| Unrestricted `startListeningForCommand()` | `VoiceRuntime.requestCommandListening(req)` | **STRICT GUARD** | Prevents unexpected background STT starts without explicit trigger origin. |
| `micController.forceAcquire()` | Synchronized atomic handoff | **HARDEN** | Eliminates race condition where wake AudioRecord and STT compete for the mic. |
| `VoiceStateMachine.transition` without manual start from disabled | `VoiceEvent.ManualCommandStart` | **ENABLE** | Allows manual mic button to function seamlessly when wake word is disabled. |
