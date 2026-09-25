/**
 * LORD SAI — ENQUIRY SENDER: WHATSAPP + EMAIL (js/enquiry.js)
 *
 * The website has no server, so the enquiry, contact and review forms reach the owner two ways
 * at once:
 *   1. WhatsApp: WhatsApp opens (the app on phones, WhatsApp Web or Desktop on computers) with
 *      the details already typed in a chat to the owner, and the visitor taps Send.
 *      WhatsApp does not let a website send a message on its own.
 *   2. Email: the same details are emailed to the owner in the background, whether or not the
 *      visitor sends the WhatsApp message.
 *
 * Settings (edit below):
 *   whatsapp      - number that receives WhatsApp enquiries (country code + number, digits only)
 *   email         - inbox that receives the email copies, through FormSubmit (https://formsubmit.co).
 *                   No account or key: the FIRST enquiry sends an "Activate Form" email to this
 *                   inbox, and after that one click every enquiry is delivered. Once activated,
 *                   the random code from FormSubmit's email can replace the address here, so the
 *                   address is not visible in the page source.
 *   web3formsKey  - optional alternative to FormSubmit. When a Web3Forms access key is filled in
 *                   (free at https://web3forms.com: enter the owner's email and the key is emailed
 *                   there), emails go through Web3Forms instead. The key is made to be public.
 *
 * Usage (call directly inside the submit handler, or pop-up blockers stop WhatsApp opening):
 *   var sent = LSI_Enquiry.send({ subject, intro, fields: [[label, value], ...], messageLabel,
 *                                 message, source, replyTo });
 *   sent.url     - the wa.me link, for a "WhatsApp didn't open? Tap here" link
 *   sent.opened  - false if the browser blocked WhatsApp opening
 *   sent.emailed - Promise<boolean>: true once the email copy was accepted (never rejects)
 * Empty fields are left out.
 */
(function (window) {
    "use strict";

    var settings = {
        whatsapp: "919920254354",
        email: "lordsai.academy@gmail.com",
        web3formsKey: "YOUR_WEB3FORMS_ACCESS_KEY"
    };

    var FORMSUBMIT_URL = "https://formsubmit.co/ajax/";
    var WEB3FORMS_URL = "https://api.web3forms.com/submit";
    var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

    function clean(v) { return v == null ? "" : String(v).trim(); }

    function websiteName() {
        var mode = window.LSI_Mode && typeof window.LSI_Mode.getMode === "function" ? window.LSI_Mode.getMode() : "";
        return mode === "mutual-fund" ? "Lord Sai Investment" : "Lord Sai Share Market Academy";
    }

    function timestamp() {
        try {
            return new Date().toLocaleString("en-IN", {
                timeZone: "Asia/Kolkata", day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit"
            }) + " IST";
        } catch (e) {
            return new Date().toString();
        }
    }

    /** "web3forms" when its key is filled in, else "formsubmit" when an email is set, else "". */
    function emailService() {
        var k = clean(settings.web3formsKey);
        if (k !== "" && k.indexOf("YOUR_") !== 0) return "web3forms";
        return clean(settings.email) ? "formsubmit" : "";
    }

    // ---- WhatsApp ---------------------------------------------------------------------------

    /** Plain text for WhatsApp; *text* shows as bold there. */
    function whatsappText(o) {
        var lines = [clean(o.intro) || "Hello " + websiteName() + ", I have an enquiry from your website.", ""];
        (o.fields || []).forEach(function (f) {
            var value = clean(f[1]);
            if (value) lines.push("*" + f[0] + ":* " + value);
        });
        var message = clean(o.message);
        if (message) lines.push("", "*" + (o.messageLabel || "Message") + ":*", message);
        if (o.source) lines.push("", "(" + o.source + ")");
        return lines.join("\n");
    }

    function link(text) {
        return "https://wa.me/" + settings.whatsapp + (text ? "?text=" + encodeURIComponent(text) : "");
    }

    function openWhatsApp(url) {
        var win = null;
        try { win = window.open(url, "_blank"); } catch (e) { win = null; }
        if (win) { try { win.opener = null; } catch (e) { /* cross-origin already */ } }
        return !!win;
    }

    // ---- Email (FormSubmit, or Web3Forms when its key is set) ----------------------------------

    function sendEmail(o) {
        var service = emailService();
        if (!service) {
            if (window.console) console.warn("[LSI_Enquiry] Email copy skipped: no email set in js/enquiry.js.");
            return Promise.resolve(false);
        }
        if (!window.fetch) return Promise.resolve(false);
        var subject = clean(o.subject) || "New website enquiry";
        var replyTo = clean(o.replyTo);
        if (!EMAIL_RE.test(replyTo)) replyTo = ""; // a real address makes "Reply" in the inbox go to the visitor
        var url, data;
        if (service === "web3forms") {
            url = WEB3FORMS_URL;
            data = { access_key: clean(settings.web3formsKey), subject: subject, from_name: websiteName() + " website" };
            if (replyTo) data.replyto = replyTo;
        } else {
            url = FORMSUBMIT_URL + clean(settings.email);
            data = { _subject: subject, _template: "table", _captcha: "false" };
            if (replyTo) data._replyto = replyTo;
        }
        (o.fields || []).forEach(function (f) {
            var value = clean(f[1]);
            if (value) data[f[0]] = value;
        });
        var message = clean(o.message);
        if (message) data[o.messageLabel || "Message"] = message;
        if (o.source) data["Sent from"] = o.source;
        data["Page"] = window.location.href.split("#")[0];
        data["Sent at"] = timestamp();

        return fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json", "Accept": "application/json" },
            body: JSON.stringify(data)
        }).then(function (r) {
            return r.json().catch(function () { return {}; }).then(function (res) {
                // FormSubmit answers success as the text "true"/"false", Web3Forms as true/false.
                var ok = r.ok && (res.success === true || res.success === "true");
                if (!ok && window.console) {
                    // Until the owner clicks "Activate Form", FormSubmit replies that the form needs activation.
                    console.warn("[LSI_Enquiry] Email copy not sent (" + service + "):", r.status, res.message || res);
                }
                return ok;
            });
        }).catch(function (err) {
            if (window.console) console.warn("[LSI_Enquiry] Email copy not sent (" + service + "):", err);
            return false;
        });
    }

    // ---- Both -------------------------------------------------------------------------------

    function send(o) {
        o = o || {};
        var url = link(whatsappText(o));
        var opened = openWhatsApp(url); // first, while the click still counts as the visitor's action
        return { url: url, opened: opened, emailed: sendEmail(o) };
    }

    /**
     * Note shown under an enquiry form once WhatsApp has opened, with a link in case it did not.
     * English only; js/lang.js translates it (entries in js/i18n/common.js).
     */
    function sentNote(url) {
        return '<div><i class="fab fa-whatsapp me-2"></i> <strong>Almost done!</strong> WhatsApp has opened with your enquiry. ' +
            'Please tap <strong>Send</strong> there so it reaches us. WhatsApp didn\'t open? ' +
            '<a href="' + url + '" target="_blank" rel="noopener" class="alert-link">Tap here</a>.</div>';
    }

    /** Line added under the note once the email copy went through. */
    function emailNote() {
        return '<div class="small mt-2"><i class="fas fa-envelope me-1"></i> A copy has also been emailed to us.</div>';
    }

    window.LSI_Enquiry = {
        send: send,
        link: link,
        sentNote: sentNote,
        emailNote: emailNote,
        websiteName: websiteName,
        emailService: emailService,
        settings: settings
    };
})(window);
