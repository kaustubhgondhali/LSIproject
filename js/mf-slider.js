/**
 * LORD SAI — MUTUAL FUND HOMEPAGE SLIDER (js/mf-slider.js)
 *
 * The Mutual Fund homepage hero carousel (#mfCarouselTrack in home.html) uses the five
 * static project image files directly from the img/ folder:
 *   1. img/mf-hero-child-plan.jpg
 *   2. img/mf-hero-retirement.jpg
 *   3. img/mf-hero-sip.jpg
 *   4. img/mf-hero-own-home.jpg
 *   5. img/mf-hero-financial-goals.jpg
 *
 * Dynamic fetching and swapping from /api/public/mutual-fund/slider-images has been disabled
 * so the homepage slider strictly uses the local Git-tracked project image files.
 * This eliminates network requests, backend dependencies, and any potential race conditions.
 *
 * The slider engine in js/main.js (initMfHeroSlider) handles autoplay, arrows, dots,
 * transitions, touch-swipe, and responsive layout across desktop, tablet, and mobile.
 */
(function (window) {
    "use strict";

    // Immediate resolution: signals to js/main.js that the static project slides in home.html
    // are ready for initialization immediately without waiting on network calls or backend services.
    window.mfSliderReady = Promise.resolve();
})(window);
