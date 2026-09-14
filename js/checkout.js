/**
 * LORD SAI ACADEMY — COURSE CHECKOUT (js/checkout.js)
 *
 * BUY COURSE -> details form -> POST /payments/create-order -> Razorpay Checkout
 *            -> POST /payments/verify (server validates signature) -> success screen
 *
 * The browser never decides the price and never marks a payment successful; both come from
 * the backend. If verification fails, nothing is enrolled.
 */
(function () {
    "use strict";

    var api = LSI_Auth.api;
    var modalEl, modal, courses = {}, selected = null, lastOrder = null;
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

    /** Pulls live prices so the page never shows a stale number after the admin edits a course. */
    function loadCatalog() {
        return api("/public/courses", { skipAuthRedirect: true }).then(function (res) {
            if (!res.success || !res.data) return;
            res.data.forEach(function (c) {
                courses[c.courseCode] = c;
                document.querySelectorAll('.lsi-buy-course[data-course-code="' + c.courseCode + '"] .lsi-course-price')
                    .forEach(function (span) { span.innerText = inr(c.effectivePrice); });
            });
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

    function openModal(courseCode) {
        selected = courses[courseCode];
        if (!selected) {
            alert("This course is not available for online enrollment right now. Please contact the academy.");
            return;
        }
        el("purchaseCourseName").innerText = selected.courseName;
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
            courseId: selected.id,
            fullName: el("purchaseName").value.trim(),
            email: el("purchaseEmail").value.trim(),
            mobile: el("purchaseMobile").value.trim()
        };
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
            description: order.courseName,
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
            el("purchaseFailedMessage").innerText = "Payment was not completed. " + ((resp.error && resp.error.description) || "The payment was declined by your bank or wallet.") + " No course enrollment was created.";
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
        el("purchaseSuccessMessage").innerText = d.message || "Your enrollment is active.";
        el("successCourse").innerText = d.courseName;
        el("successStudentId").innerText = d.studentId || "—";
        el("successOrderRef").innerText = d.orderRef;
        el("successEmail").innerText = d.email;
        el("successHint").innerText = d.newAccount
            ? "Open the email, set your password, then log in with your Student ID or email."
            : "Log in with your existing password — the new course is already in your dashboard.";
        var title = document.querySelector("#purchaseStepSuccess h4");
        if (title) title.innerText = d.paymentMode === "DEMO" ? "Demo Payment Successful" : "Payment Successful";
        showStep("Success");
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
                if (courses[code]) openModal(code);
                else loadCatalog().then(function () { openModal(code); });
            });
        });
        el("purchaseForm").addEventListener("submit", startPayment);
        el("purchaseRetryBtn").addEventListener("click", function () { showStep("Details"); setBusy(false); });
        el("purchaseCancelRetryBtn").addEventListener("click", function () { showStep("Details"); setBusy(false); });
        el("purchaseMobile").addEventListener("input", function () { this.value = this.value.replace(/\D/g, "").slice(0, 10); });
    });
})();
