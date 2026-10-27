/**
 * LORD SAI ACADEMY — CHECKOUT (js/checkout.js)
 *
 * One checkout for every digital product (courses on courses.html, ebooks on store.html):
 * BUY -> details form -> POST /payments/create-order -> Razorpay Checkout
 *     -> POST /payments/verify (server validates signature) -> success screen
 *
 * The browser never decides the price and never marks a payment successful; both come from
 * the backend. If verification fails, nothing is granted.
 *   .lsi-buy-course[data-course-code]  -> productType COURSE
 *   .lsi-buy-ebook[data-ebook-id]      -> productType EBOOK
 */
(function () {
    "use strict";

    var api = LSI_Auth.api;
    var modalEl, modal, courses = {}, ebooks = {}, selected = null, lastOrder = null, lastPurchaseEmail = null;
    var paymentMode = null; // "DEMO" | "RAZORPAY", from GET /payments/mode

    function el(id) { return document.getElementById(id); }
    function inr(v) { return "₹" + Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 }); }
    function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

    function showStep(name) {
        ["Details", "Verifying", "Success", "Failed", "Cancelled"].forEach(function (s) {
            el("purchaseStep" + s).classList.toggle("d-none", s !== name);
        });
    }

    function setBusy(busy) {
        el("purchasePayBtn").disabled = busy;
        el("purchasePayText").classList.toggle("d-none", busy);
        el("purchasePaySpinner").classList.toggle("d-none", !busy);
    }

    function showError(msg) {
        var e = el("purchaseError");
        e.innerText = msg; e.classList.remove("d-none");
    }

    // ---- Course Purchase switch (Main Admin > Courses) -----------------------------------------
    // Comes from the server with the course list (purchaseEnabled), so every visitor sees the same
    // state, and the payment API refuses a closed course anyway. Until the list has loaded a course
    // counts as open (the server still has the final say).
    var OLD_SWITCH_KEY = "shareMarketCoursePurchaseEnabled";   // the old switch, kept only in a browser

    function isCoursePurchaseOpen(code) {
        var c = courses[code];
        return !c || c.purchaseEnabled !== false;
    }

    /** The old browser-only switch could leave a stale "off" here that disabled the Buy button. */
    function forgetOldBrowserSwitch() {
        try { localStorage.removeItem(OLD_SWITCH_KEY); } catch (e) {}
        try { sessionStorage.removeItem(OLD_SWITCH_KEY); } catch (e) {}
        try {
            if (document.cookie.indexOf(OLD_SWITCH_KEY + "=") >= 0) {
                document.cookie = OLD_SWITCH_KEY + "=; path=/; max-age=0; SameSite=Lax";
                if (location.hostname === "localhost") document.cookie = OLD_SWITCH_KEY + "=; path=/; domain=localhost; max-age=0; SameSite=Lax";
            }
        } catch (e) {}
    }

    function applyCoursePurchaseState() {
        if (typeof window.LSI_updateCourseUI === "function") {   // courses.html: banner, badges and buttons
            window.LSI_updateCourseUI();
            renderCoursePrices();
            return;
        }
        document.querySelectorAll(".lsi-buy-course").forEach(function (btn) {
            var open = isCoursePurchaseOpen(btn.getAttribute("data-course-code"));
            if (!btn._lsiOriginalHtml) {
                btn._lsiOriginalHtml = btn.innerHTML;
                btn._lsiOriginalClass = btn.className;
            }
            if (!open) {
                btn.disabled = true;
                btn.setAttribute("aria-disabled", "true");
                btn.classList.add("disabled");
                btn.classList.remove("btn-buy-course-red");
                btn.classList.add("btn-secondary");
                btn.style.cursor = "not-allowed";
                btn.style.opacity = "0.85";
                btn.innerHTML = '<i class="fas fa-ban me-2"></i> Admissions Currently Closed (Purchase Disabled)';
            } else {
                btn.disabled = false;
                btn.removeAttribute("aria-disabled");
                btn.className = btn._lsiOriginalClass;
                btn.style.cursor = "";
                btn.style.opacity = "";
                btn.innerHTML = btn._lsiOriginalHtml;
            }
        });
        renderCoursePrices();
    }

    /**
     * Writes each loaded course's live price into the page. Inside any [data-course-code] element:
     *   .lsi-course-price          -> the price charged
     *   [data-price-text]          -> its English template with {price} / {listPrice} filled in
     *                                 (lang.js translates it again, the "{#}" keys match any number)
     *   .lsi-course-discount-only  -> hidden when there is no discount
     * Runs again after button HTML is restored, since that snapshot still holds the page's fallback price.
     */
    function renderCoursePrices() {
        Object.keys(courses).forEach(function (code) {
            var c = courses[code], scope = '[data-course-code="' + code + '"] ';
            var values = { price: inr(c.effectivePrice), listPrice: inr(c.price) };
            var discount = c.discountedPrice && Number(c.discountedPrice) < Number(c.price);
            document.querySelectorAll(scope + ".lsi-course-price").forEach(function (node) {
                if (node.textContent !== values.price) node.textContent = values.price;
            });
            document.querySelectorAll(scope + "[data-price-text]").forEach(function (node) {
                var text = node.getAttribute("data-price-text").replace(/\{(price|listPrice)\}/g, function (m, k) { return values[k]; });
                if (node.textContent !== text) node.textContent = text;
            });
            document.querySelectorAll(scope + ".lsi-course-discount-only").forEach(function (node) {
                node.classList.toggle("d-none", !discount);
            });
        });
    }

    /** Pulls live prices so the page never shows a stale number after the admin edits a product. */
    function loadCatalog() {
        var wantCourses = document.querySelector(".lsi-buy-course") || !document.querySelector(".lsi-buy-ebook");
        var wantEbooks = document.querySelector(".lsi-buy-ebook") || document.getElementById("lsiEbookCatalog");
        var calls = [];
        if (wantCourses) calls.push(api("/public/courses", { skipAuthRedirect: true }).then(function (res) {
            if (!res.success || !res.data) return;
            res.data.forEach(function (c) {
                c.productType = "COURSE"; c.productName = c.courseName;
                courses[c.courseCode] = c;
            });
            applyCoursePurchaseState();   // also renders the prices
        }));
        if (wantEbooks) calls.push(api("/public/ebooks", { skipAuthRedirect: true }).then(function (res) {
            if (!res.success || !res.data) return;
            res.data.forEach(function (e) {
                e.productType = "EBOOK"; e.productName = e.title;
                ebooks[String(e.id)] = e;
                document.querySelectorAll('.lsi-buy-ebook[data-ebook-id="' + e.id + '"] .lsi-ebook-price')
                    .forEach(function (span) { span.innerText = inr(e.effectivePrice); });
            });
            renderEbookCatalog(res.data);
        }));
        return Promise.all(calls);
    }

    /** store.html: renders the admin-managed ebook catalogue into #lsiEbookCatalog (if present). */
    function renderEbookCatalog(list) {
        var grid = document.getElementById("lsiEbookCatalog");
        if (!grid) return;
        var mediaUrl = function (p) { return p && p.indexOf("images/") === 0 ? LSI_Auth.apiBase + "/public/" + p : p; };
        if (!list.length) {
            grid.innerHTML = '<div class="col-12"><div class="alert alert-light border text-muted small mb-0"><i class="fas fa-book me-1"></i> No ebooks are on sale right now. Please check back soon.</div></div>';
            return;
        }
        grid.innerHTML = list.map(function (e) {
            var discount = e.discountedPrice && Number(e.discountedPrice) < Number(e.price);
            return '<div class="col-lg-4 col-md-6"><div class="card p-3 h-100 shadow-sm border bg-white lsi-ebook-card">' +
                '<div class="d-flex gap-3 align-items-start mb-3">' +
                (e.coverImagePath ? '<img src="' + esc(mediaUrl(e.coverImagePath)) + '" alt="" class="rounded border" style="width:84px;height:112px;object-fit:cover;flex-shrink:0;">'
                    : '<div class="rounded border d-flex align-items-center justify-content-center bg-light text-primary" style="width:84px;height:112px;flex-shrink:0;font-size:28px;"><i class="fas fa-book"></i></div>') +
                '<div class="flex-grow-1 min-w-0">' + (e.category ? '<span class="badge bg-primary mb-2">' + esc(e.category) + '</span>' : '') +
                '<h5 class="fw-bold text-dark mb-1" style="font-size:16px;">' + esc(e.title) + '</h5>' +
                (e.author ? '<div class="text-muted small mb-1"><i class="fas fa-pen-nib me-1"></i>' + esc(e.author) + '</div>' : '') +
                (e.language ? '<div class="text-muted small"><i class="fas fa-language me-1"></i>' + esc(e.language) + '</div>' : '') + '</div></div>' +
                '<p class="text-muted small mb-3">' + esc(e.shortDescription || "") + '</p>' +
                '<div class="mt-auto d-flex justify-content-between align-items-center gap-2">' +
                '<div><strong class="text-primary fs-5 lsi-ebook-price">' + inr(e.effectivePrice) + '</strong>' + (discount ? ' <small class="text-muted text-decoration-line-through">' + inr(e.price) + '</small>' : '') + '</div>' +
                '<button type="button" class="btn btn-sm btn-primary rounded-pill px-3 lsi-buy-ebook" data-ebook-id="' + e.id + '"><i class="fas fa-shopping-cart me-1"></i> Buy Ebook</button>' +
                '</div><div class="text-muted mt-2" style="font-size:11px;"><i class="fas fa-lock me-1"></i> Readable only inside your Student Portal after login.</div></div></div>';
        }).join("");
        grid.querySelectorAll(".lsi-buy-ebook").forEach(function (btn) {
            btn.addEventListener("click", function () { openModal(ebooks[btn.getAttribute("data-ebook-id")]); });
        });
    }

    /** Asks the backend which mode is active; the backend enforces it regardless of what we render. */
    function loadMode() {
        return api("/payments/mode", { skipAuthRedirect: true }).then(function (res) {
            paymentMode = res.success && res.data ? res.data.mode : null;
            return res.success ? res.data : null;
        });
    }

    function renderMode(info) {
        var banner = el("purchaseModeBanner"), btn = el("purchasePayText"), foot = el("purchaseFootnote");
        var amount = el("purchasePayAmount") ? el("purchasePayAmount").innerText : "";
        if (!info) {
            banner.className = "alert alert-warning py-2 px-3 small";
            banner.innerHTML = "<i class=\"fas fa-exclamation-triangle me-1\"></i> Payment gateway is temporarily unavailable. Please try again.";
            el("purchasePayBtn").disabled = true;
            return;
        }
        el("purchasePayBtn").disabled = false;
        if (info.mode === "DEMO") {
            banner.className = "alert alert-warning py-2 px-3 small";
            banner.innerHTML = "<strong><i class=\"fas fa-flask me-1\"></i> Demo Payment Mode</strong><br>" + esc(info.message);
            btn.innerHTML = "<i class=\"fas fa-check-circle me-2\"></i> Complete Demo Payment — <span id=\"purchasePayAmount\">" + esc(amount) + "</span>";
            foot.innerHTML = "<i class=\"fas fa-info-circle me-1\"></i> No real money will be charged. Your enrollment and login email will be created exactly as in a real purchase.";
        } else {
            banner.className = "alert alert-success py-2 px-3 small";
            banner.innerHTML = "<strong><i class=\"fas fa-shield-alt me-1\"></i> Secure payment powered by Razorpay.</strong><br>UPI, cards, net banking and wallets accepted.";
            btn.innerHTML = "<i class=\"fas fa-lock me-2\"></i> Pay Now — <span id=\"purchasePayAmount\">" + esc(amount) + "</span>";
            foot.innerHTML = "<i class=\"fas fa-shield-alt me-1\"></i> Payments are processed by Razorpay. We never see or store your card details.";
        }
        banner.classList.remove("d-none");
    }

    /** Alerts are not page text, so ask the language engine for the translation directly. */
    function tr(text) {
        return window.LSI_Lang && typeof window.LSI_Lang.t === "function" ? window.LSI_Lang.t(text) : text;
    }

    function openModal(product) {
        selected = product;
        if (!selected) {
            alert(tr("This product is not available for online purchase right now. Please contact the academy."));
            return;
        }
        if (selected.productType === "COURSE" && selected.purchaseEnabled === false) {
            alert(tr("Purchase is currently unavailable for this course. Please contact the academy."));
            return;
        }
        var isEbook = selected.productType === "EBOOK";
        var label = el("purchaseProductLabel"); if (label) label.innerText = isEbook ? "Ebook" : "Course";
        var title = el("purchaseModalLabel"); if (title) title.innerText = isEbook ? "Buy Ebook" : "Buy Course";
        var benefits = el("purchaseBenefits");
        if (benefits) benefits.innerHTML = isEbook
            ? '<li class="mb-1"><i class="fas fa-check text-success me-1"></i> Read inside your Student Portal any time</li><li class="mb-1"><i class="fas fa-check text-success me-1"></i> Invoice emailed as PDF</li><li><i class="fas fa-check text-success me-1"></i> Same account for courses &amp; ebooks</li>'
            : '<li class="mb-1"><i class="fas fa-check text-success me-1"></i> Lifetime access to lesson videos</li><li class="mb-1"><i class="fas fa-check text-success me-1"></i> Module handouts &amp; PDFs</li><li class="mb-1"><i class="fas fa-check text-success me-1"></i> Trade journal with mentor review</li><li><i class="fas fa-check text-success me-1"></i> Doubt desk support</li>';
        el("purchaseCourseName").innerText = selected.productName;
        el("purchaseTotal").innerText = inr(selected.effectivePrice);
        el("purchasePayAmount").innerText = inr(selected.effectivePrice);
        var list = el("purchaseListPrice");
        if (selected.discountedPrice && Number(selected.discountedPrice) < Number(selected.price)) {
            list.innerText = inr(selected.price); list.parentElement.classList.remove("d-none");
        } else {
            list.parentElement.classList.add("d-none");
        }
        el("purchaseError").classList.add("d-none");
        el("purchaseForm").classList.remove("was-validated");
        // Only a logged-in student buying for their own account gets their details filled in. An admin
        // or office session on the same browser must not put the staff account into the form — the
        // fields stay empty and show their placeholder hints.
        var user = LSI_Auth.getCurrentUser();
        if (user && user.role === "student") {
            el("purchaseName").value = user.name || "";
            el("purchaseEmail").value = user.email || "";
            el("purchaseMobile").value = (user.mobile || "").replace(/\D/g, "").slice(-10);
        }
        showStep("Details");
        setBusy(false);
        el("purchaseModeBanner").classList.add("d-none");
        modal.show();
        loadMode().then(renderMode);
    }

    function loadRazorpay() {
        if (window.Razorpay) return Promise.resolve();
        return new Promise(function (resolve, reject) {
            var s = document.createElement("script");
            s.src = LSI_CONFIG.RAZORPAY_CHECKOUT_JS;
            s.onload = resolve;
            s.onerror = function () { reject(new Error("Could not load the payment gateway. Please check your connection.")); };
            document.head.appendChild(s);
        });
    }

    function startPayment(e) {
        e.preventDefault();
        var form = el("purchaseForm");
        el("purchaseError").classList.add("d-none");
        if (!form.checkValidity()) { form.classList.add("was-validated"); return; }

        var body = {
            productType: selected.productType,
            fullName: el("purchaseName").value.trim(),
            email: el("purchaseEmail").value.trim(),
            mobile: el("purchaseMobile").value.trim()
        };
        if (selected.productType === "EBOOK") body.ebookId = selected.id; else body.courseId = selected.id;
        setBusy(true);

        if (paymentMode === "DEMO") {
            completeDemoPayment(body);
            return;
        }

        Promise.all([api("/payments/create-order", { method: "POST", body: body, skipAuthRedirect: true }), loadRazorpay()])
            .then(function (results) {
                var res = results[0];
                if (!res.success) {
                    var msg = res.errors ? Object.values(res.errors).join(" ") : res.message;
                    throw new Error(msg || "Could not start the payment.");
                }
                lastOrder = res.data;
                openRazorpay(res.data);
            })
            .catch(function (err) { setBusy(false); showError(err.message); });
    }

    function openRazorpay(order) {
        var rzp = new window.Razorpay({
            key: order.razorpayKeyId,
            amount: order.amountPaise,
            currency: order.currency,
            name: LSI_CONFIG.BRAND_NAME,
            description: order.productName || order.courseName,
            image: LSI_CONFIG.BRAND_LOGO,
            order_id: order.razorpayOrderId,
            prefill: { name: order.customerName, email: order.customerEmail, contact: order.customerMobile },
            notes: { order_ref: order.orderRef },
            theme: { color: LSI_CONFIG.BRAND_COLOR },
            modal: {
                ondismiss: function () {
                    setBusy(false);
                    showStep("Cancelled");
                }
            },
            handler: function (response) { verifyPayment(response); }
        });
        rzp.on("payment.failed", function (resp) {
            setBusy(false);
            el("purchaseFailedMessage").innerText = "Payment was not completed. " + ((resp.error && resp.error.description) || "The payment was declined by your bank or wallet.") + " Nothing has been added to your account.";
            showStep("Failed");
        });
        rzp.open();
    }

    /** Demo mode only. The backend refuses this call the moment Razorpay is configured. */
    function completeDemoPayment(body) {
        showStep("Verifying");
        api("/payments/demo-complete", { method: "POST", body: body, skipAuthRedirect: true }).then(function (res) {
            if (!res.success || !res.data) {
                setBusy(false);
                if (res.status === 409 && /Real payments are active/i.test(res.message || "")) {
                    // Admin switched modes while this modal was open: reload the mode and let the student retry.
                    showStep("Details"); loadMode().then(renderMode);
                    showError("The payment mode has just changed. Please click Pay Now again.");
                    return;
                }
                el("purchaseFailedMessage").innerText = (res.errors ? Object.values(res.errors).join(" ") : res.message) || "The demo payment could not be completed.";
                showStep("Failed");
                return;
            }
            showSuccess(res.data);
        });
    }

    function showSuccess(d) {
        var isEbook = d.productType === "EBOOK";
        el("purchaseSuccessMessage").innerText = d.message || "Your purchase is active.";
        el("successCourse").innerText = d.productName || d.courseName;
        var pl = el("successProductLabel"); if (pl) pl.innerText = isEbook ? "Ebook" : "Course";
        var al = el("successAccessLabel"); if (al) al.innerText = isEbook ? "Ebook access" : "Enrollment";
        var inv = el("successInvoice"); if (inv) inv.innerText = d.invoiceNumber || "—";
        el("successStudentId").innerText = d.studentId || "—";
        el("successOrderRef").innerText = d.orderRef;
        el("successEmail").innerText = d.email;

        // The backend reports whether the setup email actually went out. When it did not, say so
        // and offer a resend instead of sending the student to an inbox that will stay empty.
        var emailFailed = d.setupEmailSent === false;
        el("successEmailLabel").innerText = emailFailed ? "Registered email" : "Email sent to";
        el("successEmailWarning").classList.toggle("d-none", !emailFailed);
        el("successResendResult").classList.add("d-none");
        el("successResendBtn").disabled = false;
        lastPurchaseEmail = d.email;

        el("successHint").innerText = (emailFailed
            ? (isEbook
                ? "Your ebook account email contains a fresh initial password and the invoice."
                : "Your Student ID above is only a reference number. Once you receive the setup email, create your password and log in with the User ID given in that email.")
            : d.newAccount
                ? (isEbook
                    ? "Open the email and log in with the User ID and initial password it contains, then change the password. Your invoice is attached to the email."
                    : "Open the email, set your password, then log in with the User ID from the email (or your email address). Your invoice is attached to the email.")
                : "Log in with your User ID (or email) and your existing password — the new " + (isEbook ? "ebook" : "course") + " is already in your dashboard and the invoice has been emailed.")
            + " Purchased courses and ebooks can be accessed only inside the official Student Portal after login.";
        var title = document.querySelector("#purchaseStepSuccess h4");
        if (title) title.innerText = d.paymentMode === "DEMO" ? "Demo Payment Successful" : "Payment Successful";
        showStep("Success");
    }

    function resendSetupEmail() {
        if (!lastPurchaseEmail) return;
        var btn = el("successResendBtn"), out = el("successResendResult");
        btn.disabled = true;
        out.classList.remove("d-none");
        out.className = "small mt-2 text-muted";
        out.innerText = "Sending…";
        api("/auth/resend-setup", { method: "POST", body: { email: lastPurchaseEmail }, skipAuthRedirect: true })
            .then(function (res) {
                btn.disabled = false;
                out.className = "small mt-2 " + (res.success ? "text-success" : "text-danger");
                out.innerText = res.message || (res.success ? "Setup email sent." : "Could not send the email.");
            });
    }

    function verifyPayment(rzpResponse) {
        showStep("Verifying");
        api("/payments/verify", {
            method: "POST",
            skipAuthRedirect: true,
            body: {
                razorpayOrderId: rzpResponse.razorpay_order_id,
                razorpayPaymentId: rzpResponse.razorpay_payment_id,
                razorpaySignature: rzpResponse.razorpay_signature
            }
        }).then(function (res) {
            if (!res.success || !res.data) {
                el("purchaseFailedMessage").innerText = res.message || "We could not verify this payment.";
                showStep("Failed");
                return;
            }
            showSuccess(res.data);
        });
    }

    function initCheckout() {
        forgetOldBrowserSwitch();
        applyCoursePurchaseState();

        modalEl = el("purchaseModal");
        if (modalEl && typeof bootstrap !== "undefined" && bootstrap.Modal) {
            try {
                modal = new bootstrap.Modal(modalEl);
            } catch (e) {}
        }

        loadCatalog();
        document.querySelectorAll(".lsi-buy-course").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var code = btn.getAttribute("data-course-code");
                if (!isCoursePurchaseOpen(code)) return;   // closed by the Main Admin (button is disabled too)
                if (courses[code]) openModal(courses[code]);
                else loadCatalog().then(function () { openModal(courses[code]); });
            });
        });
        document.querySelectorAll(".lsi-buy-ebook").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var id = btn.getAttribute("data-ebook-id");
                if (ebooks[id]) openModal(ebooks[id]);
                else loadCatalog().then(function () { openModal(ebooks[id]); });
            });
        });
        if (el("purchaseForm")) el("purchaseForm").addEventListener("submit", startPayment);
        if (el("successResendBtn")) el("successResendBtn").addEventListener("click", resendSetupEmail);
        if (el("purchaseRetryBtn")) el("purchaseRetryBtn").addEventListener("click", function () { showStep("Details"); setBusy(false); });
        if (el("purchaseCancelRetryBtn")) el("purchaseCancelRetryBtn").addEventListener("click", function () { showStep("Details"); setBusy(false); });
        if (el("purchaseMobile")) el("purchaseMobile").addEventListener("input", function () { this.value = this.value.replace(/\D/g, "").slice(0, 10); });

    }

    applyCoursePurchaseState();
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initCheckout);
    } else {
        initCheckout();
    }

    window.LSI_isCoursePurchaseOpen = isCoursePurchaseOpen;
    window.LSI_renderCoursePrices = renderCoursePrices;
})();
