/**
 * LORD SAI — WEBSITE WHATSAPP NUMBER (js/site-whatsapp.js)
 *
 * Every "Chat on WhatsApp" link on the website and the enquiry forms (js/enquiry.js) open a chat
 * with ONE number, set by the Main Admin under WhatsApp Settings. The pages ship with the default
 * number in their links; this script points them at the saved number as soon as it is known and
 * remembers it in the browser so the next page switches instantly. Displayed phone numbers and
 * tel: links are not touched; only elements marked data-site-whatsapp-display show this number.
 *
 *   LSI_SiteWhatsApp.number()        "919987049070" (saved number, or the default)
 *   LSI_SiteWhatsApp.link(text)      https://wa.me/<number>?text=...
 */
(function (window, document) {
    "use strict";

    var DEFAULT_NUMBER = "919987049070";
    /** Numbers the static pages may still carry; only links to these are re-pointed. */
    var SITE_NUMBERS = ["919987049070", "919920254354"];
    var CACHE_KEY = "lsi_site_whatsapp_number";
    var current = DEFAULT_NUMBER;

    try {
        var cached = window.localStorage.getItem(CACHE_KEY);
        if (/^\d{11,15}$/.test(cached || "")) current = cached;
    } catch (e) { /* private mode: default stays */ }

    function apiBase() {
        if (window.LSI_CONFIG && window.LSI_CONFIG.API_BASE) return window.LSI_CONFIG.API_BASE;
        var host = window.location.hostname;
        var local = host === "localhost" || host === "127.0.0.1" || host === "" || window.location.protocol === "file:";
        return local ? "http://localhost:8080/api" : "/api";
    }

    function isSiteNumber(n) { return n === current || SITE_NUMBERS.indexOf(n) !== -1; }

    /** Re-points one wa.me / api.whatsapp.com link at the website number; other numbers are left alone. */
    function repoint(a) {
        var href = a.getAttribute("href") || "";
        var m = href.match(/^(https?:\/\/wa\.me\/)\+?(\d+)(.*)$/i);
        if (m && isSiteNumber(m[2]) && m[2] !== current) {
            a.setAttribute("href", m[1] + current + m[3]);
            return;
        }
        m = href.match(/^(https?:\/\/(?:api|web)\.whatsapp\.com\/send\?(?:.*&)?phone=)\+?(\d+)(.*)$/i);
        if (m && isSiteNumber(m[2]) && m[2] !== current) {
            a.setAttribute("href", m[1] + current + m[3]);
        }
    }

    /** "919987049070" -> "+91 99870 49070" (other countries: "+" and the digits). */
    function display(n) {
        return /^91\d{10}$/.test(n) ? "+91 " + n.substring(2, 7) + " " + n.substring(7) : "+" + n;
    }

    function apply() {
        document.querySelectorAll('a[href*="wa.me/"], a[href*="whatsapp.com/send"]').forEach(repoint);
        document.querySelectorAll("[data-site-whatsapp-display]").forEach(function (el) { el.textContent = display(current); });
    }

    function setNumber(n) {
        if (!/^\d{11,15}$/.test(n || "")) return;
        current = n;
        try { window.localStorage.setItem(CACHE_KEY, n); } catch (e) { /* ignore */ }
        apply();
    }

    function load() {
        if (typeof fetch !== "function") return;
        fetch(apiBase() + "/public/site-contact", { headers: { "Accept": "application/json" } })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (res) { if (res && res.data) setNumber(res.data.whatsappNumber); })
            .catch(function () { /* offline: the cached or default number stays */ });
    }

    // Links added later (translations, pop-ups) are fixed at the moment they are clicked.
    document.addEventListener("click", function (e) {
        var a = e.target && e.target.closest ? e.target.closest("a[href]") : null;
        if (a) repoint(a);
    }, true);

    function init() { apply(); load(); }
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
    else init();

    window.LSI_SiteWhatsApp = {
        number: function () { return current; },
        link: function (text) { return "https://wa.me/" + current + (text ? "?text=" + encodeURIComponent(text) : ""); }
    };
})(window, document);
