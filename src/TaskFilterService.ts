/**
 * TaskFilterService.
 * Handles spreadsheet resolution, regex sheet name matching (/^tasks?\b/i),
 * zero-hardcoded column extraction, Hub MRZ filtering, and 3-column SHA-256 fingerprinting.
 */
const TaskFilterService: ITaskFilterService = Object.freeze({
  getSpreadsheet(): GoogleAppsScript.Spreadsheet.Spreadsheet {
    let ss: GoogleAppsScript.Spreadsheet.Spreadsheet | null = null;
    try {
      ss = SpreadsheetApp.getActiveSpreadsheet();
    } catch {}

    if (!ss) {
      const configuredId = Config.getSpreadsheetId();
      if (configuredId) {
        ss = SpreadsheetApp.openById(configuredId);
      }
    }

    if (!ss) {
      throw new Error(
        '[TaskFilterService] No active Spreadsheet found and no SPREADSHEET_ID configured in ScriptProperties. Bind script to sheet or call setupInitialConfig(token, spreadsheetId).'
      );
    }
    return ss;
  },

  resolveTaskSheet(ss: GoogleAppsScript.Spreadsheet.Spreadsheet): GoogleAppsScript.Spreadsheet.Sheet {
    const sheets = ss.getSheets();
    for (const sheet of sheets) {
      const name = sheet.getName().trim();
      if (Config.DATASET.TAB_REGEX.test(name)) {
        return sheet;
      }
    }

    const availableNames = sheets.map(s => `"${s.getName()}"`).join(', ');
    throw new Error(
      `[TaskFilterService] Could not find sheet matching pattern ${Config.DATASET.TAB_REGEX}. Available sheets: [${availableNames}]`
    );
  },

  getSheetData(): any[][] {
    const ss = this.getSpreadsheet();
    const sheet = this.resolveTaskSheet(ss);
    return sheet.getDataRange().getValues();
  },

  getFilteredTasks(customData?: any[][]): TaskRecord[] {
    let data = customData;
    if (!data) {
      data = this.getSheetData();
    }

    if (!data || data.length < 2) {
      Logger.log('[TaskFilterService] Sheet is empty or contains only header row.');
      return [];
    }

    const headerRow = data[0];
    const resolver = HeaderResolver.create(headerRow);
    const indices: ResolvedIndices = resolver.resolveIndices({
      trackingNumber: Config.DATASET.REQUIRED_FIELDS.TRACKING_NUMBER,
      l5Name: Config.DATASET.REQUIRED_FIELDS.L5_NAME,
      leg: Config.DATASET.REQUIRED_FIELDS.LEG,
      promiseDate: Config.DATASET.REQUIRED_FIELDS.PROMISE_DATE,
      createdDate: Config.DATASET.REQUIRED_FIELDS.CREATED_DATE,
      dc: Config.DATASET.REQUIRED_FIELDS.DC
    });

    const targetHub = Config.DATASET.TARGET_HUB.trim().toUpperCase();
    const filteredTasks: TaskRecord[] = [];

    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      const rawDc = row[indices.dc];
      if (rawDc === null || rawDc === undefined) continue;

      const dcValue = String(rawDc).trim().toUpperCase();
      if (dcValue === targetHub) {
        const trackingNumber = String(row[indices.trackingNumber] || '').trim();
        const l5Name = String(row[indices.l5Name] || '').trim();
        const leg = String(row[indices.leg] || '').trim();
        const promiseDate = DateUtils.formatFast(row[indices.promiseDate]);
        const createdDate = DateUtils.formatFast(row[indices.createdDate]);

        if (trackingNumber || l5Name) {
          filteredTasks.push({
            trackingNumber: trackingNumber || 'N/A',
            l5Name: l5Name || 'N/A',
            leg: leg || 'N/A',
            promiseDate: promiseDate,
            createdDate: createdDate,
            rawRowNumber: i + 1
          });
        }
      }
    }

    Logger.log(`[TaskFilterService] Filtered ${filteredTasks.length} tasks for DC '${targetHub}' from total ${data.length - 1} rows.`);
    return filteredTasks;
  },

  computeFingerprint(tasks: readonly TaskRecord[]): string {
    if (!Array.isArray(tasks) || tasks.length === 0) {
      return 'EMPTY_SET';
    }

    const serialized = tasks
      .map(t => `${t.trackingNumber}|${t.l5Name}|${t.leg}|${t.promiseDate}|${t.createdDate}`)
      .join('\n');

    const digestBytes = Utilities.computeDigest(
      Utilities.DigestAlgorithm.SHA_256,
      serialized,
      Utilities.Charset.UTF_8
    );

    let hex = '';
    for (let i = 0; i < digestBytes.length; i++) {
      const byteVal = (digestBytes[i] + 256) % 256;
      const byteHex = byteVal.toString(16);
      hex += byteHex.length === 1 ? '0' + byteHex : byteHex;
    }
    return hex;
  },

  compute3ColumnFingerprint(data: any[][]): string {
    if (!Array.isArray(data) || data.length < 2) {
      return 'EMPTY_DATA';
    }

    const rows: string[] = [];
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      if (!row || !Array.isArray(row)) continue;

      const c0 = row[0] !== undefined && row[0] !== null ? String(row[0]).trim() : '';
      const c1 = row[1] !== undefined && row[1] !== null ? String(row[1]).trim() : '';
      const c2 = row[2] !== undefined && row[2] !== null ? String(row[2]).trim() : '';

      if (c0 || c1 || c2) {
        rows.push(`${c0}|${c1}|${c2}`);
      }
    }

    if (rows.length === 0) {
      return 'EMPTY_DATA';
    }

    const serialized = rows.join('\n');
    const digestBytes = Utilities.computeDigest(
      Utilities.DigestAlgorithm.SHA_256,
      serialized,
      Utilities.Charset.UTF_8
    );

    let hex = '';
    for (let i = 0; i < digestBytes.length; i++) {
      const byteVal = (digestBytes[i] + 256) % 256;
      const byteHex = byteVal.toString(16);
      hex += byteHex.length === 1 ? '0' + byteHex : byteHex;
    }
    return hex;
  },

  getLastFingerprint(): string | null {
    return PropertiesService.getScriptProperties().getProperty(Config.PROPERTIES.LAST_FINGERPRINT_KEY);
  },

  getLastProcessedDate(): string | null {
    return PropertiesService.getScriptProperties().getProperty(Config.PROPERTIES.LAST_PROCESSED_DATE_KEY);
  },

  saveProcessedState(dateStr: string, fingerprint: string, count: number): void {
    const props = PropertiesService.getScriptProperties();
    props.setProperties({
      [Config.PROPERTIES.LAST_PROCESSED_DATE_KEY]: dateStr,
      [Config.PROPERTIES.LAST_FINGERPRINT_KEY]: fingerprint,
      [Config.PROPERTIES.LAST_TIMESTAMP_KEY]: new Date().toISOString(),
      [Config.PROPERTIES.LAST_COUNT_KEY]: String(count)
    });
    Logger.log(`[TaskFilterService] Saved processed state: Date=${dateStr}, Fingerprint=${fingerprint}, Count=${count}`);
  },

  saveFingerprint(fingerprint: string, count: number): void {
    const props = PropertiesService.getScriptProperties();
    props.setProperties({
      [Config.PROPERTIES.LAST_FINGERPRINT_KEY]: fingerprint,
      [Config.PROPERTIES.LAST_TIMESTAMP_KEY]: new Date().toISOString(),
      [Config.PROPERTIES.LAST_COUNT_KEY]: String(count)
    });
    Logger.log(`[TaskFilterService] Stored fingerprint: ${fingerprint} (Count: ${count})`);
  },

  clearState(): void {
    const props = PropertiesService.getScriptProperties();
    props.deleteProperty(Config.PROPERTIES.LAST_PROCESSED_DATE_KEY);
    props.deleteProperty(Config.PROPERTIES.LAST_FINGERPRINT_KEY);
    props.deleteProperty(Config.PROPERTIES.LAST_TIMESTAMP_KEY);
    props.deleteProperty(Config.PROPERTIES.LAST_COUNT_KEY);
    Logger.log('[TaskFilterService] Fingerprint and state properties cleared.');
  }
});
