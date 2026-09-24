/**
 * LORD SAI ACADEMY — STUDENT PORTAL CONTENT PROTECTION (js/student-protect.js)
 *
 * Loaded exclusively by authenticated Student Portal pages (student-dashboard.html, learn.html)
 * after js/auth.js. Activates only for an authenticated STUDENT session.
 *
 * Browser-Level Screenshot & Screen-Recording Mitigation System:
 *   - Instantaneous FULL-PAGE BLACKOUT overlay (#capture-protection-overlay) whenever the page
 *     loses visibility, is backgrounded, minimized, or blurred during potential screen capture.
 *   - Instant concealment of course content (video, handouts, ebooks, UI) under pure opaque black.
 *   - 100% state preservation: video playback time, playing state, and ebook reading position
 *     are preserved without reloading, navigating, or resetting.
 *   - Zero watermark: videos, handouts, and UI remain 100% visually clean with NO watermarks.
 *   - Detection & suppression of screenshot keys (PrintScreen, PrtSc, Cmd+Shift+3/4/5),
 *     save shortcuts (Ctrl/Cmd+S), print attempts (Ctrl/Cmd+P), and devtools chords.
 *   - Public pages (home, courses, about, login) are completely unaffected.
 *
 * TECHNICAL REALITY NOTICE:
 * Web browsers cannot intercept operating-system-level screen recorders (e.g. OBS Studio, OS Snipping
 * Tool outside the browser window, or external hardware/cameras). The browser-level blackout mitigates
 * standard browser capture and blur events while server-side authentication, short-lived signed stream
 * tickets, and Range-restricted video access provide strong content protection.
 */
