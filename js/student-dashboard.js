/**
 * LORD SAI ACADEMY — STUDENT LMS (js/student-dashboard.js)
 * Application-style learning portal. Every view is rendered from the existing
 * /api/student/** and /api/public/** endpoints; nothing here is mock data.
 */
(function (window) {
    "use strict";

    var api = LSI_Auth.api;
    var doubtModal, readerModal, readerBlobUrl = null;
    var state = { overview: null, courses: [], content: {}, publicCourses: [], doubts: [], ebooks: [], invoices: [], certificates: [], certCourses: [],
        journal: { entries: [], page: 0, last: true, editingId: null, editCharges: null, resultTouched: false, openId: null } };

    // ---- helpers ---------------------------------------------------------------------------

    function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
    function el(id) { return document.getElementById(id); }
    function setText(id, v) { var e = el(id); if (e) e.textContent = v == null ? "—" : v; }
    function money(v) { return v == null ? "—" : "₹" + Number(v).toFixed(2); }
    function fmtDate(iso) { if (!iso) return "—"; var d = new Date(iso); return isNaN(d) ? esc(iso) : d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" }); }
    function duration(sec) { if (!sec) return ""; var m = Math.round(sec / 60); return m >= 60 ? Math.floor(m / 60) + "h " + (m % 60) + "m" : m + " min"; }
    function chip(status) {
        var map = { ACTIVE: "ok", COMPLETED: "info", INACTIVE: "muted", CANCELLED: "danger", OPEN: "warn", IN_PROGRESS: "info", RESOLVED: "ok", PENDING_REVIEW: "muted", REVIEWED: "ok", NEEDS_REVISION: "danger", PAID: "ok", SUCCESS: "ok", FAILED: "danger", REFUNDED: "muted", REVOKED: "danger",
            PENDING: "warn", APPROVED: "info", SCHEDULED: "info", REJECTED: "danger", PASSED: "ok", SUBMITTED: "info", EXPIRED: "danger", UPCOMING: "warn", CLOSED: "muted", ELIGIBLE: "ok",
            PROFIT: "ok", LOSS: "danger", BREAKEVEN: "muted" };
        return '<span class="lms-chip ' + (map[status] || "muted") + '">' + esc(String(status || "").replace(/_/g, " ").toLowerCase()) + '</span>';
    }
    function progressBar(pct, done, total) {
        return '<div class="lms-progress-row"><span>' + (done != null ? done + ' of ' + total + ' lessons completed' : 'Progress') + '</span><strong>' + pct + '%</strong></div>' +
            '<div class="lms-progress"><span style="width:' + pct + '%"></span></div>';
    }
    // Admin-uploaded thumbnails (images/<file>) are served by the API; static img/... paths are used as-is.
    function mediaUrl(p) { return p && p.indexOf("images/") === 0 ? LSI_Auth.apiBase + "/public/" + p : p; }
    function thumb(path, cls) {
        return path ? '<div class="' + cls + '" style="background-image:url(\'' + esc(mediaUrl(path)) + '\')"></div>'
            : '<div class="' + cls + ' placeholder"><i class="fas fa-chart-line"></i></div>';
    }
    function hasAccess(c) { return c.enrollmentStatus === "ACTIVE" || c.enrollmentStatus === "COMPLETED"; }
    function learnUrl(c, lessonId) { return "learn.html?course=" + c.courseId + (lessonId ? "&lesson=" + lessonId : ""); }
    function toast(msg, ok) {
        var t = document.createElement("div");
        t.className = "toast show text-white border-0 " + (ok === false ? "bg-danger" : "bg-success");
        t.style.cssText = "position:fixed;bottom:20px;right:20px;z-index:3000;";
        t.innerHTML = '<div class="d-flex"><div class="toast-body">' + esc(msg) + '</div><button type="button" class="btn-close btn-close-white me-2 m-auto" onclick="this.closest(\'.toast\').remove()"></button></div>';
        document.body.appendChild(t); setTimeout(function () { t.remove(); }, 4000);
    }

    // A 401 means the login itself has ended (logged out elsewhere, password changed, expired) and auth.js
    // has already cleared it: say so plainly and go to the login page instead of leaving a dead form.
    function sessionEnded(r, errBox) {
        if (!r || r.status !== 401) return false;
        var msg = "Your login session has ended. Please log in again.";
        if (errBox) { errBox.textContent = msg; errBox.classList.remove("d-none"); } else { toast(msg, false); }
        setTimeout(function () { window.location.replace("student-login.html?session_expired=1"); }, 2000);
        return true;
    }

    // ---- navigation ------------------------------------------------------------------------

    // An Automation Admin student's free ebook login (with no course) sees only these sections.
    var EBOOK_ONLY_VIEWS = ["ebooks", "profile", "settings"];
    function ebookOnly() { return !!(state.overview && state.overview.ebookOnly); }

    function applyEbookOnly() {
        var on = ebookOnly();
        document.querySelectorAll(".lms-nav a[data-view]").forEach(function (a) {
            if (EBOOK_ONLY_VIEWS.indexOf(a.dataset.view) < 0) a.style.display = on ? "none" : "";
        });
        document.querySelectorAll(".lms-nav .lms-nav-label").forEach(function (l) {
            if (/support/i.test(l.textContent)) l.style.display = on ? "none" : "";
        });
        var note = el("ebookOnlyNote"); if (note) note.hidden = !on;
        var search = el("lmsSearch"); if (search) search.placeholder = on ? "Search your ebooks..." : "Search your courses, lessons and resources...";
    }

    function show(view) {
        if (view === "general") view = "journal"; // old "Student General" links open the Student Journal
        if (ebookOnly() && EBOOK_ONLY_VIEWS.indexOf(view) < 0) view = "ebooks";
        if (view === "exams" || view === "exam-take") view = "certificates"; // the exam pages were replaced by Certificate
        document.querySelectorAll(".lms-view").forEach(function (v) { v.classList.remove("active"); });
        var target = el("view-" + view);
        if (!target) { view = "dashboard"; target = el("view-dashboard"); }
        target.classList.add("active");
        document.querySelectorAll(".lms-nav a[data-view]").forEach(function (a) { a.classList.toggle("active", a.dataset.view === view); });
        el("lmsSidebar").classList.remove("open"); el("lmsBackdrop").classList.remove("show");
        window.scrollTo({ top: 0 });
        var loaders = { discover: renderDiscover, progress: renderProgress, certificates: loadCertificates, journal: loadJournalView, profile: renderProfile, ebooks: loadEbooks, invoices: loadInvoices };
        if (loaders[view]) loaders[view]();
    }

    // ---- data loading ----------------------------------------------------------------------

    function loadAll() {
        return Promise.all([api("/student/profile"), api("/student/courses"), api("/student/ebooks")]).then(function (r) {
            if (r[0].success) state.overview = r[0].data;
            applyEbookOnly();
            if (r[1].success) state.courses = r[1].data || [];
            if (r[2].success) state.ebooks = r[2].data || [];
            renderHeader(); renderDashboard(); renderLearning(); renderEbooks();
            fillDoubtCourses();
            api("/student/certificates").then(function (r) { if (r.success) { state.certificates = r.data || []; setText("navCertCount", state.certificates.length || ""); } });
            return loadContent();
        });
    }

    // ---- my ebooks -------------------------------------------------------------------------

    function loadEbooks() {
        return api("/student/ebooks").then(function (res) {
            if (res.success) { state.ebooks = res.data || []; renderEbooks(); }
            return loadShop();
        });
    }

    function ebookCard(e) {
        var active = e.status === "ACTIVE";
        var blockedByAcademy = !active && e.source === "ACADEMY";
        var cover = e.coverImagePath ? '<div class="thumb" style="background-image:url(\'' + esc(mediaUrl(e.coverImagePath)) + '\');background-size:contain;background-repeat:no-repeat;background-color:#0A1128"></div>'
            : '<div class="thumb placeholder"><i class="fas fa-book"></i></div>';
        return '<div class="lms-card lms-course">' + cover +
            '<div class="body">' +
            '<div class="d-flex justify-content-between align-items-center mb-2">' + (blockedByAcademy ? '<span class="lms-chip danger">blocked</span>' : chip(active ? "ACTIVE" : "INACTIVE")) + '<small class="text-muted">' + (e.source === "ACADEMY" ? "Free from the academy · " : "Purchased ") + fmtDate(e.purchasedAt) + '</small></div>' +
            '<h3 class="title">' + esc(e.title) + '</h3>' +
            (e.author ? '<div class="text-muted" style="font-size:12px;margin-bottom:6px"><i class="fas fa-pen-nib me-1"></i>' + esc(e.author) + '</div>' : '') +
            '<p class="desc">' + esc(e.shortDescription || "") + '</p>' +
            '<div class="meta">' + (e.category ? '<span><i class="fas fa-tag me-1"></i>' + esc(e.category) + '</span>' : '') + (e.language ? '<span><i class="fas fa-language me-1"></i>' + esc(e.language) + '</span>' : '') + (e.invoiceNumber ? '<span><i class="fas fa-file-invoice me-1"></i>' + esc(e.invoiceNumber) + '</span>' : '') + '</div>' +
            '<div class="foot">' +
            (active && e.fileAvailable ? '<button type="button" class="lms-btn accent sm" data-read="' + e.ebookId + '"><i class="fas fa-book-open"></i> Read / View</button>'
                : '<span class="' + (blockedByAcademy ? "text-danger fw-semibold" : "text-muted") + ' small">' + (active ? "File not available yet — contact the academy"
                    : blockedByAcademy ? "Your ebook access is blocked. Please contact the academy." : "Access inactive — contact the academy") + '</span>') +
            (e.invoiceId ? '<button type="button" class="lms-btn ghost sm" data-invoice="' + e.invoiceId + '"><i class="fas fa-download"></i> Invoice</button>' : '') +
            '</div></div></div>';
    }

    function renderEbooks() {
        var grid = el("ebooksGrid"); if (!grid) return;
        setText("navEbookCount", state.ebooks.length || "");
        if (!state.ebooks.length) {
            grid.innerHTML = '<div class="lms-card lms-empty" style="grid-column:1/-1"><i class="fas fa-book"></i>No ebooks yet. ' +
                (isCourseStudent() ? 'You can buy one below.' : '<a href="store.html#ebooks">Browse ebooks in the store</a>.') + '</div>';
            return;
        }
        grid.innerHTML = state.ebooks.map(ebookCard).join("");
        grid.querySelectorAll("[data-read]").forEach(function (b) { b.addEventListener("click", function () { openEbook(b.dataset.read); }); });
        grid.querySelectorAll("[data-invoice]").forEach(function (b) { b.addEventListener("click", function () { downloadInvoice(b.dataset.invoice); }); });
    }

    // ---- buy ebooks (course students) ----------------------------------------------------------
    // Same pipeline as the public store: the price comes from the database, a Razorpay payment is
    // confirmed only by the server (/payments/verify), and demo mode works only while no gateway is
    // configured. The buyer is always this account: /student/ebooks/{id}/order reads the name and
    // email on the server, so nothing typed here can send the ebook to anyone else.

    var buyModal, shop = { list: [], selected: null, mode: null, busy: false, paid: false, failure: null, order: null, bought: null };

    function isCourseStudent() { return state.courses.some(hasAccess); }
    function ownsEbook(id) { return state.ebooks.some(function (e) { return String(e.ebookId) === String(id) && e.status === "ACTIVE"; }); }
    function rupees(v) { return money(v).replace(/\.00$/, ""); }
    function errText(res, fallback) { return (res.errors ? Object.values(res.errors).join(" ") : res.message) || fallback; }
    function hasDiscount(b) { return b.discountedPrice != null && Number(b.discountedPrice) < Number(b.price); }

    function loadShop() {
        var box = el("ebookShop"); if (!box) return;
        box.hidden = !isCourseStudent();
        if (box.hidden) return;
        return api("/public/ebooks", { skipAuthRedirect: true }).then(function (res) {
            if (!res.success) {
                el("ebookShopGrid").innerHTML = '<div class="lms-card lms-empty" style="grid-column:1/-1"><i class="fas fa-exclamation-circle"></i>' + esc(errText(res, "Could not load the ebooks on sale.")) + '</div>';
                return;
            }
            shop.list = res.data || [];
            renderShop();
        });
    }

    function shopCard(b) {
        var cover = b.coverImagePath ? '<div class="thumb" style="background-image:url(\'' + esc(mediaUrl(b.coverImagePath)) + '\');background-size:contain;background-repeat:no-repeat;background-color:#0A1128"></div>'
            : '<div class="thumb placeholder"><i class="fas fa-book"></i></div>';
        return '<div class="lms-card lms-course">' + cover +
            '<div class="body">' +
            (b.category ? '<div class="mb-2"><span class="lms-chip info">' + esc(b.category) + '</span></div>' : '') +
            '<h3 class="title">' + esc(b.title) + '</h3>' +
            (b.author ? '<div class="text-muted" style="font-size:12px;margin-bottom:6px"><i class="fas fa-pen-nib me-1"></i>' + esc(b.author) + '</div>' : '') +
            '<p class="desc">' + esc(b.shortDescription || "") + '</p>' +
            '<div class="meta">' + (b.language ? '<span><i class="fas fa-language me-1"></i>' + esc(b.language) + '</span>' : '') + '</div>' +
            '<div class="foot"><div class="price">' + rupees(b.effectivePrice) + (hasDiscount(b) ? '<s>' + rupees(b.price) + '</s>' : '') + '</div>' +
            (b.available ? '<button type="button" class="lms-btn primary sm" data-buy="' + esc(b.id) + '"><i class="fas fa-shopping-cart"></i> Buy Ebook</button>'
                : '<span class="text-muted small">Coming soon</span>') +
            '</div></div></div>';
    }

    function renderShop() {
        var grid = el("ebookShopGrid"); if (!grid) return;
        var list = shop.list.filter(function (b) { return !ownsEbook(b.id); });
        var empty = function (msg) { return '<div class="lms-card lms-empty" style="grid-column:1/-1"><i class="fas fa-book"></i>' + msg + '</div>'; };
        if (!shop.list.length) grid.innerHTML = empty("No ebooks are on sale right now. Please check back soon.");
        else if (!list.length) grid.innerHTML = empty("You already have every ebook that is on sale. New titles will appear here.");
        else grid.innerHTML = list.map(shopCard).join("");
        grid.querySelectorAll("[data-buy]").forEach(function (b) { b.addEventListener("click", function () { openBuy(b.dataset.buy); }); });
    }

    function buyStep(name) {
        ["Details", "Busy", "Result"].forEach(function (s) { el("ebookBuy" + s).classList.toggle("d-none", s !== name); });
        el("ebookBuyClose").disabled = name === "Busy";
    }

    function setBuyBusy(on) { shop.busy = on; el("ebookBuyPay").disabled = on || !shop.mode; }

    function showBuyError(msg) { var e = el("ebookBuyError"); e.textContent = msg; e.classList.remove("d-none"); }

    function openBuy(id) {
        var b = shop.list.find(function (x) { return String(x.id) === String(id); });
        if (!b || shop.busy) return;
        var o = state.overview || {};
        shop.selected = b; shop.bought = null; shop.order = null;
        setText("ebookBuyTitle", b.title);
        setText("ebookBuyAuthor", b.author ? "by " + b.author : "");
        setText("ebookBuyPrice", rupees(b.effectivePrice));
        setText("ebookBuyWas", hasDiscount(b) ? rupees(b.price) : "");
        // Shown for confirmation only: the server uses the account's details, the student types nothing.
        el("ebookBuyAccount").innerHTML = [["Name", o.fullName], ["Student ID", o.studentId], ["Email", o.email], ["Mobile", o.mobile]]
            .filter(function (f) { return f[1]; })
            .map(function (f) { return '<div><span>' + esc(f[0]) + '</span><strong>' + esc(f[1]) + '</strong></div>'; }).join("");
        el("ebookBuyError").classList.add("d-none");
        buyStep("Details");
        checkBuyMode();
        buyModal.show();
    }

    /** The backend decides Razorpay or demo; it also enforces that choice whatever this page does. */
    function checkBuyMode() {
        var banner = el("ebookBuyMode");
        shop.mode = null; setBuyBusy(false);
        setText("ebookBuyPayText", "Pay " + rupees(shop.selected.effectivePrice));
        banner.className = "alert alert-light border small py-2 px-3";
        banner.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Checking payment options…';
        return api("/payments/mode", { skipAuthRedirect: true }).then(function (res) {
            var info = res.success && res.data ? res.data : null;
            shop.mode = info && (info.mode === "DEMO" || info.mode === "RAZORPAY") ? info.mode : null;
            if (!shop.mode) {
                banner.className = "alert alert-warning small py-2 px-3";
                banner.innerHTML = '<i class="fas fa-exclamation-triangle me-1"></i> Online payment is unavailable right now. Please try again in a few minutes.';
            } else if (shop.mode === "DEMO") {
                banner.className = "alert alert-warning small py-2 px-3";
                banner.innerHTML = '<strong><i class="fas fa-flask me-1"></i> Demo Payment Mode</strong><br>' + esc(info.message || "No real money will be charged.");
                setText("ebookBuyPayText", "Complete Demo Payment — " + rupees(shop.selected.effectivePrice));
            } else {
                banner.className = "alert alert-success small py-2 px-3";
                banner.innerHTML = '<strong><i class="fas fa-shield-alt me-1"></i> Secure payment powered by Razorpay.</strong><br>UPI, cards, net banking and wallets accepted.';
            }
            setBuyBusy(false);
        });
    }

    function submitBuy(e) {
        e.preventDefault();
        if (shop.busy || !shop.mode || !shop.selected) return;
        el("ebookBuyError").classList.add("d-none");
        var path = "/student/ebooks/" + encodeURIComponent(shop.selected.id);
        setBuyBusy(true);
        if (shop.mode === "DEMO") {
            buyStep("Busy"); setText("ebookBuyBusyText", "Completing the demo payment…");
            api(path + "/demo-complete", { method: "POST" }).then(function (res) {
                if (res.success && res.data && res.data.paymentStatus === "SUCCESS") buySucceeded(res.data);
                else buyRefused(res, "The demo payment could not be completed.");
            });
            return;
        }
        Promise.all([api(path + "/order", { method: "POST" }), loadRazorpay()]).then(function (r) {
            var res = r[0];
            if (!res.success || !res.data) { buyRefused(res, "Could not start the payment."); return; }
            openRazorpay(res.data);
        }, function (err) { setBuyBusy(false); showBuyError(err.message); });
    }

    /** Nothing was charged: back to the form with the reason. */
    function buyRefused(res, fallback) {
        var msg = res.message || "";
        buyStep("Details");
        if ((res.status === 409 && /real payments are active/i.test(msg)) || (res.status === 503 && /demo payment mode/i.test(msg))) {
            // The admin switched the payment mode while this window was open.
            checkBuyMode(); showBuyError("The payment mode has just changed. Please click the button again."); return;
        }
        setBuyBusy(false);
        if (res.status === 409 && /already have access/i.test(msg)) {
            showBuyError("You already own this ebook — it is in My Ebooks above."); loadEbooks(); return;
        }
        showBuyError(errText(res, fallback));
    }

    function loadRazorpay() {
        if (window.Razorpay) return Promise.resolve();
        return new Promise(function (resolve, reject) {
            var s = document.createElement("script");
            s.src = LSI_CONFIG.RAZORPAY_CHECKOUT_JS;
            s.onload = resolve;
            s.onerror = function () { s.remove(); reject(new Error("Could not load the payment gateway. Please check your connection.")); };
            document.head.appendChild(s);
        });
    }

    function openRazorpay(order) {
        var rzp;
        shop.order = order; shop.paid = false; shop.failure = null;
        try {
            rzp = new window.Razorpay({
                key: order.razorpayKeyId,
                amount: order.amountPaise,
                currency: order.currency,
                name: LSI_CONFIG.BRAND_NAME,
                description: order.productName,
                image: LSI_CONFIG.BRAND_LOGO,
                order_id: order.razorpayOrderId,
                prefill: { name: order.customerName, email: order.customerEmail, contact: order.customerMobile },
                notes: { order_ref: order.orderRef },
                theme: { color: LSI_CONFIG.BRAND_COLOR },
                modal: { ondismiss: function () { if (!shop.paid) razorpayClosed(); } },
                handler: function (response) { shop.paid = true; verifyBuy(response); }
            });
            rzp.on("payment.failed", function (resp) {
                shop.failure = (resp && resp.error && resp.error.description) || "The payment was declined by your bank or wallet.";
            });
        } catch (err) {
            setBuyBusy(false); showBuyError("Could not open the payment window. Please try again."); return;
        }
        // Bootstrap keeps keyboard focus inside its own dialog, which would stop typing in
        // Razorpay's window, so this dialog steps aside while Razorpay is open.
        el("ebookBuyModal").addEventListener("hidden.bs.modal", function () { rzp.open(); }, { once: true });
        buyModal.hide();
    }

    function razorpayClosed() {
        setBuyBusy(false);
        if (shop.failure) {
            buyResult("danger", "Payment not completed", "Payment was not completed. " + shop.failure + " Nothing has been added to your account.",
                [["Order", shop.order && shop.order.orderRef]], true);
        } else {
            buyResult("warn", "Payment cancelled", "The payment window was closed before paying, so nothing was charged and nothing was added to your account.", null, true);
        }
        buyModal.show();
    }

    function verifyBuy(r) {
        buyStep("Busy"); setText("ebookBuyBusyText", "Confirming your payment…");
        buyModal.show();
        api("/payments/verify", {
            method: "POST", skipAuthRedirect: true,
            body: { razorpayOrderId: r.razorpay_order_id, razorpayPaymentId: r.razorpay_payment_id, razorpaySignature: r.razorpay_signature }
        }).then(function (res) {
            if (res.success && res.data && res.data.paymentStatus === "SUCCESS") { buySucceeded(res.data); return; }
            setBuyBusy(false);
            buyResult("danger", "Payment not confirmed", errText(res, "We could not verify this payment.") +
                " If money was deducted, the ebook will appear in My Ebooks once the payment reaches us — otherwise contact the academy with your order number.",
                [["Order", shop.order && shop.order.orderRef]], false);
        });
    }

    /** Shown only after the backend confirmed the payment and granted the ebook. */
    function buySucceeded(d) {
        var demo = d.paymentMode === "DEMO", title = shop.selected ? shop.selected.title : d.productName;
        setBuyBusy(false);
        shop.bought = shop.selected && shop.selected.id;
        buyResult("ok", demo ? "Demo payment successful" : "Payment successful",
            "“" + title + "” is now in My Ebooks." + (demo ? " No money was charged." : "") + " " +
            (d.setupEmailSent ? "The invoice has been emailed to " + d.email + "." : "We could not email the invoice this time — you can download it from My Invoices."),
            [["Order", d.orderRef], ["Invoice", d.invoiceNumber], ["Amount", d.amountInr != null ? rupees(d.amountInr) : null]], false);
        loadEbooks().then(function () {
            var mine = state.ebooks.find(function (e) { return String(e.ebookId) === String(shop.bought); });
            el("ebookBuyRead").classList.toggle("d-none", !(mine && mine.status === "ACTIVE" && mine.fileAvailable));
        });
    }

    function buyResult(kind, title, text, facts, canRetry) {
        var icon = el("ebookBuyResultIcon"), box = el("ebookBuyFacts");
        icon.className = "lms-buy-icon " + kind;
        icon.innerHTML = '<i class="fas ' + ({ ok: "fa-check", warn: "fa-times", danger: "fa-exclamation" })[kind] + '"></i>';
        setText("ebookBuyResultTitle", title);
        setText("ebookBuyResultText", text);
        box.innerHTML = (facts || []).filter(function (f) { return f[1]; }).map(function (f) {
            return '<div><span>' + esc(f[0]) + '</span><strong>' + esc(f[1]) + '</strong></div>';
        }).join("");
        box.classList.toggle("d-none", !box.innerHTML);
        el("ebookBuyRetry").classList.toggle("d-none", !canRetry);
        el("ebookBuyRead").classList.add("d-none");
        buyStep("Result");
    }

    /** Streams the protected PDF with the bearer token and shows it inside the portal (never a plain link). */
    function openEbook(ebookId) {
        var e = state.ebooks.find(function (x) { return String(x.ebookId) === String(ebookId); }) || {};
        setText("ebookReaderTitle", e.title || "Ebook");
        setText("ebookReaderMeta", (e.author ? e.author + " · " : "") + "Licensed to " + ((state.overview && state.overview.studentId) || "you") + " — for personal use inside the Student Portal only");
        el("ebookReaderLoading").innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span> Opening your ebook…';
        el("ebookReaderLoading").style.display = "flex";
        el("ebookReaderFrame").style.display = "none";
        el("ebookReaderPages").style.display = "none";
        setReaderControls(false);
        readerModal.show();
        fetchProtected("/student/ebooks/" + ebookId + "/download").then(function (blob) {
            // PDF.js gives zoom and portrait / landscape; the browser's own viewer stays as the fallback.
            return loadPdfJs().then(function () { return showInReader(blob); }).catch(function () { showInFrame(blob); });
        }, function (err) {
            el("ebookReaderLoading").innerHTML = '<span class="text-warning"><i class="fas fa-lock me-1"></i> ' + esc(err.message) + '</span>';
        });
    }

    // ---- ebook reader: zoom in / out and portrait / landscape (PDF.js, as for lesson handouts) ----
    // Text always stays upright: pages are never rotated. Portrait shows the whole page on screen,
    // like holding the book; Landscape spreads the page across the full width for larger text.

    var PDFJS_SRC = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js";
    var PDFJS_WORKER = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";
    /** Percent of the current fit (Portrait: whole page; Landscape: full width). */
    var ZOOM_STEPS = [50, 75, 100, 125, 150, 175, 200, 250, 300];
    /** Stay under iOS Safari's canvas limit (~16.7 MP); larger pages render at a lower pixel density. */
    var MAX_CANVAS_PIXELS = 16000000;
    var reader = { task: null, pdf: null, zoom: 100, landscape: false, chosen: false, scale: 1, observer: null, gen: 0 };

    /** Until the student picks a mode, the reader follows the screen: wide -> Landscape, tall -> Portrait. */
    function readerIsWide() { var box = el("ebookReaderPages"); return box.clientWidth > box.clientHeight; }

    /** pdf.js is fetched only the first time an ebook is opened, so the dashboard itself stays light. */
    function loadPdfJs() {
        if (window.pdfjsLib) return Promise.resolve();
        if (!loadPdfJs.pending) {
            loadPdfJs.pending = new Promise(function (resolve, reject) {
                var s = document.createElement("script");
                s.src = PDFJS_SRC;
                s.onload = function () { window.pdfjsLib ? resolve() : reject(new Error("PDF viewer unavailable")); };
                s.onerror = function () { loadPdfJs.pending = null; s.remove(); reject(new Error("PDF viewer unavailable")); };
                document.head.appendChild(s);
            });
        }
        return loadPdfJs.pending;
    }

    /** The previous in-frame viewer, used only when pdf.js cannot load. Zoom / orientation stay disabled. */
    function showInFrame(blob) {
        if (readerBlobUrl) URL.revokeObjectURL(readerBlobUrl);
        readerBlobUrl = URL.createObjectURL(blob);
        var frame = el("ebookReaderFrame");
        frame.src = readerBlobUrl + "#toolbar=0&navpanes=0";
        frame.style.display = "block";
        el("ebookReaderLoading").style.display = "none";
    }

    function showInReader(blob) {
        var gen = ++reader.gen;
        return blob.arrayBuffer().then(function (buf) {
            pdfjsLib.GlobalWorkerOptions.workerSrc = PDFJS_WORKER;
            reader.task = pdfjsLib.getDocument({ data: buf, isEvalSupported: false });
            return reader.task.promise;
        }).then(function (pdf) {
            if (gen !== reader.gen) return;
            reader.pdf = pdf;
            reader.zoom = 100;
            reader.chosen = false;
            el("ebookReaderLoading").style.display = "none";
            el("ebookReaderPages").style.display = "block";
            reader.landscape = readerIsWide();
            setReaderControls(true);
            layoutReader(1);
        });
    }

    function currentReaderPage() {
        var box = el("ebookReaderPages"), slots = box.querySelectorAll(".lms-reader-page");
        for (var i = 0; i < slots.length; i++) {
            if (slots[i].offsetTop + slots[i].offsetHeight > box.scrollTop + 20) return i + 1;
        }
        return 1;
    }

    /** Lays out one placeholder per page at the current zoom / fit; pages are drawn as they scroll into view. */
    function layoutReader(keepPage) {
        var box = el("ebookReaderPages"), pdf = reader.pdf;
        if (!pdf) return;
        var gen = ++reader.gen;
        if (reader.observer) reader.observer.disconnect();
        pdf.getPage(1).then(function (first) {
            if (gen !== reader.gen) return;
            // The page's own orientation (including a PDF's built-in page rotation), so text reads straight.
            var base = first.getViewport({ scale: 1 });
            var fitWidth = Math.max(200, box.clientWidth - 32) / base.width;
            var fitPage = Math.min(fitWidth, Math.max(200, box.clientHeight - 32) / base.height);
            reader.scale = (reader.landscape ? fitWidth : fitPage) * reader.zoom / 100;
            var w = Math.floor(base.width * reader.scale), h = Math.floor(base.height * reader.scale);
            var html = "";
            for (var n = 1; n <= pdf.numPages; n++) {
                html += '<div class="lms-reader-page" data-page="' + n + '" style="width:' + w + 'px;height:' + h + 'px" aria-label="Page ' + n + ' of ' + pdf.numPages + '"></div>';
            }
            box.innerHTML = html;
            reader.observer = new IntersectionObserver(function (entries) {
                entries.forEach(function (en) { if (en.isIntersecting) drawPage(en.target, gen); else releasePage(en.target); });
            }, { root: box, rootMargin: "800px 0px" });
            box.querySelectorAll(".lms-reader-page").forEach(function (s) { reader.observer.observe(s); });
            var target = box.querySelector('[data-page="' + keepPage + '"]');
            box.scrollTop = target && keepPage > 1 ? target.offsetTop - 12 : 0;
            updateReaderControls();
        });
    }

    function drawPage(slot, gen) {
        if (slot.dataset.drawn) return;
        slot.dataset.drawn = "1";
        var n = Number(slot.dataset.page);
        reader.pdf.getPage(n).then(function (page) {
            if (gen !== reader.gen || !slot.dataset.drawn) return;
            var css = page.getViewport({ scale: reader.scale });
            var density = Math.min(window.devicePixelRatio || 1, 2, Math.sqrt(MAX_CANVAS_PIXELS / (css.width * css.height)));
            var vp = page.getViewport({ scale: reader.scale * density });
            var canvas = document.createElement("canvas");
            canvas.width = Math.floor(vp.width); canvas.height = Math.floor(vp.height);
            canvas.style.width = Math.floor(css.width) + "px"; canvas.style.height = Math.floor(css.height) + "px";
            // Pages of a different size than page 1 take their own size.
            slot.style.width = canvas.style.width; slot.style.height = canvas.style.height;
            return page.render({ canvasContext: canvas.getContext("2d"), viewport: vp }).promise.then(function () {
                if (gen !== reader.gen || !slot.dataset.drawn) return;
                slot.innerHTML = ""; slot.appendChild(canvas);
                if (window.LSI_Protect) LSI_Protect.stampCanvas(canvas);
            });
        }).catch(function () {
            if (gen === reader.gen) slot.innerHTML = '<div class="lms-reader-err">Page ' + n + ' could not be displayed.</div>';
        });
    }

    /** Pages far from view give their memory back and are redrawn when the reader returns. */
    function releasePage(slot) {
        if (!slot.dataset.drawn) return;
        delete slot.dataset.drawn;
        slot.innerHTML = "";
    }

    function setReaderControls(on) {
        ["ebookZoomOut", "ebookZoomIn", "ebookPortrait", "ebookLandscape"].forEach(function (id) { el(id).disabled = !on; });
        if (!on) setText("ebookZoomLevel", "100%");
    }

    function updateReaderControls() {
        setText("ebookZoomLevel", reader.zoom + "%");
        el("ebookZoomOut").disabled = reader.zoom <= ZOOM_STEPS[0];
        el("ebookZoomIn").disabled = reader.zoom >= ZOOM_STEPS[ZOOM_STEPS.length - 1];
        el("ebookPortrait").classList.toggle("on", !reader.landscape);
        el("ebookLandscape").classList.toggle("on", reader.landscape);
        el("ebookPortrait").setAttribute("aria-pressed", String(!reader.landscape));
        el("ebookLandscape").setAttribute("aria-pressed", String(reader.landscape));
    }

    function zoomReader(dir) {
        if (!reader.pdf) return;
        var i = ZOOM_STEPS.indexOf(reader.zoom) + dir;
        if (i < 0 || i >= ZOOM_STEPS.length) return;
        reader.zoom = ZOOM_STEPS[i];
        layoutReader(currentReaderPage());
    }

    function orientReader(landscape) {
        if (!reader.pdf) return;
        reader.chosen = true;
        if (reader.landscape === landscape) return;
        reader.landscape = landscape;
        layoutReader(currentReaderPage());
    }

    function closeReader() {
        reader.gen++;
        if (reader.observer) { reader.observer.disconnect(); reader.observer = null; }
        if (reader.task && reader.task.destroy) { try { reader.task.destroy(); } catch (e) { /* ignore */ } }
        reader.task = null; reader.pdf = null;
        el("ebookReaderPages").innerHTML = "";
        el("ebookReaderPages").style.display = "none";
        setReaderControls(false);
    }

    function fetchProtected(path) {
        var session = LSI_Auth.getSession();
        return fetch(LSI_Auth.apiBase + path, { headers: { Authorization: "Bearer " + (session ? session.token : "") } }).then(function (r) {
            if (r.ok) {
                var contentType = (r.headers.get("Content-Type") || "").toLowerCase();
                if (contentType.indexOf("application/pdf") !== 0) throw new Error("The file could not be loaded.");
                return r.blob().then(function (blob) {
                    if (!blob.size) throw new Error("The file is empty or unavailable.");
                    return blob;
                });
            }
            return r.text().then(function (t) {
                var msg = "This file is not available.";
                try { msg = JSON.parse(t).message || msg; } catch (e) { /* ignore */ }
                throw new Error(r.status === 403 ? "You do not have access to this file." : msg);
            });
        });
    }

    // ---- my invoices -----------------------------------------------------------------------

    function loadInvoices() {
        var body = el("invoicesBody");
        return api("/student/invoices").then(function (res) {
            if (!res.success) { body.innerHTML = '<div class="lms-empty text-danger">' + esc(res.message) + '</div>'; return; }
            state.invoices = res.data || [];
            if (!state.invoices.length) { body.innerHTML = '<div class="lms-empty"><i class="fas fa-file-invoice"></i>No invoices yet. Invoices are generated automatically for every purchase.</div>'; return; }
            body.innerHTML = '<div class="lms-table-wrap"><table class="lms-table"><thead><tr><th>Invoice No.</th><th>Product</th><th>Type</th><th>Purchase date</th><th>Amount</th><th>Status</th><th></th></tr></thead><tbody>' +
                state.invoices.map(function (i) {
                    return '<tr><td class="font-monospace">' + esc(i.invoiceNumber) + '</td><td>' + esc(i.productName) + '</td><td>' + esc(i.productType === "EBOOK" ? "Ebook" : "Course") + '</td><td>' + fmtDate(i.purchaseDate) + '</td><td><strong>' + money(i.total) + '</strong></td><td>' + chip(i.paymentStatus === "SUCCESS" ? "PAID" : i.paymentStatus) + '</td>' +
                        '<td class="text-nowrap"><button type="button" class="lms-btn primary sm" data-invoice="' + i.id + '"><i class="fas fa-download"></i> Download</button> <button type="button" class="lms-btn ghost sm" data-invoice-view="' + i.id + '"><i class="fas fa-eye"></i> View</button></td></tr>';
                }).join("") + '</tbody></table></div>';
            body.querySelectorAll("[data-invoice]").forEach(function (b) { b.addEventListener("click", function () { downloadInvoice(b.dataset.invoice); }); });
            body.querySelectorAll("[data-invoice-view]").forEach(function (b) { b.addEventListener("click", function () { downloadInvoice(b.dataset.invoiceView, true); }); });
        });
    }

    function downloadInvoice(id, view) {
        var inv = state.invoices.find(function (x) { return String(x.id) === String(id); });
        fetchProtected("/student/invoices/" + id + "/pdf").then(function (blob) {
            var url = URL.createObjectURL(blob);
            if (view) { window.open(url, "_blank"); }
            else { var a = document.createElement("a"); a.href = url; a.download = (inv ? inv.invoiceNumber : "invoice") + ".pdf"; document.body.appendChild(a); a.click(); a.remove(); }
            setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
        }).catch(function (err) { toast(err.message, false); });
    }

    function loadContent() {
        var calls = state.courses.filter(hasAccess).map(function (c) {
            return api("/student/courses/" + c.courseId).then(function (res) { if (res.success) state.content[c.courseId] = res.data; });
        });
        return Promise.all(calls);
    }

    // ---- header & dashboard ----------------------------------------------------------------

    function renderHeader() {
        var o = state.overview; if (!o) return;
        var first = (o.fullName || "Student").split(" ")[0];
        setText("topName", o.fullName); setText("topStudentId", o.studentId || o.email);
        setText("topAvatar", (o.fullName || "S").split(" ").map(function (p) { return p[0]; }).join("").slice(0, 2).toUpperCase());
        setText("dashName", first);
        setText("statCourses", o.enrolledCourses);
        setText("statLessons", o.completedLessons + " / " + o.totalLessons);
        setText("statDoubts", o.openDoubts);
        setText("navCourseCount", o.enrolledCourses || "");
        setText("navDoubtCount", o.openDoubts || "");
    }

    function renderDashboard() {
        var active = state.courses.filter(hasAccess);
        var card = el("continueCard");
        if (!active.length) {
            card.innerHTML = '<div class="lms-card lms-empty"><i class="fas fa-book-open"></i>You are not enrolled in any course yet.<br><a href="#discover" data-view="discover" class="lms-btn primary sm mt-3">Discover courses</a></div>';
            bindViewLinks(card); return;
        }
        // Prefer an in-progress course, then the most recently enrolled.
        var c = active.filter(function (x) { return x.progressPercent > 0 && x.progressPercent < 100; })[0] || active[0];
        var done = c.progressPercent >= 100;
        card.innerHTML = '<div class="lms-card lms-continue">' + thumb(c.thumbnailPath, "thumb") +
            '<div class="body">' +
            '<span class="lms-chip ' + (done ? "info" : "ok") + ' align-self-start">' + (done ? "Completed" : "In progress") + '</span>' +
            '<div class="course">' + esc(c.courseName) + '</div>' +
            (c.nextLessonTitle ? '<div class="next">Continue: <strong>' + esc(c.nextLessonTitle) + '</strong></div>' : '<div class="next">' + (done ? "All lessons completed — revisit any lesson." : "Start with the first lesson.") + '</div>') +
            '<div class="mt-3 mb-3">' + progressBar(c.progressPercent, c.completedLessons, c.totalLessons) + '</div>' +
            '<div class="mt-auto"><a href="' + learnUrl(c, c.nextLessonId) + '" class="lms-btn accent"><i class="fas fa-play"></i> ' + (done ? "Review course" : c.progressPercent > 0 ? "Continue Learning" : "Start Learning") + '</a></div>' +
            '</div></div>';
    }

    function bindViewLinks(root) {
        root.querySelectorAll("[data-view]").forEach(function (a) {
            a.addEventListener("click", function (e) { e.preventDefault(); location.hash = a.dataset.view; show(a.dataset.view); });
        });
    }

    // ---- my learning -----------------------------------------------------------------------

    function courseCard(c) {
        var done = c.progressPercent >= 100, access = hasAccess(c);
        return '<div class="lms-card lms-course">' + thumb(c.thumbnailPath, "thumb") +
            '<div class="body">' +
            '<div class="d-flex justify-content-between align-items-center mb-2">' + chip(c.enrollmentStatus) + '<small class="text-muted">' + fmtDate(c.enrolledAt) + '</small></div>' +
            '<h3 class="title">' + esc(c.courseName) + '</h3>' +
            '<p class="desc">' + esc(c.shortDescription || "") + '</p>' +
            '<div class="meta"><span><i class="fas fa-layer-group me-1"></i>' + c.moduleCount + ' modules</span><span><i class="fas fa-play-circle me-1"></i>' + c.totalLessons + ' lessons</span>' + (c.duration ? '<span><i class="far fa-clock me-1"></i>' + esc(c.duration) + '</span>' : '') + '</div>' +
            progressBar(c.progressPercent, c.completedLessons, c.totalLessons) +
            (c.nextLessonTitle && !done ? '<div class="text-muted mt-2" style="font-size:12px">Next: <strong class="text-dark">' + esc(c.nextLessonTitle) + '</strong></div>' : '') +
            '<div class="foot">' +
            (access ? '<a href="' + learnUrl(c, c.nextLessonId) + '" class="lms-btn accent sm"><i class="fas fa-play"></i> ' + (done ? "Review" : c.progressPercent > 0 ? "Continue" : "Start") + '</a>' : '<span class="text-muted small">Access inactive — contact the academy</span>') +
            (access ? '<a href="#progress" data-view="progress" class="lms-btn ghost sm">Details</a>' : '') +
            '</div></div></div>';
    }

    function renderLearning() {
        var grid = el("learningGrid");
        if (!state.courses.length) {
            grid.innerHTML = '<div class="lms-card lms-empty" style="grid-column:1/-1"><i class="fas fa-book-open"></i>No enrollments yet. <a href="#discover" data-view="discover">Browse available courses</a>.</div>';
        } else {
            grid.innerHTML = state.courses.map(courseCard).join("");
        }
        bindViewLinks(grid);
    }

    // ---- discover --------------------------------------------------------------------------

    function renderDiscover() {
        var grid = el("discoverGrid");
        grid.innerHTML = '<div class="lms-empty" style="grid-column:1/-1"><span class="spinner-border spinner-border-sm"></span></div>';
        api("/public/courses", { skipAuthRedirect: true }).then(function (res) {
            state.publicCourses = res.success ? (res.data || []) : [];
            var enrolledIds = state.courses.map(function (c) { return c.courseId; });
            if (!state.publicCourses.length) { grid.innerHTML = '<div class="lms-card lms-empty" style="grid-column:1/-1">No courses are open for enrollment right now.</div>'; return; }
            grid.innerHTML = state.publicCourses.map(function (c) {
                var enrolled = enrolledIds.indexOf(c.id) >= 0;
                return '<div class="lms-card lms-course">' + thumb(c.thumbnailPath, "thumb") +
                    '<div class="body">' +
                    '<div class="d-flex justify-content-between align-items-center mb-2">' + (enrolled ? '<span class="lms-chip ok">Enrolled</span>' : '<span class="lms-chip info">Open for enrollment</span>') + '<strong>' + money(c.effectivePrice).replace(".00", "") + '</strong></div>' +
                    '<h3 class="title">' + esc(c.courseName) + '</h3>' +
                    '<p class="desc">' + esc(c.shortDescription || "") + '</p>' +
                    '<div class="meta"><span><i class="fas fa-layer-group me-1"></i>' + c.moduleCount + ' modules</span><span><i class="fas fa-play-circle me-1"></i>' + c.lessonCount + ' lessons</span></div>' +
                    '<div class="foot">' + (enrolled
                        ? '<a href="#learning" data-view="learning" class="lms-btn accent sm"><i class="fas fa-play"></i> Go to course</a>'
                        : '<a href="courses.html" class="lms-btn primary sm"><i class="fas fa-shopping-cart"></i> View &amp; enroll</a>') +
                    '</div></div></div>';
            }).join("");
            bindViewLinks(grid);
        });
    }

    // ---- progress --------------------------------------------------------------------------

    function renderProgress() {
        var body = el("progressBody");
        var active = state.courses.filter(hasAccess);
        if (!active.length) { body.innerHTML = '<div class="lms-card lms-empty"><i class="fas fa-chart-pie"></i>Progress appears once you are enrolled in a course.</div>'; return; }
        body.innerHTML = active.map(function (c) {
            var content = state.content[c.courseId];
            var modules = content ? content.modules : [];
            return '<div class="lms-card pad mb-3">' +
                '<div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-2"><div><h3 class="title mb-1" style="font-size:16px;font-weight:700">' + esc(c.courseName) + '</h3><small class="text-muted">' + c.moduleCount + ' modules · ' + c.totalLessons + ' lessons</small></div>' + chip(c.enrollmentStatus) + '</div>' +
                progressBar(c.progressPercent, c.completedLessons, c.totalLessons) +
                '<div class="mt-3">' + (modules.length ? modules.map(function (m, i) {
                    var pct = m.lessonCount ? Math.round(m.completedCount * 100 / m.lessonCount) : 0;
                    return '<div class="lms-module' + (i === 0 ? " open" : "") + '"><div class="head" data-toggle><i class="fas fa-chevron-right small text-muted"></i><strong>Module ' + (i + 1) + ': ' + esc(m.moduleName) + '</strong><small class="text-muted">' + m.completedCount + '/' + m.lessonCount + '</small><span class="lms-chip ' + (pct === 100 ? "ok" : pct > 0 ? "info" : "muted") + '">' + pct + '%</span></div>' +
                        '<div class="lessons">' + (m.lessons.map(function (l) {
                            return '<div class="lms-lesson' + (l.completed ? " done" : "") + '"><i class="' + (l.completed ? "fas fa-check-circle" : "far fa-circle") + '"></i><a href="' + learnUrl(c, l.id) + '">' + esc(l.lessonTitle) + '</a><small class="text-muted">' + (l.hasVideo ? duration(l.videoDurationSeconds) || "video" : "") + (l.hasMaterial ? ' <i class="fas fa-file-pdf text-danger ms-1"></i>' : "") + '</small></div>';
                        }).join("") || '<div class="lms-lesson text-muted">No lessons published yet.</div>') + '</div></div>';
                }).join("") : '<div class="text-muted small">Curriculum not available.</div>') + '</div></div>';
        }).join("");
        body.querySelectorAll("[data-toggle]").forEach(function (h) { h.addEventListener("click", function () { h.parentElement.classList.toggle("open"); }); });
    }

    // ---- certificate -------------------------------------------------------------------------
    // Course Completion Certificate: complete the course -> apply here -> the academy approves ->
    // the certificate (generated on the server on the Lord Sai design) is ready to view / download.
    // The backend decides eligibility and status and prints the account's registered name; no exam.

    var certApplyModal;
    var CERT_STATES = {
        NOT_ELIGIBLE: { chip: '<span class="lms-chip muted">Not eligible yet</span>', status: "Not Applied" },
        ELIGIBLE: { chip: '<span class="lms-chip info">Ready to apply</span>', status: "Not Applied" },
        PENDING: { chip: '<span class="lms-chip warn">Pending</span>', status: "Pending Admin Approval" },
        APPROVED: { chip: '<span class="lms-chip info">Approved</span>', status: "Approved" },
        REJECTED: { chip: '<span class="lms-chip danger">Rejected</span>', status: "Rejected" },
        GENERATED: { chip: '<span class="lms-chip ok">Certificate Available</span>', status: "Certificate Available" }
    };

    function loadCertificates() {
        var body = el("certificatesBody");
        return Promise.all([api("/student/certificates/courses"), api("/student/certificates")]).then(function (r) {
            if (!r[0].success) { body.innerHTML = '<div class="lms-card lms-empty text-danger">' + esc(r[0].message) + '</div>'; return; }
            state.certCourses = r[0].data || [];
            if (r[1].success) state.certificates = r[1].data || [];
            setText("navCertCount", state.certificates.length || "");
            if (!state.certCourses.length) {
                body.innerHTML = '<div class="lms-card lms-empty"><i class="fas fa-award"></i>Enroll in a course and complete it to apply for your Course Completion Certificate.<br><a href="#discover" data-view="discover" class="lms-btn primary sm mt-3">Discover courses</a></div>';
                bindViewLinks(body); return;
            }
            body.innerHTML = state.certCourses.map(certCard).join("");
            body.querySelectorAll("[data-cert-apply]").forEach(function (b) { b.addEventListener("click", function () { openCertApply(b.dataset.certApply); }); });
            body.querySelectorAll("[data-cert-view]").forEach(function (b) { b.addEventListener("click", function () { openCertificate(b.dataset.certView, false); }); });
            body.querySelectorAll("[data-cert-download]").forEach(function (b) { b.addEventListener("click", function () { openCertificate(b.dataset.certDownload, true); }); });
            bindViewLinks(body);
        });
    }

    function certCard(x) {
        var s = CERT_STATES[x.state] || CERT_STATES.NOT_ELIGIBLE, r = x.request, c = x.certificate;
        var html = '<div class="lms-card pad mb-3" data-cert-course="' + x.courseId + '">' +
            '<div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-2"><div>' +
            '<h3 class="title mb-1" style="font-size:16px;font-weight:700">Course Completion Certificate</h3>' +
            '<small class="text-muted">' + esc(x.courseName) + '</small></div>' + s.chip + '</div>' +
            '<div class="mb-2" style="font-size:13.5px">Status: <strong class="cert-status">' + esc(s.status) + '</strong></div>';
        if (x.state === "NOT_ELIGIBLE") {
            var mc = state.courses.find(function (m) { return String(m.courseId) === String(x.courseId); });
            if (mc && mc.totalLessons) html += '<div class="mb-2">' + progressBar(mc.progressPercent, mc.completedLessons, mc.totalLessons) + '</div>';
            html += '<div class="lms-exam-note">Complete your course before applying for a certificate.</div>' +
                '<div class="mt-3"><a href="learn.html?course=' + x.courseId + '" class="lms-btn accent sm"><i class="fas fa-play"></i> Continue course</a></div>';
        } else if (x.state === "ELIGIBLE") {
            html += '<div class="lms-exam-note ok"><strong>You have completed your course.</strong>' + (x.courseCompletedAt ? ' Completed on ' + fmtDate(x.courseCompletedAt) + '.' : '') + '</div>' +
                '<div class="mt-3"><button type="button" class="lms-btn primary" data-cert-apply="' + x.courseId + '"><i class="fas fa-award"></i> Apply for Certificate</button></div>';
        } else if (x.state === "PENDING" || x.state === "APPROVED") {
            html += '<div class="lms-exam-note"><strong>Certificate application submitted.</strong><br>Status: Pending Admin Approval. You will be able to view and download your certificate here once the academy approves it.</div>' +
                '<div class="lms-exam-kv mt-3"><div><small>Student</small><strong>' + esc(r.studentName) + '</strong></div><div><small>Student ID</small><strong>' + esc(r.studentId || "—") + '</strong></div>' +
                '<div><small>Applied on</small><strong>' + fmtDate(r.requestedAt) + '</strong></div></div>';
        } else if (x.state === "REJECTED") {
            html += '<div class="lms-exam-note danger"><strong>Certificate application was rejected.</strong>' + (r && r.rejectionReason ? '<br>Reason: ' + esc(r.rejectionReason) : '') + '</div>' +
                (x.canApply ? '<div class="mt-3"><button type="button" class="lms-btn primary" data-cert-apply="' + x.courseId + '"><i class="fas fa-redo"></i> Apply Again</button></div>' : '');
        } else if (x.state === "GENERATED" && c) {
            html += '<div class="lms-exam-kv">' +
                '<div><small>Course</small><strong>' + esc(c.courseName) + '</strong></div>' +
                '<div><small>Student</small><strong>' + esc(c.studentName) + '</strong></div>' +
                '<div><small>Student ID</small><strong>' + esc(c.studentId || "—") + '</strong></div>' +
                '<div><small>Issue Date</small><strong>' + fmtDate(c.issuedAt) + '</strong></div>' +
                '<div><small>Certificate No.</small><strong>' + esc(c.certificateNumber) + '</strong></div></div>' +
                '<div class="d-flex flex-wrap gap-2 mt-3"><button type="button" class="lms-btn primary" data-cert-view="' + c.id + '"><i class="fas fa-eye"></i> View Certificate</button>' +
                '<button type="button" class="lms-btn accent" data-cert-download="' + c.id + '"><i class="fas fa-download"></i> Download Certificate</button></div>';
        }
        return html + '</div>';
    }

    function openCertApply(courseId) {
        var x = (state.certCourses || []).find(function (e) { return String(e.courseId) === String(courseId); }); if (!x) return;
        var o = state.overview || {};
        el("certApplyCourseId").value = x.courseId;
        el("certApplyName").value = o.fullName || "";
        el("certApplyName").dataset.accountName = o.fullName || "";
        el("certApplyConfirm").checked = false;
        updateCertNameNote();
        el("certApplyStudentId").value = o.studentId || "";
        el("certApplyCourse").value = x.courseName;
        el("certApplyCompletion").value = x.courseCompletedAt ? fmtDate(x.courseCompletedAt) : "Completed";
        el("certApplyError").classList.add("d-none");
        el("certApplySubmit").disabled = false;
        certApplyModal.show();
    }

    // The certificate fonts print Latin letters (accents allowed); the server checks the same rule.
    var CERT_NAME = /^[A-Za-z\u00C0-\u024F][A-Za-z\u00C0-\u024F .'-]*$/;
    function cleanCertName(v) { return String(v || "").trim().replace(/\s+/g, " "); }
    function updateCertNameNote() {
        var input = el("certApplyName"), note = el("certApplyNameNote");
        var typed = cleanCertName(input.value), account = cleanCertName(input.dataset.accountName);
        el("certApplyNamePreview").textContent = typed ? typed.toUpperCase() : "—";
        var changed = typed !== "" && account !== "" && typed !== account;
        note.textContent = changed ? "This differs from your registered name (" + account + "). The academy will see your correction before issuing the certificate." : "";
        note.classList.toggle("d-none", !changed);
    }

    function submitCertApply(e) {
        e.preventDefault();
        var btn = el("certApplySubmit"), err = el("certApplyError");
        if (btn.disabled) return;
        var name = cleanCertName(el("certApplyName").value);
        var fail = function (m) { err.textContent = m; err.classList.remove("d-none"); };
        if (name.length < 2 || name.length > 150) return fail("Please enter your full name (2 to 150 characters) as it should appear on the certificate.");
        if (!CERT_NAME.test(name)) return fail("Type the name in English letters (A-Z). Only spaces, dots, apostrophes and hyphens are allowed besides letters.");
        if (!el("certApplyConfirm").checked) return fail("Please confirm that your name is correct.");
        btn.disabled = true; err.classList.add("d-none");
        // The Student ID always comes from the account on the server; the name is the one the student confirmed.
        api("/student/certificate-requests", { method: "POST", body: { courseId: Number(el("certApplyCourseId").value), certificateName: name } }).then(function (res) {
            btn.disabled = false;
            if (!res.success) {
                err.textContent = res.errors ? Object.values(res.errors).join(" ") : res.message; err.classList.remove("d-none");
                if (res.status === 409) loadCertificates();
                return;
            }
            certApplyModal.hide(); toast(res.message || "Certificate application submitted.");
            loadCertificates();
        });
    }

    function certificateFileName(c) {
        var n = String((c && c.studentName) || "").replace(/[^A-Za-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
        return "LSA-Certificate-" + (n || (c ? c.certificateNumber : "certificate")) + ".pdf";
    }

    /** Streams the student's own certificate PDF with the bearer token (never a public link). */
    function openCertificate(id, download) {
        var c = state.certificates.find(function (x) { return String(x.id) === String(id); });
        // Open the tab during the click so pop-up blockers allow it; the PDF is loaded into it after.
        var win = download ? null : window.open("", "_blank");
        fetchProtected("/student/certificates/" + id + "/pdf").then(function (blob) {
            var url = URL.createObjectURL(blob);
            if (download || !win) {
                var a = document.createElement("a"); a.href = url; a.download = certificateFileName(c);
                document.body.appendChild(a); a.click(); a.remove();
            } else {
                win.location.href = url;
            }
            setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
        }).catch(function (err) { if (win) win.close(); toast(err.message, false); });
    }

    // ---- student journal ----------------------------------------------------------------------
    // The student's own daily trading journal (/api/student/journal). Entries are private to the
    // account; P&L, the derived result and the performance summary come from the backend.

    var JOURNAL_PAGE_SIZE = 50;

    function todayIso() { var d = new Date(); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 10); }
    // Trade dates are plain calendar days; parse them as local midnight so no timezone shifts the day.
    function fmtDay(iso) { return fmtDate(iso ? iso + "T00:00:00" : iso); }
    function inr(v) {
        if (v == null) return "—";
        var n = Number(v);
        return (n < 0 ? "−" : "") + "₹" + Math.abs(n).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    function pnl(v) {
        if (v == null) return '<span class="text-muted">—</span>';
        var n = Number(v);
        return '<span class="lms-pnl ' + (n > 0 ? "up" : n < 0 ? "down" : "") + '">' + (n > 0 ? "+" : "") + inr(n) + '</span>';
    }
    function numOrNull(id) { var v = el(id).value; return v === "" ? null : Number(v); }
    function resultFor(net) { return net > 0 ? "PROFIT" : net < 0 ? "LOSS" : "BREAKEVEN"; }
    function tradeTypeLabel(t) { return t === "BUY" ? "Buy" : t === "SELL" ? "Sell" : String(t || "").charAt(0) + String(t || "").slice(1).toLowerCase(); }
    var TRADING_TYPES = { EQUITIES: "Equities", MUTUAL_FUNDS: "Mutual Funds", ETFS: "ETFs", OPTIONS: "Options", FUTURES: "Futures", COMMODITIES: "Commodities" };
    function tradingTypeLabel(t) { return TRADING_TYPES[t] || ""; }

    function loadJournalView() {
        if (!el("journalDate").value) el("journalDate").value = todayIso();
        loadJournal(0);
        loadJournalSummary();
    }

    function loadJournal(page) {
        var j = state.journal;
        return api("/student/journal?page=" + page + "&size=" + JOURNAL_PAGE_SIZE).then(function (res) {
            if (!res.success) { el("journalList").innerHTML = '<div class="lms-empty text-danger">' + esc(res.message) + '</div>'; return; }
            var d = res.data || {}, rows = d.content || [];
            j.entries = page === 0 ? rows : j.entries.concat(rows);
            j.page = page;
            j.last = d.last != null ? d.last : !(d.page && d.page.number + 1 < d.page.totalPages);
            renderJournal();
        });
    }

    function loadJournalSummary() {
        return api("/student/journal/summary").then(function (res) {
            if (!res.success) return;
            var s = res.data;
            setText("jsTotal", s.totalTrades);
            setText("jsWins", s.winningTrades);
            setText("jsLosses", s.losingTrades);
            setText("jsWinRate", s.winRatePercent + "%");
            el("jsNet").innerHTML = pnl(s.netPnl);
            setText("jsCharges", inr(s.totalCharges));
        });
    }

    function renderJournal() {
        var j = state.journal, list = el("journalList");
        var q = el("journalSearch").value.trim().toLowerCase();
        var rows = j.entries.filter(function (t) { return !q || ((t.symbol || "") + " " + (t.strategy || "") + " " + (t.segment || "") + " " + tradingTypeLabel(t.tradingType)).toLowerCase().indexOf(q) >= 0; });
        if (!j.entries.length) { list.innerHTML = '<div class="lms-empty"><i class="fas fa-book-open"></i>No trades yet. Add your first trade above — the performance summary updates as you go.</div>'; return; }
        if (!rows.length) { list.innerHTML = '<div class="lms-empty">No trades match “' + esc(q) + '”.</div>'; return; }
        list.innerHTML = '<div class="lms-table-wrap"><table class="lms-table"><thead><tr><th>Date</th><th>Symbol</th><th>Type of Trading</th><th>Type</th><th>Entry → Exit</th><th>Qty</th><th>Charges</th><th>Net P&amp;L</th><th>Strategy</th><th>Result</th><th></th></tr></thead><tbody>' +
            rows.map(function (t) {
                var open = String(j.openId) === String(t.id);
                return '<tr' + (String(j.editingId) === String(t.id) ? ' class="lms-journal-editing"' : '') + '><td class="text-nowrap">' + fmtDay(t.tradeDate) + '</td>' +
                    '<td><strong>' + esc(t.symbol) + '</strong>' + (t.segment ? '<br><small class="text-muted">' + esc(t.segment) + '</small>' : '') + '</td>' +
                    '<td>' + (t.tradingType ? esc(tradingTypeLabel(t.tradingType)) : '<span class="text-muted">—</span>') + '</td>' +
                    '<td>' + esc(tradeTypeLabel(t.tradeType)) + '</td>' +
                    '<td class="text-nowrap">' + inr(t.entryPrice) + ' → ' + inr(t.exitPrice) + '</td>' +
                    '<td>' + (t.quantity != null ? t.quantity : "—") + '</td>' +
                    '<td class="text-nowrap">' + inr(t.charges) + '</td>' +
                    '<td class="text-nowrap">' + pnl(t.netPnl) + '</td>' +
                    '<td>' + esc(t.strategy || "—") + '</td>' +
                    '<td>' + (t.result ? chip(t.result) : '<span class="text-muted">—</span>') + '</td>' +
                    '<td class="text-nowrap text-end"><button type="button" class="lms-btn ghost sm" data-journal-view="' + t.id + '" aria-expanded="' + open + '" title="Notes"><i class="fas fa-' + (open ? "chevron-up" : "sticky-note") + '"></i></button> ' +
                    '<button type="button" class="lms-btn ghost sm" data-journal-edit="' + t.id + '" title="Edit"><i class="fas fa-pen"></i></button> ' +
                    '<button type="button" class="lms-btn ghost sm text-danger" data-journal-delete="' + t.id + '" title="Delete"><i class="fas fa-trash"></i></button></td></tr>' +
                    (open ? '<tr class="lms-journal-detail"><td colspan="11"><div class="row g-3">' +
                        '<div class="col-md-6"><small>Why did this trade happen?</small>' + (t.tradeReason ? esc(t.tradeReason).replace(/\n/g, "<br>") : (t.notes ? esc(t.notes).replace(/\n/g, "<br>") : '<span class="text-muted">Not recorded</span>')) + '</div>' +
                        '<div class="col-md-6"><small>Why Profit / Loss happened?</small>' + (t.outcomeReason ? esc(t.outcomeReason).replace(/\n/g, "<br>") : '<span class="text-muted">Not recorded</span>') + '</div>' +
                        (t.grossPnl != null ? '<div class="col-12 text-muted small">Gross ' + pnl(t.grossPnl) + ' − charges ' + inr(t.charges || 0) + ' = net ' + pnl(t.netPnl) + '</div>' : '') +
                        '</div></td></tr>' : '');
            }).join("") + '</tbody></table></div>' +
            (j.last ? '' : '<div class="p-3 text-center border-top"><button type="button" class="lms-btn ghost sm" id="journalMoreBtn">Load more trades</button></div>');
        list.querySelectorAll("[data-journal-view]").forEach(function (b) { b.addEventListener("click", function () { j.openId = String(j.openId) === b.dataset.journalView ? null : b.dataset.journalView; renderJournal(); }); });
        list.querySelectorAll("[data-journal-edit]").forEach(function (b) { b.addEventListener("click", function () { editJournal(b.dataset.journalEdit); }); });
        list.querySelectorAll("[data-journal-delete]").forEach(function (b) { b.addEventListener("click", function () { deleteJournal(b.dataset.journalDelete, b); }); });
        var more = el("journalMoreBtn");
        if (more) more.addEventListener("click", function () { more.disabled = true; loadJournal(j.page + 1); });
    }

    /**
     * Live P&L as the student types; the Result follows it until the student picks one themselves.
     * Charges are no longer entered; an older trade being edited keeps the charges it was saved with.
     */
    function updateJournalPreview() {
        var entry = numOrNull("journalEntry"), exit = numOrNull("journalExit"), qty = numOrNull("journalQty"), charges = state.journal.editCharges || 0;
        var box = el("journalPreview");
        if (entry == null || exit == null || !qty) { box.innerHTML = "Enter entry price, quantity and exit price to see the P&amp;L."; return; }
        var gross = Math.round((el("journalType").value === "SELL" ? entry - exit : exit - entry) * qty * 100) / 100;
        var net = Math.round((gross - charges) * 100) / 100;
        box.innerHTML = 'Net P&amp;L <strong>' + pnl(net) + '</strong>' + (charges ? ' <small class="text-muted">(gross ' + inr(gross) + ' − saved charges ' + inr(charges) + ')</small>' : '');
        if (!state.journal.resultTouched) el("journalResult").value = resultFor(net);
    }

    function resetJournalForm() {
        var j = state.journal;
        el("journalForm").reset();
        el("journalDate").value = todayIso();
        el("journalError").classList.add("d-none");
        j.editingId = null; j.editCharges = null; j.resultTouched = false;
        el("journalFormTitle").textContent = "Add a trade";
        el("journalEditing").textContent = "";
        el("journalSubmitBtn").innerHTML = '<i class="fas fa-plus"></i> Save Trade';
        updateJournalPreview();
        if (j.entries.length) renderJournal();
    }

    function editJournal(id) {
        var j = state.journal;
        var t = j.entries.find(function (x) { return String(x.id) === String(id); }); if (!t) return;
        j.editingId = t.id;
        el("journalDate").value = t.tradeDate || todayIso();
        el("journalSegment").value = t.segment || "Equity";
        el("journalSymbol").value = t.symbol || "";
        el("journalType").value = t.tradeType === "SELL" ? "SELL" : "BUY";
        el("journalEntry").value = t.entryPrice != null ? t.entryPrice : "";
        el("journalExit").value = t.exitPrice != null ? t.exitPrice : "";
        el("journalQty").value = t.quantity != null ? t.quantity : "";
        j.editCharges = t.charges != null ? Number(t.charges) : null;
        // A segment saved outside the dropdown list (older entries) is kept as an extra option.
        var seg = el("journalSegment");
        if (t.segment && !Array.prototype.some.call(seg.options, function (o) { return o.value === t.segment; })) seg.add(new Option(t.segment, t.segment));
        if (t.segment) seg.value = t.segment;
        el("journalTradingType").value = TRADING_TYPES[t.tradingType] ? t.tradingType : "";
        el("journalStrategy").value = t.strategy || "";
        el("journalWhy").value = t.tradeReason || t.notes || "";
        el("journalOutcome").value = t.outcomeReason || "";
        // Keep a result the student chose that differs from the P&L; otherwise let it follow the numbers.
        j.resultTouched = !!t.result && t.netPnl != null && t.result !== resultFor(Number(t.netPnl));
        if (t.result) el("journalResult").value = t.result;
        updateJournalPreview();
        el("journalFormTitle").textContent = "Edit trade";
        el("journalEditing").textContent = "Editing " + t.symbol + " · " + fmtDay(t.tradeDate);
        el("journalSubmitBtn").innerHTML = '<i class="fas fa-save"></i> Update Trade';
        el("journalError").classList.add("d-none");
        renderJournal();
        el("journalFormCard").scrollIntoView({ behavior: "smooth", block: "start" });
    }

    function submitJournal(e) {
        e.preventDefault();
        var j = state.journal, btn = el("journalSubmitBtn"), err = el("journalError");
        var body = {
            tradeDate: el("journalDate").value,
            segment: el("journalSegment").value,
            symbol: el("journalSymbol").value.trim(),
            tradeType: el("journalType").value,
            entryPrice: numOrNull("journalEntry"),
            exitPrice: numOrNull("journalExit"),
            quantity: numOrNull("journalQty"),
            // Not asked any more: a new trade has none; an edited older trade keeps what it had.
            charges: j.editingId ? j.editCharges : null,
            tradingType: el("journalTradingType").value || null,
            strategy: el("journalStrategy").value.trim(),
            result: el("journalResult").value,
            tradeReason: el("journalWhy").value.trim(),
            outcomeReason: el("journalOutcome").value.trim()
        };
        var editing = j.editingId;
        btn.disabled = true; err.classList.add("d-none");
        api("/student/journal" + (editing ? "/" + editing : ""), { method: editing ? "PUT" : "POST", body: body }).then(function (res) {
            btn.disabled = false;
            if (!res.success) { err.textContent = res.errors ? Object.values(res.errors).join(" ") : res.message; err.classList.remove("d-none"); return; }
            toast(res.message || (editing ? "Trade entry updated." : "Trade entry saved."));
            resetJournalForm();
            loadJournal(0); loadJournalSummary();
        });
    }

    function deleteJournal(id, btn) {
        var t = state.journal.entries.find(function (x) { return String(x.id) === String(id); });
        if (!window.confirm("Delete this trade" + (t ? " (" + t.symbol + ", " + fmtDay(t.tradeDate) + ")" : "") + "? This cannot be undone.")) return;
        btn.disabled = true;
        api("/student/journal/" + id, { method: "DELETE" }).then(function (res) {
            if (!res.success) { btn.disabled = false; toast(res.message, false); return; }
            toast(res.message || "Trade entry deleted.");
            if (String(state.journal.editingId) === String(id)) resetJournalForm();
            loadJournal(0); loadJournalSummary();
        });
    }

    // ---- profile ---------------------------------------------------------------------------

    function renderProfile() {
        var o = state.overview; if (!o) return;
        var row = function (k, v) { return '<div class="d-flex justify-content-between py-2" style="border-bottom:1px solid #F1F5F9"><span class="text-muted">' + k + '</span><strong class="text-end">' + esc(v || "—") + '</strong></div>'; };
        el("profileCard").innerHTML = '<div class="d-flex align-items-center gap-3 mb-3"><div class="lms-avatar" style="width:56px;height:56px;font-size:20px">' + esc(el("topAvatar").textContent) + '</div><div><strong class="d-block" style="font-size:16px">' + esc(o.fullName) + '</strong><span class="lms-chip ok">Student</span></div></div>' +
            row("Student ID (reference)", o.studentId) + row("User ID (login)", o.loginUserId || "—") + row("Email", o.email) + row("Mobile", o.mobile) + row("Batch", o.batch) + row("Location", o.location) + row("Registered", fmtDate(o.registrationDate)) + row("Last login", fmtDate(o.lastLoginAt));
        el("profileCourses").innerHTML = '<strong class="d-block mb-2">Enrollment record</strong>' + (state.courses.length ? '<div class="lms-table-wrap"><table class="lms-table"><thead><tr><th>Course</th><th>Status</th><th>Enrolled</th><th>Progress</th></tr></thead><tbody>' +
            state.courses.map(function (c) { return '<tr><td>' + esc(c.courseName) + '</td><td>' + chip(c.enrollmentStatus) + '</td><td>' + fmtDate(c.enrolledAt) + '</td><td>' + c.progressPercent + '%</td></tr>'; }).join("") + '</tbody></table></div>' : '<div class="text-muted small">No enrollments.</div>') +
            '<strong class="d-block mb-2 mt-3">Ebook record</strong>' + (state.ebooks.length ? '<div class="lms-table-wrap"><table class="lms-table"><thead><tr><th>Ebook</th><th>Status</th><th>Purchased</th><th>Invoice</th></tr></thead><tbody>' +
            state.ebooks.map(function (e) { return '<tr><td>' + esc(e.title) + '</td><td>' + chip(e.status) + '</td><td>' + fmtDate(e.purchasedAt) + '</td><td class="font-monospace">' + esc(e.invoiceNumber || "—") + '</td></tr>'; }).join("") + '</tbody></table></div>' : '<div class="text-muted small">No ebooks.</div>');
    }

    // ---- doubt desk ------------------------------------------------------------------------

    function fillDoubtCourses() {
        var sel = el("doubtCourse");
        sel.innerHTML = '<option value="">General (no specific course)</option>' + state.courses.filter(hasAccess).map(function (c) { return '<option value="' + c.courseId + '">' + esc(c.courseName) + '</option>'; }).join("");
    }

    function loadDoubts() {
        return api("/student/doubts?size=50").then(function (res) {
            var list = el("doubtsList");
            if (!res.success) { list.innerHTML = '<div class="lms-empty text-danger">' + esc(res.message) + '</div>'; return; }
            state.doubts = (res.data && res.data.content) || [];
            if (!state.doubts.length) { list.innerHTML = '<div class="lms-empty"><i class="fas fa-comments"></i>You have not asked any doubts yet.</div>'; }
            else list.innerHTML = state.doubts.map(function (d) {
                return '<div class="lms-list-item"><div class="ico"><i class="fas fa-question"></i></div><div class="txt"><strong>' + esc(d.title) + '</strong><small>' + fmtDate(d.createdAt) + (d.courseName ? " · " + esc(d.courseName) : "") + (d.replyCount ? ' · <span class="text-primary">' + d.replyCount + ' repl' + (d.replyCount === 1 ? "y" : "ies") + '</span>' : "") + '</small></div>' + chip(d.status) + '<button class="lms-btn ghost sm" data-doubt="' + d.id + '">Open</button></div>';
            }).join("");
            list.querySelectorAll("[data-doubt]").forEach(function (b) { b.addEventListener("click", function () { openDoubt(b.dataset.doubt); }); });
            var box = el("dashDoubts");
            var open = state.doubts.filter(function (d) { return d.status !== "RESOLVED"; }).slice(0, 3);
            box.innerHTML = open.length ? open.map(function (d) { return '<div class="lms-list-item"><div class="ico"><i class="fas fa-question"></i></div><div class="txt"><strong>' + esc(d.title) + '</strong><small>' + fmtDate(d.createdAt) + '</small></div>' + chip(d.status) + '</div>'; }).join("")
                : '<div class="lms-empty" style="padding:22px">No open doubts. Stuck on something? Ask the mentor.</div>';
        });
    }

    function openDoubt(id) {
        api("/student/doubts/" + id).then(function (res) {
            if (!res.success) { toast(res.message, false); return; }
            var d = res.data;
            el("doubtThreadTitle").textContent = d.title;
            el("doubtReplyId").value = d.id;
            var html = '<div class="mb-2">' + chip(d.status) + ' <small class="text-muted">' + fmtDate(d.createdAt) + (d.courseName ? " · " + esc(d.courseName) : "") + '</small></div>' +
                '<div class="lms-msg"><small>You asked</small>' + esc(d.description).replace(/\n/g, "<br>") + '</div>';
            (d.replies || []).forEach(function (r) {
                var mentor = r.authorRole !== "STUDENT";
                html += '<div class="lms-msg' + (mentor ? " mentor" : "") + '"><small>' + (mentor ? '<i class="fas fa-user-tie me-1"></i>' : "") + esc(r.authorName) + ' · ' + fmtDate(r.createdAt) + '</small>' + esc(r.message).replace(/\n/g, "<br>") + '</div>';
            });
            el("doubtThreadBody").innerHTML = html;
            el("doubtReplyForm").style.display = d.status === "RESOLVED" ? "none" : "";
            doubtModal.show();
        });
    }

    function submitDoubt(e) {
        e.preventDefault();
        var btn = el("doubtSubmitBtn");
        var body = { title: el("doubtTitle").value.trim(), description: el("doubtDescription").value.trim() };
        if (el("doubtCourse").value) body.courseId = Number(el("doubtCourse").value);
        btn.disabled = true;
        api("/student/doubts", { method: "POST", body: body }).then(function (res) {
            btn.disabled = false;
            if (!res.success) { el("doubtError").textContent = res.errors ? Object.values(res.errors).join(" ") : res.message; el("doubtError").classList.remove("d-none"); return; }
            el("doubtError").classList.add("d-none"); el("newDoubtForm").reset(); toast("Doubt submitted to the mentor desk.");
            loadDoubts(); refreshOverview();
        });
    }

    function submitReply(e) {
        e.preventDefault();
        var id = el("doubtReplyId").value, text = el("doubtReplyText").value.trim();
        if (!text) return;
        api("/student/doubts/" + id + "/replies", { method: "POST", body: { message: text } }).then(function (res) {
            if (!res.success) { toast(res.message, false); return; }
            el("doubtReplyText").value = ""; openDoubt(id); loadDoubts();
        });
    }

    function refreshOverview() {
        api("/student/profile").then(function (r) { if (r.success) { state.overview = r.data; renderHeader(); } });
    }

    // ---- search ----------------------------------------------------------------------------

    function search(q) {
        var box = el("lmsSearchResults");
        q = q.trim().toLowerCase();
        if (q.length < 2) { box.style.display = "none"; return; }
        var hits = [];
        state.courses.forEach(function (c) {
            if (c.courseName.toLowerCase().indexOf(q) >= 0) hits.push({ t: c.courseName, s: "Course", href: learnUrl(c, c.nextLessonId), i: "fa-book" });
            var content = state.content[c.courseId]; if (!content) return;
            content.modules.forEach(function (m) {
                m.lessons.forEach(function (l) {
                    if (l.lessonTitle.toLowerCase().indexOf(q) >= 0) hits.push({ t: l.lessonTitle, s: c.courseName + " · " + m.moduleName, href: learnUrl(c, l.id), i: "fa-play-circle" });
                    // Handouts open in the lesson page's watermarked viewer.
                    if (l.materialOriginalName && l.materialOriginalName.toLowerCase().indexOf(q) >= 0) hits.push({ t: l.materialOriginalName, s: "Handout · " + m.moduleName, href: learnUrl(c, l.id) + "&material=1", i: "fa-file-pdf" });
                });
            });
        });
        state.ebooks.forEach(function (e) {
            if ((e.title || "").toLowerCase().indexOf(q) >= 0) hits.push({ t: e.title, s: "Ebook", href: "#ebooks", view: "ebooks", i: "fa-book" });
        });
        state.certificates.forEach(function (c) {
            if ((c.courseName + " " + c.certificateNumber).toLowerCase().indexOf(q) >= 0) hits.push({ t: c.certificateNumber, s: "Certificate · " + c.courseName, href: "#certificates", view: "certificates", i: "fa-award" });
        });
        if (!ebookOnly() && ("certificate".indexOf(q) >= 0 || "course completion certificate".indexOf(q) >= 0)) hits.push({ t: "Certificate", s: "Apply for, view and download your course completion certificate", href: "#certificates", view: "certificates", i: "fa-award" });
        if (!ebookOnly() && ("student general".indexOf(q) >= 0 || "student journal".indexOf(q) >= 0 || "trading journal".indexOf(q) >= 0)) hits.push({ t: "Student Journal", s: "Daily trading journal", href: "#journal", view: "journal", i: "fa-book-open" });
        box.innerHTML = hits.slice(0, 12).map(function (h) { return '<a href="' + h.href + '"' + (h.view ? ' data-view="' + h.view + '"' : "") + '><i class="fas ' + h.i + ' me-2 text-muted"></i>' + esc(h.t) + '<br><small>' + esc(h.s) + '</small></a>'; }).join("") || '<a href="#" class="text-muted">No matches</a>';
        box.style.display = "block";
        bindViewLinks(box);
    }

    // ---- boot ------------------------------------------------------------------------------

    document.addEventListener("DOMContentLoaded", function () {
        if (!LSI_Auth.isAuthenticated()) return;
        doubtModal = new bootstrap.Modal(el("doubtThreadModal"));
        readerModal = new bootstrap.Modal(el("ebookReaderModal"));
        el("ebookZoomOut").addEventListener("click", function () { zoomReader(-1); });
        el("ebookZoomIn").addEventListener("click", function () { zoomReader(1); });
        el("ebookPortrait").addEventListener("click", function () { orientReader(false); });
        el("ebookLandscape").addEventListener("click", function () { orientReader(true); });
        var readerResize;
        window.addEventListener("resize", function () {
            if (!reader.pdf) return;
            clearTimeout(readerResize);
            readerResize = setTimeout(function () {
                if (!reader.chosen) reader.landscape = readerIsWide();
                layoutReader(currentReaderPage());
            }, 250);
        });
        el("ebookReaderModal").addEventListener("hidden.bs.modal", function () {
            closeReader();
            el("ebookReaderFrame").src = "about:blank";
            if (readerBlobUrl) { URL.revokeObjectURL(readerBlobUrl); readerBlobUrl = null; }
        });
        el("newDoubtForm").addEventListener("submit", submitDoubt);
        el("doubtReplyForm").addEventListener("submit", submitReply);
        certApplyModal = new bootstrap.Modal(el("certApplyModal"));
        el("certApplyForm").addEventListener("submit", submitCertApply);
        el("certApplyName").addEventListener("input", updateCertNameNote);
        buyModal = new bootstrap.Modal(el("ebookBuyModal"));
        el("ebookBuyForm").addEventListener("submit", submitBuy);
        el("ebookBuyRetry").addEventListener("click", function () { el("ebookBuyError").classList.add("d-none"); buyStep("Details"); checkBuyMode(); });
        el("ebookBuyRead").addEventListener("click", function () {
            var id = shop.bought;
            el("ebookBuyModal").addEventListener("hidden.bs.modal", function () { openEbook(id); }, { once: true });
            buyModal.hide();
        });

        el("journalForm").addEventListener("submit", submitJournal);
        el("journalClearBtn").addEventListener("click", resetJournalForm);
        ["journalEntry", "journalQty", "journalExit"].forEach(function (id) { el(id).addEventListener("input", updateJournalPreview); });
        el("journalType").addEventListener("change", updateJournalPreview);
        el("journalResult").addEventListener("change", function () { state.journal.resultTouched = true; });
        el("journalSearch").addEventListener("input", renderJournal);

        bindViewLinks(el("lmsSidebar"));
        bindViewLinks(el("view-dashboard"));
        el("lmsMenuBtn").addEventListener("click", function () { el("lmsSidebar").classList.toggle("open"); el("lmsBackdrop").classList.toggle("show"); });
        el("lmsBackdrop").addEventListener("click", function () { el("lmsSidebar").classList.remove("open"); el("lmsBackdrop").classList.remove("show"); });

        var timer;
        el("lmsSearch").addEventListener("input", function () { var q = this.value; clearTimeout(timer); timer = setTimeout(function () { search(q); }, 200); });
        document.addEventListener("click", function (e) { if (!e.target.closest(".lms-search")) el("lmsSearchResults").style.display = "none"; });

        el("changePasswordForm").addEventListener("submit", function (e) {
            e.preventDefault();
            var err = el("cpError"); err.classList.add("d-none");
            if (el("cpNew").value !== el("cpConfirm").value) { err.textContent = "The new passwords do not match."; err.classList.remove("d-none"); return; }
            LSI_Auth.changePassword(el("cpCurrent").value, el("cpNew").value).then(function (r) {
                if (sessionEnded(r, err)) return;
                if (!r.success) { err.textContent = r.errors ? Object.values(r.errors).join(" ") : r.message; err.classList.remove("d-none"); return; }
                toast(r.message || "Password changed."); setTimeout(function () { LSI_Auth.logout(); }, 1200);
            });
        });

        el("changeUserIdForm").addEventListener("submit", function (e) {
            e.preventDefault();
            var err = el("cuError"); err.classList.add("d-none");
            var fail = function (m) { err.textContent = m; err.classList.remove("d-none"); };
            var mine = String((state.overview && (state.overview.loginUserId || state.overview.studentId)) || "").toLowerCase();
            var cur = el("cuCurrent").value.trim(), next = el("cuNew").value.trim(), again = el("cuConfirm").value.trim();
            if (mine && cur.toLowerCase() !== mine) return fail("The current User ID does not match your account.");
            if (next !== again) return fail("The new User IDs do not match.");
            if (mine && next.toLowerCase() === mine) return fail("The new User ID is the same as your current one.");
            var btn = this.querySelector('[type="submit"]'); btn.disabled = true;
            api("/student/change-user-id", { method: "POST", body: { currentUserId: cur, newUserId: next, confirmUserId: again, currentPassword: el("cuPassword").value } }).then(function (r) {
                btn.disabled = false;
                el("cuPassword").value = "";
                if (sessionEnded(r, err)) return;
                if (!r.success) { fail(r.errors ? Object.values(r.errors).join(" ") : (r.message || "Could not change your User ID. Please try again.")); return; }
                el("changeUserIdForm").reset();
                toast(r.message || "User ID updated.");
                // The token is keyed on the account, not the ID, so the session stays valid; refresh the cached profile and header.
                LSI_Auth.refreshProfile().then(refreshOverview);
            });
        });
        el("changeUserIdForm").addEventListener("reset", function () { el("cuError").classList.add("d-none"); });

        loadAll().then(function () {
            var initial = (location.hash || "#dashboard").replace("#", "");
            show(initial);
        });
        loadDoubts();
    });
})(window);
