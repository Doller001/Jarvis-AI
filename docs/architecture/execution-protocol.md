# JARVIS / AND9 — Execution & Verification Protocol

## 1. State Lifecycle
$$\text{CREATED} \longrightarrow \text{DISPATCHED} \longrightarrow \text{EXECUTING} \longrightarrow \text{EXECUTED} \longrightarrow \text{VERIFIED} \longrightarrow \text{COMPLETED}$$

## 2. Wire Protocol Models
- `DeviceCommandPayload(command_id, action, params, timeout_ms, requires_ack)`
- `DeviceResultPayload(command_id, status, verified, error, evidence)`
- `CancelRequestPayload(command_id, reason)`
- `TaskExecutionReport(plan, results, completed, passed_count, total_count, spoken_summary)`

## 3. Verification Rules
1. `OPEN_APP`: Verifies foreground application package / component state.
2. `VOLUME_SET`: Verifies actual stream volume match.
3. `TOGGLE_TORCH`: Verifies hardware camera flash mode.
4. `PLAY_MEDIA`: Verifies audio stream activity or media player intent dispatch.
