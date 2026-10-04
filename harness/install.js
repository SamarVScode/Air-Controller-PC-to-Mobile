#!/usr/bin/env node

/**
 * Universal Drop-In Installer (harness/install.js)
 * Automatically configures and merges lifecycle interceptor hooks for:
 *   - Claude Code (.claude/settings.json)
 *   - Cursor (.cursor/rules/jev-harness.mdc)
 *   - Antigravity / Agent CLI (.agents/hooks.json)
 * 
 * Features:
 *   - Non-destructive safe merge with backup
 *   - Explicit target flags & dry-run support
 */

import fs from 'fs';
import path from 'path';
import os from 'os';
import { fileURLToPath } from 'url';
import { clearDecisionHistory } from './decision-tracker.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Creates a timestamped backup of a file before modifying it.
 */
export function backupFile(filePath) {
  if (fs.existsSync(filePath)) {
    const timestamp = Date.now();
    const backupPath = `${filePath}.bak.${timestamp}`;
    try {
      fs.copyFileSync(filePath, backupPath);
      return backupPath;
    } catch {}
  }
  return null;
}

/**
 * Ensures target workspace has a .env template for TYPESAFE_API_KEY.
 */
export function ensureEnvFile(targetDir, dryRun = false) {
  const envPath = path.join(targetDir, '.env');
  const template = '# TypeSafe Aegis - Jev Cognitive Interceptor\n# Add your TypeSafe API Key below.\nTYPESAFE_API_KEY=your_typesafe_api_key_here\nAEGIS_SHIFT_LEFT=true\nAEGIS_FULL_AUDIT=true\n';

  const gitignorePath = path.join(targetDir, '.gitignore');
  if (!dryRun) {
    if (fs.existsSync(gitignorePath)) {
      const gitignoreContent = fs.readFileSync(gitignorePath, 'utf8');
      const lines = gitignoreContent.split(/\r?\n/).map(l => l.trim());
      if (!lines.includes('.env')) {
        const needsNewline = gitignoreContent.length > 0 && !gitignoreContent.endsWith('\n');
        fs.appendFileSync(gitignorePath, (needsNewline ? '\n' : '') + '.env\n', 'utf8');
      }
    } else {
      fs.writeFileSync(gitignorePath, '.env\n', 'utf8');
    }
  }

  if (!fs.existsSync(envPath)) {
    if (!dryRun) {
      fs.writeFileSync(envPath, template, 'utf8');
    }
    return { created: true, path: envPath };
  }

  const existing = fs.readFileSync(envPath, 'utf8');
  let appendContent = '';
  if (!existing.includes('TYPESAFE_API_KEY')) {
    appendContent += '\n# TypeSafe Aegis Configuration\nTYPESAFE_API_KEY=your_typesafe_api_key_here\n';
  }
  if (!existing.includes('AEGIS_SHIFT_LEFT')) {
    appendContent += 'AEGIS_SHIFT_LEFT=true\n';
  }
  if (!existing.includes('AEGIS_FULL_AUDIT')) {
    appendContent += 'AEGIS_FULL_AUDIT=true\n';
  }
  if (appendContent && !dryRun) {
    fs.appendFileSync(envPath, appendContent, 'utf8');
    return { updated: true, path: envPath };
  }

  return { exists: true, path: envPath };
}

/**
 * Ensures target workspace has a CLAUDE.md with Universal Guidelines.
 */
