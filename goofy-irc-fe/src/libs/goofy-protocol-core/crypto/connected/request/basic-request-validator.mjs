export class BasicRequestValidator {
    static DEF_MAX_VALIDITY_GRACE_PERIOD = 5; // seconds
    static DEF_MAX_VALIDITY_FUTURE = 60 * 60; // 1 hour

    static DEF_MAX_USED_IDS = 10_000;

    constructor() {
        this.usedIds = new Set(); // Set<bigint|number>
    }

    isUniqueIdValid(uniqueId) {
        return !this.usedIds.has(uniqueId);
    }

    invalidateUniqueId(uniqueId) {
        if (this.usedIds.has(uniqueId))
            return false;
        this.usedIds.add(uniqueId);
        return true;
    }

    isValidUntilValid(validUntil) {
        const nowMs = Date.now();

        // accept Date or numeric epoch millis
        const validUntilMs = validUntil instanceof Date ? validUntil.getTime() : validUntil;

        const min = nowMs - BasicRequestValidator.DEF_MAX_VALIDITY_GRACE_PERIOD * 1000;
        const max = nowMs + BasicRequestValidator.DEF_MAX_VALIDITY_FUTURE * 1000;

        return validUntilMs > min && validUntilMs < max;
    }
}