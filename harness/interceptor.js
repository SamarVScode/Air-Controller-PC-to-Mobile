#!/usr/bin/env node

/**
 * Universal Lifecycle Interceptor Coordinator (harness/interceptor.js)
 * Coordinates preToolUse, postToolUse, and preExit lifecycle hooks.
 * Integrates:
 *   - sensitive-guard.js (Fastpath & exfiltration guard)
 *   - core-laws-linter.js (Universal code invariant linter)
 *   - cycle-detector.js (3 vs 5 thrashing circuit breaker + virtual shadow buffer)
 *   - state-collector.js (Adaptive context envelope)
 *   - jev-client.js (Native fetch client & bipartite fail-safe)
 *   - acceptance-gate.js (Deterministic runner parser & Jev completion gate)
 */

import fs from 'fs';
import tty from 'tty';
import path from 'path';
import { createHash } from 'crypto';
import { collectState, extractUserGoal, extractArtifactContract, anchorCriteriaFalse, extractLastAssistantResponse } from './state-collector.js';
import { checkCycle, clearHistory, loadSession, saveSession, reconstructShadowBuffer } from './cycle-detector.js';
import { isDestructiveAction, jevBooleanCheck, isApiKeyConfigured } from './jev-client.js';
import { evaluatePathSecurity, isReadInspectionTool, inspectCommandForSensitivePaths } from './sensitive-guard.js';
import { lintCoreLaws, formatCoreLawsReport } from './core-laws-linter.js';
import { verifyAcceptanceGate } from './acceptance-gate.js';
import { recordDecision } from './decision-tracker.js';

const rawArgs = process.argv.slice(2);
const engineIdx = rawArgs.indexOf('--engine');
const engine = engineIdx !== -1 ? rawArgs[engineIdx + 1] : (tty.isatty(0) ? 'claude' : 'antigravity');
const cleanArgs = engineIdx !== -1 ? rawArgs.filter((_, i) => i !== engineIdx && i !== engineIdx + 1) : [...rawArgs];

const [mode = 'pre-tool', arg1, arg2] = cleanArgs;

export class InterceptorExitSentinel extends Error {
  constructor(code = 0) {
    super(`Interceptor exited with code ${code}`);
    this.name = 'InterceptorExitSentinel';
    this.code = code;
  }
}

/**
 * Universal exit helper supporting both Claude/Cursor (exit codes)
 * and Antigravity (JSON response on stdout).
 */
export function exitWithDecision({ allowed = true, reason = '', mode = 'pre-tool', engine = 'claude' } = {}) {
  const code = (engine === 'antigravity') ? 0 : (allowed ? 0 : 2);
  if (engine === 'antigravity') {
    let response;
    if (mode === 'pre-tool' || mode === 'preToolUse') {
      response = allowed
        ? { decision: 'allow' }
        : { decision: 'deny', reason: reason || 'Operation vetoed by Jev cognitive harness.' };
    } else if (mode === 'verify-gate' || mode === 'preExit' || mode === 'pre-exit' || mode === 'Stop') {
      response = allowed
        ? { decision: 'allow' }
        : { decision: 'continue', reason: reason || 'Acceptance gate rejected termination. Resolve failing tests.' };
    } else {
      response = {};
    }
    const out = JSON.stringify(response) + '\n';
    try { fs.writeSync(1, out); } catch { process.stdout.write(out); }
  }

  process.exitCode = code;
  setTimeout(() => process.exit(code), 25).unref();
  throw new InterceptorExitSentinel(code);
}

