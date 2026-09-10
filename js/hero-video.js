/**
 * LORD SAI INVESTMENT & SHARE MARKET ACADEMY
 * HOME HERO BACKGROUND VIDEO CONTROLLER (js/hero-video.js)
 *
 * Lightweight, dependency-free controller for the Academy hero background
 * video (Bull vs Bear market visual). Responsibilities:
 *   1. Respect prefers-reduced-motion -> never force playback.
 *   2. Pause the video when it is scrolled out of view / tab is hidden,
 *      to save battery & CPU (resume automatically when back in view).
 *   3. Gracefully handle browsers that block autoplay.
 *
 * This script is intentionally isolated from the (removed) scroll-driven
 * animation system: it never reads scroll position and never scrubs,
 * pins, or ties video playback to scroll in any way.
 */
(function () {
    "use strict";

    var video = document.getElementById("heroBgVideo");
    if (!video) return;

    var reduceMotionQuery = window.matchMedia
        ? window.matchMedia("(prefers-reduced-motion: reduce)")
        : null;

    function prefersReducedMotion() {
        return !!(reduceMotionQuery && reduceMotionQuery.matches);
    }

    function safePlay() {
        if (prefersReducedMotion()) return;
        var playPromise = video.play();
        if (playPromise && typeof playPromise.catch === "function") {
            playPromise.catch(function () {
                /* Autoplay blocked by browser policy - poster image remains visible. */
            });
        }
    }

    function safePause() {
        if (!video.paused) {
            video.pause();
        }
    }

    // Reduced-motion users get a static poster frame; do not initialize playback at all.
    if (prefersReducedMotion()) {
        safePause();
        return;
    }

    // Pause/resume based on visibility in the viewport (not scroll position).
    if ("IntersectionObserver" in window) {
        var observer = new IntersectionObserver(
            function (entries) {
                entries.forEach(function (entry) {
                    if (entry.isIntersecting) {
                        safePlay();
                    } else {
                        safePause();
                    }
                });
            },
            { threshold: 0.1 }
        );
        observer.observe(video);
    } else {
        safePlay();
    }

    // Pause when the browser tab itself is hidden.
    document.addEventListener("visibilitychange", function () {
        if (document.hidden) {
            safePause();
        } else if (!prefersReducedMotion()) {
            safePlay();
        }
    });

    // If the user's motion preference changes mid-session, react immediately.
    if (reduceMotionQuery && typeof reduceMotionQuery.addEventListener === "function") {
        reduceMotionQuery.addEventListener("change", function (e) {
            if (e.matches) {
                safePause();
            } else {
                safePlay();
            }
        });
    }
})();
