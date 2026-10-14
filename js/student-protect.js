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
 *   - Capture lock (N x 5 minutes for the Nth attempt in 24 h, course and ebook counted separately):
 *     a detected capture shortcut blurs lesson videos and handouts, or the ebook
 *     reader behind a countdown. The lock survives reloads and new tabs (localStorage + server copy).
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
        locals: [],
        lockUntil: { COURSE: 0, EBOOK: 0 },
        penaltyTimer: null,
        chord: null
    };
    /** Each capture attempt adds 5 minutes: 5, 10, 15 … (matches the server). */
    var PENALTY_STEP_MS = 5 * 60 * 1000;
    /** Strikes reset 24 hours after the first attempt (matches the server). */
    var STRIKE_WINDOW_MS = 24 * 60 * 60 * 1000;
    var SCOPES = ["COURSE", "EBOOK"];
    var IS_APPLE = /Mac|iPhone|iPad|iPod/i.test(navigator.platform || navigator.userAgent || "");
    /** Containers blurred during a capture lock, per scope. Course and ebook locks are independent. */
    var PENALTY_TARGETS = {
        COURSE: ["#playerShell", "#materialViewer"],
        EBOOK: ["#ebookReaderModal .modal-body"]
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

    function report(type, detail, scope) {
        if (!state.active || !Auth || !Auth.api) return;
        var now = Date.now();
        var gap = type === "PROTECTED_CONTENT_BLUR" ? 60000 : 8000;
        var throttleKey = type + "|" + (scope || "");
        if (state.lastEvent[throttleKey] && now - state.lastEvent[throttleKey] < gap) return;
        state.lastEvent[throttleKey] = now;
        try {
            Auth.api("/student/protection-events", {
                method: "POST", skipAuthRedirect: true,
                body: { type: type, courseId: state.courseId, lessonId: state.lessonId, detail: String(detail || "").slice(0, 200), scope: scope || null }
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
        if (state.videoState && state.videoState.wasPlaying && !penaltyActive("COURSE")) {
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

    // ---- capture lock: the Nth attempt within 24 h blurs that content for N x 5 minutes ----------
    // Course (lesson video + handouts) and ebook are separate scopes with separate strikes and timers.

    function storageKey(kind, scope) {
        var s = session();
        var sid = (state.profile && state.profile.studentId) || (s && s.profile && (s.profile.studentId || s.profile.email)) || "me";
        return "lsi_capture_" + kind + ":" + sid + ":" + scope;
    }

    function saveLock(scope) {
        try { localStorage.setItem(storageKey("lock", scope), String(state.lockUntil[scope])); } catch (e) { /* storage may be blocked */ }
    }

    function loadLock(scope) {
        try { return parseInt(localStorage.getItem(storageKey("lock", scope)), 10) || 0; } catch (e) { return 0; }
    }

    /** Counts this attempt for the scope and returns the lock length: 5 min x attempts in the 24 h window. */
    function nextPenaltyMs(scope) {
        var key = storageKey("strikes", scope);
        var now = Date.now(), strikes = null;
        try { strikes = JSON.parse(localStorage.getItem(key)); } catch (e) { /* fresh start */ }
        if (!strikes || !strikes.windowStart || now - strikes.windowStart >= STRIKE_WINDOW_MS) {
            strikes = { count: 0, windowStart: now };
        }
        strikes.count += 1;
        try { localStorage.setItem(key, JSON.stringify(strikes)); } catch (e) { /* storage may be blocked */ }
        return strikes.count * PENALTY_STEP_MS;
    }

    /** What the student is looking at right now: the open ebook reader, the lesson page, or neither. */
    function currentScope() {
        var reader = el("ebookReaderModal");
        if (reader && reader.classList.contains("show")) return "EBOOK";
        if (el("playerShell") || el("materialViewer")) return "COURSE";
        return null;
    }

    function mountPenaltyCovers(scope) {
        PENALTY_TARGETS[scope].forEach(function (sel) {
            var box = document.querySelector(sel);
            if (!box || box.querySelector(":scope > .lsi-penalty-cover")) return;
            if (getComputedStyle(box).position === "static") box.style.position = "relative";
            var cover = document.createElement("div");
            cover.className = "lsi-penalty-cover";
            cover.setAttribute("data-scope", scope);
            cover.setAttribute("role", "alert");
            cover.innerHTML = '<div class="lsi-penalty-card"><i class="fas fa-user-shield"></i>' +
                '<strong>Screen capture detected</strong>' +
                '<span>' + (scope === "EBOOK" ? "This ebook" : "Course content") + ' is hidden for <b class="lsi-penalty-time"></b></span>' +
                '<small>Each further attempt within 24 hours adds 5 more minutes. Screenshots and screen recordings of LSI VITC ' +
                (scope === "EBOOK" ? "ebooks" : "course videos and handouts") + ' are not permitted.</small></div>';
            box.appendChild(cover);
        });
    }

    function pauseAllMedia() {
        document.querySelectorAll("video, audio").forEach(function (m) { try { if (!m.paused) m.pause(); } catch (e) { /* ignore */ } });
    }

    function penaltyActive(scope) { return state.lockUntil[scope || "COURSE"] > Date.now(); }

    function tickPenalty() {
        var running = false;
        SCOPES.forEach(function (scope) {
            var left = state.lockUntil[scope] - Date.now();
            var cls = "lsi-lock-" + scope.toLowerCase();
            if (left <= 0) {
                if (document.body.classList.contains(cls)) {
                    state.lockUntil[scope] = 0;
                    saveLock(scope);
                    document.body.classList.remove(cls);
                }
                return;
            }
            running = true;
            document.body.classList.add(cls);
            mountPenaltyCovers(scope);
            var sec = Math.ceil(left / 1000);
            var label = Math.floor(sec / 60) + ":" + ("0" + (sec % 60)).slice(-2);
            document.querySelectorAll('.lsi-penalty-cover[data-scope="' + scope + '"] .lsi-penalty-time')
                .forEach(function (n) { n.textContent = label; });
        });
        if (!running) { clearInterval(state.penaltyTimer); state.penaltyTimer = null; }
    }

    function showPenalty(scope) {
        if (scope === "COURSE") pauseAllMedia();
        tickPenalty();
        if (!state.penaltyTimer) state.penaltyTimer = setInterval(tickPenalty, 1000);
    }

    /** Locks the content in view (course or ebook) and tells the server, which keeps its own copy. */
    function startPenalty(detail) {
        if (!state.active) return;
        var scope = currentScope();
        if (!scope) { report("SCREEN_CAPTURE_ATTEMPT", detail); return; }
        if (!penaltyActive(scope)) {
            state.lockUntil[scope] = Date.now() + nextPenaltyMs(scope);
            saveLock(scope);
        }
        showPenalty(scope);
        report("SCREEN_CAPTURE_ATTEMPT", detail, scope);
    }

    /** Restores running locks after reload / in another tab: local copy first, then the server's. */
    function restorePenalty() {
        SCOPES.forEach(function (scope) {
            var local = loadLock(scope);
            if (local > Date.now()) { state.lockUntil[scope] = local; showPenalty(scope); }
        });
        if (!Auth || !Auth.api) return;
        Auth.api("/student/protection-lock", { skipAuthRedirect: true }).then(function (res) {
            var data = (res && res.data) || {};
            SCOPES.forEach(function (scope) {
                var secs = data[scope];
                if (secs > 0) {
                    state.lockUntil[scope] = Math.max(state.lockUntil[scope], Date.now() + secs * 1000);
                    saveLock(scope);
                    showPenalty(scope);
                }
            });
        }).catch(function () { /* offline: the local copy still applies */ });
    }

    function onPlayDuringPenalty(e) {
        if (penaltyActive("COURSE") && e.target && e.target.pause) e.target.pause();
    }

    function onStorage(e) {
        SCOPES.forEach(function (scope) {
            if (e.key !== storageKey("lock", scope)) return;
            var until = parseInt(e.newValue, 10) || 0;
            if (until > Date.now()) { state.lockUntil[scope] = until; showPenalty(scope); }
        });
    }

    /**
     * OS capture shortcuts (Win+Shift+S, Win+Alt+R, Win+G, Cmd+Shift+3/4/5) are usually swallowed by the
     * system, so the page only sees the modifiers. Modifiers held and then an immediate focus loss is
     * treated as a capture tool opening.
     */
    function trackChord(e) {
        var k = e.key || "";
        var isMod = k === "Meta" || k === "OS" || k === "Shift" || k === "Alt" || k === "Control";
        if (!isMod) { state.chord = null; return; }
        if (e.metaKey && (e.shiftKey || e.altKey)) {
            state.chord = { at: Date.now(), name: e.shiftKey ? "Win/Cmd+Shift" : "Win/Cmd+Alt" };
        }
    }

    function chordJustUsed() {
        return !!(state.chord && Date.now() - state.chord.at < 1500);
    }

    // ---- event handlers --------------------------------------------------------------------------

    function onKeyDown(e) {
        var k = (e.key || "").toLowerCase();
        var mod = e.ctrlKey || e.metaKey;
        trackChord(e);

        if (!IS_APPLE && e.metaKey && !e.ctrlKey && (k === "g" || (e.altKey && (k === "r" || k === "printscreen")) || (e.shiftKey && (k === "s" || k === "r")))) {
            e.preventDefault();
            applyBlackout(true, null, null, 2500);
            startPenalty("Capture shortcut Win+" + (e.altKey ? "Alt+" : "") + (e.shiftKey ? "Shift+" : "") + e.key);
            return;
        }

        if (e.key === "PrintScreen" || e.key === "Snapshot") {
            e.preventDefault();
            applyBlackout(true, null, null, 2500);
            startPenalty("PrintScreen key");
            try {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText("Screen capture of LSI VITC course content is not permitted.").catch(function () {});
                }
            } catch (err) {}
            return;
        }

        // macOS screen capture chords
        if (e.metaKey && e.shiftKey && (k === "3" || k === "4" || k === "5" || k === "6")) {
            e.preventDefault();
            applyBlackout(true, null, null, 2500);
            startPenalty("Cmd+Shift+" + k);
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
            applyBlackout(true, null, null, 2500);
            startPenalty("PrintScreen key (keyup)");
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
        var chord = chordJustUsed() ? state.chord.name : null;
        state.chord = null;
        clearTimeout(state.blurTimer);
        state.blurTimer = setTimeout(function () {
            // A tab switch hides the document; a capture overlay steals focus but leaves the page visible.
            if (chord && !document.hidden) {
                startPenalty(chord + " shortcut followed by focus loss (system capture tool)");
            }
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
        window.addEventListener("storage", onStorage);
        document.addEventListener("play", onPlayDuringPenalty, true);
        restorePenalty();

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
        /** True while the capture lock keeps videos and ebooks blurred. */
        isCaptureLocked: function (scope) { return penaltyActive(scope); },
        /** Safe no-op to ensure course handouts and videos remain 100% visually clean without watermarks. */
        stampCanvas: function (canvas) {}
    };

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
    else boot();
})(window, document);
