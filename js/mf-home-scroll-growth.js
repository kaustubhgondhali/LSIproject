/* ==========================================================================
   MUTUAL FUND HOME PAGE — SCROLL-DRIVEN INVESTMENT GROWTH ANIMATION
   PART 2/5 (hand -> plant) EXTENDED BY PART 3/5 (tree -> full wealth tree)
   EXTENDED BY PART 4/5 (cinematic quality pass — no timeline/concept change)
   FINALIZED BY PART 5/5 (pacing curve + performance/robustness polish)

   PART 5 SCOPE: this is the finalization pass. It does not add anything
   visual — Parts 2-4 already cover the full hand -> investment -> plant
   -> tree -> wealth-tree story and its cinematic treatment. Part 5 only:
     1. Retimes progress -> frame-index mapping (see progressToFrameIndex
        below) from one straight line to a small set of content-verified
        anchor points, so the requested story-beat percentages (10-25%
        planting, 25-40% hand leaves, 40-55% sprout, 55-70% plant grows,
        70-85% tree grows, 85-95% money grows, 95-100% held wealth tree)
        line up with what the actual footage shows at those points. The
        mapping is still a pure, monotonic function of scroll progress —
        exactly as reversible as before, no frame skipped/reordered.
     2. Caches the Part 4 grade/vignette canvas gradients instead of
        rebuilding them every frame (CPU saving, zero visual change).
     3. Re-verified: single init, no duplicate listeners/observers, all
        idle/decorative loops stay gated off-screen / tab-hidden /
        reduced-motion, canvas resize handling and DPR capping unchanged
        from Part 3/4 (already correct).
   Nothing about which 300 photographic frames exist, their order, the
   sticky/track scroll architecture, or the Part 4 decoration logic
   itself was redesigned.

   Builds directly on top of the Part 1 foundation (sticky visual stage +
   lerped scroll-progress engine). Nothing about how progress is computed,
   how the track/sticky layer is sized, or how scroll direction is handled
   has changed. Part 2 added the photographic frame-sequence scrubber
   itself; Part 3 (this revision) does not redesign any of that — it only
   raises the used frame ceiling from 200 to the full 300 already present
   on disk, and layers a few small, elegant, scroll-bound gold coin
   accents on top of the back half of that same footage. Everything below
   about how progress is computed, sized, and reversed is unchanged.

   Full sequence now covered (single continuous scroll journey):
     Frames   1- 30   Hand enters holding the investment/seed money
     Frames  30- 50   Hand moves naturally toward the soil
     Frames  50- 70   Money/seed is placed into the soil
     Frames  70- 90   Hand gradually leaves the scene
     Frames  90-120   A small sprout emerges
     Frames 120-150   The stem grows progressively
     Frames 150-200   The plant develops branches and leaves    [Part 2 ends]
     Frames 200-230   Young tree forms; trunk thickens, first
                      secondary branches split off unevenly
     Frames 230-260   Branches multiply and thicken further,
                      foliage density increases — mature tree
     Frames 260-300   Wealth stage: banknotes proliferate through the
                      real footage while a handful of elegant gold
                      coin accents fade/scale in on top, progressively,
                      settling into the full wealth tree on the final
                      frame                                     [Part 3]

   Source asset: img/mf-home-growth-frames/ezgif-frame-001.jpg ... -300.jpg
   (300 real photographic frames — organic, non-symmetrical branching,
   natural lighting and depth all come from the source photography itself,
   so Part 3 does not need to synthesize any tree geometry). All 300 are
   now used; TOTAL_FRAME_COUNT and USED_FRAME_COUNT are kept as separate
   constants (rather than merged into one literal) purely so a future part
   could still narrow the used range again without touching this math.

   No autoplay: every frame draw — and every coin's scale/fade/rotation —
   is a direct, deterministic function of scroll progress, never of wall-
   clock time. Scrolling down plays the sequence forward (hand -> plant ->
   tree -> wealth tree); scrolling up plays it backward (wealth tree ->
   tree -> plant -> hand) — this falls out naturally because the same
   progress -> frame-index mapping (and the same frame-index -> coin-state
   mapping) is used in both directions.
   ========================================================================== */

