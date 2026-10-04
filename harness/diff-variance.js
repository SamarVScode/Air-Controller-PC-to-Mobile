/**
 * Diff Variance Calculator (harness/diff-variance.js)
 * Normalized Levenshtein & Structural Diff Variance Calculator.
 */

/**
 * Computes standard Levenshtein distance between two strings with O(min(N, M)) memory.
 */
export function levenshteinDistance(a = '', b = '') {
  const s1 = String(a || '');
  const s2 = String(b || '');

  if (s1 === s2) return 0;
  if (!s1.length) return s2.length;
  if (!s2.length) return s1.length;

  let prevRow = new Array(s2.length + 1);
  let currRow = new Array(s2.length + 1);

  for (let j = 0; j <= s2.length; j++) {
    prevRow[j] = j;
  }

  for (let i = 0; i < s1.length; i++) {
    currRow[0] = i + 1;
    const char1 = s1[i];

    for (let j = 0; j < s2.length; j++) {
      const char2 = s2[j];
      const cost = char1 === char2 ? 0 : 1;
      currRow[j + 1] = Math.min(
        currRow[j] + 1,       // insertion
        prevRow[j + 1] + 1,   // deletion
        prevRow[j] + cost     // substitution
      );
    }

    const temp = prevRow;
    prevRow = currRow;
    currRow = temp;
  }

  return prevRow[s2.length];
}

/**
 * Normalizes text by removing whitespace, comments, and empty lines to assess structural change.
 */
export function normalizeStructure(text = '') {
  return String(text || '')
    .replace(/\/\*[\s\S]*?\*\//g, '') // strip block comments
    .replace(/(?<![:/])\/\/.*/g, '')     // strip line comments preserving URLs
    .replace(/(^|\s)#(?![0-9a-fA-F]{3,8}\b|include|define|pragma|ifdef|ifndef|endif\b).*/gm, '') // strip comments preserving preprocessor & hex colors
    .replace(/\s+/g, ' ')             // collapse whitespace
    .trim();
}

/**
 * Computes normalized diff variance between 0.0 and 1.0.
 * 0.0 means identical.
 * 1.0 means completely different.
 */
export function computeDiffVariance(prevText = '', currText = '') {
  const p = String(prevText || '');
  const c = String(currText || '');

  if (p === c) return 0.0;

  // Run normalizeStructure on prevText and currText before checking large length
  const pStruct = normalizeStructure(p);
  const cStruct = normalizeStructure(c);
  if (pStruct === cStruct) {
    // Only comments or whitespace changed -> purely cosmetic
    return 0.05;
  }

  const maxLen = Math.max(p.length, c.length);
  if (maxLen === 0) return 0.0;

  // Fast path for very large strings: compute line frequency map (multiset) or chunk frequency
  if (maxLen > 2500) {
    const prevLines = p.split('\n').map(l => l.trim()).filter(Boolean);
    const currLines = c.split('\n').map(l => l.trim()).filter(Boolean);
    const totalLines = Math.max(prevLines.length, currLines.length);

    if (totalLines > 5) {
      const prevFreq = new Map();
      for (const line of prevLines) {
        prevFreq.set(line, (prevFreq.get(line) || 0) + 1);
      }

      let common = 0;
      for (const line of currLines) {
        const count = prevFreq.get(line) || 0;
        if (count > 0) {
          common++;
          prevFreq.set(line, count - 1);
        }
      }

      const similarity = common / Math.max(totalLines, 1);
      return Math.round((1.0 - similarity) * 100) / 100;
    }

    // Single-line or few-line large files (e.g. minified bundles, data JSON):
    // Chunk into 100-char segments to compute accurate structural variance
    const chunkString = (str, size = 100) => {
      const chunks = [];
      for (let i = 0; i < str.length; i += size) {
        chunks.push(str.slice(i, i + size));
      }
      return chunks;
    };
    const pChunks = chunkString(p);
    const cChunks = chunkString(c);
    const pFreq = new Map();
    for (const ch of pChunks) pFreq.set(ch, (pFreq.get(ch) || 0) + 1);
    let commonChunks = 0;
    for (const ch of cChunks) {
      const count = pFreq.get(ch) || 0;
      if (count > 0) {
        commonChunks++;
        pFreq.set(ch, count - 1);
      }
    }
    const totalChunks = Math.max(pChunks.length, cChunks.length, 1);
    const chunkSimilarity = commonChunks / totalChunks;
    return Math.round((1.0 - chunkSimilarity) * 100) / 100;
  }

  const dist = levenshteinDistance(p, c);
  const rawVariance = dist / maxLen;

  return Math.round(rawVariance * 100) / 100;
}

/**
 * Evaluates whether an edit is substantive (>= 0.15) or trivial (< 0.15).
 */
export function isSubstantiveChange(prevText = '', currText = '', threshold = 0.15) {
  const variance = computeDiffVariance(prevText, currText);
  return variance >= threshold;
}

/**
 * Classifies edit variance for the cycle breaker.
 * Mandated threshold:
 * - Variance < 15%: trivial churn -> allowed repeats: 3
 * - Variance >= 15%: novel exploration -> allowed repeats: 5
 */
export function classifyEditVariance(prevText = '', currText = '') {
  const variance = computeDiffVariance(prevText, currText);
  const isSubstantive = variance >= 0.15;

  return {
    variance,
    isSubstantive,
    allowedRepeats: isSubstantive ? 5 : 3,
    category: isSubstantive ? 'novel_exploration' : 'trivial_churn'
  };
}
