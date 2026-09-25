/**
 * LORD SAI — E-BOOK ACCESS (js/ebook-access.js)
 *
 * store.html #ebooks: the visitor types the access code, and the right code opens the e-book
 * inside the Store page in a protected reader. PDF.js draws every page as a picture, on
 * computers, phones and tablets alike, so the reader has no Download, Print or Copy button
 * and no text to select.
 *
 * While the e-book is open (only the reader is affected, never the rest of the site):
 *   - right-click / long-press menus, dragging, selecting and copying are blocked on the book,
 *     and so are Ctrl/Cmd + A and C when aimed at it
 *   - while the reader is on screen: Ctrl/Cmd + S, P and U, Ctrl+Shift+S (browser screenshot),
 *     Ctrl+Shift+X, F12 and the developer-tools shortcuts are blocked
 *   - printing the page prints "This e-book cannot be printed." instead of the book
 *   - the pages blur when the window or tab loses focus (a snipping, screen-sharing or
 *     recording app is opened, or the visitor switches away), when Print Screen is pressed, and
 *     while the Windows / Command key is held (Win+PrtSc, Win+Shift+S, Cmd+Shift+3/4/5)
 *
 * NOTE: no website can block screenshots or screen recording. Phone screenshot buttons,
 * Windows' Snipping Tool, screen recorders and cameras work outside the browser, and a web page
 * is not told about them. Only an installed app can black them out (Android's FLAG_SECURE),
 * which a website cannot use. The measures above make copying harder; they are not a
 * guarantee. This is a static website, so the access code and the PDF's address can
 * also be read by anyone who opens the page source.
 *
 * When the site is opened straight from disk (file://), browsers do not let PDF.js read the
 * file, so the browser's own PDF viewer is used there, with its toolbar hidden.
 *
 * To change the e-book or the code, edit the settings below.
 */
