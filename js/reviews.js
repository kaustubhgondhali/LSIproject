/**
 * LORD SAI — VISITOR REVIEWS (js/reviews.js)
 * Loads approved reviews for the current website (Academy / Mutual Fund) into the
 * testimonials page and handles the "Add Your Review" form. Submissions are stored as
 * PENDING on the server and only appear after an admin approves them.
 *
 * Requires: js/api-config.js, js/mode.js (optional), Bootstrap 5.
 */
(function (window, document) {
    "use strict";

    var API = (window.LSI_CONFIG && window.LSI_CONFIG.API_BASE) || "/api";
    var MIN_LEN = 20, MAX_LEN = 1000, MAX_PHOTO_MB = 5;
    var PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];

    function el(id) { return document.getElementById(id); }
    function esc(s) {
        return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }
    function site() {
        var mode = window.LSI_Mode && typeof window.LSI_Mode.getMode === "function" ? window.LSI_Mode.getMode() : "academy";
        return (mode === "mutual-fund" || mode === "mf") ? "MUTUAL_FUND" : "SHARE_MARKET";
    }
    function mediaUrl(p) { return p && p.indexOf("images/") === 0 ? API + "/public/" + p : p; }
    function stars(n) {
        var out = "";
        for (var i = 1; i <= 5; i++) out += '<i class="' + (i <= n ? "fas" : "far") + ' fa-star"></i>';
        return '<span class="lsi-review-stars" aria-label="' + n + ' out of 5 stars">' + out + '</span>';
    }
    function initials(name) {
        return (name || "").split(/\s+/).filter(Boolean).slice(0, 2).map(function (w) { return w.charAt(0).toUpperCase(); }).join("") || "?";
    }
    function fmtDate(iso) {
        if (!iso) return "";
        try { return new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }); } catch (e) { return ""; }
    }

    // ---- Approved reviews grid --------------------------------------------------------------

    function card(r) {
        var isMf = site() === "MUTUAL_FUND";
        var borderClass = isMf ? "border-success" : "border-primary";
        var badgeColor = isMf ? "text-success" : "text-primary";
        var avatar = r.profileImagePath
            ? '<img src="' + esc(mediaUrl(r.profileImagePath)) + '" alt="" class="lsi-review-avatar">'
            : '<div class="trust-icon me-3 lsi-review-initials ' + (isMf ? 'icon-green' : '') + '">' + esc(initials(r.fullName)) + '</div>';
        return '<div class="col-lg-4 col-md-6">' +
            '<div class="card p-4 h-100 shadow-sm border-start border-4 ' + borderClass + ' lsi-review-card">' +
            '<div class="d-flex align-items-center mb-3">' + avatar +
            '<div class="flex-grow-1 min-w-0"><h6 class="fw-bold mb-0 text-dark text-truncate">' + esc(r.fullName) + '</h6>' +
            (r.course ? '<small class="' + badgeColor + ' fw-bold d-block text-truncate">' + esc(r.course) + '</small>' : "") +
            '</div></div>' +
            '<div class="d-flex justify-content-between align-items-center mb-2">' + stars(r.rating) +
            '<small class="text-muted">' + esc(fmtDate(r.approvedAt)) + '</small></div>' +
            '<p class="text-muted small mb-0" style="line-height: 1.8;">"' + esc(r.reviewText) + '"</p>' +
            '</div></div>';
    }

    function loadApproved() {
        var grid = el("visitorReviewsGrid"), wrap = el("visitorReviews");
        if (!grid || !wrap) return;
        fetch(API + "/public/reviews?site=" + site()).then(function (r) { return r.json(); }).then(function (res) {
            var list = (res && res.success && Array.isArray(res.data)) ? res.data : [];
            if (!list.length) {
                grid.innerHTML = '<div class="col-12"><div class="lsi-review-empty text-center text-muted py-4">' +
                    '<i class="far fa-comment-dots fa-2x mb-2 d-block opacity-50"></i>Be the first to share your experience.</div></div>';
            } else {
                grid.innerHTML = list.map(card).join("");
            }
            wrap.classList.remove("d-none");
        }).catch(function () { /* backend offline: section stays hidden, static testimonials remain */ });
    }

    // ---- Add Your Review form ---------------------------------------------------------------

    var rating = 0;

    function setRating(n, hover) {
        var btns = document.querySelectorAll("#reviewStars button");
        btns.forEach(function (b) {
            var v = Number(b.dataset.value);
            b.classList.toggle("active", v <= (hover || rating));
            b.setAttribute("aria-checked", v === rating ? "true" : "false");
        });
        var labels = ["", "Poor", "Fair", "Good", "Very good", "Excellent"];
        el("reviewRatingLabel").textContent = (hover || rating) ? labels[hover || rating] : "Select a rating";
    }

    function showError(msg) {
        var e = el("reviewFormError");
        e.innerHTML = msg;
        e.classList.remove("d-none");
        e.focus && e.focus();
    }
    function clearErrors() {
        el("reviewFormError").classList.add("d-none");
        document.querySelectorAll("#reviewForm .is-invalid").forEach(function (i) { i.classList.remove("is-invalid"); });
        document.querySelectorAll("#reviewForm .invalid-feedback").forEach(function (f) { f.style.display = ""; f.textContent = ""; });
    }
    function invalid(id, msg) {
        var i = el(id);
        if (!i) return;
        i.classList.add("is-invalid");
        var box = i.closest(".col-12, .col-md-6") || i.parentNode;
        var fb = box.querySelector(".invalid-feedback");
        if (fb) { fb.textContent = msg; fb.style.display = "block"; }
    }

    function validate() {
        clearErrors();
        var ok = true;
        var name = el("reviewName").value.trim(), email = el("reviewEmail").value.trim(), text = el("reviewText").value.trim();
        if (!name) { invalid("reviewName", "Please enter your full name."); ok = false; }
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) { invalid("reviewEmail", "Please enter a valid email address."); ok = false; }
        if (rating < 1 || rating > 5) { el("reviewStars").classList.add("is-invalid"); el("reviewRatingLabel").textContent = "Please select a star rating."; ok = false; }
        if (text.length < MIN_LEN || text.length > MAX_LEN) { invalid("reviewText", "Your review must be between " + MIN_LEN + " and " + MAX_LEN + " characters."); ok = false; }
        var photo = el("reviewPhoto").files[0];
        if (photo) {
            if (PHOTO_TYPES.indexOf(photo.type) < 0) { invalid("reviewPhoto", "Please choose a JPG, PNG or WebP image."); ok = false; }
            else if (photo.size > MAX_PHOTO_MB * 1024 * 1024) { invalid("reviewPhoto", "Photo must be smaller than " + MAX_PHOTO_MB + " MB."); ok = false; }
        }
        return ok;
    }

    function submit(e) {
        e.preventDefault();
        if (!validate()) return;
        var btn = el("reviewSubmitBtn");
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span> Submitting…';

        var fd = new FormData();
        fd.append("site", site());
        fd.append("fullName", el("reviewName").value.trim());
        fd.append("email", el("reviewEmail").value.trim());
        fd.append("course", el("reviewCourse").value.trim());
        fd.append("rating", String(rating));
        fd.append("reviewText", el("reviewText").value.trim());
        var photo = el("reviewPhoto").files[0];
        if (photo) fd.append("photo", photo);

        fetch(API + "/public/reviews", { method: "POST", body: fd })
            .then(function (r) { return r.json().then(function (j) { j.status = r.status; return j; }); })
            .then(function (res) {
                if (res.success) {
                    el("reviewFormWrap").classList.add("d-none");
                    el("reviewSuccess").classList.remove("d-none");
                    el("reviewSuccess").focus && el("reviewSuccess").focus();
                } else {
                    var msg = res.errors ? Object.keys(res.errors).map(function (k) { return esc(res.errors[k]); }).join("<br>") : esc(res.message || "Something went wrong. Please try again.");
                    if (res.errors) Object.keys(res.errors).forEach(function (k) {
                        invalid({ fullName: "reviewName", email: "reviewEmail", course: "reviewCourse", reviewText: "reviewText" }[k], res.errors[k]);
                    });
                    showError(msg);
                }
            })
            .catch(function () { showError("We could not reach the server. Please check your connection and try again."); })
            .then(function () { btn.disabled = false; btn.innerHTML = '<i class="fas fa-paper-plane me-2"></i> Submit Review'; });
    }

    function resetForm() {
        var f = el("reviewForm");
        if (!f) return;
        f.reset();
        rating = 0; setRating(0);
        clearErrors();
        el("reviewStars").classList.remove("is-invalid");
        el("reviewCount").textContent = "0 / " + MAX_LEN;
        el("reviewPhotoPreview").classList.add("d-none");
        el("reviewFormWrap").classList.remove("d-none");
        el("reviewSuccess").classList.add("d-none");
    }

    function adaptModalToMode() {
        var isMf = site() === "MUTUAL_FUND";
        var header = document.querySelector("#addReviewModal .modal-header");
        var submitBtn = el("reviewSubmitBtn");
        var courseLabel = el("reviewCourseLabel");
        var courseInput = el("reviewCourse");
        var courseDatalist = el("reviewCourseOptions");
        var subText = el("addReviewModalSubtitle");

        if (header) {
            header.style.background = isMf
                ? "linear-gradient(135deg, #0F4C5C, #2EC4B6)"
                : "linear-gradient(135deg, var(--lsi-navy), var(--lsi-blue))";
        }
        if (subText) {
            subText.textContent = isMf
                ? "Share your Mutual Fund investment experience — published after approval by our team."
                : "Share your Share Market Academy learning experience — published after approval by our team.";
        }
        if (submitBtn) {
            if (isMf) {
                submitBtn.className = "btn btn-success rounded-pill w-100 py-2 mt-3";
                submitBtn.style.backgroundColor = "#2EC4B6";
                submitBtn.style.borderColor = "#2EC4B6";
            } else {
                submitBtn.className = "btn btn-primary rounded-pill w-100 py-2 mt-3";
                submitBtn.style.backgroundColor = "";
                submitBtn.style.borderColor = "";
            }
        }
        if (courseLabel) {
            courseLabel.innerHTML = isMf
                ? 'Service / Investment Goal <span class="fw-normal">(optional)</span>'
                : 'Course / Program <span class="fw-normal">(optional)</span>';
        }
        if (courseInput) {
            courseInput.placeholder = isMf
                ? "e.g. Mutual Funds & SIP, Retirement Planning"
                : "e.g. Swing Trading, Technical Analysis, Basics";
        }
        if (courseDatalist) {
            var opts = isMf ? [
                "Mutual Funds & Systematic Investment Plans (SIP)",
                "Long-Term Wealth Compounding",
                "Systematic Withdrawal Plans (SWP)",
                "Retirement Corpus Planning",
                "Child Education & Future Planning",
                "Goal-Based Financial Planning",
                "Financial Planning & Insurance"
            ] : [
                "Share Market Education & Training (Master Course)",
                "Stock Market Basics",
                "Technical Analysis & Price Action",
                "Intraday Trading Strategies",
                "Swing Trading (Working Professionals)",
                "Options Trading & Derivatives",
                "Capital Protection & Risk Rules"
            ];
            courseDatalist.innerHTML = opts.map(function (o) { return '<option value="' + esc(o) + '"></option>'; }).join("");
        }
    }

    function init() {
        loadApproved();
        adaptModalToMode();
        var form = el("reviewForm");
        if (!form) return;

        el("reviewText").setAttribute("maxlength", String(MAX_LEN));
        el("reviewText").addEventListener("input", function () { el("reviewCount").textContent = this.value.length + " / " + MAX_LEN; });

        document.querySelectorAll("#reviewStars button").forEach(function (b) {
            b.addEventListener("click", function () { rating = Number(b.dataset.value); el("reviewStars").classList.remove("is-invalid"); setRating(rating); });
            b.addEventListener("mouseenter", function () { setRating(rating, Number(b.dataset.value)); });
            b.addEventListener("mouseleave", function () { setRating(rating); });
            b.addEventListener("keydown", function (ev) {
                var v = Number(b.dataset.value), next = null;
                if (ev.key === "ArrowRight" || ev.key === "ArrowUp") next = Math.min(5, v + 1);
                if (ev.key === "ArrowLeft" || ev.key === "ArrowDown") next = Math.max(1, v - 1);
                if (next) { ev.preventDefault(); rating = next; setRating(rating); document.querySelector('#reviewStars button[data-value="' + next + '"]').focus(); }
            });
        });

        el("reviewPhoto").addEventListener("change", function () {
            var f = this.files[0], img = el("reviewPhotoPreview");
            if (f && PHOTO_TYPES.indexOf(f.type) >= 0 && f.size <= MAX_PHOTO_MB * 1024 * 1024) {
                img.src = URL.createObjectURL(f); img.classList.remove("d-none");
            } else { img.classList.add("d-none"); }
        });

        form.addEventListener("submit", submit);
        var modal = el("addReviewModal");
        if (modal) {
            modal.addEventListener("hidden.bs.modal", function () { if (!el("reviewSuccess").classList.contains("d-none")) loadApproved(); resetForm(); });
            modal.addEventListener("show.bs.modal", adaptModalToMode);
            modal.addEventListener("shown.bs.modal", function () { el("reviewName").focus(); });
        }
        setRating(0);
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
    else init();
})(window, document);
