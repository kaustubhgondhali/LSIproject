/**
 * ============================================================================
 * LORD SAI — WHATSAPP "CLICK TO CHAT" (js/whatsapp-chat.js)
 * ============================================================================
 * Opens WhatsApp on this computer (WhatsApp Web, or the WhatsApp app) — or the app on a phone —
 * on a student's chat with the message already typed. This is WhatsApp's own feature: no WhatsApp
 * Business account and no API. The admin presses Send; nothing is sent automatically.
 *
 *   LSI_WhatsAppChat.open(phone, text)            call INSIDE the click: opens straight away
 *   LSI_WhatsAppChat.prepare()                    call inside the click when the message arrives later
 *   LSI_WhatsAppChat.openPrepared(handle, phone, text) / LSI_WhatsAppChat.cancel(handle)
 *   LSI_WhatsAppChat.copyPicture(png)             picture (Blob, or a Promise of one) -> clipboard, for
 *                                                 Ctrl + V in the chat; resolves true when copied
 *   LSI_WhatsAppChat.savePicture(blob, fileName)  the picture as a file, when it cannot be copied
 *   LSI_WhatsAppChat.mode() / setMode("web"|"app")  remembered on this computer
 *   LSI_WhatsAppChat.number(mobile)               "9876543210" -> "919876543210"
 */
(function (window, document) {
    "use strict";

    var MODE_KEY = "lsi_whatsapp_open_mode";
    var TAB = "lsiWhatsApp";   // one WhatsApp tab, reused where the browser allows it

    function isMobile() {
        var ua = navigator.userAgent || "";
        return /Android|iPhone|iPad|iPod|Mobile|Silk|Opera Mini|IEMobile/i.test(ua)
            || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);   // iPadOS reports itself as a Mac
    }

    function mode() {
        try { return window.localStorage.getItem(MODE_KEY) === "app" ? "app" : "web"; } catch (e) { return "web"; }
    }

    function setMode(m) {
        try { window.localStorage.setItem(MODE_KEY, m === "app" ? "app" : "web"); } catch (e) { /* private mode: default stays */ }
    }

    /** India first: 10 digits get 91; a leading 0 or 00 is dropped. Same rule as the server. */
    function number(mobile) {
        var d = String(mobile == null ? "" : mobile).replace(/\D/g, "");
        if (d.indexOf("00") === 0) d = d.substring(2);
        if (d.length === 11 && d.charAt(0) === "0") d = d.substring(1);
        if (d.length === 10) return "91" + d;
        return d.length >= 11 && d.length <= 15 ? d : null;
    }

    function url(phone, text) {
        var p = number(phone) || "", t = encodeURIComponent(text || "");
        if (isMobile()) return "https://wa.me/" + p + "?text=" + t;
        if (mode() === "app") return "whatsapp://send?phone=" + p + "&text=" + t;
        return "https://web.whatsapp.com/send?phone=" + p + "&text=" + t;
    }

    /** The WhatsApp app on this computer (Windows / Mac) opens through its own link type. */
    function launchApp(u) {
        var a = document.createElement("a");
        a.href = u;
        a.style.display = "none";
        document.body.appendChild(a);
        a.click();
        a.remove();
    }

    /** Opens now. Must run inside the click; returns false when the browser blocked the new tab. */
    function open(phone, text) {
        var u = url(phone, text);
        if (u.indexOf("whatsapp://") === 0) { launchApp(u); return true; }
        var w = null;
        try { w = window.open(u, TAB); } catch (e) { w = null; }
        if (w) { try { w.focus(); } catch (e) { /* ignore */ } }
        return !!w;
    }

    /** Reserves the WhatsApp tab during the click, for a message that comes from the server afterwards. */
    function prepare() {
        if (!isMobile() && mode() === "app") return { app: true };
        var w = null;
        try { w = window.open("", TAB); } catch (e) { w = null; }
        if (w) {
            try {
                if (w.location.href === "about:blank") {
                    w.document.write('<!doctype html><meta charset="utf-8"><title>Opening WhatsApp…</title>' +
                        '<body style="margin:0;height:100vh;display:flex;align-items:center;justify-content:center;' +
                        'font-family:Segoe UI,Arial,sans-serif;color:#075E54;background:#F0FDF4">' +
                        '<p style="font-size:18px">Preparing the WhatsApp message…</p></body>');
                    w.document.close();
                }
            } catch (e) { /* the tab already shows WhatsApp: it is pointed at the new chat below */ }
        }
        return { win: w };
    }

    function openPrepared(handle, phone, text) {
        var u = url(phone, text);
        if (handle && handle.app) { launchApp(u); return true; }
        if (handle && handle.win && !handle.win.closed) {
            try { handle.win.location.href = u; handle.win.focus(); return true; } catch (e) { /* fall through */ }
        }
        return open(phone, text);
    }

    /** Closes the reserved tab when nothing is to be opened after all (only if it is still our placeholder). */
    function cancel(handle) {
        try {
            if (handle && handle.win && !handle.win.closed && handle.win.location.href === "about:blank") handle.win.close();
        } catch (e) { /* it shows WhatsApp: leave it */ }
    }

    /**
     * WhatsApp's chat link carries text only, so a picture goes in through the clipboard: copied here,
     * pasted in the chat with Ctrl + V. Copy BEFORE opening WhatsApp — the clipboard only works while
     * this page is in front. Needs a recent browser on https (or on this computer itself).
     */
    function copyPicture(png) {
        var C = window.ClipboardItem;
        if (!(window.isSecureContext && navigator.clipboard && navigator.clipboard.write && C)) return Promise.resolve(false);
        if (typeof C.supports === "function" && !C.supports("image/png")) return Promise.resolve(false);
        try {
            return navigator.clipboard.write([new C({ "image/png": png })]).then(function () { return true; }, function () { return false; });
        } catch (e) {
            return Promise.resolve(false);
        }
    }

    /** Saves the picture as a file (when it cannot be copied), for attaching in WhatsApp instead. */
    function savePicture(blob, fileName) {
        if (!blob) return false;
        var u = URL.createObjectURL(blob);
        var a = document.createElement("a");
        a.href = u;
        a.download = fileName || "receipt.png";
        a.style.display = "none";
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () { URL.revokeObjectURL(u); }, 60000);
        return true;
    }

    window.LSI_WhatsAppChat = {
        open: open, prepare: prepare, openPrepared: openPrepared, cancel: cancel,
        copyPicture: copyPicture, savePicture: savePicture,
        url: url, mode: mode, setMode: setMode, number: number, isMobile: isMobile
    };
})(window, document);
