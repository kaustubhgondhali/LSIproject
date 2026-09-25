/**
 * LORD SAI — VISITOR REVIEWS (js/reviews.js)
 * Handles the "Add Your Review" form on the testimonials page. The website has no server, so
 * a submitted review opens WhatsApp with the review typed in a chat to the owner (the visitor
 * taps Send) and also emails it to the owner (js/enquiry.js); the owner decides which reviews
 * to publish.
 *
 * Requires: js/enquiry.js, js/mode.js (optional), Bootstrap 5.
 */
(function (window, document) {
    "use strict";

    var MIN_LEN = 20, MAX_LEN = 1000;

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
        return ok;
    }

    function submit(e) {
        e.preventDefault();
        if (!validate()) return;
        if (!window.LSI_Enquiry) { window.open("https://wa.me/919920254354", "_blank"); return; }

        var stars = new Array(rating + 1).join("★") + new Array(6 - rating).join("☆");
        var sent = window.LSI_Enquiry.send({
            subject: "New review from " + el("reviewName").value.trim() + " (" + rating + "/5)",
            intro: "Hello " + window.LSI_Enquiry.websiteName() + ", here is my review from your website.",
            fields: [
                ["Name", el("reviewName").value],
                ["Email", el("reviewEmail").value],
                ["Rating", rating + "/5 " + stars],
                ["Course / service", el("reviewCourse").value]
            ],
            messageLabel: "Review",
            message: el("reviewText").value,
            source: "Sent from the Add Your Review form",
            replyTo: el("reviewEmail").value
        });
        el("reviewWhatsAppLink").href = sent.url;
        el("reviewEmailNote").classList.add("d-none");
        sent.emailed.then(function (ok) { if (ok) el("reviewEmailNote").classList.remove("d-none"); });
        el("reviewFormWrap").classList.add("d-none");
        el("reviewSuccess").classList.remove("d-none");
        el("reviewSuccess").focus && el("reviewSuccess").focus();
    }

    function resetForm() {
        var f = el("reviewForm");
        if (!f) return;
        f.reset();
        rating = 0; setRating(0);
        clearErrors();
        el("reviewStars").classList.remove("is-invalid");
        el("reviewCount").textContent = "0 / " + MAX_LEN;
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

        form.addEventListener("submit", submit);
        var modal = el("addReviewModal");
        if (modal) {
            modal.addEventListener("hidden.bs.modal", resetForm);
            modal.addEventListener("show.bs.modal", adaptModalToMode);
            modal.addEventListener("shown.bs.modal", function () { el("reviewName").focus(); });
        }
        setRating(0);
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
    else init();
})(window, document);
