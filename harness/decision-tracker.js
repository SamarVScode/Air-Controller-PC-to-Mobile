/**
 * Decision Tracker & Audit Logger (harness/decision-tracker.js)
 * Logs and analyzes all cognitive decisions made by Jev System One,
 * the Acceptance Gate, Cycle Detector, and Interceptor across lifecycle turns.
 */

import fs from 'fs';
import path from 'path';
import { createHash, randomUUID } from 'crypto';

export const DEFAULT_AUDIT_FILENAME = 'decision-audit.jsonl';

/**
 * Resolves the root audit directory for persistent logs (.aegis or session-specific)
 */
export function getAuditLogPath(targetDir = process.cwd()) {
  let resolvedDir = path.resolve(String(targetDir).replace(/^["']|["']$/g, ''));
  if (['.agents', '.claude', '.cursor'].includes(path.basename(resolvedDir))) {
    resolvedDir = path.dirname(resolvedDir);
  }
  const aegisDir = path.join(resolvedDir, '.aegis');
  if (!fs.existsSync(aegisDir)) {
    try {
      fs.mkdirSync(aegisDir, { recursive: true });
    } catch {}
  }
  return path.join(aegisDir, DEFAULT_AUDIT_FILENAME);
}

/**
 * Searches upward from target directory to find the closest existing .aegis/decision-audit.jsonl
 */
export function findAuditLogPath(targetDir = process.cwd()) {
  let curr = path.resolve(String(targetDir).replace(/^["']|["']$/g, ''));
  if (['.agents', '.claude', '.cursor'].includes(path.basename(curr))) {
    curr = path.dirname(curr);
  }
  // If current directory already has .aegis/decision-audit.jsonl, use it immediately
  const localCandidate = path.join(curr, '.aegis', DEFAULT_AUDIT_FILENAME);
  if (fs.existsSync(localCandidate)) {
    return localCandidate;
  }
  const root = path.parse(curr).root;
  let ptr = curr;
  while (ptr && ptr !== root) {
    const candidate = path.join(ptr, '.aegis', DEFAULT_AUDIT_FILENAME);
    if (fs.existsSync(candidate)) {
      return candidate;
    }
    // Stop search at git boundary so child repositories in monorepos do not bleed into parent
    if (fs.existsSync(path.join(ptr, '.git')) && ptr !== curr) {
      break;
    }
    ptr = path.dirname(ptr);
  }
  return getAuditLogPath(targetDir);
}

/**
 * Record a single cognitive decision into the persistent audit trail.
 */
export function recordDecision(entry = {}) {
  try {
    const targetDir = entry.targetDir || process.cwd();
    const sessionId = entry.sessionId || process.env.AEGIS_SESSION_ID || 'default';
    const logPath = findAuditLogPath(targetDir);

    const record = {
      id: entry.id || (typeof randomUUID === 'function' ? randomUUID() : `dec-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`),
      timestamp: entry.timestamp || new Date().toISOString(),
      sessionId: sessionId,
      iteration: entry.iteration ?? null,
      source: entry.source || 'jev_system_one', // 'jev_system_one' | 'acceptance_gate' | 'cycle_detector' | 'sensitive_guard' | 'interceptor'
      decisionType: entry.decisionType || 'eval', // 'destructive_veto' | 'cycle_thrash_veto' | 'claim_reconciliation' | 'test_runner_gate' | 'jev_semantic_gate' | 'security_exfiltration_veto' | 'jev_evaluation'
      toolName: entry.toolName || '',
      inputSummary: (entry.inputSummary || '').toString().slice(0, 300),
      verdict: entry.verdict || (entry.passed === false ? 'vetoed' : 'approved'), // 'approved' | 'vetoed' | 'passed' | 'rejected' | 'bypassed'
      passed: entry.passed !== false,
      noul: typeof entry.noul === 'number' ? entry.noul : null,
      probability: typeof entry.probability === 'number' ? entry.probability : null,
      score: entry.score ?? null,
      latencyMs: typeof entry.latencyMs === 'number' ? entry.latencyMs : null,
      reason: (entry.reason || '').toString().slice(0, 500),
      metadata: entry.metadata || {}
    };

    const line = JSON.stringify(record) + '\n';
    fs.appendFileSync(logPath, line, 'utf8');
    return record;
  } catch (err) {
    // Audit logging must be non-blocking and fail-safe
    return null;
  }
}

/**
 * Retrieve parsed history of decisions from the audit log.
 */
export function getDecisionHistory(options = {}) {
  const targetDir = options.targetDir || process.cwd();
  const sessionId = options.sessionId || null;
  const limit = options.limit || 100;
  const filterType = options.filterType || null;
  const isCustomDir = Boolean(options.targetDir && path.resolve(options.targetDir) !== path.resolve(process.cwd()));
  const logPath = isCustomDir ? getAuditLogPath(targetDir) : findAuditLogPath(targetDir);

  if (!fs.existsSync(logPath)) {
    return [];
  }

  try {
    const content = fs.readFileSync(logPath, 'utf8');
    const lines = content.split('\n').filter(l => l.trim().length > 0);
    const results = [];

    for (let i = lines.length - 1; i >= 0; i--) {
      try {
        const item = JSON.parse(lines[i]);
        if (sessionId && item.sessionId !== sessionId) continue;
        if (filterType && item.decisionType !== filterType && item.source !== filterType) continue;
        results.push(item);
        if (results.length >= limit) break;
      } catch {}
    }

    return results;
  } catch {
    return [];
  }
}

/**
 * Compute aggregate statistics and decision frequency across turns/iterations.
 */
export function getDecisionSummary(options = {}) {
  const history = getDecisionHistory({ ...options, limit: 10000 });
  const totalDecisions = history.length;

  if (totalDecisions === 0) {
    return {
      totalDecisions: 0,
      approvedCount: 0,
      vetoedCount: 0,
      bySource: {},
      byType: {},
      byVerdict: {},
      averageLatencyMs: 0,
      decisionsPerIteration: 0,
      activeSessions: 0,
      recentDecisions: []
    };
  }

  let approvedCount = 0;
  let vetoedCount = 0;
  let totalLatency = 0;
  let latencyRecords = 0;

  const bySource = {};
  const byType = {};
  const byVerdict = {};
  const sessions = new Set();
  const iterations = new Set();

  for (const item of history) {
    if (item.sessionId) sessions.add(item.sessionId);
    if (item.iteration != null) iterations.add(`${item.sessionId}-${item.iteration}`);

    if (item.passed === false || item.verdict === 'vetoed' || item.verdict === 'rejected') {
      vetoedCount++;
    } else {
      approvedCount++;
    }

    bySource[item.source] = (bySource[item.source] || 0) + 1;
    byType[item.decisionType] = (byType[item.decisionType] || 0) + 1;
    byVerdict[item.verdict] = (byVerdict[item.verdict] || 0) + 1;

    if (typeof item.latencyMs === 'number' && item.latencyMs >= 0) {
      totalLatency += item.latencyMs;
      latencyRecords++;
    }
  }

  const iterationCount = iterations.size || sessions.size || 1;
  const decisionsPerIteration = Number((totalDecisions / iterationCount).toFixed(2));
  const averageLatencyMs = latencyRecords > 0 ? Number((totalLatency / latencyRecords).toFixed(1)) : 0;

  return {
    totalDecisions,
    approvedCount,
    vetoedCount,
    bySource,
    byType,
    byVerdict,
    averageLatencyMs,
    decisionsPerIteration,
    activeSessions: sessions.size,
    recentDecisions: history.slice(0, 10)
  };
}

/**
 * Format decision summary for terminal / CLI output.
 */
export function formatDecisionSummary(summary) {
  if (!summary || summary.totalDecisions === 0) {
    return '[AEGIS TRACKER]: No decision records found in .aegis/decision-audit.jsonl';
  }

  const lines = [];
  lines.push('======================================================');
  lines.push('          AGENTAEGIS COGNITIVE DECISION AUDIT');
  lines.push('======================================================');
  lines.push(`Total Decisions Recorded:  ${summary.totalDecisions}`);
  lines.push(`Approved / Passed:         ${summary.approvedCount}`);
  lines.push(`Vetoed / Blocked:          ${summary.vetoedCount}`);
  lines.push(`Active Sessions:           ${summary.activeSessions}`);
  lines.push(`Decisions Per Iteration:   ${summary.decisionsPerIteration} decisions/turn`);
  lines.push(`Avg Decision Latency:      ${summary.averageLatencyMs} ms`);
  lines.push('------------------------------------------------------');
  lines.push('Decisions by Source:');
  for (const [src, count] of Object.entries(summary.bySource)) {
    lines.push(`  - ${src.padEnd(22)} : ${count}`);
  }
  lines.push('Decisions by Type:');
  for (const [typ, count] of Object.entries(summary.byType)) {
    lines.push(`  - ${typ.padEnd(25)} : ${count}`);
  }
  lines.push('------------------------------------------------------');
  lines.push('Recent Decisions (Latest First):');

  for (const dec of (summary.recentDecisions || []).slice(0, 5)) {
    const ts = dec.timestamp ? dec.timestamp.split('T')[1].slice(0, 8) : '--:--:--';
    const status = dec.passed ? '[PASS]' : '[VETO]';
    const typeStr = (dec.decisionType || 'decision').slice(0, 20);
    const summaryStr = (dec.inputSummary || dec.reason || '').slice(0, 40);
    lines.push(`  ${ts} ${status.padEnd(7)} ${typeStr.padEnd(20)} | ${summaryStr}`);
  }
  lines.push('======================================================');
  return lines.join('\n');
}

/**
 * Clear the audit log.
 */
export function clearDecisionHistory(options = {}) {
  const targetDir = options.targetDir || process.cwd();
  const logPath = getAuditLogPath(targetDir);
  try {
    fs.writeFileSync(logPath, '', 'utf8');
    return true;
  } catch {
    return false;
  }
}
