/**
 * Manifest Sniffer (harness/manifest-sniffer.js)
 * Dedicated zero-config ecosystem detector for Web, Node, Python, Rust, Go, and Mobile projects.
 */

import fs from 'fs';
import path from 'path';

/**
 * Searches upward from startDir to find the closest directory containing any of the target files.
 */
export function findWorkspaceRoot(startDir = process.cwd(), markers = []) {
  let current = path.resolve(startDir);
  const root = path.parse(current).root;
  const visited = new Set();

  while (current !== root) {
    let canonical = current;
    try {
      canonical = fs.realpathSync(current);
    } catch {}

    if (visited.has(canonical)) break;
    visited.add(canonical);

    for (const marker of markers) {
      if (fs.existsSync(path.join(current, marker))) {
        return current;
      }
    }
    const parent = path.dirname(current);
    if (parent === current) break;
    current = parent;
  }
  return startDir;
}

/**
 * Searches upward from startDir (up to maxLevels) returning the directory containing any marker, or null.
 */
export function findMarkerUpward(startDir, markers = [], maxLevels = 3) {
  try {
    let current = path.resolve(startDir);
    const visited = new Set();

    for (let i = 0; i <= maxLevels; i++) {
      let canonical = current;
      try {
        canonical = fs.realpathSync(current);
      } catch {}

      if (visited.has(canonical)) break;
      visited.add(canonical);

      for (const marker of markers) {
        if (fs.existsSync(path.join(current, marker))) return current;
      }
      const parent = path.dirname(current);
      if (parent === current) break;
      current = parent;
    }
  } catch {}
  return null;
}

/**
 * Detects whether a specific file or code snippet belongs to the Google Apps Script ecosystem.
 * Checks:
 *   1. File extension: .gs
 *   2. Path segment: /gas/, /appsscript/, /clasp/, or appsscript.json
 *   3. Workspace root files: .clasp.json or appsscript.json
 *   4. Code content keywords: SpreadsheetApp, google.script.run, HtmlService, Utilities.formatDate
 */
export function isGasContext(filePath = '', codeString = '') {
  const fileStr = String(filePath || '');
  const codeStr = String(codeString || '');

  // 1. Explicit file extension
  if (fileStr.endsWith('.gs')) return true;

  // 2. Explicit GAS path segment
  if (/(^|[\\/])(gas|appsscript|clasp)([\\/]|$)/i.test(fileStr) || fileStr.includes('appsscript.json')) return true;

  // 3. In-code Google Sheets / Apps Script APIs
  const hasGasApis = /\b(SpreadsheetApp|google\.script\.run|HtmlService|Utilities\.formatDate)\b/.test(codeStr);
  if (hasGasApis) {
    return true;
  }

  // Prevent hybrid workspace poisoning:
  // If filePath ends with non-GAS web extensions AND codeString does NOT contain GAS APIs,
  // return false even if .clasp.json exists at root.
  const nonGasExtensions = ['.tsx', '.jsx', '.vue', '.svelte', '.py', '.go', '.rs', '.java', '.cpp', '.c'];
  if (nonGasExtensions.some(ext => fileStr.toLowerCase().endsWith(ext))) {
    return false;
  }

  // 4. Workspace root indicators (walks upward up to 3 levels from file directory, then checks process.cwd)
  try {
    if (fileStr) {
      const startDir = path.dirname(path.resolve(fileStr));
      if (findMarkerUpward(startDir, ['.clasp.json', 'appsscript.json'], 3)) {
        return true;
      }
    }
    if (fs.existsSync(path.join(process.cwd(), '.clasp.json')) || fs.existsSync(path.join(process.cwd(), 'appsscript.json'))) {
      return true;
    }
  } catch {}

  return false;
}

export const isGasTarget = isGasContext;

/**
 * Detects workspace ecosystem and returns test execution contract.
 * Ecosystems: Web (npm/yarn/pnpm/bun), GAS (clasp/tsc), Android (gradlew), and Backend (pytest/cargo/go/make).
 */
