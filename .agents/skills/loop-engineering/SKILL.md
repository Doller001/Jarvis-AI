---
name: loop-engineering
description: >-
  Architectural patterns and design guides for Human-in-the-Loop (HITL) and Agentic Execution Loops (Plan -> Act -> Observe -> Reflect -> Refine). Use this skill when designing self-correcting loops, error recovery paths, safety guardrails, or interactive check-in mechanisms for AI workflows.
---

# Loop Engineering Skill

Use this skill to implement self-correcting agent execution loops, human-in-the-loop guardrails, and iterative reasoning frameworks.

## Key Capabilities & Use Cases

- **Agentic Execution Loop**: Structured Plan-Act-Observe-Reflect cycles for complex execution tasks.
- **Human-in-the-Loop (HITL)**: Setting explicit verification triggers, approval gates, and interactive check-ins.
- **Error Recovery & Reflection**: Automatic detection of stuck execution, infinite retry loops, or invalid output states with self-correction rules.

## Core Loop Workflow

1. **Plan**:
   - Formulate initial multi-step strategy with clear success criteria.
2. **Act**:
   - Execute single tool call or concrete action.
3. **Observe**:
   - Inspect raw execution output, logs, or error traces strictly.
4. **Reflect & Self-Correct**:
   - Compare actual outcome against plan. If error occurs, adjust strategy before retrying.
5. **Check-in / Complete**:
   - Trigger human approval gate for high-risk operations or declare verified task completion.
