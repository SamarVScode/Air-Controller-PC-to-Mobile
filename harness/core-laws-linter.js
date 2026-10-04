/**
 * Universal Code Invariants Linter (harness/core-laws-linter.js)
 * Static pattern linter enforcing universal code safety invariants:
 *   1. Prohibits unsafe dynamic evaluation (eval, new Function, vm.runInThisContext)
 *   2. Prohibits hardcoded private keys & plaintext cryptographic headers in source
 *   3. Prohibits direct prototype pollution (__proto__ assignment)
 * Universal across React, Node, Python, Go, Rust, and polyglot codebases.
 * Can be bypassed per-file with '// aegis-ignore: core-laws' or '// aegis-ignore: all'.
 */

export const PRAGMA_REGEX = /\/\/\s*aegis-ignore:\s*(core-laws|header-map-law|invariants|all)\b/i;

export const INVARIANT_RULES = [
  {
    id: 'UNSAFE_DYNAMIC_EVAL',
    law: 'UNSAFE_DYNAMIC_EVAL',
    description: 'Dynamic code execution (eval or new Function) introduces arbitrary code execution vulnerabilities.',
    pattern: /\b(eval\s*\(|new\s+Function\s*\(|vm\.runInThisContext\s*\()/i
  },
  {
    id: 'HARDCODED_PRIVATE_KEY',
    law: 'HARDCODED_PRIVATE_KEY',
    description: 'Hardcoded plaintext private key detected in source code.',
    pattern: /-----BEGIN\s+([A-Z0-9_-]+\s+)?PRIVATE\s+KEY-----/i
  },
  {
    id: 'PROTOTYPE_POLLUTION',
    law: 'PROTOTYPE_POLLUTION',
    description: 'Direct __proto__ assignment risks object prototype pollution.',
    pattern: /\b__proto__\s*=/i
  }
];

export const GAS_RULES = [
  {
    id: 'HEADER_MAP_LAW',
    law: 'HEADER_MAP_LAW',
    description: 'Hardcoded numeric array indexing prohibited in GAS spreadsheets. Use header map.',
    pattern: /\b(row|data|record)\[\d+\]/
  },
  {
    id: 'SAFE_SERIALIZATION_LAW',
    law: 'SAFE_SERIALIZATION_LAW',
    description: 'Raw Date or Blob objects across google.script.run RPC are not serializable. Pass numeric timestamp or string.',
    pattern: /google\.script\.run\.[a-zA-Z0-9_]+\([^)]*\b(new\s+Blob\b|new\s+Date\s*\(\s*\)(?!\s*\.(getTime|toISOString|valueOf|toUTCString|toDateString|toString)\b))/
  }
];

/**
 * Lints code string against universal safety invariants.
 */
export function lintCoreLaws(codeString = '', filePath = '') {
  const code = String(codeString || '');
  if (!code.trim() || PRAGMA_REGEX.test(code)) {
    return {
      clean: true,
      violationCount: 0,
      violations: []
    };
  }

  const lines = code.split(/\r?\n/);
  const violations = [];
  const normalizedPath = String(filePath || '').replace(/\\/g, '/');
  const isGas = normalizedPath.endsWith('.gs') || normalizedPath.includes('gas/') || (normalizedPath.endsWith('.html') && code.includes('google.script.run'));
  const activeRules = isGas ? [...INVARIANT_RULES, ...GAS_RULES] : INVARIANT_RULES;

  lines.forEach((line, index) => {
    const trimmed = line.trim();
    if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('#')) return;

    // Item 37: Strip inline comments, and for non-key rules, strip string literals to avoid false positives
    const codeWithoutComments = line
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/(?<!:)\/\/.*/g, '');
    const codeWithoutStrings = codeWithoutComments.replace(/(["'`])(?:\\.|[^\\])*?\1/g, '""');

    for (const rule of activeRules) {
      const targetText = rule.id === 'HARDCODED_PRIVATE_KEY' ? codeWithoutComments : codeWithoutStrings;
      if (rule.pattern.test(targetText)) {
        violations.push({
          ruleId: rule.id,
          law: rule.law,
          line: index + 1,
          filePath: filePath || 'unknown',
          description: rule.description,
          sample: trimmed.slice(0, 100)
        });
      }
    }
  });

  return {
    clean: violations.length === 0,
    violationCount: violations.length,
    violations
  };
}

/**
 * Returns true if code contains un-pragmad universal invariant violations.
 */
export function isCoreLawsViolated(codeString = '', filePath = '') {
  return lintCoreLaws(codeString, filePath).violationCount > 0;
}

/**
 * Formats report for violations or clean status.
 */
export function formatCoreLawsReport(violations = []) {
  if (!violations || violations.length === 0) {
    return '[UNIVERSAL LINTER]: All checks passed cleanly (0 violations).';
  }
  const lines = violations.map(v => `  - [${v.ruleId}] Line ${v.line}: ${v.description} (File: ${v.filePath})`);
  return `[UNIVERSAL LINTER VETO]: Detected ${violations.length} code invariant violation(s):\n${lines.join('\n')}`;
}
