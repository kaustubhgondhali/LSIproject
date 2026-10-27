package com.lordsai.lsi.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SiteCode {
    ACADEMY, MUTUAL_FUND, SHARE_MARKET;

    @JsonCreator
    public static SiteCode fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace('-', '_');
        if ("SHARE_MARKET".equals(normalized) || "SHAREMARKET".equals(normalized)) {
            return SHARE_MARKET;
        }
        if ("MUTUAL_FUND".equals(normalized) || "MUTUALFUND".equals(normalized) || "MF".equals(normalized)) {
            return MUTUAL_FUND;
        }
        if ("ACADEMY".equals(normalized)) {
            return ACADEMY;
        }
        return SiteCode.valueOf(normalized);
    }
}
