/**
 * Website Instructions — understands natural-language needs like
 * "i want to add student" and ranks the most relevant numbered topics.
 */
(function () {
    var STOP = {
        a: 1, an: 1, the: 1, i: 1, me: 1, my: 1, we: 1, our: 1, you: 1, your: 1,
        to: 1, for: 1, of: 1, in: 1, on: 1, at: 1, by: 1, from: 1, with: 1, about: 1,
        and: 1, or: 1, but: 1, if: 1, is: 1, am: 1, are: 1, was: 1, were: 1, be: 1,
        do: 1, does: 1, did: 1, can: 1, could: 1, should: 1, would: 1, will: 1,
        please: 1, want: 1, wanna: 1, need: 1, needs: 1, needed: 1, how: 1,
        what: 1, where: 1, when: 1, who: 1, which: 1, this: 1, that: 1, there: 1,
        it: 1, its: 1, into: 1, onto: 1, so: 1, just: 1, also: 1, some: 1, any: 1,
        tell: 1, show: 1, give: 1, explain: 1, help: 1, guide: 1, steps: 1, step: 1,
        process: 1, procedure: 1, way: 1, able: 1, like: 1, know: 1, confusion: 1,
        query: 1, question: 1, questions: 1
    };

    // Phrase → boost tokens (intent understanding for common office/student phrasing).
    var INTENTS = [
        { re: /\b(add|create|new|admit|admission|enroll|register)\b.{0,40}\bstudent/, boost: ["add", "student", "admission"] },
        { re: /\bstudent\b.{0,40}\b(add|create|new|admit|admission|enroll|register)\b/, boost: ["add", "student", "admission"] },
        { re: /\b(edit|update|change)\b.{0,30}\bstudent/, boost: ["edit", "student"] },
        { re: /\b(archive|restore)\b.{0,20}\bstudent/, boost: ["archive", "restore", "student"] },
        { re: /\b(delete|remove|permanently)\b.{0,30}\bstudent/, boost: ["delete", "student", "permanently"] },
        { re: /\b(discount|give\s*discount|reduce\s*fee)\b/, boost: ["discount", "fee"] },
        { re: /\b(record|collect|take|receive)\b.{0,40}\b(payment|fee|fees|installment)\b/, boost: ["record", "payment", "fees", "installment"] },
        { re: /\b(payment|fee|fees|installment)\b.{0,40}\b(record|collect|take|receive)\b/, boost: ["record", "payment", "fees"] },
        { re: /\b(edit|correct|change)\b.{0,20}\bpayment\b/, boost: ["edit", "payment"] },
        { re: /\bpending\b.{0,20}\b(fee|fees|balance|due)\b/, boost: ["pending", "fees"] },
        { re: /\b(fee\s*reminder|send\s*reminder|dues\s*reminder)\b/, boost: ["fee", "reminder", "whatsapp", "email"] },
        { re: /\b(print|download|share|generate)\b.{0,30}\breceipt/, boost: ["receipt", "print", "pdf", "share"] },
        { re: /\breceipt\b/, boost: ["receipt"] },
        { re: /\b(mark|take|do)\b.{0,20}\battendance\b/, boost: ["attendance", "mark"] },
        { re: /\battendance\b/, boost: ["attendance"] },
        { re: /\b(add|edit|create)\b.{0,20}\bbatch/, boost: ["batch", "add", "edit"] },
        { re: /\b(batch|batches|course price|installment slot)/, boost: ["batch", "course", "price"] },
        { re: /\b(payment\s*mode|session\s*type)\b/, boost: ["payment", "mode", "session", "type"] },
        { re: /\b(student\s*id|serial|series|lsa)\b/, boost: ["student", "id", "serial", "series"] },
        { re: /\b(ebook\s*login|create\s*ebook)\b/, boost: ["ebook", "login", "create"] },
        { re: /\b(ebook\s*password|reset\s*password)\b/, boost: ["ebook", "reset", "password"] },
        { re: /\b(reset\s*device|device\s*binding)\b/, boost: ["ebook", "reset", "device"] },
        { re: /\b(block|unblock)\b.{0,20}\bebook\b/, boost: ["ebook", "block", "unblock"] },
        { re: /\b(send|message|whatsapp|email|bulk)\b.{0,40}\b(student|message|reminder|notice|selected)?/, boost: ["email", "whatsapp", "message", "bulk"] },
        { re: /\b(message\s*history|retry\s*failed)\b/, boost: ["message", "history", "retry"] },
        { re: /\b(export|print|excel|pdf|csv)\b/, boost: ["export", "print", "excel", "pdf"] },
        { re: /\b(report|reports)\b/, boost: ["report", "run"] },
        { re: /\b(approve|reject)\b.{0,30}\b(certificate|cert|request)\b/, boost: ["certificate", "approve", "request"] },
        { re: /\b(certificate|cert)\b/, boost: ["certificate"] },
        { re: /\b(apply|request)\b.{0,20}\b(certificate|cert)\b/, boost: ["certificate", "apply"] },
        { re: /\b(grant|revoke)\b.{0,20}\bebook\b/, boost: ["grant", "ebook", "revoke"] },
        { re: /\b(enroll|enrol)\b/, boost: ["enroll", "course"] },
        { re: /\b(doubt|mentor|ask)\b/, boost: ["doubt"] },
        { re: /\b(journal|trade\s*note|add\s*trade)\b/, boost: ["journal", "trade"] },
        { re: /\b(change|forgot|reset)\b.{0,20}\bpassword\b/, boost: ["password", "settings"] },
        { re: /\bpassword\b/, boost: ["password", "settings"] },
        { re: /\b(user\s*id|userid|login\s*id)\b/, boost: ["user", "id", "settings"] },
        { re: /\b(razorpay|payment\s*gateway|demo\s*mode)\b/, boost: ["payment", "gateway", "razorpay"] },
        { re: /\b(blog|article|post)\b/, boost: ["blog"] },
        { re: /\b(testimonial|review)\b/, boost: ["testimonial", "review"] },
        { re: /\b(module|lesson|curriculum)\b/, boost: ["module", "lesson"] },
        { re: /\b(buy|purchase)\b.{0,20}\bebook\b/, boost: ["buy", "ebook"] },
        { re: /\b(ebook|e-book)\b/, boost: ["ebook"] },
        { re: /\b(invoice|invoices)\b/, boost: ["invoice"] },
        { re: /\b(enrollment|enrolled)\b/, boost: ["enrollment"] },
        { re: /\b(device\s*binding|logout\s*all)\b/, boost: ["device", "logout"] },
        { re: /\b(deactivate|activate)\b.{0,20}\bstudent\b/, boost: ["deactivate", "activate", "student"] },
        { re: /\b(whatsapp\s*number|chat\s*on\s*whatsapp)\b/, boost: ["whatsapp", "settings"] },
        { re: /\b(smtp|email\s*setting)\b/, boost: ["email", "settings", "smtp"] },
        { re: /\b(slider|banner|success\s*stor)/, boost: ["slider", "stories"] },
        { re: /\b(watch|play)\b.{0,20}\b(lesson|video|course)\b/, boost: ["watch", "lesson", "course"] },
        { re: /\b(progress|completion)\b/, boost: ["progress"] },
        { re: /\bdiscover\b/, boost: ["discover"] }
    ];

    var SYNONYMS = {
        admit: "admission", admitting: "admission", admission: "admission",
        create: "add", creating: "add", new: "add", register: "add", registering: "add",
        enroll: "add", enrolment: "enrollment", enrollment: "enrollment",
        fee: "fees", fees: "fees", pay: "payment", paying: "payment", payments: "payment",
        installment: "installment", instalment: "installment",
        balance: "pending", due: "pending", overdue: "pending",
        present: "attendance", absent: "attendance",
        pwd: "password", pass: "password",
        cert: "certificate", certificates: "certificate",
        wa: "whatsapp", mail: "email",
        id: "id", ids: "id",
        login: "login", signin: "login", "sign-in": "login",
        buy: "buy", purchase: "buy",
        watch: "course", learn: "course", lesson: "lesson", lessons: "lesson",
        module: "module", modules: "module",
        report: "reports", reports: "reports",
        setting: "settings", settings: "settings",
        student: "student", students: "student",
        receipt: "receipt", receipts: "receipt",
        batch: "batch", batches: "batch",
        course: "course", courses: "course",
        ebook: "ebook", ebooks: "ebook",
        journal: "journal",
        doubt: "doubt",
        invoice: "invoice", invoices: "invoice",
        blog: "blog", blogs: "blog",
        razorpay: "razorpay", gateway: "gateway",
        serial: "serial", series: "series"
    };

    function norm(s) {
        return String(s || "").toLowerCase()
            .replace(/[’']/g, "")
            .replace(/[^a-z0-9/.\s-]+/g, " ")
            .replace(/\s+/g, " ")
            .trim();
    }

    function stemToken(t) {
        if (SYNONYMS[t]) return SYNONYMS[t];
        if (t.length > 4 && /ies$/.test(t)) return t.slice(0, -3) + "y";
        if (t.length > 3 && /s$/.test(t) && !/ss$/.test(t)) return t.slice(0, -1);
        return t;
    }

    function analyzeQuery(raw) {
        var q = norm(raw);
        if (!q) return { tokens: [], phrases: [] };

        var boost = [];
        INTENTS.forEach(function (intent) {
            if (intent.re.test(q)) boost = boost.concat(intent.boost);
        });

        var tokens = q.split(" ")
            .map(stemToken)
            .filter(function (t) { return t && t.length > 1 && !STOP[t]; });

        boost.forEach(function (b) {
            b = stemToken(b);
            if (tokens.indexOf(b) < 0) tokens.push(b);
        });

        // Keep short bigrams for phrases like "student id", "course price".
        var words = q.split(" ").filter(Boolean);
        var phrases = [];
        for (var i = 0; i < words.length - 1; i++) {
            var a = stemToken(words[i]), b = stemToken(words[i + 1]);
            if (!STOP[words[i]] && !STOP[words[i + 1]] && a && b) phrases.push(a + " " + b);
        }

        return { tokens: tokens, phrases: phrases, raw: q };
    }

    function scoreBlock(block, analyzed) {
        if (!analyzed.tokens.length && !analyzed.phrases.length) return 1;
        var hay = norm((block.getAttribute("data-keywords") || "") + " " + block.textContent);
        var title = norm((block.querySelector("h3") || {}).textContent || "");
        var score = 0;
        var hits = 0;

        analyzed.phrases.forEach(function (p) {
            if (hay.indexOf(p) >= 0) { score += 8; hits++; }
            if (title.indexOf(p) >= 0) score += 6;
        });

        analyzed.tokens.forEach(function (t) {
            if (hay.indexOf(t) >= 0) {
                score += title.indexOf(t) >= 0 ? 5 : 2;
                hits++;
            }
        });

        // Need at least one meaningful hit for a non-empty query.
        if (hits === 0) return 0;
        // Prefer blocks that cover more of the intent.
        score += hits * 1.5;
        return score;
    }

    function bindGuide(root) {
        if (!root || root.dataset.guideBound) return;
        root.dataset.guideBound = "1";
        var input = root.querySelector(".guide-search");
        var meta = root.querySelector(".guide-search-meta");
        var empty = root.querySelector(".guide-search-empty");
        var results = root.querySelector(".guide-results") || root;
        var blocks = Array.prototype.slice.call(root.querySelectorAll(".guide-block"));
        if (!input || !blocks.length) return;

        // Remember original order for reset.
        blocks.forEach(function (b, i) { b.dataset.guideOrder = String(i); });

        function run() {
            var analyzed = analyzeQuery(input.value);
            var ranked = blocks.map(function (block) {
                return { block: block, score: scoreBlock(block, analyzed) };
            });

            var searching = !!(analyzed.raw);
            if (searching) {
                ranked.sort(function (a, b) { return b.score - a.score; });
            } else {
                ranked.sort(function (a, b) {
                    return Number(a.block.dataset.guideOrder) - Number(b.block.dataset.guideOrder);
                });
            }

            var shown = 0;
            var bestSet = false;
            ranked.forEach(function (row) {
                var ok = !searching || row.score > 0;
                row.block.style.display = ok ? "" : "none";
                var isBest = searching && ok && !bestSet;
                if (isBest) bestSet = true;
                row.block.classList.toggle("guide-best", isBest);
                var badge = row.block.querySelector(".guide-best-badge");
                if (isBest) {
                    if (!badge) {
                        badge = document.createElement("span");
                        badge.className = "guide-best-badge";
                        badge.textContent = "Best match";
                        var h3 = row.block.querySelector("h3");
                        if (h3) h3.appendChild(badge);
                    }
                } else if (badge) {
                    badge.remove();
                }
                if (ok) shown++;
                results.appendChild(row.block); // keep ranked order (best first while searching)
            });

            if (meta) {
                if (!searching) {
                    meta.textContent = blocks.length + " topics — type what you want to do (e.g. “I want to add student”)";
                } else if (shown) {
                    meta.textContent = "Found " + shown + " relevant topic" + (shown === 1 ? "" : "s") + " for: “" + analyzed.raw + "”";
                } else {
                    meta.textContent = "No matching topic for: “" + analyzed.raw + "”";
                }
            }
            if (empty) {
                empty.classList.toggle("d-none", !(searching && shown === 0));
                if (searching && shown === 0) {
                    empty.textContent = "Could not match that request. Try: “add student”, “record payment”, “mark attendance”, “change password”.";
                }
            }
        }

        input.addEventListener("input", run);
        input.addEventListener("search", run);
        run();
    }

    function initAll() {
        document.querySelectorAll(".lsi-guide").forEach(bindGuide);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initAll);
    } else {
        initAll();
    }
})();
