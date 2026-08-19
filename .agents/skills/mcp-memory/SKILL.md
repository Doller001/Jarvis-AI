---
name: mcp-memory
description: >-
  Provides persistent knowledge graph and key-value memory storage across AI agent sessions using Cloudflare serverless DBs. Use this skill when managing persistent memories, saving long-term user preferences, creating entity knowledge graphs, or recalling context from past interactions.
---

# MCP Memory Skill

Use this skill to maintain persistent long-term memory for AI agents using entity-relation knowledge graphs, Cloudflare D1 + Vectorize vector search, and key-value observation stores.

## Key Capabilities & Use Cases

- **Cross-Session Persistence**: Store user preferences, coding habits, and project architectural decisions across independent agent chat turns.
- **Hybrid Semantic Search**: Combine vector embeddings (`@cf/baai/bge-m3`) and relational SQLite metadata for accurate context retrieval.
- **Entity & Relation Management**: Register entities, relate concepts, and update factual observations over time.

## Operational Workflow

1. **Saving Memory**:
   - Extract core concepts, preferences, or decisions from user instructions.
   - Invoke memory tools (`save_memory` / `remember`, `create_entities`, `add_observations`).
2. **Recalling Context**:
   - Query memory via semantic vector search (`search_memory` / `recall`).
   - Augment current conversation prompt with retrieved past decisions.
3. **Memory Maintenance**:
   - Update obsolete notes or remove invalid entity relationships as project specs evolve.