export function ensureClaudeMarkdown(targetDir, dryRun = false) {
  const claudeFile = path.join(targetDir, 'CLAUDE.md');
  if (fs.existsSync(claudeFile)) {
    return { exists: true, file: claudeFile };
  }

  const content = `# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding
**Don't assume. Don't hide confusion. Surface tradeoffs.**
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First
**Minimum code that solves the problem. Nothing speculative.**
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.
- Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes
**Touch only what you must. Clean up only your own mess.**
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.
- When your changes create orphans: Remove imports/variables/functions that your changes made unused.
- The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution
**Define success criteria. Loop until verified.**
- Transform tasks into verifiable goals:
  - "Add validation" -> "Write tests for invalid inputs, then make them pass"
  - "Fix the bug" -> "Write a test that reproduces it, then make them pass"
  - "Refactor X" -> "Ensure tests pass before and after"
- For multi-step tasks, state a brief plan:
\`\`\`
1. [Step] -> verify: [check]
2. [Step] -> verify: [check]
3. [Step] -> verify: [check]
\`\`\`
- Deterministic Verification: Run the automated test suite (\`npm test\`) before declaring any task complete.

## 5. Token Efficiency & Context Protection
- Universal Research Sandboxing: When reading multi-file documentation (Obsidian vaults, markdown notes, PDF specs, Excel/CSV datasets, or multi-query web searches), never dump raw files or scraped search outputs directly into coordinator context. Delegate bulk research to an ephemeral subagent that distills findings into a compact <1,500 token summary artifact (RESEARCH.md) and terminates. The coordinator only reads the summary artifact once.
- Focal Chunking: When inspecting code files, use targeted line slices (StartLine/EndLine) or symbol grep rather than loading entire multi-thousand-line files.
- Supervisor Coordination: Do not poll subagent or task status in an active loop. Stop calling tools and rely on reactive wakeups.

## 6. Shift-Left Two-Phase Verification (Jev System One)
- Every file write or modification is intercepted in-memory before disk commit.
- Phase 1 (Spec Gate): Jev certifies that the True/False specification contract for the file is rigorous, complete, and non-trivial.
- Phase 2 (Code Gate): Jev mathematically validates that the proposed code satisfies the certified specification without stubs, mock bypasses, or unhandled errors.
- Declare your specification contract in:
  * Tool call Description (e.g., Description: 'Implements JWT RS256 verification and 401 error handler'),
  * File header JSDoc (/** @aegis-contract @claim ... @true ... @false ... */),
  * Or companion file (.<filename>.spec.json).
`;

  if (!dryRun) {
    fs.writeFileSync(claudeFile, content, 'utf8');
  }

  return { created: true, file: claudeFile };
}

/**
 * Ensures target workspace has a GEMINI.md with Universal Guidelines for Antigravity.
 */
export function ensureAntigravityMarkdown(targetDir, dryRun = false) {
  const geminiFile = path.join(targetDir, 'GEMINI.md');
  if (fs.existsSync(geminiFile)) {
    return { exists: true, file: geminiFile };
  }

  const content = `# TypeSafe AI Jev Guardrails Active

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
- Prior to declaring any task complete, run the project's automated test suite (\`npm test\`) to satisfy the Jev Acceptance Gate.
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
`;

  if (!dryRun) {
    fs.writeFileSync(geminiFile, content, 'utf8');
  }

  return { created: true, file: geminiFile };
}

/**
 * Detects installed agent environments in the target directory or user profile.
 */
export function detectInstalledEngines(targetDir = process.cwd()) {
  const engines = [];

  // Antigravity check
  if (fs.existsSync(path.join(targetDir, '.agents')) || fs.existsSync(path.join(os.homedir(), '.agents')) || fs.existsSync(path.join(targetDir, 'GEMINI.md'))) {
    engines.push('antigravity');
  }

  // Claude Code check
  if (fs.existsSync(path.join(targetDir, '.claude')) || fs.existsSync(path.join(targetDir, '.claude.json')) || fs.existsSync(path.join(os.homedir(), '.claude'))) {
    engines.push('claude');
  }

  // Cursor check
  if (fs.existsSync(path.join(targetDir, '.cursor')) || fs.existsSync(path.join(targetDir, '.cursorrules'))) {
    engines.push('cursor');
  }

  // If none explicitly detected, default to antigravity & claude
  if (engines.length === 0) {
    engines.push('antigravity', 'claude');
  }

  return engines;
}

