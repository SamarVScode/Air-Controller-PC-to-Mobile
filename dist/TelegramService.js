"use strict";
/**
 * TelegramService.
 * Handles card formatting, strict <3800 char batch chunking, HTML entity escaping,
 * and robust UrlFetchApp transmission with exponential backoff & rate-limiting protection.
 */
const TelegramService = Object.freeze({
    escapeHtml(str) {
        if (str === null || str === undefined)
            return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    },
    formatTaskCard(task) {
        const tracking = this.escapeHtml(task.trackingNumber || 'N/A');
        const l5 = this.escapeHtml(task.l5Name || 'N/A');
        const leg = this.escapeHtml(task.leg || 'N/A');
        const promise = this.escapeHtml(task.promiseDate || 'N/A');
        const created = this.escapeHtml(task.createdDate || 'N/A');
        return [
            `📦 <b>Tracking Ids:</b> <code>${tracking}</code>`,
            `👤 <b>L5_Name:</b> ${l5}`,
            `🏷 <b>Leg:</b> ${leg}`,
            `📅 <b>Promise date:</b> ${promise}`,
            `⏰ <b>Created date:</b> ${created}`
        ].join('\n');
    },
    buildMessages(tasks) {
        if (!Array.isArray(tasks) || tasks.length === 0) {
            return [];
        }
        const totalCount = tasks.length;
        const cards = tasks.map(t => this.formatTaskCard(t));
        const cardDivider = '\n────────────────────\n';
        const maxLength = Config.TELEGRAM.MAX_MESSAGE_LENGTH;
        const rawChunks = [];
        let currentCards = [];
        let currentLength = 0;
        for (const card of cards) {
            const cardLengthWithDivider = card.length + cardDivider.length;
            if (currentCards.length > 0 && (currentLength + cardLengthWithDivider + 250) > maxLength) {
                rawChunks.push(currentCards);
                currentCards = [card];
                currentLength = card.length;
            }
            else {
                currentCards.push(card);
                currentLength += cardLengthWithDivider;
            }
        }
        if (currentCards.length > 0) {
            rawChunks.push(currentCards);
        }
        const totalBatches = rawChunks.length;
        return rawChunks.map((chunkCards, idx) => {
            const batchNum = idx + 1;
            let header = `🔔 <b>Today's Task Count -> ${totalCount}</b>\n`;
            header += `🏢 <b>DC:</b> ${Config.DATASET.TARGET_HUB}`;
            if (totalBatches > 1) {
                header += ` | <b>Part ${batchNum} of ${totalBatches}</b>`;
            }
            header += '\n━━━━━━━━━━━━━━━━━━━━\n';
            const body = chunkCards.join(cardDivider);
            return header + body;
        });
    },
    sendMessage(botToken, chatId, threadId, text) {
        const url = `https://api.telegram.org/bot${botToken}/sendMessage`;
        const payload = {
            chat_id: String(chatId),
            message_thread_id: threadId,
            text: text,
            parse_mode: Config.TELEGRAM.PARSE_MODE,
            disable_web_page_preview: true
        };
        const options = {
            method: 'post',
            contentType: 'application/json',
            payload: JSON.stringify(payload),
            muteHttpExceptions: true
        };
        let attempts = 0;
        let backoff = Config.TELEGRAM.INITIAL_BACKOFF_MS;
        while (attempts < Config.TELEGRAM.MAX_RETRIES) {
            attempts++;
            try {
                const response = UrlFetchApp.fetch(url, options);
                const code = response.getResponseCode();
                const responseText = response.getContentText();
                if (code === 200) {
                    return JSON.parse(responseText);
                }
                if (code === 429) {
                    let retryAfterSec = 3;
                    try {
                        const parsed = JSON.parse(responseText);
                        if (parsed.parameters && parsed.parameters.retry_after) {
                            retryAfterSec = parsed.parameters.retry_after;
                        }
                    }
                    catch { }
                    Logger.log(`[TelegramService] Rate limited (429). Retrying in ${retryAfterSec}s...`);
                    Utilities.sleep(retryAfterSec * 1000);
                    continue;
                }
                if (code >= 500 && code < 600) {
                    Logger.log(`[TelegramService] Server error (${code}). Retrying in ${backoff}ms...`);
                    Utilities.sleep(backoff);
                    backoff *= 2;
                    continue;
                }
                throw new Error(`[TelegramService] API error (HTTP ${code}): ${responseText}`);
            }
            catch (err) {
                if (attempts >= Config.TELEGRAM.MAX_RETRIES) {
                    const message = err instanceof Error ? err.message : String(err);
                    throw new Error(`[TelegramService] Failed after ${attempts} attempts: ${message}`);
                }
                Utilities.sleep(backoff);
                backoff *= 2;
            }
        }
        throw new Error('[TelegramService] Max retries exhausted.');
    },
    sendBatch(messages, botToken, chatId, threadId) {
        if (!botToken) {
            throw new Error('[TelegramService] Bot token is missing. Configure via setupInitialConfig(botToken).');
        }
        let successCount = 0;
        for (let i = 0; i < messages.length; i++) {
            this.sendMessage(botToken, chatId, threadId, messages[i]);
            successCount++;
            if (i < messages.length - 1) {
                Utilities.sleep(Config.TELEGRAM.INTER_MESSAGE_DELAY_MS);
            }
        }
        return successCount;
    }
});
