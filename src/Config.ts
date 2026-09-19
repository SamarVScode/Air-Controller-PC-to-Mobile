/**
 * Global Configuration Object for Daily Task Alert System.
 * Encapsulated as a frozen singleton according to Obsidian Rule 1.
 */
const DEFAULT_SPREADSHEET_ID = '1c9QrKJ-EPHUYi-QYgd1zuUY3bcTo4uHW5K7qsRbqeCs';
const DEFAULT_BOT_TOKEN = '8599162082:AAHKXAP96RF4QRDbkph1-ZhP7LQa711eeFg';

const Config: AppConfig = Object.freeze({
  DEFAULT_SPREADSHEET_ID: DEFAULT_SPREADSHEET_ID,
  DEFAULT_BOT_TOKEN: DEFAULT_BOT_TOKEN,

  TELEGRAM: Object.freeze({
    CHAT_ID: '-1003779595579',
    MESSAGE_THREAD_ID: 9,
    MAX_MESSAGE_LENGTH: 3800,
    MAX_RETRIES: 3,
    INITIAL_BACKOFF_MS: 1000,
    INTER_MESSAGE_DELAY_MS: 350,
    PARSE_MODE: 'HTML'
  }),

  DATASET: Object.freeze({
    TARGET_HUB: 'MRZ',
    TAB_REGEX: /^tasks?\b/i,
    REQUIRED_FIELDS: Object.freeze({
      TRACKING_NUMBER: Object.freeze(['final_tracking_number', 'tracking_number', 'tracking id', 'final_tracking_id', 'tracking']),
      L5_NAME: Object.freeze(['l5_name', 'l5', 'l5 name', 'l5_user']),
      LEG: Object.freeze(['attribute', 'leg', 'task_leg', 'attributes']),
      PROMISE_DATE: Object.freeze(['promise_date', 'promise date', 'promised_date', 'promise']),
      CREATED_DATE: Object.freeze(['created_date', 'created date', 'creation_date', 'created']),
      DC: Object.freeze(['dc', 'hub', 'delivery_center', 'dc_name'])
    })
  }),

  PROPERTIES: Object.freeze({
    BOT_TOKEN_KEY: 'TELEGRAM_BOT_TOKEN',
    SPREADSHEET_ID_KEY: 'SPREADSHEET_ID',
    LAST_PROCESSED_DATE_KEY: 'LAST_PROCESSED_DATE',
    LAST_FINGERPRINT_KEY: 'LAST_SENT_FINGERPRINT',
    LAST_TIMESTAMP_KEY: 'LAST_SENT_TIMESTAMP',
    LAST_COUNT_KEY: 'LAST_SENT_COUNT'
  }),

  TRIGGER: Object.freeze({
    HANDLER_FUNCTION: 'runDailyTaskAlert',
    START_HOUR: 7, // 7:00 AM IST
    POLL_INTERVAL_MINUTES: 10,
    TIMEZONE: 'Asia/Kolkata'
  }),

  getBotToken(this: AppConfig): string {
    const props = PropertiesService.getScriptProperties();
    return props.getProperty(this.PROPERTIES.BOT_TOKEN_KEY) || DEFAULT_BOT_TOKEN;
  },

  getSpreadsheetId(this: AppConfig): string | null {
    const props = PropertiesService.getScriptProperties();
    return props.getProperty(this.PROPERTIES.SPREADSHEET_ID_KEY) || DEFAULT_SPREADSHEET_ID;
  },

  setupInitialConfig(this: AppConfig, botToken: string, spreadsheetId?: string | null): void {
    if (!botToken || typeof botToken !== 'string') {
      throw new Error('[Config] Valid botToken string is required.');
    }
    const props = PropertiesService.getScriptProperties();
    props.setProperty(this.PROPERTIES.BOT_TOKEN_KEY, botToken.trim());
    if (spreadsheetId && typeof spreadsheetId === 'string' && spreadsheetId.trim()) {
      props.setProperty(this.PROPERTIES.SPREADSHEET_ID_KEY, spreadsheetId.trim());
    }
    Logger.log('[Config] Initial configuration saved successfully to ScriptProperties.');
  }
});
