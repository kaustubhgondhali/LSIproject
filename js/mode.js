/**
 * LORD SAI SHARE MARKET CLASSES
 * WEBSITE MODE MANAGEMENT & PRESENTATION CONTROLLER (js/mode.js)
 * 
 * Manages two presentation and navigation modes:
 *  1. 'academy'     -> Shows Academy content & Course tab; Hides Investments tab
 *  2. 'mutual-fund' -> Shows Mutual Fund content & Investments tab; Hides Course tab
 */

(function (window, $) {
    "use strict";

    const STORAGE_KEY = "lsi_site_mode";
    const DEFAULT_MODE = "academy";

    const LSI_Mode = {
        activeMode: null,

        /**
         * Get the current active mode ('academy' or 'mutual-fund')
         */
        getMode: function () {
            // 1. Dedicated pages have absolute priority
            if (window.location && window.location.pathname) {
                const path = window.location.pathname.toLowerCase();
                if (path.endsWith("investments.html") || path.endsWith("financial-planning.html") || path.endsWith("insurance.html") || path.endsWith("sip.html") || path.endsWith("swp.html")) {
                    this.activeMode = "mutual-fund";
                    return "mutual-fund";
                }
                if (path.endsWith("courses.html") || path.endsWith("student-login.html") || path.endsWith("set-password.html") || path.endsWith("student-dashboard.html") || path.endsWith("admin-dashboard.html") || path.endsWith("automation-admin.html")) {
                    this.activeMode = "academy";
                    return "academy";
                }
            }

            if (this.activeMode) {
                return this.activeMode;
            }

            // 2. Check URL query param
            if (window.location && window.location.search) {
                const urlParams = new URLSearchParams(window.location.search);
                const paramMode = urlParams.get("mode");
                if (paramMode) {
                    const normalized = (paramMode.toLowerCase() === "mf" || paramMode.toLowerCase() === "mutual-fund") ? "mutual-fund" : "academy";
                    this.activeMode = normalized;
                    if (window.sessionStorage) window.sessionStorage.setItem(STORAGE_KEY, normalized);
                    if (window.localStorage) window.localStorage.setItem(STORAGE_KEY, normalized);
                    return normalized;
                }
            }

            // 3. Check sessionStorage then localStorage
            try {
                if (window.sessionStorage) {
                    const sessionMode = window.sessionStorage.getItem(STORAGE_KEY);
                    if (sessionMode === "academy" || sessionMode === "mutual-fund") {
                        this.activeMode = sessionMode;
                        return sessionMode;
                    }
                }
                if (window.localStorage) {
                    const localMode = window.localStorage.getItem(STORAGE_KEY);
                    if (localMode === "academy" || localMode === "mutual-fund") {
                        this.activeMode = localMode;
                        return localMode;
                    }
                }
            } catch (e) {
                console.warn("Storage access error:", e);
            }

            this.activeMode = DEFAULT_MODE;
            return DEFAULT_MODE;
        },

        /**
         * Set the active mode and persist it
         */
        setMode: function (mode, reload) {
            const normalized = (mode === "mutual-fund" || mode === "mf") ? "mutual-fund" : "academy";
            this.activeMode = normalized;
            try {
                if (window.sessionStorage) {
                    window.sessionStorage.setItem(STORAGE_KEY, normalized);
                }
                if (window.localStorage) {
                    window.localStorage.setItem(STORAGE_KEY, normalized);
                }
            } catch (e) {
                console.warn("Storage write error:", e);
            }

            this.applyMode(normalized);


            if (reload && window.location) {
                const url = new URL(window.location.href);
                url.searchParams.set("mode", normalized);
                window.location.href = url.toString();
            }
        },

        /**
         * Apply visibility rules and CSS classes according to active mode
         */
        applyMode: function (mode) {
            const currentMode = mode || this.getMode();
            const root = typeof document !== "undefined" ? document.documentElement : null;
            const body = typeof document !== "undefined" ? document.body : null;

            if (root && root.classList) {
                root.classList.remove("mode-academy");
                root.classList.remove("mode-mutual-fund");
                root.classList.add(currentMode === "mutual-fund" ? "mode-mutual-fund" : "mode-academy");
            }
            if (body && body.classList) {
                body.classList.remove("mode-academy");
                body.classList.remove("mode-mutual-fund");
                body.classList.add(currentMode === "mutual-fund" ? "mode-mutual-fund" : "mode-academy");
            }


            // Apply DOM adjustments when jQuery is ready
            if (typeof $ !== "undefined") {
                $(function () {
                    LSI_Mode.updateDOM(currentMode);
                    if (window.LSI_Lang) window.LSI_Lang.translateDOM(window.LSI_Lang.getLanguage());
                });
            } else {
                // jQuery not loaded yet (mode.js included before jQuery on some
                // pages) - wait for it before touching the DOM.
                var waitForJQuery = function () {
                    if (typeof window.jQuery !== "undefined") {
                        $ = window.jQuery;
                        window.jQuery(function () {
                            LSI_Mode.updateDOM(currentMode);
                            if (window.LSI_Lang) window.LSI_Lang.translateDOM(window.LSI_Lang.getLanguage());
                        });
                    } else {
                        window.setTimeout(waitForJQuery, 50);
                    }
                };
                waitForJQuery();
            }
        },

        /**
         * Update navigation items, footer links, and section visibility
         */
        updateDOM: function (mode) {
            // If on gateway page, do not alter gateway cards
            const pathname = window.location ? (window.location.pathname || "") : "";
            const isGateway = pathname.endsWith("index.html") || pathname === "/" || pathname === "" || $(".gateway-wrapper").length > 0;
            if (isGateway) {
                return;
            }

            const currentMode = mode || this.getMode();

            // 1. Identify Course, SIP, SWP and Investment dropdowns & links
            const $courseNavItems = $('a[href^="courses.html"]').closest('.nav-item, .dropdown, li');
            const $courseDirectLinks = $('a[href^="courses.html"]');
            
            const $mfNavItems = $('a[href^="sip.html"], a[href^="swp.html"], a[href^="investments.html"]').closest('.nav-item, .dropdown, li');
            const $mfDirectLinks = $('a[href^="sip.html"], a[href^="swp.html"], a[href^="investments.html"]');

            if (currentMode === "academy") {
                // ACADEMY MODE:
                // SHOW: Course
                // HIDE: SIP, SWP, Investments
                $mfNavItems.addClass("d-none-mode").hide();
                $mfDirectLinks.addClass("d-none-mode").hide();

                $courseNavItems.removeClass("d-none-mode").show();
                $courseDirectLinks.removeClass("d-none-mode").show();

                $('.mode-mf-only, [data-mode="mutual-fund"]').hide();
                $('.mode-academy-only, [data-mode="academy"]').show();

                // Update Mode Badges if present
                $('.current-mode-label').text("Academy Portal");
                $('.current-mode-badge').removeClass("bg-success").addClass("bg-primary").html('<i class="fas fa-graduation-cap me-1"></i> Academy Mode');

                // Dynamic document title refinement
                if (document && document.title && !document.title.includes("Academy")) {
                    document.title = "Lord Sai Share Market Academy | " + document.title;
                }
            } else {
                // MUTUAL FUND DISTRIBUTION MODE:
                // SHOW: SIP, SWP, Investments
                // HIDE: Course
                $courseNavItems.addClass("d-none-mode").hide();
                $courseDirectLinks.addClass("d-none-mode").hide();

                $mfNavItems.removeClass("d-none-mode").show();
                $mfDirectLinks.removeClass("d-none-mode").show();

                $('.mode-academy-only, [data-mode="academy"]').hide();
                $('.mode-mf-only, [data-mode="mutual-fund"]').show();

                // Update Mode Badges if present
                $('.current-mode-label').text("Mutual Fund Distribution");
                $('.current-mode-badge').removeClass("bg-primary").addClass("bg-success").html('<i class="fas fa-chart-pie me-1"></i> Mutual Fund Mode');

                // Dynamic document title refinement
                if (document && document.title && !document.title.includes("Mutual Fund")) {
                    document.title = "Mutual Fund Distribution (ARN-280789) | " + document.title;
                }
            }

            // 2. Intercept and convert any legacy index.html links to home.html with active mode preserved (excluding switch buttons)
            $('a[href^="index.html"]:not(.gateway-link):not(.topbar-switch-btn):not(.navbar-switch-item):not([data-i18n*="switch"])').each(function () {
                const href = $(this).attr("href");
                const hash = href.includes("#") ? "#" + href.split("#")[1] : "";
                $(this).attr("href", "home.html?mode=" + currentMode + hash);
            });

            // 3. Keep mode parameter on internal dual-mode page links (.html, .html#hash, etc.)
            $('a[href*=".html"]:not(.gateway-link):not(.topbar-switch-btn):not(.navbar-switch-item):not([data-i18n*="switch"]):not(.lsi-admin-link)').each(function () {
                const href = $(this).attr("href");
                if (href && !href.startsWith("http") && !href.startsWith("//") && !href.startsWith("#")
                        && !href.includes("?mode=") && !href.includes("&mode=")) {
                    const hashParts = href.split("#");
                    const path = hashParts[0].toLowerCase();
                    const hash = hashParts[1] ? "#" + hashParts[1] : "";

                    // Never append academy mode to dedicated mutual-fund pages
                    if (currentMode === "academy" && (path.endsWith("investments.html") || path.endsWith("financial-planning.html") || path.endsWith("insurance.html") || path.endsWith("sip.html") || path.endsWith("swp.html"))) {
                        return;
                    }
                    // Never append mutual-fund mode to dedicated academy pages
                    if (currentMode === "mutual-fund" && (path.endsWith("courses.html") || path.endsWith("student-login.html") || path.endsWith("set-password.html") || path.endsWith("student-dashboard.html"))) {
                        return;
                    }
                    if (path === "index.html") {
                        return;
                    }
                    const joiner = hashParts[0].includes("?") ? "&" : "?";
                    $(this).attr("href", hashParts[0] + joiner + "mode=" + currentMode + hash);
                }
            });

            // 4. Inject or update "Switch Business" button in Topbar and Navbar
            LSI_Mode.renderSwitchButton(currentMode);

            // 5. Refresh animations safely and independently
            if (typeof window !== "undefined") {
                window.dispatchEvent(new Event('resize'));
                if (window.LiveCandlestickBackground && typeof window.LiveCandlestickBackground.refresh === "function") {
                    window.LiveCandlestickBackground.refresh();
                }
                if (typeof window.initLiveDisclaimerTicker === "function") {
                    window.initLiveDisclaimerTicker();
                }
            }
        },

        /**
         * Render / update the business switch button label based on active website:
         * - Mutual Fund website -> "Share Market Class"
         * - Share Market website -> "Invest & Wealth Manager"
         * Keeps the exact icon (<i class="fas fa-th-large me-1"></i>) and href ("index.html").
         */
        renderSwitchButton: function (currentMode) {
            const isMf = currentMode === "mutual-fund";
            const btnText = isMf ? "Share Market Class" : "Invest & Wealth Manager";
            const btnTitle = isMf ? "Switch to Share Market Class" : "Switch to Invest & Wealth Manager";

            // 1. In Topbar (inject if missing)
            if ($('.topbar').length && !$('.topbar-switch-btn').length) {
                const switchHtml = `
                    <a href="index.html" class="btn btn-sm btn-outline-warning rounded-pill py-1 px-3 me-3 topbar-switch-btn" title="${btnTitle}" data-i18n="topbar.switch">
                        <i class="fas fa-th-large me-1"></i> ${btnText}
                    </a>
                `;
                const $target = $('.topbar .col-lg-5 .d-inline-flex');
                if ($target.length) {
                    $target.prepend(switchHtml);
                }
            }

            // 2. In Navbar for mobile and desktop (inject if missing)
            if ($('.navbar-nav').length && !$('.navbar-switch-item').length) {
                const navSwitchHtml = `
                    <a href="index.html" class="nav-item nav-link navbar-switch-item text-warning fw-bold d-lg-none" title="${btnTitle}" data-i18n="nav.switchBusiness">
                        <i class="fas fa-th-large me-1"></i> ${btnText}
                    </a>
                `;
                $('.navbar-nav').append(navSwitchHtml);
            }

            // 3. Update all existing switch buttons across the page
            $('.topbar-switch-btn, .navbar-switch-item, [data-i18n="topbar.switch"], [data-i18n="nav.switchBusiness"]').each(function () {
                const $btn = $(this);
                $btn.attr('title', btnTitle);
                const $mfSpan = $btn.find('.mode-mf-only');
                const $acadSpan = $btn.find('.mode-academy-only');
                if ($mfSpan.length && $acadSpan.length) {
                    $mfSpan.html('<i class="fas fa-th-large me-1"></i> Share Market Class');
                    $acadSpan.html('<i class="fas fa-th-large me-1"></i> Invest & Wealth Manager');
                } else {
                    const $icon = $btn.find('i');
                    const iconHtml = $icon.length ? $icon.prop('outerHTML') : '<i class="fas fa-th-large me-1"></i>';
                    $btn.html(iconHtml + ' ' + btnText);
                }
            });
        }
    };

    // Auto-apply mode immediately on script evaluation
    LSI_Mode.applyMode();

    window.LSI_Mode = LSI_Mode;


})(typeof window !== "undefined" ? window : globalThis, typeof window !== "undefined" ? window.jQuery : undefined);