(function (window, document) {
    "use strict";

    var Auth = window.LSI_Auth;
    var state = {
        active: false,
        profile: null,
        courseId: null,
        lessonId: null,
        lastEvent: {},
        warnTimer: null,
        blurTimer: null,
        restoreTimer: null,
        autoRestoreTimer: null,
        blackoutActive: false,
        videoState: { wasPlaying: false, currentTime: 0 },
        overlay: null,
        shield: null,
        modal: null,
        inactive: null,
        locals: []
    };
    var EDITABLE = 'input, textarea, select, option, [contenteditable=""], [contenteditable="true"], .lsi-allow-select';

    function el(id) { return document.getElementById(id); }
    function escXml(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
    function isEditable(t) { return !!(t && t.closest && t.closest(EDITABLE)); }
    function session() { try { return Auth && Auth.getSession ? Auth.getSession() : null; } catch (e) { return null; } }
    function isStudentSession() { var s = session(); return !!(s && s.token && s.role === "student"); }
    function certificateOpen() { var c = el("certificatePrint"); return !!(c && !c.classList.contains("d-none")); }

    function isPageOrIframeFocused() {
        try {
            if (document.hasFocus && document.hasFocus()) return true;
            var active = document.activeElement;
            if (active && (active.tagName === "IFRAME" || active.tagName === "EMBED")) {
                return true;
            }
        } catch (e) {}
        return false;
    }

    // ---- audit reporting (throttled per type; never sends tokens) ------------------------------

    function report(type, detail) {
        if (!state.active || !Auth || !Auth.api) return;
        var now = Date.now();
        var gap = type === "PROTECTED_CONTENT_BLUR" ? 60000 : 8000;
        if (state.lastEvent[type] && now - state.lastEvent[type] < gap) return;
        state.lastEvent[type] = now;
        try {
            Auth.api("/student/protection-events", {
                method: "POST", skipAuthRedirect: true,
                body: { type: type, courseId: state.courseId, lessonId: state.lessonId, detail: String(detail || "").slice(0, 200) }
            });
        } catch (e) { /* reporting must never break the page */ }
    }

    // ---- watermark (strictly disabled - clean presentation) ----------------------------------

    function mountWatermark() {
        document.querySelectorAll(".lsi-wm-local").forEach(function (node) { node.remove(); });
        state.locals = [];
    }

    function attachLocalWatermark(container) {
        if (!container) return;
        container.querySelectorAll(":scope > .lsi-wm-local").forEach(function (node) { node.remove(); });
    }

    // ---- full-page blackout & overlay management ---------------------------------------------

    function mountOverlays() {
        if (!state.overlay) {
            state.overlay = el("capture-protection-overlay");
            if (!state.overlay) {
                state.overlay = document.createElement("div");
                state.overlay.id = "capture-protection-overlay";
                state.overlay.setAttribute("aria-hidden", "true");
                var blackout = document.createElement("div");
                blackout.className = "capture-protection-blackout";
                state.overlay.appendChild(blackout);
                (document.body || document.documentElement).appendChild(state.overlay);
            }
        }

        if (state.shield) return;
        state.shield = document.createElement("div");
        state.shield.className = "lsi-shield";
        state.shield.setAttribute("aria-hidden", "true");
        document.body.appendChild(state.shield);

        state.inactive = document.createElement("div");
        state.inactive.className = "lsi-inactive-overlay";
        state.inactive.innerHTML = '<div class="lsi-inactive-card"><i class="fas fa-eye-slash"></i><div>Protected course content is hidden while this window is inactive.</div><small>Click back into the window to continue.</small></div>';
        document.body.appendChild(state.inactive);

        state.modal = document.createElement("div");
        state.modal.className = "lsi-warn";
        state.modal.setAttribute("role", "alertdialog");
        state.modal.setAttribute("aria-modal", "true");
        state.modal.innerHTML =
            '<div class="lsi-warn-card">' +
            '<div class="lsi-warn-icon"><i class="fas fa-shield-alt"></i></div>' +
            '<h3>Protected Content</h3>' +
            '<p id="lsiWarnText">Screen capture and recording of LSI VITC course content is not permitted.</p>' +
            '<p class="lsi-warn-sub">All access to academy course material is licensed and monitored.</p>' +
            '<button type="button" class="lms-btn primary" id="lsiWarnContinue">Continue</button>' +
            '</div>';
        document.body.appendChild(state.modal);
        el("lsiWarnContinue").addEventListener("click", unlock);

        var notice = document.createElement("div");
        notice.className = "lsi-print-notice";
        notice.innerHTML = "<h2>Printing protected course content is not permitted.</h2><p>LSI VITC — " + escXml((state.profile && state.profile.studentId) || "") + "</p>";
        document.body.appendChild(notice);
    }

    // ---- media state preservation -------------------------------------------------------------

    function handleMediaOnBlackout() {
        var videos = document.querySelectorAll("video");
        videos.forEach(function (v) {
            try {
                var isPlaying = (!v.paused && !v.ended && v.readyState > 2);
                state.videoState.wasPlaying = isPlaying;
                state.videoState.currentTime = v.currentTime;
                if (isPlaying) {
                    v.pause();
                }
            } catch (e) { /* ignore */ }
        });
        document.querySelectorAll("audio").forEach(function (a) {
            try { if (!a.paused) a.pause(); } catch (e) { /* ignore */ }
        });
    }

    function handleMediaOnRestore() {
        if (state.videoState && state.videoState.wasPlaying) {
            var videos = document.querySelectorAll("video");
            videos.forEach(function (v) {
                try {
                    v.play().catch(function () {});
                } catch (e) { /* ignore */ }
            });
        }
        state.videoState.wasPlaying = false;
    }

    // ---- blackout application ------------------------------------------------------------------

    function applyBlackout(on, reportType, reportDetail, autoRestoreMs) {
        if (!state.active) return;
        mountOverlays();
        clearTimeout(state.autoRestoreTimer);

        if (on) {
            if (!state.blackoutActive) {
                state.blackoutActive = true;
                handleMediaOnBlackout();
                document.documentElement.classList.add("capture-protection-active");
                document.body.classList.add("capture-protection-active");
            }
            if (reportType) report(reportType, reportDetail);
            if (autoRestoreMs && autoRestoreMs > 0) {
                state.autoRestoreTimer = setTimeout(function () {
                    if (!document.hidden && isPageOrIframeFocused()) {
                        applyBlackout(false);
                    }
                }, autoRestoreMs);
            }
        } else {
            if (state.blackoutActive) {
                state.blackoutActive = false;
                document.documentElement.classList.remove("capture-protection-active");
                document.body.classList.remove("capture-protection-active");
                handleMediaOnRestore();
            }
        }
    }

    /** Compatibility modal lockdown for explicit user interaction */
    function lockdown(type, message, detail, opts) {
        opts = opts || {};
        mountOverlays();
        applyBlackout(true, type, detail, opts.autoMs || 3000);
        if (el("lsiWarnText")) el("lsiWarnText").textContent = message;
        document.body.classList.add("lsi-capture-lock");
        clearTimeout(state.warnTimer);
        state.warnTimer = setTimeout(unlock, opts.autoMs || 8000);
        setTimeout(function () { var b = el("lsiWarnContinue"); if (b) b.focus(); }, 30);
    }

    function unlock() {
        clearTimeout(state.warnTimer);
        document.body.classList.remove("lsi-capture-lock");
        if (!document.hidden && isPageOrIframeFocused()) {
            applyBlackout(false);
        }
    }

    function setInactive(on) {
        if (!state.active) return;
        if (on) {
            applyBlackout(true, "PROTECTED_CONTENT_BLUR", document.hidden ? "tab hidden" : "window blurred");
        } else {
            applyBlackout(false);
        }
    }

    // ---- event handlers --------------------------------------------------------------------------

    function onKeyDown(e) {
        var k = (e.key || "").toLowerCase();
        var mod = e.ctrlKey || e.metaKey;

        if (e.key === "PrintScreen" || e.key === "Snapshot") {
            e.preventDefault();
            applyBlackout(true, "SCREEN_CAPTURE_ATTEMPT", "PrintScreen key", 2500);
            try {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText("Screen capture of LSI VITC course content is not permitted.").catch(function () {});
                }
            } catch (err) {}
            return;
        }

        // macOS screen capture chords
        if (e.metaKey && e.shiftKey && (k === "3" || k === "4" || k === "5")) {
            e.preventDefault();
            applyBlackout(true, "SCREEN_CAPTURE_ATTEMPT", "Cmd+Shift+" + k, 2500);
            return;
        }

        if (mod && k === "p") {
            if (certificateOpen()) return;
            e.preventDefault(); e.stopPropagation();
            applyBlackout(true, "PRINT_ATTEMPT", "Ctrl/Cmd+P", 2500);
            return;
        }

        if (mod && k === "s") {
            if (isEditable(e.target) && !e.shiftKey) { e.preventDefault(); return; }
            e.preventDefault(); e.stopPropagation();
            applyBlackout(true, "DOWNLOAD_ATTEMPT", "Ctrl/Cmd+S", 2500);
            return;
        }

        if (mod && (k === "c" || k === "x" || k === "a") && !isEditable(e.target)) {
            e.preventDefault(); e.stopPropagation();
            report("COPY_ATTEMPT", "Ctrl/Cmd+" + k.toUpperCase());
            return;
        }

        var devtools = e.key === "F12" || (mod && k === "u") ||
            (mod && e.shiftKey && (k === "i" || k === "j" || k === "c")) ||
            (e.metaKey && e.altKey && (k === "i" || k === "j" || k === "c"));
        if (devtools) {
            e.preventDefault(); e.stopPropagation();
            report("DEVTOOLS_SHORTCUT", e.key);
        }
    }

    function onKeyUp(e) {
        if (e.key === "PrintScreen" || e.key === "Snapshot") {
            applyBlackout(true, "SCREEN_CAPTURE_ATTEMPT", "PrintScreen key (keyup)", 2500);
            try {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText("Screen capture of LSI VITC course content is not permitted.").catch(function () {});
                }
            } catch (err) {}
        }
    }

    function onContextMenu(e) { if (!isEditable(e.target)) e.preventDefault(); }
    function onCopyCut(e) { if (!isEditable(e.target)) { e.preventDefault(); report("COPY_ATTEMPT", e.type + " event"); } }
    function onDragStart(e) { var t = e.target; if (t && (t.tagName === "IMG" || t.tagName === "VIDEO" || t.tagName === "CANVAS" || !isEditable(t))) e.preventDefault(); }
    function onSelectStart(e) { if (!isEditable(e.target)) e.preventDefault(); }

    function onBeforePrint() {
        if (certificateOpen()) { document.body.classList.add("lsi-print-allowed"); return; }
        document.body.classList.remove("lsi-print-allowed");
        applyBlackout(true, "PRINT_ATTEMPT", "beforeprint", 2500);
    }
    function onAfterPrint() {
        document.body.classList.remove("lsi-print-allowed");
        if (!document.hidden && isPageOrIframeFocused()) {
            applyBlackout(false);
        }
    }

    function onVisibility() {
        if (document.hidden) {
            clearTimeout(state.restoreTimer);
            applyBlackout(true, "PROTECTED_CONTENT_BLUR", "tab hidden");
        } else {
            clearTimeout(state.restoreTimer);
            state.restoreTimer = setTimeout(function () {
                if (!document.hidden && isPageOrIframeFocused()) {
                    applyBlackout(false);
                }
            }, 60);
        }
    }

    function onBlur() {
        clearTimeout(state.blurTimer);
        state.blurTimer = setTimeout(function () {
            if (document.hidden || !isPageOrIframeFocused()) {
                applyBlackout(true, "PROTECTED_CONTENT_BLUR", "window blurred");
            }
        }, 80);
    }

    function onFocus() {
        clearTimeout(state.blurTimer);
        clearTimeout(state.restoreTimer);
        state.restoreTimer = setTimeout(function () {
            if (!document.hidden && isPageOrIframeFocused()) {
                applyBlackout(false);
            }
        }, 60);
    }

    // ---- activation ------------------------------------------------------------------------------

    function activate(profile) {
        state.profile = {
            name: profile.name || profile.fullName || "Student",
            studentId: profile.studentId || "",
            email: profile.email || ""
        };
        mountWatermark();
        if (state.active) { return; }
        state.active = true;
        document.body.classList.add("lsi-protected");
        mountOverlays();

        document.addEventListener("keydown", onKeyDown, true);
        document.addEventListener("keyup", onKeyUp, true);
        document.addEventListener("contextmenu", onContextMenu, true);
        document.addEventListener("copy", onCopyCut, true);
        document.addEventListener("cut", onCopyCut, true);
        document.addEventListener("dragstart", onDragStart, true);
        document.addEventListener("selectstart", onSelectStart, true);
        document.addEventListener("visibilitychange", onVisibility);
        window.addEventListener("blur", onBlur);
        window.addEventListener("focus", onFocus);
        window.addEventListener("beforeprint", onBeforePrint);
        window.addEventListener("afterprint", onAfterPrint);

        try {
            console.log("%cLSI VITC — Protected Student Portal", "font-size:16px;font-weight:700;color:#0077B6");
            console.log("This content is licensed to one student. Copying or redistributing it is a breach of the academy's terms.");
        } catch (e) { /* console may be unavailable */ }
    }

    function boot() {
        if (!isStudentSession()) return;
        var s = session();
        if (s && s.profile) activate(s.profile);
        if (Auth.refreshProfile) {
            Auth.refreshProfile().then(function (p) { if (p) activate(p); });
        }
    }

    window.LSI_Protect = {
        /** Tells the audit trail which course/lesson subsequent events belong to. */
        setContext: function (courseId, lessonId) { state.courseId = courseId || null; state.lessonId = lessonId || null; },
        attachLocalWatermark: attachLocalWatermark,
        report: report,
        lockdown: lockdown,
        applyBlackout: applyBlackout,
        isActive: function () { return state.active; },
        /** Safe no-op to ensure course handouts and videos remain 100% visually clean without watermarks. */
        stampCanvas: function (canvas) {}
    };

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
    else boot();
})(window, document);
