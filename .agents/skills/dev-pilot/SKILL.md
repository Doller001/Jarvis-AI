---
name: dev-pilot
description: Autonomous dev: build, audit, fix bugs, secure, remember.
type: orchestrator
version: "1.0.0"
author: saif
license: MIT
whenToUse: >
  When the user wants an autonomous full-stack dev agent that can build
  frontend + backend, audit/understand a codebase, find & fix bugs, run tests,
  review code, check security, dedupe files, fetch data/docs, remember the
  codebase across sessions, and auto-create/modify skills. Use for any
  end-to-end coding, maintenance, or self-improving-agent task.
metadata:
  hermes:
    tags: [fullstack, orchestrator, audit, debugging, security, memory, code-review, skills, automation]
    related_skills: [apk-studio, agi-knowledge, ponytail, debug-issue, minimal-fix, review-delta, review-pr, code-review-graph, explore-codebase, build-graph, refactor-safely, loop-engineering, install-loop, pocketbase, gitmcp-docs, mcp-memory, developer-roadmap, i-have-adhd]
---

# Dev-Pilot — Autonomous Full-Stack Dev Agent

One orchestrator that mixes every capability the user asked for. It does not
reinvent tooling — it routes each concern to an already-installed sub-skill,
then verifies with a real run. Think of it as the "main brain" that drives the
other skills.

## When to Use
- Build a full-stack app (frontend + backend) end to end.
- Audit / understand an existing codebase, then remove double or unwanted files.
- Find and fix bugs (root-cause, not symptoms), run tests, review code.
- Check security of code/secrets/config.
- Fetch data/docs from the web and integrate findings.
- Remember the codebase + user preferences across sessions (persistent memory).
- Auto-create or modify skills so the agent improves itself over time.
- Set up recurring automation (loop engineering) for maintenance tasks.
- Do NOT use for: pure prose / translation / non-engineering questions.

## Capability -> Sub-skill routing

| Intent | Load / apply | Why |
|---|---|---|
| Understand / map a codebase | `explore-codebase` + `build-graph` + `code-review-graph` | Knowledge graph of functions/classes/calls; read only what a change touches |
| Remember full codebase + user across sessions | `mcp-memory` + skill's own memory tool | Persistent semantic memory, not keyword search |
| Build backend (auth/storage/realtime/REST) | `pocketbase` | Single Go binary or Go lib; SQLite + admin UI |
| Build frontend / full-stack APK | `apk-studio` | Production + personal APK workflows |
| Write smallest correct code | `ponytail` (full) | YAGNI, stdlib-first, root-cause, shortest diff |
| Find & fix a specific bug / CI failure | `minimal-fix` | One problem, smallest diff, verify before "done" |
| Trace a bug across call chains | `debug-issue` + `code-review-graph` | Blast-radius + impact analysis |
| Code review (PR / diff / untested change) | `review-delta` / `review-pr` / `review-changes` | Token-efficient, risk-scored, test-coverage aware |
| Refactor safely / kill dead code | `refactor-safely` | Dependency analysis, preview before apply |
| Recurring automation / scheduled loops | `loop-engineering` + `install-loop` | Designed system that runs the agent on a schedule |
| Fetch docs / library / framework API | `gitmcp-docs` (context7/playwright/mcp-servers) | Current docs instead of guessing versions |
| Learning path for a role/tech | `developer-roadmap` | 82 community roadmaps |
| Output shaping for focus | `i-have-adhd` | Lead with next action, number steps, visible wins |
| AGI / general-intelligence framing | `agi-knowledge` | Grounded reference, human-oversight caveat |

> External-repo note: several sub-skills reference a `/root/skill-repos/...`
> clone absent on this box — fall back to live docs/CLI (GitHub + npm/go
> install/download). Never block on a missing path.

## Standard operating loop (run this for any task)

```
1. CONTEXT   -> build/read the code-review-graph; load mcp-memory for user prefs
2. PLAN      -> scope the change; use ponytail ladder (YAGNI first)
3. BUILD     -> backend (pocketbase) + frontend (apk-studio) or targeted code
4. TEST      -> run relevant tests/lint; minimal-fix only what fails
5. REVIEW    -> review-delta / review-pr; fix high-risk findings
6. SECURITY  -> scan secrets, denylist paths, auth at trust boundaries
7. AUDIT     -> dedupe / remove unwanted files; refactor-safely dead code
8. REMEMBER  -> write durable facts to memory + mcp-memory
9. IMPROVE   -> if a repeatable capability emerged, auto-create/modify a skill
10. VERIFY   -> run it for real; report what now works
```

### 1. Audit & understand codebase
- Build graph: `build-graph` (or `build_or_update_graph_tool` if CRG MCP is
  wired). Then `explore-codebase` for architecture; `code-review-graph` for
  blast radius.
