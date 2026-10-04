/**
 * Acceptance Gatekeeper (harness/acceptance-gate.js)
 * Dual-stage completion verification pipeline with Claim-to-Reality Reconciliation:
 *   Stage 1: Multi-framework test execution & semantic runner output parsing with benign stderr triage.
 *   Stage 1.5: Claim-to-Reality Reconciliation Engine ("Lie Detector") auditing agent claims against disk & telemetry.
 *   Stage 2: TypeSafe AI Jev completion gate with decision threshold.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { exec, spawn, spawnSync } from 'child_process';
import { promisify } from 'util';
import { detectWorkspaceEcosystem } from './manifest-sniffer.js';
import { parseTestRunnerOutput, triageStderr } from './runner-parser.js';
import { isDestructiveAction, jevBooleanCheck } from './jev-client.js';
import { loadSession } from './cycle-detector.js';
import { recordDecision } from './decision-tracker.js';

export function safeSpawnAsync(commandStr, options = {}) {
  return new Promise((resolve, reject) => {
    const tokens = (commandStr || '').trim().match(/(?:[^\s"']+|"[^"]*"|'[^']*')+/g) || [];
    if (tokens.length === 0) {
      return reject({ stdout: '', stderr: 'Empty command', code: 1 });
    }

    let executable;
    let spawnArgs;

    if (process.platform === 'win32') {
      executable = process.env.ComSpec || 'cmd.exe';
      spawnArgs = ['/d', '/s', '/c', `"${commandStr}"`];
    } else {
      executable = tokens[0].replace(/^["']|["']$/g, '');
      spawnArgs = tokens.slice(1).map(t => t.replace(/^["']|["']$/g, ''));
    }

    const timeout = options.timeout || 45000;
    const maxBuffer = options.maxBuffer || 10 * 1024 * 1024;
    let stdout = '';
    let stderr = '';
    let timedOut = false;
    let maxBufferExceeded = false;

    let child;
    try {
      child = spawn(executable, spawnArgs, {
      windowsVerbatimArguments: process.platform === 'win32',
        cwd: options.cwd || process.cwd(),
        env: options.env || { ...process.env, CI: 'true', FORCE_COLOR: '0' },
        windowsHide: true,
        shell: false
      });
    } catch (err) {
      return reject({ stdout: '', stderr: err.message, code: 1, error: err });
    }

    const killChildProcess = () => {
      try {
        if (process.platform === 'win32' && child.pid) {
          spawnSync('taskkill', ['/pid', child.pid.toString(), '/T', '/F']);
        } else {
          child.kill('SIGTERM');
        }
      } catch {}
    };

    const timer = setTimeout(() => {
      timedOut = true;
      killChildProcess();
    }, timeout);

    child.stdout?.on('data', chunk => {
      if (stdout.length + chunk.length > maxBuffer) {
        maxBufferExceeded = true;
        killChildProcess();
      } else {
        stdout += chunk.toString();
      }
    });

    child.stderr?.on('data', chunk => {
      if (stderr.length + chunk.length > maxBuffer) {
        maxBufferExceeded = true;
        killChildProcess();
      } else {
        stderr += chunk.toString();
      }
    });

    child.on('error', err => {
      clearTimeout(timer);
      reject({ stdout, stderr, code: 1, message: err.message, error: err });
    });

    child.on('close', code => {
      clearTimeout(timer);
      if (timedOut) {
        return reject({ stdout, stderr, code: 124, killed: true, message: 'Process timed out' });
      }
      if (maxBufferExceeded) {
        return reject({ stdout, stderr, code: 1, isMaxBuffer: true, message: 'maxBuffer exceeded' });
      }
      if (code !== 0) {
        return reject({ stdout, stderr, code: code ?? 1 });
      }
      resolve({ stdout, stderr, code: 0 });
    });
  });
}

export function extractVerifiableClaims(statementText = '') {
  if (!statementText || typeof statementText !== 'string') {
    return {
      testClaims: false,
      fileModifications: [],
      buildClaims: false
    };
  }

  const testClaims = /all\s+tests?\s+pass|tests?\s+passed|0\s+fail|passed\s+all\s+tests|test\s+suite\s+pass/i.test(statementText);
  const buildClaims = /build\s+succeeded|compilation\s+succeeded|compiled\s+successfully/i.test(statementText);

  const fileRegex = /(?:created|modified|updated|added|wrote(?:\s+to)?)\s+[`"']?([a-zA-Z0-9_\-\./\\]+\.[a-zA-Z0-9]+)[`"']?/gi;
  const fileModifications = [];
  let match;
  while ((match = fileRegex.exec(statementText)) !== null) {
    const candidate = match[1];
    // Item 27: Filter out version numbers like 1.0, 2.1, v1.2
    const isVersion = /^v?\d+\.\d+(\.\d+)?$/i.test(candidate);
    if (candidate && !isVersion && !fileModifications.includes(candidate)) {
      fileModifications.push(candidate);
    }
  }

  return {
    testClaims,
    fileModifications,
    buildClaims
  };
}

export function reconcileClaimsWithGroundTruth(claims, sessionState = {}, workspaceDir = process.cwd(), testRunResult = null) {
  const safeClaims = claims || { testClaims: false, fileModifications: [], buildClaims: false };
  const safeSession = sessionState || {};
  const effectiveWorkspace = workspaceDir || process.cwd();

  // Test execution claims check
  if (safeClaims.testClaims) {
    if (testRunResult && (!testRunResult.passed || testRunResult.exitCode !== 0)) {
      return {
        reconciled: false,
        reason: 'Blatant fabrication: Agent claimed all tests passed, but test runner failed.',
        stage: 'claim_reconciliation_gate',
        hardVeto: true
      };
    }
    if (testRunResult && (testRunResult.testsRun === 0 || testRunResult.testsRun === '0')) {
      return {
        reconciled: false,
        reason: 'Blatant fabrication: Agent claimed all tests passed, but zero tests were executed.',
        stage: 'claim_reconciliation_gate',
        hardVeto: true
      };
    }
    if (!testRunResult && safeSession.lastTestPassed === false) {
      return {
        reconciled: false,
        reason: 'Blatant fabrication: Agent claimed tests passed, but last executed test in session failed.',
        stage: 'claim_reconciliation_gate',
        hardVeto: true
      };
    }
  }

  // File modification claims check
  if (safeClaims.fileModifications && safeClaims.fileModifications.length > 0) {
    for (const claimedPath of safeClaims.fileModifications) {
      const resolved = path.resolve(effectiveWorkspace, claimedPath);
      const base = resolved.replace(/\.[^/.]+$/, '');
      const extensionVariants = ['.ts', '.tsx', '.jsx', '.mjs', '.cjs', '.js'];
      const fileExists = fs.existsSync(resolved) || extensionVariants.some(ext => fs.existsSync(base + ext));
      if (!fileExists) {
        return {
          reconciled: false,
          reason: `Blatant fabrication: Agent claimed to create/modify '${claimedPath}', but file does not exist on disk (checked .js/.ts/.tsx/.jsx/.mjs/.cjs variants).`,
          stage: 'claim_reconciliation_gate',
          hardVeto: true
        };
      }
    }
  }

  // Build claims check
  if (safeClaims.buildClaims) {
    if (safeSession.lastBuildFailed) {
      return {
        reconciled: false,
        reason: 'Blatant fabrication: Agent claimed build succeeded, but build failed with errors.',
        stage: 'claim_reconciliation_gate',
        hardVeto: true
      };
    }
  }

  const claimsCount = (safeClaims.testClaims ? 1 : 0) +
    (safeClaims.fileModifications ? safeClaims.fileModifications.length : 0) +
    (safeClaims.buildClaims ? 1 : 0);

  return {
    reconciled: true,
    claimsCount,
    claims: safeClaims
  };
}

export async function verifyAcceptanceGate(customCommand = null, targetDir = null, agentStatement = null, sessionId = 'default') {
  let effectiveTargetDir = targetDir || process.cwd();
  if (['.agents', '.claude', '.cursor'].includes(path.basename(effectiveTargetDir))) {
    effectiveTargetDir = path.dirname(effectiveTargetDir);
  }

  const session = (sessionId && typeof sessionId === 'object') ? sessionId : loadSession(sessionId || 'default');
  if ((!process.env.TASK_DESCRIPTION || process.env.TASK_DESCRIPTION === 'Autonomous software engineering task') && session.taskDescription) {
    process.env.TASK_DESCRIPTION = session.taskDescription;
  }

  // Deterministic authorization pre-gate: hard veto if agentStatement
  // contains restricted patterns before any async work or Jev call.
  // Applies primarily to the Stop hook path where agentStatement arrives without
  // the customCommand isDestructiveAction() guard below.
  const RESTRICTED_PATTERNS = [
    /rm\s+-[a-z]*r[a-z]*f?/i,
    /git\s+reset\b/i,
    /drop\s+(table|database|schema)\b/i,
    /delete\s+from\b/i,
    /truncate(\s+table)?\b/i
  ];
  // Item 21: Only check for standalone destructive command executions, not narrative agent statements
  if (!customCommand && agentStatement) {
    const standaloneCmd = /^\s*(?:rm\s+-[a-z]*r[a-z]*f?|git\s+reset\b|drop\s+(?:table|database)|delete\s+from|truncate(?:\s+table)?)/im;
    if (standaloneCmd.test(agentStatement)) {
      return {
        passed: false,
        stage: 'auth_pre_gate',
        hardVeto: true,
        reason: 'Hard veto: restricted pattern detected in agent statement before Jev evaluation.',
        probability: 0.0
      };
    }
  }

  if (customCommand) {
    if (isDestructiveAction('run_command', { CommandLine: customCommand })) {
      return {
        passed: false,
        stage: 'stage_1_semantic_parser',
        reason: 'Destructive command blocked in acceptance gate.'
      };
    }

    const trimmedCmd = String(customCommand).trim();
    const SAFE_RUNNERS = /^(\.?[\/\\])?(\.?\.?[\/\\])?(\.venv[/\\](bin|Scripts)[/\\]|venv[/\\](bin|Scripts)[/\\])?(npm|npx|yarn|pnpm|bun|node|pytest|poetry|pipenv|cargo|go|gradle|gradlew(\.bat)?|make|ctest|clasp)\b/i;
    const match = trimmedCmd.match(SAFE_RUNNERS);
    if (!match) {
      return {
        passed: false,
        stage: 'stage_1_semantic_parser',
        reason: 'Command not permitted: acceptance gate commands must begin with a known safe runner/builder (npm, npx, yarn, pnpm, bun, node, pytest, poetry, pipenv, cargo, go, gradle, gradlew, make, ctest, clasp).'
      };
    }

    const runnerName = match[0].split(/[\/\\]/).pop().replace(/\.exe$|\.bat$/i, '').toLowerCase();

    if (runnerName === 'npx') {
      const ALLOWED_NPX_TOOLS = new Set([
        'tsc', 'jest', 'mocha', 'vitest', 'playwright', 'cypress', 'clasp',
        'eslint', 'prettier', 'pytest', 'cargo', 'ts-node', 'c8', 'nyc',
        'tap', 'ava', 'jasmine'
      ]);
      const npxRest = trimmedCmd.slice(match[0].length).trim();
      const tokens = npxRest.split(/\s+/).filter(Boolean);
      let targetTool = '';
      for (const token of tokens) {
        if (token.startsWith('-')) continue;
        targetTool = token;
        break;
      }
      const toolBase = path.basename(targetTool).toLowerCase().replace(/^@[^/]+\//, '');
      if (!targetTool || (!ALLOWED_NPX_TOOLS.has(toolBase) && !ALLOWED_NPX_TOOLS.has(targetTool.toLowerCase()))) {
        return {
          passed: false,
          stage: 'stage_1_semantic_parser',
          reason: `Command not permitted: npx utility '${targetTool || 'unknown'}' is not an authorized build, test, or lint utility.`
        };
      }
    }

    if (runnerName === 'node') {
      const nodeRest = trimmedCmd.slice(match[0].length).trim();
      const hasScriptFile = /\.(js|mjs|cjs|ts)(["'\s]|$)/i.test(nodeRest);
      const hasTestFlag = /(?:^|\s)--test\b/.test(nodeRest);
      const hasEvalFlag = /(?:^|\s)(-e|--eval)\b/.test(nodeRest);
      const hasCheckFlag = /(?:^|\s)--check\b/.test(nodeRest);

      if (!hasScriptFile && !hasTestFlag && !hasEvalFlag && !hasCheckFlag) {
        return {
          passed: false,
          stage: 'stage_1_semantic_parser',
          reason: 'Command not permitted: node invocation in acceptance gate must run a script file (.js, .mjs, .cjs, .ts), --test, -e, or --check.'
        };
      }
    }

    // Disallow shell chaining and control operators to prevent command injection
    const SHELL_CONTROL_CHARS = /[;&|`$><]|\n|\r/;
    if (SHELL_CONTROL_CHARS.test(trimmedCmd)) {
      return {
        passed: false,
        stage: 'stage_1_semantic_parser',
        reason: 'Command rejected: shell chaining and control operators (&&, ||, ;, |, `, $, >, <) are forbidden in acceptance gate custom commands.'
      };
    }
  }

  const workspace = detectWorkspaceEcosystem(effectiveTargetDir);
  const testCommand = customCommand || workspace.testCommand || 'npm test';

  const isNoContract = !customCommand && workspace.ecosystem === 'unknown';

  let stdout = '';
  let stderr = '';
  let exitCode = 0;
  let isTimeout = false;
  let isMaxBuffer = false;

  if (!isNoContract) {
    try {
      const res = await safeSpawnAsync(testCommand, {
        cwd: effectiveTargetDir,
        timeout: 45000,
        maxBuffer: 10 * 1024 * 1024,
        env: { ...process.env, CI: 'true', FORCE_COLOR: '0' }
      });
      stdout = res.stdout || '';
      stderr = res.stderr || '';
    } catch (err) {
      stdout = err.stdout || '';
      stderr = err.stderr || '';
      if (err.killed || (typeof err.message === 'string' && err.message.includes('timed out'))) {
        isTimeout = true;
        exitCode = 124;
      } else if (err.isMaxBuffer) {
        isMaxBuffer = true;
        exitCode = 1;
      } else {
        exitCode = typeof err.code === 'number' ? err.code : 1;
      }
    }
  }

  if (isTimeout) {
    return {
      passed: false,
      stage: 'stage_1_semantic_parser',
      probability: 0.0,
      exitCode: 124,
      testsRun: 0,
      reason: 'Stage 1 Verification Failed: Test execution timed out after 45000ms and process was terminated.',
      stdout_tail: stdout.slice(-1500),
      stderr_tail: stderr.slice(-1000)
    };
  }

  if (isMaxBuffer) {
    return {
      passed: false,
      stage: 'stage_1_semantic_parser',
      probability: 0.0,
      exitCode: 1,
      testsRun: 0,
      reason: 'Stage 1 Verification Failed: Subprocess output exceeded 10MB maxBuffer limit.',
      stdout_tail: stdout.slice(-1500),
      stderr_tail: stderr.slice(-1000)
    };
  }

  // Step 1: Deterministic semantic regex parsing
  const parsedRun = isNoContract
    ? { passed: true, testsRun: 0, reason: 'No test contract detected in workspace; Stage 1 skipped.' }
    : parseTestRunnerOutput(workspace.ecosystem, stdout, stderr, exitCode, { command: testCommand });

  // Step 1.5 (Lie Detector): If agentStatement is provided, call extractVerifiableClaims and reconcileClaimsWithGroundTruth
  let reconciliationResult = null;
  if (agentStatement) {
    const sessionState = (sessionId && typeof sessionId === 'object') ? sessionId : loadSession(sessionId || 'default');
    const claims = extractVerifiableClaims(agentStatement);
    reconciliationResult = reconcileClaimsWithGroundTruth(
      claims,
      sessionState,
      effectiveTargetDir,
      { passed: parsedRun.passed, exitCode, testsRun: parsedRun.testsRun }
    );

    if (reconciliationResult.reconciled === false) {
      return {
        passed: false,
        stage: 'claim_reconciliation_gate',
        reason: reconciliationResult.reason,
        exitCode,
        testsRun: parsedRun.testsRun ?? 0,
        claimsAudited: true,
        claims,
        hardVeto: reconciliationResult.hardVeto || false
      };
    }
  }

  if (!isNoContract && !parsedRun.passed) {
    return {
      passed: false,
      stage: 'stage_1_semantic_parser',
      probability: 0.0,
      exitCode,
      testsRun: parsedRun.testsRun ?? 0,
      reason: `Stage 1 Verification Failed: ${parsedRun.reason}`,
      stdout_tail: stdout.slice(-1500),
      stderr_tail: stderr.slice(-1000)
    };
  }

  if (!isNoContract && parsedRun.testsRun === 0) {
    return {
      passed: false,
      stage: 'stage_1_semantic_parser',
      probability: 0.0,
      exitCode,
      testsRun: 0,
      reason: 'Stage 1 Verification Failed: Test runner reported 0 tests executed. Empty or unexecuted test suites cannot satisfy completion verification.',
      stdout_tail: stdout.slice(-1500),
      stderr_tail: stderr.slice(-1000)
    };
  }

  // Triage stderr to filter benign warnings (Fix 5)
  const { isBenign, cleanedStderr } = triageStderr(stderr);


  // Step 2: Jev Semantic Completion Gate (P >= 0.85)
  const jevState = {
    workspace_ecosystem: workspace.ecosystem,
    command: isNoContract ? "none (no test contract detected)" : testCommand,
    exit_code: exitCode,
    tests_passed: parsedRun.testsRun ?? (isNoContract ? 0 : 1),
    tests_failed: 0,
    runner_summary: isNoContract ? "No test contract detected in workspace; claim reconciliation applied." : stdout.slice(-600).trim(),
    has_unhandled_stderr: !isBenign && cleanedStderr.length > 0,
    // 7-Pillar Precision Context Metadata
    user_intent: (agentStatement || process.env.TASK_DESCRIPTION || session.taskDescription || 'Complete verified software engineering task').slice(0, 1500),
    test_runner_contract: {
      ecosystem: workspace.ecosystem,
      command: isNoContract ? "none" : testCommand,
      exit_code: exitCode,
      last_test_passed: exitCode === 0 && parsedRun.passed === true
    },
    disk_modification_state: {
      claims_audited: Boolean(agentStatement && reconciliationResult),
      file_modifications: reconciliationResult?.claims?.fileModifications || []
    },
    causal_trajectory: {
      rolling_history: session.rollingHistory ? session.rollingHistory.slice(-5) : [],
      stderr_tail: (stderr || '').slice(-600)
    },
    runtime_metadata: {
      platform: process.platform,
      node_version: process.version,
      arch: process.arch
    },
    authorization_boundary: {
      workspace_root: effectiveTargetDir,
      allowed_paths: [effectiveTargetDir]
    }
  };

  if (agentStatement && reconciliationResult) {
    jevState.agent_statement = agentStatement;
    jevState.claims_audited = true;
    jevState.claims = reconciliationResult.claims;
    jevState.claims_reconciled = reconciliationResult.reconciled;
    jevState.claims_count = reconciliationResult.claimsCount;
  } else {
    jevState.claims_audited = false;
  }

  const assertion = isNoContract
    ? 'Has the agent completed the engineering task consistently with verifiable claims and zero unauthorized modifications?'
    : 'Did the automated test suite complete successfully with zero failures and verified completion status?';

  const criteriaTrue = isNoContract
    ? 'The agent statements and verifiable claims match ground truth on disk with zero errors and no unverified claims.'
    : 'The test runner completed with exit code 0 and reported zero test failures.';

  const criteriaFalse = isNoContract
    ? 'Unreconciled claims, missing files, unauthorized changes, or incomplete task execution.'
    : 'Tests failed, crashed, aborted, or exited with an error.';

  const jevResult = await jevBooleanCheck({
    state: jevState,
    assertion,
    criteriaTrue,
    criteriaFalse,
    isDestructive: false
  });

  const prob = typeof jevResult.probability === 'number' ? jevResult.probability : 0.0;
  const isApproved = jevResult.approved === true && prob >= 0.85 && exitCode === 0;

  const sessionKey = (typeof sessionId === 'string' && sessionId) ? sessionId : 'default';
  recordDecision({
    targetDir: effectiveTargetDir,
    sessionId: sessionKey,
    source: 'acceptance_gate',
    decisionType: isNoContract ? 'stage_2_no_contract_jev_gate' : 'stage_2_jev_gate',
    toolName: 'verifyAcceptanceGate',
    inputSummary: isNoContract ? (agentStatement || 'no test contract check') : testCommand,
    passed: isApproved,
    verdict: isApproved ? 'passed' : 'vetoed',
    probability: prob,
    reason: isApproved
      ? `Verified complete by Jev Acceptance Gate (Probability: ${prob.toFixed(2)} >= 0.85)${agentStatement ? ' with audited and reconciled claims' : ''}`
      : `Rejected by Jev Acceptance Gate (Probability: ${prob.toFixed(2)} < 0.85 threshold). Output indicates unverified or failing state.`,
    metadata: {
      user_intent: jevState.user_intent,
      assertion,
      criteria: {
        true: criteriaTrue,
        false: criteriaFalse
      },
      model: jevResult.model || 'jev-1.13.0',
      seven_pillars: {
        pillar_1_user_intent: jevState.user_intent,
        pillar_2_runtime_metadata: jevState.runtime_metadata,
        pillar_3_target_file_ast: null,
        pillar_4_git_delta: {
          claims_audited: jevState.disk_modification_state.claims_audited,
          file_modifications: jevState.disk_modification_state.file_modifications
        },
        pillar_5_causal_trajectory: jevState.causal_trajectory,
        pillar_6_verification_contract: jevState.test_runner_contract,
        pillar_7_authorization_boundary: jevState.authorization_boundary
      }
    }
  });

  return {
    passed: isApproved,
    stage: isNoContract ? 'stage_2_no_contract_jev_gate' : 'stage_2_jev_gate',
    // Authoritative gate signal: single composite confidence derived from Jev noul probability.
    // Use this field for any downstream auto-apply gate logic.
    // 'probability', 'usage', and 'model' below are supplementary audit metadata only.
    gate_confidence: parseFloat(prob.toFixed(3)),
    // Supplementary audit field -- use gate_confidence for downstream gate logic.
    probability: prob,
    exitCode,
    testsRun: parsedRun.testsRun,
    // Supplementary audit metadata only -- do not use for gate decisions.
    usage: jevResult.usage || {},
    model: jevResult.model,
    claimsAudited: Boolean(agentStatement),
    ...(reconciliationResult ? { claims: reconciliationResult.claims, claimsReconciled: reconciliationResult.reconciled } : {}),
    reason: isApproved
      ? `Verified complete by Jev Acceptance Gate (Probability: ${prob.toFixed(2)} >= 0.85)${agentStatement ? ' with audited and reconciled claims' : ''}`
      : `Rejected by Jev Acceptance Gate (Probability: ${prob.toFixed(2)} < 0.85 threshold). Output indicates unverified or failing state.`,
    stdout_tail: stdout.slice(-1000),
    stderr_tail: stderr.slice(-500)
  };
}

// CLI entrypoint if executed directly
if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  const customCmd = process.argv[2] || null;
  const agentStatement = process.argv[3] || null;
  verifyAcceptanceGate(customCmd, process.cwd(), agentStatement)
    .then(res => {
      console.log(JSON.stringify(res, null, 2));
      process.exit(res.passed ? 0 : 1);
    })
    .catch(err => {
      console.error(err);
      process.exit(1);
    });
}
