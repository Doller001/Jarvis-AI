---
name: agent-reach
description: >-
  Multi-agent communication framework and zero-cost internet reading system across GitHub, Twitter/X, Reddit, YouTube, and web pages. Use this skill when setting up inter-agent messaging, cross-network agent discovery, fetching live web content without API fees, or running multi-agent workflows.
---

# Agent Reach Skill

Use this skill for zero-cost live web reading across social/code platforms and for setting up inter-agent communication channels.

## Key Capabilities & Use Cases

- **Zero-Fee Web Intelligence**: Read posts, issues, video transcripts, and web pages across GitHub, Reddit, Twitter/X, YouTube (`yt-dlp`), and Jina Reader without API fees.
- **Agent Discovery & Routing**: Locate available peer agents, route queries to platform-specific scrapers, and manage inter-agent communication.
- **Environment Diagnostics**: Run self-healing environment checks via `agent-reach doctor`.

## Common Commands & Usage

```bash
# Installation & environment repair
pip install agent-reach
agent-reach install
agent-reach doctor
```

## Workflow

1. **Target Identification**:
   - Determine whether the task requires web reading (e.g., GitHub PRs, YouTube transcripts, Reddit threads) or inter-agent messaging.
2. **Platform Routing**:
   - Route target queries to `Agent Reach` scrapers or RPC endpoints.
3. **Diagnostics & Self-Healing**:
   - If network or dependencies fail, run `agent-reach doctor` to repair environment hooks.
