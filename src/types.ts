/**
 * Domain Models and Interfaces for Daily Task Alert System.
 * Note: Under Google Apps Script and module "None", interfaces and types are globally declared.
 */

interface TaskRecord {
  trackingNumber: string;
  l5Name: string;
  leg: string;
  promiseDate: string;
  createdDate: string;
  rawRowNumber?: number;
}

interface ResolvedIndices {
  trackingNumber: number;
  l5Name: number;
  leg: number;
  promiseDate: number;
  createdDate: number;
  dc: number;
}

interface AppResult {
  success: boolean;
  count: number;
  status: string;
  fingerprint?: string;
  messageCount: number;
}

type ExecutionResult = AppResult;

interface TriggerInfo {
  id: string;
  handler: string;
  eventType: string;
  triggerSource: string;
}

interface TelegramResponse {
  ok: boolean;
  result?: unknown;
  description?: string;
  error_code?: number;
  parameters?: {
    retry_after?: number;
    migrate_to_chat_id?: number;
  };
}

interface TelegramConfig {
  readonly CHAT_ID: string;
  readonly MESSAGE_THREAD_ID: number;
  readonly MAX_MESSAGE_LENGTH: number;
  readonly MAX_RETRIES: number;
  readonly INITIAL_BACKOFF_MS: number;
  readonly INTER_MESSAGE_DELAY_MS: number;
  readonly PARSE_MODE: string;
}

interface DatasetFieldsConfig {
  readonly TRACKING_NUMBER: readonly string[];
  readonly L5_NAME: readonly string[];
  readonly LEG: readonly string[];
  readonly PROMISE_DATE: readonly string[];
  readonly CREATED_DATE: readonly string[];
  readonly DC: readonly string[];
}

interface DatasetConfig {
  readonly TARGET_HUB: string;
  readonly TAB_REGEX: RegExp;
  readonly REQUIRED_FIELDS: DatasetFieldsConfig;
}

interface PropertiesConfig {
  readonly BOT_TOKEN_KEY: string;
  readonly SPREADSHEET_ID_KEY: string;
  readonly LAST_PROCESSED_DATE_KEY: string;
  readonly LAST_FINGERPRINT_KEY: string;
  readonly LAST_TIMESTAMP_KEY: string;
  readonly LAST_COUNT_KEY: string;
}

interface TriggerConfig {
  readonly HANDLER_FUNCTION: string;
  readonly START_HOUR: number;
  readonly POLL_INTERVAL_MINUTES: number;
  readonly TIMEZONE: string;
  readonly SCHEDULE_HOUR?: number;
}

interface AppConfig {
  readonly TELEGRAM: TelegramConfig;
  readonly DATASET: DatasetConfig;
  readonly PROPERTIES: PropertiesConfig;
  readonly TRIGGER: TriggerConfig;
  readonly DEFAULT_SPREADSHEET_ID?: string;
  readonly DEFAULT_BOT_TOKEN?: string;
  getBotToken(this: AppConfig): string;
  getSpreadsheetId(this: AppConfig): string | null;
  setupInitialConfig(this: AppConfig, botToken: string, spreadsheetId?: string | null): void;
}

interface IHeaderResolverInstance {
  readonly rawHeaders: readonly string[];
  getIndex(aliases: string | readonly string[]): number;
  resolveIndices<T extends Record<string, readonly string[]>>(
    fieldDefinitions: T
  ): { readonly [K in keyof T]: number };
}

interface IHeaderResolver {
  normalize(header: unknown): string;
  create(headerRow: readonly unknown[]): IHeaderResolverInstance;
}

interface IDateUtils {
  pad2(n: number): string;
  formatFast(value: unknown): string;
}

interface ITelegramService {
  escapeHtml(str: unknown): string;
  formatTaskCard(task: TaskRecord): string;
  buildMessages(tasks: readonly TaskRecord[]): string[];
  sendMessage(
    botToken: string,
    chatId: string | number,
    threadId: number | undefined,
    text: string
  ): TelegramResponse;
  sendBatch(
    messages: readonly string[],
    botToken: string,
    chatId: string | number,
    threadId: number | undefined
  ): number;
}

interface ITaskFilterService {
  getSpreadsheet(): GoogleAppsScript.Spreadsheet.Spreadsheet;
  resolveTaskSheet(ss: GoogleAppsScript.Spreadsheet.Spreadsheet): GoogleAppsScript.Spreadsheet.Sheet;
  getSheetData(): any[][];
  getFilteredTasks(customData?: any[][]): TaskRecord[];
  computeFingerprint(tasks: readonly TaskRecord[]): string;
  compute3ColumnFingerprint(data: any[][]): string;
  getLastFingerprint(): string | null;
  getLastProcessedDate(): string | null;
  saveProcessedState(dateStr: string, fingerprint: string, count: number): void;
  saveFingerprint(fingerprint: string, count: number): void;
  clearState(): void;
}

interface ITriggerManager {
  cleanTriggers(handlerFunctionName?: string): number;
  setup10MinuteTrigger(handlerName?: string): GoogleAppsScript.Script.Trigger;
  createDailyTrigger(
    handlerName?: string,
    hour?: number,
    timezone?: string
  ): GoogleAppsScript.Script.Trigger;
  listTriggers(): TriggerInfo[];
}

interface AppStatus {
  botTokenConfigured: boolean;
  spreadsheetIdConfigured: string | null;
  lastFingerprint: string | null;
  lastProcessedDate: string | null;
  triggers: TriggerInfo[];
}