- Produce a map: entry points, core modules, external deps, risk hotspots.

### 2. Remove double / unwanted files (dedup audit)
- Find duplicates: `search_files` glob + content hash compare (execute_code or
  terminal `fd`/`rg --files` + `sha256sum`).
- Flag: exact dupes, near-dupes, stale build artifacts (`node_modules`,
  `__pycache__`, `.next`, `dist`, `.code-review-graph`), and orphaned files not
  imported anywhere (use graph's `children_of` / `find_large_functions`).
- Delete only after confirming nothing references them (graph + grep). Prefer
  `refactor-safely` dead-code detection over blind `rm`.

### 3. Find & fix bugs
- Reproduce/confirm (command + observed vs expected).
- Trace with `debug-issue` + CRG blast radius — read only impacted code.
- Root cause, not symptom (ponytail rule): grep every caller of the function
  you're about to touch; fix once where all callers route through.
- `minimal-fix`: smallest diff, run tests, summarize change + verification.

### 4. Testing
- Run the project's own suite (from AGENTS.md / project skills).
- Add the smallest runnable check for non-trivial logic (assert/`__main__` /
  one `test_*.py`). No frameworks unless asked (YAGNI).
- Loop-verify: `loop-verifier` / `loop-budget` keep scope bounded on long runs.

### 5. Data fetching
- Web: `web_search` / `web_extract` (or `gitmcp-docs` context7 for library
  docs). Fetch, then fold findings into code/decisions. Cite sources.

### 6. Memory — always remember
- Use the persistent `memory` tool for durable user facts/preferences/env.
- Use `mcp-memory` (addToMCPMemory / searchMCPMemory) for cross-session semantic
  recall of the user and project.
- After each meaningful task, write: what changed, why, open risks, user prefs.

### 7. Auto create & modify skills (self-improvement)
- When a repeatable workflow emerges, capture it with `skill_manage`:
  `create` (first time) or `patch`/`edit` (update).
- Keep frontmatter valid: `name`, `description` ≤60 chars (one sentence, trigger
  first), `type`, `whenToUse`, `metadata.hermes.{tags,related_skills}`.
- Ground new skills in real steps + verification; never ship a stub.

### 8. Full-stack build
- Backend: `pocketbase` (standalone `./pocketbase serve` or Go lib).
- Frontend / APK: `apk-studio` (Expo/Flutter/Kotlin). For web: React/Vite or
  FastAPI+HTML. Wire client↔backend via SDK; externalize secrets.
- Verify end-to-end on a device/emulator or live endpoint.

### 9. Security check
- Secrets: never hardcode; never edit `.env`/auth/payments/secret paths
  (denylist) — escalate instead.
- Trust boundaries: validate input, handle errors that prevent data loss, set
  access rules per collection (pocketbase), least-privilege.
- Ponytail rule: never lazy about security/validation/accessibility.
- Flag: hardcoded keys, `eval`/`exec` on untrusted input, missing auth on
  endpoints, overly-permissive CORS/access rules, SQL string concat.

### 10. Loop engineering (recurring automation)
- Map a recurring pain to a pattern (`daily-triage`, `ci-sweeper`,
  `dependency-sweeper`, `issue-triage`, `post-merge-cleanup`).
- `install-loop` front door: `npx @cobusgreyling/loop init . --pattern <p>
  --tool <t>` then `loop doctor .`. Week-one = report-only, no auto-merge.

## Cross-cutting principles
- **Ponytail always on** for code: shortest working diff, stdlib/native first,
  root-cause fixes, no unrequested abstractions.
- **Verify, don't claim.** A compiling build is not "done" — run it, hit the
  endpoint, install the APK. The verifier decides completion.
- **Token efficiency:** when CRG MCP is wired, start with
  `get_minimal_context` + `detail_level="minimal"`; escalate only when needed.
- **Safety first.** Destructive ops (rm -rf, force push, migrations, drop table)
  require confirmation. Denylist paths untouched.
- **Self-improving.** Every solved class of problem becomes a skill via
  `skill_manage` so the agent gets better without re-learning.

## Deliverable checklist
- [ ] Codebase understood + graph built; map produced
- [ ] Duplicates/unwanted files removed (graph-verified, not blind)
- [ ] Bugs fixed root-cause; tests pass
- [ ] Code review passed (no high-risk unaddressed findings)
- [ ] Security scan clean (no hardcoded secrets, trust boundaries safe)
- [ ] Backend + frontend built and verified end-to-end
- [ ] Memory updated (memory tool + mcp-memory)
- [ ] Repeatable capability captured as/updated in a skill
- [ ] (optional) Loop scheduled for recurring maintenance
