---
name: ponytail
description: >-
  Clean web scraping, data extraction, and content retrieval service optimized for AI agent consumption. Enforces anti-bloat principles, minimal dependencies, and the "lazy senior engineer" decision ladder. Use this skill when scraping web pages, extracting documentation from URLs, converting web content to token-efficient Markdown, or enforcing minimal code implementation.
---

# Ponytail Minimalist Web Scraping & Code Engineering Skill

Use this skill to extract clean web content for AI processing and to enforce anti-bloat minimalism across code updates.

## 1. Web Scraping & Content Extraction
- **Clean Markdown Extraction**: Strip out scripts, navigation bars, footers, and ads, leaving pure content.
- **Token Efficiency**: Convert HTML to token-optimized Markdown snippets suitable for LLM context windows.

## 2. Minimalist Code Decision Ladder (Anti-Bloat Heuristics)
Before writing or generating new code, strictly evaluate the 6-step Decision Ladder:
1. **YAGNI**: *Does this feature or abstraction actually need to exist right now?*
2. **Standard Library**: *Does the language standard library already provide this capability?*
3. **Native Platform**: *Is there a native platform feature available (e.g. native HTML5 `<dialog>` or `<input type="date">`)?*
4. **Existing Dependencies**: *Does an existing project package/dependency already solve this?*
5. **One-Liner Test**: *Can this function be expressed cleanly in 1–3 lines without new utilities?*
6. **Minimum Implementation**: *Only after steps 1–5 pass, write the absolute minimum code required.*
