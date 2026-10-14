/**
 * LORD SAI — WEBSITE ENQUIRIES: EMAIL + WHATSAPP (js/enquiry.js)
 *
 * Used by the "Enquire Now" pop-up (js/main.js) and the Contact page form. On submit:
 *   1. the fields are checked here (the server checks them again);
 *   2. POST /api/public/enquiry emails the enquiry to the academy inbox through the backend's
 *      email system (Main Admin > Email Settings) — no mail credentials in the browser;
 *   3. WhatsApp opens with the same enquiry typed in, addressed to the website WhatsApp number
 *      (Main Admin > WhatsApp Settings, via js/site-whatsapp.js): the app on
 *      phones, WhatsApp Web on computers. A website cannot send it by itself — the visitor presses
 *      Send.
 * WhatsApp is a separate channel: if the email could not go out, the visitor is told so honestly
 * and WhatsApp still opens so the enquiry can reach us. A form that fails validation never opens
 * WhatsApp.
 *
 * Browsers only allow a new tab straight from the click, so the tab is opened immediately
 * ("Preparing WhatsApp…") and pointed at WhatsApp once the server has answered. If the browser
 * blocks it anyway, the result note carries a "Click here to continue on WhatsApp" link.
 *
 * Requires js/api-config.js (API base). js/mode.js is optional (which website the form was on).
 *
 *   LSI_Enquiry.submit(form, { name, phone, email, interest, message }, { result })
 *     fields are the form's own elements (missing ones are skipped); `result` is an optional
 *     element for the status note (otherwise one is added at the end of the form).
 *   LSI_Enquiry.clear(form)  removes the note and the field errors (e.g. when a pop-up closes).
 */