(function (window, document) {
    "use strict";

    var ACCESS_CODE = "LSIRA";            // checked without regard to upper / lower case
    var EBOOK_PDF = "pdf/lsi-ebook.pdf";  // put the e-book PDF at this path (or change the path)
    var MAX_PAGE_WIDTH = 860;             // CSS px; keep in step with .lsi-ebook-page max-width

    var PDFJS_BASE = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/";
    var pdfjsPromise = null;

    function el(id) { return document.getElementById(id); }

    /** The protected PDF.js reader needs a web address; from disk only the browser's viewer works. */
    function useBuiltInViewer() {
        return !/^https?:$/.test(window.location.protocol) && navigator.pdfViewerEnabled === true;
    }

    function showMessage(frame, text) {
        frame.innerHTML = "";
        var box = document.createElement("div");
        box.className = "lsi-ebook-message";
        box.innerHTML = '<i class="fas fa-book"></i><p></p>';
        box.querySelector("p").textContent = text; // English; the language switcher translates it
        frame.appendChild(box);
    }

    function showMissing(frame) {
        el("lsiEbookFullscreen").classList.add("d-none");
        showMessage(frame, "The e-book file has not been added yet. Please check back soon or contact the academy desk.");
    }

    function loadPdfJs() {
        if (window.pdfjsLib) return Promise.resolve(window.pdfjsLib);
        if (pdfjsPromise) return pdfjsPromise;
        pdfjsPromise = new Promise(function (resolve, reject) {
            var s = document.createElement("script");
            s.src = PDFJS_BASE + "pdf.min.js";
            s.onload = function () {
                if (!window.pdfjsLib) { reject(new Error("PDF.js did not load")); return; }
                window.pdfjsLib.GlobalWorkerOptions.workerSrc = PDFJS_BASE + "pdf.worker.min.js";
                resolve(window.pdfjsLib);
            };
            s.onerror = function () { pdfjsPromise = null; reject(new Error("PDF.js could not be downloaded")); };
            document.head.appendChild(s);
        });
        return pdfjsPromise;
    }

    function renderBuiltIn(frame) {
        frame.innerHTML = "";
        var iframe = document.createElement("iframe");
        iframe.className = "lsi-ebook-iframe";
        iframe.title = "E-Book";
        iframe.src = EBOOK_PDF + "#toolbar=0&navpanes=0&view=FitH";
        frame.appendChild(iframe);
    }

    // ---- Reader (PDF.js) ----------------------------------------------------------------------

    /**
     * PDF.js: one canvas per page, drawn when it scrolls near the reader's
     * view and dropped again when it is far away, so long books do not use up the phone's memory.
     */
    function renderWithPdfJs(frame) {
        showMessage(frame, "Opening the e-book...");
        loadPdfJs().then(function (pdfjsLib) {
            return pdfjsLib.getDocument(EBOOK_PDF).promise;
        }).then(function (pdf) {
            return pdf.getPage(1).then(function (first) {
                var base = first.getViewport({ scale: 1 });
                frame.innerHTML = "";
                var pages = document.createElement("div");
                pages.className = "lsi-ebook-pages";
                frame.appendChild(pages);
                var width = Math.max(240, Math.min(pages.clientWidth - 16, MAX_PAGE_WIDTH));
                var scale = width / base.width;
                var ratio = Math.min(window.devicePixelRatio || 1, 2);

                var draw = function (holder) {
                    if (holder.lsiToken) return;
                    var token = holder.lsiToken = {};
                    pdf.getPage(Number(holder.getAttribute("data-page"))).then(function (page) {
                        if (holder.lsiToken !== token) return null;
                        var vp = page.getViewport({ scale: scale * ratio });
                        var canvas = document.createElement("canvas");
                        canvas.width = Math.floor(vp.width);
                        canvas.height = Math.floor(vp.height);
                        return page.render({ canvasContext: canvas.getContext("2d"), viewport: vp }).promise.then(function () {
                            if (holder.lsiToken !== token) return;
                            holder.appendChild(canvas);
                        });
                    }).catch(function () { if (holder.lsiToken === token) holder.lsiToken = null; });
                };
                var drop = function (holder) {
                    holder.lsiToken = null;
                    while (holder.firstChild) holder.removeChild(holder.firstChild);
                };

                var observer = "IntersectionObserver" in window
                    ? new IntersectionObserver(function (entries) {
                        entries.forEach(function (e) { if (e.isIntersecting) draw(e.target); else drop(e.target); });
                    }, { root: pages, rootMargin: "1200px 0px" })
                    : null;

                for (var n = 1; n <= pdf.numPages; n++) {
                    var holder = document.createElement("div");
                    holder.className = "lsi-ebook-page";
                    holder.setAttribute("data-page", String(n));
                    holder.style.aspectRatio = base.width + " / " + base.height;
                    pages.appendChild(holder);
                    if (observer) observer.observe(holder); else draw(holder);
                }
            });
        }).catch(function (err) {
            if (err && err.name === "MissingPDFException") showMissing(frame);
            else showMessage(frame, "The e-book could not be opened here. Please check your internet connection and try again.");
        });
    }

    // ---- Full screen --------------------------------------------------------------------------

    function fullscreenElement() { return document.fullscreenElement || document.webkitFullscreenElement || null; }

    /** Fills the screen with the reader: the browser's full screen where allowed, else the window. */
    function setExpanded(viewer, on) {
        viewer.classList.toggle("is-expanded", on);
        document.documentElement.classList.toggle("lsi-ebook-expanded", on); // stops the page behind from scrolling
        var btn = el("lsiEbookFullscreen");
        btn.querySelector("i").className = on ? "fas fa-compress me-1" : "fas fa-expand me-1";
        btn.querySelector("span").textContent = on ? "Exit full screen" : "Full screen";
        btn.setAttribute("aria-pressed", on ? "true" : "false");
    }

    function toggleFullscreen(viewer) {
        var on = !viewer.classList.contains("is-expanded");
        setExpanded(viewer, on);
        try {
            if (on) {
                var req = viewer.requestFullscreen || viewer.webkitRequestFullscreen;
                var p = req && req.call(viewer);
                if (p && p.catch) p.catch(function () { /* not allowed here: the window-sized reader stays */ });
            } else if (fullscreenElement()) {
                (document.exitFullscreen || document.webkitExitFullscreen).call(document);
            }
        } catch (e) { /* browsers without the Fullscreen API keep the window-sized reader */ }
    }

    // ---- Protection while the e-book is open --------------------------------------------------
    //
    // Limited to the e-book: menus, selecting, copying and dragging are blocked only on the reader
    // (#lsiEbookViewer), and the keyboard shortcuts only while the unlocked reader is on screen.
    // The access-code box, the rest of the Store page and the other pages work as usual.

    var guarded = false;
    var shieldReasons = {};                 // why the reader is blurred right now
    var onScreen = true, pointerInside = false;

    /** The reader stays blurred while any reason is active (window unfocused, tab hidden, capture key). */
    function shield(viewer, reason, on) {
        if (on) shieldReasons[reason] = true; else delete shieldReasons[reason];
        viewer.classList.toggle("is-shielded", Object.keys(shieldReasons).length > 0);
    }

    function unshieldAll(viewer) {
        shieldReasons = {};
        viewer.classList.remove("is-shielded");
    }

    function spoilClipboard() {
        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText("Screenshots of this e-book are not allowed.").catch(function () { /* not permitted */ });
            }
        } catch (e) { /* not permitted */ }
    }

    function isEditable(t) {
        return !!(t && t.closest && t.closest("input, textarea, select, [contenteditable=''], [contenteditable='true']"));
    }

    /** Browser shortcuts for saving, printing, page source, developer tools and web capture. */
    function isBlockedShortcut(e) {
        var code = e.code || "", mod = e.ctrlKey || e.metaKey;
        if (code === "F12" || e.key === "F12") return true;                                  // developer tools
        if (mod && (code === "KeyS" || code === "KeyP" || code === "KeyU")) return true;      // save (Ctrl+Shift+S: Edge/Firefox screenshot), print, page source
        if (mod && e.shiftKey && code === "KeyX") return true;                                // Edge "Web select"
        if (mod && (e.shiftKey || e.altKey) && (code === "KeyI" || code === "KeyJ" || code === "KeyC")) return true; // developer tools
        return false;
    }

    /** Ctrl/Cmd + A or C aimed at the e-book; typing fields elsewhere on the page keep working. */
    function isCopyShortcut(e, viewer) {
        if (!(e.ctrlKey || e.metaKey) || e.shiftKey || e.altKey) return false;
        if (e.code !== "KeyA" && e.code !== "KeyC") return false;
        if (viewer.contains(e.target)) return true;
        return pointerInside && !isEditable(e.target);
    }

    /** The Windows / Command key: held for Win+PrtSc, Win+Shift+S and Cmd+Shift+3/4/5. */
    function isSystemKey(e) { return e.key === "Meta" || e.key === "OS"; }

    function protect(viewer, protectedReader) {
        if (guarded) return;
        guarded = true;

        ["contextmenu", "dragstart", "selectstart", "copy", "cut"].forEach(function (type) {
            viewer.addEventListener(type, function (e) { e.preventDefault(); });
        });
        viewer.addEventListener("pointerenter", function () { pointerInside = true; });
        viewer.addEventListener("pointerleave", function () { pointerInside = false; });
        if ("IntersectionObserver" in window) {
            new IntersectionObserver(function (entries) {
                onScreen = entries[entries.length - 1].isIntersecting;
            }).observe(viewer);
        }

        document.addEventListener("keydown", function (e) {
            if (onScreen && (isBlockedShortcut(e) || isCopyShortcut(e, viewer))) {
                e.preventDefault();
                e.stopPropagation();
                return;
            }
            // Blur first: the OS may take the picture before the browser hears the rest of the keys.
            if (protectedReader && e.key === "PrintScreen") shield(viewer, "print-screen", true);
            if (protectedReader && isSystemKey(e)) shield(viewer, "system-key", true);
            if (e.key === "Escape" && viewer.classList.contains("is-expanded") && !fullscreenElement()) setExpanded(viewer, false);
        }, true);
        document.addEventListener("keyup", function (e) {
            if (protectedReader && e.key === "PrintScreen") { shield(viewer, "print-screen", true); spoilClipboard(); }
            if (protectedReader && isSystemKey(e)) shield(viewer, "system-key", false);
        }, true);

        // The browser's own viewer (only used from disk) takes focus when clicked, so losing focus
        // is not a sign of a screenshot there.
        if (protectedReader) {
            window.addEventListener("blur", function () { shield(viewer, "window", true); });
            window.addEventListener("focus", function () {
                shield(viewer, "window", false);
                shield(viewer, "system-key", false);    // its key-up may have gone to the other app
                shield(viewer, "print-screen", false);
            });
            document.addEventListener("visibilitychange", function () { shield(viewer, "tab", document.hidden); });
            el("lsiEbookShield").addEventListener("click", function () { unshieldAll(viewer); });
        }

        el("lsiEbookFullscreen").addEventListener("click", function () { toggleFullscreen(viewer); });
        ["fullscreenchange", "webkitfullscreenchange"].forEach(function (type) {
            document.addEventListener(type, function () {
                if (!fullscreenElement() && viewer.classList.contains("is-expanded")) setExpanded(viewer, false);
            });
        });
    }

    function openReader() {
        var viewer = el("lsiEbookViewer"), frame = el("lsiEbookFrame");
        var builtIn = useBuiltInViewer();
        el("lsiEbookFullscreen").classList.remove("d-none");
        viewer.classList.remove("d-none");
        protect(viewer, !builtIn);
        var render = function () { if (builtIn) renderBuiltIn(frame); else renderWithPdfJs(frame); };

        // On a web server, check the file is there first, so a missing PDF shows a clear message.
        if (/^https?:$/.test(window.location.protocol) && window.fetch) {
            fetch(EBOOK_PDF, { method: "HEAD", cache: "no-store" })
                .then(function (r) { if (r.ok) render(); else showMissing(frame); })
                .catch(render);
        } else {
            render();
        }
    }

    function init() {
        var form = el("lsiEbookForm");
        if (!form) return;
        var input = el("lsiEbookCode"), error = el("lsiEbookError");

        input.addEventListener("input", function () {
            input.classList.remove("is-invalid");
            error.classList.add("d-none");
        });

        form.addEventListener("submit", function (e) {
            e.preventDefault();
            var code = input.value.replace(/\s+/g, "").toUpperCase();
            if (code !== ACCESS_CODE.toUpperCase()) {
                input.classList.add("is-invalid");
                input.setAttribute("aria-invalid", "true");
                error.classList.remove("d-none");
                input.focus();
                input.select();
                return;
            }
            input.classList.remove("is-invalid");
            input.removeAttribute("aria-invalid");
            error.classList.add("d-none");
            el("lsiEbookLocked").classList.add("d-none");
            var granted = el("lsiEbookGranted");
            granted.classList.remove("d-none");
            openReader();
            granted.focus({ preventScroll: true });
            el("lsiEbookViewer").scrollIntoView({ behavior: "smooth", block: "start" });
        });
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
    else init();
})(window, document);
