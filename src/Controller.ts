/**
 * Controller Gateway.
 * Top-level global entry points for Google Apps Script execution, triggers, and UI macros.
 * Adheres to Obsidian Rule 1: Flat Scope & Controller Gateways.
 * Implements the 5-Gate 10-Minute Polling Engine.
 */

function runDailyTaskAlert(isManual?: boolean): AppResult {
  const manualRun = isManual === true;
  Logger.log(`[Controller] Starting runDailyTaskAlert (isManual: ${manualRun})...`);

  const now = new Date();
  const tz = Config.TRIGGER.TIMEZONE;
  const todayStr = Utilities.formatDate(now, tz, 'yyyy-MM-dd');
  const hour = parseInt(Utilities.formatDate(now, tz, 'H'), 10);

  // Gate 1 (Hour Window)
  if (!manualRun && hour < Config.TRIGGER.START_HOUR) {
    Logger.log('[Controller] Before 7:00 AM window. Exiting.');
    return { success: true, count: 0, status: 'SKIPPED_BEFORE_HOURS', messageCount: 0 };
  }

  // Gate 2 (Date Lock)
  const lastProcessedDate = TaskFilterService.getLastProcessedDate();
  if (!manualRun && lastProcessedDate === todayStr) {
    Logger.log(`[Controller] Already processed today (${todayStr}). Exiting.`);
    return { success: true, count: 0, status: 'SKIPPED_ALREADY_PROCESSED_TODAY', messageCount: 0 };
  }

  // Read sheet data
  const data = TaskFilterService.getSheetData();

  // Gate 3 (Empty Data Check)
  if (!manualRun && (!data || data.length < 2)) {
    Logger.log('[Controller] Sheet has no rows. Waiting for data paste.');
    return { success: true, count: 0, status: 'SKIPPED_EMPTY', messageCount: 0 };
  }

  if (!data || data.length < 2) {
    Logger.log('[Controller] Sheet has no rows.');
    return { success: true, count: 0, status: 'SKIPPED_EMPTY', messageCount: 0 };
  }

  // Gate 4 (3-Column Fingerprint Change)
  const currentFingerprint = TaskFilterService.compute3ColumnFingerprint(data);
  const lastFingerprint = TaskFilterService.getLastFingerprint();

  if (!manualRun && currentFingerprint === lastFingerprint) {
    Logger.log('[Controller] First 3 columns unchanged (stale data). Waiting for fresh dump.');
    return { success: true, count: 0, status: 'SKIPPED_DATA_UNCHANGED', messageCount: 0 };
  }

  const botToken = Config.getBotToken();
  if (!botToken) {
    const errMsg = '[Controller] TELEGRAM_BOT_TOKEN is not configured! Call setupInitialConfig("YOUR_BOT_TOKEN") first.';
    Logger.log(errMsg);
    throw new Error(errMsg);
  }

  // Gate 5 (Filter tasks for DC == 'MRZ')
  const tasks = TaskFilterService.getFilteredTasks(data);
  const count = tasks.length;

  if (count === 0) {
    Logger.log(`[Controller] Zero tasks matched DC '${Config.DATASET.TARGET_HUB}'. No message sent.`);
    return { success: true, count: 0, status: 'NO_TASKS_FOUND', messageCount: 0 };
  }

  // Build & dispatch Telegram messages
  const messages = TelegramService.buildMessages(tasks);
  Logger.log(`[Controller] Prepared ${messages.length} message batch(es) for ${count} tasks.`);

  const chatId = Config.TELEGRAM.CHAT_ID;
  const threadId = Config.TELEGRAM.MESSAGE_THREAD_ID;
  const sentCount = TelegramService.sendBatch(messages, botToken, chatId, threadId);

  // Save processed state
  TaskFilterService.saveProcessedState(todayStr, currentFingerprint, count);

  Logger.log(`[Controller] Successfully dispatched ${sentCount} batch(es) for ${count} tasks.`);
  return {
    success: true,
    count: count,
    status: 'SENT',
    fingerprint: currentFingerprint,
    messageCount: sentCount
  };
}

function testRun(): AppResult {
  Logger.log('================== [testRun STARTED] ==================');
  try {
    const result = runDailyTaskAlert(true);
    Logger.log('[testRun RESULT]: ' + JSON.stringify(result, null, 2));
    Logger.log('================== [testRun FINISHED] ==================');
    return result;
  } catch (err: unknown) {
    const error = err instanceof Error ? err : new Error(String(err));
    Logger.log('[testRun ERROR]: ' + error.message + '\n' + (error.stack || ''));
    Logger.log('================== [testRun FAILED] ==================');
    throw error;
  }
}

function setup10MinuteTrigger(): string {
  Logger.log('[Controller] Setting up 10-minute polling trigger...');
  const trigger = TriggerManager.setup10MinuteTrigger(Config.TRIGGER.HANDLER_FUNCTION);
  Logger.log('[Controller] 10-minute trigger active: ' + trigger.getUniqueId());
  return trigger.getUniqueId();
}

function setupDailyTrigger(): string {
  return setup10MinuteTrigger();
}

function setupInitialConfig(botToken: string, spreadsheetId?: string | null): void {
  Config.setupInitialConfig(botToken, spreadsheetId);
}

function clearState(): void {
  TaskFilterService.clearState();
}

function checkStatus(): AppStatus {
  const status: AppStatus = {
    botTokenConfigured: Boolean(Config.getBotToken()),
    spreadsheetIdConfigured: Config.getSpreadsheetId(),
    lastFingerprint: TaskFilterService.getLastFingerprint(),
    lastProcessedDate: TaskFilterService.getLastProcessedDate(),
    triggers: TriggerManager.listTriggers()
  };
  Logger.log('[checkStatus]: ' + JSON.stringify(status, null, 2));
  return status;
}
