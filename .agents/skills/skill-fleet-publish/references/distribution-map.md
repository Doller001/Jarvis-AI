# Skill Distribution Map (this user's fleet)

Copy any finished SKILL.md byte-identically into ALL of these. Create the
`{skill_name}/` subdir under each if absent.

## Targets
| Agent | Path |
|---|---|
| Hermes (curator-managed) | `~/.hermes/skills/<name>/SKILL.md` |
| Codex | `~/.codex/skills/<name>/SKILL.md` |
| OpenCode | `~/.config/opencode/skills/<name>/SKILL.md` |
| Gemini (antigravity, global) | `~/.gemini/antigravity-cli/skills/<name>/SKILL.md` |
| Gemini (shared) | `~/.gemini/skills/<name>/SKILL.md` |
| Project workspace | `~/Downloads/raphael-ai-assistant-main/.agents/skills/<name>/SKILL.md` |

## Verify after copy
```bash
for p in \
  ~/.hermes/skills/<name>/SKILL.md \
  ~/.codex/skills/<name>/SKILL.md \
  ~/.config/opencode/skills/<name>/SKILL.md \
  ~/.gemini/antigravity-cli/skills/<name>/SKILL.md \
  ~/.gemini/skills/<name>/SKILL.md \
  ~/Downloads/raphael-ai-assistant-main/.agents/skills/<name>/SKILL.md ; do
  [ -f "$p" ] && echo "OK   $p ($(wc -c < "$p") bytes)" || echo "MISS $p"
done
```

## Notes / gotchas
- The `~/.gemini/antigravity-cli/builtin/skills/*` dir is NOT user-writable —
  publish into `~/.gemini/antigravity-cli/skills/` (the user layer), not `builtin`.
- Codex ships a `.system/` subdir (skill-installer etc.) at top level; your
  skill goes as a sibling, not inside `.system`.
- OpenCode may already hold copies of the 36 skills from `/home/saif/Desktop/skills`
  (e.g. `minimal-fix`, `budget-negotiator`, `loop-*`). New skills are additive.
- If a path doesn't exist yet, `mkdir -p` it first — all are under $HOME.
