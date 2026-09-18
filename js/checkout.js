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
                document.querySelectorAll('.lsi-buy-course[data-course-code="' + c.courseCode + '"] .lsi-course-price')
                    .forEach(function (span) { span.innerText = inr(c.effectivePrice); });
            });
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

    function openModal(product) {
        selected = product;
        if (!selected) {
            alert("This product is not available for online purchase right now. Please contact the academy.");
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
        var user = LSI_Auth.getCurrentUser();
        if (user) {
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
                : "Note your Student ID above. Once you receive the setup email you can create your password and log in.")
            : d.newAccount
                ? (isEbook
                    ? "Open the email, use the initial password to log in, then change it. Your invoice is attached to the email."
                    : "Open the email, set your password, then log in with your Student ID or email. Your invoice is attached to the email.")
                : "Log in with your existing password — the new " + (isEbook ? "ebook" : "course") + " is already in your dashboard and the invoice has been emailed.")
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

    document.addEventListener("DOMContentLoaded", function () {
        modalEl = el("purchaseModal");
        if (!modalEl || typeof bootstrap === "undefined") return;
        modal = new bootstrap.Modal(modalEl);

        loadCatalog();
        document.querySelectorAll(".lsi-buy-course").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var code = btn.getAttribute("data-course-code");
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
        el("purchaseForm").addEventListener("submit", startPayment);
        el("successResendBtn").addEventListener("click", resendSetupEmail);
        el("purchaseRetryBtn").addEventListener("click", function () { showStep("Details"); setBusy(false); });
        el("purchaseCancelRetryBtn").addEventListener("click", function () { showStep("Details"); setBusy(false); });
        el("purchaseMobile").addEventListener("input", function () { this.value = this.value.replace(/\D/g, "").slice(0, 10); });
    });
})();
