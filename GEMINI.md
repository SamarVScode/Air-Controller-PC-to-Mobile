# TypeSafe AI Jev Guardrails Active

> [!NOTE]
> This workspace is actively monitored by the **TypeSafe AI Jev Interceptor Harness**.

## Operational Guidelines

### 1. Think Before Coding
- Don't assume. Don't hide confusion. Surface tradeoffs explicitly.
- State your assumptions before implementing. If uncertain, ask.
- If multiple interpretations exist, present them rather than picking silently.

### 2. Simplicity First
- Minimum code that solves the problem. Nothing speculative.
- No unrequested abstractions, extra configurability, or future-proofing.
- If you write 200 lines and it could be 50, rewrite it.

### 3. Surgical Changes (Zero Thrashing)
- Touch only what you must. Clean up only your own changes.
- Do not modify or reformat adjacent working code.
- If Jev or the cycle detector issues a warning or veto regarding repetitive edits, immediately halt and pivot your implementation strategy. Do not retry identical modifications.

### 4. Goal-Driven Execution (Deterministic Verification)
- Prior to declaring any task complete, run the project's automated test suite (`npm test`) to satisfy the Jev Acceptance Gate.
- The Lie Detector audits agent claims against disk and test truth before completion is approved.
- Keep diffs and outputs concise. Bounded context enforced.

### 5. Token Efficiency & Context Protection
- Universal Research Sandboxing: For multi-file documentation (Obsidian vaults, markdown directories, PDF specs, Excel/CSV datasets, or multi-query web searches), do not dump raw files or scraped HTML into coordinator context. Delegate bulk research to an ephemeral subagent that produces a compact <1,500 token summary artifact (RESEARCH.md).
- Focal Chunking: Read only necessary line slices (StartLine/EndLine) or symbol search rather than loading whole multi-thousand-line files.
- Supervisor Coordination: Never poll subagents or tasks in an active loop. Rely on reactive wakeups or schedule timers.

### 6. Shift-Left Two-Phase Verification (Jev System One)
- Every file write or modification is intercepted in-memory before disk commit.
- Phase 1 (Spec Gate): Jev certifies that the True/False specification contract for the file is rigorous, complete, and non-trivial.
- Phase 2 (Code Gate): Jev mathematically validates that the proposed code satisfies the certified specification without stubs, mock bypasses, or unhandled errors.
- Declare your specification contract in:
  * Tool call Description (e.g., Description: 'Implements JWT RS256 verification and 401 error handler'),
  * File header JSDoc (/** @aegis-contract @claim ... @true ... @false ... */),
  * Or companion file (.<filename>.spec.json).
