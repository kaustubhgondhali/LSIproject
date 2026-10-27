package com.lordsai.lsi.automation.entity;

public enum AttendanceStatus {
    PRESENT, ABSENT, LATE, EXCUSED;

    /** LATE counts as attended; EXCUSED is neither present nor a mark against the student. */
    public boolean countsAsPresent() {
        return this == PRESENT || this == LATE;
    }
}
