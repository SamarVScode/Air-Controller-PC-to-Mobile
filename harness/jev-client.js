/**
 * Jev Client (harness/jev-client.js)
 * Native zero-dependency fetch client for TypeSafe AI System One (/v1/systemone).
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

/**
 * Automatically attempt to load .env from workspace root without external dependencies
 */
export function loadEnvFile(envPath) {
  if (!fs.existsSync(envPath)) return;
  try {
    const raw = fs.readFileSync(envPath, 'utf8');
    for (const line of raw.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eqIdx = trimmed.indexOf('=');
      if (eqIdx !== -1) {
        const key = trimmed.slice(0, eqIdx).trim();
        let val = trimmed.slice(eqIdx + 1).trim();
        if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
          val = val.slice(1, -1);
        }
        if (!(key in process.env)) {
          process.env[key] = val;
        }
      }
    }
  } catch {}
}

const __dirname = path.dirname(fileURLToPath(import.meta.url));
loadEnvFile(path.join(__dirname, '..', '.env'));
loadEnvFile(path.join(process.cwd(), '.env'));

export function isApiKeyConfigured(targetDir = process.cwd()) {
  const targetEnv = path.join(targetDir, '.env');
  if (fs.existsSync(targetEnv)) {
    try {
      const raw = fs.readFileSync(targetEnv, 'utf8');
      let foundKey = null;
      for (const line of raw.split('\n')) {
        const trimmed = line.trim();
        if (trimmed.startsWith('TYPESAFE_API_KEY=') || trimmed.startsWith('OPENROUTER_API_KEY=')) {
          const eqIdx = trimmed.indexOf('=');
          const val = trimmed.slice(eqIdx + 1).trim().replace(/^['"]|['"]$/g, '');
          foundKey = val;
          break;
        }
      }
      if (foundKey !== null) {
        if (!foundKey || foundKey === 'your_typesafe_api_key_here' || foundKey === 'your_actual_key_here' || foundKey.length < 8) {
          return false;
        }
        return true;
      }
    } catch {}
  }
  loadEnvFile(targetEnv);
  loadEnvFile(path.join(__dirname, '..', '.env'));
  const key = process.env.TYPESAFE_API_KEY || process.env.OPENROUTER_API_KEY || '';
  if (!key) return false;
  const trimmed = key.trim();
  if (trimmed === 'your_typesafe_api_key_here' || trimmed === 'your_actual_key_here' || trimmed === 'your_openrouter_api_key_here' || trimmed.length < 8) {
    return false;
  }
  return true;
}

export const TYPESAFE_API_URL = process.env.TYPESAFE_BASE_URL || 'https://api.typesafe.ai/v1/systemone';
export const OPENROUTER_DECISIONS_URL = 'https://openrouter.ai/api/alpha/decisions';
export const DEFAULT_MODEL = 'jev-1.13.0';
export const DEFAULT_TIMEOUT_MS = 5000;

/**
 * Detects whether a proposed tool call contains destructive patterns.
 * Polymorphic: accepts (toolName, toolArgs) or (state).
 * Patterns: rm -rf, git reset, DROP, DELETE, truncate, kill, Windows aliases, destructive file rewrites.
 */
export function isDestructiveAction(toolNameOrState, maybeToolArgs = {}) {
  let toolName = '';
  let toolArgs = {};
  if (toolNameOrState && typeof toolNameOrState === 'object' && ('proposed_tool' in toolNameOrState || 'tool_args' in toolNameOrState)) {
    toolName = toolNameOrState.proposed_tool || '';
    toolArgs = toolNameOrState.tool_args || {};
  } else {
    toolName = String(toolNameOrState || '');
    toolArgs = maybeToolArgs && typeof maybeToolArgs === 'object' ? maybeToolArgs : {};
  }

  const tool = (toolName || '').toLowerCase();
  const args = toolArgs || {};

  // 1. Check command execution strings
  const cmdStr = (args.command || args.CommandLine || args.cmd || args.script || '').toString();
  if (cmdStr) {
    const destructiveCmdPatterns = [
      /\brm\s+-[a-z]*r[a-z]*f?/i,           // rm -rf, rm -r, rm -fr
      /\b(rd|rmdir)\s+.*\/[sq]/i,          // rd/rmdir /s /q
      /\brmdir\b/i,                         // rmdir
      /\berase\b/i,                         // erase
      /\bdel\s+.*\/[fsq]/i,                 // del /f /s /q
      /\bdel\s+(\/[a-z]\s+)*\/[fq]/i,       // del /f, del /q
      /\bremove-item\b.*-recurse/i,         // PowerShell Remove-Item -Recurse
      /\bri\s+(-[a-zA-Z]*r[a-zA-Z]*\s+)+-[a-zA-Z]*fo/i, // PowerShell Remove-Item alias: ri -r -fo
      /(powershell|pwsh)(\.exe)?\s+.*(-encodedcommand|[-/]enc)/i, // PowerShell encoded command alias
      /\bgit\s+reset\b/i,                   // git reset, git reset --hard
      /\bgit\s+clean\s+-[a-z]*f/i,          // git clean -f
      /\bgit\s+checkout\s+--\s+\./i,        // git checkout -- .
      /\bdrop\s+(table|database|schema)\b/i,// DROP TABLE, DROP DATABASE
      /\bdelete\s+from\b/i,                 // DELETE FROM
      /\btruncate(\s+table)?\b/i,           // TRUNCATE TABLE
      /\b(kill|pkill|killall|taskkill)\b/i, // kill, pkill, taskkill
      />\s*\/dev\/null/i                    // wipe redirect
    ];

    for (const pattern of destructiveCmdPatterns) {
      if (pattern.test(cmdStr)) return true;
    }

    // Check for encoded command obfuscation (e.g. powershell -enc / -e / -encodedcommand)
    const encodedMatch = cmdStr.match(/(powershell|pwsh)(\.exe)?\s+.*(-encodedcommand|[-/]enc|-e)\s+([A-Za-z0-9+/=]+)/i);
    if (encodedMatch && encodedMatch[4]) {
      try {
        const b64 = encodedMatch[4];
        const decodedUtf16 = Buffer.from(b64, 'base64').toString('utf16le');
        const decodedUtf8 = Buffer.from(b64, 'base64').toString('utf8');
        for (const pattern of destructiveCmdPatterns) {
          if (pattern.test(decodedUtf16) || pattern.test(decodedUtf8)) return true;
        }
      } catch {}
    }
  }

  // 2. Check full serialized arguments for destructive patterns across any parameter
  let serializedArgs = '';
  try { serializedArgs = JSON.stringify(args); } catch {}

  const globalDestructivePatterns = [
    /\brm\s+-[a-z]*r[a-z]*f?/i,
    /\b(rd|rmdir)\s+.*\/[sq]/i,
    /\berase\b/i,
    /\bdel\s+.*\/[fsq]/i,
    /\bri\s+(-[a-zA-Z]*r[a-zA-Z]*\s+)+-[a-zA-Z]*fo/i,
    /(powershell|pwsh)(\.exe)?\s+.*(-encodedcommand|[-/]enc)/i,
    /\bgit\s+reset\b/i,
    /\bdrop\s+(table|database|schema)\b/i,
    /\bdelete\s+from\b/i,
    /\btruncate\s+table\b/i
  ];
  for (const pattern of globalDestructivePatterns) {
    if (pattern.test(serializedArgs)) return true;
  }

  // 3. Destructive file rewrites: overwriting files with empty/nullified content (Bug 12)
  if (tool.includes('write')) {
    const isExplicitOverwrite = args.Overwrite === true || args.overwrite === true;
    const targetPath = args.TargetFile || args.file_path || args.path || args.target || '';
    const fileExists = targetPath && fs.existsSync(targetPath);
    const isOverwritingExisting = isExplicitOverwrite || Boolean(fileExists);
    const content = args.CodeContent ?? args.code ?? args.content;
    if (isOverwritingExisting && typeof content === 'string' && content.trim().length === 0) {
      return true;
    }
  }

  return false;
}

/**
 * Handles bipartite timeout fail-safe:
 * Destructive operations -> Hard fail-closed block.
 * Benign operations -> Fail-open with warning.
 */
export function handleBipartiteFailSafe(isDestructive, error) {
  const isAuthError = /401|unauthorized|invalid api key|neither typesafe_api_key/i.test(error?.message || '');
  if (isAuthError) {
    return {
      approved: false,
      isHazard: true,
      timedOut: false,
      fallback: true,
      probability: 0.0,
      reason: '[AEGIS GUARD FATAL]: Unauthorized or invalid API key. Hard fail-closed enforced.'
    };
  }

  const isTimeout = error?.name === 'AbortError' || error?.code === 'ETIMEDOUT' || /timed out/i.test(error?.message || '');

  if (isDestructive) {
    return {
      approved: false,
      isHazard: true,
      timedOut: isTimeout,
      fallback: true,
      probability: 0.0,
      reason: `[JEV SECURITY BLOCK]: API call failed (${error?.message || 'timeout'}) during a potentially destructive operation. Hard fail-closed enforced. Blocked for safety.`
    };
  }

  return {
    approved: true,
    isHazard: false,
    timedOut: isTimeout,
    fallback: true,
    probability: 0.5,
    reason: `[JEV WARN]: API call failed (${error?.message || 'timeout'}); failing open for benign action. Developer review advised.`
  };
}

/**
 * Raw client call to TypeSafe AI System One with AbortController timeout
 */
export async function callJevSystemOne({
  state,
  questions,
  apiKey,
  model = DEFAULT_MODEL,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  maxRetries = 3
}) {
  const resolvedApiKey = apiKey || process.env.TYPESAFE_API_KEY || process.env.OPENROUTER_API_KEY;
  if (!resolvedApiKey) {
    throw new Error('Neither TYPESAFE_API_KEY nor OPENROUTER_API_KEY is configured in the environment.');
  }

  const isTypeSafeNative = resolvedApiKey.startsWith('apikey_') || Boolean(process.env.TYPESAFE_API_KEY);
  const targetUrl = isTypeSafeNative ? TYPESAFE_API_URL : OPENROUTER_DECISIONS_URL;
  const resolvedModel = model || (isTypeSafeNative ? DEFAULT_MODEL : '~typesafe/jev-latest');

  const payload = {
    model: resolvedModel,
    state,
    questions
  };

  const headers = {
    'Authorization': `Bearer ${resolvedApiKey}`,
    'Content-Type': 'application/json'
  };

  if (!isTypeSafeNative) {
    headers['HTTP-Referer'] = 'https://typesafe.ai';
    headers['X-Title'] = 'jev-agent-harness';
  }

  let lastError = null;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const response = await fetch(targetUrl, {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
        signal: controller.signal
      });

      clearTimeout(timer);

      const responseText = await response.text();
      let responseData;
      try {
        responseData = JSON.parse(responseText);
      } catch {
        throw new Error(`TypeSafe Decisions API returned non-JSON (status ${response.status}): ${responseText}`);
      }

      if (!response.ok) {
        const isTransient = response.status === 429 || response.status === 503 || response.status === 502 || response.status === 529;
        if (isTransient && attempt < maxRetries) {
          const delay = Math.min(Math.pow(2, attempt) * 1000, 4000);
          await new Promise(r => setTimeout(r, delay));
          continue;
        }
        const msg = responseData?.error?.message || responseData?.message || JSON.stringify(responseData);
        const apiErr = new Error(`TypeSafe API error (status ${response.status}): ${msg}`);
        apiErr.status = response.status;
        apiErr.isFatal = response.status >= 400 && response.status < 500 && response.status !== 429;
        throw apiErr;
      }

      return responseData;
    } catch (err) {
      clearTimeout(timer);
      if (err.name === 'AbortError') {
        const timeoutErr = new Error(`TypeSafe API call timed out after ${timeoutMs}ms`);
        timeoutErr.code = 'ETIMEDOUT';
        throw timeoutErr;
      }
      if (err.isFatal || (err.status >= 400 && err.status < 500 && err.status !== 429)) {
        throw err;
      }
      if (attempt === maxRetries) throw err;
      lastError = err;
      const delay = Math.min(Math.pow(2, attempt) * 1000, 4000);
      await new Promise(r => setTimeout(r, delay));
    }
  }

  throw lastError || new Error('TypeSafe API call failed after retries');
}

