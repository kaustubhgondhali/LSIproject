package com.lordsai.lsi.entity.enums;

/** Content-protection events the Student Portal reports for the audit trail. */
public enum ProtectionEventType {
    SCREEN_CAPTURE_ATTEMPT,
    PRINT_ATTEMPT,
    PROTECTED_CONTENT_BLUR,
    DOWNLOAD_ATTEMPT,
    COPY_ATTEMPT,
    DEVTOOLS_SHORTCUT,
    /** Raised server-side when a media request fails authorization; never sent by the browser. */
    UNAUTHORIZED_MEDIA_REQUEST
}
