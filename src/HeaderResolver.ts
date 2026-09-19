/**
 * HeaderResolver Service.
 * Implements "The Header Map Law": Dynamic header mapping with alias resolution.
 * ZERO hardcoded column array indices.
 */
const HeaderResolver: IHeaderResolver = Object.freeze({
  normalize(header: unknown): string {
    if (header === null || header === undefined) return '';
    return String(header)
      .trim()
      .toLowerCase()
      .replace(/[\s\-_]+/g, '_')
      .replace(/[^a-z0-9_]/g, '');
  },

  create(headerRow: readonly unknown[]): IHeaderResolverInstance {
    if (!Array.isArray(headerRow) || headerRow.length === 0) {
      throw new Error('[HeaderResolver] Header row must be a non-empty array.');
    }

    const map = new Map<string, number>();
    const rawHeaders: string[] = [];

    for (let i = 0; i < headerRow.length; i++) {
      const raw = headerRow[i];
      rawHeaders.push(String(raw));
      const normalized = HeaderResolver.normalize(raw);
      if (normalized && !map.has(normalized)) {
        map.set(normalized, i);
      }
    }

    const instance: IHeaderResolverInstance = Object.freeze({
      rawHeaders: Object.freeze([...rawHeaders]),

      getIndex(aliases: string | readonly string[]): number {
        const aliasList: readonly string[] = Array.isArray(aliases) ? aliases : [aliases];
        for (const alias of aliasList) {
          const norm = HeaderResolver.normalize(alias);
          const idx = map.get(norm);
          if (idx !== undefined) {
            return idx;
          }
        }
        throw new Error(
          `[HeaderResolver] Missing required column matching aliases: [${aliasList.join(', ')}]. Available headers: [${rawHeaders.join(', ')}]`
        );
      },

      resolveIndices<T extends Record<string, readonly string[]>>(
        fieldDefinitions: T
      ): { readonly [K in keyof T]: number } {
        const resolved = {} as { [K in keyof T]: number };
        const missing: string[] = [];

        for (const [key, aliases] of Object.entries(fieldDefinitions)) {
          try {
            resolved[key as keyof T] = this.getIndex(aliases);
          } catch {
            missing.push(key);
          }
        }

        if (missing.length > 0) {
          throw new Error(
            `[HeaderResolver] Failed to resolve required fields: [${missing.join(', ')}]. Available headers: [${rawHeaders.join(', ')}]`
          );
        }

        return Object.freeze(resolved);
      }
    });

    return instance;
  }
});
