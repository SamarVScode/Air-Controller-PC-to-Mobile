/**
 * Test Runner Semantic Output Parser (harness/runner-parser.js)
 * Multi-framework semantic test output regex parsers (Jest/Vitest, pytest, cargo, gradlew, tsc, go).
 */

export const BENIGN_STDERR_PATTERNS = [
  /ExperimentalWarning:/i,
  /DeprecationWarning:/i,
  /\(node:\d+\)/i,
  /punycode/i,
  /Debugger listening/i,
  /For more information, see/i,
  /trace-warnings/i,
  /\(Use `node/i
];

/**
 * Triages stderr output to filter benign runtime warnings from genuine failures.
 */
export function triageStderr(stderr = '') {
  if (!stderr || !stderr.trim()) return { isBenign: true, cleanedStderr: '' };
  const lines = stderr.split('\n').map(l => l.trim()).filter(Boolean);
  const criticalLines = lines.filter(line => !BENIGN_STDERR_PATTERNS.some(p => p.test(line)));
  return {
    isBenign: criticalLines.length === 0,
    cleanedStderr: criticalLines.join('\n')
  };
}

export function parseTestRunnerOutput(ecosystem = 'node', stdout = '', stderr = '', exitCode = 0, options = {}) {
  // 1. If process exited with non-zero code, it failed deterministically
  if (exitCode !== 0) {
    return {
      passed: false,
      reason: `Process exited with non-zero exit code ${exitCode}.`,
      exitCode
    };
  }

  const combined = `${stdout}\n${stderr}`;
  const trimmedStdout = stdout.trim();

  // 2. Detect echo spoofing (e.g. echo "All tests passed!" with exit 0)
  if (/^["']?(all\s+tests?\s+passed|tests?\s+ok|success)["']?$/im.test(trimmedStdout) && trimmedStdout.split('\n').length <= 2) {
    return {
      passed: false,
      spoofDetected: true,
      reason: 'Spoofed test completion: plain shell echo statement detected without actual test runner telemetry.'
    };
  }

  // TAP stream test failure matcher
  if (/^not ok\b/m.test(combined)) {
    return {
      passed: false,
      reason: 'TAP stream test failure detected'
    };
  }

  // 3. Framework-specific semantic regex parsing
  const rawEco = (ecosystem || '').toLowerCase();
  const normalizedEco = ['javascript', 'typescript', 'jest', 'vitest', 'mocha', 'node'].includes(rawEco) ? 'node' : rawEco;
  switch (normalizedEco) {
    case 'node': {
      // Reject 100% skipped test suites
      const skippedMatch = combined.match(/Tests:\s+(\d+)\s+skipped,\s+(\d+)\s+total/i);
      if (skippedMatch && parseInt(skippedMatch[1], 10) === parseInt(skippedMatch[2], 10) && parseInt(skippedMatch[2], 10) > 0) {
        return { passed: false, testsRun: 0, reason: 'All tests in test suite were skipped.' };
      }

      // Mocha failure parser
      const mochaMatch = combined.match(/(\d+)\s+passing.*?\n\s*(\d+)\s+failing/i);
      if (mochaMatch) {
        const passing = parseInt(mochaMatch[1], 10);
        const failing = parseInt(mochaMatch[2], 10);
        if (failing > 0) {
          return { passed: false, testsRun: passing + failing, reason: `${failing} Mocha test(s) failing.` };
        }
      }

      // Mocha passing parser (when 0 failing)
      const mochaPassMatch = combined.match(/(\d+)\s+passing\b/i);
      if (mochaPassMatch && !/failing/i.test(combined)) {
        const passedCount = parseInt(mochaPassMatch[1], 10);
        if (passedCount > 0) {
          return { passed: true, testsRun: passedCount };
        }
      }

      // Jest / Vitest: "Tests: 12 passed, 12 total" or "Tests: 5 passed, 5 total"
      const jestMatch = combined.match(/Tests:\s+(\d+)\s+passed,\s+(\d+)\s+total/i);
      if (jestMatch) {
        const passed = parseInt(jestMatch[1], 10);
        const total = parseInt(jestMatch[2], 10);
        if (total === 0) {
          return { passed: false, testsRun: 0, reason: 'Runner reported 0 total tests executed.' };
        }
        if (passed < total) {
          return { passed: false, testsRun: total, reason: `Only ${passed} of ${total} tests passed.` };
        }
        return { passed: true, testsRun: passed };
      }

      // Generic summary / Custom runner: "Test Summary: N passed, N failed" or "TEST RESULTS: N PASSED, N FAILED"
      const summaryMatch = combined.match(/(?:Test Summary|TEST RESULTS):\s*(\d+)\s+passed,\s*(\d+)\s+failed/i);
      if (summaryMatch) {
        const passed = parseInt(summaryMatch[1], 10);
        const failed = parseInt(summaryMatch[2] || '0', 10);
        if (failed > 0) return { passed: false, testsRun: passed + failed, reason: `${failed} test(s) failed.` };
        if (passed === 0) return { passed: false, testsRun: 0, reason: '0 tests passed in runner output.' };
        return { passed: true, testsRun: passed };
      }

      // node:test native runner: "tests 12\npass 12\nfail 0"
      const nodeTestMatch = combined.match(/(?:\u2139|[iI])?\s*pass\s+(\d+)\s+(?:\u2139|[iI])?\s*fail\s+(\d+)/i);
      if (nodeTestMatch) {
        const passed = parseInt(nodeTestMatch[1], 10);
        const failed = parseInt(nodeTestMatch[2], 10);
        if (failed > 0) return { passed: false, reason: `${failed} test(s) failed.` };
        if (passed === 0) return { passed: false, reason: '0 tests passed in node:test.' };
        return { passed: true, testsRun: passed };
      }

      // Empty test runner execution
      if (/No tests found|No test files found/i.test(combined)) {
        return { passed: false, reason: 'Test runner executed with exit 0, but reported: No tests found.' };
      }

      // Generic success marker: "All passed successfully", "All N tests passed"
      if (/All\s+(passed successfully|\d+\s+tests\s+passed)/i.test(combined)) {
        return { passed: true, testsRun: 1 };
      }
      break;
    }

    case 'python': {
      // Pytest: "== 5 passed in 0.23s =="
      if (/collected 0 items|no tests ran/i.test(combined)) {
        return { passed: false, testsRun: 0, reason: 'Pytest executed but 0 test items were collected.' };
      }

      const pytestMatch = combined.match(/==+\s+(\d+)\s+passed/i);
      if (pytestMatch) {
        const count = parseInt(pytestMatch[1], 10);
        if (count === 0) return { passed: false, reason: 'Pytest reported 0 tests passed.' };
        return { passed: true, testsRun: count };
      }

      // Python unittest: "Ran 8 tests in 0.05s\n\nOK"
      const unittestMatch = combined.match(/Ran\s+(\d+)\s+tests?\s+in\s+[\d\.]+s\s*\n\s*OK/i);
      if (unittestMatch) {
        return { passed: true, testsRun: parseInt(unittestMatch[1], 10) };
      }
      break;
    }

    case 'rust': {
      // Cargo test: "test result: ok. 8 passed; 0 failed; 0 ignored;"
      const cargoMatch = combined.match(/test result: ok\.\s+(\d+)\s+passed;\s+(\d+)\s+failed/i);
      if (cargoMatch) {
        const passed = parseInt(cargoMatch[1], 10);
        const failed = parseInt(cargoMatch[2], 10);
        if (failed > 0) return { passed: false, reason: `${failed} cargo tests failed.` };
        if (passed === 0) return { passed: false, reason: 'Cargo test reported 0 tests passed.' };
        return { passed: true, testsRun: passed };
      }
      break;
    }

    case 'go': {
      // Go test: "--- PASS: TestLogin (0.00s)" or "PASS\nok  command-line-arguments"
      if (/--- FAIL:/i.test(combined) || /FAIL\s+.*\[build failed\]/i.test(combined)) {
        return { passed: false, reason: 'Go test reported test failures or build errors.' };
      }
      if (/PASS\s*\n\s*ok/i.test(combined) || /--- PASS:/i.test(combined)) {
        const passCount = (combined.match(/--- PASS:/g) || []).length || 1;
        return { passed: true, testsRun: passCount };
      }
      break;
    }

    case 'android': {
      // Gradle: "BUILD SUCCESSFUL in 12s", "12 tests completed, 0 failed"
      if (/BUILD FAILED/i.test(combined)) {
        return { passed: false, reason: 'Gradle build and test suite reported BUILD FAILED.' };
      }
      const gradleMatch = combined.match(/(\d+)\s+tests?\s+completed,\s+(\d+)\s+failed/i);
      if (gradleMatch) {
        const completed = parseInt(gradleMatch[1], 10);
        const failed = parseInt(gradleMatch[2], 10);
        if (failed > 0) return { passed: false, reason: `${failed} Android tests failed.` };
        return { passed: true, testsRun: completed };
      }
      if (/BUILD SUCCESSFUL/i.test(combined)) {
        return { passed: true };
      }
      break;
    }

    case 'gas': {
      // Clasp / TypeScript: "Found 0 errors. Watching for file changes." or clean exit
      if (/error TS\d+:/i.test(combined)) {
        return { passed: false, reason: 'TypeScript compilation errors detected in GAS backend.' };
      }
      const cmd = String(options.command || options.testCommand || '').toLowerCase();
      const isBuildCheck = /clasp\s+(status|push)|tsc\s+--noEmit/i.test(cmd) || /clasp\s+(status|push)|tsc\s+--noEmit/i.test(combined) || !/test/i.test(cmd || combined);
      return {
        passed: true,
        testsRun: 1,
        isBuildVerification: isBuildCheck,
        reason: isBuildCheck ? 'Google Apps Script build / manifest verified cleanly.' : 'GAS tests passed.'
      };
    }

    case 'c': {
      // Make / CTest
      if (/FAIL|FAILED|Assertion failed/i.test(combined)) {
        return { passed: false, reason: 'C/C++ test suite reported failure assertions.' };
      }
      if (/100% tests passed|All \d+ tests passed/i.test(combined)) {
        return { passed: true, testsRun: 1 };
      }
      break;
    }
  }

  // 4. Fallback check for unhandled stack traces or failure markers even if exit code was 0
  if (/\b(AssertionError|Traceback \(most recent call last\)|UnhandledPromiseRejection|FATAL ERROR)\b/i.test(combined)) {
    return {
      passed: false,
      reason: 'Process exited with code 0 but unhandled exceptions or error tracebacks were found in logs.'
    };
  }

  // Item 25: Fallback for clean exit code 0 when runner produces unrecognized or quiet output without failure markers
  if (exitCode === 0) {
    const trimmed = combined.trim();
    if (!trimmed) {
      return {
        passed: false,
        testsRun: 0,
        reason: 'Test command exited with code 0 but emitted zero test telemetry or execution output.'
      };
    }
    if (!/\b(FAIL|FAILED|AssertionError|Error:|Errors:|Fatal)\b/i.test(combined)) {
      return {
        passed: true,
        testsRun: 1,
        fallbackSuccess: true,
        reason: 'Test command exited with code 0 and clean output.'
      };
    }
  }

  // If no recognized framework telemetry pattern matched and failures or unrecognized output present, reject
  return {
    passed: false,
    testsRun: 0,
    unparseable: true,
    reason: 'Unrecognized test runner telemetry. Could not deterministically verify passing tests from output.'
  };
}
