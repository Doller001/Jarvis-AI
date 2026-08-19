---
name: code-review-graph
description: >-
  Graph-based code review and dependency analysis tool that analyzes codebases using AST/dependency graph representations. Use this skill when conducting architectural code reviews, analyzing pull request impact graphs, detecting circular dependencies, or tracing structural changes across modules.
---

# Code Review Graph Skill

Use this skill to perform Tree-sitter powered architectural code reviews, structural dependency indexing, and context-window-optimized impact analysis.

## Key Capabilities & Use Cases

- **AST Symbol Extraction**: Parse source code using Tree-sitter into local SQLite relational graph schemas across 12+ programming languages.
- **Context Window Optimization**: Drastically reduce token usage by extracting only exact symbol slices, caller graphs, and dependency paths rather than dumping full source files.
- **Incremental Sync & Impact Analysis**: Track blast radius for PRs, identify modified symbol callers, and catch architectural rule breaks.

## CLI & Workflow Integration

```bash
pip install code-review-graph
code-review-graph build        # Index project AST into graph
code-review-graph visualize    # Generate HTML dependency graph
```

## Review Workflow

1. **Build / Sync Graph**: Index current codebase AST to populate SQLite structural dependency DB.
2. **Trace Impact**: Query target functions/classes to trace upstream callers and downstream dependents.
3. **Targeted Context Injection**: Fetch minimal symbol context instead of full files during code reviews and refactoring.
4. **Architectural Audit**: Verify zero circular dependencies, layer isolation compliance, and clean public API signatures.
