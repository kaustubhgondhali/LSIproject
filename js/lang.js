/**
 * LORD SAI SHARE MARKET CLASSES
 * LANGUAGE MANAGEMENT & LOCALIZATION CONTROLLER (js/lang.js)
 *
 * Supported Languages:
 *   - 'en': English (Default)
 *   - 'mr': Marathi (मराठी)
 *   - 'hi': Hindi (हिंदी)
 *
 * Persists selected language across navigation using localStorage ('lsi_site_lang').
 * Dynamically mounts the language selector below the navigation bar aligned right.
 */

(function (window, document) {
    "use strict";

    const STORAGE_KEY = "lsi_site_lang";
    const DEFAULT_LANG = "en";
    const SUPPORTED_LANGS = ["en", "mr", "hi"];

    /* =====================================================================
     * PHRASE TRANSLATION ENGINE
     *
     * Everything that is not tagged with data-i18n is translated here by
     * looking its English text up in window.LSI_Phrases:
     *
     *     "English text": ["हिंदी", "मराठी"]
     *
     * The dictionaries live in js/i18n/common.js (text shared by several
     * pages) and js/i18n/<page>.js, and are only downloaded once Hindi or
     * Marathi is chosen, so English visitors load nothing extra.
     *
     * A "unit" is the smallest block whose text reads as one sentence: an
     * element that holds only text and inline tags (a, strong, span, br, i...).
     *   - Plain units ("Enquire Now", "<i></i> Call Us") translate each text
     *     run in place (icons are left untouched).
     *   - Mixed units, where an inline tag with its own text sits inside the
     *     sentence ("<strong>Note:</strong> Past returns..."), are keyed as a
     *     template: "<0>Note:</0> Past returns..." so the translation can put
     *     the tag where that language needs it. The original inline elements
     *     (with their classes and listeners) are moved, never recreated.
     * Numbers are matched generically: "Module {#}" covers Module 1..9, and
     * "{#}" in the translation receives the numbers back in order ({#2} picks
     * a specific one).
     * Originals are kept, so switching back to English restores the page
     * exactly, and a MutationObserver translates text that scripts add later.
     * Mark anything that must stay English with translate="no" or .notranslate.
     * ===================================================================== */
    const I18N_VERSION = "20260925h";
    const SKIP_TAGS = new Set(["SCRIPT", "STYLE", "NOSCRIPT", "TEMPLATE", "CODE", "PRE", "TEXTAREA",
        "CANVAS", "IFRAME", "OBJECT", "EMBED", "VIDEO", "AUDIO", "MATH", "HEAD"]);
    const INLINE_TAGS = new Set(["A", "ABBR", "B", "BDI", "BDO", "BR", "CITE", "DFN", "EM", "I", "KBD",
        "MARK", "Q", "S", "SMALL", "SPAN", "STRONG", "SUB", "SUP", "TIME", "U", "VAR", "WBR", "IMG",
        "FONT", "DEL", "INS"]);
    const TRANSLATABLE_ATTRS = ["placeholder", "title"];
    const TOKEN_RE = /(<\/?\d+\/?>)|(\d+(?:[.,]\d+)*)/g;
    const SVG_NS = "http://www.w3.org/2000/svg";

    function norm(s) {
        return String(s).replace(/\s+/g, " ").trim(); // \s also covers the no-break space
    }

    function hasLetters(s) {
        return /[A-Za-z]/.test(s);
    }

    function isBlank(node) {
        return node.nodeType === 3 && !/\S/.test(node.nodeValue);
    }

    /** Key with every number replaced by {#} (placeholder tags like <0> are left alone). */
    function numberKey(key) {
        const nums = [];
        const k = key.replace(TOKEN_RE, function (m, tag, num) {
            if (tag) return tag;
            nums.push(num);
            return "{#}";
        });
        return { key: k, nums: nums };
    }

    function hasText(el) {
        return /\S/.test(el.textContent || "");
    }

    function isSkipped(el) {
        if (SKIP_TAGS.has(el.tagName)) return true;
        if (el.id === "siteLangBar") return true;
        if (el.getAttribute("translate") === "no") return true;
        if (el.classList && el.classList.contains("notranslate")) return true;
        return false;
    }

    /** True when el and everything inside it is inline (text-level) markup. */
    function isInlineOnly(el) {
        const kids = el.children;
        for (let i = 0; i < kids.length; i++) {
            const c = kids[i];
            if (c.namespaceURI === SVG_NS || !INLINE_TAGS.has(c.tagName) || isSkipped(c)) return false;
            if (c.children.length && !isInlineOnly(c)) return false;
        }
        return true;
    }

    const Phrases = {
        lang: "en",
        records: new Set(),
        textRec: new WeakMap(),
        observer: null,
        applying: false,
        pending: new Set(),
        pendingTimer: null,
        loadPromise: null,
        keyedSkip: null,
        collecting: null,

        /** Download js/i18n/common.js and js/i18n/<page>.js (once). */
        ensureLoaded: function () {
            if (this.loadPromise) return this.loadPromise;
            const page = ((window.location.pathname.split("/").pop() || "index.html").replace(/\.html?$/i, "") || "index");
            const files = ["common", page];
            const scripts = document.querySelectorAll("script[src]");
            let base = "js/";
            for (let i = 0; i < scripts.length; i++) {
                const m = scripts[i].getAttribute("src").match(/^(.*)lang\.js(\?.*)?$/);
                if (m) { base = m[1]; break; }
            }
            this.loadPromise = Promise.all(files.map(function (name) {
                return new Promise(function (resolve) {
                    const s = document.createElement("script");
                    s.src = base + "i18n/" + name + ".js?v=" + I18N_VERSION;
                    s.async = false;
                    s.onload = s.onerror = function () { resolve(); };
                    document.head.appendChild(s);
                });
            }));
            return this.loadPromise;
        },

        lookup: function (key) {
            const dict = window.LSI_Phrases;
            if (!dict || !key) return null;
            const idx = this.lang === "hi" ? 0 : 1;
            let v = dict[key];
            if (v && v[idx]) return v[idx];
            if (/\d/.test(key)) {
                const nk = numberKey(key);
                v = dict[nk.key];
                if (v && v[idx]) {
                    let seq = 0;
                    return v[idx].replace(/\{#(\d*)\}/g, function (m, n) {
                        const val = n ? nk.nums[Number(n) - 1] : nk.nums[seq++];
                        return val === undefined ? "" : val;
                    });
                }
            }
            return null;
        },

        /** Record a key while collecting (used by the extraction tooling only). */
        collect: function (key) {
            if (!key || !hasLetters(key.replace(/<\/?\d+\/?>/g, ""))) return;
            const k = /\d/.test(key) ? numberKey(key).key : key;
            this.collecting.set(k, (this.collecting.get(k) || 0) + 1);
        },

        translateTextNode: function (node) {
            if (this.textRec.has(node)) return;
            const raw = node.nodeValue;
            const key = norm(raw);
            if (!key || !hasLetters(key)) return;
            if (this.collecting) { this.collect(key); return; }
            const tr = this.lookup(key);
            if (!tr) return;
            const lead = raw.match(/^\s*/)[0];
            const trail = raw.match(/\s*$/)[0];
            const value = (lead ? " " : "") + tr + (trail ? " " : "");
            const rec = { type: "text", node: node, orig: raw, tr: value };
            node.nodeValue = value;
            this.textRec.set(node, rec);
            this.records.add(rec);
        },

        /** Serialise a mixed unit to "<0>Note:</0> text" and remember the elements. */
        serialise: function (el, elements) {
            let out = "";
            const kids = el.childNodes;
            for (let i = 0; i < kids.length; i++) {
                const n = kids[i];
                if (n.nodeType === 3) {
                    out += n.nodeValue;
                } else if (n.nodeType === 1) {
                    const idx = elements.push(n) - 1;
                    out += hasText(n) ? "<" + idx + ">" + this.serialise(n, elements) + "</" + idx + ">" : "<" + idx + "/>";
                }
            }
            return out;
        },

        translateMixed: function (el) {
            const elements = [];
            const key = norm(this.serialise(el, elements));
            if (this.collecting) { this.collect(key); return; }
            const tr = this.lookup(key);
            const wanted = (key.match(/<\/?\d+\/?>/g) || []).sort().join();
            if (!tr || (tr.match(/<\/?\d+\/?>/g) || []).sort().join() !== wanted) {
                // No usable template translation: fall back to translating each text run.
                this.translatePlainRuns(el);
                return;
            }
            const containers = [el];
            elements.forEach(function (e) { if (hasText(e)) containers.push(e); });
            const saved = containers.map(function (c) { return { el: c, nodes: Array.prototype.slice.call(c.childNodes), made: [] }; });
            const byEl = new Map(saved.map(function (s) { return [s.el, s]; }));
            containers.forEach(function (c) { while (c.firstChild) c.removeChild(c.firstChild); });
            const stack = [el];
            const parts = tr.split(/(<\/?\d+\/?>)/);
            for (let i = 0; i < parts.length; i++) {
                const p = parts[i];
                if (!p) continue;
                const top = stack[stack.length - 1];
                let m;
                if ((m = p.match(/^<(\d+)\/>$/))) {
                    top.appendChild(elements[+m[1]]);
                    byEl.get(top).made.push(elements[+m[1]]);
                } else if ((m = p.match(/^<(\d+)>$/))) {
                    const child = elements[+m[1]];
                    top.appendChild(child);
                    byEl.get(top).made.push(child);
                    stack.push(child);
                } else if (/^<\/\d+>$/.test(p)) {
                    if (stack.length > 1) stack.pop();
                } else {
                    const t = document.createTextNode(p);
                    top.appendChild(t);
                    byEl.get(top).made.push(t);
                }
            }
            const rec = { type: "mixed", el: el, saved: saved };
            this.records.add(rec);
        },

        translatePlainRuns: function (el) {
            const self = this;
            const kids = Array.prototype.slice.call(el.childNodes);
            kids.forEach(function (n) {
                if (n.nodeType === 3) self.translateTextNode(n);
                else if (n.nodeType === 1 && !isSkipped(n) && hasText(n)) self.translateUnit(n);
            });
        },

        /** el holds only inline markup: translate it as plain runs or as one mixed template. */
        translateUnit: function (el) {
            const kids = Array.prototype.slice.call(el.childNodes);
            let start = 0, end = kids.length - 1;
            const edge = function (n) { return isBlank(n) || (n.nodeType === 1 && !hasText(n)); };
            while (start <= end && edge(kids[start])) start++;
            while (end >= start && edge(kids[end])) end--;
            let directText = false, innerElement = false;
            for (let i = start; i <= end; i++) {
                if (kids[i].nodeType === 3 && !isBlank(kids[i])) directText = true;
                if (kids[i].nodeType === 1) innerElement = true;
            }
            if (directText && innerElement) this.translateMixed(el);
            else this.translatePlainRuns(el);
        },

        visit: function (el) {
            if (el.nodeType !== 1 || isSkipped(el)) return;
            if (this.keyedSkip && el.hasAttribute("data-i18n") && this.keyedSkip(el)) return;
            if (el.namespaceURI === SVG_NS) {
                const self = this;
                const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
                let t;
                while ((t = walker.nextNode())) self.translateTextNode(t);
                return;
            }
            if (!hasText(el)) return;
            if (isInlineOnly(el)) { this.translateUnit(el); return; }
            const kids = Array.prototype.slice.call(el.childNodes);
            for (let i = 0; i < kids.length; i++) {
                const n = kids[i];
                if (n.nodeType === 3) this.translateTextNode(n);
                else if (n.nodeType === 1) this.visit(n);
            }
        },

        translateAttrs: function (root) {
            const self = this;
            const els = root.querySelectorAll("[placeholder],[title],input[type=submit][value],input[type=button][value],input[type=reset][value]");
            els.forEach(function (el) {
                if (el.closest("#siteLangBar, [translate=no], .notranslate")) return;
                const attrs = TRANSLATABLE_ATTRS.slice();
                if (el.tagName === "INPUT" && /^(submit|button|reset)$/i.test(el.type)) attrs.push("value");
                attrs.forEach(function (a) {
                    if (!el.hasAttribute(a)) return;
                    const raw = el.getAttribute(a);
                    const key = norm(raw);
                    if (!key || !hasLetters(key)) return;
                    if (self.collecting) { self.collect(key); return; }
                    const tr = self.lookup(key);
                    if (!tr) return;
                    el.setAttribute(a, tr);
                    self.records.add({ type: "attr", el: el, attr: a, orig: raw, tr: tr });
                });
            });
        },

        translateTitle: function () {
            const raw = document.title;
            const key = norm(raw);
            if (!key) return;
            if (this.collecting) { this.collect(key); return; }
            const tr = this.lookup(key);
            if (!tr) return;
            document.title = tr;
            this.records.add({ type: "title", orig: raw, tr: tr });
        },

        restoreRecord: function (rec) {
            if (rec.type === "text") {
                if (rec.node.nodeValue === rec.tr) rec.node.nodeValue = rec.orig;
                this.textRec.delete(rec.node);
            } else if (rec.type === "mixed") {
                // Put each container's original children back, unless a script has
                // replaced them since (then the script's newer content is kept).
                rec.saved.forEach(function (s) {
                    const now = s.el.childNodes;
                    let untouched = now.length === s.made.length;
                    for (let i = 0; untouched && i < now.length; i++) if (now[i] !== s.made[i]) untouched = false;
                    if (!untouched) return;
                    while (s.el.firstChild) s.el.removeChild(s.el.firstChild);
                    s.nodes.forEach(function (n) { s.el.appendChild(n); });
                });
            } else if (rec.type === "attr") {
                if (rec.el.getAttribute(rec.attr) === rec.tr) rec.el.setAttribute(rec.attr, rec.orig);
            } else if (rec.type === "title") {
                if (document.title === rec.tr) document.title = rec.orig;
            }
        },

        restoreAll: function () {
            const self = this;
            this.guard(function () {
                self.records.forEach(function (rec) { self.restoreRecord(rec); });
                self.records.clear();
            });
        },

        /** Run DOM writes without the observer reacting to them. */
        guard: function (fn) {
            this.applying = true;
            try { fn(); } finally {
                if (this.observer) this.observer.takeRecords();
                this.applying = false;
            }
        },

        translateAll: function (lang) {
            const self = this;
            this.restoreAll();
            this.lang = lang;
            if (lang === "en") { this.stopObserver(); return; }
            this.guard(function () {
                self.visit(document.body);
                self.translateAttrs(document.body);
                self.translateTitle();
            });
            this.startObserver();
        },

        startObserver: function () {
            if (this.observer || typeof MutationObserver === "undefined") {
                if (this.observer) this.observer.observe(document.body, this.observeOptions);
                return;
            }
            const self = this;
            this.observeOptions = { childList: true, characterData: true, subtree: true, attributes: true, attributeFilter: ["placeholder", "title", "value"] };
            this.observer = new MutationObserver(function (list) { self.onMutations(list); });
            this.observer.observe(document.body, this.observeOptions);
        },

        stopObserver: function () {
            if (this.observer) this.observer.disconnect();
        },

        onMutations: function (list) {
            if (this.applying || this.lang === "en") return;
            const self = this;
            list.forEach(function (m) {
                if (m.type === "attributes") {
                    const el = m.target;
                    self.records.forEach(function (rec) {
                        if (rec.type === "attr" && rec.el === el && rec.attr === m.attributeName) self.records.delete(rec);
                    });
                    self.pending.add(el);
                    return;
                }
                let target = m.type === "characterData" ? m.target.parentElement : m.target;
                if (!target) return;
                if (m.type === "characterData" && !hasLetters(m.target.nodeValue || "") && !self.textRec.has(m.target)) return;
                if (m.type === "childList") {
                    let letters = false;
                    m.addedNodes.forEach(function (n) { if (n.nodeType === 1 || hasLetters(n.nodeValue || "")) letters = true; });
                    if (!letters) return;
                }
                self.pending.add(target);
            });
            if (this.pending.size && !this.pendingTimer) {
                this.pendingTimer = setTimeout(function () { self.flush(); }, 40);
            }
        },

        flush: function () {
            this.pendingTimer = null;
            const targets = Array.from(this.pending);
            this.pending.clear();
            if (this.lang === "en") return;
            const self = this;
            const roots = [];
            targets.forEach(function (el) {
                if (!el.isConnected || !document.body.contains(el)) return;
                if (el.closest("#siteLangBar, [translate=no], .notranslate, script, style")) return;
                let u = el;
                while (u.parentElement && u.parentElement !== document.body && isInlineOnly(u.parentElement)) u = u.parentElement;
                if (roots.indexOf(u) === -1) roots.push(u);
            });
            if (!roots.length) return;
            this.guard(function () {
                roots.forEach(function (u) {
                    // Put the unit back to English (keeping what the script just wrote), then translate it again.
                    self.records.forEach(function (rec) {
                        const node = rec.type === "text" ? rec.node : rec.el;
                        if (!node || rec.type === "title") return;
                        if (u.contains(node)) {
                            if (rec.type === "text" && node.nodeValue !== rec.tr) { self.textRec.delete(node); self.records.delete(rec); return; }
                            self.restoreRecord(rec);
                            self.records.delete(rec);
                        }
                    });
                    self.visit(u);
                    self.translateAttrs(u);
                    if (u.parentElement && (u.hasAttribute("placeholder") || u.hasAttribute("title") || u.tagName === "INPUT")) {
                        self.translateAttrs(u.parentElement);
                    }
                });
            });
        },

        /** Tooling: every translatable key on the current page, with counts. */
        collectAll: function () {
            this.collecting = new Map();
            try {
                this.visit(document.body);
                this.translateAttrs(document.body);
                this.translateTitle();
                return Array.from(this.collecting.entries());
            } finally {
                this.collecting = null;
            }
        }
    };

    const LSI_Lang = {
        currentLang: DEFAULT_LANG,
        originalCache: new Map(),

        /**
         * Initialize the language manager
         */
        init: function () {
            const lang = this.detectLanguage();
            this.mountSelector();
            this.setLanguage(lang, false);
            this.bindEvents();
            this.hookModeController();
        },

        /**
         * Detect language from URL param -> localStorage -> default ('en')
         */
        detectLanguage: function () {
            // 1. URL search param (?lang=mr)
            if (window.location && window.location.search) {
                const params = new URLSearchParams(window.location.search);
                const pLang = (params.get("lang") || "").toLowerCase();
                if (SUPPORTED_LANGS.includes(pLang)) {
                    this.persistLanguage(pLang);
                    return pLang;
                }
            }

            // 2. LocalStorage / SessionStorage
            try {
                if (window.localStorage) {
                    const stored = window.localStorage.getItem(STORAGE_KEY);
                    if (stored && SUPPORTED_LANGS.includes(stored)) {
                        return stored;
                    }
                }
                if (window.sessionStorage) {
                    const sessionStored = window.sessionStorage.getItem(STORAGE_KEY);
                    if (sessionStored && SUPPORTED_LANGS.includes(sessionStored)) {
                        return sessionStored;
                    }
                }
            } catch (e) {
                console.warn("[LSI_Lang] Storage access error:", e);
            }

            return DEFAULT_LANG;
        },

        /**
         * Persist language choice in localStorage
         */
        persistLanguage: function (lang) {
            try {
                if (window.localStorage) window.localStorage.setItem(STORAGE_KEY, lang);
                if (window.sessionStorage) window.sessionStorage.setItem(STORAGE_KEY, lang);
            } catch (e) {
                console.warn("[LSI_Lang] Storage write error:", e);
            }
        },

        /**
         * Get current active language
         */
        getLanguage: function () {
            return this.currentLang;
        },

        /**
         * Set language and translate page
         */
        setLanguage: function (lang, persist = true) {
            if (!SUPPORTED_LANGS.includes(lang)) lang = DEFAULT_LANG;
            this.currentLang = lang;

            if (persist) {
                this.persistLanguage(lang);
            }

            // Set document lang attribute
            if (document.documentElement) {
                document.documentElement.setAttribute("lang", lang);
            }

            // Update button active state
            this.updateSelectorButtons(lang);

            // Translate all elements
            this.translateDOM(lang);

            // Dispatch custom event for dynamic components (calculators, charts, etc.)
            try {
                const event = new CustomEvent("lsi:languageChanged", { detail: { lang: lang } });
                window.dispatchEvent(event);
            } catch (e) {}
        },

        /**
         * Dynamically mount the language selector bar below the navigation bar
         */
        mountSelector: function () {
            if (document.getElementById("siteLangBar")) {
                return;
            }

            const bar = document.createElement("div");
            bar.className = "site-lang-bar";
            bar.id = "siteLangBar";
            bar.setAttribute("aria-label", "Language Selection");
            bar.innerHTML = `
                <div class="container d-flex justify-content-end align-items-center">
                    <div class="lang-selector-wrap" role="group" aria-label="Language Selector">
                        <button type="button" class="lang-btn" data-lang="en" aria-label="Switch language to English">English</button>
                        <span class="lang-divider">|</span>
                        <button type="button" class="lang-btn" data-lang="mr" aria-label="Switch language to Marathi">मराठी</button>
                        <span class="lang-divider">|</span>
                        <button type="button" class="lang-btn" data-lang="hi" aria-label="Switch language to Hindi">हिंदी</button>
                    </div>
                </div>
            `;

            // Identify insertion point:
            // 1. Gateway page (index.html): directly after <header class="gateway-header">
            const gatewayHeader = document.querySelector(".gateway-header");
            if (gatewayHeader) {
                gatewayHeader.insertAdjacentElement("afterend", bar);
                return;
            }

            // 2. Standard page: directly after the navbar container (.navbar or .navbar wrapper)
            const navbarWrapper = document.querySelector(".navbar-wrapper") ||
                                  document.querySelector(".container-fluid.position-relative.p-0") ||
                                  document.querySelector("nav.navbar");
            if (navbarWrapper) {
                navbarWrapper.insertAdjacentElement("afterend", bar);
                return;
            }

            // Fallback: prepend to body
            if (document.body) {
                document.body.insertAdjacentElement("afterbegin", bar);
            }
        },

        /**
         * Update active highlight on language selector buttons
         */
        updateSelectorButtons: function (lang) {
            const buttons = document.querySelectorAll(".lang-btn");
            buttons.forEach(function (btn) {
                const btnLang = btn.getAttribute("data-lang");
                if (btnLang === lang) {
                    btn.classList.add("active");
                    btn.setAttribute("aria-pressed", "true");
                } else {
                    btn.classList.remove("active");
                    btn.setAttribute("aria-pressed", "false");
                }
            });
        },

        /**
         * Translate all user-facing DOM elements
         */
        translateDOM: function (lang) {
            const self = this;
            Phrases.guard(function () { self.translateKeyed(lang); });

            // Everything without a data-i18n key is handled by the phrase engine.
            Phrases.keyedSkip = function (el) { return self.hasKeyedTranslation(el); };
            if (lang === "en") {
                Phrases.translateAll("en");
                return;
            }
            Phrases.ensureLoaded().then(function () {
                if (self.currentLang === lang) Phrases.translateAll(lang);
            });
        },

        /** True when a data-i18n element is fully handled by the keyed dictionaries. */
        hasKeyedTranslation: function (el) {
            const key = el.getAttribute("data-i18n");
            if (key === "topbar.switch" || key === "nav.switchBusiness") return true;
            const t = window.LSI_Translations || {};
            return !!(t.hi && t.hi[key] && t.mr && t.mr[key]);
        },

        /**
         * Translate a single English string for scripts (alerts, chart labels...).
         * Returns the input unchanged in English or when no translation exists.
         */
        t: function (text) {
            if (this.currentLang === "en") return text;
            return Phrases.lookup(norm(text)) || text;
        },

        translateKeyed: function (lang) {
            const dict = (window.LSI_Translations && window.LSI_Translations[lang]) || {};
            const self = this;

            // 1. Translate elements with data-i18n attribute
            const i18nElements = document.querySelectorAll("[data-i18n]");
            i18nElements.forEach(function (el) {
                const key = el.getAttribute("data-i18n");

                // Dynamic business switch button label depends on current website
                if (key === "topbar.switch" || key === "nav.switchBusiness") {
                    const isMf = (window.LSI_Mode && typeof window.LSI_Mode.getMode === "function" && window.LSI_Mode.getMode() === "mutual-fund") ||
                                 (document.documentElement && document.documentElement.classList.contains("mode-mutual-fund")) ||
                                 (document.body && document.body.classList.contains("mode-mutual-fund")) ||
                                 (window.location && /investments\.html|financial-planning\.html|insurance\.html|sip\.html|swp\.html/i.test(window.location.pathname));
                    
                    const mfSpan = el.querySelector(".mode-mf-only");
                    const acadSpan = el.querySelector(".mode-academy-only");

                    let mfLabel = "Share Market Class";
                    let acadLabel = "Invest & Wealth Manager";
                    if (lang === "mr") {
                        mfLabel = "शेअर मार्केट क्लास";
                        acadLabel = "इन्व्हेस्ट आणि वेल्थ मॅनेजर";
                    } else if (lang === "hi") {
                        mfLabel = "शेयर मार्केट क्लास";
                        acadLabel = "इन्वेस्ट और वेल्थ मैनेजर";
                    }

                    if (mfSpan && acadSpan) {
                        mfSpan.innerHTML = '<i class="fas fa-th-large me-1"></i> ' + mfLabel;
                        acadSpan.innerHTML = '<i class="fas fa-th-large me-1"></i> ' + acadLabel;
                        return;
                    }

                    const icon = el.querySelector("i.fas, i.fab, i.far, i.fa, i.bi");
                    const iconHtml = icon ? icon.outerHTML : '<i class="fas fa-th-large me-1"></i>';
                    const activeLabel = isMf ? mfLabel : acadLabel;
                    const fullHtml = iconHtml + " " + activeLabel;
                    self.originalCache.set(el, fullHtml);
                    el.innerHTML = fullHtml;
                    return;
                }

                let translated = dict[key];

                // Cache original English innerHTML once
                if (!self.originalCache.has(el)) {
                    self.originalCache.set(el, el.innerHTML);
                }

                if (lang === "en") {
                    // Restore original HTML
                    el.innerHTML = self.originalCache.get(el);
                    return;
                }

                if (!translated) {
                    return;
                }

                // If element has leading/trailing icons or badges, preserve them!
                const icon = el.querySelector("i.fas, i.fab, i.far, i.fa, i.bi");
                if (icon && !translated.includes("<i")) {
                    const iconClone = icon.cloneNode(true);
                    el.innerHTML = "";
                    el.appendChild(iconClone);
                    el.appendChild(document.createTextNode(" " + translated));
                } else {
                    el.innerHTML = translated;
                }
            });

            // 2. Translate placeholders (data-i18n-placeholder)
            const placeholderElements = document.querySelectorAll("[data-i18n-placeholder]");
            placeholderElements.forEach(function (el) {
                const key = el.getAttribute("data-i18n-placeholder");
                const translated = dict[key];
                if (translated) {
                    el.setAttribute("placeholder", translated);
                }
            });

            // 3. Translate titles / tooltips (data-i18n-title)
            const titleElements = document.querySelectorAll("[data-i18n-title]");
            titleElements.forEach(function (el) {
                const key = el.getAttribute("data-i18n-title");
                const translated = dict[key];
                if (translated) {
                    el.setAttribute("title", translated);
                }
            });

            // Untagged navigation links are now covered by the phrase engine
            // (which also restores them on switching back to English).
        },

        /**
         * Fallback & automatic translation for standard repetitive UI labels
         */
        translateCommonUI: function (lang, dict) {
            if (lang === "en") return;

            // Translate navbar links by exact href matching
            const navLinkMap = {
                'a[href="home.html"]': "nav.home",
                'a[href="about.html"]': "nav.about",
                'a[href="founder.html"]': "nav.founder",
                'a[href="courses.html"]': "nav.course",
                'a[href="sip.html"]': "nav.sipTab",
                'a[href="swp.html"]': "nav.swpTab",
                'a[href="investments.html"]': "nav.investments",
                'a[href="stories.html"]': "nav.stories",
                'a[href="testimonials.html"]': "nav.testimonials",
                'a[href="blog.html"]': "nav.blog",
                'a[href="store.html"]': "nav.store",
                'a[href="contact.html"]': "nav.contact"
            };

            const self = this;
            Object.keys(navLinkMap).forEach(function (selector) {
                const key = navLinkMap[selector];
                const text = dict[key];
                if (!text) return;

                document.querySelectorAll(".navbar-nav " + selector).forEach(function (link) {
                    if (link.getAttribute("data-i18n")) return; // already handled
                    if (!self.originalCache.has(link)) {
                        self.originalCache.set(link, link.innerHTML);
                    }
                    const icon = link.querySelector("i");
                    if (icon) {
                        link.innerHTML = icon.outerHTML + " " + text;
                    } else {
                        link.textContent = text;
                    }
                });
            });
        },

        /**
         * Bind click events on language selector buttons
         */
        bindEvents: function () {
            const self = this;
            document.addEventListener("click", function (e) {
                const btn = e.target.closest(".lang-btn");
                if (!btn) return;
                e.preventDefault();
                const lang = btn.getAttribute("data-lang");
                if (lang) {
                    self.setLanguage(lang, true);
                }
            });
        },

        /**
         * Coordinate with LSI_Mode so that switching modes preserves translations
         */
        hookModeController: function () {
            const self = this;
            // Listen for window resize or mode changes
            window.addEventListener("lsi:modeChanged", function () {
                self.translateDOM(self.currentLang);
            });

            // Also check periodically or on jQuery ready
            if (typeof window.jQuery !== "undefined") {
                window.jQuery(function () {
                    self.translateDOM(self.currentLang);
                });
            }
        }
    };

    // Auto-init on DOM ready
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function () {
            LSI_Lang.init();
        });
    } else {
        LSI_Lang.init();
    }

    LSI_Lang.phrases = Phrases;
    window.LSI_Lang = LSI_Lang;

})(typeof window !== "undefined" ? window : globalThis, typeof document !== "undefined" ? document : null);

