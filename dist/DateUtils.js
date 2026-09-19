"use strict";
/**
 * DateUtils Service.
 * Implements "Fast Date Handling": ZERO Utilities.formatDate() inside row loops.
 */
const DateUtils = Object.freeze({
    pad2(n) {
        return n < 10 ? '0' + n : String(n);
    },
    formatFast(value) {
        if (value === null || value === undefined || value === '') {
            return 'N/A';
        }
        if (value instanceof Date) {
            if (isNaN(value.getTime()))
                return 'Invalid Date';
            const y = value.getFullYear();
            const m = DateUtils.pad2(value.getMonth() + 1);
            const d = DateUtils.pad2(value.getDate());
            const h = DateUtils.pad2(value.getHours());
            const min = DateUtils.pad2(value.getMinutes());
            const s = DateUtils.pad2(value.getSeconds());
            return `${y}-${m}-${d} ${h}:${min}:${s}`;
        }
        if (typeof value === 'number' && !isNaN(value) && value > 1000) {
            const utcMs = Math.round((value - 25569) * 86400000);
            const dObj = new Date(utcMs);
            if (!isNaN(dObj.getTime())) {
                return DateUtils.formatFast(dObj);
            }
        }
        const str = String(value).trim();
        if (!str)
            return 'N/A';
        if (str.length >= 10 && (str.includes('-') || str.includes('/') || str.includes('T'))) {
            const parsed = new Date(str);
            if (!isNaN(parsed.getTime())) {
                return DateUtils.formatFast(parsed);
            }
        }
        return str;
    }
});
