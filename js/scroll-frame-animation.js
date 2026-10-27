/* ==========================================================================
   Unified Ultra-Wide Cinematic Frame Animation — Academy Page
   LORD SAI SHARE MARKET CLASSES

   Single Canvas Architecture:
     - 100% Seamless panoramic composite rendered directly onto #scrollFrameCanvas.
     - Central 300-frame Bull vs Bear battle sequence (0..299).
     - Left Bullish Market Atmosphere: Deep electric navy fog, ascending cyan candlestick charts, subtle eagle aura.
     - Right Bearish Market Atmosphere: Deep emerald slate fog, descending green candlestick charts, subtle wolf aura.
     - Zero separate DOM panels, zero rectangular seams, zero vertical cut-offs.
     - Mathematical gradient feathering across boundary zones for an unbroken visual continuum.
     - High-DPI 1080p/4K canvas backing with 60/120fps physics lerp scrub.
   ========================================================================== */

(function () {
    "use strict";

    var TRACK_ID = "scrollFrameTrack";
    var LAYER_ID = "scrollFrameAnimationLayer";
    var CANVAS_ID = "scrollFrameCanvas";
    var HEADER_SELECTOR = ".navbar, .gateway-header";

    var FRAME_FOLDER = "img/scroll-animation/";
    var FRAME_PREFIX = "ezgif-frame-";
    var FRAME_EXT = ".jpg";
    var FRAME_COUNT = 300; // ezgif-frame-001.jpg ... ezgif-frame-300.jpg
    var FRAME_PAD = 3;
    var LAST_INDEX = FRAME_COUNT - 1;

    var LEFT_FLANK_URL = "img/bull-market-blue-eagle-flank.jpg";
    var RIGHT_FLANK_URL = "img/bear-market-green-wolf-flank.jpg";

    var NATIVE_WIDTH = 1920;
    var NATIVE_HEIGHT = 1080;

    var BACKGROUND_BATCH_SIZE = 12;

    // Calibrated scroll scrub distance for cinematic pacing
    function extraScrollDistanceForViewport() {
        var w = window.innerWidth || document.documentElement.clientWidth;
        if (w <= 575.98) return 4800; // Mobile: ~4800px gives ample room to observe every battle frame
        if (w <= 991.98) return 6200; // Tablet: ~6200px
        return 7800;                  // Desktop: ~7800px gives ~26px per frame for smooth, unhurried scrubbing
    }

    var trackEl = null;
    var layerEl = null;
    var canvas = null;
    var ctx = null;

    function getElements() {
        if (!trackEl) trackEl = document.getElementById(TRACK_ID);
        if (!layerEl) layerEl = document.getElementById(LAYER_ID);
        if (!canvas) canvas = document.getElementById(CANVAS_ID);
        if (canvas && !ctx) {
            ctx = canvas.getContext("2d", { alpha: false });
        }
        return !!(trackEl && layerEl && canvas && ctx);
    }

    function isAcademyMode() {
        if (window.LSI_Mode && typeof window.LSI_Mode.getMode === "function") {
            return window.LSI_Mode.getMode() === "academy";
        }
        if (document.body && document.body.classList.contains("mode-mutual-fund")) {
            return false;
        }
        if (document.body && document.body.classList.contains("mode-academy")) {
            return true;
        }
        if (document.documentElement && document.documentElement.classList.contains("mode-mutual-fund")) {
            return false;
        }
        if (document.documentElement && document.documentElement.classList.contains("mode-academy")) {
            return true;
        }
        return true;
    }

    function isElementVisible(el) {
        if (!el) return false;
        var style = window.getComputedStyle ? window.getComputedStyle(el) : el.currentStyle;
        if (style && (style.display === "none" || style.visibility === "hidden")) {
            return false;
        }
        var parent = el.parentElement;
        while (parent && parent !== document.body && parent !== document.documentElement) {
            var pStyle = window.getComputedStyle ? window.getComputedStyle(parent) : parent.currentStyle;
            if (pStyle && (pStyle.display === "none" || pStyle.visibility === "hidden")) {
                return false;
            }
            parent = parent.parentElement;
        }
        if (el.offsetParent !== null) return true;
        var rect = el.getBoundingClientRect();
        return rect.width > 0 || rect.height > 0 || (style && style.display !== "none");
    }

    function getHeaderEl() {
        return document.querySelector(HEADER_SELECTOR);
    }

    function getStickyTopOffset() {
        var headerEl = getHeaderEl();
        if (headerEl) {
            var h = headerEl.offsetHeight || headerEl.getBoundingClientRect().height;
            if (h > 0) return Math.round(h);
        }
        return 75;
    }

    var frameState = new Array(FRAME_COUNT);
    var leftFlankDrawable = null;
    var rightFlankDrawable = null;

    var currentRenderedIndex = -1;
    var renderedProgress = 0;
    var targetProgress = 0;
    var isLerping = false;

    var supportsCreateImageBitmap = typeof window.createImageBitmap === "function";
    var supportsFetchPriority = (function () {
        try {
            return "fetchPriority" in new Image();
        } catch (e) {
            return false;
        }
    })();

    var requestIdle = window.requestIdleCallback
        ? window.requestIdleCallback.bind(window)
        : function (cb) {
            return window.setTimeout(function () {
                cb({ didTimeout: true, timeRemaining: function () { return 0; } });
            }, 50);
        };

    function pad(num, size) {
        var s = String(num);
        while (s.length < size) s = "0" + s;
        return s;
    }

    function frameUrl(index) {
        return FRAME_FOLDER + FRAME_PREFIX + pad(index, FRAME_PAD) + FRAME_EXT;
    }

    function clamp(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function isFrameLoaded(index) {
        var state = frameState[index];
        return !!(state && state.drawable);
    }

    function getDrawableDims(drawable) {
        if (typeof ImageBitmap !== "undefined" && drawable instanceof ImageBitmap) {
            return { width: drawable.width, height: drawable.height };
        }
        return { width: drawable.naturalWidth, height: drawable.naturalHeight };
    }

    function getNearestLoadedIndex(index) {
        if (isFrameLoaded(index)) return index;
        for (var d = 1; d <= LAST_INDEX; d++) {
            var lower = index - d;
            var upper = index + d;
            if (lower >= 0 && isFrameLoaded(lower)) return lower;
            if (upper <= LAST_INDEX && isFrameLoaded(upper)) return upper;
        }
        return -1;
    }

    function sizeCanvasToContainer() {
        if (!isElementVisible(layerEl)) return;
        var rect = layerEl.getBoundingClientRect();
        var dpr = Math.min(window.devicePixelRatio || 1, 2.5);

        var physicalWidth = Math.round(rect.width * dpr);
        var targetWidth = physicalWidth > NATIVE_WIDTH ? physicalWidth : NATIVE_WIDTH;
        var targetHeight = Math.round(targetWidth * (NATIVE_HEIGHT / NATIVE_WIDTH));

        if (canvas.width !== targetWidth || canvas.height !== targetHeight) {
            canvas.width = targetWidth;
            canvas.height = targetHeight;
        }
    }

    // Dynamic Atmospheric Floating Particles
    var particles = [];
    (function initParticles() {
        for (var i = 0; i < 36; i++) {
            var isLeft = i < 18;
            particles.push({
                x: isLeft ? (0.04 + Math.random() * 0.32) : (0.64 + Math.random() * 0.32),
                baseY: 0.15 + Math.random() * 0.70,
                speed: 0.08 + Math.random() * 0.16,
                radius: 1.2 + Math.random() * 2.2,
                color: isLeft ? "rgba(0, 180, 216, " : "rgba(46, 196, 182, ",
                alpha: 0.3 + Math.random() * 0.5,
                phase: Math.random() * Math.PI * 2
            });
        }
    })();

    // Main Unified Canvas Drawing Engine
    function drawUnifiedScene(drawable, progress) {
        var cw = canvas.width;
        var ch = canvas.height;
        if (!cw || !ch) return;

        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = "high";

        // 1. Panoramic Seamless Atmospheric Base Gradient
        var baseGrad = ctx.createLinearGradient(0, 0, cw, 0);
        baseGrad.addColorStop(0, "#010712");     // Deep electric navy
        baseGrad.addColorStop(0.18, "#03152a");  // Cyan atmospheric haze
        baseGrad.addColorStop(0.38, "#040a14");  // Soft navy bridge
        baseGrad.addColorStop(0.50, "#03060c");  // Central combat void
        baseGrad.addColorStop(0.62, "#031210");  // Soft emerald bridge
        baseGrad.addColorStop(0.82, "#011915");  // Emerald atmospheric haze
        baseGrad.addColorStop(1, "#010a08");     // Deep forest slate
        ctx.fillStyle = baseGrad;
        ctx.fillRect(0, 0, cw, ch);

        // 2. Atmospheric Radial Glow Cones (Behind Combatants)
        var leftGlow = ctx.createRadialGradient(cw * 0.18, ch * 0.50, 0, cw * 0.18, ch * 0.50, cw * 0.36);
        leftGlow.addColorStop(0, "rgba(0, 180, 216, 0.22)");
        leftGlow.addColorStop(0.6, "rgba(0, 119, 182, 0.08)");
        leftGlow.addColorStop(1, "rgba(0, 0, 0, 0)");
        ctx.fillStyle = leftGlow;
        ctx.fillRect(0, 0, cw * 0.5, ch);

        var rightGlow = ctx.createRadialGradient(cw * 0.82, ch * 0.50, 0, cw * 0.82, ch * 0.50, cw * 0.36);
        rightGlow.addColorStop(0, "rgba(46, 196, 182, 0.22)");
        rightGlow.addColorStop(0.6, "rgba(15, 159, 144, 0.08)");
        rightGlow.addColorStop(1, "rgba(0, 0, 0, 0)");
        ctx.fillStyle = rightGlow;
        ctx.fillRect(cw * 0.5, 0, cw * 0.5, ch);

        // 3. Left Flank: Bullish Market Environment
        if (leftFlankDrawable) {
            ctx.save();
            ctx.globalAlpha = 0.50 + Math.sin(progress * Math.PI) * 0.12;
            var lShift = -(1 - progress) * 16;
            var lWidth = cw * 0.44;
            ctx.drawImage(leftFlankDrawable, lShift, 0, lWidth, ch);

            // Feathered horizontal erasure toward center
            var lFade = ctx.createLinearGradient(cw * 0.24, 0, cw * 0.44, 0);
            lFade.addColorStop(0, "rgba(3, 6, 12, 0)");
            lFade.addColorStop(0.6, "rgba(3, 6, 12, 0.65)");
            lFade.addColorStop(1, "#03060c");
            ctx.fillStyle = lFade;
            ctx.fillRect(cw * 0.24, 0, cw * 0.20 + 2, ch);
            ctx.restore();
        }

        // 4. Right Flank: Bearish Market Environment
        if (rightFlankDrawable) {
            ctx.save();
            ctx.globalAlpha = 0.50 + Math.sin((progress + 0.25) * Math.PI) * 0.12;
            var rShift = (1 - progress) * 16;
            var rX = cw * 0.56 + rShift;
            var rWidth = cw * 0.44;
            ctx.drawImage(rightFlankDrawable, rX, 0, rWidth, ch);

            // Feathered horizontal erasure toward center
            var rFade = ctx.createLinearGradient(cw * 0.56, 0, cw * 0.76, 0);
            rFade.addColorStop(0, "#03060c");
            rFade.addColorStop(0.4, "rgba(3, 6, 12, 0.65)");
            rFade.addColorStop(1, "rgba(3, 6, 12, 0)");
            ctx.fillStyle = rFade;
            ctx.fillRect(cw * 0.56 - 2, 0, cw * 0.20 + 2, ch);
            ctx.restore();
        }

        // 5. Central 300-Frame Bull vs Bear Fight
        if (drawable) {
            ctx.save();
            ctx.globalAlpha = 1.0;
            ctx.drawImage(drawable, 0, 0, cw, ch);

            // Seamless Atmospheric Edge Blending (Dissolves any central video side borders)
            var leftSeamBlend = ctx.createLinearGradient(0, 0, cw * 0.26, 0);
            leftSeamBlend.addColorStop(0, "rgba(1, 7, 18, 0.82)");
            leftSeamBlend.addColorStop(0.45, "rgba(2, 16, 32, 0.35)");
            leftSeamBlend.addColorStop(1, "rgba(3, 6, 12, 0)");
            ctx.fillStyle = leftSeamBlend;
            ctx.fillRect(0, 0, cw * 0.26, ch);

            var rightSeamBlend = ctx.createLinearGradient(cw * 0.74, 0, cw, 0);
            rightSeamBlend.addColorStop(0, "rgba(3, 6, 12, 0)");
            rightSeamBlend.addColorStop(0.55, "rgba(1, 20, 16, 0.35)");
            rightSeamBlend.addColorStop(1, "rgba(1, 10, 8, 0.82)");
            ctx.fillStyle = rightSeamBlend;
            ctx.fillRect(cw * 0.74, 0, cw * 0.26, ch);
            ctx.restore();
        }

        // 6. Glowing Floating Market Sparks & Data Particles
        for (var p = 0; p < particles.length; p++) {
            var pt = particles[p];
            var py = (pt.baseY + Math.sin(progress * 4 + pt.phase) * 0.08) * ch;
            var px = pt.x * cw;
            var pAlpha = pt.alpha * (0.7 + Math.sin(progress * Math.PI + pt.phase) * 0.3);

            ctx.beginPath();
            ctx.arc(px, py, pt.radius, 0, Math.PI * 2);
            ctx.fillStyle = pt.color + pAlpha + ")";
            ctx.fill();
        }

        // 7. Global Cinematic Top & Bottom Vignette
        var vignette = ctx.createLinearGradient(0, 0, 0, ch);
        vignette.addColorStop(0, "rgba(2, 5, 12, 0.50)");
        vignette.addColorStop(0.12, "rgba(2, 5, 12, 0)");
        vignette.addColorStop(0.88, "rgba(2, 5, 12, 0)");
        vignette.addColorStop(1, "rgba(2, 5, 12, 0.50)");
        ctx.fillStyle = vignette;
        ctx.fillRect(0, 0, cw, ch);
    }

    // Direct, continuous, 100% smooth scroll-to-frame mapping:
    // Scroll progress 0%   -> Frame 001 (0)
    // Scroll progress 25%  -> Frame 075 (75)
    // Scroll progress 50%  -> Frame 150 (149)
    // Scroll progress 75%  -> Frame 225 (224)
    // Scroll progress 100% -> Frame 300 (299)
    function progressToFrameIndex(progress) {
        var t = clamp(progress, 0, 1);
        return clamp(Math.round(t * LAST_INDEX), 0, LAST_INDEX);
    }

    function renderFrameIndex(index) {
        if (!isAcademyMode() || !isElementVisible(trackEl)) return;
        var resolvedIndex = getNearestLoadedIndex(index);
        if (resolvedIndex === -1) return;
        currentRenderedIndex = resolvedIndex;
        drawUnifiedScene(frameState[resolvedIndex].drawable, renderedProgress);
    }

    function requestFrame(index, priority) {
        if (index < 0 || index > LAST_INDEX) return;
        if (frameState[index]) return;
        frameState[index] = { drawable: null };

        var img = new Image();
        img.decoding = "async";
        if (supportsFetchPriority) {
            img.fetchPriority = priority || "low";
        }

        img.onload = function () {
            var state = frameState[index];
            if (!state) return;
            state.drawable = img;
            if (supportsCreateImageBitmap) {
                window.createImageBitmap(img, {
                    premultiplyAlpha: "none"
                }).then(function (bitmap) {
                    if (frameState[index]) frameState[index].drawable = bitmap;
                    var curTarget = progressToFrameIndex(renderedProgress);
                    if (Math.abs(curTarget - index) <= 4 || currentRenderedIndex === -1 || (curTarget === 0 && index === 0) || (curTarget === LAST_INDEX && index === LAST_INDEX)) {
                        renderFrameIndex(curTarget);
                    }
                }).catch(function () {
                    var curTarget = progressToFrameIndex(renderedProgress);
                    if (Math.abs(curTarget - index) <= 4 || currentRenderedIndex === -1 || (curTarget === 0 && index === 0) || (curTarget === LAST_INDEX && index === LAST_INDEX)) {
                        renderFrameIndex(curTarget);
                    }
                });
            } else {
                var curTarget = progressToFrameIndex(renderedProgress);
                if (Math.abs(curTarget - index) <= 4 || currentRenderedIndex === -1 || (curTarget === 0 && index === 0) || (curTarget === LAST_INDEX && index === LAST_INDEX)) {
                    renderFrameIndex(curTarget);
                }
            }
        };

        img.onerror = function () {};
        img.src = frameUrl(index + 1);
    }

    function loadFlankAssets() {
        var leftImg = new Image();
        leftImg.onload = function () {
            if (supportsCreateImageBitmap) {
                window.createImageBitmap(leftImg, { premultiplyAlpha: "none", resizeQuality: "high" }).then(function (bmp) {
                    leftFlankDrawable = bmp;
                    if (currentRenderedIndex >= 0) renderFrameIndex(currentRenderedIndex);
                }).catch(function () {
                    leftFlankDrawable = leftImg;
                    if (currentRenderedIndex >= 0) renderFrameIndex(currentRenderedIndex);
                });
            } else {
                leftFlankDrawable = leftImg;
                if (currentRenderedIndex >= 0) renderFrameIndex(currentRenderedIndex);
            }
        };
        leftImg.src = LEFT_FLANK_URL;

        var rightImg = new Image();
        rightImg.onload = function () {
            if (supportsCreateImageBitmap) {
                window.createImageBitmap(rightImg, { premultiplyAlpha: "none", resizeQuality: "high" }).then(function (bmp) {
                    rightFlankDrawable = bmp;
                    if (currentRenderedIndex >= 0) renderFrameIndex(currentRenderedIndex);
                }).catch(function () {
                    rightFlankDrawable = rightImg;
                    if (currentRenderedIndex >= 0) renderFrameIndex(currentRenderedIndex);
                });
            } else {
                rightFlankDrawable = rightImg;
                if (currentRenderedIndex >= 0) renderFrameIndex(currentRenderedIndex);
            }
        };
        rightImg.src = RIGHT_FLANK_URL;
    }

    function buildPriorityQueue(centerIndex) {
        var queue = [];
        var seen = {};
        function add(i) {
            if (i >= 0 && i <= LAST_INDEX && !seen[i]) {
                seen[i] = true;
                queue.push(i);
            }
        }
        add(centerIndex);
        for (var d = 1; d <= LAST_INDEX; d++) {
            add(centerIndex + d);
            add(centerIndex - d);
        }
        return queue;
    }

    var backgroundQueue = [];
    var backgroundQueuePos = 0;
    var backgroundStarted = false;

    function processBackgroundBatch() {
        if (!isAcademyMode() || !isElementVisible(trackEl)) return;
        var count = 0;
        while (backgroundQueuePos < backgroundQueue.length && count < BACKGROUND_BATCH_SIZE) {
            requestFrame(backgroundQueue[backgroundQueuePos], "low");
            backgroundQueuePos++;
            count++;
        }
        if (backgroundQueuePos < backgroundQueue.length) {
            requestIdle(processBackgroundBatch, { timeout: 1000 });
        }
    }

    function startBackgroundPreload() {
        if (backgroundStarted || !isAcademyMode() || !isElementVisible(trackEl)) return;
        backgroundStarted = true;
        var center = progressToFrameIndex(targetProgress);
        backgroundQueue = buildPriorityQueue(center);
        backgroundQueuePos = 0;
        requestIdle(processBackgroundBatch, { timeout: 600 });
    }

    function sizeTrackAndStickyOffset() {
        if (!getElements() || !isAcademyMode() || !isElementVisible(trackEl)) return;
        var stickyTop = getStickyTopOffset();
        layerEl.style.top = stickyTop + "px";

        var panelHeight = layerEl.offsetHeight || layerEl.getBoundingClientRect().height || 450;
        var extra = extraScrollDistanceForViewport();
        trackEl.style.height = Math.round(panelHeight + extra) + "px";
    }

    function computeScrollProgress() {
        if (!getElements() || !isAcademyMode() || !isElementVisible(trackEl)) return 0;
        var trackRect = trackEl.getBoundingClientRect();
        var layerRect = layerEl.getBoundingClientRect();
        var stickyTop = getStickyTopOffset();

        var scrollableWithinTrack = trackRect.height - layerRect.height;
        if (scrollableWithinTrack <= 0) return trackRect.top <= stickyTop ? 1 : 0;

        var progress = (stickyTop - trackRect.top) / scrollableWithinTrack;
        return clamp(progress, 0, 1);
    }

    function stepLerp() {
        var delta = targetProgress - renderedProgress;
        var absDelta = Math.abs(delta);

        // Continuous smooth physics lerping:
        var lerpRate = 0.35;
        if (absDelta > 0.15) {
            lerpRate = 0.65;
        }

        if (absDelta < 0.0004 || (targetProgress >= 0.98 && absDelta < 0.05) || (targetProgress <= 0.02 && absDelta < 0.05)) {
            renderedProgress = targetProgress;
            isLerping = false;
        } else {
            renderedProgress += delta * lerpRate;
            isLerping = true;
            window.requestAnimationFrame(stepLerp);
        }

        var frameIndex = progressToFrameIndex(renderedProgress);
        if (!frameState[frameIndex]) requestFrame(frameIndex, "high");
        renderFrameIndex(frameIndex);
    }

    function onScroll() {
        if (!getElements() || !isAcademyMode() || !isElementVisible(trackEl)) return;
        targetProgress = computeScrollProgress();

        var targetIndex = progressToFrameIndex(targetProgress);
        if (!frameState[targetIndex]) requestFrame(targetIndex, "high");

        // Proactively warm adjacent frames in both directions for seamless scrubbing
        for (var d = 1; d <= 12; d++) {
            var ahead = targetIndex + d;
            var behind = targetIndex - d;
            if (ahead <= LAST_INDEX && !frameState[ahead]) requestFrame(ahead, "high");
            if (behind >= 0 && !frameState[behind]) requestFrame(behind, "high");
        }

        // Preload boundary frames immediately with high priority when nearing ends
        if (targetProgress >= 0.85 && !frameState[LAST_INDEX]) {
            requestFrame(LAST_INDEX, "high");
        }
        if (targetProgress <= 0.15 && !frameState[0]) {
            requestFrame(0, "high");
        }

        if (!isLerping) {
            isLerping = true;
            window.requestAnimationFrame(stepLerp);
        }
    }

    var resizeTimeout = null;
    function onResize() {
        if (resizeTimeout) window.clearTimeout(resizeTimeout);
        resizeTimeout = window.setTimeout(function () {
            if (getElements() && isAcademyMode() && isElementVisible(trackEl)) {
                sizeTrackAndStickyOffset();
                sizeCanvasToContainer();
                targetProgress = computeScrollProgress();
                renderedProgress = targetProgress;
                renderFrameIndex(progressToFrameIndex(renderedProgress));
                if (!backgroundStarted) startBackgroundPreload();
            }
        }, 80);
    }

    function init() {
        getElements();
        if (isAcademyMode() && isElementVisible(trackEl)) {
            sizeTrackAndStickyOffset();
            sizeCanvasToContainer();

            loadFlankAssets();

            targetProgress = computeScrollProgress();
            renderedProgress = targetProgress;
            var initialIndex = progressToFrameIndex(targetProgress);

            requestFrame(0, "high");
            requestFrame(initialIndex, "high");
            requestFrame(LAST_INDEX, "high");

            for (var b = 0; b <= Math.min(30, LAST_INDEX); b++) {
                requestFrame(b, "high");
            }

            if (document.readyState === "complete") {
                requestIdle(startBackgroundPreload, { timeout: 1500 });
                sizeTrackAndStickyOffset();
            } else {
                window.addEventListener("load", function () {
                    requestIdle(startBackgroundPreload, { timeout: 1500 });
                    sizeTrackAndStickyOffset();
                    onScroll();
                });
            }
        }

        if (document.body) {
            var modeObserver = new MutationObserver(function () {
                getElements();
                if (isAcademyMode() && isElementVisible(trackEl)) {
                    sizeTrackAndStickyOffset();
                    onScroll();
                }
            });
            modeObserver.observe(document.body, { attributes: true, attributeFilter: ["class"] });
        }
    }

    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onResize);

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }

    window.ScrollFrameAnimation = {
        get frameCount() { return FRAME_COUNT; },
        get currentFrameIndex() { return currentRenderedIndex; },
        get targetProgress() { return targetProgress; },
        get renderedProgress() { return renderedProgress; },
        get framesLoaded() {
            var n = 0;
            for (var i = 0; i < FRAME_COUNT; i++) {
                if (isFrameLoaded(i)) n++;
            }
            return n;
        },
        refresh: function() {
            getElements();
            if (isAcademyMode() && isElementVisible(trackEl)) {
                sizeTrackAndStickyOffset();
                sizeCanvasToContainer();
                loadFlankAssets();
                targetProgress = computeScrollProgress();
                renderedProgress = targetProgress;
                var initialIndex = progressToFrameIndex(renderedProgress);
                requestFrame(initialIndex, "high");
                for (var d = 1; d <= 12; d++) {
                    var a = initialIndex + d;
                    var b = initialIndex - d;
                    if (a <= LAST_INDEX) requestFrame(a, "high");
                    if (b >= 0) requestFrame(b, "high");
                }
                renderFrameIndex(initialIndex);
                if (!backgroundStarted) startBackgroundPreload();
            }
        }
    };
})();