export function detectWorkspaceEcosystem(targetDir = process.cwd()) {
  const dir = path.resolve(targetDir);

  const exists = (file) => fs.existsSync(path.join(dir, file));

  const markersFound = [];

  // 1. Google Apps Script (GAS) check
  // Markers: .clasp.json, appsscript.json
  if (exists('.clasp.json') || exists('appsscript.json')) {
    if (exists('.clasp.json')) markersFound.push('.clasp.json');
    if (exists('appsscript.json')) markersFound.push('appsscript.json');

    const hasTsConfig = exists('tsconfig.json');
    if (hasTsConfig) markersFound.push('tsconfig.json');

    return {
      ecosystem: 'gas',
      packageManager: 'clasp',
      testCommand: hasTsConfig ? 'npx tsc --noEmit' : 'npx clasp status',
      fallbackCommand: 'npx clasp status',
      rootDir: dir,
      markers: markersFound
    };
  }

  // 2. Android (Gradle) check
  // Markers: gradlew, gradlew.bat, build.gradle, build.gradle.kts
  const hasGradlew = exists('gradlew') || exists('gradlew.bat');
  const hasBuildGradle = exists('build.gradle') || exists('build.gradle.kts');
  if (hasGradlew || hasBuildGradle) {
    if (exists('gradlew')) markersFound.push('gradlew');
    if (exists('gradlew.bat')) markersFound.push('gradlew.bat');
    if (exists('build.gradle')) markersFound.push('build.gradle');
    if (exists('build.gradle.kts')) markersFound.push('build.gradle.kts');

    const gradleCmd = process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew';
    return {
      ecosystem: 'android',
      packageManager: 'gradlew',
      testCommand: `${gradleCmd} test`,
      fallbackCommand: `${gradleCmd} check`,
      rootDir: dir,
      markers: markersFound
    };
  }

  // 3. Rust (Cargo) check
  if (exists('Cargo.toml')) {
    markersFound.push('Cargo.toml');
    return {
      ecosystem: 'rust',
      packageManager: 'cargo',
      testCommand: 'cargo test',
      fallbackCommand: 'cargo test --all',
      rootDir: dir,
      markers: markersFound
    };
  }

  // 4. Go check
  if (exists('go.mod')) {
    markersFound.push('go.mod');
    return {
      ecosystem: 'go',
      packageManager: 'go',
      testCommand: 'go test ./...',
      fallbackCommand: 'go test -v ./...',
      rootDir: dir,
      markers: markersFound
    };
  }

  // 5. Python check
  // Precedence: poetry.lock > Pipfile.lock > pyproject.toml > requirements.txt / setup.py
  const isPython = exists('pyproject.toml') || exists('poetry.lock') || exists('Pipfile') || exists('Pipfile.lock') || exists('requirements.txt') || exists('setup.py');
  if (isPython) {
    if (exists('poetry.lock')) {
      markersFound.push('poetry.lock');
      return {
        ecosystem: 'python',
        packageManager: 'poetry',
        testCommand: 'poetry run pytest',
        fallbackCommand: 'poetry run python -m unittest',
        rootDir: dir,
        markers: markersFound
      };
    }
    if (exists('Pipfile.lock') || exists('Pipfile')) {
      if (exists('Pipfile.lock')) markersFound.push('Pipfile.lock');
      else markersFound.push('Pipfile');
      return {
        ecosystem: 'python',
        packageManager: 'pipenv',
        testCommand: 'pipenv run pytest',
        fallbackCommand: 'pipenv run python -m unittest',
        rootDir: dir,
        markers: markersFound
      };
    }
    if (exists('pyproject.toml')) markersFound.push('pyproject.toml');
    if (exists('requirements.txt')) markersFound.push('requirements.txt');
    if (exists('setup.py')) markersFound.push('setup.py');

    // Check for local virtual environment
    const hasVenv = exists('.venv') || exists('venv');
    const venvPytest = process.platform === 'win32'
      ? (exists('.venv\\Scripts\\pytest.exe') ? '.venv\\Scripts\\pytest.exe' : (exists('venv\\Scripts\\pytest.exe') ? 'venv\\Scripts\\pytest.exe' : 'pytest'))
      : (exists('.venv/bin/pytest') ? '.venv/bin/pytest' : (exists('venv/bin/pytest') ? 'venv/bin/pytest' : 'pytest'));

    return {
      ecosystem: 'python',
      packageManager: hasVenv ? 'venv' : 'pip',
      testCommand: hasVenv ? venvPytest : 'pytest',
      fallbackCommand: 'python -m unittest',
      rootDir: dir,
      markers: markersFound
    };
  }

  // 6. Web / Node.js check (Lockfile Precedence: Bun > pnpm > Yarn > npm)
  if (exists('package.json') || exists('bun.lockb') || exists('bun.lock') || exists('pnpm-lock.yaml') || exists('yarn.lock') || exists('package-lock.json')) {
    if (exists('bun.lockb') || exists('bun.lock')) {
      markersFound.push(exists('bun.lockb') ? 'bun.lockb' : 'bun.lock');
      return {
        ecosystem: 'node',
        packageManager: 'bun',
        testCommand: 'bun test',
        fallbackCommand: 'bun run test',
        rootDir: dir,
        markers: markersFound
      };
    }

    if (exists('pnpm-lock.yaml')) {
      markersFound.push('pnpm-lock.yaml');
      return {
        ecosystem: 'node',
        packageManager: 'pnpm',
        testCommand: 'pnpm test',
        fallbackCommand: 'npx vitest run',
        rootDir: dir,
        markers: markersFound
      };
    }

    if (exists('yarn.lock')) {
      markersFound.push('yarn.lock');
      return {
        ecosystem: 'node',
        packageManager: 'yarn',
        testCommand: 'yarn test',
        fallbackCommand: 'yarn run test',
        rootDir: dir,
        markers: markersFound
      };
    }

    if (exists('package-lock.json')) markersFound.push('package-lock.json');
    if (exists('package.json')) markersFound.push('package.json');

    return {
      ecosystem: 'node',
      packageManager: 'npm',
      testCommand: 'npm test',
      fallbackCommand: 'node --test',
      rootDir: dir,
      markers: markersFound
    };
  }

  // 7. C / C++ (Makefile, CMakeLists.txt) check
  if (exists('Makefile') || exists('CMakeLists.txt')) {
    if (exists('Makefile')) markersFound.push('Makefile');
    if (exists('CMakeLists.txt')) markersFound.push('CMakeLists.txt');
    return {
      ecosystem: 'c',
      packageManager: 'make',
      testCommand: 'make test',
      fallbackCommand: 'make check',
      rootDir: dir,
      markers: markersFound
    };
  }

  // Unknown fallback
  return {
    ecosystem: 'unknown',
    packageManager: 'none',
    testCommand: 'npm test',
    fallbackCommand: 'node --test',
    rootDir: dir,
    markers: []
  };
}
