/**
 * Sensitive Credential Exfiltration Guard (harness/sensitive-guard.js)
 * Bipartite heuristic fastpath scanner for read operations with Jev security escalation.
 */

import fs from 'fs';
import path from 'path';
import { jevBooleanCheck } from './jev-client.js';

export const SENSITIVE_PATH_PATTERNS = [
  /(^|[\\/])(\.env(\.|$)|id_rsa|id_ed25519|.*\.pem$|.*\.key$)/i,
  /(^|[\\/])etc[\\/](shadow|passwd)/i,
  /(^|[\\/])(system32|syswow64)[\\/]config[\\/](sam|system|security)/i,
  /(^|[\\/])\.\.[\\/]/,
  /(^|[\\/])(\.?secrets|credentials|certs|private_keys)[\\/]/i,
  /\.(pem|key|pkcs12|pfx|p12)$/i,                  // Private key certificates
  /(credentials|secrets|token|auth_token)\.(json|yaml|yml|xml)$/i, // Cloud/app secrets
  /(^|[/\\])\.aws[/\\]/i,                          // AWS credentials
  /(^|[/\\])\.config[/\\]gcloud[/\\]/i,            // GCP credentials
  /(^|[/\\])\.aegis-harness($|[/\\])/i,               // Harness session history (must not be readable by agent)
  /(^|[/\\])\.aegis([/\\].*)?$/i,                 // Aegis audit logs and decision records
  /(^|[/\\])harness([/\\].*)?$/i,                 // Harness infrastructure (must not be accessible to coding agents)
  /(^|[/\\])\.kube[/\\]/i                          // Kubernetes cluster configs
];

export function getCustomDenyPatterns(workspaceDir = process.cwd()) {
  try {
    const configPath = path.join(workspaceDir, '.aegis.json');
    if (fs.existsSync(configPath)) {
      const cfg = JSON.parse(fs.readFileSync(configPath, 'utf8'));
      if (Array.isArray(cfg.sensitive_patterns)) {
        return cfg.sensitive_patterns.map(p => new RegExp(p, 'i'));
      }
    }
  } catch {}
  return [];
}

export const RESTRICTED_DIRECTORIES = SENSITIVE_PATH_PATTERNS;

export const READ_TOOL_NAMES = [
  'view_file',
  'read_file',
  'read_url_content',
  'read_resource',
  'grep',
  'search',
  'cat',
  'head',
  'tail'
];

/**
 * Checks whether a tool is a read-only inspection operation.
 */
export function isReadInspectionTool(toolName = '') {
  const lower = (toolName || '').toLowerCase();
  return READ_TOOL_NAMES.some(name => lower.includes(name));
}

/**
 * Tests whether a path matches credential or exfiltration patterns.
 */