/**
 * Installs Claude Code hooks into .claude/settings.json
 */
export function installClaudeHooks(targetDir, interceptorPath, dryRun = false) {
  const claudeDir = path.join(targetDir, '.claude');
  const settingsFile = path.join(claudeDir, 'settings.json');

  let existing = {};
  if (fs.existsSync(settingsFile)) {
    try {
      const raw = fs.readFileSync(settingsFile, 'utf8').replace(/^\uFEFF/, '');
      existing = JSON.parse(raw);
    } catch {
      existing = {};
    }
  }

  const relPath = path.relative(targetDir, interceptorPath).replace(/\\/g, '/');
  const execPath = (relPath && !relPath.startsWith('..') && !path.isAbsolute(relPath))
    ? (relPath.startsWith('.') ? relPath : `./${relPath}`)
    : interceptorPath;

  const preToolCmd = `node "${execPath}" --engine claude pre-tool`;
  const stopCmd = `node "${execPath}" --engine claude verify-gate`;
  const preInvocationCmd = `node "${execPath}" --engine claude pre-invocation`;

  const updated = { ...existing };
  if (!updated.hooks || typeof updated.hooks !== 'object') {
    updated.hooks = {};
  }
  if (!Array.isArray(updated.hooks.UserPromptSubmit)) updated.hooks.UserPromptSubmit = [];
  const userPromptSubmitHook = { matcher: '.*', hooks: [{ type: 'command', command: preInvocationCmd }] };
  const userPromptSubmitIdx = updated.hooks.UserPromptSubmit.findIndex(h => typeof h === 'object' && h.hooks?.[0]?.command?.includes('interceptor.js'));
  if (userPromptSubmitIdx !== -1) updated.hooks.UserPromptSubmit[userPromptSubmitIdx] = userPromptSubmitHook;
  else updated.hooks.UserPromptSubmit.push(userPromptSubmitHook);

  // Merge PreToolUse
  if (!Array.isArray(updated.hooks.PreToolUse)) updated.hooks.PreToolUse = [];
  const preHook = { matcher: '.*', hooks: [{ type: 'command', command: preToolCmd }] };
  const preIdx = updated.hooks.PreToolUse.findIndex(h => typeof h === 'object' && h.hooks?.[0]?.command?.includes('interceptor.js'));
  if (preIdx !== -1) updated.hooks.PreToolUse[preIdx] = preHook;
  else updated.hooks.PreToolUse.push(preHook);

  // Merge Stop
  if (!Array.isArray(updated.hooks.Stop)) updated.hooks.Stop = [];
  const stopHook = { matcher: '.*', hooks: [{ type: 'command', command: stopCmd }] };
  const stopIdx = updated.hooks.Stop.findIndex(h => typeof h === 'object' && h.hooks?.[0]?.command?.includes('interceptor.js'));
  if (stopIdx !== -1) updated.hooks.Stop[stopIdx] = stopHook;
  else updated.hooks.Stop.push(stopHook);


  const content = JSON.stringify(updated, null, 2) + '\n';

  if (!dryRun) {
    if (!fs.existsSync(claudeDir)) fs.mkdirSync(claudeDir, { recursive: true });
    backupFile(settingsFile);
    fs.writeFileSync(settingsFile, content, 'utf8');
  }

  return { target: 'claude', file: settingsFile, content };
}

/**
 * Installs Antigravity hooks into .agents/hooks.json
 */