/**
 * Boolean Check wrapper with bipartite timeout handling
 */
export async function jevBooleanCheck({
  state,
  assertion,
  criteriaTrue,
  criteriaFalse,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  isDestructive = false
}) {
  try {
    const res = await callJevSystemOne({
      state,
      questions: {
        boolean_check: {
          type: 'noul',
          instructions: assertion,
          criteria: {
            true: criteriaTrue || 'The assertion is true, valid, or compliant',
            false: criteriaFalse || 'The assertion is false, invalid, or non-compliant'
          }
        }
      },
      timeoutMs
    });

    const noul = res.answers?.boolean_check?.noul;
    const prob = typeof noul === 'number' ? noul : 0.5;
    const result = prob >= 0.5;

    return {
      approved: result,
      probability: prob,
      result,
      confidence: Math.round(Math.abs(prob - 0.5) * 2 * 100) / 100,
      usage: res.usage || {},
      model: res.model,
      timedOut: false
    };
  } catch (err) {
    return handleBipartiteFailSafe(isDestructive, err);
  }
}

/**
 * Rubric Score wrapper
 */
export async function jevRubricScore({
  state,
  rubric = [],
  instructions = 'Score the provided state according to the rubric criteria.',
  timeoutMs = DEFAULT_TIMEOUT_MS
}) {
  const res = await callJevSystemOne({
    state,
    questions: {
      rubric_score: {
        type: 'score',
        instructions,
        criteria: rubric
      }
    },
    timeoutMs
  });

  const scoreData = res.answers?.rubric_score;
  const score = typeof scoreData?.score === 'number' ? scoreData.score : 0;
  const rounded = Math.round(score);
  const legend = scoreData?.legend || {};
  const description = legend[rounded] || legend[String(rounded)] || `Score ${score}`;

  return {
    score,
    description,
    confidence: scoreData?.confidence ?? 1.0,
    usage: res.usage || {},
    model: res.model
  };
}

/**
 * Classification wrapper
 */
export async function jevClassify({
  state,
  instructions,
  criteria,
  timeoutMs = DEFAULT_TIMEOUT_MS
}) {
  const res = await callJevSystemOne({
    state,
    questions: {
      category: {
        type: 'choice',
        instructions: instructions || 'Classify the item.',
        criteria
      }
    },
    timeoutMs
  });

  const choiceData = res.answers?.category;
  return {
    choice: choiceData?.choice,
    probabilities: choiceData?.probabilities || {},
    confidence: choiceData?.confidence ?? 1.0,
    usage: res.usage || {},
    model: res.model
  };
}