export function isSensitivePath(targetPath = '', workspaceDir = process.cwd()) {
  const rawPath = String(targetPath || '');
  if (!rawPath) return false;

  let decoded = rawPath;
  try {
    decoded = decodeURIComponent(rawPath);
  } catch {}

  const normalized = path.normalize(decoded).replace(/\\/g, '/').replace(/^\.\//, '');
  const rawNormalized = path.normalize(rawPath).replace(/\\/g, '/').replace(/^\.\//, '');

  // Safe exception: .env.example or template files
  if (/\.env\.example$|\.env\.template$/i.test(normalized) ||
      /\.env\.example$|\.env\.template$/i.test(rawNormalized) ||
      /\.env\.example$|\.env\.template$/i.test(decoded) ||
      /\.env\.example$|\.env\.template$/i.test(rawPath)) {
    return false;
  }

  const customPatterns = getCustomDenyPatterns(workspaceDir);
  const allPatterns = [...SENSITIVE_PATH_PATTERNS, ...customPatterns];

  const isDevRepo = path.basename(workspaceDir) === 'jev-mcp' ||
    normalized.includes('/jev-mcp/') ||
    rawNormalized.includes('/jev-mcp/') ||
    process.env.AEGIS_DEV_MODE === 'true';
  const effectivePatterns = isDevRepo ? allPatterns.filter(p => !p.source.includes('harness')) : allPatterns;

  for (const pattern of effectivePatterns) {
    if (pattern.test(normalized) || pattern.test(rawNormalized) || pattern.test(decoded) || pattern.test(rawPath)) {
      return true;
    }
  }
  return false;
}

/**
 * Inspects raw command strings for sensitive credential paths (Issue 4 mitigation)
 */
export function inspectCommandForSensitivePaths(cmdStr = '', workspaceDir = process.cwd()) {
  if (!cmdStr || typeof cmdStr !== 'string') return { isSensitive: false, matchedPattern: null };

  // Skip if referencing safe examples/templates only
  if (/\.env\.(example|template)/i.test(cmdStr)) {
    const stripped = cmdStr.replace(/\.env\.(example|template)/gi, '');
    return inspectCommandForSensitivePaths(stripped, workspaceDir);
  }

  const commandSensitivePatterns = [
    /(^|[\s"'/\=@])\.env(\.[\w-]+)?(\s|$|['"\\])/i,
    /\b(id_rsa|id_ed25519)\b/i,
    /(^|[\s"'/\=@])etc[/\\](shadow|passwd)/i,
    /(^|[\s"'/\=@])\.aws[/\\]/i,
    /(^|[\s"'/\=@])\.config[/\\]gcloud[/\\]/i,
    /(^|[\s"'/\=@])\.kube[/\\]/i,
    /\b(credentials|secrets|token|auth_token)\.(json|yaml|yml|xml)\b/i,
    /\b[\w.-]+\.(pem|key|pkcs12|pfx|p12)\b/i,
    /\bbase64\s+(-d|--decode)\b/i,
    /\b(curl|wget)\b.*(-d|--data|-F|--upload-file|--post-file)\s*['"]?@/i,
    /\b(cat|type|head|tail|more|less|Get-Content|gc)\s+.*(\.env|id_rsa|id_ed25519|passwd|shadow|\.aws|\.kube)/i,
    /\b(fs\.readFileSync|open\s*\()[^)]*(\.env|id_rsa|id_ed25519|passwd|shadow)/i
  ];

  // Strip git commit messages, script eval parameters, echo literals, and search patterns
  const sanitized = cmdStr
    .replace(/(?:-[a-zA-Z]*m|--message)(?:\s+|=)(?:"[\s\S]*?"|'[\s\S]*?'|`[\s\S]*?`|[^\s]+)/gi, '')
    .replace(/\b(?:echo|printf)\s+(?:"[\s\S]*?"|'[\s\S]*?'|`[\s\S]*?`)/gi, '')
    .replace(/\b(?:grep|rg|findstr)\b(?:\s+-[a-zA-Z]+)*\s+(?:"[\s\S]*?"|'[\s\S]*?')/gi, '');

  const customPatterns = getCustomDenyPatterns(workspaceDir);
  const allPatterns = [...commandSensitivePatterns, ...customPatterns];

  for (const pattern of allPatterns) {
    if (pattern.test(sanitized)) {
      return { isSensitive: true, matchedPattern: pattern.toString() };
    }
  }
  return { isSensitive: false, matchedPattern: null };
}

/**
 * Evaluates path security.
 * Fastpath: Benign source files are approved with 0 tokens and 0 latency.
 * Escalation: Sensitive patterns trigger Jev security classifier.
 */
export async function evaluatePathSecurity(toolName = '', targetPath = '', taskContext = '', forceSensitive = false, workspaceDir = process.cwd()) {
  const normalizedPath = targetPath ? path.normalize(String(targetPath)).replace(/\\/g, '/').replace(/^\.\//, '') : '';
  const isRead = isReadInspectionTool(toolName);
  const cmdSensitive = inspectCommandForSensitivePaths(targetPath, workspaceDir).isSensitive;
  const isSensitive = Boolean(forceSensitive) || isSensitivePath(normalizedPath, workspaceDir) || isSensitivePath(targetPath, workspaceDir) || cmdSensitive;

  // Fastpath: If read operation targets standard code and not sensitive, pass at 0 tokens
  if (isRead && !isSensitive) {
    return {
      fastpath: true,
      approved: true,
      isHazard: false,
      tokens: 0,
      reason: 'Layer 1 Fastpath: Benign source file inspection approved at zero token cost.'
    };
  }

  // If not sensitive and not a read tool, let normal vetting proceed
  if (!isSensitive) {
    return {
      fastpath: false,
      approved: true,
      isHazard: false,
      reason: 'Target path does not match credential patterns.'
    };
  }

  // Escalated Layer 2 Security Intercept for sensitive files
  const jevRes = await jevBooleanCheck({
    state: {
      tool: toolName,
      target_path: targetPath,
      task_context: (taskContext || '').slice(0, 500)
    },
    assertion: 'Is accessing this target path legitimate, necessary for the stated task, and free of credential exfiltration risk?',
    criteriaTrue: 'The read operation is safe, essential to the developer-instructed task, and does not leak production secrets or credentials externally.',
    criteriaFalse: 'The operation attempts to read private keys, production credentials, or sensitive secrets that risk data exfiltration.',
    isDestructive: true // Enforce fail-closed on timeout for sensitive paths
  });

  const approved = jevRes.approved === true && (jevRes.probability || 0) >= 0.70;

  return {
    fastpath: false,
    approved,
    isHazard: !approved,
    probability: jevRes.probability || 0,
    timedOut: jevRes.timedOut,
    reason: approved
      ? `Jev Security Warning: Access to sensitive file '${targetPath}' approved under task context.`
      : `[JEV SECURITY BLOCK]: Access to sensitive credential target '${targetPath}' was blocked by Jev Exfiltration Guard.`
  };
}