export function installAntigravityHooks(targetDir, interceptorPath, dryRun = false) {
  const agentsDir = path.join(targetDir, '.agents');
  const hooksFile = path.join(agentsDir, 'hooks.json');

  let existing = {};
  if (fs.existsSync(hooksFile)) {
    try {
      const raw = fs.readFileSync(hooksFile, 'utf8').replace(/^\uFEFF/, '');
      existing = JSON.parse(raw);
    } catch {
      existing = {};
    }
  }

  const relPath = path.relative(agentsDir, interceptorPath).replace(/\\/g, '/');
  const execPath = (relPath && !path.isAbsolute(relPath))
    ? (relPath.startsWith('.') ? relPath : `./${relPath}`)
    : interceptorPath;

  // On Windows, Antigravity resolves relative paths relative to .agents/.
  // Wrapping relative paths in quotes (e.g., "node \"../harness/interceptor.js\"")
  // causes Antigravity's Windows resolver to create a path with embedded quotes:
  // C:\...\.agents\"..\harness\interceptor.js", which throws Cannot find module.
  // Therefore, relative paths or unspaced paths must NOT have quotes.
  const cleanExecPath = (!execPath.includes(' ') || execPath.startsWith('.'))
    ? execPath.replace(/^["']|["']$/g, '')
    : `"${execPath}"`;

  const preInvocationCmd = `node ${cleanExecPath} --engine antigravity pre-invocation`;
  const preToolCmd = `node ${cleanExecPath} --engine antigravity pre-tool`;
  const postToolCmd = `node ${cleanExecPath} --engine antigravity post-tool`;
  const stopCmd = `node ${cleanExecPath} --engine antigravity verify-gate`;

  const updated = { ...existing };
  delete updated['aegis-guard'];

  // Antigravity official specification: top-level "hooks" containing typed hook events
  if (!updated.hooks || typeof updated.hooks !== 'object') {
    updated.hooks = {};
  }

  // PreInvocation
  const preInvocationEntry = { type: 'command', command: preInvocationCmd };
  if (!Array.isArray(updated.hooks.PreInvocation)) updated.hooks.PreInvocation = [];
  const preInvIdx = updated.hooks.PreInvocation.findIndex(h => {
    const cmd = h?.command || h?.hooks?.[0]?.command || '';
    return cmd.includes('interceptor.js');
  });
  if (preInvIdx !== -1) updated.hooks.PreInvocation[preInvIdx] = preInvocationEntry;
  else updated.hooks.PreInvocation.push(preInvocationEntry);

  // PreToolUse
  const preToolEntry = {
    matcher: '.*',
    hooks: [
      {
        type: 'command',
        command: preToolCmd
      }
    ]
  };
  if (!Array.isArray(updated.hooks.PreToolUse)) updated.hooks.PreToolUse = [];
  const preToolIdx = updated.hooks.PreToolUse.findIndex(h => {
    const cmd = h?.command || h?.hooks?.[0]?.command || '';
    return cmd.includes('interceptor.js');
  });
  if (preToolIdx !== -1) updated.hooks.PreToolUse[preToolIdx] = preToolEntry;
  else updated.hooks.PreToolUse.push(preToolEntry);

  // PostToolUse (Item 19)
  const postToolEntry = {
    matcher: '.*',
    hooks: [
      {
        type: 'command',
        command: postToolCmd
      }
    ]
  };
  if (!Array.isArray(updated.hooks.PostToolUse)) updated.hooks.PostToolUse = [];
  const postToolIdx = updated.hooks.PostToolUse.findIndex(h => {
    const cmd = h?.command || h?.hooks?.[0]?.command || '';
    return cmd.includes('interceptor.js');
  });
  if (postToolIdx !== -1) updated.hooks.PostToolUse[postToolIdx] = postToolEntry;
  else updated.hooks.PostToolUse.push(postToolEntry);

  // Stop
  const stopEntry = {
    type: 'command',
    command: stopCmd
  };
  if (!Array.isArray(updated.hooks.Stop)) updated.hooks.Stop = [];
  const stopIdx = updated.hooks.Stop.findIndex(h => {
    const cmd = h?.command || h?.hooks?.[0]?.command || '';
    return cmd.includes('interceptor.js');
  });
  if (stopIdx !== -1) updated.hooks.Stop[stopIdx] = stopEntry;
  else updated.hooks.Stop.push(stopEntry);

  const content = JSON.stringify(updated, null, 2) + '\n';

  if (!dryRun) {
    if (!fs.existsSync(agentsDir)) fs.mkdirSync(agentsDir, { recursive: true });
    backupFile(hooksFile);
    fs.writeFileSync(hooksFile, content, 'utf8');
  }

  return { target: 'antigravity', file: hooksFile, content };
}

/**
 * Installs Cursor rule into .cursor/rules/jev-harness.mdc
 */
export function installCursorRule(targetDir, interceptorPath, dryRun = false) {
  const cursorRulesDir = path.join(targetDir, '.cursor', 'rules');
  const ruleFile = path.join(cursorRulesDir, 'jev-harness.mdc');

  const ruleContent = `---
description: TypeSafe AI Jev Cognitive Interceptor & Acceptance Gate Enforcement
globs: *
alwaysApply: true
---

# Universal Aegis Guidelines
1. Think Before Coding: State assumptions explicitly and surface tradeoffs before implementing.
2. Simplicity First: Write minimum code that solves the problem; zero speculative abstractions.
3. Surgical Changes: Touch only what you must; avoid repetitive edits to prevent thrashing circuit breakers.
4. Goal-Driven Execution: Run the automated test suite ('npm test' / verify-gate) before declaring completion.
`;

  if (!dryRun) {
    if (!fs.existsSync(cursorRulesDir)) fs.mkdirSync(cursorRulesDir, { recursive: true });
    backupFile(ruleFile);
    fs.writeFileSync(ruleFile, ruleContent, 'utf8');
  }

  return { target: 'cursor', file: ruleFile, content: ruleContent };
}

/**
 * Main install entrypoint
 */
export function runInstall(options = {}) {
  const targetDir = options.targetDir ? path.resolve(options.targetDir) : process.cwd();
  const dryRun = Boolean(options.dryRun);
  const requestedTargets = options.targets || [];
  const interceptorPath = options.interceptorPath || path.resolve(targetDir, 'harness', 'interceptor.js');

  console.log('================================================================================');
  console.log('JEV COGNITIVE HARNESS - UNIVERSAL DROP-IN INSTALLER');
  console.log(`Target Workspace : ${targetDir}`);
  console.log(`Dry Run Mode     : ${dryRun}`);
  console.log('================================================================================\n');

  // Copy harness folder if installing into an external target
  const sourceHarnessDir = path.resolve(__dirname);
  const targetHarnessDir = path.resolve(targetDir, 'harness');
  const isSameDir = process.platform === 'win32'
    ? path.resolve(sourceHarnessDir).toLowerCase() === path.resolve(targetHarnessDir).toLowerCase()
    : path.resolve(sourceHarnessDir) === path.resolve(targetHarnessDir);

  if (!isSameDir && !dryRun) {
    const copyDirRecursive = (src, dest) => {
      if (!fs.existsSync(dest)) fs.mkdirSync(dest, { recursive: true });
      let count = 0;
      const entries = fs.readdirSync(src, { withFileTypes: true });
      for (const entry of entries) {
        const srcPath = path.join(src, entry.name);
        const destPath = path.join(dest, entry.name);
        if (entry.isDirectory()) {
          count += copyDirRecursive(srcPath, destPath);
        } else if (entry.isFile()) {
          fs.copyFileSync(srcPath, destPath);
          count++;
        }
      }
      return count;
    };
    const copiedCount = copyDirRecursive(sourceHarnessDir, targetHarnessDir);
    console.log(` Copied ${copiedCount} harness modules into: ${targetHarnessDir}`);
  }

  // Determine active targets
  let targets = requestedTargets.length > 0 ? requestedTargets : detectInstalledEngines(targetDir);
  if (requestedTargets.includes('all')) {
    targets = ['claude', 'antigravity', 'cursor'];
  }

  const results = [];

  for (const t of targets) {
    if (t === 'claude') {
      const res = installClaudeHooks(targetDir, interceptorPath, dryRun);
      results.push(res);
      console.log(` [Claude Code] Hook configured: ${res.file}`);
      const mdRes = ensureClaudeMarkdown(targetDir, dryRun);
      if (mdRes.created) {
        console.log(` [Claude Code] Generated CLAUDE.md: ${mdRes.file}`);
      } else {
        console.log(` [Claude Code] Existing CLAUDE.md preserved: ${mdRes.file}`);
      }
    } else if (t === 'antigravity') {
      const res = installAntigravityHooks(targetDir, interceptorPath, dryRun);
      results.push(res);
      console.log(` [Antigravity] Hook configured: ${res.file}`);
      const mdRes = ensureAntigravityMarkdown(targetDir, dryRun);
      if (mdRes.created) {
        console.log(` [Antigravity] Generated GEMINI.md: ${mdRes.file}`);
      } else {
        console.log(` [Antigravity] Existing GEMINI.md preserved: ${mdRes.file}`);
      }
    } else if (t === 'cursor') {
      const res = installCursorRule(targetDir, interceptorPath, dryRun);
      results.push(res);
      console.log(` [Cursor] Rule injected: ${res.file}`);
    }
  }
  // Ensure .env configuration file exists
  const envResult = ensureEnvFile(targetDir, dryRun);
  if (envResult.created) {
    console.log(` [Config] Created template .env at: ${envResult.path}`);
  } else if (envResult.updated) {
    console.log(` [Config] Appended TYPESAFE_API_KEY placeholder to: ${envResult.path}`);
  } else {
    console.log(` [Config] Existing .env file verified: ${envResult.path}`);
  }

  // Ensure clean initialized decision audit log on install
  if (!dryRun) {
    clearDecisionHistory({ targetDir });
    console.log(` [Audit] Initialized clean empty decision log: ${path.join(targetDir, '.aegis', 'decision-audit.jsonl')}`);
  }

  console.log('\n================================================================================');
  console.log(`Installation complete for ${results.length} environment(s). Zero-config active!`);
  console.log('Next Steps:');
  console.log('  1. Open .env and set your TYPESAFE_API_KEY');
  console.log('  2. Run your coding agent normally (Claude Code, Cursor, or Antigravity)');
  console.log('================================================================================\n');

  let mainConfig;
  if (results[0]?.content) {
    try {
      const raw = results[0].content.replace(/^\uFEFF/, '');
      mainConfig = JSON.parse(raw);
    } catch {
      mainConfig = buildMergedHooksConfig({}, 'antigravity', interceptorPath);
    }
  } else {
    mainConfig = buildMergedHooksConfig({}, 'antigravity', interceptorPath);
  }

  if (mainConfig) {
    const rawHooks = mainConfig['aegis-guard'] || mainConfig.hooks || mainConfig;
    const extractCmd = (item) => {
      if (typeof item === 'string') return item;
      if (item?.command) return item.command;
      if (item?.hooks?.[0]?.command) return item.hooks[0].command;
      return '';
    };
    mainConfig.hooks = {
      PreToolUse: (rawHooks.PreToolUse || []).map(entry => ({ command: extractCmd(entry) })),
      PostToolUse: (rawHooks.PostToolUse || []).map(entry => ({ command: extractCmd(entry) })),
      Stop: (rawHooks.Stop || []).map(entry => ({ command: extractCmd(entry) }))
    };
    if (rawHooks.PreInvocation) {
      mainConfig.hooks.PreInvocation = (rawHooks.PreInvocation || []).map(entry => ({ command: extractCmd(entry) }));
    }
    if (rawHooks.UserPromptSubmit) {
      mainConfig.hooks.UserPromptSubmit = (rawHooks.UserPromptSubmit || []).map(entry => ({ command: extractCmd(entry) }));
    }
  }
  return {
    success: true,
    dryRun,
    targetDir,
    results,
    config: mainConfig,
    hooksFile: results[0]?.file
  };
}

// CLI handler
const isDirectCli = process.argv[1] && (
  process.platform === 'win32'
    ? path.resolve(process.argv[1]).toLowerCase() === path.resolve(fileURLToPath(import.meta.url)).toLowerCase()
    : path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))
);
if (isDirectCli) {
  const args = process.argv.slice(2);
  let dryRun = false;
  let targetDir = process.cwd();
  const targets = [];

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--dry-run') dryRun = true;
    else if (arg === '--all') targets.push('all');
    else if (arg === '--claude') targets.push('claude');
    else if (arg === '--cursor') targets.push('cursor');
    else if (arg === '--antigravity') targets.push('antigravity');
    else if (arg === '--target-dir' && args[i + 1]) {
      targetDir = args[i + 1];
      i++;
    }
  }

  try {
    runInstall({ dryRun, targetDir, targets });
  } catch (err) {
    console.error('Installer failed:', err.message);
    process.exit(1);
  }
}

