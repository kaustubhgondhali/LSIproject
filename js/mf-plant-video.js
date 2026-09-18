/**
 * LORD SAI INVESTMENT & SHARE MARKET ACADEMY
 * MUTUAL FUND PAGES — "PLANT GROWING MONEY" VIDEO CONTROLLER (js/mf-plant-video.js)
 *
 * Lightweight, dependency-free controller for the Mutual Fund section
 * background video (used on Investments, SIP, Financial Planning,
 * Insurance and Founder — Mutual Fund mode — pages only).
 *
 * This is a standalone file, intentionally separate from js/hero-video.js
 * (which drives the Home page Academy hero video) so that Home page
 * behaviour is never touched by this change.
 *
 * Responsibilities:
 *   1. Respect prefers-reduced-motion -> never force playback.
 *   2. Pause each video when scrolled out of view / tab is hidden,
 *      to save battery & CPU (resume automatically when back in view).
 *   3. Gracefully handle browsers that block autoplay.
 *   4. Support multiple independent video instances on the same page
 *      (e.g. one in the header hero, without requiring unique IDs).
 *
 * This script never reads scroll position and never scrubs, pins, or
 * ties video playback to scroll in any way — playback is purely
 * autoplay/loop based, per the design requirements.
 */
(function () {
    "use strict";

    var videos = document.querySelectorAll(".mf-plant-video-el");
    if (!videos || videos.length === 0) return;

    var reduceMotionQuery = window.matchMedia
        ? window.matchMedia("(prefers-reduced-motion: reduce)")
        : null;

    function prefersReducedMotion() {
        return !!(reduceMotionQuery && reduceMotionQuery.matches);
    }

    function safePlay(video) {
        if (prefersReducedMotion()) return;
        var playPromise = video.play();
        if (playPromise && typeof playPromise.catch === "function") {
            playPromise.catch(function () {
                /* Autoplay blocked by browser policy - poster image remains visible. */
            });
        }
    }

    function safePause(video) {
        if (!video.paused) {
            video.pause();
        }
    }

    Array.prototype.forEach.call(videos, function (video) {
        // Reduced-motion users get a static poster frame; do not initialize playback at all.
        if (prefersReducedMotion()) {
            safePause(video);
            return;
        }

        // Pause/resume based on viewport visibility (not scroll position).
        if ("IntersectionObserver" in window) {
            var observer = new IntersectionObserver(
                function (entries) {
                    entries.forEach(function (entry) {
                        if (entry.isIntersecting) {
                            safePlay(video);
                        } else {
                            safePause(video);
                        }
                    });
                },
                { threshold: 0.1 }
            );
            observer.observe(video);
        } else {
            safePlay(video);
        }
    });

    // Pause all instances when the browser tab itself is hidden.
    document.addEventListener("visibilitychange", function () {
        Array.prototype.forEach.call(videos, function (video) {
            if (document.hidden) {
                safePause(video);
            } else if (!prefersReducedMotion()) {
                safePlay(video);
            }
        });
    });

    // If the user's motion preference changes mid-session, react immediately.
    if (reduceMotionQuery && typeof reduceMotionQuery.addEventListener === "function") {
        reduceMotionQuery.addEventListener("change", function (e) {
            Array.prototype.forEach.call(videos, function (video) {
                if (e.matches) {
                    safePause(video);
                } else {
                    safePlay(video);
                }
            });
        });
    }
})();
