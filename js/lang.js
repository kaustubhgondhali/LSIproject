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
            const dict = (window.LSI_Translations && window.LSI_Translations[lang]) || {};
            const enDict = (window.LSI_Translations && window.LSI_Translations.en) || {};
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

            // 4. Translate common navigation items automatically if not tagged
            this.translateCommonUI(lang, dict);
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

    window.LSI_Lang = LSI_Lang;

})(typeof window !== "undefined" ? window : globalThis, typeof document !== "undefined" ? document : null);

