package com.lordsai.lsi.entity.enums;

/**
 * Active application roles are ADMIN and STUDENT only.
 * TEACHER is retained solely so historical rows/audit entries still deserialize; it can no
 * longer log in, hold a session or reach any API (see AuthService / JwtAuthenticationFilter).
 * "Teachers" as a public Success Story category is unrelated to this enum.
 */
public enum Role {
    ADMIN, STUDENT,
    @Deprecated TEACHER;

    public boolean isUsable() {
        return this == ADMIN || this == STUDENT;
    }
}