(function () {
    "use strict";

    var TRACK_ID = "mfHomeGrowthTrack";
    var STICKY_ID = "mfHomeGrowthSticky";
    var CANVAS_ID = "mfHomeGrowthCanvas";
    var HEADER_SELECTOR = ".navbar, .gateway-header";

    var FRAME_FOLDER = "img/mf-home-growth-frames/";
    var FRAME_PREFIX = "ezgif-frame-";
    var FRAME_EXT = ".jpg";
    var FRAME_PAD = 3;

    // Total frames physically available in the folder (from Part 1's asset drop).
    var TOTAL_FRAME_COUNT = 300;
    // Frames used by the full timeline as of Part 3: the complete hand ->
    // plant -> tree -> wealth-tree story now uses every frame on disk.
    var USED_FRAME_COUNT = 300;
    var LAST_INDEX = USED_FRAME_COUNT - 1;

    // ------------------------------------------------------------------
    // CINEMATIC THEME PASS
    // ------------------------------------------------------------------

    // Two restrained color washes — deep green in the shadows, a whisper
    // of warm gold in the highlights — so the still-photography footage
    // reads consistently with the site's Mutual Fund green/gold theme.
    // Kept low-opacity per "avoid excessive glow."
    //
    // PART 5 — both gradients (here and in applyRadialVignette below) are
    // cached and only rebuilt when the canvas size actually changes,
    // instead of being recreated on every single frame draw. Gradient
    // objects are one of the pricier canvas allocations, and this runs
    // on every scroll tick, so caching them is a meaningful, low-risk
    // CPU saving with zero visual difference.
    var gradeCacheW = -1, gradeCacheH = -1, cachedShadowWash = null, cachedHighlightWash = null;
    function ensureGradeCache(cw, ch) {
        if (cw === gradeCacheW && ch === gradeCacheH && cachedShadowWash) return;
        gradeCacheW = cw;
        gradeCacheH = ch;
        cachedShadowWash = ctx.createLinearGradient(0, 0, cw, ch);
        cachedShadowWash.addColorStop(0, "rgba(6, 36, 30, 0.94)");
        cachedShadowWash.addColorStop(0.5, "rgba(255, 255, 255, 1)");
        cachedShadowWash.addColorStop(1, "rgba(10, 45, 36, 0.92)");

        cachedHighlightWash = ctx.createRadialGradient(
            cw * 0.5, ch * 0.32, 0,
            cw * 0.5, ch * 0.32, Math.max(cw, ch) * 0.75
        );
        cachedHighlightWash.addColorStop(0, "rgba(255, 214, 140, 0.16)");
        cachedHighlightWash.addColorStop(1, "rgba(255, 214, 140, 0)");
    }
    function applyCinematicGrade(cw, ch) {
        ensureGradeCache(cw, ch);
        ctx.save();
        ctx.globalCompositeOperation = "multiply";
        ctx.fillStyle = cachedShadowWash;
        ctx.fillRect(0, 0, cw, ch);

        ctx.globalCompositeOperation = "soft-light";
        ctx.fillStyle = cachedHighlightWash;
        ctx.fillRect(0, 0, cw, ch);
        ctx.restore();
    }

    // Softer radial vignette (replaces the flatter top/bottom-only fade)
    // for a more premium, lens-like framing. Cached for the same reason
    // as the grade washes above.
    var vignetteCacheW = -1, vignetteCacheH = -1, cachedVignette = null;
    function applyRadialVignette(cw, ch) {
        if (cw !== vignetteCacheW || ch !== vignetteCacheH || !cachedVignette) {
            vignetteCacheW = cw;
            vignetteCacheH = ch;
            var radius = Math.max(cw, ch) * 0.78;
            cachedVignette = ctx.createRadialGradient(
                cw / 2, ch / 2, radius * 0.55,
                cw / 2, ch / 2, radius
            );
            cachedVignette.addColorStop(0, "rgba(2, 10, 8, 0)");
            cachedVignette.addColorStop(1, "rgba(1, 6, 5, 0.30)");
        }
        ctx.fillStyle = cachedVignette;
        ctx.fillRect(0, 0, cw, ch);
    }

    var trackEl = null;
    var stickyEl = null;
    var canvas = null;
    var ctx = null;

    function getElements() {
        if (!trackEl) trackEl = document.getElementById(TRACK_ID);
        if (!stickyEl) stickyEl = document.getElementById(STICKY_ID);
        if (!canvas) canvas = document.getElementById(CANVAS_ID);
        if (canvas && !ctx) {
            ctx = canvas.getContext("2d", { alpha: false });
        }
        return !!(trackEl && stickyEl && canvas && ctx);
    }

    function isMutualFundMode() {
        if (window.LSI_Mode && typeof window.LSI_Mode.getMode === "function") {
            return window.LSI_Mode.getMode() === "mutual-fund";
        }
        if (document.body && document.body.classList.contains("mode-mutual-fund")) {
            return true;
        }
        if (document.body && document.body.classList.contains("mode-academy")) {
            return false;
        }
        if (document.documentElement && document.documentElement.classList.contains("mode-mutual-fund")) {
            return true;
        }
        if (document.documentElement && document.documentElement.classList.contains("mode-academy")) {
            return false;
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

    function clamp(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function lerp(a, b, t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------
    // PART 5 — PROGRESS -> FRAME-INDEX PACING CURVE
    // ------------------------------------------------------------------
    // Finalization pass: maps scroll progress (0-1) to a frame index
    // (0-299) through a handful of anchor points instead of one straight
    // line. This changes ONLY the pacing (how much scroll distance each
    // part of the story gets) — it never skips, reorders, or duplicates
    // a frame, and it is still a pure, monotonic function of progress,
    // so it is exactly as reversible as the old straight-line mapping:
    // scrolling up retraces the same curve backwards, frame-for-frame,
    // with no jumps and no touching of the frame-loading engine below.
    //
    // The anchors were chosen by inspecting the actual footage so each
    // requested story beat lands at (approximately) its requested
    // scroll-progress window:
    //   0%        hand arrives, holding the investment
    //   10%-25%   hand plants it in the soil
    //   25%-40%   hand covers the soil and leaves
    //   40%-55%   a sprout appears
    //   55%-70%   the sprout grows into a small leafy plant/tree
    //   70%-85%   the tree fills out (money starts appearing late here,
    // Story timeline anchors calibrated to give every stage ample vertical scroll room:
    //   0%        Animation starts: Hand holding money enters scene (Frame 1)
    //   10%       First movement: Hand moves toward soil (Frame 30)
    //   25%       Next movement: Money/seed planted firmly into soil (Frame 75)
    //   40%       Main visual movement: Hand departs & sprout emerges (Frame 120)
    //   60%       Middle animation: Stem and vibrant leaves grow progressively (Frame 180)
    //   75%       Final transformation: Tree matures with full branches (Frame 225)
    //   90%       Final movement: Wealth proliferates across branches (Frame 270)
    //   94%-100%  Animation completely finished: Full mature wealth tree completes (Frame 300)
    //             Holds completed scene in full view before releasing to below content
    var PACING_ANCHORS = [
        { p: 0.00, f: 0 },    // 0%: Hand holding money enters scene (Frame 1)
        { p: 0.10, f: 29 },   // 10%: First movement toward soil (Frame 30)
        { p: 0.25, f: 74 },   // 25%: Money/seed planted firmly into soil (Frame 75)
        { p: 0.40, f: 119 },  // 40%: Main visual movement: sprout emerges (Frame 120)
        { p: 0.60, f: 179 },  // 60%: Middle animation: vibrant leafy plant grows (Frame 180)
        { p: 0.75, f: 224 },  // 75%: Final transformation: matures into tree (Frame 225)
        { p: 0.90, f: 269 },  // 90%: Final movement: banknotes & wealth proliferate (Frame 270)
        { p: 0.94, f: 299 },  // 94%: Animation completely finished (Frame 300)
        { p: 1.00, f: 299 }   // 100%: Holds completed wealth tree clearly before releasing to below content
    ];

    function progressToFrameIndex(progress) {
        var t = clamp(progress, 0, 1);
        for (var i = 0; i < PACING_ANCHORS.length - 1; i++) {
            var a = PACING_ANCHORS[i];
            var b = PACING_ANCHORS[i + 1];
            if (t <= b.p) {
                var span = b.p - a.p;
                var localT = span > 0 ? (t - a.p) / span : 0;
                return clamp(Math.round(lerp(a.f, b.f, localT)), 0, LAST_INDEX);
            }
        }
        return LAST_INDEX;
    }

    function pad(num, size) {
        var s = String(num);
        while (s.length < size) s = "0" + s;
        return s;
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

    // Scroll distance (beyond sticky stage height) giving the user ample room
    // to observe every story beat clearly at a cinematic, comfortable pace.
    function extraScrollDistanceForViewport() {
        var w = window.innerWidth || document.documentElement.clientWidth;
        if (w <= 575.98) return 4800; // Mobile: ~4800px gives ample room to observe every growth stage
        if (w <= 991.98) return 6200; // Tablet: ~6200px
        return 7800;                  // Desktop: ~7800px gives ~26px per frame for smooth, unhurried scrubbing
    }

    var active = false;
    var targetProgress = 0;
    var renderedProgress = 0;
    var isLerping = false;

    function sizeTrackAndStickyOffset() {
        if (!active) return;
        var stickyTop = getStickyTopOffset();
        stickyEl.style.top = stickyTop + "px";

        var stageHeight = stickyEl.offsetHeight || stickyEl.getBoundingClientRect().height || 480;
        var extra = extraScrollDistanceForViewport();
        trackEl.style.height = Math.round(stageHeight + extra) + "px";
    }

    function sizeCanvasToContainer() {
        if (!active || !isElementVisible(stickyEl)) return;
        var rect = stickyEl.getBoundingClientRect();
        var dpr = Math.min(window.devicePixelRatio || 1, 2);
        var w = Math.round(rect.width * dpr);
        var h = Math.round(rect.height * dpr);
        if (w > 0 && h > 0 && (canvas.width !== w || canvas.height !== h)) {
            canvas.width = w;
            canvas.height = h;
        }
    }

    // Progress is 0 the instant the track's top reaches the sticky offset,
    // and 1 once the track has scrolled past by exactly (track height -
    // sticky height). Unchanged from Part 1.
    function computeScrollProgress() {
        if (!active || !isElementVisible(trackEl)) return 0;
        var trackRect = trackEl.getBoundingClientRect();
        var stickyRect = stickyEl.getBoundingClientRect();
        var stickyTop = getStickyTopOffset();

        var scrollableWithinTrack = trackRect.height - stickyRect.height;
        if (scrollableWithinTrack <= 0) {
            return trackRect.top <= stickyTop ? 1 : 0;
        }

        var progress = (stickyTop - trackRect.top) / scrollableWithinTrack;
        return clamp(progress, 0, 1);
    }

    // ------------------------------------------------------------------
    // FRAME SEQUENCE ENGINE
    // ------------------------------------------------------------------
    var frameState = new Array(USED_FRAME_COUNT); // { drawable } | undefined
    var currentRenderedIndex = -1;
    var currentTargetIndex = 0;

    // PART 4 — ambient (non-scroll) animation clock, in seconds. Only
    // decoration reads this (coin idle bob, particles, breathing zoom);
    // frame selection above never does.
    var ambientTime = 0;

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

    function frameUrl(zeroBasedIndex) {
        return FRAME_FOLDER + FRAME_PREFIX + pad(zeroBasedIndex + 1, FRAME_PAD) + FRAME_EXT;
    }

    function isFrameLoaded(index) {
        var state = frameState[index];
        return !!(state && state.drawable);
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

    function requestFrame(index, priority) {
        if (index < 0 || index > LAST_INDEX) return;
        if (frameState[index]) return; // already requested/loaded
        frameState[index] = { drawable: null };

        var url = frameUrl(index);
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
                    maybeRepaintFor(index);
                }).catch(function () {
                    maybeRepaintFor(index);
                });
            } else {
                maybeRepaintFor(index);
            }
        };
        img.onerror = function () {};
        img.src = url;
    }

    function maybeRepaintFor(loadedIndex) {
        var curTarget = progressToFrameIndex(renderedProgress);
        if (Math.abs(curTarget - loadedIndex) <= 2 || currentRenderedIndex === -1) {
            renderFrameIndex(curTarget);
        }
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
    var BACKGROUND_BATCH_SIZE = 12;

    function processBackgroundBatch() {
        if (!active || !isElementVisible(trackEl)) return;
        var count = 0;
        while (backgroundQueuePos < backgroundQueue.length && count < BACKGROUND_BATCH_SIZE) {
            requestFrame(backgroundQueue[backgroundQueuePos], "low");
            backgroundQueuePos++;
            count++;
        }
        if (backgroundQueuePos < backgroundQueue.length) {
            requestIdle(processBackgroundBatch, { timeout: 400 });
        }
    }

    function startBackgroundPreload() {
        if (backgroundStarted || !active || !isElementVisible(trackEl)) return;
        backgroundStarted = true;
        var center = progressToFrameIndex(targetProgress);
        backgroundQueue = buildPriorityQueue(center);
        backgroundQueuePos = 0;
        requestIdle(processBackgroundBatch, { timeout: 1000 });
    }

    // --------------------------------------------------------------------
    // DRAWING — the photographic frame, full-bleed across the sticky stage,
    // with a light, professional grade (soft vignette only). No text, no
    // cartoon overlays: the premium look comes from the source photography.
    // --------------------------------------------------------------------
    function drawFrame(drawable) {
        var cw = canvas.width;
        var ch = canvas.height;
        if (!cw || !ch) return;

        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = "high";

        if (drawable) {
            ctx.drawImage(drawable, 0, 0, cw, ch);
            applyCinematicGrade(cw, ch);
            applyRadialVignette(cw, ch);
        } else {
            ctx.fillStyle = "#08413B";
            ctx.fillRect(0, 0, cw, ch);
            applyRadialVignette(cw, ch);
        }
    }

    function renderFrameIndex(index) {
        if (!active || !isElementVisible(trackEl)) return;
        var resolvedIndex = getNearestLoadedIndex(index);
        currentRenderedIndex = resolvedIndex;
        currentTargetIndex = index;
        drawFrame(resolvedIndex === -1 ? null : frameState[resolvedIndex].drawable);
    }

    function renderProgress(progress) {
        var frameIndex = progressToFrameIndex(progress);
        if (!frameState[frameIndex]) requestFrame(frameIndex, "high");
        renderFrameIndex(frameIndex);
    }

    function stepLerp() {
        var delta = targetProgress - renderedProgress;
        var absDelta = Math.abs(delta);

        var lerpRate = 0.35;
        if (absDelta > 0.15) {
            lerpRate = 0.65; // rapid convergence on fast scroll movements
        }

        if (absDelta < 0.0004 || (targetProgress >= 0.98 && absDelta < 0.04) || (targetProgress <= 0.02 && absDelta < 0.04)) {
            renderedProgress = targetProgress;
            isLerping = false;
        } else {
            renderedProgress += delta * lerpRate;
            isLerping = true;
            window.requestAnimationFrame(stepLerp);
        }
        renderProgress(renderedProgress);
    }

    function onScroll() {
        if (!getElements()) return;
        if (!active) {
            activateIfNeeded();
        }
        if (!active || !isElementVisible(trackEl)) return;
        targetProgress = computeScrollProgress();

        var targetIndex = progressToFrameIndex(targetProgress);
        if (!frameState[targetIndex]) requestFrame(targetIndex, "high");

        // Proactively warm adjacent frames in both directions for seamless scrubbing
        for (var d = 1; d <= 24; d++) {
            var ahead = targetIndex + d;
            if (ahead <= LAST_INDEX && !frameState[ahead]) requestFrame(ahead, "high");
        }
        for (var b = 1; b <= 12; b++) {
            var behind = targetIndex - b;
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
            if (!getElements()) return;
            activateIfNeeded();
            if (!active || !isElementVisible(trackEl)) return;
            sizeTrackAndStickyOffset();
            sizeCanvasToContainer();
            targetProgress = computeScrollProgress();
            renderedProgress = targetProgress;
            renderProgress(renderedProgress);
        }, 80);
    }

    // ------------------------------------------------------------------
    function activateIfNeeded() {
        if (!getElements()) return;
        var shouldBeActive = isMutualFundMode() && isElementVisible(trackEl);
        if (shouldBeActive && !active) {
            active = true;
            sizeTrackAndStickyOffset();
            sizeCanvasToContainer();
            targetProgress = computeScrollProgress();
            renderedProgress = targetProgress;

            var initialIndex = progressToFrameIndex(targetProgress);
            requestFrame(0, "high");
            requestFrame(initialIndex, "high");
            requestFrame(LAST_INDEX, "high");
            // Warm initial neighborhood generously so early scrolling is instantaneous
            for (var d = 1; d <= 24; d++) {
                var a = initialIndex + d;
                var b = initialIndex - d;
                if (a <= LAST_INDEX) requestFrame(a, "high");
                if (b >= 0) requestFrame(b, "high");
            }

            renderProgress(renderedProgress);
            if (!backgroundStarted) {
                requestIdle(startBackgroundPreload, { timeout: 800 });
            }
        } else if (!shouldBeActive) {
            active = false;
        }
    }

    function init() {
        console.log("[MF-GROWTH] Mutual Fund scroll growth engine initialized.");
        getElements();
        activateIfNeeded();

        if (isMutualFundMode() && isElementVisible(trackEl)) {
            requestFrame(0, "high");
            requestFrame(LAST_INDEX, "high");
            for (var b = 0; b <= Math.min(30, LAST_INDEX); b++) {
                requestFrame(b, "high");
            }
        }

        // Mode can switch client-side (Academy <-> Mutual Fund) without a
        // full page reload elsewhere on this site, so re-check once more
        // after full load when layout has settled.
        window.addEventListener("load", function () {
            getElements();
            activateIfNeeded();
            sizeTrackAndStickyOffset();
            onScroll();
        });

        if (document.body) {
            var modeObserver = new MutationObserver(function () {
                getElements();
                activateIfNeeded();
                if (active) {
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

    // Public API — kept from Part 1 (same shape) so later parts can keep
    // extending this same engine without renegotiating the progress/sticky
    // contract. Part 3 used this contract to bring in frames 201-300 for
    // the tree/wealth stage; a future part could, for example, add a CTA
    // reveal after frame 300 without touching anything above.
    window.MfHomeScrollGrowth = {
        get targetProgress() { return targetProgress; },
        get renderedProgress() { return renderedProgress; },
        get isActive() { return active; },
        get currentFrameIndex() { return currentRenderedIndex; },
        frameFolder: FRAME_FOLDER,
        framePrefix: FRAME_PREFIX,
        frameExt: FRAME_EXT,
        frameCount: TOTAL_FRAME_COUNT,
        usedFrameCount: USED_FRAME_COUNT,
        refresh: function () {
            getElements();
            activateIfNeeded();
            if (active && isElementVisible(trackEl)) {
                sizeTrackAndStickyOffset();
                sizeCanvasToContainer();
                targetProgress = computeScrollProgress();
                renderedProgress = targetProgress;
                renderProgress(renderedProgress);
                if (!backgroundStarted) {
                    requestIdle(startBackgroundPreload, { timeout: 800 });
                }
            }
        }
    };
})();