export const installHooks = (options = {}) => {
  const opts = typeof options === 'object' ? { ...options } : {};
  if (opts.engine && !opts.targets) {
    opts.targets = [opts.engine];
  }
  return runInstall(opts);
};

export const detectEngine = (targetDir) => detectInstalledEngines(targetDir)[0] || 'antigravity';

export function buildMergedHooksConfig(existingConfig = {}, engine = 'antigravity', interceptorPath = null) {
  const resolvedInterceptor = interceptorPath || path.resolve(__dirname, 'interceptor.js');
  const cleanInterceptor = (engine === 'antigravity' && (!resolvedInterceptor.includes(' ') || resolvedInterceptor.startsWith('.')))
    ? resolvedInterceptor.replace(/^["']|["']$/g, '')
    : `"${resolvedInterceptor}"`;
  const preToolCmd = `node ${cleanInterceptor} --engine ${engine} pre-tool`;
  const postToolCmd = `node ${cleanInterceptor} --engine ${engine} post-tool`;
  const stopCmd = `node ${cleanInterceptor} --engine ${engine} verify-gate`;

  const config = JSON.parse(JSON.stringify(existingConfig || {}));
  const usesNestedHooks = config.hooks && typeof config.hooks === 'object';
  const target = usesNestedHooks ? config.hooks : (config.PreToolUse || config.Stop ? config : (config.hooks = {}));

  const getCmd = (entry) => {
    if (typeof entry === 'string') return entry;
    if (entry?.command) return entry.command;
    if (entry?.hooks?.[0]?.command) return entry.hooks[0].command;
    return '';
  };

  const updateOrPushHook = (arr, cmd, eventName = '') => {
    const idx = arr.findIndex(entry => getCmd(entry).includes('interceptor.js'));
    if (idx !== -1) {
      if (arr[idx]?.hooks?.[0]) {
        arr[idx].hooks[0].command = cmd;
        arr[idx].command = cmd;
      } else if (typeof arr[idx] === 'object') {
        arr[idx].command = cmd;
      } else {
        arr[idx] = { command: cmd };
      }
    } else {
      const isToolHook = eventName === 'PreToolUse' || eventName === 'PostToolUse';
      const newEntry = (isToolHook && engine === 'antigravity')
        ? { matcher: '.*', hooks: [{ type: 'command', command: cmd }], command: cmd }
        : { command: cmd };
      arr.push(newEntry);
    }
  };

  if (!Array.isArray(target.PreToolUse)) target.PreToolUse = [];
  updateOrPushHook(target.PreToolUse, preToolCmd, 'PreToolUse');

  if (engine !== 'claude') {
    if (!Array.isArray(target.PostToolUse)) target.PostToolUse = [];
    updateOrPushHook(target.PostToolUse, postToolCmd, 'PostToolUse');
  }

  if (!Array.isArray(target.Stop)) target.Stop = [];
  updateOrPushHook(target.Stop, stopCmd, 'Stop');

  return config;
}
