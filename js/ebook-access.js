/**
 * LORD SAI — E-BOOK ACCESS (js/ebook-access.js)
 *
 * store.html #ebooks: the visitor types the access code, and the right code opens the e-book
 * PDF inside the Store page. Computers use the browser's own PDF viewer; phones and tablets
 * (which usually cannot show a PDF inside a page) get the pages drawn by PDF.js instead.
 *
 * To change the e-book or the code, edit the two settings below.
 *
 * NOTE: this is a static website, so the code and the PDF's address can be read by anyone
 * who opens the page source. It keeps casual visitors out; it is not strong protection.
 */
(function (window, document) {
    "use strict";

    var ACCESS_CODE = "LSIRA";            // checked without regard to upper / lower case
    var EBOOK_PDF = "pdf/lsi-ebook.pdf";  // put the e-book PDF at this path (or change the path)

    var PDFJS_BASE = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/";
    var pdfjsPromise = null;

    function el(id) { return document.getElementById(id); }

    /** Computers with a built-in PDF viewer get it in an <iframe>; touch devices get PDF.js. */
    function useBuiltInViewer() {
        var desktop = window.matchMedia && window.matchMedia("(hover: hover) and (pointer: fine)").matches;
        return navigator.pdfViewerEnabled === true && desktop;
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
        el("lsiEbookNewTab").classList.add("d-none");
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
        iframe.src = EBOOK_PDF + "#view=FitH";
        frame.appendChild(iframe);
    }

    /** PDF.js: one canvas per page, each drawn when it scrolls near the reader's view. */
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
                var width = Math.max(240, pages.clientWidth - 16);
                var scale = width / base.width;
                var ratio = Math.min(window.devicePixelRatio || 1, 2);

                var draw = function (holder) {
                    if (holder.getAttribute("data-drawn")) return;
                    holder.setAttribute("data-drawn", "1");
                    pdf.getPage(Number(holder.getAttribute("data-page"))).then(function (page) {
                        var vp = page.getViewport({ scale: scale * ratio });
                        var canvas = document.createElement("canvas");
                        canvas.width = Math.floor(vp.width);
                        canvas.height = Math.floor(vp.height);
                        holder.appendChild(canvas);
                        return page.render({ canvasContext: canvas.getContext("2d"), viewport: vp }).promise;
                    }).catch(function () { holder.removeAttribute("data-drawn"); });
                };

                var observer = "IntersectionObserver" in window
                    ? new IntersectionObserver(function (entries) {
                        entries.forEach(function (e) { if (e.isIntersecting) { draw(e.target); observer.unobserve(e.target); } });
                    }, { root: pages, rootMargin: "600px 0px" })
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
            else showMessage(frame, "The e-book could not be opened here. Please use “Open full screen” above.");
        });
    }

    function openReader() {
        var viewer = el("lsiEbookViewer"), frame = el("lsiEbookFrame");
        var newTab = el("lsiEbookNewTab");
        newTab.href = EBOOK_PDF;
        newTab.classList.remove("d-none");
        viewer.classList.remove("d-none");
        var render = function () { if (useBuiltInViewer()) renderBuiltIn(frame); else renderWithPdfJs(frame); };

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
