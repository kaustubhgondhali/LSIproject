package com.lordsai.lsi.entity.enums;

/**
 * Active application roles: ADMIN (Main Admin), STUDENT and AUTOMATION_ADMIN (the academy
 * office panel for students / fees / receipts / attendance under /api/automation/**).
 * TEACHER is retained solely so historical rows/audit entries still deserialize; it can no
 * longer log in, hold a session or reach any API (see AuthService / JwtAuthenticationFilter).
 * "Teachers" as a public Success Story category is unrelated to this enum.
 */
public enum Role {
    ADMIN, STUDENT, AUTOMATION_ADMIN,
    @Deprecated TEACHER;

    public boolean isUsable() {
        return this == ADMIN || this == STUDENT || this == AUTOMATION_ADMIN;
    }
}
