# JARVIS / AND9 — Risk Register

| Risk ID | Description | Severity | Likelihood | Mitigation Strategy |
|---|---|---|---|---|
| R-01 | Unbounded PROCESSING hang on cloud disconnect | Critical | High | Android-side 6.0s watchdog timer in `VoiceRuntime` + OkHttp 5.0s read timeouts. |
| R-02 | False action completion claims | High | Medium | Enforced `DISPATCHED != EXECUTED != VERIFIED = COMPLETED` rule with step telemetry. |
| R-03 | Wake-word false accept saturation | Medium | Medium | Adaptive noise floor calibration + temporal 5-window majority vote. |
| R-04 | Audio session / Volume mode corruption | High | Low | `AudioSessionManager` snapshotting volume and mode, ensuring strict invariants. |
| R-05 | Service killed by Android OS battery managers | High | Medium | Foreground service with ongoing sticky notification + partial wake locks when active. |