(function (window, document) {
    "use strict";

    /** The website WhatsApp number (Main Admin > WhatsApp Settings), via js/site-whatsapp.js. */
    function ownerWhatsApp() {
        return (window.LSI_SiteWhatsApp && window.LSI_SiteWhatsApp.number()) || "919987049070";
    }
    var MAX_MESSAGE = 3000;
    var TIMEOUT_MS = 30000;
    var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;                // same rules as the backend
    var MOBILE_RE = /^(?:\+?91[\s-]?|0)?[6-9](?:[\s-]?\d){9}$/;
    var FORMS = "#demoRegistrationForm, #webinarBannerForm, #contactInquiryForm, #lsiContactForm";

    var MSG = {
        name: "Please enter your full name.",
        phone: "Please enter a valid 10-digit mobile number.",
        email: "Please enter a valid email address.",
        interest: "Please select a program of interest.",
        message: "Your message is too long (maximum " + MAX_MESSAGE + " characters).",
        fix: "Please correct the highlighted fields."
    };

    function clean(v) { return v == null ? "" : String(v).trim(); }
    function apiBase() { return (window.LSI_CONFIG && window.LSI_CONFIG.API_BASE) || "/api"; }

    function siteName() {
        var mode = window.LSI_Mode && typeof window.LSI_Mode.getMode === "function" ? window.LSI_Mode.getMode() : "";
        return mode === "mutual-fund" ? "Mutual Fund" : "Share Market Academy";
    }

    function pageName() {
        var file = (window.location.pathname.split("/").pop() || "").replace(/\.html?$/i, "");
        if (!file || file === "index" || file === "home") return "Home";
        if (/^(sip|swp)$/i.test(file)) return file.toUpperCase();
        return file.replace(/-/g, " ").replace(/\b\w/g, function (c) { return c.toUpperCase(); });
    }

    // ---- WhatsApp -------------------------------------------------------------------------------

    function isMobile() {
        var ua = navigator.userAgent || "";
        return /Android|iPhone|iPad|iPod|Mobile|Silk|Opera Mini|IEMobile/i.test(ua)
            || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);   // iPadOS reports itself as a Mac
    }

    function whatsappText(d) {
        var lines = ["New Enquiry - LSI Website", "", "Name: " + d.name];
        if (d.email) lines.push("Email: " + d.email);
        lines.push("Phone: " + d.phone);
        if (d.interest) lines.push("Interest: " + d.interest);
        if (d.message) lines.push("Message: " + d.message);
        return lines.join("\n");
    }

    /** The app on phones, WhatsApp Web on computers. Every character of the text is URL-encoded. */
    function whatsappUrl(text) {
        var t = encodeURIComponent(text);
        return isMobile()
            ? "https://wa.me/" + ownerWhatsApp() + "?text=" + t
            : "https://web.whatsapp.com/send?phone=" + ownerWhatsApp() + "&text=" + t;
    }

    /** Works everywhere (wa.me offers the app or WhatsApp Web) — used for the fallback link. */
    function fallbackUrl(text) { return "https://wa.me/" + ownerWhatsApp() + "?text=" + encodeURIComponent(text); }

    /** Opened during the click so pop-up blockers allow it; pointed at WhatsApp later. */
    function openPlaceholder() {
        var win = null;
        try { win = window.open("", "_blank"); } catch (e) { win = null; }
        if (!win) return null;
        try {
            win.document.write('<!doctype html><meta charset="utf-8"><title>Opening WhatsApp…</title>' +
                '<body style="margin:0;height:100vh;display:flex;align-items:center;justify-content:center;' +
                'font-family:Segoe UI,Arial,sans-serif;color:#0F766E;background:#F0FDFA">' +
                '<p style="font-size:18px">Sending your enquiry and preparing WhatsApp…</p></body>');
            win.document.close();
        } catch (e) { /* the tab still works as a target */ }
        return win;
    }

    /** Points the placeholder at WhatsApp (or tries a fresh tab). Returns whether a tab is showing it. */
    function goToWhatsApp(win, url) {
        if (win && !win.closed) {
            try { win.opener = null; } catch (e) { /* ignore */ }
            try { win.location.replace(url); return true; } catch (e) { /* fall through */ }
        }
        var fresh = null;
        // (not the "noopener" feature: with it window.open always returns null, even when the tab opened)
        try { fresh = window.open(url, "_blank"); } catch (e) { fresh = null; }
        if (fresh) { try { fresh.opener = null; } catch (e) { /* ignore */ } }
        return !!fresh;
    }

    function closeQuietly(win) { try { if (win && !win.closed) win.close(); } catch (e) { /* ignore */ } }

    // ---- validation -----------------------------------------------------------------------------

    function feedbackFor(el) {
        for (var n = el.nextElementSibling; n; n = n.nextElementSibling) {
            if (n.classList.contains("invalid-feedback")) return n;
        }
        var fb = document.createElement("div");
        fb.className = "invalid-feedback";
        el.insertAdjacentElement("afterend", fb);
        return fb;
    }

    function markField(el, message) {
        if (!el) return;
        el.classList.add("is-invalid");
        el.setAttribute("aria-invalid", "true");
        feedbackFor(el).textContent = message;
    }

    function clearFields(form) {
        form.querySelectorAll(".is-invalid").forEach(function (el) {
            el.classList.remove("is-invalid");
            el.removeAttribute("aria-invalid");
        });
    }

    function read(fields) {
        return {
            name: clean(fields.name && fields.name.value),
            phone: clean(fields.phone && fields.phone.value),
            email: clean(fields.email && fields.email.value),
            interest: clean(fields.interest && fields.interest.value),
            message: clean(fields.message && fields.message.value)
        };
    }

    /** { field: message } for every problem; empty when the enquiry can be sent. */
    function validate(d, fields) {
        var errors = {};
        if (d.name.length < 2 || d.name.length > 120) errors.name = MSG.name;
        if (!MOBILE_RE.test(d.phone)) errors.phone = MSG.phone;
        if (d.email && (!EMAIL_RE.test(d.email) || d.email.length > 190)) errors.email = MSG.email;
        if (fields.interest && fields.interest.required && !d.interest) errors.interest = MSG.interest;
        if (d.message.length > MAX_MESSAGE) errors.message = MSG.message;
        return errors;
    }

    // ---- status note ------------------------------------------------------------------------------

    function resultBox(form, given) {
        if (given) return given;
        var box = form.querySelector(".lsi-enquiry-result");
        if (!box) {
            box = document.createElement("div");
            box.className = "lsi-enquiry-result mt-3 mb-0 shadow-sm";
            form.appendChild(box);
        }
        return box;
    }

    function hideResult(box) {
        if (!box) return;
        box.classList.add("d-none");
        box.textContent = "";
    }

    function el(tag, cls, text) {
        var e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text != null) e.textContent = text;
        return e;
    }

    /**
     * kind: "success" | "warning" | "danger". lines: plain-text sentences (translated by js/lang.js).
     * waUrl: adds "WhatsApp didn't open? Click here to continue on WhatsApp."
     */
    function showResult(box, kind, icon, title, lines, waUrl, blocked) {
        box.classList.remove("d-none", "alert-success", "alert-warning", "alert-danger");
        box.classList.add("alert", "alert-" + kind);
        box.setAttribute("role", kind === "danger" ? "alert" : "status");
        box.textContent = "";

        var row = el("div", "d-flex gap-2 align-items-start");
        row.appendChild(el("i", icon + " mt-1"));
        var body = el("div");
        if (title) body.appendChild(el("strong", "d-block mb-1", title));
        lines.forEach(function (line) { body.appendChild(el("div", null, line)); });
        if (waUrl) {
            if (blocked) body.appendChild(el("div", "mt-2 fw-bold", "Your browser did not open WhatsApp automatically."));
            var more = el("div", "small mt-2");
            more.appendChild(document.createTextNode("WhatsApp didn't open? "));
            var a = el("a", "alert-link", "Click here to continue on WhatsApp.");
            a.href = waUrl;
            a.target = "_blank";
            a.rel = "noopener noreferrer";
            more.appendChild(a);
            body.appendChild(more);
        }
        row.appendChild(body);
        box.appendChild(row);
    }

    // ---- sending --------------------------------------------------------------------------------

    function post(payload) {
        var ctrl = typeof AbortController === "function" ? new AbortController() : null;
        var timer = ctrl ? setTimeout(function () { ctrl.abort(); }, TIMEOUT_MS) : null;
        return fetch(apiBase() + "/public/enquiry", {
            method: "POST",
            headers: { "Content-Type": "application/json", "Accept": "application/json" },
            body: JSON.stringify(payload),
            signal: ctrl ? ctrl.signal : undefined
        }).then(function (r) {
            return r.json().catch(function () { return {}; }).then(function (res) {
                return { status: r.status, delivered: r.ok && res.success === true, res: res };
            });
        }).catch(function () {
            return { status: 0, delivered: false, res: {} };
        }).then(function (out) {
            if (timer) clearTimeout(timer);
            return out;
        });
    }

    function setBusy(button, on) {
        if (!button) return;
        if (on) {
            button.dataset.lsiLabel = button.innerHTML;
            button.disabled = true;
            button.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span> Sending…';
        } else if (button.dataset.lsiLabel != null) {
            button.innerHTML = button.dataset.lsiLabel;
            button.disabled = false;
            delete button.dataset.lsiLabel;
        }
    }

    /** Must be called from the submit handler (see the note at the top about pop-up blockers). */
    function submit(form, fields, options) {
        options = options || {};
        if (form.dataset.lsiEnquiryBusy === "1") return Promise.resolve("busy");   // double clicks

        var box = resultBox(form, options.result);
        var data = read(fields);
        clearFields(form);
        var errors = validate(data, fields);
        var invalid = Object.keys(errors);
        if (invalid.length) {
            invalid.forEach(function (k) { markField(fields[k], errors[k]); });
            showResult(box, "danger", "fas fa-exclamation-circle", null, [MSG.fix]);
            if (fields[invalid[0]] && fields[invalid[0]].focus) fields[invalid[0]].focus();
            return Promise.resolve("invalid");
        }

        form.dataset.lsiEnquiryBusy = "1";
        var button = form.querySelector('button[type="submit"]');
        setBusy(button, true);
        hideResult(box);
        var trap = form.querySelector('input[name="website"]');
        var text = whatsappText(data);
        var placeholder = openPlaceholder();

        return post({
            name: data.name, email: data.email, phone: data.phone, subject: data.interest, message: data.message,
            site: siteName(), page: pageName(), website: trap ? trap.value : ""
        }).then(function (out) {
            if (out.status === 400) {
                // The server disagreed with the form (it checks the same rules): no WhatsApp.
                closeQuietly(placeholder);
                var serverErrors = (out.res && out.res.errors) || {};
                Object.keys(serverErrors).forEach(function (k) {
                    markField(fields[k === "subject" ? "interest" : k], serverErrors[k]);
                });
                showResult(box, "danger", "fas fa-exclamation-circle", null, [(out.res && out.res.message) || MSG.fix]);
                return "invalid";
            }

            var opened = goToWhatsApp(placeholder, whatsappUrl(text));
            var link = fallbackUrl(text);
            if (out.delivered) {
                showResult(box, "success", "fas fa-check-circle", "Enquiry submitted!", [
                    "Your enquiry has been submitted successfully. A copy has been sent to our team, and WhatsApp is ready to send your enquiry.",
                    "Please press Send in WhatsApp so it reaches us there too."
                ], link, !opened);
                form.reset();
                return "sent";
            }
            if (out.status === 429) {
                showResult(box, "warning", "fas fa-exclamation-triangle", "Too many enquiries from this connection.", [
                    "Please wait a few minutes before sending another, or send this one on WhatsApp — it is ready with your details."
                ], link, !opened);
            } else if (out.status === 0) {
                showResult(box, "warning", "fas fa-exclamation-triangle", "We could not reach our server.", [
                    "Your enquiry has not been emailed. WhatsApp is ready with your details — please press Send there so it reaches us."
                ], link, !opened);
            } else {
                showResult(box, "warning", "fas fa-exclamation-triangle", "Your enquiry could not be emailed right now.", [
                    "Our email system did not accept it, so please send it on WhatsApp instead — WhatsApp is ready with your details. Just press Send."
                ], link, !opened);
            }
            return "not-emailed";
        }).then(function (outcome) {
            delete form.dataset.lsiEnquiryBusy;
            setBusy(button, false);
            return outcome;
        });
    }

    function clear(form) {
        if (!form) return;
        clearFields(form);
        var box = form.querySelector(".lsi-enquiry-result");
        if (box) box.remove();
    }

    // ---- set up the forms -------------------------------------------------------------------------

    /** Hidden field that people never see but form-filling bots do; the server ignores such posts. */
    function addHoneypot(form) {
        if (form.querySelector('input[name="website"]')) return;
        var trap = document.createElement("div");
        trap.setAttribute("aria-hidden", "true");
        trap.style.cssText = "position:absolute;left:-10000px;top:auto;width:1px;height:1px;overflow:hidden;";
        var input = document.createElement("input");
        input.type = "text";
        input.name = "website";
        input.tabIndex = -1;
        input.autocomplete = "off";
        trap.appendChild(input);
        form.appendChild(trap);
    }

    function init() {
        document.querySelectorAll(FORMS).forEach(function (form) {
            form.noValidate = true;           // our messages instead of the browser's pop-ups
            addHoneypot(form);
            ["input", "change"].forEach(function (type) {
                form.addEventListener(type, function (e) {
                    if (e.target && e.target.classList && e.target.classList.contains("is-invalid")) {
                        e.target.classList.remove("is-invalid");
                        e.target.removeAttribute("aria-invalid");
                    }
                });
            });
        });
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
    else init();

    window.LSI_Enquiry = {
        submit: submit,
        clear: clear,
        whatsappText: whatsappText,
        whatsappUrl: whatsappUrl,
        fallbackUrl: fallbackUrl,
        validate: validate,
        ownerWhatsApp: ownerWhatsApp
    };
})(window, document);
