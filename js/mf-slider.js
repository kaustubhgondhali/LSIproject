/**
 * LORD SAI — MUTUAL FUND HOMEPAGE SLIDER DATA LOADER (js/mf-slider.js)
 *
 * Fetches admin-managed slider images and swaps them into the EXISTING Mutual Fund hero
 * carousel markup on home.html (#mfCarouselTrack). The carousel engine (initMfHeroSlider in
 * js/main.js) and its CSS, animation, transition, arrows, dots, autoplay and touch-swipe logic
 * are completely untouched — only the image data source becomes dynamic instead of hardcoded.
 *
 * Guarantees:
 *  - Every image is loaded (validated) BEFORE a slide is created for it. A deleted / 404 image
 *    therefore never produces an empty slide — it is simply left out of the slide list.
 *  - Works regardless of whether the engine has already initialised: if it has, the previous
 *    instance is torn down (track.__mfSliderDestroy) and re-initialised on the new slide set, so
 *    slide counts, clones, dots and autoplay positions can never go stale.
 *  - If the backend is unreachable, or no active image loads, the slides already hardcoded in
 *    home.html are left exactly as they are, so the homepage never goes blank.
 *
 * Mutual-Fund-only; the Share Market homepage has no equivalent and is never touched.
 * Load order: this file must appear BEFORE js/main.js.
 */
(function (window, document) {
    "use strict";

    var API = (window.LSI_CONFIG && window.LSI_CONFIG.API_BASE) || "/api";
    // If validating the admin-managed images takes longer than this, the engine starts on the
    // static fallback slides and the validated set is swapped in (and re-initialised) when ready.
    var INIT_GUARD_MS = 15000;

    function mediaUrl(path) {
        // Admin uploads are stored as "images/<uuid>.ext" and served by the API; the original
        // hardcoded slides use static "img/..." paths, which are used as-is.
        return path && path.indexOf("images/") === 0 ? API + "/public/" + path : path;
    }

    function esc(s) {
        return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }

    /** Resolves to the image record if its file really loads, or null if it is missing/broken.
     *  Browsers always fire onload or onerror, so no artificial timeout drops a slow-but-valid image. */
    function validate(image) {
        return new Promise(function (resolve) {
            var url = mediaUrl(image.imagePath);
            if (!url) { resolve(null); return; }
            var probe = new Image();
            probe.onload = function () { resolve(image); };
            probe.onerror = function () { resolve(null); };
            probe.src = url;
        });
    }

    /** Exactly the same per-slide markup already used in home.html's hardcoded slides. */
    function buildSlideHtml(image, index) {
        var url = mediaUrl(image.imagePath);
        var alt = image.altText || image.title || "Mutual Fund Highlight";
        return '<div class="mf-carousel-slide" data-slide-index="' + index + '">' +
            '<div class="mf-slide-bg" style="background-image: url(\'' + esc(url) + '\');" aria-hidden="true"></div>' +
            '<div class="mf-slide-image" style="background-image: url(\'' + esc(url) + '\');" role="img" aria-label="' + esc(alt) + '"></div>' +
            '</div>';
    }

    function applyImages(images) {
        var track = document.getElementById("mfCarouselTrack");
        if (!track || !images.length) {
            return; // Keep the existing hardcoded slides — safe fallback, homepage never breaks.
        }
        // If the engine already ran (e.g. this data arrived after DOM ready), tear it down first so
        // its slide count, clones, dots and timers are rebuilt from the new list.
        var alreadyInitialised = track.getAttribute("data-slider-initialized") === "true";
        if (alreadyInitialised && typeof track.__mfSliderDestroy === "function") {
            track.__mfSliderDestroy();
        }
        track.innerHTML = images.map(buildSlideHtml).join("");
        var dots = document.getElementById("mfSliderDots");
        if (dots) { dots.innerHTML = ""; } // the engine regenerates one dot per slide (none for a single slide)
        if (alreadyInitialised && typeof window.initMfHeroSlider === "function") {
            window.initMfHeroSlider();
        }
    }

    var dataApplied = fetch(API + "/public/mutual-fund/slider-images", { cache: "no-store" })
        .then(function (response) { return response.json(); })
        .then(function (result) {
            if (!result || !result.success || !Array.isArray(result.data)) { return []; }
            return Promise.all(result.data.map(validate));
        })
        .then(function (checked) {
            applyImages(checked.filter(function (img) { return img !== null; }));
        })
        .catch(function () {
            // Backend offline or request failed: do nothing, existing markup stays as-is.
        });

    // Exposed so js/main.js's initMfHeroSlider() waits for this before reading the slide list.
    // Resolves when the validated slides are in place, or after INIT_GUARD_MS on a slow network —
    // in which case the engine starts on the hardcoded slides and applyImages() re-initialises it
    // once the real list arrives. Still initialises normally if this script is missing from a page.
    window.mfSliderReady = Promise.race([
        dataApplied,
        new Promise(function (resolve) { setTimeout(resolve, INIT_GUARD_MS); })
    ]);
})(window, document);
