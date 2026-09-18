/**
 * LORD SAI ACADEMY — STUDENT PORTAL CONTENT PROTECTION (js/student-protect.js)
 *
 * Loaded by every Student Portal page after js/auth.js. Activates only for an authenticated
 * STUDENT session and applies, page-wide:
 *   - a dynamic, student-specific watermark (name / Student ID / email) drawn on protected
 *     media only: inside the lesson player and on rendered handout pages. There is no
 *     page-background watermark and no roaming identity badge;
 *   - best-effort screen-capture detection (Print Screen, print / save / copy / dev-tools
 *     shortcuts) that hides the content behind a warning overlay and reports the event;
 *   - hiding protected content while the tab is hidden or the window loses focus;
 *   - context-menu, copy/cut, drag and text-selection blocking outside editable fields;
 *   - print suppression (except the student's own certificate).
 *
 * Browsers cannot stop an operating system or a phone camera from capturing the screen. The
 * server-side enrollment checks and the identifying watermark are the primary protection; the
 * rest is deterrence. Nothing here logs the student out or blocks legitimate typing.
 */
(function (window, document) {
    "use strict";

    var Auth = window.LSI_Auth;
    var state = {
        active: false, profile: null, courseId: null, lessonId: null,
        lastEvent: {}, warnTimer: null, blurTimer: null, wmTimer: null,
        shield: null, modal: null, inactive: null, locals: []
    };
    var EDITABLE = 'input, textarea, select, option, [contenteditable=""], [contenteditable="true"], .lsi-allow-select';

    function el(id) { return document.getElementById(id); }
    function escXml(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
    function isEditable(t) { return !!(t && t.closest && t.closest(EDITABLE)); }
    function session() { try { return Auth && Auth.getSession ? Auth.getSession() : null; } catch (e) { return null; } }
    function isStudentSession() { var s = session(); return !!(s && s.token && s.role === "student"); }
    function certificateOpen() { var c = el("certificatePrint"); return !!(c && !c.classList.contains("d-none")); }

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

    // ---- watermark ------------------------------------------------------------------------------

    function watermarkImage() {
        var p = state.profile || {};
        var lines = ["LSI VITC", p.name || "Student", p.studentId || "", p.email || "", "Protected Course Content"].filter(Boolean);
        var w = 380, h = 240, cx = w / 2, cy = h / 2, start = cy - ((lines.length - 1) * 21) / 2;
        var svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + w + '" height="' + h + '">' +
            '<g transform="rotate(-27 ' + cx + ' ' + cy + ')" fill="#0a1128" fill-opacity="0.16" font-family="Inter,Segoe UI,Arial,sans-serif" text-anchor="middle">' +
            lines.map(function (t, i) {
                return '<text x="' + cx + '" y="' + (start + i * 21) + '" font-size="' + (i === 0 ? 16 : 13) + '" font-weight="' + (i === 0 || i === 2 ? 800 : 600) + '">' + escXml(t) + '</text>';
            }).join("") + '</g></svg>';
        return 'url("data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg) + '")';
    }

    function shiftWatermark() {
        var pos = Math.floor(Math.random() * 380) + "px " + Math.floor(Math.random() * 240) + "px";
        state.locals.forEach(function (l) { if (l.isConnected) l.style.backgroundPosition = pos; });
    }

    /** Refreshes the watermark on the media layers only; nothing is added to the page background. */
    function mountWatermark() {
        var img = watermarkImage();
        state.locals.forEach(function (l) { if (l.isConnected) l.style.backgroundImage = img; });
        shiftWatermark();
        clearInterval(state.wmTimer);
        if (state.locals.length) state.wmTimer = setInterval(shiftWatermark, 20000);
    }

    /** Adds a watermark layer inside a container so it stays visible when that container is fullscreen. */
    function attachLocalWatermark(container) {
        if (!container || container.querySelector(":scope > .lsi-wm-local")) return;
        var l = document.createElement("div");
        l.className = "lsi-wm-local";
        l.setAttribute("aria-hidden", "true");
        if (state.profile) l.style.backgroundImage = watermarkImage();
        container.appendChild(l);
        state.locals.push(l);
    }

    // ---- warning overlay -------------------------------------------------------------------------

    function mountOverlays() {
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
            '<p class="lsi-warn-sub">Your Student ID is embedded in the content watermark.</p>' +
            '<button type="button" class="lms-btn primary" id="lsiWarnContinue">Continue</button>' +
            '</div>';
        document.body.appendChild(state.modal);
        el("lsiWarnContinue").addEventListener("click", unlock);

        var notice = document.createElement("div");
        notice.className = "lsi-print-notice";
        notice.innerHTML = "<h2>Printing protected course content is not permitted.</h2><p>LSI VITC — " + escXml((state.profile && state.profile.studentId) || "") + "</p>";
        document.body.appendChild(notice);
    }

    function pauseMedia() {
        document.querySelectorAll("video, audio").forEach(function (m) { try { if (!m.paused) m.pause(); } catch (e) { /* ignore */ } });
    }

    /** Hides the content behind the warning; restores on Continue or after a short delay. */
    function lockdown(type, message, detail, opts) {
        opts = opts || {};
        mountOverlays();
        el("lsiWarnText").textContent = message;
        document.body.classList.add("lsi-capture-lock");
        if (!opts.keepPlaying) pauseMedia();
        clearTimeout(state.warnTimer);
        state.warnTimer = setTimeout(unlock, opts.autoMs || 8000);
        report(type, detail);
        setTimeout(function () { var b = el("lsiWarnContinue"); if (b) b.focus(); }, 30);
    }

    function unlock() {
        clearTimeout(state.warnTimer);
        document.body.classList.remove("lsi-capture-lock");
    }

    function setInactive(on) {
        if (!state.active) return;
        document.body.classList.toggle("lsi-inactive", on);
        if (on) { pauseMedia(); report("PROTECTED_CONTENT_BLUR", document.hidden ? "tab hidden" : "window blurred"); }
    }

    // ---- event handlers --------------------------------------------------------------------------

    function onKeyDown(e) {
        var k = (e.key || "").toLowerCase();
        var mod = e.ctrlKey || e.metaKey;

        if (e.key === "PrintScreen") {
            e.preventDefault();
            lockdown("SCREEN_CAPTURE_ATTEMPT", "Screen capture is not allowed for course content.", "PrintScreen key");
            return;
        }
        // macOS screenshot chords rarely reach the page, but when they do, react.
        if (e.metaKey && e.shiftKey && (k === "3" || k === "4" || k === "5")) {
            e.preventDefault();
            lockdown("SCREEN_CAPTURE_ATTEMPT", "Screen capture is not allowed for course content.", "Cmd+Shift+" + k);
            return;
        }
        if (mod && k === "p") {
            if (certificateOpen()) return;
            e.preventDefault(); e.stopPropagation();
            lockdown("PRINT_ATTEMPT", "Printing protected course content is not permitted.", "Ctrl/Cmd+P", { autoMs: 5000 });
            return;
        }
        if (mod && k === "s") {
            if (isEditable(e.target) && !e.shiftKey) { e.preventDefault(); return; }
            e.preventDefault(); e.stopPropagation();
            lockdown("DOWNLOAD_ATTEMPT", "Saving or downloading course content is not permitted.", "Ctrl/Cmd+S", { autoMs: 5000 });
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
        // Windows fires keyup reliably for Print Screen even when keydown is swallowed.
        if (e.key !== "PrintScreen") return;
        lockdown("SCREEN_CAPTURE_ATTEMPT", "Screen capture is not allowed for course content.", "PrintScreen key");
        try {
            var p = state.profile || {};
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText("Screen capture of LSI VITC course content is not permitted. " + (p.studentId || "")).catch(function () { /* ignore */ });
            }
        } catch (err) { /* clipboard access is optional */ }
    }

    function onContextMenu(e) { if (!isEditable(e.target)) e.preventDefault(); }
    function onCopyCut(e) { if (!isEditable(e.target)) { e.preventDefault(); report("COPY_ATTEMPT", e.type + " event"); } }
    function onDragStart(e) { var t = e.target; if (t && (t.tagName === "IMG" || t.tagName === "VIDEO" || t.tagName === "CANVAS" || !isEditable(t))) e.preventDefault(); }
    function onSelectStart(e) { if (!isEditable(e.target)) e.preventDefault(); }

    function onBeforePrint() {
        if (certificateOpen()) { document.body.classList.add("lsi-print-allowed"); return; }
        document.body.classList.remove("lsi-print-allowed");
        report("PRINT_ATTEMPT", "beforeprint");
    }
    function onAfterPrint() { document.body.classList.remove("lsi-print-allowed"); }

    function onVisibility() { setInactive(document.hidden); }
    function onBlur() {
        clearTimeout(state.blurTimer);
        state.blurTimer = setTimeout(function () { if (!document.hasFocus()) setInactive(true); }, 400);
    }
    function onFocus() { clearTimeout(state.blurTimer); setInactive(false); }

    // ---- activation ------------------------------------------------------------------------------

    function activate(profile) {
        state.profile = {
            name: profile.name || profile.fullName || "Student",
            studentId: profile.studentId || "",
            email: profile.email || ""
        };
        if (state.active) { mountWatermark(); return; }
        state.active = true;
        document.body.classList.add("lsi-protected");
        mountOverlays();
        document.querySelectorAll("[data-protect-fs]").forEach(attachLocalWatermark);
        mountWatermark();

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
            console.log("This content is licensed to one student and carries an identifying watermark. Copying or redistributing it is a breach of the academy's terms.");
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
        isActive: function () { return state.active; },
        /** Draws the student watermark across a canvas (used by the handout viewer). */
        stampCanvas: function (canvas) {
            if (!state.profile) return;
            var ctx = canvas.getContext("2d"); if (!ctx) return;
            var p = state.profile, text = "LSI VITC · " + (p.name || "") + " · " + (p.studentId || p.email || "");
            ctx.save();
            ctx.globalAlpha = 0.14; ctx.fillStyle = "#0a1128";
            ctx.font = "700 " + Math.max(14, Math.round(canvas.width / 38)) + "px Inter, Segoe UI, Arial, sans-serif";
            ctx.translate(canvas.width / 2, canvas.height / 2); ctx.rotate(-Math.PI / 7);
            var step = Math.max(140, Math.round(canvas.height / 4)), w = ctx.measureText(text).width + 120;
            for (var y = -canvas.height; y < canvas.height; y += step) {
                for (var x = -canvas.width; x < canvas.width; x += w) { ctx.fillText(text, x, y); }
            }
            ctx.restore();
        }
    };

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
    else boot();
})(window, document);
