/**
 * TriggerManager Service.
 * Implements "Trigger Hygiene": Queries existing project triggers, cleanly purges duplicates,
 * and sets up robust 10-minute polling triggers.
 */
const TriggerManager: ITriggerManager = Object.freeze({
  cleanTriggers(handlerFunctionName?: string): number {
    const targetHandler = handlerFunctionName || Config.TRIGGER.HANDLER_FUNCTION;
    const triggers = ScriptApp.getProjectTriggers();
    let deletedCount = 0;

    for (const trigger of triggers) {
      if (trigger.getHandlerFunction() === targetHandler) {
        ScriptApp.deleteTrigger(trigger);
        deletedCount++;
      }
    }

    Logger.log(`[TriggerManager] Cleaned ${deletedCount} existing triggers for handler "${targetHandler}".`);
    return deletedCount;
  },

  setup10MinuteTrigger(handlerName?: string): GoogleAppsScript.Script.Trigger {
    const handler = handlerName || Config.TRIGGER.HANDLER_FUNCTION;
    this.cleanTriggers(handler);

    const newTrigger = ScriptApp.newTrigger(handler)
      .timeBased()
      .everyMinutes(Config.TRIGGER.POLL_INTERVAL_MINUTES || 10)
      .create();

    Logger.log(
      `[TriggerManager] Successfully created 10-minute trigger for "${handler}". Trigger ID: ${newTrigger.getUniqueId()}`
    );

    return newTrigger;
  },

  createDailyTrigger(
    handlerName?: string,
    hour?: number,
    timezone?: string
  ): GoogleAppsScript.Script.Trigger {
    const handler = handlerName || Config.TRIGGER.HANDLER_FUNCTION;
    const triggerHour = hour !== undefined ? hour : Config.TRIGGER.START_HOUR;
    const tz = timezone || Config.TRIGGER.TIMEZONE;

    this.cleanTriggers(handler);

    const newTrigger = ScriptApp.newTrigger(handler)
      .timeBased()
      .everyDays(1)
      .atHour(triggerHour)
      .inTimezone(tz)
      .create();

    Logger.log(
      `[TriggerManager] Successfully created daily trigger for "${handler}" at hour ${triggerHour}:00 (${tz}). Trigger ID: ${newTrigger.getUniqueId()}`
    );

    return newTrigger;
  },

  listTriggers(): TriggerInfo[] {
    const triggers = ScriptApp.getProjectTriggers();
    return triggers.map(t => ({
      id: t.getUniqueId(),
      handler: t.getHandlerFunction(),
      eventType: String(t.getEventType()),
      triggerSource: String(t.getTriggerSource())
    }));
  }
});
