---
name: skill-fleet-publish
description: Author English skills; deliver to Telegram and agent fleet.
type: workflow
version: "1.0.0"
author: saif
license: MIT
whenToUse: >
  When the user asks to build, modify, or publish a skill; send a skill to
  Telegram; or distribute a skill across their multi-agent setup (Hermes,
  Codex, OpenCode, Gemini).
metadata:
  hermes:
    tags: [skill-authoring, skill-distribution, telegram, multi-agent, publishing]
    related_skills: [apk-studio, agi-knowledge]
---

# Skill Fleet Publish

How this user wants skills authored, validated, and shipped across their agent
fleet. Follow this every time a skill is built or updated for them.

## When to Use
- User says "skill banao", "modify karo skills", "add karo skill", "telegram pe
  do", or names a set of agent directories to publish into.
- Any task that ends in a skill artifact meant to live in more than one agent.

## Hard rules (from explicit user corrections this session)
1. **Author skills in ENGLISH.** Even when the source material, doc, or request
   is in another language (e.g. Urdu/Hinglish), the SKILL.md body is English.
   Translate/summarize the source; do not keep the skill in the source tongue.
   (User: "skill english me hi banao".)
2. **Telegram-first delivery.** Send the finished skill to Telegram BEFORE
   distributing it to the agent directories. Deliver as a FILE attachment, not
   pasted text — Telegram truncates text at ~4096 chars but accepts file
   attachments of any size.
   ```bash
   hermes send --to telegram --subject "Skill: <name>" "MEDIA:/abs/path/to/SKILL.md"
   ```
3. **Distribute to the full fleet.** After Telegram, copy the same SKILL.md into
   every target dir (see references/distribution-map.md). Keep byte-identical
   content across all copies.

## Authoring quality bar
- Class-level, not one-off. One skill per CLASS of task, with a rich SKILL.md and
  a `references/` dir for session-specific detail — not a long flat list of
  narrow entries.
- Ground factual claims with real data. When web_search is unavailable, use the
  curl-to-knowledge-API fallback in references/research-without-websearch.md.
- Keep the frontmatter `description` ≤ 60 chars (one sentence, trigger first).
  Longer descriptions get truncated in the skill index and lose the routing
  signal. (This bit me this session: a 66-char description was rejected by the
  creator.)

## Delivery checklist
- [ ] SKILL.md authored in English
- [ ] Sent to Telegram as file attachment (Telegram-first)
- [ ] Copied to all fleet dirs (hermes, codex, opencode, gemini global, gemini shared, project workspace)
- [ ] Byte-identical verify across all copies
