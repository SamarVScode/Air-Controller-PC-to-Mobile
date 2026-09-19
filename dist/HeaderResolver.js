"use strict";
/**
 * HeaderResolver Service.
 * Implements "The Header Map Law": Dynamic header mapping with alias resolution.
 * ZERO hardcoded column array indices.
 */
const HeaderResolver = Object.freeze({
    normalize(header) {
        if (header === null || header === undefined)
            return '';
        return String(header)
            .trim()
            .toLowerCase()
            .replace(/[\s\-_]+/g, '_')
            .replace(/[^a-z0-9_]/g, '');
    },
    create(headerRow) {
        if (!Array.isArray(headerRow) || headerRow.length === 0) {
            throw new Error('[HeaderResolver] Header row must be a non-empty array.');
        }
        const map = new Map();
        const rawHeaders = [];
        for (let i = 0; i < headerRow.length; i++) {
            const raw = headerRow[i];
            rawHeaders.push(String(raw));
            const normalized = HeaderResolver.normalize(raw);
            if (normalized && !map.has(normalized)) {
                map.set(normalized, i);
            }
        }
        const instance = Object.freeze({
            rawHeaders: Object.freeze([...rawHeaders]),
            getIndex(aliases) {
                const aliasList = Array.isArray(aliases) ? aliases : [aliases];
                for (const alias of aliasList) {
                    const norm = HeaderResolver.normalize(alias);
                    const idx = map.get(norm);
                    if (idx !== undefined) {
                        return idx;
                    }
                }
                throw new Error(`[HeaderResolver] Missing required column matching aliases: [${aliasList.join(', ')}]. Available headers: [${rawHeaders.join(', ')}]`);
            },
            resolveIndices(fieldDefinitions) {
                const resolved = {};
                const missing = [];
                for (const [key, aliases] of Object.entries(fieldDefinitions)) {
                    try {
                        resolved[key] = this.getIndex(aliases);
                    }
                    catch {
                        missing.push(key);
                    }
                }
                if (missing.length > 0) {
                    throw new Error(`[HeaderResolver] Failed to resolve required fields: [${missing.join(', ')}]. Available headers: [${rawHeaders.join(', ')}]`);
                }
                return Object.freeze(resolved);
            }
        });
        return instance;
    }
});