// Helper to parse JSON with auto-stripping of shell single-quotes and resilient fallback
export function safeParseJson(raw) {
  if (!raw) return {};
  const rawInput = String(raw).trim();
  if (!rawInput) return {};

  let cleaned = rawInput;
  if (cleaned.startsWith("'") && cleaned.endsWith("'")) {
    cleaned = cleaned.slice(1, -1).trim();
  }
  // Strip control and binary non-printable characters
  cleaned = cleaned.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F-\x9F]/g, '');

  // 1. Direct standard parse
  try {
    return JSON.parse(cleaned);
  } catch {}

  // 1b. Fix unescaped Windows backslashes in file paths (Bug 10)
  try {
    const escapedWindows = cleaned.replace(/\\(?!["\\/bfnrt]|u[0-9a-fA-F]{4})/g, '\\\\');
    return JSON.parse(escapedWindows);
  } catch {}

  // 2. Fix trailing commas (e.g. {"a": 1, "b": 2,})
  try {
    const fixed = cleaned.replace(/,\s*([}\]])/g, '$1');
    return JSON.parse(fixed);
  } catch {}

  // 3. Fix single-quoted JSON (Item 34: preserve inner apostrophes)
  try {
    const fixed = cleaned.replace(/'((?:\\'|[^'])*)'/g, (_, inner) => '"' + inner.replace(/"/g, '\\"') + '"');
    return JSON.parse(fixed);
  } catch {}

  // 4. Extract embedded JSON object substring if surrounded by raw text/noise
  try {
    const jsonMatch = cleaned.match(/\{[\s\S]*\}/);
    if (jsonMatch) {
      try {
        const sanitizedMatch = jsonMatch[0].replace(/\\(?!["\\/bfnrt]|u[0-9a-fA-F]{4})/g, '\\\\');
        return JSON.parse(sanitizedMatch);
      } catch {
        const fixed = jsonMatch[0].replace(/,\s*([}\]])/g, '$1').replace(/'/g, '"').replace(/\\(?!["\\/bfnrt]|u[0-9a-fA-F]{4})/g, '\\\\');
        return JSON.parse(fixed);
      }
    }
  } catch {}

  // 5. Unquoted keys and comma-delimited key-value fallback
  try {
    if (cleaned.includes(':')) {
      const obj = {};
      for (const part of cleaned.split(',')) {
        const lastColon = part.lastIndexOf(':');
        if (lastColon !== -1) {
          const val = part.slice(lastColon + 1).trim().replace(/^['"]|['"]$/g, '');
          const beforeColon = part.slice(0, lastColon).trim();
          const keyMatch = beforeColon.match(/([a-zA-Z0-9_]+)$/);
          if (keyMatch) {
            obj[keyMatch[1]] = val;
          }
        }
      }
      if (Object.keys(obj).length > 0) return obj;
    }
  } catch {}

  return {};
}

export async function readStdinJson(timeoutMs = null) {
  if (typeof timeoutMs === "number") return new Promise(r => setTimeout(() => r({}), timeoutMs));
  if (tty.isatty(0)) return {};
  try {
    const rawStr = fs.readFileSync(0, 'utf8');
    const parsed = safeParseJson(rawStr);
    if (rawStr && rawStr.trim() && Object.keys(parsed).length === 0) {
      if (isDestructiveAction('run_command', { CommandLine: rawStr })) {
        globalThis.__currentOperationDestructive = true;
        const vetoReason = 'Raw input blocked by safety filter: Destructive pattern detected in unparsed payload.';
        console.error(`[JEV SAFETY VETO]: ${vetoReason}`);
        recordDecision({
          sessionId: process.env.AEGIS_SESSION_ID || 'default',
          targetDir: process.cwd(),
          source: 'safety_filter',
          decisionType: 'raw_destructive_veto',
          toolName: 'unparsed',
          inputSummary: rawStr.slice(0, 100),
          passed: false,
          verdict: 'vetoed',
          reason: vetoReason
        });
        const argv = process.argv.slice(2);
        const detectedMode = argv.find(a => ['pre-tool', 'verify-gate', 'pre-invocation', 'post-tool', 'Stop', 'pre-exit'].includes(a)) || 'pre-tool';
        const engIdx = argv.indexOf('--engine');
        const detectedEngine = engIdx !== -1 ? argv[engIdx + 1] : (tty.isatty(0) ? 'claude' : 'antigravity');
        exitWithDecision({ allowed: false, reason: vetoReason, mode: detectedMode, engine: detectedEngine });
      }
    }
    return parsed;
  } catch (err) {
    if (err instanceof InterceptorExitSentinel || err?.name === 'InterceptorExitSentinel') {
      throw err;
    }
    return {};
  }
}

/**
 * Main lifecycle dispatcher
 */
export async function runInterceptor() {
  // Read stdin once before mode dispatch so session_id and workspacePaths can be extracted.
  const stdinPayload = await readStdinJson();
  let effectiveWorkspace = (stdinPayload.workspacePaths && stdinPayload.workspacePaths[0])
    ? path.resolve(stdinPayload.workspacePaths[0])
    : process.cwd();
  if (['.agents', '.claude', '.cursor'].includes(path.basename(effectiveWorkspace))) {
    effectiveWorkspace = path.dirname(effectiveWorkspace);
  }

  const cwdHash = createHash('sha256').update(effectiveWorkspace).digest('hex').slice(0, 8);
  let sessionFromFile = null;
  try {
    const sessionFilePath = path.join(effectiveWorkspace, '.aegis-session');
    if (fs.existsSync(sessionFilePath)) {
      sessionFromFile = fs.readFileSync(sessionFilePath, 'utf8').trim() || null;
    }
  } catch {}
  // stdinPayload.session_id is stable across all Claude Code hook invocations
  // (PreToolUse, PostToolUse, Stop) in one session. Eliminates ppid fragmentation.
  const sessionId = process.env.AEGIS_SESSION_ID ||
                    stdinPayload.session_id ||
                    process.env.CONVERSATION_ID ||
                    process.env.CLAUDE_CONVERSATION_ID ||
                    process.env.CURSOR_SESSION_ID ||
                    sessionFromFile ||
                    (`cwd-${cwdHash}`);

  // -------------------------------------------------------------------------
  // HOOK 0: PRE-INVOCATION / USER PROMPT EARLY GUARDRAIL
  // -------------------------------------------------------------------------
  if (mode === 'pre-invocation' || mode === 'preInvocation' || mode === 'user-prompt' || mode === 'UserPrompt') {
    const assistantResp = stdinPayload.statement || stdinPayload.message || stdinPayload.final_response || stdinPayload.lastAssistantResponse || stdinPayload.last_assistant_response;
    if (assistantResp) {
      const session = loadSession(sessionId);
      session.lastAssistantResponse = assistantResp;
      saveSession(session, sessionId);
    }
    if (!isApiKeyConfigured(effectiveWorkspace)) {
      const warningMsg = '[AEGIS GUARD FATAL]: TYPESAFE_API_KEY is not configured or missing in .env. Jev System One cognitive interceptor cannot operate without a valid API key. Please configure TYPESAFE_API_KEY in .env before issuing tasks.';
      console.error(warningMsg);
      if (engine === 'antigravity') {
        const response = {
          injectSteps: [
            {
              ephemeralMessage: warningMsg
            }
          ]
        };
        process.stdout.write(JSON.stringify(response) + '\n');
        process.exit(0);
      } else {
        process.exit(2);
      }
    }
    if (engine === 'antigravity') {
      try { fs.writeSync(1, JSON.stringify({ injectSteps: [] }) + '\n'); } catch { process.stdout.write(JSON.stringify({ injectSteps: [] }) + '\n'); }
    }
    process.exit(0);
  }

  // -------------------------------------------------------------------------
  // HOOK 1: PRE-TOOL USE
  // -------------------------------------------------------------------------
  if (mode === 'pre-tool' || mode === 'preToolUse') {
    let toolName = '';
    let toolArgs = {};

    if (engine === 'antigravity') {
      const stdinData = stdinPayload;
      toolName = stdinData.toolCall?.name || stdinData.name || stdinData.tool_name || '';
      toolArgs = stdinData.toolCall?.args || stdinData.args || stdinData.tool_input || {};
    } else if (engine === 'claude' || engine === 'claude-code') {
      const stdinData = stdinPayload;
      if (stdinData.tool_name) {
        // Stdin envelope from Claude Code hook runtime
        toolName = stdinData.tool_name;
        toolArgs = stdinData.tool_input || {};
      } else {
        // CLI fallback (tests, direct invocation)
        toolName = arg1 || '';
        if (arg2) {
          toolArgs = safeParseJson(arg2);
          if (arg2.trim() && Object.keys(toolArgs).length === 0) {
            if (isDestructiveAction('run_command', { CommandLine: arg2 })) {
              const vetoReason = 'Raw input blocked by safety filter: Destructive pattern detected in payload.';
              console.error(`[JEV SAFETY VETO]: ${vetoReason}`);
              recordDecision({
                sessionId,
                targetDir: effectiveWorkspace,
                source: 'safety_filter',
                decisionType: 'raw_destructive_veto',
                toolName,
                inputSummary: arg2.slice(0, 100),
                passed: false,
                verdict: 'vetoed',
                reason: vetoReason
              });
              exitWithDecision({ allowed: false, reason: vetoReason, mode, engine });
            }
          }
        } else {
          toolArgs = Object.keys(stdinData).length > 0 ? stdinData : {};
        }
      }
    } else if (engine === 'cursor') {
      const stdinData = stdinPayload;
      if (stdinData.name) {
        // Stdin envelope from Cursor hook runtime
        toolName = stdinData.name;
        toolArgs = stdinData.arguments || {};
      } else {
        // CLI fallback
        toolName = arg1 || '';
        toolArgs = arg2 ? safeParseJson(arg2) : (Object.keys(stdinData).length > 0 ? stdinData : {});
      }
    } else {
      toolName = arg1 || '';
      if (arg2) {
        toolArgs = safeParseJson(arg2);
        if (arg2.trim() && Object.keys(toolArgs).length === 0) {
          if (isDestructiveAction('run_command', { CommandLine: arg2 })) {
            const vetoReason = 'Raw input blocked by safety filter: Destructive pattern detected in payload.';
            console.error(`[JEV SAFETY VETO]: ${vetoReason}`);
            recordDecision({
              sessionId,
              targetDir: effectiveWorkspace,
              source: 'safety_filter',
              decisionType: 'raw_destructive_veto',
              toolName,
              inputSummary: arg2.slice(0, 100),
              passed: false,
              verdict: 'vetoed',
              reason: vetoReason
            });
            exitWithDecision({ allowed: false, reason: vetoReason, mode, engine });
          }
        }
      } else {
        const stdinData = stdinPayload;
        toolArgs = Object.keys(stdinData).length > 0 ? stdinData : {};
      }
    }

    const targetFile = toolArgs.TargetFile || toolArgs.file_path || toolArgs.path || toolArgs.target || toolArgs.AbsolutePath || toolArgs.CommandLine || toolArgs.command || '';
    const newContent = toolArgs.CodeContent || toolArgs.ReplacementContent || toolArgs.content || toolArgs.code || toolArgs.new_string || toolArgs.newContent || '';
    const isDestructive = isDestructiveAction(toolName, toolArgs);
    const cmdStr = (toolArgs.command || toolArgs.CommandLine || toolArgs.cmd || toolArgs.script || '').toString();
    const toolLower = (toolName || '').toLowerCase();

    // Step 0: Early API Key Fail-Closed Guardrail (Blocks all tool actions if key is missing)
    if (!isApiKeyConfigured(effectiveWorkspace)) {
      const missingKeyMsg = '[AEGIS GUARD FATAL]: TYPESAFE_API_KEY is not configured or missing in .env. Jev cognitive interceptor requires a valid API key to operate safely. All tool executions are blocked.';
      console.error(missingKeyMsg);
      recordDecision({
        sessionId,
        targetDir: effectiveWorkspace,
        source: 'api_key_guard',
        decisionType: 'missing_api_key_veto',
        toolName,
        inputSummary: targetFile,
        passed: false,
        verdict: 'vetoed',
        reason: missingKeyMsg
      });
      exitWithDecision({ allowed: false, reason: missingKeyMsg, mode, engine });
    }

    // Step 0b: Infrastructure & Harness Anti-Tamper Isolation
    // Prevents coding agents (Claude / Antigravity) from modifying, replacing, or accessing the harness directory
    const isDevRepo = path.basename(effectiveWorkspace) === 'jev-mcp' || targetFile.includes('/jev-mcp/') || targetFile.includes('\\jev-mcp\\') || process.env.AEGIS_DEV_MODE === 'true';
    const isHarnessTarget = /(^|[/\\])(harness|\.aegis|\.agents)([/\\]|$)/i.test(targetFile);
    if (!isDevRepo && isHarnessTarget && (isReadInspectionTool(toolName) || toolLower.includes('write') || toolLower.includes('replace') || toolLower.includes('edit') || isDestructive)) {
      const tamperMsg = `[JEV SECURITY VETO]: Access denied. The 'harness' infrastructure directory is protected and not accessible to coding agents.`;
      console.error(tamperMsg);
      recordDecision({
        sessionId,
        targetDir: effectiveWorkspace,
        source: 'sensitive_guard',
        decisionType: 'harness_tamper_veto',
        toolName,
        inputSummary: targetFile || cmdStr,
        passed: false,
        verdict: 'vetoed',
        reason: tamperMsg
      });
      exitWithDecision({ allowed: false, reason: tamperMsg, mode, engine });
    }

    // Dynamic intent resolution: Extract user task goal if missing or generic
    const userGoal = extractUserGoal({
      engine,
      transcriptPath: stdinPayload.transcriptPath || stdinPayload.transcript_path,
      conversationId: sessionId,
      cwd: process.cwd()
    });
    if (userGoal) {
      const session = loadSession(sessionId);
      session.taskDescription = userGoal;
      saveSession(session, sessionId);
      if (!process.env.TASK_DESCRIPTION || process.env.TASK_DESCRIPTION === 'Autonomous software engineering task') {
        process.env.TASK_DESCRIPTION = userGoal;
      }
    }

    // Virtual whole-file buffer reconstruction for replace_file_content and Claude/Cursor Edit (Fix 3 & Bug 8)
    let wholeFileContent = newContent;
    const isEditTool = toolName === 'replace_file_content' || toolLower.includes('replace') || toolLower.includes('edit');
    if (isEditTool) {
      const oldStr = toolArgs.TargetContent || toolArgs.old_string || toolArgs.target_content || '';
      const newStr = toolArgs.ReplacementContent || toolArgs.new_string || toolArgs.replacement_content || toolArgs.content || '';
      wholeFileContent = reconstructShadowBuffer(
        targetFile,
        oldStr,
        newStr,
        sessionId
      );
    } else if (toolName === 'write_to_file' || toolLower.includes('write')) {
      wholeFileContent = newContent;
    }

    // Check if operation is destructive and set global flag for fail-closed error handling
    if (isDestructive) {
      globalThis.__currentOperationDestructive = true;
    }

    // Generic command credential-exfiltration check (Issue 4 mitigation)
    if (cmdStr) {
      const sensitiveInCmd = inspectCommandForSensitivePaths(cmdStr);
      if (sensitiveInCmd.isSensitive) {
        const secResult = await evaluatePathSecurity('run_command', cmdStr, process.env.TASK_DESCRIPTION, true, effectiveWorkspace);
        if (!secResult.approved) {
          recordDecision({
            sessionId,
            source: 'sensitive_guard',
            decisionType: 'security_exfiltration_veto',
            toolName,
            inputSummary: cmdStr,
            passed: false,
            verdict: 'vetoed',
            reason: secResult.reason
          });
          console.error(secResult.reason);
          exitWithDecision({ allowed: false, reason: secResult.reason, mode, engine });
        }
      }
    }

    // Step 1: Sensitive Credential Read Guard (Layer 1 Fastpath vs Layer 2 Security)
    if (isReadInspectionTool(toolName)) {
      const secResult = await evaluatePathSecurity(toolName, targetFile, process.env.TASK_DESCRIPTION, false, effectiveWorkspace);
      if (secResult.fastpath) {
        // Fastpath approved: 0 tokens, 0ms latency
        exitWithDecision({ allowed: true, mode, engine });
      }
      if (!secResult.approved) {
        recordDecision({
          sessionId,
          source: 'sensitive_guard',
          decisionType: 'credential_read_veto',
          toolName,
          inputSummary: targetFile,
          passed: false,
          verdict: 'vetoed',
          reason: secResult.reason
        });
        console.error(secResult.reason);
        exitWithDecision({ allowed: false, reason: secResult.reason, mode, engine });
      }
    }

    // Step 2: Diff Variance Cycle Detector & Thrashing Circuit Breaker (Universal)
    const isReadOnly = isReadInspectionTool(toolName) || toolName === 'view_file' || toolName === 'read_file';
    const isMutation = !isReadOnly && Boolean(wholeFileContent || toolLower.includes('write') || toolLower.includes('replace') || toolLower.includes('edit'));
    const isCommand = !isReadOnly && (toolName === 'run_command' || toolName.toLowerCase().includes('command') || toolName === 'Bash');
    const cycle = checkCycle(toolName, targetFile, wholeFileContent, sessionId, isMutation, isCommand);
    if (cycle.isThrashing) {
      recordDecision({
        sessionId,
        source: 'cycle_detector',
        decisionType: 'cycle_thrash_veto',
        toolName,
        inputSummary: `${targetFile} (diff hash: ${cycle.diffHash || 'unknown'})`,
        passed: false,
        verdict: 'vetoed',
        reason: cycle.critique
      });
      console.error(cycle.critique);
      exitWithDecision({ allowed: false, reason: cycle.critique, mode, engine });
    }
    if (cycle.warning && cycle.critique) {
      console.error(cycle.critique);
    }

    // Step 3: Universal Code Invariants Linter
    if (wholeFileContent && (toolLower.includes('write') || toolLower.includes('replace') || toolLower.includes('edit'))) {
      const lintResult = lintCoreLaws(wholeFileContent, targetFile);
      if (!lintResult.clean) {
        recordDecision({
          sessionId,
          source: 'core_laws_linter',
          decisionType: 'code_invariant_violation',
          toolName,
          inputSummary: targetFile,
          passed: false,
          verdict: 'vetoed',
          reason: `Violated ${lintResult.violations.length} core law(s)`
        });
        console.error(formatCoreLawsReport(lintResult.violations));
        exitWithDecision({ allowed: false, reason: `Violated ${lintResult.violations.length} core law(s)`, mode, engine });
      }
    }

    // Step 3b: Shift-Left Two-Phase Semantic Gating (Jev System One)
    const isShiftLeftEnabled = process.env.AEGIS_SHIFT_LEFT !== 'false' || Boolean(toolArgs.enableShiftLeft);
    const isFileWriteOrEdit = wholeFileContent && (
      toolLower.includes('write') ||
      toolLower.includes('replace') ||
      toolLower.includes('edit')
    );

    if (isShiftLeftEnabled && isFileWriteOrEdit && !isDestructive) {
      try {
        const session = loadSession(sessionId);
        const resolvedTask = (process.env.TASK_DESCRIPTION && process.env.TASK_DESCRIPTION !== 'Autonomous software engineering task')
          ? process.env.TASK_DESCRIPTION
          : (userGoal || process.env.TASK_DESCRIPTION || 'Autonomous software engineering task');

        const stateOptions = {
          workingFileContent: wholeFileContent ? wholeFileContent.slice(0, 30000) : null,
          rollingHistory: session.rollingHistory || [],
          lastStderr: session.lastStderr || '',
          lastTestPassed: session.lastTestPassed ?? null,
          authorization: { workspace_root: effectiveWorkspace, role: 'developer' },
          runtimeMetadata: { platform: process.platform, node_version: process.version, arch: process.arch }
        };

        const state = collectState(resolvedTask, toolName, toolArgs, sessionId, session.lastStderr || '', 'localized', stateOptions);

        // Extract or dynamically synthesize artifact specification contract
        const contract = extractArtifactContract({
          targetFile,
          codeContent: wholeFileContent,
          toolArgs,
          workspaceRoot: effectiveWorkspace,
          userGoal: resolvedTask
        });

        const targetBase = path.basename(targetFile);
        const anchoredFalseCriteria = anchorCriteriaFalse(contract.criteriaFalse);

        // Standardized Hardcoded Assertion Templates
        const specAssertion = `The proposed True/False criteria for '${targetBase}' is rigorous, complete, and non-trivial for the requested task, strictly preventing stubs, security vulnerabilities, and logic flaws.`;
        const codeAssertion = `The proposed code in '${targetBase}' fulfills the certified specification and satisfies production-grade invariants without stubs, mock bypasses, or regressions.`;

        const isTwoPhase = process.env.AEGIS_TWO_PHASE !== 'false';

        // -------------------------------------------------------------------
        // PHASE 1: Specification Vetting Gate (spec_vetting_gate)
        // -------------------------------------------------------------------
        if (isTwoPhase) {
          const specCriteriaTrue = 'The criteria explicitly mandates concrete functional implementations, error handling, and security constraints without tautological or trivial definitions.';
          const specCriteriaFalse = 'The criteria is tautological, trivial (e.g. self-fulfilling or vacuously true), permits dummy placeholder stubs, ignores error handling, or omits task requirements.';

          const specContext = {
            task: resolvedTask,
            targetFile: targetBase,
            contractSource: contract.source,
            declaredClaim: contract.claim,
            proposedCriteria: {
              true: contract.criteriaTrue,
              false: anchoredFalseCriteria
            }
          };

          const specCheck = await jevBooleanCheck({
            state: JSON.stringify(specContext, null, 2),
            assertion: specAssertion,
            criteriaTrue: specCriteriaTrue,
            criteriaFalse: specCriteriaFalse,
            isDestructive: false
          });

          recordDecision({
            sessionId,
            targetDir: effectiveWorkspace,
            source: 'jev_system_one',
            decisionType: 'spec_vetting_gate',
            toolName,
            inputSummary: `${targetBase} specification (${contract.source})`,
            passed: specCheck.approved,
            verdict: specCheck.approved ? 'passed' : 'vetoed',
            noul: specCheck.noul,
            probability: specCheck.probability,
            reason: specCheck.reason || (specCheck.approved
              ? `Specification for '${targetBase}' certified sound by Jev System One (Probability: ${specCheck.probability})`
              : `Specification for '${targetBase}' rejected by Jev System One (Probability: ${specCheck.probability})`),
            metadata: {
              assertion: specAssertion,
              criteria: { true: specCriteriaTrue, false: specCriteriaFalse },
              model: specCheck.model || 'jev-1.13.0',
              usage: specCheck.usage || {},
              specContext
            }
          });

          if (!specCheck.approved) {
            const specVetoMsg = `[JEV SPEC VETO]: Specification for '${targetBase}' rejected by Jev System One (Probability: ${specCheck.probability || 0}): ${specCheck.reason || 'Criteria is too soft, trivial, or permits stubs.'}`;
            console.error(specVetoMsg);
            exitWithDecision({ allowed: false, reason: specVetoMsg, mode, engine });
            return;
          }
        }

        // -------------------------------------------------------------------
        // PHASE 2: Code Conformance Gate (code_conformance_gate)
        // -------------------------------------------------------------------
        const codeCheck = await jevBooleanCheck({
          state,
          assertion: codeAssertion,
          criteriaTrue: contract.criteriaTrue,
          criteriaFalse: anchoredFalseCriteria,
          isDestructive: false
        });

        recordDecision({
          sessionId,
          targetDir: effectiveWorkspace,
          source: 'jev_system_one',
          decisionType: 'code_conformance_gate',
          toolName,
          inputSummary: `${targetFile} (${wholeFileContent.length} bytes)`,
          passed: codeCheck.approved,
          verdict: codeCheck.approved ? 'passed' : 'vetoed',
          noul: codeCheck.noul,
          probability: codeCheck.probability,
          reason: codeCheck.reason || (codeCheck.approved
            ? `Code for '${targetBase}' approved by Jev System One (Probability: ${codeCheck.probability})`
            : `Code for '${targetBase}' rejected by Jev System One (Probability: ${codeCheck.probability})`),
          metadata: {
            assertion: codeAssertion,
            criteria: {
              true: contract.criteriaTrue,
              false: anchoredFalseCriteria
            },
            model: codeCheck.model || 'jev-1.13.0',
            usage: codeCheck.usage || {},
            seven_pillars_input: state
          }
        });

        if (!codeCheck.approved) {
          const codeVetoMsg = `[JEV CODE VETO]: Code for '${targetBase}' rejected by Jev System One (Probability: ${codeCheck.probability || 0}): ${codeCheck.reason || 'Failed certified specification.'}`;
          console.error(codeVetoMsg);
          exitWithDecision({ allowed: false, reason: codeVetoMsg, mode, engine });
          return;
        }
      } catch (artifactErr) {
        if (artifactErr instanceof InterceptorExitSentinel || artifactErr?.name === 'InterceptorExitSentinel') {
          throw artifactErr;
        }
        console.warn(`[JEV WARN]: Artifact check failed to reach Jev (${artifactErr.message}); proceeding under fail-open.`);
      }
    }

    // Step 4: Destructive Side-Effect Vetting via Jev System One (Isolated fail-closed guard)
    if (isDestructive) {
      try {
        const session = loadSession(sessionId);
        const resolvedTask = (process.env.TASK_DESCRIPTION && process.env.TASK_DESCRIPTION !== 'Autonomous software engineering task')
          ? process.env.TASK_DESCRIPTION
          : (userGoal || process.env.TASK_DESCRIPTION || 'Autonomous software engineering task');

        const stateOptions = {
          workingFileContent: wholeFileContent ? wholeFileContent.slice(0, 1500) : null,
          rollingHistory: session.rollingHistory || [],
          lastStderr: session.lastStderr || '',
          lastTestPassed: session.lastTestPassed ?? null,
          authorization: { workspace_root: effectiveWorkspace, role: 'developer' },
          runtimeMetadata: { platform: process.platform, node_version: process.version, arch: process.arch }
        };

        const state = collectState(resolvedTask, toolName, toolArgs, sessionId, session.lastStderr || '', 'localized', stateOptions);
        const jevCheck = await jevBooleanCheck({
          state,
          assertion: 'Is this operation safe to proceed without risking irrecoverable data loss, database destruction, or repository corruption?',
          criteriaTrue: 'The operation is safe, intentional, and standard for the task.',
          criteriaFalse: 'The operation risks catastrophic data loss, database drops, hard git resets, or directory wipes.',
          isDestructive: true
        });

        recordDecision({
          sessionId,
          targetDir: effectiveWorkspace,
          source: 'jev_system_one',
          decisionType: 'destructive_vetting',
          toolName,
          inputSummary: cmdStr || targetFile || toolName,
          passed: jevCheck.approved,
          verdict: jevCheck.approved ? 'approved' : 'vetoed',
          noul: jevCheck.noul,
          probability: jevCheck.probability,
          reason: jevCheck.reason,
          metadata: {
            user_intent: resolvedTask,
            assertion: 'Is this operation safe to proceed without risking irrecoverable data loss, database destruction, or repository corruption?',
            criteria: {
              true: 'The operation is safe, intentional, and standard for the task.',
              false: 'The operation risks catastrophic data loss, database drops, hard git resets, or directory wipes.'
            },
            model: jevCheck.model || 'jev-1.13.0',
            seven_pillars: {
              pillar_1_user_intent: state.task,
              pillar_2_proposed_action: {
                tool: state.proposed_tool,
                args: state.tool_args,
                runtime_metadata: stateOptions.runtimeMetadata
              },
              pillar_3_target_file_ast: state.target_file_ast,
              pillar_4_git_delta: {
                status: state.git_status,
                diff_stat: state.diff_stat,
                git_diff: state.git_diff
              },
              pillar_5_causal_trajectory: state.causal_trajectory,
              pillar_6_verification_contract: state.workspace,
              pillar_7_authorization_boundary: state.authorization_boundary
            }
          }
        });

        if (!jevCheck.approved) {
          console.error(`[JEV SAFETY VETO]: Operation '${toolName}' blocked by Jev safety filter: ${jevCheck.reason || 'High risk of destructive data loss.'}`);
          exitWithDecision({ allowed: false, reason: jevCheck.reason || 'High risk of destructive data loss.', mode, engine });
        }
      } catch (vetErr) {
        if (vetErr instanceof InterceptorExitSentinel || vetErr?.name === 'InterceptorExitSentinel') {
          throw vetErr;
        }
        recordDecision({
          sessionId,
          source: 'jev_system_one',
          decisionType: 'destructive_vetting_error',
          toolName,
          inputSummary: cmdStr || targetFile || toolName,
          passed: false,
          verdict: 'vetoed',
          reason: `Vetting error: ${vetErr.message}. Enforcing hard fail-closed.`
        });
        console.error(`[JEV SAFETY VETO]: Error occurred during destructive vetting for '${toolName}': ${vetErr.message}. Enforcing hard fail-closed.`);
        exitWithDecision({ allowed: false, reason: `Vetting error: ${vetErr.message}. Enforcing hard fail-closed.`, mode, engine });
      }
    }

    if (process.env.AEGIS_FULL_AUDIT === 'true') {
      recordDecision({
        sessionId,
        targetDir: effectiveWorkspace,
        source: 'interceptor',
        decisionType: 'pre_tool_fastpath_passed',
        toolName,
        inputSummary: targetFile || cmdStr || toolName,
        passed: true,
        verdict: 'passed',
        reason: 'Operation passed local fastpath policies (Pillar 7 boundary, cycle detector, core laws linter)'
      });
    }

    // Approved to proceed
    exitWithDecision({ allowed: true, mode, engine });
  }

  // -------------------------------------------------------------------------
  // HOOK 2: POST-TOOL USE
  // -------------------------------------------------------------------------
  if (mode === 'post-tool' || mode === 'postToolUse') {
    const stdinData = stdinPayload;
    const assistantResp = stdinPayload.statement || stdinPayload.message || stdinPayload.final_response || stdinPayload.lastAssistantResponse || stdinPayload.last_assistant_response;
    if (assistantResp) {
      const session = loadSession(sessionId);
      session.lastAssistantResponse = assistantResp;
      saveSession(session, sessionId);
    }
    const isError = stdinData.isError || stdinData.error;
    const stderr = stdinData.stderr || (isError ? String(stdinData.output || '') : '');

    if (stderr) {
      // Store last stderr in session for adaptive failure enveloping
      const session = loadSession(sessionId);
      session.lastStderr = stderr.slice(-1500);
      saveSession(session, sessionId);
    }

    const TEST_RUNNER_PATTERN = /^(npm|yarn|pnpm|bun)\s+test|pytest|cargo\s+test|go\s+test|node\s+.*test|gradlew\s+test/i;
    const TEST_OUTPUT_FAIL_MARKERS = /passing|failing|failed|PASSED|FAILED|tests?\s+passed|test suite|AssertionError|FAIL\b/i;

    const toolLower = (stdinData.tool_name || stdinData.name || stdinData.tool || '').toLowerCase();
    const isCommandTool = toolLower === 'bash' || toolLower === 'run_command' || toolLower.includes('command') || toolLower.includes('terminal');

    if (isCommandTool) {
      const cmd = stdinData.tool_input?.command || stdinData.tool_input?.CommandLine || stdinData.tool_input?.cmd || stdinData.command || '';
      const output = stdinData.tool_result?.output || stdinData.tool_result?.stdout || stdinData.tool_result || stdinData.output || '';
      const toolIsError = stdinData.tool_result?.is_error !== undefined
        ? stdinData.tool_result.is_error
        : (stdinData.isError || stdinData.error || false);

      if (TEST_RUNNER_PATTERN.test(cmd)) {
        const testSession = loadSession(sessionId);
        if (toolIsError === true && TEST_OUTPUT_FAIL_MARKERS.test(output)) {
          // Genuine test failure with test output - not a timeout or spawn error
          testSession.lastTestPassed = false;
          saveSession(testSession, sessionId);
          recordDecision({
            sessionId,
            source: 'test_runner',
            decisionType: 'post_tool_test_tracking',
            toolName: stdinData.tool_name || stdinData.name || 'command',
            inputSummary: cmd,
            passed: false,
            verdict: 'failed',
            reason: 'Test runner exited with failure markers'
          });
        } else if (toolIsError === false) {
          // Clean exit - tests passed
          testSession.lastTestPassed = true;
          saveSession(testSession, sessionId);
          recordDecision({
            sessionId,
            source: 'test_runner',
            decisionType: 'post_tool_test_tracking',
            toolName: stdinData.tool_name || stdinData.name || 'command',
            inputSummary: cmd,
            passed: true,
            verdict: 'passed',
            reason: 'Test runner exited cleanly with code 0'
          });
        }
      }
    }

    process.exit(0);
  }

  // -------------------------------------------------------------------------
  // HOOK 3: VERIFY GATE / PRE-EXIT
  // -------------------------------------------------------------------------
  if (mode === 'verify-gate' || mode === 'preExit' || mode === 'pre-exit') {
    const stdinData = stdinPayload;
    const customCmd = arg1 || stdinData.command || stdinData.customCommand || null;
    let agentStatement = stdinData.statement || stdinData.message || stdinData.final_response || arg2 || null;

    const session = loadSession(sessionId);

    // Item 8: Load session.taskDescription when process.env.TASK_DESCRIPTION is empty or generic
    if ((!process.env.TASK_DESCRIPTION || process.env.TASK_DESCRIPTION === 'Autonomous software engineering task') && session.taskDescription) {
      process.env.TASK_DESCRIPTION = session.taskDescription;
    }

    const transcriptPath = stdinPayload.transcriptPath || stdinPayload.transcript_path;
    // Item 20: In verify-gate mode, if agentStatement is missing, fallback to extractLastAssistantResponse || session.taskDescription
    if (!agentStatement) {
      agentStatement = session.lastAssistantResponse || extractLastAssistantResponse({
        engine,
        transcriptPath,
        conversationId: sessionId
      }) || session.taskDescription || null;
    }

    const gateResult = await verifyAcceptanceGate(customCmd, effectiveWorkspace, agentStatement, sessionId);

    // Stage 2 decision already recorded in verifyAcceptanceGate

    if (gateResult.passed) {
      console.error(`\n======================================================`);
      console.error(`[JEV ACCEPTANCE GATE]: Verification Passed (${gateResult.reason})`);
      console.error(`======================================================\n`);
      exitWithDecision({ allowed: true, mode, engine });
    } else {
      console.error(`\n======================================================`);
      console.error(`[JEV ACCEPTANCE GATE REJECTED]: ${gateResult.reason}`);
      console.error(`Exit Code: ${gateResult.exitCode ?? 'none'} | Tests Run: ${gateResult.testsRun ?? 'none'}`);
      console.error(`Agent completion halted. Resolve failing tests before terminating.`);
      console.error(`======================================================\n`);
      exitWithDecision({ allowed: false, reason: gateResult.reason, mode, engine });
    }
  }

  // Default passthrough
  exitWithDecision({ allowed: true, mode, engine });
}

import { fileURLToPath } from 'url';
const isDirectRun = process.argv[1] && (
  process.platform === 'win32'
    ? path.resolve(process.argv[1]).toLowerCase() === fileURLToPath(import.meta.url).toLowerCase()
    : path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
);
if (isDirectRun) {
  runInterceptor().catch(err => {
    if (err instanceof InterceptorExitSentinel || err?.name === 'InterceptorExitSentinel') {
      return;
    }
    console.error('Interceptor unexpected error:', err.message);
    console.error(err.stack || '(no stack trace available)');
    if (mode === 'pre-tool' || mode === 'preToolUse' || mode === 'verify-gate' || mode === 'preExit' || mode === 'pre-exit' || globalThis.__currentOperationDestructive || rawArgs.some(a => isDestructiveAction('run_command', { CommandLine: a }))) {
      const errorReason = `[JEV SAFETY VETO]: Interceptor unhandled error during critical lifecycle gate: ${err.message || 'unknown error'}. Fail-closed enforced.`;
      console.error(errorReason);
      try {
        exitWithDecision({ allowed: false, reason: errorReason, mode, engine });
      } catch {
        process.exit(engine === 'antigravity' ? 0 : 2);
      }
    }
    process.exit(0); // Fail open gracefully on internal unexpected error for benign post-tool telemetry
  });
}
