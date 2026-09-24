/**
 * LORD SAI ACADEMY — MASTER ADMIN DASHBOARD (js/admin-dashboard.js)
 * All data comes from /api/admin/**. Every destructive action asks for confirmation.
 */
(function (window) {
    "use strict";

    var api = LSI_Auth.api;
    var formModal, formSubmitHandler = null, gappModal;
    var courseCache = [];
    var state = { paymentStatus: "", studentPage: 0, enrollPage: 0, paymentPage: 0, auditPage: 0, blogPage: 0, blogSite: "", blogCategory: "", blogStatus: "", blogSearch: "", catSite: "", invoicePage: 0 };
    var ebookCache = [];

    // ---- utils -----------------------------------------------------------------------------

    function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
    function el(id) { return document.getElementById(id); }
    function inr(v) { return v == null ? "—" : "₹" + Number(v).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
    function dt(iso) { if (!iso) return "—"; var d = new Date(iso); return isNaN(d) ? esc(iso) : d.toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }); }
    function d(iso) { if (!iso) return "—"; var x = new Date(iso); return isNaN(x) ? esc(iso) : x.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }); }
    function badge(status) {
        var map = { ACTIVE: "success", COMPLETED: "primary", INACTIVE: "secondary", CANCELLED: "danger", DISABLED: "danger", PENDING_SETUP: "warning text-dark",
            SUCCESS: "success", CREATED: "warning text-dark", PENDING: "warning text-dark", FAILED: "danger", REFUNDED: "dark", DRAFT: "secondary",
            OPEN: "warning text-dark", IN_PROGRESS: "info text-dark", RESOLVED: "success",
            PAYMENT: "success", ADMIN_MANUAL: "info text-dark", DEMO: "warning text-dark", RAZORPAY: "primary",
            STUDENTS: "info text-dark", TEACHERS: "primary", PARENTS: "success", PUBLISHED: "success", UNPUBLISHED: "secondary", APPROVED: "success", DECLINED: "danger",
            SENT: "success", REVOKED: "danger", EMAIL: "primary", WHATSAPP: "success", COURSE: "info text-dark", EBOOK: "primary",
            SCHEDULED: "primary", REJECTED: "danger", PASSED: "success", SUBMITTED: "primary", EXPIRED: "danger", UPCOMING: "warning text-dark", CLOSED: "secondary" };
        return '<span class="badge bg-' + (map[status] || "secondary") + '">' + esc(String(status || "").replace(/_/g, " ")) + '</span>';
    }
    // The two public websites share one SiteCode value ("ACADEMY") for what visitors see as
    // "Share Market Academy" — this keeps the Admin UI honest about the visible brand name
    // without renaming the underlying site/content architecture.
    function siteName(site) { return site === "MUTUAL_FUND" ? "Mutual Fund" : "Share Market"; }
    function siteBadge(site) { return '<span class="badge bg-' + (site === "MUTUAL_FUND" ? "success" : "info text-dark") + '">' + siteName(site) + '</span>'; }
    function toast(msg, ok) {
        var t = document.createElement("div");
        t.className = "toast show align-items-center text-white border-0 mb-2 " + (ok === false ? "bg-danger" : "bg-success");
        t.innerHTML = '<div class="d-flex"><div class="toast-body">' + esc(msg) + '</div><button type="button" class="btn-close btn-close-white me-2 m-auto" onclick="this.closest(\'.toast\').remove()"></button></div>';
        el("toast-area").appendChild(t);
        setTimeout(function () { t.remove(); }, 4500);
    }
    function handle(res, okMsg) {
        if (res.success) { if (okMsg !== false) toast(res.message || okMsg || "Saved."); return true; }
        toast(res.errors ? Object.values(res.errors).join(" ") : (res.message || "Something went wrong."), false);
        return false;
    }
    function pager(containerId, page, onPage) {
        var c = el(containerId);
        if (!page || page.totalPages <= 1) { c.innerHTML = ""; return; }
        var html = '<div class="btn-group btn-group-sm">';
        html += '<button class="btn btn-outline-secondary" ' + (page.first ? "disabled" : "") + ' data-p="' + (page.number - 1) + '">&laquo;</button>';
        html += '<button class="btn btn-outline-secondary disabled">' + (page.number + 1) + ' / ' + page.totalPages + '</button>';
        html += '<button class="btn btn-outline-secondary" ' + (page.last ? "disabled" : "") + ' data-p="' + (page.number + 1) + '">&raquo;</button></div>';
        c.innerHTML = html;
        c.querySelectorAll("[data-p]").forEach(function (b) { b.addEventListener("click", function () { onPage(Number(b.dataset.p)); }); });
    }
    function confirmAction(msg) { return window.confirm(msg); }

    /** Opens the shared modal with the given fields; onSubmit receives a values object. */
    function openForm(title, fieldsHtml, onSubmit, opts) {
        opts = opts || {};
        el("formModalTitle").innerText = title;
        el("formModalBody").innerHTML = fieldsHtml;
        el("formModalError").classList.add("d-none");
        el("formModalSubmit").innerText = opts.submitLabel || "Save";
        el("formModalDialog").className = "modal-dialog modal-dialog-centered " + (opts.size || "");
        formSubmitHandler = onSubmit;
        formModal.show();
    }
    function formError(msg) { var e = el("formModalError"); e.innerText = msg; e.classList.remove("d-none"); }
    function fieldVals() {
        var out = {};
        el("formModalForm").querySelectorAll("[name]").forEach(function (i) {
            if (i.type === "checkbox") out[i.name] = i.checked;
            else if (i.type === "file") out[i.name] = i.files[0] || null;
            else out[i.name] = i.value;
        });
        return out;
    }
    function input(name, label, value, opts) {
        opts = opts || {};
        var attrs = (opts.required ? " required" : "") + (opts.type === "number" ? ' step="0.01" min="0"' : "") + (opts.maxlength ? ' maxlength="' + opts.maxlength + '"' : "") + (opts.placeholder ? ' placeholder="' + esc(opts.placeholder) + '"' : "");
        if (opts.type === "textarea") return '<div class="mb-3"><label class="form-label small fw-bold">' + esc(label) + '</label><textarea class="form-control" name="' + name + '" rows="' + (opts.rows || 3) + '"' + attrs + '>' + esc(value || "") + '</textarea>' + (opts.help ? '<div class="form-text">' + esc(opts.help) + '</div>' : "") + '</div>';
        if (opts.type === "select") return '<div class="mb-3"><label class="form-label small fw-bold">' + esc(label) + '</label><select class="form-select" name="' + name + '"' + attrs + '>' + opts.options.map(function (o) { return '<option value="' + esc(o.value) + '"' + (String(o.value) === String(value) ? " selected" : "") + '>' + esc(o.label) + '</option>'; }).join("") + '</select></div>';
        return '<div class="mb-3"><label class="form-label small fw-bold">' + esc(label) + '</label><input type="' + (opts.type || "text") + '" class="form-control" name="' + name + '" value="' + esc(value == null ? "" : value) + '"' + attrs + '>' + (opts.help ? '<div class="form-text">' + esc(opts.help) + '</div>' : "") + '</div>';
    }

    // ---- navigation ------------------------------------------------------------------------

    function show(view) {
        document.querySelectorAll(".view").forEach(function (v) { v.classList.remove("active"); });
        var target = el("view-" + view);
        if (target) target.classList.add("active");
        document.querySelectorAll(".adm-nav a[data-view]").forEach(function (a) { a.classList.toggle("active", a.dataset.view === view); });
        el("sidebar").classList.remove("open");
        var loaders = { dashboard: loadDashboard, students: function () { loadStudents(0); }, courses: function () { initShareMarketPurchaseToggle(); return loadCourses(); }, curriculum: initCurriculum,
            enrollments: function () { loadEnrollments(0); }, payments: function () { loadPayments(0); }, stories: loadStories, reviews: function () { loadReviews(0); },
            mentoring: loadMentoring, "mf-slider": loadSlider, blogs: function () { loadBlogs(0); }, "blog-categories": loadBlogCategories,
            gateway: loadGateway, email: loadEmailSettings, audit: function () { loadAudit(0); }, settings: function () { el("settingsApiBase").innerText = LSI_Auth.apiBase; initShareMarketPurchaseToggle(); }, whatsapp: loadWhatsAppSettings,
            ebooks: loadEbooks, invoices: function () { loadInvoices(0); },
            "exam-applications": function () { loadExamApplications(0); }, exams: loadExams, "exam-results": function () { loadExamResults(0); }, certificates: function () { loadCertificates(0); } };
        if (loaders[view]) loaders[view]();
    }

    // ---- dashboard -------------------------------------------------------------------------

    function loadDashboard() {
        api("/admin/dashboard").then(function (res) {
            if (!handle(res, false)) return;
            var s = res.data;
            var cards = [
                ["Total Students", s.totalStudents, "fa-user-graduate", "rgba(15,159,144,.12)", "#0F9F90"],
                ["Active Students", s.activeStudents, "fa-user-check", "rgba(16,185,129,.12)", "#10b981"],
                ["Pending Setup", s.pendingSetupStudents, "fa-user-clock", "rgba(245,158,11,.12)", "#f59e0b"],
                ["Active Courses", s.activeCourses + " / " + s.totalCourses, "fa-book", "rgba(59,130,246,.12)", "#3b82f6"],
                ["Active Enrollments", s.activeEnrollments + " / " + s.totalEnrollments, "fa-id-card", "rgba(99,102,241,.12)", "#6366f1"],
                ["Total Revenue", inr(s.totalRevenue), "fa-rupee-sign", "rgba(16,185,129,.12)", "#10b981"],
                ["Successful Payments", s.successfulPayments, "fa-check-circle", "rgba(16,185,129,.12)", "#10b981"],
                ["Demo / Razorpay Payments", s.demoPayments + " / " + s.razorpayPayments, "fa-credit-card", "rgba(99,102,241,.12)", "#6366f1"],
                ["Pending Reviews", s.pendingReviews, "fa-star-half-alt", "rgba(245,158,11,.12)", "#f59e0b"],
                ["Approved Reviews", s.approvedReviews, "fa-star", "rgba(16,185,129,.12)", "#10b981"],
                ["Declined Reviews", s.declinedReviews, "fa-ban", "rgba(239,68,68,.12)", "#ef4444"],
                ["Pending / Failed", s.pendingPayments + " / " + s.failedPayments, "fa-exclamation-circle", "rgba(239,68,68,.12)", "#ef4444"],
                ["Open Doubts", s.openDoubts, "fa-question-circle", "rgba(245,158,11,.12)", "#f59e0b"],
                ["Disabled Students", s.disabledStudents, "fa-user-slash", "rgba(239,68,68,.12)", "#ef4444"],
                ["Ebooks (active / total)", (s.activeEbooks || 0) + " / " + (s.totalEbooks || 0), "fa-tablet-alt", "rgba(11,95,165,.12)", "#0B5FA5"],
                ["Ebook Access Granted", s.ebookEntitlements || 0, "fa-book-reader", "rgba(11,95,165,.12)", "#0B5FA5"],
                ["Invoices Issued", s.totalInvoices || 0, "fa-file-invoice", "rgba(16,185,129,.12)", "#10b981"]
            ];
            renderPaymentStatus(s);
            updatePendingReviews(s.pendingReviews);
            loadExamCounters();
            el("statCards").innerHTML = cards.map(function (c) {
                return '<div class="col-xl-3 col-md-4 col-sm-6"><div class="adm-card stat-card"><div class="ico" style="background:' + c[3] + ';color:' + c[4] + '"><i class="fas ' + c[2] + '"></i></div><div><div class="val">' + esc(c[1]) + '</div><div class="lbl">' + c[0] + '</div></div></div></div>';
            }).join("");
            el("recentEnrollments").innerHTML = (s.recentEnrollments || []).map(function (e) {
                return '<tr><td><a href="#" data-student="' + e.studentUserId + '">' + esc(e.studentName) + '</a><br><small class="text-muted">' + esc(e.studentId || "") + '</small></td><td>' + esc(e.courseName) + '</td><td>' + badge(e.source) + '</td><td><small>' + d(e.enrolledAt) + '</small></td></tr>';
            }).join("") || '<tr><td colspan="4" class="text-muted">No enrollments yet.</td></tr>';
            el("recentPayments").innerHTML = (s.recentPayments || []).map(function (p) {
                return '<tr><td><small class="font-monospace">' + esc(p.orderRef) + '</small></td><td>' + esc(p.customerName) + '<br><small class="text-muted">' + esc(p.customerEmail) + '</small></td><td>' + inr(p.amount) + '</td><td>' + badge(p.status) + '</td></tr>';
            }).join("") || '<tr><td colspan="4" class="text-muted">No payments yet.</td></tr>';
            bindStudentLinks(el("recentEnrollments"));
        });
    }

    function renderPaymentStatus(s) {
        var real = s.paymentMode === "RAZORPAY";
        el("paymentStatusCard").innerHTML =
            '<div class="d-flex flex-wrap align-items-center gap-3">' +
            '<div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width:52px;height:52px;font-size:22px;background:' + (real ? "rgba(16,185,129,.15);color:#10b981" : "rgba(245,158,11,.15);color:#f59e0b") + '"><i class="fas ' + (real ? "fa-shield-alt" : "fa-flask") + '"></i></div>' +
            '<div class="flex-grow-1"><div class="fw-bold" style="font-size:17px">' + (real ? "RAZORPAY PAYMENT" : "DEMO PAYMENT") + ' <span class="badge ' + (real ? "bg-success" : "bg-warning text-dark") + ' ms-1">● ACTIVE</span></div>' +
            '<div class="text-muted small">' + (real ? "Students are charged through Razorpay Checkout; every payment is verified server-side before enrollment." : "Razorpay is not configured — students enroll through demo payments (no money is charged). Configure Razorpay to switch to real payments.") + '</div></div>' +
            '<div class="d-flex flex-wrap gap-3 small text-nowrap">' +
            '<div><div class="text-muted">Successful</div><div class="fw-bold fs-5">' + esc(s.successfulPayments) + '</div></div>' +
            '<div><div class="text-muted">Demo</div><div class="fw-bold fs-5">' + esc(s.demoPayments) + '</div></div>' +
            '<div><div class="text-muted">Razorpay</div><div class="fw-bold fs-5">' + esc(s.razorpayPayments) + '</div></div>' +
            '<div><div class="text-muted">Enrollments</div><div class="fw-bold fs-5">' + esc(s.activeEnrollments) + '</div></div>' +
            '<div><div class="text-muted">Failed</div><div class="fw-bold fs-5 text-danger">' + esc(s.failedPayments) + '</div></div>' +
            '</div>' +
            '<a href="#gateway" data-view="gateway" class="btn btn-sm ' + (real ? "btn-outline-success" : "btn-primary") + '"><i class="fas fa-credit-card me-1"></i> ' + (real ? "Payment Gateway" : "Configure Razorpay") + '</a>' +
            '</div>';
        el("paymentStatusCard").querySelector("[data-view]").addEventListener("click", function (e) { e.preventDefault(); show("gateway"); });
    }

    /** Admin uploads (images/<file>) are served by the API; static site paths (img/...) are used as-is. */
    function mediaUrl(p) {
        if (!p) return "";
        if (p.indexOf("images/") === 0) return LSI_Auth.apiBase + "/public/" + p;
        return p;
    }

    function updatePendingReviews(n) {
        n = Number(n) || 0;
        var b = el("navPendingReviews");
        if (b) { b.innerText = n; b.classList.toggle("d-none", n === 0); }
        var a = el("pendingReviewsAlert");
        if (a) {
            a.classList.toggle("d-none", n === 0);
            a.classList.toggle("d-flex", n > 0);
            if (n > 0) a.innerHTML = '<i class="fas fa-star"></i><div class="flex-grow-1"><strong>' + n + ' review' + (n === 1 ? "" : "s") + '</strong> waiting for approval.</div><a href="#reviews" class="btn btn-sm btn-warning">Review now</a>';
            var link = a.querySelector("a[href='#reviews']");
            if (link) link.addEventListener("click", function (e) { e.preventDefault(); show("reviews"); });
        }
    }

    function bindStudentLinks(root) {
        root.querySelectorAll("[data-student]").forEach(function (a) {
            a.addEventListener("click", function (e) { e.preventDefault(); openStudent(a.dataset.student); });
        });
    }

    // ---- students --------------------------------------------------------------------------

    function loadStudents(page) {
        state.studentPage = page;
        var q = "/admin/students?page=" + page + "&size=20";
        var search = el("studentSearch").value.trim();
        var status = el("studentStatusFilter").value;
        if (search) q += "&search=" + encodeURIComponent(search);
        if (status) q += "&status=" + status;
        api(q).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("studentsBody").innerHTML = (p.content || []).map(function (s) {
                return '<tr><td class="font-monospace small">' + esc(s.studentId || "—") + '</td><td><a href="#" data-student="' + s.id + '" class="fw-bold">' + esc(s.fullName) + '</a>' + (s.hasActiveSession ? ' <span class="badge badge-soft ms-1" title="Logged in now">online</span>' : '') + '</td><td>' + esc(s.email) + '</td><td>' + esc(s.mobile || "—") + '</td><td>' + s.enrolledCourses + '</td><td>' + badge(s.accountStatus) + '</td><td><small>' + dt(s.lastLoginAt) + '</small></td>' +
                    '<td class="text-nowrap"><button class="btn btn-xs btn-outline-primary" data-student="' + s.id + '">Open</button></td></tr>';
            }).join("") || '<tr><td colspan="8" class="text-muted text-center py-4">No students found.</td></tr>';
            el("studentsCount").innerText = p.totalElements + " student(s)";
            pager("studentsPager", p, loadStudents);
            bindStudentLinks(el("studentsBody"));
        });
    }

    function openStudentForm(existing) {
        var f = input("fullName", "Full name", existing && existing.fullName, { required: true, maxlength: 150 }) +
            input("email", "Email", existing && existing.email, { required: true, type: "email" }) +
            input("mobile", "Mobile (10 digits)", existing && existing.mobile, { required: true, maxlength: 10 }) +
            input("batch", "Batch (optional)", existing && existing.batch, { maxlength: 100, placeholder: "e.g. Uran Weekend Batch 2026" }) +
            input("location", "Location (optional)", existing && existing.location, { maxlength: 150 }) +
            (existing ? "" :
                '<div class="form-check form-switch mb-2"><input class="form-check-input" type="checkbox" role="switch" name="sendCredentials" id="stSendCreds"><label class="form-check-label small fw-bold" for="stSendCreds">Send login credentials by email</label></div>' +
                '<div class="alert alert-info small py-2 mb-0" id="stCredsHint">A password setup link will be emailed to the student. No password is shown to you.</div>');
        openForm(existing ? "Edit Student" : "Add Student", f, function (v) {
            return api(existing ? "/admin/students/" + existing.id : "/admin/students", { method: existing ? "PUT" : "POST", body: v }).then(function (res) {
                if (handle(res)) { formModal.hide(); existing ? openStudent(existing.id) : loadStudents(0); }
                else formError(res.errors ? Object.values(res.errors).join(" ") : res.message);
            });
        });
        if (!existing && el("stSendCreds")) {
            el("stSendCreds").addEventListener("change", function () {
                el("stCredsHint").innerText = this.checked
                    ? "A temporary password will be generated, the account activated, and the Student ID + password emailed with the login link. The password is never shown to you."
                    : "A password setup link will be emailed to the student. No password is shown to you.";
            });
        }
    }

    function openStudent(id) {
        show("student-detail");
        el("studentDetail").innerHTML = '<div class="text-muted">Loading...</div>';
        Promise.all([api("/admin/students/" + id), api("/admin/students/" + id + "/enrollments"), api("/admin/students/" + id + "/payments"),
                     api("/admin/students/" + id + "/sessions"), api("/admin/students/" + id + "/ebooks"),
                     api("/admin/students/" + id + "/device")]).then(function (r) {
            if (!r[0].success) { el("studentDetail").innerHTML = '<div class="alert alert-danger">' + esc(r[0].message) + '</div>'; return; }
            var s = r[0].data, enr = r[1].data || [], pay = r[2].data || [], ses = r[3].data || [], ebk = r[4].data || [], dev = (r[5] && r[5].success) ? r[5].data : null;
            var disabled = s.accountStatus === "DISABLED";
            el("studentDetail").innerHTML =
                '<div class="adm-card p-4 mb-3"><div class="d-flex flex-wrap justify-content-between align-items-start gap-3">' +
                '<div><h4 class="fw-bold mb-1">' + esc(s.fullName) + ' ' + badge(s.accountStatus) + '</h4>' +
                '<div class="text-muted small">Student ID <strong class="font-monospace text-dark">' + esc(s.studentId || "—") + '</strong> · ' + esc(s.email) + ' · ' + esc(s.mobile || "") + '</div>' +
                '<div class="text-muted small">Batch: ' + esc(s.batch || "—") + ' · Location: ' + esc(s.location || "—") + ' · Registered ' + d(s.registrationDate) + ' · Last login ' + dt(s.lastLoginAt) + '</div></div>' +
                '<div class="d-flex flex-wrap gap-2">' +
                '<button class="btn btn-sm btn-outline-primary" id="sdEdit"><i class="fas fa-edit me-1"></i> Edit</button>' +
                '<button class="btn btn-sm btn-outline-secondary" id="sdReset"><i class="fas fa-key me-1"></i> Send Password Link</button>' +
                '<button class="btn btn-sm btn-outline-primary" id="sdResendSetup"><i class="fas fa-paper-plane me-1"></i> Resend Setup Email</button>' +
                '<button class="btn btn-sm btn-outline-secondary" id="sdSendCreds"><i class="fas fa-envelope-open-text me-1"></i> Send Login Credentials</button>' +
                '<button class="btn btn-sm btn-outline-warning" id="sdLogout"><i class="fas fa-sign-out-alt me-1"></i> Logout All Devices</button>' +
                '<button class="btn btn-sm btn-outline-danger" id="sdResetDevice"><i class="fas fa-laptop me-1"></i> Reset Device Binding</button>' +
                '<button class="btn btn-sm ' + (disabled ? "btn-success" : "btn-outline-danger") + '" id="sdToggle">' + (disabled ? '<i class="fas fa-check me-1"></i> Activate' : '<i class="fas fa-ban me-1"></i> Deactivate') + '</button>' +
                '<button class="btn btn-sm btn-outline-danger" id="sdDelete"><i class="fas fa-trash me-1"></i> Delete</button>' +
                '</div></div></div>' +
                '<div class="row g-3">' +
                '<div class="col-lg-6"><div class="adm-card p-3"><div class="d-flex justify-content-between align-items-center mb-2"><h6 class="fw-bold mb-0"><i class="fas fa-laptop me-1 text-primary"></i> Registered Computer (1 Account = 1 Device)</h6>' +
                (dev ? '<button class="btn btn-xs btn-outline-danger" id="sdCardResetDevice"><i class="fas fa-trash me-1"></i> Reset Binding</button>' : '') +
                '</div>' +
                (dev ? (
                    '<div class="small">' +
                    '<div class="mb-1"><strong>Device Name:</strong> ' + esc(dev.deviceName || "Primary Computer") + ' <span class="badge ' + (dev.deviceStatus === 'ACTIVE' ? 'bg-success' : 'bg-secondary') + ' ms-1">' + esc(dev.deviceStatus || "BOUND") + '</span></div>' +
                    '<div class="mb-1"><strong>Platform:</strong> ' + esc(dev.devicePlatform || "—") + '</div>' +
                    '<div class="mb-1"><strong>Device ID:</strong> <span class="font-monospace text-muted">' + esc(dev.deviceId) + '</span></div>' +
                    '<div class="mb-1"><strong>Registered:</strong> ' + dt(dev.registeredAt) + '</div>' +
                    '<div class="mb-1"><strong>Last Activity:</strong> ' + dt(dev.lastSeenAt) + '</div>' +
                    (dev.resetRequired ? '<div class="alert alert-warning py-1 px-2 mt-2 mb-0 small"><i class="fas fa-exclamation-triangle me-1"></i> Device reset pending. Student can register their new computer on next login.</div>' : '') +
                    '</div>'
                ) : '<div class="text-muted small py-2"><i class="fas fa-info-circle me-1"></i> No computer bound yet. The student will bind their computer automatically on first login.</div>') +
                '</div></div>' +
                '<div class="col-lg-6"><div class="adm-card p-3"><div class="d-flex justify-content-between align-items-center mb-2"><h6 class="fw-bold mb-0">Courses & Progress</h6><button class="btn btn-xs btn-primary" id="sdEnroll">+ Enroll in course</button></div>' +
                '<table class="table table-sm mb-0"><thead><tr><th>Course</th><th>Status</th><th>Progress</th><th>Source</th><th></th></tr></thead><tbody>' +
                (enr.map(function (e) { return '<tr><td>' + esc(e.courseName) + '</td><td>' + badge(e.status) + '</td><td>' + e.progressPercent + '% <small class="text-muted">(' + e.completedLessons + '/' + e.totalLessons + ')</small></td><td>' + badge(e.source) + '</td><td>' + enrollmentActions(e) + '</td></tr>'; }).join("") || '<tr><td colspan="5" class="text-muted">Not enrolled in any course.</td></tr>') +
                '</tbody></table></div></div>' +
                '<div class="col-lg-6"><div class="adm-card p-3"><div class="d-flex justify-content-between align-items-center mb-2"><h6 class="fw-bold mb-0">Ebooks</h6><button class="btn btn-xs btn-primary" id="sdGrantEbook">+ Grant ebook</button></div>' +
                '<table class="table table-sm mb-0"><thead><tr><th>Ebook</th><th>Status</th><th>Source</th><th>Granted</th><th></th></tr></thead><tbody>' +
                (ebk.map(function (e) { return '<tr><td>' + esc(e.title) + '</td><td>' + badge(e.status) + '</td><td>' + badge(e.source) + '</td><td><small>' + d(e.grantedAt) + '</small></td><td><button class="btn btn-xs btn-outline-' + (e.status === "ACTIVE" ? "danger" : "success") + '" data-ent="' + e.id + '" data-next="' + (e.status === "ACTIVE" ? "REVOKED" : "ACTIVE") + '">' + (e.status === "ACTIVE" ? "Revoke" : "Restore") + '</button></td></tr>'; }).join("") || '<tr><td colspan="5" class="text-muted">No ebooks.</td></tr>') +
                '</tbody></table></div></div>' +
                '<div class="col-lg-6"><div class="adm-card p-3"><h6 class="fw-bold mb-2">Payment History</h6>' +
                '<table class="table table-sm mb-0"><thead><tr><th>Order</th><th>Product</th><th>Amount</th><th>Invoice</th><th>Status</th><th>Date</th></tr></thead><tbody>' +
                (pay.map(function (p) { return '<tr><td class="font-monospace small">' + esc(p.orderRef) + '</td><td>' + esc(p.productName || p.courseName) + ' ' + badge(p.productType || "COURSE") + '</td><td>' + inr(p.amount) + '</td><td class="font-monospace small">' + (p.invoiceId ? '<a href="#" data-invoice-pdf="' + p.invoiceId + '">' + esc(p.invoiceNumber) + '</a>' : '—') + '</td><td>' + badge(p.status) + '</td><td><small>' + dt(p.createdAt) + '</small></td></tr>'; }).join("") || '<tr><td colspan="6" class="text-muted">No payments.</td></tr>') +
                '</tbody></table></div></div>' +
                '<div class="col-lg-6"><div class="adm-card p-3"><h6 class="fw-bold mb-2">Login Sessions</h6>' +
                '<table class="table table-sm mb-0"><thead><tr><th>Device</th><th>IP</th><th>Started</th><th>Last activity</th><th>State</th></tr></thead><tbody>' +
                (ses.slice(0, 10).map(function (x) { return '<tr><td><small class="text-truncate d-inline-block" style="max-width:220px" title="' + esc(x.deviceInfo) + '">' + esc(x.deviceInfo) + '</small></td><td><small>' + esc(x.ipAddress) + '</small></td><td><small>' + dt(x.createdAt) + '</small></td><td><small>' + dt(x.lastActivityAt) + '</small></td><td>' + (x.active ? '<span class="badge bg-success">active</span>' : '<span class="badge bg-secondary">' + esc((x.revokeReason || "ended").toLowerCase()) + '</span>') + '</td></tr>'; }).join("") || '<tr><td colspan="5" class="text-muted">Never logged in.</td></tr>') +
                '</tbody></table></div></div></div>';

            el("sdEdit").onclick = function () { openStudentForm(s); };
            el("sdReset").onclick = function () { if (confirmAction("Email a password link to " + s.email + "? Their current sessions will be ended.")) api("/admin/students/" + id + "/reset-password", { method: "POST" }).then(handle); };
            el("sdResendSetup").onclick = function () { if (confirmAction("Re-send the enrollment email (Student ID + password setup link) to " + s.email + "?")) api("/admin/students/" + id + "/resend-setup", { method: "POST" }).then(handle); };
            el("sdSendCreds").onclick = function () { if (confirmAction("Generate a secure password setup link for " + s.fullName + " and email it to " + s.email + "? Their current password stops working and all sessions end.")) api("/admin/students/" + id + "/send-credentials", { method: "POST" }).then(function (r) { if (handle(r)) openStudent(id); }); };
            el("sdLogout").onclick = function () { if (confirmAction("Log this student out of all devices?")) api("/admin/students/" + id + "/force-logout", { method: "POST" }).then(function (r) { if (handle(r)) openStudent(id); }); };

            function doResetDevice() {
                if (!confirmAction("Reset the registered computer for " + s.fullName + "?\n\nThis will remove the current computer binding and terminate all active sessions so the student can bind their new computer on next login.")) return;
                api("/admin/students/" + id + "/reset-device", { method: "POST" }).then(function (r) { if (handle(r)) openStudent(id); });
            }
            if (el("sdResetDevice")) el("sdResetDevice").onclick = doResetDevice;
            if (el("sdCardResetDevice")) el("sdCardResetDevice").onclick = doResetDevice;
            el("sdToggle").onclick = function () {
                var next = disabled ? "ACTIVE" : "DISABLED";
                if (confirmAction((disabled ? "Activate" : "Deactivate") + " this account?" + (disabled ? "" : " The student will be logged out and cannot sign in."))) api("/admin/students/" + id + "/status?status=" + next, { method: "PATCH" }).then(function (r) { if (handle(r)) openStudent(id); });
            };
            el("sdDelete").onclick = function () { if (confirmAction("Permanently delete " + s.fullName + "? This only works for accounts with no payments or enrollments.")) api("/admin/students/" + id, { method: "DELETE" }).then(function (r) { if (handle(r)) show("students"); }); };
            el("sdEnroll").onclick = function () { openManualEnroll(s); };
            el("sdGrantEbook").onclick = function () { openGrantEbook(s); };
            el("studentDetail").querySelectorAll("[data-ent]").forEach(function (b) {
                b.addEventListener("click", function () {
                    if (!confirmAction((b.dataset.next === "REVOKED" ? "Revoke" : "Restore") + " this ebook access?")) return;
                    api("/admin/ebook-entitlements/" + b.dataset.ent + "/status?status=" + b.dataset.next, { method: "PATCH" }).then(function (r) { if (handle(r)) openStudent(id); });
                });
            });
            bindInvoiceLinks(el("studentDetail"));
            bindEnrollmentActions(el("studentDetail"), function () { openStudent(id); });
        });
    }

    // ---- Share Market Course Purchase Toggle (Frontend Setting) ------------
    var SM_PURCHASE_STORAGE_KEY = "shareMarketCoursePurchaseEnabled";
    var SM_CHANNEL_NAME = "lsi_purchase_channel";

    function isShareMarketPurchaseEnabled() {
        try {
            var val = localStorage.getItem(SM_PURCHASE_STORAGE_KEY);
            if (val === "false") return false;
            if (val === "true") return true;
        } catch (e) {}
        try {
            var m = document.cookie.match(new RegExp('(?:^|;\\s*)' + SM_PURCHASE_STORAGE_KEY + '=(true|false)(?:;|$)'));
            if (m) return m[1] !== "false";
        } catch (e) {}
        try {
            var sVal = sessionStorage.getItem(SM_PURCHASE_STORAGE_KEY);
            if (sVal === "false") return false;
            if (sVal === "true") return true;
        } catch (e) {}
        return true;
    }

    function setShareMarketPurchaseEnabled(enabled) {
        var isEnabled = Boolean(enabled);
        var str = isEnabled ? "true" : "false";
        try { localStorage.setItem(SM_PURCHASE_STORAGE_KEY, str); } catch (e) {}
        try { sessionStorage.setItem(SM_PURCHASE_STORAGE_KEY, str); } catch (e) {}
        try {
            document.cookie = SM_PURCHASE_STORAGE_KEY + "=" + str + "; path=/; max-age=31536000; SameSite=Lax";
            if (location.hostname === "localhost") {
                document.cookie = SM_PURCHASE_STORAGE_KEY + "=" + str + "; path=/; domain=localhost; max-age=31536000; SameSite=Lax";
            }
        } catch (e) {}

        try {
            if (typeof BroadcastChannel !== "undefined") {
                var bc = new BroadcastChannel(SM_CHANNEL_NAME);
                bc.postMessage({ key: SM_PURCHASE_STORAGE_KEY, enabled: isEnabled });
            }
        } catch (e) {}

        updateShareMarketPurchaseUI(isEnabled);
        toast(isEnabled ? "Share Market course purchase is now ENABLED." : "Share Market course purchase is now DISABLED (Admissions Closed).");
    }

    function updateShareMarketPurchaseUI(enabled) {
        var isEnabled = enabled !== false;
        var param = isEnabled ? "on" : "off";

        // Courses view controls
        var sw = el("shareMarketPurchaseSwitch");
        var badgeEl = el("shareMarketPurchaseBadge");
        var btnText = el("btnToggleShareMarketPurchaseText");
        var linkCourses = el("adminPreviewCourseLink");

        if (sw) sw.checked = isEnabled;
        if (badgeEl) {
            badgeEl.innerText = isEnabled ? "ON" : "OFF";
            badgeEl.className = "badge " + (isEnabled ? "bg-success" : "bg-secondary");
        }
        if (btnText) {
            btnText.innerText = isEnabled ? "Turn OFF" : "Turn ON";
        }
        if (linkCourses) {
            linkCourses.href = "courses.html?purchase=" + param;
        }

        // Settings view controls
        var sSw = el("settingsShareMarketPurchaseSwitch");
        var sBadge = el("settingsShareMarketPurchaseBadge");
        var sStatus = el("settingsShareMarketStatusText");
        var linkSettings = el("settingsPreviewCourseLink");

        if (sSw) sSw.checked = isEnabled;
        if (sBadge) {
            sBadge.innerText = isEnabled ? "ON" : "OFF";
            sBadge.className = "badge " + (isEnabled ? "bg-success" : "bg-secondary");
        }
        if (sStatus) {
            sStatus.innerText = isEnabled ? "Enabled" : "Disabled";
            sStatus.className = isEnabled ? "text-success fw-bold" : "text-danger fw-bold";
        }
        if (linkSettings) {
            linkSettings.href = "courses.html?purchase=" + param;
        }
    }

    function initShareMarketPurchaseToggle() {
        var enabled = isShareMarketPurchaseEnabled();
        updateShareMarketPurchaseUI(enabled);

        var sw = el("shareMarketPurchaseSwitch");
        if (sw && !sw._lsiBound) {
            sw._lsiBound = true;
            sw.addEventListener("change", function () {
                setShareMarketPurchaseEnabled(sw.checked);
            });
        }

        var btn = el("btnToggleShareMarketPurchase");
        if (btn && !btn._lsiBound) {
            btn._lsiBound = true;
            btn.addEventListener("click", function () {
                setShareMarketPurchaseEnabled(!isShareMarketPurchaseEnabled());
            });
        }

        var sSw = el("settingsShareMarketPurchaseSwitch");
        if (sSw && !sSw._lsiBound) {
            sSw._lsiBound = true;
            sSw.addEventListener("change", function () {
                setShareMarketPurchaseEnabled(sSw.checked);
            });
        }
    }

    // Expose helpers globally immediately
    window.LSI_setPurchaseSetting = setShareMarketPurchaseEnabled;
    window.LSI_togglePurchaseSetting = function () { setShareMarketPurchaseEnabled(!isShareMarketPurchaseEnabled()); };
    window.LSI_isPurchaseEnabled = isShareMarketPurchaseEnabled;
    window.LSI_initPurchaseToggle = initShareMarketPurchaseToggle;

    // Run right away if DOM is already ready or in progress
    initShareMarketPurchaseToggle();
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initShareMarketPurchaseToggle);
    }
    window.addEventListener("load", initShareMarketPurchaseToggle);
    try {
        if (typeof BroadcastChannel !== "undefined") {
            var admBc = new BroadcastChannel(SM_CHANNEL_NAME);
            admBc.onmessage = function (ev) {
                if (ev.data && ev.data.key === SM_PURCHASE_STORAGE_KEY) {
                    updateShareMarketPurchaseUI(ev.data.enabled);
                }
            };
        }
    } catch (e) {}

    // ---- courses ---------------------------------------------------------------------------

    function loadCourses() {
        return api("/admin/courses").then(function (res) {
            if (!handle(res, false)) return [];
            courseCache = res.data || [];
            el("coursesBody").innerHTML = courseCache.map(function (c) {
                return '<tr><td><div class="d-flex align-items-center gap-2">' + (c.thumbnailPath ? '<img src="' + esc(mediaUrl(c.thumbnailPath)) + '" alt="" style="width:56px;height:40px;object-fit:cover;border-radius:6px;flex-shrink:0">' : '<span class="d-inline-flex align-items-center justify-content-center bg-light text-muted" style="width:56px;height:40px;border-radius:6px;flex-shrink:0"><i class="fas fa-image"></i></span>') + '<div><strong>' + esc(c.courseName) + '</strong><br><small class="text-muted">' + esc(c.shortDescription || "") + '</small></div></div></td><td class="font-monospace small">' + esc(c.courseCode) + '</td>' +
                    '<td>' + inr(c.effectivePrice) + (c.discountedPrice ? '<br><small class="text-muted text-decoration-line-through">' + inr(c.price) + '</small>' : '') + '</td>' +
                    '<td>' + c.moduleCount + ' / ' + c.lessonCount + '</td><td>' + c.activeStudents + '</td><td>' + badge(c.status) + '</td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-act="edit" data-id="' + c.id + '" title="Edit"><i class="fas fa-edit"></i></button>' +
                    '<button class="btn btn-outline-primary" data-act="rename" data-id="' + c.id + '" title="Rename"><i class="fas fa-i-cursor"></i></button>' +
                    '<button class="btn btn-outline-primary" data-act="price" data-id="' + c.id + '" title="Change price"><i class="fas fa-tag"></i></button>' +
                    '<button class="btn btn-outline-primary" data-act="thumbnail" data-id="' + c.id + '" title="Upload thumbnail image"><i class="fas fa-image"></i></button>' +
                    '<button class="btn btn-outline-primary" data-act="modules" data-id="' + c.id + '" title="Manage modules & lessons"><i class="fas fa-layer-group"></i></button>' +
                    '<button class="btn btn-outline-' + (c.status === "ACTIVE" ? "warning" : "success") + '" data-act="status" data-id="' + c.id + '" title="' + (c.status === "ACTIVE" ? "Deactivate" : "Activate") + '"><i class="fas fa-power-off"></i></button>' +
                    '<button class="btn btn-outline-danger" data-act="delete" data-id="' + c.id + '" title="Delete"><i class="fas fa-trash"></i></button>' +
                    '</div></td></tr>';
            }).join("") || '<tr><td colspan="7" class="text-muted text-center py-4">No courses yet. Click "Add Course".</td></tr>';
            el("coursesBody").querySelectorAll("[data-act]").forEach(function (b) {
                b.addEventListener("click", function () { courseAction(b.dataset.act, courseCache.find(function (c) { return String(c.id) === b.dataset.id; })); });
            });
            return courseCache;
        });
    }

    function courseAction(act, c) {
        if (act === "edit") return openCourseForm(c);
        if (act === "modules") { show("curriculum"); el("curriculumCourse").value = c.id; loadCurriculum(c.id); return; }
        if (act === "thumbnail") return openForm("Upload Thumbnail — " + c.courseName,
            '<div class="mb-3"><label class="form-label small fw-bold">Image (JPG / PNG / WebP, max 5 MB)</label><input type="file" class="form-control" name="file" accept="image/jpeg,image/png,image/webp" required></div>' +
            (c.thumbnailPath ? '<div class="small text-muted">Current: <code>' + esc(c.thumbnailPath) + '</code> — it will be replaced.</div>' : ""),
            function (v) {
                if (!v.file) { formError("Choose an image."); return Promise.resolve(); }
                var fd = new FormData(); fd.append("file", v.file);
                return api("/admin/courses/" + c.id + "/thumbnail", { method: "POST", body: fd }).then(function (r) { if (handle(r)) { formModal.hide(); loadCourses(); } else formError(r.message); });
            }, { submitLabel: "Upload" });
        if (act === "rename") return openForm("Rename Course", input("courseName", "Course name", c.courseName, { required: true, maxlength: 200 }), function (v) {
            return api("/admin/courses/" + c.id + "/rename", { method: "PATCH", body: v }).then(function (r) { if (handle(r)) { formModal.hide(); loadCourses(); } else formError(r.message); });
        });
        if (act === "price") return openForm("Change Price", input("price", "List price (₹)", c.price, { required: true, type: "number" }) + input("discountedPrice", "Selling price (₹) — leave blank for no discount", c.discountedPrice, { type: "number" }), function (v) {
            var body = { price: Number(v.price), discountedPrice: v.discountedPrice === "" ? null : Number(v.discountedPrice) };
            return api("/admin/courses/" + c.id + "/price", { method: "PATCH", body: body }).then(function (r) { if (handle(r)) { formModal.hide(); loadCourses(); } else formError(r.message); });
        });
        if (act === "status") {
            var next = c.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
            if (!confirmAction((next === "ACTIVE" ? "Activate" : "Deactivate") + " '" + c.courseName + "'? " + (next === "ACTIVE" ? "It will be purchasable on the website." : "It will be hidden from the website; existing students keep access."))) return;
            return api("/admin/courses/" + c.id + "/status", { method: "PATCH", body: { status: next } }).then(function (r) { if (handle(r)) loadCourses(); });
        }
        if (act === "delete") {
            if (!confirmAction("Delete '" + c.courseName + "' and all its modules and lessons? This cannot be undone.")) return;
            return api("/admin/courses/" + c.id, { method: "DELETE" }).then(function (r) { if (handle(r)) loadCourses(); });
        }
    }

    function openCourseForm(c) {
        var f = input("courseName", "Course name", c && c.courseName, { required: true, maxlength: 200 }) +
            input("courseCode", "Course code (letters, numbers, hyphens)", c && c.courseCode, { required: true, maxlength: 40, placeholder: "e.g. SMET-MASTER" }) +
            '<div class="row"><div class="col-6">' + input("price", "List price (₹)", c && c.price, { required: true, type: "number" }) + '</div><div class="col-6">' + input("discountedPrice", "Selling price (₹, optional)", c && c.discountedPrice, { type: "number" }) + '</div></div>' +
            input("shortDescription", "Short description (shown on cards)", c && c.shortDescription, { type: "textarea", rows: 2, maxlength: 500 }) +
            input("description", "Full description", c && c.description, { type: "textarea", rows: 4 }) +
            '<div class="row"><div class="col-6">' + input("duration", "Duration label", c && c.duration, { maxlength: 100, placeholder: "e.g. 12 weeks" }) + '</div><div class="col-6">' + input("displayOrder", "Display order", c ? c.displayOrder : 0, { type: "number" }) + '</div></div>' +
            input("thumbnailPath", "Thumbnail image path (optional)", c && c.thumbnailPath, { maxlength: 255, placeholder: "img/courses/my-course.jpg" }) +
            (c ? "" : '<div class="alert alert-info small py-2">New courses start as <strong>Draft</strong>. Activate them from the Courses list when ready to sell.</div>');
        openForm(c ? "Edit Course" : "Add Course", f, function (v) {
            var body = Object.assign({}, v, { price: Number(v.price), discountedPrice: v.discountedPrice === "" ? null : Number(v.discountedPrice), displayOrder: v.displayOrder === "" ? null : Number(v.displayOrder) });
            return api(c ? "/admin/courses/" + c.id : "/admin/courses", { method: c ? "PUT" : "POST", body: body }).then(function (r) {
                if (handle(r)) { formModal.hide(); loadCourses(); } else formError(r.errors ? Object.values(r.errors).join(" ") : r.message);
            });
        }, { size: "modal-lg" });
    }

    // ---- ebooks ----------------------------------------------------------------------------

    function loadEbooks() {
        return api("/admin/ebooks").then(function (res) {
            if (!handle(res, false)) return [];
            ebookCache = res.data || [];
            el("ebooksBody").innerHTML = ebookCache.map(function (e) {
                return '<tr><td><div class="d-flex align-items-center gap-2">' + (e.coverImagePath ? '<img src="' + esc(mediaUrl(e.coverImagePath)) + '" alt="" style="width:40px;height:54px;object-fit:cover;border-radius:6px;flex-shrink:0">' : '<span class="d-inline-flex align-items-center justify-content-center bg-light text-muted" style="width:40px;height:54px;border-radius:6px;flex-shrink:0"><i class="fas fa-book"></i></span>') +
                    '<div><strong>' + esc(e.title) + '</strong><br><small class="text-muted">' + esc(e.author || "") + (e.category ? ' · ' + esc(e.category) : '') + '</small><br><small class="' + (e.hasPdf ? "text-success" : "text-danger") + '">' + (e.hasPdf ? '<i class="fas fa-file-pdf me-1"></i>' + esc(e.pdfOriginalName || "PDF uploaded") : "No PDF uploaded") + '</small></div></div></td>' +
                    '<td class="font-monospace small">' + esc(e.ebookCode) + '</td>' +
                    '<td>' + inr(e.effectivePrice) + (e.discountedPrice ? '<br><small class="text-muted text-decoration-line-through">' + inr(e.price) + '</small>' : '') + '</td>' +
                    '<td>' + badge(e.status) + '</td><td>' + e.purchaseCount + '</td><td><small>' + d(e.createdAt) + '</small></td><td><small>' + d(e.updatedAt) + '</small></td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-act="edit" data-id="' + e.id + '" title="Edit"><i class="fas fa-edit"></i></button>' +
                    '<button class="btn btn-outline-primary" data-act="price" data-id="' + e.id + '" title="Change price"><i class="fas fa-tag"></i></button>' +
                    '<button class="btn btn-outline-primary" data-act="pdf" data-id="' + e.id + '" title="' + (e.hasPdf ? "Replace PDF" : "Upload PDF") + '"><i class="fas fa-file-upload"></i></button>' +
                    '<button class="btn btn-outline-primary" data-act="cover" data-id="' + e.id + '" title="' + (e.coverImagePath ? "Replace cover" : "Upload cover") + '"><i class="fas fa-image"></i></button>' +
                    (e.hasPdf ? '<button class="btn btn-outline-secondary" data-act="view" data-id="' + e.id + '" title="View PDF"><i class="fas fa-eye"></i></button>' : '') +
                    '<button class="btn btn-outline-' + (e.status === "ACTIVE" ? "warning" : "success") + '" data-act="status" data-id="' + e.id + '" title="' + (e.status === "ACTIVE" ? "Deactivate" : "Activate") + '"><i class="fas fa-power-off"></i></button>' +
                    '<button class="btn btn-outline-danger" data-act="delete" data-id="' + e.id + '" title="Delete"><i class="fas fa-trash"></i></button>' +
                    '</div></td></tr>';
            }).join("") || '<tr><td colspan="8" class="text-muted text-center py-4">No ebooks yet. Click "Add Ebook".</td></tr>';
            el("ebooksBody").querySelectorAll("[data-act]").forEach(function (b) {
                b.addEventListener("click", function () { ebookAction(b.dataset.act, ebookCache.find(function (e) { return String(e.id) === b.dataset.id; })); });
            });
            return ebookCache;
        });
    }

    function uploadForm(title, label, url, accept, done) {
        openForm(title, '<div class="mb-3"><label class="form-label small fw-bold">' + esc(label) + '</label><input type="file" class="form-control" name="file" accept="' + accept + '" required></div>', function (v) {
            if (!v.file) { formError("Choose a file."); return Promise.resolve(); }
            var fd = new FormData(); fd.append("file", v.file);
            return api(url, { method: "POST", body: fd }).then(function (r) { if (handle(r)) { formModal.hide(); done(); } else formError(r.message); });
        }, { submitLabel: "Upload" });
    }

    function ebookAction(act, e) {
        if (act === "edit") return openEbookForm(e);
        if (act === "pdf") return uploadForm((e.hasPdf ? "Replace" : "Upload") + " Ebook PDF — " + e.title, "PDF file (max " + 100 + " MB). Existing buyers read the new file immediately.", "/admin/ebooks/" + e.id + "/pdf", "application/pdf", loadEbooks);
        if (act === "cover") return uploadForm((e.coverImagePath ? "Replace" : "Upload") + " Cover — " + e.title, "Image (JPG / PNG / WebP, max 5 MB)", "/admin/ebooks/" + e.id + "/cover", "image/jpeg,image/png,image/webp", loadEbooks);
        if (act === "view") return openProtected("/admin/ebooks/" + e.id + "/pdf");
        if (act === "price") return openForm("Change Ebook Price", input("price", "List price (₹)", e.price, { required: true, type: "number" }) + input("discountedPrice", "Selling price (₹) — leave blank for no discount", e.discountedPrice, { type: "number" }), function (v) {
            var body = { price: Number(v.price), discountedPrice: v.discountedPrice === "" ? null : Number(v.discountedPrice) };
            return api("/admin/ebooks/" + e.id + "/price", { method: "PATCH", body: body }).then(function (r) { if (handle(r)) { formModal.hide(); loadEbooks(); } else formError(r.message); });
        });
        if (act === "status") {
            var next = e.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
            if (!confirmAction((next === "ACTIVE" ? "Activate" : "Deactivate") + " '" + e.title + "'? " + (next === "ACTIVE" ? "It will be purchasable on the store page." : "It will be hidden from the store; existing buyers keep access."))) return;
            return api("/admin/ebooks/" + e.id + "/status", { method: "PATCH", body: { status: next } }).then(function (r) { if (handle(r)) loadEbooks(); });
        }
        if (act === "delete") {
            if (!confirmAction("Delete '" + e.title + "'? Only possible when nobody has purchased it; otherwise deactivate it.")) return;
            return api("/admin/ebooks/" + e.id, { method: "DELETE" }).then(function (r) { if (handle(r)) loadEbooks(); });
        }
    }

    function openEbookForm(e) {
        var f = input("title", "Ebook name / title", e && e.title, { required: true, maxlength: 200 }) +
            input("price", "Price (₹)", e && e.price, { required: true, type: "number", min: 0, step: "0.01" }) +
            '<div class="mb-3"><label class="form-label small fw-bold">Cover image' + (e ? ' <span class="text-muted fw-normal">(optional replacement)</span>' : '') + '</label><input type="file" class="form-control" name="cover" accept="image/jpeg,image/png,image/webp"' + (e ? '' : ' required') + '><div class="form-text">JPG, JPEG, PNG or WebP.</div></div>' +
            '<div class="mb-3"><label class="form-label small fw-bold">Ebook PDF' + (e ? ' <span class="text-muted fw-normal">(optional replacement)</span>' : '') + '</label><input type="file" class="form-control" name="pdf" accept="application/pdf,.pdf"' + (e ? '' : ' required') + '><div class="form-text">PDF only.</div></div>' +
            input("description", "Optional information / description", e && (e.shortDescription || e.description), { type: "textarea", rows: 3, maxlength: 500 });
        openForm(e ? "Edit Ebook" : "Add Ebook", f, function (v) {
            var title = String(v.title || "").trim();
            var price = String(v.price == null ? "" : v.price).trim();
            var cover = v.cover;
            var pdf = v.pdf;
            if (!title) { formError("Ebook name is required."); return Promise.resolve(); }
            if (!price || !isFinite(Number(price)) || Number(price) < 0) { formError("Enter a valid non-negative price."); return Promise.resolve(); }
            if (!e && !cover) { formError("Please upload a valid cover image."); return Promise.resolve(); }
            if (!e && !pdf) { formError("Please upload a valid PDF Ebook."); return Promise.resolve(); }
            if (cover && (!/\.(jpe?g|png|webp)$/i.test(cover.name) || !/^image\/(jpeg|png|webp)$/i.test(cover.type))) { formError("Please upload a valid cover image."); return Promise.resolve(); }
            if (pdf && (!/\.pdf$/i.test(pdf.name) || pdf.type !== "application/pdf")) { formError("Please upload a valid PDF Ebook."); return Promise.resolve(); }
            var fd = new FormData();
            fd.append("title", title);
            fd.append("price", price);
            fd.append("description", v.description || "");
            if (cover) fd.append("cover", cover);
            if (pdf) fd.append("pdf", pdf);
            return api(e ? "/admin/ebooks/" + e.id : "/admin/ebooks", { method: e ? "PUT" : "POST", body: fd }).then(function (r) {
                if (handle(r)) { formModal.hide(); loadEbooks(); } else formError(r.errors ? Object.values(r.errors).join(" ") : r.message);
            });
        }, { size: "modal-lg", submitLabel: e ? "Save Ebook" : "Upload Ebook" });
    }

    function openGrantEbook(student) {
        api("/admin/ebooks").then(function (res) {
            if (!handle(res, false)) return;
            var opts = (res.data || []).map(function (e) { return { value: e.id, label: e.title + " (" + e.ebookCode + ")" }; });
            if (!opts.length) { toast("Add an ebook first.", false); return; }
            openForm("Grant Ebook Access — " + student.fullName, input("ebookId", "Ebook", opts[0].value, { type: "select", options: opts }) + '<div class="form-text">Manual access is marked ADMIN_MANUAL; no payment or invoice is created.</div>', function (v) {
                return api("/admin/ebook-entitlements", { method: "POST", body: { studentUserId: student.id, ebookId: Number(v.ebookId) } }).then(function (r) { if (handle(r)) { formModal.hide(); openStudent(student.id); } else formError(r.message); });
            }, { submitLabel: "Grant access" });
        });
    }

    // ---- exams: applications, exams & questions, results, certificates ---------------------------------
    // All data comes from /api/admin/exam-*, /api/admin/exams and /api/admin/certificate*; scoring,
    // attempt limits and certificate eligibility are decided on the backend — nothing here computes them.

    var examCache = [], examQuestionsFor = null;

    function fillCourseSelect(id, keep) {
        var sel = el(id); if (!sel) return Promise.resolve();
        var current = keep ? sel.value : "";
        var fill = function (courses) { sel.innerHTML = '<option value="">All courses</option>' + courses.map(function (x) { return '<option value="' + x.id + '"' + (String(x.id) === current ? " selected" : "") + '>' + esc(x.courseName) + '</option>'; }).join(""); };
        if (courseCache.length) { fill(courseCache); return Promise.resolve(); }
        return loadCourses().then(fill);
    }
    function dtz(iso) { return dt(iso); }
    function examWindowBadge(s) {
        if (!s) return "";
        var w = s.window;
        var cls = w === "OPEN" ? "success" : w === "UPCOMING" ? "warning text-dark" : w === "CLOSED" ? "secondary" : w === "CANCELLED" ? "danger" : "primary";
        return '<span class="badge bg-' + cls + '">' + esc(w) + '</span>';
    }

    // -- applications ------------------------------------------------------------------------

    function updatePendingExamApps(n) {
        n = Number(n) || 0;
        var b = el("navPendingExamApps");
        if (b) { b.innerText = n; b.classList.toggle("d-none", n === 0); }
    }

    function loadExamCounters() {
        return api("/admin/exam-applications/counters").then(function (res) {
            if (!res.success) return;
            var c = res.data; updatePendingExamApps(c.pending);
            var box = el("exAppCounters"); if (!box) return;
            box.innerHTML = [["Pending", c.pending, "#f59e0b"], ["Approved", c.approved, "#3b82f6"], ["Scheduled", c.scheduled, "#6366f1"], ["Completed", c.completed, "#10b981"], ["Rejected", c.rejected, "#ef4444"]].map(function (x) {
                return '<div class="col-6 col-md-4 col-xl"><div class="adm-card stat-card py-2"><div class="ico" style="background:' + x[2] + '20;color:' + x[2] + '"><i class="fas fa-file-signature"></i></div><div><div class="val">' + x[1] + '</div><div class="lbl">' + x[0] + '</div></div></div></div>';
            }).join("");
        });
    }

    function loadExamApplications(page) {
        state.examAppPage = page || 0;
        fillCourseSelect("exAppCourse", true);
        loadExamCounters();
        var q = "/admin/exam-applications?page=" + state.examAppPage + "&size=20";
        if (el("exAppStatus").value) q += "&status=" + el("exAppStatus").value;
        if (el("exAppCourse").value) q += "&courseId=" + el("exAppCourse").value;
        if (el("exAppSearch").value.trim()) q += "&q=" + encodeURIComponent(el("exAppSearch").value.trim());
        return api(q).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("examAppsBody").innerHTML = (p.content || []).map(function (a) {
                var s = a.schedule;
                var examStatus = a.status === "COMPLETED" ? badge(a.result) : a.status === "SCHEDULED" && s ? examWindowBadge(s) : a.status === "REJECTED" ? '<span class="text-muted small">—</span>' : '<span class="text-muted small">Not scheduled</span>';
                var lastAttempt = (a.attempts || []).filter(function (t) { return t.status !== "IN_PROGRESS"; }).slice(-1)[0];
                return '<tr><td class="font-monospace small">#' + a.id + '</td>' +
                    '<td><a href="#" data-student="' + a.studentUserId + '">' + esc(a.studentName) + '</a><br><small class="text-muted font-monospace">' + esc(a.studentId || "") + '</small></td>' +
                    '<td><small>' + esc(a.studentEmail) + '</small></td><td>' + esc(a.courseName) + '<br><small class="text-muted">Completed ' + d(a.courseCompletedAt) + '</small></td>' +
                    '<td><small>' + dt(a.appliedAt) + '</small></td><td>' + badge(a.status) + (a.adminRemarks ? '<br><small class="text-muted">' + esc(a.adminRemarks) + '</small>' : '') + '</td>' +
                    '<td>' + (a.examTitle ? '<strong>' + esc(a.examTitle) + '</strong>' : '<span class="text-muted small">Exam not assigned</span>') + (s ? '<br><small>' + esc(s.examDate) + ' · ' + esc(s.startTime) + ' – ' + esc(s.endTime) + ' IST</small>' : '') +
                    (a.maxAttempts != null ? '<br><small class="text-muted">Attempts ' + a.attemptsUsed + ' / ' + a.maxAttempts + (lastAttempt ? ' · last score ' + lastAttempt.score + '/' + lastAttempt.totalMarks : '') + '</small>' : '') + '</td>' +
                    '<td>' + examStatus + (a.certificateNumber ? '<br><small class="font-monospace">' + esc(a.certificateNumber) + '</small>' : '') + '</td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm">' +
                    (a.status === "PENDING" ? '<button class="btn btn-outline-success" data-act="approve" data-id="' + a.id + '" title="Approve"><i class="fas fa-check"></i></button>' : '') +
                    (a.status === "PENDING" || a.status === "APPROVED" || a.status === "SCHEDULED" ? '<button class="btn btn-outline-primary" data-act="schedule" data-id="' + a.id + '" title="' + (a.status === "SCHEDULED" ? "Reschedule" : "Schedule exam") + '"><i class="fas fa-calendar-alt"></i></button>' : '') +
                    (a.status === "SCHEDULED" ? '<button class="btn btn-outline-warning" data-act="cancel" data-id="' + a.id + '" title="Cancel schedule"><i class="fas fa-calendar-times"></i></button>' : '') +
                    (a.status === "PENDING" || a.status === "APPROVED" || a.status === "SCHEDULED" ? '<button class="btn btn-outline-danger" data-act="reject" data-id="' + a.id + '" title="Reject"><i class="fas fa-times"></i></button>' : '') +
                    ((a.attempts || []).length ? '<button class="btn btn-outline-secondary" data-act="attempts" data-id="' + a.id + '" title="View attempts"><i class="fas fa-list-ol"></i></button>' : '') +
                    '</div></td></tr>';
            }).join("") || '<tr><td colspan="9" class="text-muted text-center py-4">No exam applications' + (el("exAppStatus").value ? ' with this status' : ' yet') + '.</td></tr>';
            el("examAppsCount").innerText = p.totalElements + " application(s)";
            pager("examAppsPager", p, loadExamApplications);
            bindStudentLinks(el("examAppsBody"));
            el("examAppsBody").querySelectorAll("[data-act]").forEach(function (b) {
                var a = (p.content || []).find(function (x) { return String(x.id) === b.dataset.id; });
                b.addEventListener("click", function () { examAppAction(b.dataset.act, a); });
            });
        });
    }

    function examAppAction(act, a) {
        if (act === "approve") {
            if (!confirmAction("Approve the exam application of " + a.studentName + " for '" + a.courseName + "'? You can schedule the exam afterwards.")) return;
            return api("/admin/exam-applications/" + a.id + "/approve", { method: "POST", body: {} }).then(function (r) { if (handle(r)) loadExamApplications(state.examAppPage); });
        }
        if (act === "reject") {
            return openForm("Reject Exam Application — " + a.studentName, '<p class="small text-muted">The student will see the application as Rejected and can apply again later if eligible.</p>' + input("remarks", "Remarks for the student (optional)", "", { type: "textarea", rows: 3, maxlength: 500 }), function (v) {
                return api("/admin/exam-applications/" + a.id + "/reject", { method: "POST", body: { remarks: v.remarks || null } }).then(function (r) { if (handle(r)) { formModal.hide(); loadExamApplications(state.examAppPage); } else formError(r.message); });
            }, { submitLabel: "Reject application" });
        }
        if (act === "cancel") {
            if (!confirmAction("Cancel the scheduled exam window for " + a.studentName + "? Finished attempts are kept; the application goes back to Approved and can be rescheduled.")) return;
            return api("/admin/exam-applications/" + a.id + "/cancel-schedule", { method: "POST", body: {} }).then(function (r) { if (handle(r)) loadExamApplications(state.examAppPage); });
        }
        if (act === "schedule") return openScheduleForm(a);
        if (act === "attempts") return showExamAttempts(a);
    }

    function openScheduleForm(a) {
        api("/admin/exams?courseId=" + a.courseId).then(function (res) {
            if (!handle(res, false)) return;
            var exams = (res.data || []).filter(function (e) { return e.status === "ACTIVE"; });
            if (!exams.length) { toast("No ACTIVE exam exists for '" + a.courseName + "'. Create the exam, add its questions and activate it first (Exams section).", false); return; }
            var s = a.schedule || {};
            var today = new Date(); var pad = function (n) { return (n < 10 ? "0" : "") + n; };
            var defDate = s.startsAt ? new Date(s.startsAt) : new Date(today.getTime() + 86400000);
            var dateStr = defDate.getFullYear() + "-" + pad(defDate.getMonth() + 1) + "-" + pad(defDate.getDate());
            var startStr = s.startsAt ? pad(new Date(s.startsAt).getHours()) + ":" + pad(new Date(s.startsAt).getMinutes()) : "10:00";
            var endStr = s.endsAt ? pad(new Date(s.endsAt).getHours()) + ":" + pad(new Date(s.endsAt).getMinutes()) : "12:00";
            var f = '<div class="small text-muted mb-3"><strong>' + esc(a.studentName) + '</strong> (' + esc(a.studentId || "") + ') · ' + esc(a.courseName) + (a.status === "SCHEDULED" ? ' · <span class="text-warning">Rescheduling (the student is notified)</span>' : '') + '</div>' +
                input("examId", "Exam", a.examId || exams[0].id, { type: "select", options: exams.map(function (e) { return { value: e.id, label: e.title + " — " + e.totalMarks + " marks, pass " + e.passingMarks + ", " + e.maxAttempts + " attempt(s)" + (e.marksConsistent ? "" : " [questions ≠ total marks]") }; }) }) +
                '<div class="row"><div class="col-md-4">' + input("examDate", "Exam date", dateStr, { type: "date", required: true }) + '</div>' +
                '<div class="col-md-4">' + input("startTime", "Start time (IST)", startStr, { type: "time", required: true }) + '</div>' +
                '<div class="col-md-4">' + input("endTime", "End time (IST)", endStr, { type: "time", required: true }) + '</div></div>' +
                input("instructions", "Instructions for the student (optional)", s.instructions || "", { type: "textarea", rows: 3, maxlength: 5000 }) +
                '<div class="form-text">The student can start the exam only between the start and end time on the server clock. Times are India Standard Time.</div>';
            openForm((a.status === "SCHEDULED" ? "Reschedule" : "Schedule") + " Exam", f, function (v) {
                if (!v.examDate || !v.startTime || !v.endTime) { formError("Exam date, start time and end time are required."); return Promise.resolve(); }
                if (v.endTime <= v.startTime) { formError("End time must be after start time."); return Promise.resolve(); }
                return api("/admin/exam-applications/" + a.id + "/schedule", { method: "POST", body: { examId: Number(v.examId), examDate: v.examDate, startTime: v.startTime, endTime: v.endTime, instructions: v.instructions || null } })
                    .then(function (r) { if (handle(r)) { formModal.hide(); loadExamApplications(state.examAppPage); } else formError(r.errors ? Object.values(r.errors).join(" ") : r.message); });
            }, { size: "modal-lg", submitLabel: a.status === "SCHEDULED" ? "Save new schedule" : "Schedule exam" });
        });
    }

    function showExamAttempts(a) {
        api("/admin/exam-applications/" + a.id + "/attempts").then(function (res) {
            if (!handle(res, false)) return;
            var rows = (res.data || []).map(function (t) {
                return '<tr><td>' + t.attemptNumber + '</td><td>' + badge(t.status) + '</td><td><strong>' + t.score + ' / ' + t.totalMarks + '</strong></td><td>' + t.passingMarks + '</td><td>' + t.correctCount + ' / ' + t.questionCount + '</td><td>' + (t.status === "IN_PROGRESS" ? '<span class="text-muted">—</span>' : badge(t.passed ? "PASSED" : "FAILED")) + '</td><td><small>' + dt(t.startedAt) + '</small></td><td><small>' + dt(t.submittedAt) + '</small></td></tr>';
            }).join("") || '<tr><td colspan="8" class="text-muted">No attempts.</td></tr>';
            openForm("Attempts — " + a.studentName + " · " + (a.examTitle || a.courseName), '<div class="table-responsive"><table class="table table-sm mb-0"><thead><tr><th>#</th><th>Status</th><th>Score</th><th>Passing</th><th>Correct</th><th>Result</th><th>Started</th><th>Submitted</th></tr></thead><tbody>' + rows + '</tbody></table></div><div class="form-text mt-2">Results are computed by the server and cannot be edited.</div>',
                function () { formModal.hide(); return Promise.resolve(); }, { size: "modal-lg", submitLabel: "Close" });
        });
    }

    // -- exams & questions -------------------------------------------------------------------

    function loadExams() {
        fillCourseSelect("examCourseFilter", true);
        var q = "/admin/exams" + (el("examCourseFilter").value ? "?courseId=" + el("examCourseFilter").value : "");
        return api(q).then(function (res) {
            if (!handle(res, false)) return [];
            examCache = res.data || [];
            el("examsBody").innerHTML = examCache.map(function (e) {
                return '<tr><td><strong>' + esc(e.title) + '</strong>' + (e.description ? '<br><small class="text-muted">' + esc(e.description.length > 80 ? e.description.slice(0, 80) + "…" : e.description) + '</small>' : '') + '</td>' +
                    '<td>' + esc(e.courseName) + '<br><small class="text-muted font-monospace">' + esc(e.courseCode) + '</small></td>' +
                    '<td>' + e.totalMarks + '</td><td>' + e.passingMarks + '</td><td>' + e.maxAttempts + '</td><td>' + (e.durationMinutes ? e.durationMinutes + ' min' : '<span class="text-muted">Window</span>') + '</td>' +
                    '<td>' + e.questionCount + ' <small class="text-muted">(' + e.questionMarksTotal + ' marks)</small>' + (e.questionCount && !e.marksConsistent ? '<br><small class="text-danger"><i class="fas fa-exclamation-triangle me-1"></i>Questions ≠ total marks</small>' : '') + '</td>' +
                    '<td>' + badge(e.status) + '</td><td>' + e.applicationCount + ' / ' + e.attemptCount + '</td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-act="questions" data-id="' + e.id + '" title="Manage questions"><i class="fas fa-list"></i> Questions</button>' +
                    '<button class="btn btn-outline-primary" data-act="edit" data-id="' + e.id + '" title="Edit exam settings"><i class="fas fa-edit"></i></button>' +
                    '<button class="btn btn-outline-' + (e.status === "ACTIVE" ? "warning" : "success") + '" data-act="status" data-id="' + e.id + '" title="' + (e.status === "ACTIVE" ? "Deactivate" : "Activate") + '"><i class="fas fa-power-off"></i></button>' +
                    '<button class="btn btn-outline-danger" data-act="delete" data-id="' + e.id + '" title="Delete"><i class="fas fa-trash"></i></button>' +
                    '</div></td></tr>';
            }).join("") || '<tr><td colspan="10" class="text-muted text-center py-4">No exams yet. Click "Create Exam".</td></tr>';
            el("examsBody").querySelectorAll("[data-act]").forEach(function (b) {
                b.addEventListener("click", function () { examAction(b.dataset.act, examCache.find(function (e) { return String(e.id) === b.dataset.id; })); });
            });
            if (examQuestionsFor) { var still = examCache.find(function (e) { return e.id === examQuestionsFor.id; }); if (still) loadExamQuestions(still); }
            return examCache;
        });
    }

    function examAction(act, e) {
        if (act === "edit") return openExamForm(e);
        if (act === "questions") return loadExamQuestions(e);
        if (act === "status") {
            var next = e.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
            if (!confirmAction((next === "ACTIVE" ? "Activate" : "Deactivate") + " '" + e.title + "'? " + (next === "ACTIVE" ? "It can then be scheduled for approved applications." : "It can no longer be scheduled or started."))) return;
            return api("/admin/exams/" + e.id + "/status", { method: "PATCH", body: { status: next } }).then(function (r) { if (handle(r)) loadExams(); });
        }
        if (act === "delete") {
            if (!confirmAction("Delete '" + e.title + "' and all its questions? Only possible when no application or attempt refers to it.")) return;
            return api("/admin/exams/" + e.id, { method: "DELETE" }).then(function (r) { if (handle(r)) { if (examQuestionsFor && examQuestionsFor.id === e.id) closeExamQuestions(); loadExams(); } });
        }
    }

    function openExamForm(e) {
        var run = function (courses) {
            var opts = courses.map(function (c) { return { value: c.id, label: c.courseName + " (" + c.courseCode + ")" }; });
            if (!opts.length) { toast("Create a course first.", false); return; }
            var f = input("courseId", "Course", e ? e.courseId : opts[0].value, { type: "select", options: opts }) +
                input("title", "Exam name / title", e && e.title, { required: true, maxlength: 200, placeholder: "e.g. Final Course Exam" }) +
                '<div class="row"><div class="col-md-4">' + input("totalMarks", "Total marks", e ? e.totalMarks : 50, { type: "number", required: true }) + '</div>' +
                '<div class="col-md-4">' + input("passingMarks", "Passing marks", e ? e.passingMarks : 30, { type: "number", required: true }) + '</div>' +
                '<div class="col-md-4">' + input("maxAttempts", "Maximum attempts", e ? e.maxAttempts : 3, { type: "number", required: true }) + '</div></div>' +
                input("durationMinutes", "Time limit per attempt in minutes (optional)", e && e.durationMinutes, { type: "number", help: "Leave blank to allow the whole scheduled window." }) +
                input("description", "Instructions shown to the student (optional)", e && e.description, { type: "textarea", rows: 3, maxlength: 5000 }) +
                '<div class="form-text">The questions you add must add up to exactly the total marks before the exam can be activated. Passing marks cannot exceed total marks.</div>';
            openForm(e ? "Edit Exam" : "Create Exam", f, function (v) {
                var total = Number(v.totalMarks), pass = Number(v.passingMarks), attempts = Number(v.maxAttempts);
                if (!String(v.title || "").trim()) { formError("Exam title is required."); return Promise.resolve(); }
                if (!(total >= 1) || total % 1 !== 0) { formError("Total marks must be a whole number of at least 1."); return Promise.resolve(); }
                if (!(pass >= 0) || pass % 1 !== 0) { formError("Passing marks must be a whole number (0 or more)."); return Promise.resolve(); }
                if (pass > total) { formError("Passing marks cannot be greater than total marks."); return Promise.resolve(); }
                if (!(attempts >= 1) || attempts % 1 !== 0) { formError("Maximum attempts must be a whole number of at least 1."); return Promise.resolve(); }
                var body = { courseId: Number(v.courseId), title: v.title.trim(), description: v.description || null, totalMarks: total, passingMarks: pass, maxAttempts: attempts, durationMinutes: v.durationMinutes ? Number(v.durationMinutes) : null };
                return api(e ? "/admin/exams/" + e.id : "/admin/exams", { method: e ? "PUT" : "POST", body: body }).then(function (r) {
                    if (handle(r)) { formModal.hide(); loadExams().then(function () { if (!e && r.data) loadExamQuestions(r.data); }); } else formError(r.errors ? Object.values(r.errors).join(" ") : r.message);
                });
            }, { size: "modal-lg", submitLabel: e ? "Save Exam" : "Create Exam" });
        };
        if (courseCache.length) run(courseCache); else loadCourses().then(run);
    }

    function loadExamQuestions(e) {
        examQuestionsFor = e;
        var panel = el("examQuestionsPanel"); panel.classList.remove("d-none");
        el("examQuestionsTitle").innerText = "Questions — " + e.title;
        el("examQuestionsMeta").innerHTML = esc(e.courseName) + " · total " + e.totalMarks + " marks · passing " + e.passingMarks + " · " + e.questionCount + " question(s) worth " + e.questionMarksTotal + " marks" +
            (e.questionCount && !e.marksConsistent ? ' <span class="text-danger">— questions must add up to ' + e.totalMarks + ' marks before activation</span>' : e.questionCount ? ' <span class="text-success">— consistent</span>' : '');
        el("examAddQuestionBtn").onclick = function () { openQuestionForm(e, null); };
        api("/admin/exams/" + e.id + "/questions").then(function (res) {
            if (!handle(res, false)) return;
            var qs = res.data || [];
            el("examQuestionsBody").innerHTML = qs.length ? qs.map(function (q, i) {
                var opt = function (k) { return '<div class="' + (q.correctOption === k ? "text-success fw-bold" : "") + '"><span class="badge ' + (q.correctOption === k ? "bg-success" : "bg-light text-dark border") + ' me-1">' + k + '</span>' + esc(q["option" + k]) + (q.correctOption === k ? ' <i class="fas fa-check-circle"></i>' : '') + '</div>'; };
                return '<div class="p-3 border-bottom' + (q.active ? "" : " bg-light") + '"><div class="d-flex justify-content-between align-items-start gap-2 flex-wrap">' +
                    '<div style="flex:1;min-width:240px"><strong>Q' + (i + 1) + '.</strong> ' + esc(q.questionText).replace(/\n/g, "<br>") + (q.active ? '' : ' <span class="badge bg-secondary">Inactive</span>') + '<div class="mt-2 small" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:4px 16px">' + opt("A") + opt("B") + opt("C") + opt("D") + '</div></div>' +
                    '<div class="text-nowrap"><span class="badge badge-soft me-2">' + q.marks + ' mark' + (q.marks === 1 ? "" : "s") + '</span><div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-qact="edit" data-id="' + q.id + '" title="Edit"><i class="fas fa-edit"></i></button>' +
                    '<button class="btn btn-outline-danger" data-qact="delete" data-id="' + q.id + '" title="Delete"><i class="fas fa-trash"></i></button></div></div></div>' +
                    (q.answerCount ? '<small class="text-muted">Answered ' + q.answerCount + ' time(s) in submitted attempts — deleting will deactivate it instead.</small>' : '') + '</div>';
            }).join("") : '<div class="p-4 text-muted text-center">No questions yet. Add MCQ questions with exactly four options and one correct answer.</div>';
            el("examQuestionsBody").querySelectorAll("[data-qact]").forEach(function (b) {
                var q = qs.find(function (x) { return String(x.id) === b.dataset.id; });
                b.addEventListener("click", function () {
                    if (b.dataset.qact === "edit") return openQuestionForm(e, q);
                    if (!confirmAction("Delete this question?")) return;
                    api("/admin/exams/" + e.id + "/questions/" + q.id, { method: "DELETE" }).then(function (r) { if (handle(r)) loadExams(); });
                });
            });
            panel.scrollIntoView({ behavior: "smooth", block: "start" });
        });
    }

    function closeExamQuestions() { examQuestionsFor = null; el("examQuestionsPanel").classList.add("d-none"); }

    function openQuestionForm(e, q) {
        var f = input("questionText", "Question", q && q.questionText, { type: "textarea", rows: 3, required: true, maxlength: 5000 }) +
            '<div class="row">' + ["A", "B", "C", "D"].map(function (k) { return '<div class="col-md-6">' + input("option" + k, "Option " + k, q && q["option" + k], { required: true, maxlength: 1000 }) + '</div>'; }).join("") + '</div>' +
            '<div class="row"><div class="col-md-6">' + input("correctOption", "Correct answer", q ? q.correctOption : "", { type: "select", options: [{ value: "", label: "— select —" }, { value: "A", label: "A" }, { value: "B", label: "B" }, { value: "C", label: "C" }, { value: "D", label: "D" }] }) + '</div>' +
            '<div class="col-md-6">' + input("marks", "Marks", q ? q.marks : 1, { type: "number", required: true }) + '</div></div>' +
            '<div class="form-text">All four options are required and must be different. The correct answer is stored on the server and never sent to students.</div>';
        openForm((q ? "Edit" : "Add") + " Question — " + e.title, f, function (v) {
            var opts = ["A", "B", "C", "D"];
            if (!String(v.questionText || "").trim()) { formError("Question is required."); return Promise.resolve(); }
            for (var i = 0; i < 4; i++) { if (!String(v["option" + opts[i]] || "").trim()) { formError("Option " + opts[i] + " is required."); return Promise.resolve(); } }
            var seen = {}; for (var j = 0; j < 4; j++) { var t = v["option" + opts[j]].trim().toLowerCase(); if (seen[t]) { formError("Option " + opts[j] + " duplicates another option. All four options must be different."); return Promise.resolve(); } seen[t] = true; }
            if (!v.correctOption) { formError("Select the correct answer (A, B, C or D)."); return Promise.resolve(); }
            var marks = Number(v.marks); if (!(marks >= 1) || marks % 1 !== 0) { formError("Marks must be a whole number of at least 1."); return Promise.resolve(); }
            var body = { questionText: v.questionText.trim(), optionA: v.optionA.trim(), optionB: v.optionB.trim(), optionC: v.optionC.trim(), optionD: v.optionD.trim(), correctOption: v.correctOption, marks: marks };
            return api(q ? "/admin/exams/" + e.id + "/questions/" + q.id : "/admin/exams/" + e.id + "/questions", { method: q ? "PUT" : "POST", body: body }).then(function (r) {
                if (handle(r)) { formModal.hide(); loadExams(); } else formError(r.errors ? Object.values(r.errors).join(" ") : r.message);
            });
        }, { size: "modal-lg", submitLabel: q ? "Save Question" : "Add Question" });
    }

    // -- results -----------------------------------------------------------------------------

    function loadExamResults(page) {
        state.examResultPage = page || 0;
        fillCourseSelect("exResCourse", true);
        if (!examCache.length || el("exResExam").options.length <= 1) {
            api("/admin/exams").then(function (r) { if (r.success) { examCache = r.data || []; var cur = el("exResExam").value; el("exResExam").innerHTML = '<option value="">All exams</option>' + examCache.map(function (e) { return '<option value="' + e.id + '"' + (String(e.id) === cur ? " selected" : "") + '>' + esc(e.title) + ' (' + esc(e.courseCode) + ')</option>'; }).join(""); } });
        }
        var q = "/admin/exam-results?page=" + state.examResultPage + "&size=20";
        if (el("exResCourse").value) q += "&courseId=" + el("exResCourse").value;
        if (el("exResExam").value) q += "&examId=" + el("exResExam").value;
        if (el("exResPassed").value) q += "&passed=" + el("exResPassed").value;
        if (el("exResSearch").value.trim()) q += "&q=" + encodeURIComponent(el("exResSearch").value.trim());
        return api(q).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("examResultsBody").innerHTML = (p.content || []).map(function (r) {
                return '<tr><td><a href="#" data-student="' + r.studentUserId + '">' + esc(r.studentName) + '</a><br><small class="text-muted">' + esc(r.studentEmail) + '</small></td><td class="font-monospace small">' + esc(r.studentId || "") + '</td>' +
                    '<td>' + esc(r.courseName) + '</td><td>' + esc(r.examTitle) + '</td><td>' + r.attemptNumber + ' / ' + r.maxAttempts + '</td><td><strong>' + r.score + '</strong></td><td>' + r.totalMarks + '</td><td>' + r.passingMarks + '</td>' +
                    '<td>' + (r.status === "EXPIRED" ? badge("EXPIRED") : badge(r.passed ? "PASSED" : "FAILED")) + '</td><td><small>' + dt(r.submittedAt) + '</small></td>' +
                    '<td>' + (r.certificateId ? '<a href="#" class="font-monospace small" data-cert-pdf="' + r.certificateId + '">' + esc(r.certificateNumber) + '</a>' : (r.passed ? '<span class="text-success small">Eligible</span>' : '<span class="text-muted small">Not eligible</span>')) + '</td></tr>';
            }).join("") || '<tr><td colspan="11" class="text-muted text-center py-4">No submitted attempts match.</td></tr>';
            el("examResultsCount").innerText = p.totalElements + " attempt(s)";
            pager("examResultsPager", p, loadExamResults);
            bindStudentLinks(el("examResultsBody"));
            bindCertificateLinks(el("examResultsBody"));
        });
    }

    // -- certificates & templates ------------------------------------------------------------

    function bindCertificateLinks(root) {
        root.querySelectorAll("[data-cert-pdf]").forEach(function (a) {
            a.addEventListener("click", function (e) { e.preventDefault(); openProtected("/admin/certificates/" + a.dataset.certPdf + "/pdf"); });
        });
    }

    function loadCertificates(page) {
        state.certPage = page || 0;
        fillCourseSelect("certCourse", true);
        loadCertificateTemplates();
        var q = "/admin/certificates?page=" + state.certPage + "&size=20";
        if (el("certCourse").value) q += "&courseId=" + el("certCourse").value;
        if (el("certSearch").value.trim()) q += "&q=" + encodeURIComponent(el("certSearch").value.trim());
        return api(q).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("certificatesBody").innerHTML = (p.content || []).map(function (c) {
                return '<tr><td class="font-monospace small">' + esc(c.certificateNumber) + '</td><td><a href="#" data-student="' + c.studentUserId + '">' + esc(c.studentName) + '</a></td><td class="font-monospace small">' + esc(c.studentId || "") + '</td>' +
                    '<td>' + esc(c.courseName) + '</td><td>' + esc(c.examTitle) + '<br><small class="text-muted">Attempt ' + c.attemptNumber + '</small></td><td>' + c.score + ' / ' + c.totalMarks + '<br><small class="text-muted">pass ' + c.passingMarks + '</small></td>' +
                    '<td><small>' + dt(c.issuedAt) + '</small></td><td><small>' + esc(c.templateName) + '</small></td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm"><button class="btn btn-outline-primary" data-cert-pdf="' + c.id + '" title="View certificate PDF"><i class="fas fa-eye"></i> View</button></div></td></tr>';
            }).join("") || '<tr><td colspan="9" class="text-muted text-center py-4">No certificates issued yet. Certificates are issued automatically when a student passes an exam.</td></tr>';
            el("certificatesCount").innerText = p.totalElements + " certificate(s)";
            pager("certificatesPager", p, loadCertificates);
            bindStudentLinks(el("certificatesBody"));
            bindCertificateLinks(el("certificatesBody"));
        });
    }

    function loadCertificateTemplates() {
        return api("/admin/certificate-templates").then(function (res) {
            if (!handle(res, false)) return;
            var s = res.data;
            el("certTemplateStatus").innerHTML = '<h6 class="fw-bold mb-2"><i class="fas fa-award me-2 text-warning"></i>Active certificate design</h6>' +
                '<div class="d-flex align-items-center gap-2 mb-2"><span class="badge bg-' + (s.usingDefault ? "primary" : "success") + '">' + (s.usingDefault ? "DEFAULT" : "CUSTOM") + '</span><strong>' + esc(s.activeTemplateName) + '</strong></div>' +
                '<p class="small text-muted mb-3">' + (s.usingDefault ? 'New certificates use the built-in Lord Sai certificate (blue frame, Lord Sai logo, founder signature block). It is always available and cannot be deleted.'
                    : 'New certificates are rendered on the uploaded background "' + esc(s.activeTemplateName) + '" with the student, course and result text laid over it. Certificates already issued are not changed.') + '</p>' +
                '<div class="d-flex flex-wrap gap-2"><button class="btn btn-sm btn-light" onclick="Admin.previewCertificateTemplate()"><i class="fas fa-eye me-1"></i> Preview</button>' +
                (s.usingDefault ? '' : '<button class="btn btn-sm btn-outline-primary" onclick="Admin.useDefaultCertificateTemplate()"><i class="fas fa-undo me-1"></i> Use default Lord Sai certificate</button>') + '</div>' +
                '<div class="form-text mt-3">Upload a JPG or PNG designed as A4 landscape (297 × 210 mm, e.g. 3508 × 2480 px). Keep the middle area free — the student name, course and result are printed there.</div>';
            el("certTemplatesBody").innerHTML = (s.templates || []).map(function (t) {
                return '<tr><td><strong>' + esc(t.name) + '</strong><br><small class="text-muted">by ' + esc(t.uploadedBy || "—") + '</small></td><td><small>' + esc(t.originalName || "") + (t.sizeBytes ? ' · ' + Math.round(t.sizeBytes / 1024) + ' KB' : '') + '</small></td><td><small>' + d(t.createdAt) + '</small></td><td>' + t.certificatesIssued + '</td>' +
                    '<td>' + (t.active ? '<span class="badge bg-success">ACTIVE</span>' : '<span class="badge bg-secondary">INACTIVE</span>') + '</td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-secondary" data-tact="view" data-id="' + t.id + '" title="View image"><i class="fas fa-image"></i></button>' +
                    (t.active ? '' : '<button class="btn btn-outline-success" data-tact="activate" data-id="' + t.id + '" title="Activate"><i class="fas fa-check"></i></button>') +
                    '<button class="btn btn-outline-danger" data-tact="delete" data-id="' + t.id + '" title="Delete"><i class="fas fa-trash"></i></button></div></td></tr>';
            }).join("") + '<tr class="table-light"><td><strong>' + esc(s.defaultTemplateName) + '</strong><br><small class="text-muted">built-in</small></td><td><small>classpath template</small></td><td>—</td><td>—</td><td>' + (s.usingDefault ? '<span class="badge bg-success">ACTIVE</span>' : '<span class="badge bg-secondary">FALLBACK</span>') + '</td><td>' + (s.usingDefault ? '' : '<button class="btn btn-sm btn-outline-success" onclick="Admin.useDefaultCertificateTemplate()"><i class="fas fa-check"></i></button>') + '</td></tr>';
            el("certTemplatesBody").querySelectorAll("[data-tact]").forEach(function (b) {
                var t = (s.templates || []).find(function (x) { return String(x.id) === b.dataset.id; });
                b.addEventListener("click", function () {
                    if (b.dataset.tact === "view") return openProtected("/admin/certificate-templates/" + t.id + "/image");
                    if (b.dataset.tact === "activate") { if (!confirmAction("Use '" + t.name + "' for all new certificates?")) return; return api("/admin/certificate-templates/" + t.id + "/activate", { method: "PATCH" }).then(function (r) { if (handle(r)) loadCertificateTemplates(); }); }
                    if (b.dataset.tact === "delete") { if (!confirmAction("Delete template '" + t.name + "'? If certificates were issued with it, it is deactivated instead. The default Lord Sai certificate is used when no design is active.")) return; return api("/admin/certificate-templates/" + t.id, { method: "DELETE" }).then(function (r) { if (handle(r)) loadCertificateTemplates(); }); }
                });
            });
        });
    }

    function openCertificateTemplateForm() {
        openForm("Upload Certificate Template", input("name", "Template name", "", { required: true, maxlength: 150, placeholder: "e.g. Gold border design 2026" }) +
            '<div class="mb-3"><label class="form-label small fw-bold">Background image (JPG / PNG, A4 landscape, max 10 MB)</label><input type="file" class="form-control" name="file" accept="image/jpeg,image/png" required></div>' +
            '<div class="form-check mb-2"><input class="form-check-input" type="checkbox" name="activate" id="ctActivate" checked><label class="form-check-label small" for="ctActivate">Activate immediately (new certificates use this design)</label></div>' +
            '<div class="form-text">The default Lord Sai certificate stays available; you can switch back at any time.</div>', function (v) {
            if (!v.file) { formError("Choose a JPG or PNG image."); return Promise.resolve(); }
            if (!/\.(jpe?g|png)$/i.test(v.file.name)) { formError("Only JPG and PNG images are supported."); return Promise.resolve(); }
            var fd = new FormData(); fd.append("file", v.file); fd.append("name", v.name || ""); fd.append("activate", v.activate ? "true" : "false");
            return api("/admin/certificate-templates", { method: "POST", body: fd }).then(function (r) { if (handle(r)) { formModal.hide(); loadCertificateTemplates(); } else formError(r.message); });
        }, { submitLabel: "Upload" });
    }

    function useDefaultCertificateTemplate() {
        if (!confirmAction("Switch new certificates to the default Lord Sai certificate?")) return;
        api("/admin/certificate-templates/use-default", { method: "POST", body: {} }).then(function (r) { if (handle(r)) loadCertificateTemplates(); });
    }

    function previewCertificateTemplate() { openProtected("/admin/certificate-templates/preview"); }

    // ---- protected downloads (bearer header, never a plain link) ----------------------------------

    function fetchProtected(path) {
        var session = LSI_Auth.getSession();
        return fetch(LSI_Auth.apiBase + path, { headers: { Authorization: "Bearer " + (session ? session.token : "") } }).then(function (r) {
            if (r.ok) return r.blob();
            return r.text().then(function (t) { var m = "Request failed (" + r.status + ")"; try { m = JSON.parse(t).message || m; } catch (e) { /* ignore */ } throw new Error(m); });
        });
    }
    function openProtected(path, download, filename) {
        fetchProtected(path).then(function (blob) {
            var url = URL.createObjectURL(blob);
            if (download) { var a = document.createElement("a"); a.href = url; a.download = filename || "download"; document.body.appendChild(a); a.click(); a.remove(); }
            else window.open(url, "_blank");
            setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
        }).catch(function (e) { toast(e.message, false); });
    }
    function bindInvoiceLinks(root) {
        root.querySelectorAll("[data-invoice-pdf]").forEach(function (a) {
            a.addEventListener("click", function (e) { e.preventDefault(); openProtected("/automation/invoices/" + a.dataset.invoicePdf + "/pdf"); });
        });
    }

    // ---- invoices -----------------------------------------------------------------------------------

    function invoiceQuery() {
        var q = {};
        if (el("invProduct").value) q.productType = el("invProduct").value;
        if (el("invStatus").value) q.status = el("invStatus").value;
        if (el("invFrom").value) q.from = el("invFrom").value;
        if (el("invTo").value) q.to = el("invTo").value;
        if (el("invSearch").value.trim()) q.q = el("invSearch").value.trim();
        return q;
    }
    function qs(obj) { return Object.keys(obj).map(function (k) { return k + "=" + encodeURIComponent(obj[k]); }).join("&"); }

    function loadInvoices(page) {
        state.invoicePage = page;
        var q = invoiceQuery(); q.page = page; q.size = 25;
        api("/automation/invoices?" + qs(q)).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("invoicesBody").innerHTML = (p.content || []).map(function (i) {
                return '<tr><td class="font-monospace small">' + esc(i.invoiceNumber) + '</td><td><a href="#" data-student="' + i.studentUserId + '">' + esc(i.studentName) + '</a><br><small class="text-muted">' + esc(i.studentEmail) + '</small></td><td class="font-monospace small">' + esc(i.studentId || "—") + '</td><td>' + esc(i.productName) + '</td><td>' + badge(i.productType) + '</td><td><strong>' + inr(i.total) + '</strong>' + (Number(i.discount) > 0 ? '<br><small class="text-muted">disc. ' + inr(i.discount) + '</small>' : '') + '</td><td>' + badge(i.paymentStatus === "SUCCESS" ? "SUCCESS" : i.paymentStatus) + '</td><td class="font-monospace"><small>' + esc(i.transactionId || "—") + '</small></td><td><small>' + dt(i.purchaseDate) + '</small></td><td><small>' + (i.emailedAt ? dt(i.emailedAt) + ' (' + i.emailCount + ')' : '<span class="text-danger">Not emailed</span>') + '</small></td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-inv="view" data-id="' + i.id + '" title="View PDF"><i class="fas fa-eye"></i></button>' +
                    '<button class="btn btn-outline-primary" data-inv="download" data-id="' + i.id + '" data-no="' + esc(i.invoiceNumber) + '" title="Download PDF"><i class="fas fa-download"></i></button>' +
                    '<button class="btn btn-outline-secondary" data-inv="print" data-id="' + i.id + '" title="Print"><i class="fas fa-print"></i></button>' +
                    '<button class="btn btn-outline-success" data-inv="email" data-id="' + i.id + '" title="Resend email"><i class="fas fa-envelope"></i></button>' +
                    '<button class="btn btn-outline-success" data-inv="whatsapp" data-id="' + i.id + '" title="Send via WhatsApp"><i class="fab fa-whatsapp"></i></button>' +
                    '</div></td></tr>';
            }).join("") || '<tr><td colspan="11" class="text-muted text-center py-4">No invoices match these filters.</td></tr>';
            el("invoicesCount").innerText = p.totalElements + " invoice(s)";
            pager("invoicesPager", p, loadInvoices);
            bindStudentLinks(el("invoicesBody"));
            el("invoicesBody").querySelectorAll("[data-inv]").forEach(function (b) {
                b.addEventListener("click", function () {
                    var id = b.dataset.id, act = b.dataset.inv;
                    if (act === "view") openProtected("/automation/invoices/" + id + "/pdf");
                    else if (act === "download") openProtected("/automation/invoices/" + id + "/pdf?download=true", true, b.dataset.no + ".pdf");
                    else if (act === "print") printProtected("/automation/invoices/" + id + "/print");
                    else if (act === "email") { if (confirmAction("Email this invoice (PDF attached) to the student again?")) api("/automation/invoices/" + id + "/resend-email", { method: "POST" }).then(function (r) { if (handle(r)) loadInvoices(state.invoicePage); }); }
                    else if (act === "whatsapp") { if (confirmAction("Send this invoice PDF to the student on WhatsApp?")) api("/automation/invoices/" + id + "/whatsapp", { method: "POST" }).then(function (r) { handle(r); }); }
                });
            });
        });
    }

    /** Opens the server-rendered printer-friendly page in a new window and triggers print. */
    function printProtected(path) {
        var session = LSI_Auth.getSession();
        fetch(LSI_Auth.apiBase + path, { headers: { Authorization: "Bearer " + (session ? session.token : "") } }).then(function (r) { return r.ok ? r.text() : Promise.reject(new Error("Could not load the print view (" + r.status + ")")); }).then(function (html) {
            var w = window.open("", "_blank");
            if (!w) { toast("Allow pop-ups to print.", false); return; }
            w.document.open(); w.document.write(html); w.document.close();
            w.focus(); setTimeout(function () { w.print(); }, 400);
        }).catch(function (e) { toast(e.message, false); });
    }

    function reportQueryFor(report) {
        if (report === "invoices") return invoiceQuery();
        return {};
    }
    function exportReport(report, format) {
        var q = reportQueryFor(report);
        openProtected("/automation/reports/" + report + "/" + format + "?" + qs(q), true, report + "." + format);
    }
    function printReport(report) {
        printProtected("/automation/reports/" + report + "/print?" + qs(reportQueryFor(report)));
    }

    // ---- curriculum ------------------------------------------------------------------------

    function initCurriculum() {
        loadCourses().then(function (courses) {
            var sel = el("curriculumCourse");
            var current = sel.value;
            sel.innerHTML = '<option value="">Select a course...</option>' + courses.map(function (c) { return '<option value="' + c.id + '">' + esc(c.courseName) + ' (' + esc(c.courseCode) + ')</option>'; }).join("");
            if (current) { sel.value = current; loadCurriculum(current); }
        });
    }

    function loadCurriculum(courseId) {
        if (!courseId) { el("curriculumBody").innerHTML = '<div class="text-muted">Select a course.</div>'; return; }
        api("/admin/courses/" + courseId + "/modules").then(function (res) {
            if (!handle(res, false)) return;
            var mods = res.data || [];
            el("curriculumBody").innerHTML = mods.map(function (m, i) {
                return '<div class="curr-module"><div class="head">' +
                    '<span class="badge bg-primary">Module ' + (i + 1) + '</span><strong class="flex-grow-1">' + esc(m.moduleName) + '</strong>' + (m.active ? '' : '<span class="badge bg-secondary">inactive</span>') +
                    '<div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-m="edit" data-id="' + m.id + '" title="Rename / edit"><i class="fas fa-edit"></i></button>' +
                    '<button class="btn btn-outline-primary" data-m="up" data-id="' + m.id + '" title="Move up" ' + (i === 0 ? "disabled" : "") + '><i class="fas fa-arrow-up"></i></button>' +
                    '<button class="btn btn-outline-primary" data-m="down" data-id="' + m.id + '" title="Move down" ' + (i === mods.length - 1 ? "disabled" : "") + '><i class="fas fa-arrow-down"></i></button>' +
                    '<button class="btn btn-outline-' + (m.active ? "warning" : "success") + '" data-m="toggle" data-id="' + m.id + '" title="' + (m.active ? "Deactivate" : "Activate") + '"><i class="fas fa-power-off"></i></button>' +
                    '<button class="btn btn-outline-success" data-m="addlesson" data-id="' + m.id + '" title="Add lesson"><i class="fas fa-plus"></i> Lesson</button>' +
                    '<button class="btn btn-outline-danger" data-m="delete" data-id="' + m.id + '" title="Delete"><i class="fas fa-trash"></i></button>' +
                    '</div></div>' +
                    (m.lessons.map(function (l, li) {
                        return '<div class="curr-lesson"><i class="' + (l.hasVideo ? "fas fa-video text-primary" : "far fa-file-video text-muted") + '"></i>' +
                            '<span class="title">' + esc(l.lessonTitle) + (l.active ? '' : ' <span class="badge bg-secondary">inactive</span>') +
                            '<br><small class="text-muted">' + (l.hasVideo ? "Video ✓" : "No video") + ' · ' + (l.hasMaterial ? "PDF: " + esc(l.materialOriginalName) : "No PDF") + '</small></span>' +
                            '<div class="btn-group btn-group-sm">' +
                            '<button class="btn btn-outline-primary" data-l="edit" data-id="' + l.id + '" title="Edit"><i class="fas fa-edit"></i></button>' +
                            '<button class="btn btn-outline-primary" data-l="video" data-id="' + l.id + '" title="Upload video"><i class="fas fa-video"></i></button>' +
                            '<button class="btn btn-outline-primary" data-l="pdf" data-id="' + l.id + '" title="Upload PDF"><i class="fas fa-file-pdf"></i></button>' +
                            (l.hasVideo ? '<button class="btn btn-outline-secondary" data-l="rmvideo" data-id="' + l.id + '" title="Remove video"><i class="fas fa-video-slash"></i></button>' : "") +
                            (l.hasMaterial ? '<button class="btn btn-outline-secondary" data-l="rmpdf" data-id="' + l.id + '" title="Remove PDF"><i class="fas fa-file-excel"></i></button>' : "") +
                            '<button class="btn btn-outline-primary" data-l="up" data-id="' + l.id + '" data-mod="' + m.id + '" ' + (li === 0 ? "disabled" : "") + '><i class="fas fa-arrow-up"></i></button>' +
                            '<button class="btn btn-outline-primary" data-l="down" data-id="' + l.id + '" data-mod="' + m.id + '" ' + (li === m.lessons.length - 1 ? "disabled" : "") + '><i class="fas fa-arrow-down"></i></button>' +
                            '<button class="btn btn-outline-' + (l.active ? "warning" : "success") + '" data-l="toggle" data-id="' + l.id + '"><i class="fas fa-power-off"></i></button>' +
                            '<button class="btn btn-outline-danger" data-l="delete" data-id="' + l.id + '"><i class="fas fa-trash"></i></button>' +
                            '</div></div>';
                    }).join("") || '<div class="curr-lesson text-muted small">No lessons yet — click "+ Lesson".</div>') +
                    '</div>';
            }).join("") || '<div class="adm-card p-4 text-muted">No modules yet. Click "Add Module".</div>';

            var reload = function () { loadCurriculum(courseId); };
            el("curriculumBody").querySelectorAll("[data-m]").forEach(function (b) {
                var m = mods.find(function (x) { return String(x.id) === b.dataset.id; });
                b.addEventListener("click", function () { moduleAction(b.dataset.m, m, mods, courseId, reload); });
            });
            el("curriculumBody").querySelectorAll("[data-l]").forEach(function (b) {
                var m = mods.find(function (x) { return x.lessons.some(function (l) { return String(l.id) === b.dataset.id; }); });
                var l = m.lessons.find(function (x) { return String(x.id) === b.dataset.id; });
                b.addEventListener("click", function () { lessonAction(b.dataset.l, l, m, reload); });
            });
        });
    }

    function moduleAction(act, m, mods, courseId, reload) {
        if (act === "edit") return openForm("Edit Module", input("moduleName", "Module name", m.moduleName, { required: true, maxlength: 200 }) + input("description", "Description", m.description, { type: "textarea" }), function (v) {
            return api("/admin/modules/" + m.id, { method: "PUT", body: v }).then(function (r) { if (handle(r)) { formModal.hide(); reload(); } else formError(r.message); });
        });
        if (act === "toggle") return api("/admin/modules/" + m.id + "/active", { method: "PATCH", body: { active: !m.active } }).then(function (r) { if (handle(r)) reload(); });
        if (act === "delete") { if (confirmAction("Delete module '" + m.moduleName + "' and its " + m.lessons.length + " lesson(s)?")) api("/admin/modules/" + m.id, { method: "DELETE" }).then(function (r) { if (handle(r)) reload(); }); return; }
        if (act === "addlesson") return openLessonForm(null, m, reload);
        if (act === "up" || act === "down") {
            var ids = mods.map(function (x) { return x.id; });
            var i = ids.indexOf(m.id), j = act === "up" ? i - 1 : i + 1;
            ids[i] = ids[j]; ids[j] = m.id;
            return api("/admin/courses/" + courseId + "/modules/reorder", { method: "PUT", body: { orderedIds: ids } }).then(function (r) { if (handle(r, false)) reload(); });
        }
    }

    function openLessonForm(l, m, reload) {
        var f = input("lessonTitle", "Lesson title", l && l.lessonTitle, { required: true, maxlength: 200 }) +
            input("description", "Description", l && l.description, { type: "textarea", rows: 3 }) +
            input("videoDurationSeconds", "Video duration (seconds, optional)", l && l.videoDurationSeconds, { type: "number" });
        openForm(l ? "Edit Lesson" : "Add Lesson to '" + m.moduleName + "'", f, function (v) {
            var body = { lessonTitle: v.lessonTitle, description: v.description, videoDurationSeconds: v.videoDurationSeconds === "" ? null : Number(v.videoDurationSeconds) };
            return api(l ? "/admin/lessons/" + l.id : "/admin/modules/" + m.id + "/lessons", { method: l ? "PUT" : "POST", body: body }).then(function (r) {
                if (handle(r)) { formModal.hide(); reload(); } else formError(r.message);
            });
        });
    }

    function lessonAction(act, l, m, reload) {
        if (act === "edit") return openLessonForm(l, m, reload);
        if (act === "toggle") return api("/admin/lessons/" + l.id + "/active", { method: "PATCH", body: { active: !l.active } }).then(function (r) { if (handle(r)) reload(); });
        if (act === "delete") { if (confirmAction("Delete lesson '" + l.lessonTitle + "'? Its video, PDF and student progress will be removed.")) api("/admin/lessons/" + l.id, { method: "DELETE" }).then(function (r) { if (handle(r)) reload(); }); return; }
        if (act === "video" || act === "pdf") return openUpload(l, act === "video" ? "video" : "material", reload);
        if (act === "rmvideo") { if (confirmAction("Remove the video from '" + l.lessonTitle + "'? Students will no longer be able to watch it until a new one is uploaded.")) api("/admin/lessons/" + l.id + "/video", { method: "DELETE" }).then(function (r) { if (handle(r)) reload(); }); return; }
        if (act === "rmpdf") { if (confirmAction("Remove the PDF from '" + l.lessonTitle + "'?")) api("/admin/lessons/" + l.id + "/material", { method: "DELETE" }).then(function (r) { if (handle(r)) reload(); }); return; }
        if (act === "up" || act === "down") {
            var ids = m.lessons.map(function (x) { return x.id; });
            var i = ids.indexOf(l.id), j = act === "up" ? i - 1 : i + 1;
            ids[i] = ids[j]; ids[j] = l.id;
            return api("/admin/modules/" + m.id + "/lessons/reorder", { method: "PUT", body: { orderedIds: ids } }).then(function (r) { if (handle(r, false)) reload(); });
        }
    }

    function openUpload(l, kind, reload) {
        var isVideo = kind === "video";
        openForm((isVideo ? "Upload Video" : "Upload PDF") + " — " + l.lessonTitle,
            '<div class="mb-3"><label class="form-label small fw-bold">' + (isVideo ? "Video file (MP4/WebM)" : "PDF file") + '</label><input type="file" class="form-control" name="file" accept="' + (isVideo ? "video/mp4,video/webm,video/quicktime" : "application/pdf") + '" required></div>' +
            '<div class="progress d-none" id="uploadProgress" style="height:8px;"><div class="progress-bar" style="width:0%"></div></div>' +
            (isVideo ? '<div class="form-text">Large videos may take a few minutes. Keep this window open.</div>' : ""),
            function (v) {
                if (!v.file) { formError("Choose a file."); return Promise.resolve(); }
                var fd = new FormData(); fd.append("file", v.file);
                el("uploadProgress").classList.remove("d-none");
                return uploadWithProgress("/admin/lessons/" + l.id + "/" + kind, fd, function (pct) { el("uploadProgress").firstElementChild.style.width = pct + "%"; })
                    .then(function (r) { if (handle(r)) { formModal.hide(); reload(); } else formError(r.message); });
            }, { submitLabel: "Upload" });
    }

    function uploadWithProgress(path, formData, onProgress) {
        return new Promise(function (resolve) {
            var xhr = new XMLHttpRequest();
            var session = LSI_Auth.getSession();
            xhr.open("POST", LSI_Auth.apiBase + path);
            xhr.setRequestHeader("Authorization", "Bearer " + (session ? session.token : ""));
            xhr.upload.onprogress = function (e) { if (e.lengthComputable) onProgress(Math.round(e.loaded / e.total * 100)); };
            xhr.onload = function () { try { resolve(JSON.parse(xhr.responseText)); } catch (e) { resolve({ success: false, message: "Upload failed (" + xhr.status + ")" }); } };
            xhr.onerror = function () { resolve({ success: false, message: "Upload failed. Check your connection." }); };
            xhr.send(formData);
        });
    }

    // ---- enrollments -----------------------------------------------------------------------

    function enrollmentActions(e) {
        var opts = ["ACTIVE", "INACTIVE", "COMPLETED", "CANCELLED"].filter(function (s) { return s !== e.status; });
        return '<div class="btn-group btn-group-sm">' + opts.map(function (s) {
            var cls = s === "ACTIVE" ? "success" : s === "CANCELLED" ? "danger" : "secondary";
            return '<button class="btn btn-outline-' + cls + ' btn-xs" data-enr="' + e.id + '" data-status="' + s + '">' + s.charAt(0) + s.slice(1).toLowerCase() + '</button>';
        }).join("") + '</div>';
    }
    function bindEnrollmentActions(root, after) {
        root.querySelectorAll("[data-enr]").forEach(function (b) {
            b.addEventListener("click", function () {
                if (!confirmAction("Set this enrollment to " + b.dataset.status + "?")) return;
                api("/admin/enrollments/" + b.dataset.enr + "/status", { method: "PATCH", body: { status: b.dataset.status } }).then(function (r) { if (handle(r)) after(); });
            });
        });
    }

    function loadEnrollments(page) {
        state.enrollPage = page;
        var q = "/admin/enrollments?page=" + page + "&size=20";
        var c = el("enrollCourseFilter").value;
        if (c) q += "&courseId=" + c;
        if (!el("enrollCourseFilter").options.length || el("enrollCourseFilter").options.length === 1) {
            loadCourses().then(function (courses) {
                el("enrollCourseFilter").innerHTML = '<option value="">All courses</option>' + courses.map(function (x) { return '<option value="' + x.id + '">' + esc(x.courseName) + '</option>'; }).join("");
            });
        }
        api(q).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("enrollmentsBody").innerHTML = (p.content || []).map(function (e) {
                return '<tr><td><a href="#" data-student="' + e.studentUserId + '">' + esc(e.studentName) + '</a><br><small class="text-muted font-monospace">' + esc(e.studentId || "") + '</small></td><td>' + esc(e.courseName) + '</td><td>' + badge(e.status) + '</td><td>' + badge(e.source) + (e.orderRef ? '<br><small class="text-muted font-monospace">' + esc(e.orderRef) + '</small>' : '') + '</td>' +
                    '<td><div class="progress" style="height:6px;width:90px"><div class="progress-bar bg-success" style="width:' + e.progressPercent + '%"></div></div><small>' + e.progressPercent + '% (' + e.completedLessons + '/' + e.totalLessons + ')</small></td>' +
                    '<td><small>' + d(e.enrolledAt) + '</small></td><td><small>' + d(e.expiryDate) + '</small></td><td>' + enrollmentActions(e) + '</td></tr>';
            }).join("") || '<tr><td colspan="8" class="text-muted text-center py-4">No enrollments.</td></tr>';
            el("enrollmentsCount").innerText = p.totalElements + " enrollment(s)";
            pager("enrollmentsPager", p, loadEnrollments);
            bindStudentLinks(el("enrollmentsBody"));
            bindEnrollmentActions(el("enrollmentsBody"), function () { loadEnrollments(state.enrollPage); });
        });
    }

    function openManualEnroll(student) {
        loadCourses().then(function (courses) {
            var f = (student ? '<div class="mb-3"><label class="form-label small fw-bold">Student</label><input class="form-control" value="' + esc(student.fullName + " (" + student.email + ")") + '" disabled><input type="hidden" name="studentUserId" value="' + student.id + '"></div>'
                    : input("studentUserId", "Student (search by name/email/ID)", "", { required: true, placeholder: "Type to search..." }) + '<div id="manualEnrollHits" class="list-group mb-3"></div>') +
                input("courseId", "Course", courses[0] && courses[0].id, { type: "select", required: true, options: courses.map(function (c) { return { value: c.id, label: c.courseName + " (" + c.courseCode + ")" }; }) }) +
                input("expiryDate", "Access expiry date (optional)", "", { type: "date" }) +
                '<div class="alert alert-warning small py-2">Manual enrollments are marked <strong>ADMIN MANUAL</strong> and recorded in the audit log. No payment record is created.</div>';
            openForm("Enroll Student Manually", f, function (v) {
                var body = { studentUserId: Number(v.studentUserId), courseId: Number(v.courseId), expiryDate: v.expiryDate || null };
                if (!body.studentUserId) { formError("Pick a student from the search results."); return Promise.resolve(); }
                return api("/admin/enrollments", { method: "POST", body: body }).then(function (r) { if (handle(r)) { formModal.hide(); student ? openStudent(student.id) : loadEnrollments(0); } else formError(r.message); });
            }, { submitLabel: "Enroll" });
            if (!student) {
                var inp = el("formModalForm").querySelector('[name="studentUserId"]');
                inp.type = "text";
                inp.addEventListener("input", function () {
                    var q = inp.value.trim();
                    if (q.length < 2) return;
                    api("/admin/search?q=" + encodeURIComponent(q)).then(function (r) {
                        var hits = (r.data && r.data.students) || [];
                        el("manualEnrollHits").innerHTML = hits.map(function (h) { return '<button type="button" class="list-group-item list-group-item-action py-1 small" data-pick="' + h.id + '" data-name="' + esc(h.fullName + " (" + h.email + ")") + '">' + esc(h.fullName) + ' · ' + esc(h.email) + ' · ' + esc(h.studentId || "") + '</button>'; }).join("");
                        el("manualEnrollHits").querySelectorAll("[data-pick]").forEach(function (b) {
                            b.addEventListener("click", function () {
                                inp.value = b.dataset.name; inp.disabled = true;
                                var hid = document.createElement("input"); hid.type = "hidden"; hid.name = "studentUserId"; hid.value = b.dataset.pick;
                                inp.name = "studentLabel"; inp.parentElement.appendChild(hid);
                                el("manualEnrollHits").innerHTML = "";
                            });
                        });
                    });
                });
            }
        });
    }

    // ---- payments --------------------------------------------------------------------------

    function loadPayments(page) {
        state.paymentPage = page;
        var q = "/admin/payments?page=" + page + "&size=25" + (state.paymentStatus ? "&status=" + state.paymentStatus : "");
        api(q).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("paymentsBody").innerHTML = (p.content || []).map(function (x) {
                return '<tr><td class="font-monospace small">' + esc(x.orderRef) + '</td><td>' + (x.userId ? '<a href="#" data-student="' + x.userId + '">' + esc(x.customerName) + '</a>' : esc(x.customerName)) + '<br><small class="text-muted font-monospace">' + esc(x.studentId || "") + '</small></td><td><small>' + esc(x.customerEmail) + '</small></td><td>' + esc(x.productName || x.courseName) + '</td><td>' + badge(x.productType || "COURSE") + '</td><td class="font-monospace"><small>' + (x.invoiceId ? '<a href="#" data-invoice-pdf="' + x.invoiceId + '">' + esc(x.invoiceNumber) + '</a>' : '—') + '</small></td><td><strong>' + inr(x.amount) + '</strong></td><td>' + badge(x.paymentMode || "RAZORPAY") + '</td><td>' + esc(x.paymentMethod || "—") + '</td>' +
                    '<td class="font-monospace"><small>' + esc(x.razorpayOrderId) + '</small></td><td class="font-monospace"><small>' + esc(x.razorpayPaymentId || "—") + '</small></td><td>' + badge(x.status) + (x.failureReason ? '<br><small class="text-danger">' + esc(x.failureReason) + '</small>' : '') + '</td><td><small>' + dt(x.createdAt) + '</small></td></tr>';
            }).join("") || '<tr><td colspan="13" class="text-muted text-center py-4">No payments.</td></tr>';
            el("paymentsCount").innerText = p.totalElements + " payment(s)";
            pager("paymentsPager", p, loadPayments);
            bindStudentLinks(el("paymentsBody"));
            bindInvoiceLinks(el("paymentsBody"));
        });
    }

    // ---- reviews / testimonials --------------------------------------------------------------

    var reviewCache = [], reviewModal, currentReview;

    function starIcons(n) {
        var out = "";
        for (var i = 1; i <= 5; i++) out += '<i class="' + (i <= n ? "fas text-warning" : "far text-muted") + ' fa-star"></i>';
        return '<span title="' + n + ' / 5" class="text-nowrap">' + out + '</span>';
    }

    function loadReviews(page) {
        state.reviewPage = page || 0;
        var status = el("reviewStatusFilter").value, site = el("reviewSiteFilter").value;
        api("/admin/reviews/counts").then(function (r) {
            if (!r.success) return;
            var c = r.data;
            updatePendingReviews(c.pending);
            el("reviewCountCards").innerHTML = [["Pending", c.pending, "fa-star-half-alt", "#f59e0b", "PENDING"], ["Approved", c.approved, "fa-star", "#10b981", "APPROVED"], ["Declined", c.declined, "fa-ban", "#ef4444", "DECLINED"]].map(function (x) {
                return '<div class="col-md-4"><a href="#" class="text-decoration-none" data-rstatus="' + x[4] + '"><div class="adm-card stat-card"><div class="ico" style="background:' + x[3] + '22;color:' + x[3] + '"><i class="fas ' + x[2] + '"></i></div><div><div class="val">' + x[1] + '</div><div class="lbl">' + x[0] + ' Reviews</div></div></div></a></div>';
            }).join("");
            el("reviewCountCards").querySelectorAll("[data-rstatus]").forEach(function (a) {
                a.addEventListener("click", function (e) { e.preventDefault(); el("reviewStatusFilter").value = a.dataset.rstatus; loadReviews(0); });
            });
        });
        api("/admin/reviews?page=" + state.reviewPage + "&size=20" + (status ? "&status=" + status : "") + (site ? "&site=" + site : "")).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data; reviewCache = p.content || [];
            el("reviewsBody").innerHTML = reviewCache.map(function (r) {
                var preview = r.reviewText.length > 90 ? r.reviewText.slice(0, 90) + "…" : r.reviewText;
                return '<tr class="' + (r.status === "PENDING" ? "table-warning" : "") + '">' +
                    '<td><div class="d-flex align-items-center gap-2">' + (r.profileImagePath ? '<img src="' + esc(mediaUrl(r.profileImagePath)) + '" alt="" class="rounded-circle" style="width:36px;height:36px;object-fit:cover;flex-shrink:0">' : '<span class="rounded-circle bg-light text-muted d-inline-flex align-items-center justify-content-center" style="width:36px;height:36px;flex-shrink:0"><i class="fas fa-user"></i></span>') +
                    '<div><strong>' + esc(r.fullName) + '</strong><br><small class="text-muted">' + esc(r.email || "Added by admin") + '</small></div></div></td>' +
                    '<td>' + siteBadge(r.site) + '</td>' +
                    '<td>' + starIcons(r.rating) + '</td><td><small>' + esc(r.course || "—") + '</small></td>' +
                    '<td style="max-width:320px"><small class="text-muted">' + esc(preview) + '</small></td>' +
                    '<td>' + badge(r.status) + '</td>' +
                    '<td><small>' + dt(r.createdAt) + '</small></td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-r="view" data-id="' + r.id + '" title="View details"><i class="fas fa-eye"></i></button>' +
                    '<button class="btn btn-outline-primary" data-r="edit" data-id="' + r.id + '" title="Edit testimonial"><i class="fas fa-edit"></i></button>' +
                    (r.status !== "APPROVED" ? '<button class="btn btn-outline-success" data-r="approve" data-id="' + r.id + '" title="Accept review"><i class="fas fa-check"></i></button>' : "") +
                    (r.status !== "DECLINED" ? '<button class="btn btn-outline-warning" data-r="decline" data-id="' + r.id + '" title="Decline review"><i class="fas fa-times"></i></button>' : "") +
                    '<button class="btn btn-outline-danger" data-r="delete" data-id="' + r.id + '" title="Delete testimonial"><i class="fas fa-trash"></i></button>' +
                    '</div></td></tr>';
            }).join("") || '<tr><td colspan="8" class="text-center text-muted py-5"><i class="far fa-comment-dots fa-2x mb-2 d-block opacity-50"></i>' + (status === "PENDING" ? "No reviews waiting for approval. Nice work!" : "No reviews found for this filter.") + '</td></tr>';
            el("reviewsCount").innerText = p.totalElements + " review(s)";
            pager("reviewsPager", p, loadReviews);
            el("reviewsBody").querySelectorAll("[data-r]").forEach(function (b) {
                var r = reviewCache.find(function (x) { return String(x.id) === b.dataset.id; });
                b.addEventListener("click", function () { reviewAction(b.dataset.r, r); });
            });
        });
    }

    function reviewAction(act, r) {
        if (act === "view") return openReview(r.id);
        if (act === "approve") return decideReview(r, true);
        if (act === "decline") return decideReview(r, false);
        if (act === "edit") return openReviewForm(r);
        if (act === "delete") return deleteReview(r);
    }

    function deleteReview(r) {
        if (!confirmAction("Are you sure you want to delete this testimonial by " + r.fullName + "? This cannot be undone.")) return Promise.resolve();
        return api("/admin/reviews/" + r.id, { method: "DELETE" }).then(function (res) {
            if (handle(res)) { if (reviewModal) reviewModal.hide(); loadReviews(state.reviewPage); }
        });
    }

    /** Add (r == null) or edit an existing testimonial. Uses the shared form modal and admin API. */
    function openReviewForm(r) {
        var f = input("site", "Website", r ? (r.site === "MUTUAL_FUND" ? "MUTUAL_FUND" : "SHARE_MARKET") : "SHARE_MARKET", { type: "select", options: [{ value: "SHARE_MARKET", label: "Share Market Academy" }, { value: "MUTUAL_FUND", label: "Mutual Fund" }] }) +
            input("fullName", "Name *", r && r.fullName, { required: true, maxlength: 150, placeholder: "e.g. Rahul Sharma or Job Professional — Uran" }) +
            input("course", "Course / program (optional)", r && r.course, { maxlength: 150, placeholder: "e.g. Swing Trading & Risk Management" }) +
            input("rating", "Rating *", r ? r.rating : 5, { type: "select", options: [5, 4, 3, 2, 1].map(function (n) { return { value: n, label: n + " star" + (n === 1 ? "" : "s") }; }) }) +
            input("reviewText", "Testimonial *", r && r.reviewText, { type: "textarea", rows: 5, required: true, help: "20 to 1000 characters." }) +
            input("email", "Email (optional)", r && r.email, { type: "email", maxlength: 190, help: "Never shown publicly. Used only to notify a visitor when their review is approved." }) +
            input("status", "Visibility", r ? r.status : "APPROVED", { type: "select", options: [{ value: "APPROVED", label: "Approved — visible on the website" }, { value: "PENDING", label: "Pending — hidden" }, { value: "DECLINED", label: "Declined — hidden" }] }) +
            (r ? "" : '<div class="mb-2"><label class="form-label small fw-bold">Photo (optional, JPG / PNG / WebP up to 5 MB)</label><input type="file" class="form-control" name="photo" accept="image/jpeg,image/png,image/webp"></div>');
        openForm(r ? "Edit Testimonial" : "Add Testimonial", f, function (v) {
            var body = { site: v.site, fullName: v.fullName, course: v.course, rating: Number(v.rating), reviewText: v.reviewText, email: v.email, status: v.status };
            return api(r ? "/admin/reviews/" + r.id : "/admin/reviews", { method: r ? "PUT" : "POST", body: body }).then(function (res) {
                if (!res.success) { formError(res.errors ? Object.values(res.errors).join(" ") : res.message); return; }
                var saved = res.data;
                var next = (!r && v.photo) ? uploadReviewPhoto(saved.id, v.photo) : Promise.resolve({ success: true });
                return next.then(function (pr) {
                    if (pr && !pr.success) toast("Testimonial saved, but the photo could not be uploaded: " + pr.message, false);
                    else toast(res.message);
                    formModal.hide(); loadReviews(state.reviewPage);
                });
            });
        }, { size: "modal-lg", submitLabel: r ? "Save Changes" : "Add Testimonial" });
    }

    function uploadReviewPhoto(id, file) {
        var fd = new FormData(); fd.append("file", file);
        return api("/admin/reviews/" + id + "/photo", { method: "POST", body: fd });
    }

    function openReviewPhoto(r) {
        openForm("Photo — " + r.fullName,
            (r.profileImagePath ? '<div class="mb-3 text-center"><img src="' + esc(mediaUrl(r.profileImagePath)) + '" alt="" class="rounded-circle border" style="width:96px;height:96px;object-fit:cover"><div class="form-text">Current photo</div></div>' : "") +
            '<div class="mb-3"><label class="form-label small fw-bold">' + (r.profileImagePath ? "Replace with" : "Upload") + ' image (JPG / PNG / WebP up to 5 MB)</label><input type="file" class="form-control" name="photo" accept="image/jpeg,image/png,image/webp"></div>' +
            (r.profileImagePath ? '<div class="form-check"><input class="form-check-input" type="checkbox" name="removePhoto" id="removePhoto"><label class="form-check-label small" for="removePhoto">Remove the current photo</label></div>' : ""),
            function (v) {
                if (v.removePhoto) {
                    return api("/admin/reviews/" + r.id + "/photo", { method: "DELETE" }).then(function (res) { if (handle(res)) { formModal.hide(); loadReviews(state.reviewPage); if (currentReview && currentReview.id === r.id) openReview(r.id); } else formError(res.message); });
                }
                if (!v.photo) { formError("Choose an image, or tick 'Remove the current photo'."); return Promise.resolve(); }
                return uploadReviewPhoto(r.id, v.photo).then(function (res) { if (handle(res)) { formModal.hide(); loadReviews(state.reviewPage); if (currentReview && currentReview.id === r.id) openReview(r.id); } else formError(res.message); });
            }, { submitLabel: "Save" });
    }

    function decideReview(r, accept) {
        var msg = accept
            ? "Are you sure you want to accept this review by " + r.fullName + "? It will become visible on the public website immediately."
            : "Are you sure you want to decline this review by " + r.fullName + "? It will be hidden from the website but kept here for your records.";
        if (!confirmAction(msg)) return Promise.resolve();
        return api("/admin/reviews/" + r.id + "/" + (accept ? "approve" : "decline"), { method: "PATCH" }).then(function (res) {
            if (handle(res)) { if (reviewModal) reviewModal.hide(); loadReviews(state.reviewPage); }
        });
    }

    function openReview(id) {
        if (!reviewModal) reviewModal = new bootstrap.Modal(el("reviewModal"));
        el("reviewModalBody").innerHTML = '<div class="text-muted">Loading…</div>';
        reviewModal.show();
        api("/admin/reviews/" + id).then(function (res) {
            if (!handle(res, false)) { reviewModal.hide(); return; }
            var r = currentReview = res.data;
            el("reviewModalBody").innerHTML =
                '<div class="d-flex flex-wrap align-items-center gap-3 mb-3">' +
                (r.profileImagePath ? '<img src="' + esc(mediaUrl(r.profileImagePath)) + '" alt="" class="rounded-circle border" style="width:72px;height:72px;object-fit:cover">' : '<span class="rounded-circle bg-light text-muted d-inline-flex align-items-center justify-content-center" style="width:72px;height:72px;font-size:28px"><i class="fas fa-user"></i></span>') +
                '<div class="flex-grow-1"><h5 class="fw-bold mb-0">' + esc(r.fullName) + ' ' + badge(r.status) + '</h5>' +
                '<div class="text-muted small">' + esc(r.email || "Added by admin — no email") + (r.course ? ' · ' + esc(r.course) : "") + '</div>' +
                '<div class="mt-1">' + starIcons(r.rating) + ' <span class="small text-muted ms-1">' + r.rating + ' / 5</span></div></div></div>' +
                '<div class="p-3 rounded-3 bg-light mb-3" style="white-space:pre-wrap;line-height:1.7">' + esc(r.reviewText) + '</div>' +
                '<div class="row g-2 small text-muted">' +
                '<div class="col-sm-6">Website: <strong class="text-dark">' + siteName(r.site) + '</strong></div>' +
                '<div class="col-sm-6">Submitted: <strong class="text-dark">' + dt(r.createdAt) + '</strong>' + (r.submittedIp ? ' <span class="font-monospace">(' + esc(r.submittedIp) + ')</span>' : "") + '</div>' +
                (r.approvedAt ? '<div class="col-sm-6">Approved: <strong class="text-dark">' + dt(r.approvedAt) + '</strong>' + (r.approvedBy ? " by " + esc(r.approvedBy) : "") + '</div>' : "") +
                (r.decidedAt ? '<div class="col-sm-6">Last decision: <strong class="text-dark">' + dt(r.decidedAt) + '</strong>' + (r.decidedBy ? " by " + esc(r.decidedBy) : "") + '</div>' : "") +
                '</div>';
            el("reviewAcceptBtn").classList.toggle("d-none", r.status === "APPROVED");
            el("reviewDeclineBtn").classList.toggle("d-none", r.status === "DECLINED");
        });
    }

    function bindReviewModal() {
        el("reviewEditBtn").addEventListener("click", function () { if (currentReview) { reviewModal.hide(); openReviewForm(currentReview); } });
        el("reviewPhotoBtn").addEventListener("click", function () { if (currentReview) { reviewModal.hide(); openReviewPhoto(currentReview); } });
        el("reviewAcceptBtn").addEventListener("click", function () { if (currentReview) decideReview(currentReview, true); });
        el("reviewDeclineBtn").addEventListener("click", function () { if (currentReview) decideReview(currentReview, false); });
        el("reviewDeleteBtn").addEventListener("click", function () {
            if (currentReview) deleteReview(currentReview);
        });
        el("reviewStatusFilter").addEventListener("change", function () { loadReviews(0); });
        el("reviewSiteFilter").addEventListener("change", function () { loadReviews(0); });
    }

    // ---- success stories -------------------------------------------------------------------

    var storyCache = [];
    var STORY_CATEGORIES = [{ value: "STUDENTS", label: "Students" }, { value: "TEACHERS", label: "Teachers" }, { value: "PARENTS", label: "Parents" }];

    function loadStories() {
        api("/admin/stories").then(function (res) {
            if (!handle(res, false)) return;
            storyCache = res.data || [];
            var filter = el("storyCategoryFilter").value;
            var list = filter ? storyCache.filter(function (x) { return x.category === filter; }) : storyCache;
            el("storiesBody").innerHTML = list.map(function (x, i) {
                var video = x.hasUploadedVideo ? '<span class="badge bg-success"><i class="fas fa-file-video me-1"></i>Uploaded</span><br><small class="text-muted">' + esc(x.videoOriginalName || "") + '</small>'
                    : x.videoUrl ? '<span class="badge bg-info text-dark"><i class="fab fa-youtube me-1"></i>Embed link</span>'
                    : '<span class="badge bg-secondary">No video</span>';
                return '<tr><td class="text-muted">' + (i + 1) + '</td>' +
                    '<td><div class="d-flex align-items-center gap-2">' + (x.thumbnailPath ? '<img src="' + esc(mediaUrl(x.thumbnailPath)) + '" alt="" style="width:64px;height:40px;object-fit:cover;border-radius:6px;flex-shrink:0">' : '<span class="d-inline-flex align-items-center justify-content-center bg-light text-muted" style="width:64px;height:40px;border-radius:6px;flex-shrink:0"><i class="fas fa-image"></i></span>') +
                    '<div><strong>' + esc(x.title) + '</strong><br><small class="text-muted">' + esc(x.personName || "") + (x.location ? " · " + esc(x.location) : "") + '</small></div></div></td>' +
                    '<td>' + badge(x.category) + '</td><td>' + video + '</td><td>' + badge(x.published ? "PUBLISHED" : "UNPUBLISHED") + '</td>' +
                    '<td><small>' + dt(x.updatedAt) + (x.updatedBy ? '<br><span class="text-muted">' + esc(x.updatedBy) + '</span>' : "") + '</small></td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-s="edit" data-id="' + x.id + '" title="Edit"><i class="fas fa-edit"></i></button>' +
                    '<button class="btn btn-outline-primary" data-s="video" data-id="' + x.id + '" title="' + (x.hasUploadedVideo ? "Replace video" : "Upload video") + '"><i class="fas fa-video"></i></button>' +
                    '<button class="btn btn-outline-primary" data-s="thumb" data-id="' + x.id + '" title="Upload thumbnail"><i class="fas fa-image"></i></button>' +
                    (x.hasUploadedVideo ? '<button class="btn btn-outline-secondary" data-s="rmvideo" data-id="' + x.id + '" title="Remove video"><i class="fas fa-video-slash"></i></button>' : "") +
                    '<button class="btn btn-outline-primary" data-s="up" data-id="' + x.id + '" title="Move up" ' + (i === 0 ? "disabled" : "") + '><i class="fas fa-arrow-up"></i></button>' +
                    '<button class="btn btn-outline-primary" data-s="down" data-id="' + x.id + '" title="Move down" ' + (i === list.length - 1 ? "disabled" : "") + '><i class="fas fa-arrow-down"></i></button>' +
                    '<button class="btn btn-outline-' + (x.published ? "warning" : "success") + '" data-s="publish" data-id="' + x.id + '" title="' + (x.published ? "Unpublish" : "Publish") + '"><i class="fas ' + (x.published ? "fa-eye-slash" : "fa-eye") + '"></i></button>' +
                    '<button class="btn btn-outline-danger" data-s="delete" data-id="' + x.id + '" title="Delete"><i class="fas fa-trash"></i></button>' +
                    '</div></td></tr>';
            }).join("") || '<tr><td colspan="7" class="text-muted text-center py-4">No stories yet. Click "Add Story".</td></tr>';
            el("storiesBody").querySelectorAll("[data-s]").forEach(function (b) {
                var x = storyCache.find(function (y) { return String(y.id) === b.dataset.id; });
                b.addEventListener("click", function () { storyAction(b.dataset.s, x, list); });
            });
        });
    }

    function openStoryForm(x) {
        var f = input("category", "Category *", x ? x.category : "", { type: "select", required: !x, options: [{ value: "", label: "Select category" }].concat(STORY_CATEGORIES) }) +
            input("title", "Video name *", x && x.title, { required: true, maxlength: 200, placeholder: "e.g. Rahul Sharma — From beginner to consistent swing trader" }) +
            input("personName", "Person name", x && x.personName, { maxlength: 150 }) +
            input("location", "Location / batch", x && x.location, { maxlength: 150, placeholder: "e.g. From Pune, Maharashtra" }) +
            input("description", "Description", x && x.description, { type: "textarea", rows: 4 }) +
            (x ? input("videoUrl", "YouTube embed link (optional)", x.videoUrl, { maxlength: 500, placeholder: "https://www.youtube.com/embed/VIDEO_ID" }) :
                '<div class="mb-3"><label class="form-label small fw-bold">Cover image *</label><input type="file" class="form-control" name="coverImage" accept="image/jpeg,image/png,image/webp" required></div>' +
                '<div class="mb-3"><label class="form-label small fw-bold">Video file *</label><input type="file" class="form-control" name="video" accept="video/mp4,video/webm,video/quicktime" required><div class="form-text">MP4, WebM or MOV, within the existing 512 MB upload limit.</div></div>');
        openForm(x ? "Edit Story" : "Add Success Story", f, function (v) {
            if (!v.title || !v.title.trim()) { formError("Video name is required."); return Promise.resolve(); }
            if (!v.category) { formError("Please select a category."); return Promise.resolve(); }
            if (!x && !v.coverImage) { formError("Cover image is required."); return Promise.resolve(); }
            if (!x && !v.video) { formError("Video file is required."); return Promise.resolve(); }
            if (!x) {
                var fd = new FormData();
                fd.append("category", v.category); fd.append("title", v.title.trim());
                if (v.personName) fd.append("personName", v.personName);
                if (v.location) fd.append("location", v.location);
                if (v.description) fd.append("description", v.description);
                fd.append("coverImage", v.coverImage); fd.append("video", v.video);
                return api("/admin/stories", { method: "POST", body: fd }).then(function (r) {
                    if (handle(r)) { formModal.hide(); loadStories(); } else formError(r.errors ? Object.values(r.errors).join(" ") : r.message);
                });
            }
            var body = { category: v.category, title: v.title, personName: v.personName, location: v.location, description: v.description, videoUrl: v.videoUrl };
            return api(x ? "/admin/stories/" + x.id : "/admin/stories", { method: x ? "PUT" : "POST", body: body }).then(function (r) {
                if (handle(r)) { formModal.hide(); loadStories(); } else formError(r.errors ? Object.values(r.errors).join(" ") : r.message);
            });
        });
    }

    function storyAction(act, x, list) {
        if (act === "edit") return openStoryForm(x);
        if (act === "publish") {
            if (!x.published && !x.hasUploadedVideo && !x.videoUrl) { toast("Upload a video or add a video link before publishing.", false); return; }
            if (!confirmAction((x.published ? "Unpublish" : "Publish") + " '" + x.title + "'? " + (x.published ? "It will disappear from the website immediately." : "It will appear on the public Success Stories page."))) return;
            return api("/admin/stories/" + x.id + "/publish", { method: "PATCH", body: { published: !x.published } }).then(function (r) { if (handle(r)) loadStories(); });
        }
        if (act === "delete") { if (confirmAction("Delete '" + x.title + "'? Its video and thumbnail will be removed. This cannot be undone.")) api("/admin/stories/" + x.id, { method: "DELETE" }).then(function (r) { if (handle(r)) loadStories(); }); return; }
        if (act === "rmvideo") { if (confirmAction("Remove the uploaded video from '" + x.title + "'?" + (x.published ? " The story will be unpublished." : ""))) api("/admin/stories/" + x.id + "/video", { method: "DELETE" }).then(function (r) { if (handle(r)) loadStories(); }); return; }
        if (act === "video") return openForm((x.hasUploadedVideo ? "Replace Video" : "Upload Video") + " — " + x.title,
            '<div class="mb-3"><label class="form-label small fw-bold">Video file (MP4 / WebM)</label><input type="file" class="form-control" name="file" accept="video/mp4,video/webm,video/quicktime" required></div>' +
            '<div class="progress d-none" id="uploadProgress" style="height:8px;"><div class="progress-bar" style="width:0%"></div></div>' +
            '<div class="form-text">Large videos may take a few minutes. Keep this window open.</div>',
            function (v) {
                if (!v.file) { formError("Choose a file."); return Promise.resolve(); }
                var fd = new FormData(); fd.append("file", v.file);
                el("uploadProgress").classList.remove("d-none");
                return uploadWithProgress("/admin/stories/" + x.id + "/video", fd, function (pct) { el("uploadProgress").firstElementChild.style.width = pct + "%"; })
                    .then(function (r) { if (handle(r)) { formModal.hide(); loadStories(); } else formError(r.message); });
            }, { submitLabel: "Upload" });
        if (act === "thumb") return openForm("Upload Thumbnail — " + x.title,
            '<div class="mb-3"><label class="form-label small fw-bold">Image (JPG / PNG / WebP, max 5 MB)</label><input type="file" class="form-control" name="file" accept="image/jpeg,image/png,image/webp" required></div>',
            function (v) {
                if (!v.file) { formError("Choose an image."); return Promise.resolve(); }
                var fd = new FormData(); fd.append("file", v.file);
                return api("/admin/stories/" + x.id + "/thumbnail", { method: "POST", body: fd }).then(function (r) { if (handle(r)) { formModal.hide(); loadStories(); } else formError(r.message); });
            }, { submitLabel: "Upload" });
        if (act === "up" || act === "down") {
            var ids = storyCache.map(function (y) { return y.id; });
            var i = ids.indexOf(x.id), j = act === "up" ? i - 1 : i + 1;
            if (j < 0 || j >= ids.length) return;
            ids[i] = ids[j]; ids[j] = x.id;
            return api("/admin/stories/reorder", { method: "PUT", body: { orderedIds: ids } }).then(function (r) { if (handle(r, false)) loadStories(); });
        }
    }

    // ---- Mutual Fund homepage slider --------------------------------------------------------

    var sliderCache = [];

    function loadSlider() {
        api("/admin/mutual-fund/slider").then(function (res) {
            if (!handle(res, false)) return;
            sliderCache = res.data || [];
            el("sliderBody").innerHTML = sliderCache.map(function (x, i) {
                return '<tr class="' + (x.active ? "" : "opacity-50") + '"><td class="text-muted">' + (i + 1) + '</td>' +
                    '<td><img src="' + esc(mediaUrl(x.imagePath)) + '" alt="" style="width:96px;height:54px;object-fit:cover;border-radius:6px;"></td>' +
                    '<td><strong>' + esc(x.title || "—") + '</strong>' + (x.altText ? '<br><small class="text-muted">' + esc(x.altText) + '</small>' : "") + '</td>' +
                    '<td>' + x.displayOrder + '</td>' +
                    '<td>' + badge(x.active ? "ACTIVE" : "INACTIVE") + '</td>' +
                    '<td><small>' + dt(x.updatedAt) + (x.updatedBy ? '<br><span class="text-muted">' + esc(x.updatedBy) + '</span>' : "") + '</small></td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-si="edit" data-id="' + x.id + '" title="Edit details"><i class="fas fa-edit"></i></button>' +
                    '<button class="btn btn-outline-primary" data-si="replace" data-id="' + x.id + '" title="Replace image"><i class="fas fa-image"></i></button>' +
                    '<button class="btn btn-outline-primary" data-si="up" data-id="' + x.id + '" title="Move up" ' + (i === 0 ? "disabled" : "") + '><i class="fas fa-arrow-up"></i></button>' +
                    '<button class="btn btn-outline-primary" data-si="down" data-id="' + x.id + '" title="Move down" ' + (i === sliderCache.length - 1 ? "disabled" : "") + '><i class="fas fa-arrow-down"></i></button>' +
                    '<button class="btn btn-outline-' + (x.active ? "warning" : "success") + '" data-si="toggle" data-id="' + x.id + '" title="' + (x.active ? "Deactivate" : "Activate") + '"><i class="fas fa-power-off"></i></button>' +
                    '<button class="btn btn-outline-danger" data-si="delete" data-id="' + x.id + '" title="Delete"><i class="fas fa-trash"></i></button>' +
                    '</div></td></tr>';
            }).join("") || '<tr><td colspan="7" class="text-muted text-center py-4">No slider images yet. Click "Add Slider Image".</td></tr>';
            el("sliderBody").querySelectorAll("[data-si]").forEach(function (b) {
                var x = sliderCache.find(function (y) { return String(y.id) === b.dataset.id; });
                b.addEventListener("click", function () { sliderAction(b.dataset.si, x); });
            });
        });
    }

    function sliderMetaFields(x) {
        return input("title", "Title (optional)", x && x.title, { maxlength: 200 }) +
            input("subtitle", "Subtitle (optional)", x && x.subtitle, { maxlength: 300 }) +
            input("buttonText", "Button text (optional)", x && x.buttonText, { maxlength: 100 }) +
            input("buttonLink", "Button link (optional)", x && x.buttonLink, { maxlength: 500, placeholder: "https://..." }) +
            input("altText", "Alt text (accessibility, optional)", x && x.altText, { maxlength: 255, help: "Describes the image for screen readers. Falls back to the title if left blank." });
    }

    function openSliderForm() {
        var f = '<div class="mb-3"><label class="form-label small fw-bold">Image *</label><input type="file" class="form-control" name="file" accept="image/jpeg,image/png,image/webp" required>' +
            '<div class="form-text">JPG, PNG or WebP. The new slide is added as the last, active slide — reorder or deactivate it afterward if needed.</div></div>' +
            sliderMetaFields(null);
        openForm("Add Slider Image", f, function (v) {
            if (!v.file) { formError("Choose an image."); return Promise.resolve(); }
            var fd = new FormData();
            fd.append("file", v.file);
            ["title", "subtitle", "buttonText", "buttonLink", "altText"].forEach(function (k) { if (v[k]) fd.append(k, v[k]); });
            return api("/admin/mutual-fund/slider", { method: "POST", body: fd }).then(function (r) {
                if (handle(r)) { formModal.hide(); loadSlider(); } else formError(r.errors ? Object.values(r.errors).join(" ") : r.message);
            });
        }, { submitLabel: "Add Slide" });
    }

    function sliderAction(act, x) {
        if (act === "edit") return openForm("Edit Slide", sliderMetaFields(x), function (v) {
            return api("/admin/mutual-fund/slider/" + x.id, { method: "PUT", body: v }).then(function (r) {
                if (handle(r)) { formModal.hide(); loadSlider(); } else formError(r.errors ? Object.values(r.errors).join(" ") : r.message);
            });
        });
        if (act === "replace") return openForm("Replace Image — " + (x.title || "Slide"),
            '<div class="mb-3"><label class="form-label small fw-bold">New image (JPG / PNG / WebP)</label><input type="file" class="form-control" name="file" accept="image/jpeg,image/png,image/webp" required></div>',
            function (v) {
                if (!v.file) { formError("Choose an image."); return Promise.resolve(); }
                var fd = new FormData(); fd.append("file", v.file);
                return api("/admin/mutual-fund/slider/" + x.id + "/image", { method: "POST", body: fd }).then(function (r) {
                    if (handle(r)) { formModal.hide(); loadSlider(); } else formError(r.message);
                });
            }, { submitLabel: "Replace" });
        if (act === "toggle") return api("/admin/mutual-fund/slider/" + x.id + "/active", { method: "PATCH", body: { active: !x.active } }).then(function (r) { if (handle(r)) loadSlider(); });
        if (act === "delete") { if (confirmAction("Delete this slide from the Mutual Fund homepage slider? This cannot be undone.")) api("/admin/mutual-fund/slider/" + x.id, { method: "DELETE" }).then(function (r) { if (handle(r)) loadSlider(); }); return; }
        if (act === "up" || act === "down") {
            var ids = sliderCache.map(function (y) { return y.id; });
            var i = ids.indexOf(x.id), j = act === "up" ? i - 1 : i + 1;
            if (j < 0 || j >= ids.length) return;
            ids[i] = ids[j]; ids[j] = x.id;
            return api("/admin/mutual-fund/slider/reorder", { method: "PUT", body: { orderedIds: ids } }).then(function (r) { if (handle(r, false)) loadSlider(); });
        }
    }

    // ---- mentoring -------------------------------------------------------------------------

    function loadMentoring() { loadDoubts(); }

    function loadDoubts() {
        var st = el("doubtStatusFilter").value;
        api("/admin/doubts?size=50" + (st ? "&status=" + st : "")).then(function (res) {
            if (!handle(res, false)) return;
            var rows = (res.data && res.data.content) || [];
            el("doubtsBody").innerHTML = rows.map(function (x) {
                return '<tr><td><small>' + dt(x.createdAt) + '</small></td><td><a href="#" data-student="' + x.studentUserId + '">' + esc(x.studentName) + '</a></td><td>' + esc(x.courseName || "General") + '</td><td><strong>' + esc(x.title) + '</strong><br><small class="text-muted">' + esc((x.description || "").slice(0, 100)) + '</small></td><td>' + x.replyCount + '</td><td>' + badge(x.status) + '</td><td><button class="btn btn-xs btn-primary" data-doubt="' + x.id + '">Open</button></td></tr>';
            }).join("") || '<tr><td colspan="7" class="text-muted text-center py-4">No doubts.</td></tr>';
            bindStudentLinks(el("doubtsBody"));
            el("doubtsBody").querySelectorAll("[data-doubt]").forEach(function (b) { b.addEventListener("click", function () { openDoubt(b.dataset.doubt); }); });
        });
    }

    function openDoubt(id) {
        api("/admin/doubts/" + id).then(function (res) {
            if (!handle(res, false)) return;
            var x = res.data;
            var thread = '<div class="mb-2">' + badge(x.status) + ' <small class="text-muted">' + esc(x.studentName) + ' · ' + dt(x.createdAt) + (x.courseName ? " · " + esc(x.courseName) : "") + '</small></div>' +
                '<div class="p-3 bg-light rounded-3 mb-3">' + esc(x.description).replace(/\n/g, "<br>") + '</div>' +
                (x.replies || []).map(function (r) { var mentor = r.authorRole !== "STUDENT"; return '<div class="p-2 rounded-3 mb-2 ' + (mentor ? "border border-success bg-success bg-opacity-10" : "bg-light") + '"><small class="d-block ' + (mentor ? "text-success fw-bold" : "text-muted") + '">' + esc(r.authorName) + ' · ' + dt(r.createdAt) + '</small>' + esc(r.message).replace(/\n/g, "<br>") + '</div>'; }).join("") +
                input("message", "Reply", "", { type: "textarea", rows: 3 }) +
                input("status", "Set status", x.status, { type: "select", options: [{ value: "OPEN", label: "Open" }, { value: "IN_PROGRESS", label: "In progress" }, { value: "RESOLVED", label: "Resolved" }] });
            openForm(x.title, thread, function (v) {
                var chain = Promise.resolve({ success: true });
                if (v.message && v.message.trim()) chain = api("/admin/doubts/" + id + "/replies", { method: "POST", body: { message: v.message.trim() } });
                return chain.then(function (r1) {
                    if (!r1.success) { formError(r1.message); return; }
                    if (v.status !== x.status) return api("/admin/doubts/" + id + "/status", { method: "PATCH", body: { status: v.status } });
                    return r1;
                }).then(function (r) { if (r && handle(r, "Saved.")) { formModal.hide(); loadDoubts(); loadDashboard(); } });
            }, { size: "modal-lg", submitLabel: "Send / Update" });
        });
    }

    // ---- blogs -----------------------------------------------------------------------------

    function blogImgSrc(path) {
        if (!path) return "";
        if (path.indexOf("http://") === 0 || path.indexOf("https://") === 0 || path.indexOf("data:") === 0) return path;
        if (path.indexOf("images/") === 0) return LSI_Auth.apiBase + "/public/" + path;
        return path;
    }

    function loadBlogCategoryFilterOptions() {
        var siteParam = state.blogSite ? "?site=" + encodeURIComponent(state.blogSite) : "";
        api("/admin/blog-categories" + siteParam).then(function (res) {
            if (!res.success) return;
            var list = res.data || [];
            var opts = '<option value="">All Categories</option>';
            list.forEach(function (c) {
                opts += '<option value="' + c.id + '"' + (String(state.blogCategory) === String(c.id) ? ' selected' : '') + '>' + esc(c.name) + ' (' + siteName(c.site) + ')</option>';
            });
            el("blogCategoryFilter").innerHTML = opts;
        });
    }

    function loadBlogs(page) {
        state.blogPage = typeof page === "number" ? page : 0;
        var qs = "?page=" + state.blogPage + "&size=15";
        if (state.blogSite) qs += "&site=" + encodeURIComponent(state.blogSite);
        if (state.blogCategory) qs += "&categoryId=" + encodeURIComponent(state.blogCategory);
        if (state.blogStatus) qs += "&status=" + encodeURIComponent(state.blogStatus);
        if (state.blogSearch) qs += "&search=" + encodeURIComponent(state.blogSearch);

        api("/admin/blogs" + qs).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            var rows = p.content || [];
            el("blogsBody").innerHTML = rows.map(function (b) {
                var imgHtml = b.featuredImagePath
                    ? '<img src="' + esc(blogImgSrc(b.featuredImagePath)) + '" style="width:50px;height:40px;object-fit:cover;border-radius:6px;" alt="">'
                    : '<div class="bg-light text-muted d-flex align-items-center justify-content-center rounded" style="width:50px;height:40px;font-size:14px;"><i class="fas fa-newspaper"></i></div>';
                var pubBtn = b.status === "PUBLISHED"
                    ? '<button class="btn btn-outline-warning" data-b="unpublish" data-id="' + b.id + '" title="Unpublish"><i class="fas fa-eye-slash"></i></button>'
                    : '<button class="btn btn-outline-success" data-b="publish" data-id="' + b.id + '" title="Publish"><i class="fas fa-paper-plane"></i></button>';

                return '<tr>' +
                    '<td>' + imgHtml + '</td>' +
                    '<td><strong>' + esc(b.title) + '</strong><br><small class="text-muted font-monospace">/blog/' + esc(b.slug) + '</small></td>' +
                    '<td>' + siteBadge(b.site) + '</td>' +
                    '<td><span class="badge bg-light text-dark border">' + esc(b.categoryName || "Uncategorized") + '</span></td>' +
                    '<td>' + badge(b.status) + '</td>' +
                    '<td><small>' + esc(b.author || "—") + '</small></td>' +
                    '<td><small>' + d(b.publishedAt) + '</small></td>' +
                    '<td><small>' + dt(b.updatedAt) + '</small></td>' +
                    '<td class="text-end text-nowrap">' +
                    '<div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-b="edit" data-id="' + b.id + '" title="Edit"><i class="fas fa-edit"></i></button>' +
                    pubBtn +
                    '<button class="btn btn-outline-danger" data-b="delete" data-id="' + b.id + '" title="Delete"><i class="fas fa-trash"></i></button>' +
                    '</div></td></tr>';
            }).join("") || '<tr><td colspan="9" class="text-muted text-center py-4">No articles match your criteria.</td></tr>';

            el("blogsCount").innerText = (p.totalElements || 0) + " article(s)";
            pager("blogsPager", p, loadBlogs);

            el("blogsBody").querySelectorAll("[data-b]").forEach(function (btn) {
                var act = btn.dataset.b;
                var id = btn.dataset.id;
                btn.addEventListener("click", function () {
                    if (act === "edit") openBlogForm(id);
                    else if (act === "publish") toggleBlogPublish(id, true);
                    else if (act === "unpublish") toggleBlogPublish(id, false);
                    else if (act === "delete") deleteBlog(id);
                });
            });
        });
    }

    function toggleBlogPublish(id, publish) {
        var action = publish ? "publish" : "unpublish";
        api("/admin/blogs/" + id + "/" + action, { method: "PATCH" }).then(function (res) {
            if (handle(res)) loadBlogs(state.blogPage);
        });
    }

    function deleteBlog(id) {
        if (!confirmAction("Are you sure you want to delete this blog article? This action cannot be undone.")) return;
        api("/admin/blogs/" + id, { method: "DELETE" }).then(function (res) {
            if (handle(res)) loadBlogs(state.blogPage);
        });
    }

    var manualSlugEdit = false;

    function populateCategoriesForForm(site, selectedId) {
        return api("/admin/blog-categories?site=" + site).then(function (res) {
            var cats = (res.data || []).filter(function (c) { return c.active; });
            var sel = el("blogFormCategoryInput");
            if (cats.length === 0) {
                sel.innerHTML = '<option value="">No active categories for this site (Create one first)</option>';
                return;
            }
            sel.innerHTML = cats.map(function (c) {
                return '<option value="' + c.id + '"' + (String(c.id) === String(selectedId) ? ' selected' : '') + '>' + esc(c.name) + '</option>';
            }).join("");
        });
    }

    function openBlogForm(blogId) {
        manualSlugEdit = false;
        el("blogFormId").value = blogId || "";
        el("blogFormImageFile").value = "";
        el("blogEditorModeWrite").classList.add("active");
        el("blogEditorModePreview").classList.remove("active");
        el("blogFormContentInput").classList.remove("d-none");
        el("blogFormContentPreview").classList.add("d-none");

        if (!blogId) {
            el("blogFormTitle").innerText = "Create New Blog Article";
            el("blogFormTitleInput").value = "";
            el("blogFormSlugInput").value = "";
            el("blogFormExcerptInput").value = "";
            el("blogFormContentInput").value = "";
            el("blogFormContentPreview").innerHTML = "";
            el("blogFormSiteInput").value = "ACADEMY";
            el("blogFormStatusInput").value = "DRAFT";
            el("blogFormAuthorInput").value = "Mentor VAIBHAV PAWAR";
            el("blogFormTagsInput").value = "";
            el("blogFormImagePath").value = "";
            el("blogFormSeoTitle").value = "";
            el("blogFormSeoDesc").value = "";
            el("blogFormSeoKeywords").value = "";
            el("blogImagePreviewBox").innerHTML = '<span class="text-muted small">No image uploaded</span>';
            el("blogFormImageRemoveBtn").classList.add("d-none");
            populateCategoriesForForm("ACADEMY", null);
            show("blog-form");
            return;
        }

        api("/admin/blogs/" + blogId).then(function (res) {
            if (!handle(res, false)) return;
            var b = res.data;
            manualSlugEdit = true;
            el("blogFormTitle").innerText = "Edit Blog — " + b.title;
            el("blogFormTitleInput").value = b.title || "";
            el("blogFormSlugInput").value = b.slug || "";
            el("blogFormExcerptInput").value = b.shortDescription || "";
            el("blogFormContentInput").value = b.content || "";
            el("blogFormSiteInput").value = b.site || "ACADEMY";
            el("blogFormStatusInput").value = b.status || "DRAFT";
            el("blogFormAuthorInput").value = b.author || "";
            el("blogFormTagsInput").value = b.tags || "";
            el("blogFormImagePath").value = b.featuredImagePath || "";
            el("blogFormSeoTitle").value = b.seoTitle || "";
            el("blogFormSeoDesc").value = b.seoDescription || "";
            el("blogFormSeoKeywords").value = b.seoKeywords || "";

            if (b.featuredImagePath) {
                el("blogImagePreviewBox").innerHTML = '<img src="' + esc(blogImgSrc(b.featuredImagePath)) + '" style="max-height:140px;max-width:100%;border-radius:6px;" alt="">';
                el("blogFormImageRemoveBtn").classList.remove("d-none");
            } else {
                el("blogImagePreviewBox").innerHTML = '<span class="text-muted small">No image uploaded</span>';
                el("blogFormImageRemoveBtn").classList.add("d-none");
            }

            populateCategoriesForForm(b.site, b.categoryId);
            show("blog-form");
        });
    }

    function saveBlog(targetStatus) {
        var id = el("blogFormId").value;
        var title = el("blogFormTitleInput").value.trim();
        var slug = el("blogFormSlugInput").value.trim();
        var excerpt = el("blogFormExcerptInput").value.trim();
        var content = el("blogFormContentInput").value.trim();
        var catId = el("blogFormCategoryInput").value;
        var site = el("blogFormSiteInput").value;
        var status = targetStatus || el("blogFormStatusInput").value || "DRAFT";

        if (!title) { toast("Blog title is required.", false); el("blogFormTitleInput").focus(); return; }
        if (!slug) { toast("Slug is required.", false); el("blogFormSlugInput").focus(); return; }
        if (!excerpt) { toast("Short description / excerpt is required.", false); el("blogFormExcerptInput").focus(); return; }
        if (!content) { toast("Article content is required.", false); el("blogFormContentInput").focus(); return; }
        if (!catId) { toast("Please select a category.", false); el("blogFormCategoryInput").focus(); return; }

        var body = {
            site: site,
            title: title,
            slug: slug,
            categoryId: Number(catId),
            shortDescription: excerpt,
            content: content,
            featuredImagePath: el("blogFormImagePath").value || null,
            author: el("blogFormAuthorInput").value.trim() || "Lord Sai Team",
            tags: el("blogFormTagsInput").value.trim() || null,
            status: status,
            seoTitle: el("blogFormSeoTitle").value.trim() || null,
            seoDescription: el("blogFormSeoDesc").value.trim() || null,
            seoKeywords: el("blogFormSeoKeywords").value.trim() || null
        };

        var saveDraftBtn = el("blogSaveDraftBtn");
        var publishBtn = el("blogPublishBtn");
        saveDraftBtn.disabled = true;
        publishBtn.disabled = true;

        var req = id
            ? api("/admin/blogs/" + id, { method: "PUT", body: body })
            : api("/admin/blogs", { method: "POST", body: body });

        req.then(function (res) {
            if (!handle(res, false)) {
                saveDraftBtn.disabled = false;
                publishBtn.disabled = false;
                return;
            }
            var saved = res.data;
            var savedId = saved.id;
            var file = el("blogFormImageFile").files[0];

            if (file) {
                var fd = new FormData();
                fd.append("file", file);
                return api("/admin/blogs/" + savedId + "/image", { method: "POST", body: fd }).then(function (imgRes) {
                    toast(status === "PUBLISHED" ? "Blog published successfully!" : "Blog saved as draft.");
                    show("blogs");
                    loadBlogs(0);
                });
            } else {
                toast(status === "PUBLISHED" ? "Blog published successfully!" : "Blog saved as draft.");
                show("blogs");
                loadBlogs(0);
            }
        }).catch(function (err) {
            toast("Error saving blog article.", false);
        }).finally(function () {
            saveDraftBtn.disabled = false;
            publishBtn.disabled = false;
        });
    }

    function insertEditorTag(openTag, closeTag, defaultText) {
        var textarea = el("blogFormContentInput");
        var start = textarea.selectionStart;
        var end = textarea.selectionEnd;
        var val = textarea.value;
        var selected = val.substring(start, end) || defaultText || "";
        var replacement = openTag + selected + (closeTag || "");
        textarea.value = val.substring(0, start) + replacement + val.substring(end);
        textarea.focus();
        textarea.selectionStart = start + openTag.length;
        textarea.selectionEnd = start + openTag.length + selected.length;
    }

    // ---- blog categories -------------------------------------------------------------------

    function loadBlogCategories() {
        var qs = state.catSite ? "?site=" + encodeURIComponent(state.catSite) : "";
        api("/admin/blog-categories" + qs).then(function (res) {
            if (!handle(res, false)) return;
            var cats = res.data || [];
            el("categoriesBody").innerHTML = cats.map(function (c) {
                var statusBadge = c.active
                    ? '<span class="badge bg-success">Active</span>'
                    : '<span class="badge bg-secondary">Inactive</span>';
                return '<tr>' +
                    '<td><strong>' + esc(c.name) + '</strong></td>' +
                    '<td><code class="small">' + esc(c.slug) + '</code></td>' +
                    '<td>' + siteBadge(c.site) + '</td>' +
                    '<td><small class="text-muted">' + esc(c.description || "—") + '</small></td>' +
                    '<td>' + c.displayOrder + '</td>' +
                    '<td>' + statusBadge + '</td>' +
                    '<td><span class="badge bg-light text-dark border">' + c.blogCount + '</span></td>' +
                    '<td class="text-end text-nowrap">' +
                    '<div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-cat="edit" data-id="' + c.id + '" title="Edit"><i class="fas fa-edit"></i></button>' +
                    '<button class="btn btn-outline-secondary" data-cat="toggle" data-id="' + c.id + '" title="Toggle Status"><i class="fas fa-power-off"></i></button>' +
                    '<button class="btn btn-outline-danger" data-cat="delete" data-id="' + c.id + '" title="Delete"><i class="fas fa-trash"></i></button>' +
                    '</div></td></tr>';
            }).join("") || '<tr><td colspan="8" class="text-muted text-center py-4">No categories found.</td></tr>';

            el("categoriesBody").querySelectorAll("[data-cat]").forEach(function (btn) {
                var act = btn.dataset.cat;
                var id = btn.dataset.id;
                var item = cats.find(function (x) { return String(x.id) === String(id); });
                btn.addEventListener("click", function () {
                    if (act === "edit") openCategoryForm(item);
                    else if (act === "toggle") toggleCategory(id);
                    else if (act === "delete") deleteCategory(id, item ? item.name : "");
                });
            });
        });
    }

    function openCategoryForm(c) {
        var siteOptions = [
            { value: "ACADEMY", label: "Share Market Academy" },
            { value: "MUTUAL_FUND", label: "Mutual Fund Distribution" }
        ];
        var f = input("site", "Website Target", c ? c.site : "ACADEMY", { type: "select", options: siteOptions, required: true }) +
            input("name", "Category Name", c ? c.name : "", { required: true, maxlength: 100, placeholder: "e.g. Technical Analysis" }) +
            input("slug", "Slug (optional, auto-generated if empty)", c ? c.slug : "", { maxlength: 120, placeholder: "e.g. technical-analysis" }) +
            input("description", "Description", c ? c.description : "", { type: "textarea", rows: 2, maxlength: 255 }) +
            input("displayOrder", "Display Order", c ? c.displayOrder : 0, { type: "number" }) +
            '<div class="form-check form-switch mb-3"><input class="form-check-input" type="checkbox" name="active" id="catActiveCheck"' + (!c || c.active ? ' checked' : '') + '><label class="form-check-label small fw-bold" for="catActiveCheck">Active (Visible in dropdowns)</label></div>';

        openForm(c ? "Edit Blog Category" : "Add Blog Category", f, function (v) {
            var body = {
                site: v.site,
                name: v.name.trim(),
                slug: v.slug ? v.slug.trim() : null,
                description: v.description ? v.description.trim() : null,
                displayOrder: Number(v.displayOrder || 0),
                active: v.active !== false
            };
            var req = c
                ? api("/admin/blog-categories/" + c.id, { method: "PUT", body: body })
                : api("/admin/blog-categories", { method: "POST", body: body });
            return req.then(function (r) {
                if (handle(r)) {
                    formModal.hide();
                    loadBlogCategories();
                    loadBlogCategoryFilterOptions();
                } else {
                    formError(r.errors ? Object.values(r.errors).join(" ") : r.message);
                }
            });
        });
    }

    function toggleCategory(id) {
        api("/admin/blog-categories/" + id + "/toggle", { method: "PATCH" }).then(function (res) {
            if (handle(res)) {
                loadBlogCategories();
                loadBlogCategoryFilterOptions();
            }
        });
    }

    function deleteCategory(id, name) {
        if (!confirmAction("Are you sure you want to delete the category '" + name + "'? Blogs linked to it cannot be orphaned if in use.")) return;
        api("/admin/blog-categories/" + id, { method: "DELETE" }).then(function (res) {
            if (handle(res)) {
                loadBlogCategories();
                loadBlogCategoryFilterOptions();
            }
        });
    }

    function loadAudit(page) {
        state.auditPage = page;
        api("/admin/audit-logs?page=" + page + "&size=50").then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("auditBody").innerHTML = (p.content || []).map(function (a) {
                return '<tr><td><small class="text-nowrap">' + dt(a.createdAt) + '</small></td><td><small>' + esc(a.actorName) + (a.actorRole ? ' <span class="badge bg-light text-dark border">' + esc(a.actorRole) + '</span>' : '') + '</small></td><td><code class="small">' + esc(a.action) + '</code></td><td><small>' + (a.entityType ? esc(a.entityType) + " #" + a.entityId : "—") + '</small></td><td><small>' + esc(a.description) + '</small></td><td><small class="text-muted">' + esc(a.ipAddress || "") + '</small></td></tr>';
            }).join("") || '<tr><td colspan="6" class="text-muted text-center py-4">No activity yet.</td></tr>';
            el("auditCount").innerText = p.totalElements + " event(s)";
            pager("auditPager", p, loadAudit);
        });
    }


    // ---- payment gateway -------------------------------------------------------------------

    function loadGateway() {
        api("/admin/payment-gateway").then(function (res) {
            if (!handle(res, false)) return;
            renderGatewayStatus(res.data);
        });
        api("/admin/payments?size=10").then(function (res) {
            if (!res.success) return;
            var rows = (res.data && res.data.content) || [];
            el("gatewayTxBody").innerHTML = rows.map(function (x) {
                return '<tr><td><small>' + dt(x.createdAt) + '</small></td><td>' + (x.userId ? '<a href="#" data-student="' + x.userId + '">' + esc(x.customerName) + '</a>' : esc(x.customerName)) + '<br><small class="text-muted">' + esc(x.customerEmail) + '</small></td><td>' + esc(x.courseName) + '</td><td><strong>' + inr(x.amount) + '</strong></td><td>' + badge(x.paymentMode || "RAZORPAY") + '</td><td>' + badge(x.status) + '</td><td class="font-monospace"><small>' + esc(x.orderRef) + '</small></td><td class="font-monospace"><small>' + esc(x.razorpayPaymentId || "—") + '</small></td></tr>';
            }).join("") || '<tr><td colspan="8" class="text-muted text-center py-4">No transactions yet.</td></tr>';
            bindStudentLinks(el("gatewayTxBody"));
        });
    }

    function renderGatewayStatus(g) {
        var real = g.mode === "RAZORPAY";
        var card = el("gatewayStatusCard");
        var modeText = real
            ? "Razorpay is configured and real payments are enabled." + (g.keyMode === "TEST"
                ? " <strong>Test-mode keys</strong> are in use — Razorpay will not move real money until live keys are saved."
                : " <strong class=\"text-danger\">Live keys are in use — students are charged real money.</strong>")
            : "Razorpay is not configured. Students can currently enroll using demo payments (no money is charged).";
        card.innerHTML =
            '<div class="d-flex flex-wrap align-items-center gap-3">' +
            '<div class="rounded-circle d-flex align-items-center justify-content-center" style="width:56px;height:56px;font-size:24px;background:' + (real ? "rgba(16,185,129,.15);color:#10b981" : "rgba(245,158,11,.15);color:#f59e0b") + '"><i class="fas ' + (real ? "fa-check-circle" : "fa-flask") + '"></i></div>' +
            '<div class="flex-grow-1"><div class="fw-bold" style="font-size:18px">' + (real ? "🟢 REAL PAYMENT ACTIVE" : "🟡 DEMO PAYMENT ACTIVE") + '</div>' +
            '<div class="text-muted">' + modeText + '</div></div>' +
            '<div class="text-end small text-muted">' +
            (g.keyId ? '<div>Key ID <code>' + esc(g.keyId) + '</code> <span class="badge ' + (g.keyMode === "LIVE" ? "bg-danger" : "bg-secondary") + '">' + esc(g.keyMode || "") + '</span></div>' : "") +
            '<div>Key Secret: ' + (g.recordExists ? esc(g.keySecretMasked) : "—") + ' · Webhook Secret: ' + (g.webhookSecretSet ? esc(g.webhookSecretMasked) : "not set") + '</div>' +
            '<div>Currency <strong>' + esc(g.currency || "INR") + '</strong>' + (g.credentialSource ? " · Source: " + esc(g.credentialSource === "ADMIN" ? "Admin panel" : "Server environment") : "") + '</div>' +
            (g.recordExists ? '<div>Status: ' + (g.enabled ? '<span class="badge bg-success">Enabled</span>' : '<span class="badge bg-secondary">Disabled</span>') + ' · Validated ' + dt(g.validatedAt) + '</div><div>Created ' + dt(g.createdAt) + (g.createdBy ? " by " + esc(g.createdBy) : "") + ' · Updated ' + dt(g.updatedAt) + (g.updatedBy ? " by " + esc(g.updatedBy) : "") + '</div>' : "") +
            '</div></div>';

        el("gwKeyId").value = g.recordExists ? g.keyId : "";
        el("gwKeySecret").value = ""; el("gwWebhookSecret").value = "";
        el("gwCurrency").value = g.currency || "INR";
        el("gatewayFormTitle").innerText = g.recordExists ? "Update Credentials" : "Configure Razorpay";
        el("gwSaveLabel").innerHTML = g.recordExists ? "Save Changes" : "Save &amp; Enable Real Payments";
        el("gwKeySecretHelp").innerText = g.recordExists ? "Leave blank to keep the saved secret; enter a new one to replace it." : "Required. Never shown again after saving.";
        el("gwKeySecret").required = !g.recordExists;
        el("gwError").classList.add("d-none");

        var acts = el("gatewayActions"); acts.innerHTML = "";
        if (g.recordExists) {
            if (g.enabled) acts.insertAdjacentHTML("beforeend", '<button class="btn btn-outline-warning btn-sm" data-gw="disable"><i class="fas fa-pause me-1"></i> Disable Razorpay (switch to Demo)</button>');
            else acts.insertAdjacentHTML("beforeend", '<button class="btn btn-outline-success btn-sm" data-gw="enable"><i class="fas fa-play me-1"></i> Enable Razorpay (Real payments)</button>');
            acts.insertAdjacentHTML("beforeend", '<button class="btn btn-outline-danger btn-sm" data-gw="remove"><i class="fas fa-trash me-1"></i> Remove Configuration</button>');
        } else {
            acts.innerHTML = '<span class="text-muted small">No configuration saved yet. ' + (g.credentialSource === "ENVIRONMENT" ? "Real payments are currently enabled through server environment variables; saving credentials here will take precedence." : "Fill in the form to enable real payments.") + '</span>';
        }
        acts.querySelectorAll("[data-gw]").forEach(function (b) {
            b.addEventListener("click", function () {
                var a = b.dataset.gw;
                var msg = a === "disable" ? "Disable Razorpay? The website will immediately switch to DEMO PAYMENT MODE — students can enroll without paying."
                        : a === "enable" ? "Re-enable Razorpay? Credentials will be re-verified and students will be charged real money through Razorpay."
                        : "Remove the Razorpay configuration entirely? The website switches to DEMO PAYMENT MODE. You can add credentials again later.";
                if (!confirmAction(msg)) return;
                var call = a === "remove" ? api("/admin/payment-gateway", { method: "DELETE" }) : api("/admin/payment-gateway/" + a, { method: "PATCH" });
                call.then(function (r) { if (handle(r)) loadGateway(); });
            });
        });
    }

    function saveGateway(e) {
        e.preventDefault();
        var keyId = el("gwKeyId").value.trim();
        var live = keyId.indexOf("rzp_live_") === 0;
        if (!confirmAction(live
                ? "You are saving LIVE Razorpay keys. Once verified, students will be charged REAL money. Continue?"
                : "Save these credentials? They will be verified with Razorpay and, if valid, real payments (test mode) will be enabled.")) return;
        var body = { keyId: keyId, keySecret: el("gwKeySecret").value, webhookSecret: el("gwWebhookSecret").value, currency: el("gwCurrency").value.trim().toUpperCase() || "INR" };
        var btn = el("gwSaveBtn"); btn.disabled = true; el("gwError").classList.add("d-none");
        api("/admin/payment-gateway", { method: "PUT", body: body }).then(function (r) {
            btn.disabled = false;
            if (!r.success) { el("gwError").innerText = r.errors ? Object.values(r.errors).join(" ") : r.message; el("gwError").classList.remove("d-none"); return; }
            toast(r.message); loadGateway();
        });
    }

    // ---- whatsapp settings (central configuration; the token never reaches the browser) -----

    function loadWhatsAppSettings() {
        api("/admin/whatsapp").then(function (res) {
            if (!handle(res, false)) { el("waStatusCard").innerHTML = '<div class="text-danger">' + esc(res.message) + '</div>'; return; }
            renderWhatsAppStatus(res.data);
        });
    }

    function renderWhatsAppStatus(w) {
        var on = w.configured;
        el("waStatusCard").innerHTML =
            '<div class="d-flex flex-wrap align-items-center gap-3">' +
            '<div class="rounded-circle d-flex align-items-center justify-content-center" style="width:56px;height:56px;font-size:24px;background:' + (on ? "rgba(16,185,129,.15);color:#10b981" : "rgba(245,158,11,.15);color:#f59e0b") + '"><i class="fab fa-whatsapp"></i></div>' +
            '<div class="flex-grow-1"><div class="fw-bold" style="font-size:18px">WhatsApp Status<br><span style="font-size:15px">' + (on ? "🟢 Connected" : "🟡 Not Configured") + '</span></div>' +
            '<div class="text-muted">' + esc(w.message) + '</div></div>' +
            '<div class="text-end small text-muted">' +
            (w.provider ? '<div>Provider <code>' + esc(w.provider) + '</code>' + (w.credentialSource ? " · Source: " + esc(w.credentialSource === "ADMIN" ? "Admin panel" : "Server environment") : "") + '</div>' : "") +
            (w.phoneNumberId ? '<div>Phone Number ID <code>' + esc(w.phoneNumberId) + '</code>' + (w.displayPhoneNumber ? " · " + esc(w.displayPhoneNumber) : "") + '</div>' : "") +
            '<div>Access Token: ' + (w.accessTokenMasked ? esc(w.accessTokenMasked) : "—") + '</div>' +
            (w.recordExists ? '<div>Status: ' + (w.enabled ? '<span class="badge bg-success">Enabled</span>' : '<span class="badge bg-secondary">Disabled</span>') + (w.validatedAt ? " · Verified " + dt(w.validatedAt) : "") + '</div><div>Updated ' + dt(w.updatedAt) + (w.updatedBy ? " by " + esc(w.updatedBy) : "") + '</div>' : "") +
            '</div></div>';

        el("waProvider").value = w.provider || "META_CLOUD";
        el("waPhoneId").value = w.recordExists ? w.phoneNumberId : "";
        el("waBusinessId").value = w.recordExists ? (w.businessAccountId || "") : "";
        el("waToken").value = "";
        el("waToken").placeholder = w.recordExists ? w.accessTokenMasked : "Paste the access token";
        el("waToken").required = !w.recordExists;
        el("waTokenHelp").innerText = w.recordExists ? "Saved and encrypted. Leave blank to keep it; paste a new token to replace it." : "Required the first time. Never shown again after saving.";
        el("waFormTitle").innerText = w.recordExists ? "Update WhatsApp Settings" : "Configure WhatsApp";
        el("waSaveLabel").innerText = w.recordExists ? "Save Changes" : "Save WhatsApp Settings";
        el("waError").classList.add("d-none"); el("waTestError").classList.add("d-none");
        el("waTestSendBtn").disabled = !on;

        var acts = el("waActions"); acts.innerHTML = "";
        if (w.recordExists) {
            if (w.enabled) acts.insertAdjacentHTML("beforeend", '<button class="btn btn-outline-warning btn-sm" data-wa="disable"><i class="fas fa-pause me-1"></i> Disable WhatsApp</button>');
            else acts.insertAdjacentHTML("beforeend", '<button class="btn btn-outline-success btn-sm" data-wa="enable"><i class="fas fa-play me-1"></i> Enable WhatsApp</button>');
            acts.insertAdjacentHTML("beforeend", '<button class="btn btn-outline-danger btn-sm" data-wa="remove"><i class="fas fa-trash me-1"></i> Remove Configuration</button>');
        } else {
            acts.innerHTML = '<span class="text-muted small">No configuration saved yet. ' + (w.credentialSource === "ENVIRONMENT" ? "WhatsApp is currently working through server environment variables; saving settings here will take precedence." : "Fill in the form to connect WhatsApp.") + '</span>';
        }
        acts.querySelectorAll("[data-wa]").forEach(function (b) {
            b.addEventListener("click", function () {
                var a = b.dataset.wa;
                var msg = a === "disable" ? "Disable WhatsApp? Invoice-on-WhatsApp and Automation Admin WhatsApp sends will stop until it is enabled again."
                        : a === "enable" ? "Re-enable WhatsApp? The saved credentials will be verified with the provider first."
                        : "Remove the WhatsApp configuration entirely? You can enter it again later.";
                if (!confirmAction(msg)) return;
                var call = a === "remove" ? api("/admin/whatsapp", { method: "DELETE" }) : api("/admin/whatsapp/" + a, { method: "PATCH" });
                call.then(function (r) { if (handle(r)) loadWhatsAppSettings(); });
            });
        });
    }

    function saveWhatsAppSettings(e) {
        e.preventDefault();
        var body = { provider: el("waProvider").value, accessToken: el("waToken").value, phoneNumberId: el("waPhoneId").value.trim(), businessAccountId: el("waBusinessId").value.trim() };
        var btn = el("waSaveBtn"); btn.disabled = true; el("waError").classList.add("d-none");
        api("/admin/whatsapp", { method: "PUT", body: body }).then(function (r) {
            btn.disabled = false; el("waToken").value = "";
            if (!r.success) { el("waError").innerText = r.errors ? Object.values(r.errors).join(" ") : r.message; el("waError").classList.remove("d-none"); return; }
            toast(r.message); loadWhatsAppSettings();
        });
    }

    function testWhatsAppConnection() {
        var btn = el("waTestConnBtn"); btn.disabled = true; el("waError").classList.add("d-none");
        api("/admin/whatsapp/test-connection", { method: "POST" }).then(function (r) {
            btn.disabled = false;
            if (!r.success) { el("waError").innerText = r.message; el("waError").classList.remove("d-none"); return; }
            toast(r.message); loadWhatsAppSettings();
        });
    }

    function sendWhatsAppTest(e) {
        e.preventDefault();
        var btn = el("waTestSendBtn"); btn.disabled = true; el("waTestError").classList.add("d-none");
        api("/admin/whatsapp/test-message", { method: "POST", body: { mobile: el("waTestMobile").value.trim(), message: el("waTestMessage").value.trim() } }).then(function (r) {
            btn.disabled = false;
            if (!r.success) { el("waTestError").innerText = r.errors ? Object.values(r.errors).join(" ") : r.message; el("waTestError").classList.remove("d-none"); return; }
            toast(r.message);
        });
    }

    // ---- email settings --------------------------------------------------------------------
    // Email-first workflow: sender address -> Auto Configure (provider, host, port, security,
    // username from the server-side registry) -> credential -> Test SMTP Connection -> Save -> Send Test Email.
    // The browser never holds SMTP knowledge or the saved credential.

    var emailProviders = null, emailDetection = null, emailHasSavedCred = false, emailGoogle = null;

    function loadEmailSettings() {
        var ready = emailProviders ? Promise.resolve() : api("/admin/email-settings/providers").then(function (res) {
            if (!res.success) return;
            emailProviders = res.data;
            var sel = el("emProvider");
            sel.innerHTML = '<option value="AUTO">Auto Detect</option>' + emailProviders.map(function (p) { return '<option value="' + esc(p.code) + '">' + esc(p.label) + '</option>'; }).join("");
        });
        ready.then(function () {
            api("/admin/email-settings").then(function (res) {
                if (!handle(res, false)) return;
                renderEmailSettings(res.data);
            });
            loadGoogleStatus();
        });
    }

    // ---- Gmail via Google OAuth 2.0 ---------------------------------------------------------
    // Google performs the sign-in. This screen only starts the flow and reads back a safe status;
    // it never receives, stores or displays an access token or refresh token, and nothing about
    // the connection is written to localStorage or sessionStorage.

    function loadGoogleStatus() {
        return api("/admin/email-settings/google").then(function (res) {
            if (!res.success) { el("emGoogleCard").innerHTML = '<div class="text-muted small">Google connection status unavailable.</div>'; return; }
            emailGoogle = res.data;
            renderGoogleCard(res.data);
            applyAuthMode(res.data.connected);
        });
    }

    function renderGoogleCard(g) {
        var head = '<div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">'
            + '<h6 class="fw-bold mb-0"><i class="fab fa-google me-2" style="color:#4285F4"></i>Email Provider \u2014 Google Gmail</h6>'
            + '<span class="badge ' + (g.connected ? "bg-success" : "bg-secondary") + '">'
            + (g.connected ? '<i class="fas fa-check me-1"></i> Connected' : "\u25CB Not connected") + '</span></div>';

        if (!g.configured) {
            el("emGoogleCard").innerHTML = head
                + '<div class="alert alert-warning small py-2 mb-2"><i class="fas fa-exclamation-triangle me-1"></i> ' + esc(g.message) + '</div>'
                + '<div class="small text-muted">Register this redirect URI on the OAuth client in Google Cloud:<br>'
                + '<code class="user-select-all">' + esc(g.redirectUri || "") + '</code></div>';
            return;
        }

        if (!g.connected) {
            el("emGoogleCard").innerHTML = head
                + '<p class="small text-muted mb-3">Connect the academy\u2019s Google account once. Google handles the sign-in and consent; '
                + 'this application never asks for, sees or stores your Google password or an App Password. '
                + 'Sending then uses the Gmail API with a permission that can only send mail \u2014 it cannot read your inbox.</p>'
                + '<button class="btn btn-primary" id="emGoogleConnect"><i class="fab fa-google me-1"></i> Connect Google Account</button>'
                + '<div class="form-text mt-2">You will be taken to Google and returned here automatically.</div>';
            el("emGoogleConnect").addEventListener("click", connectGoogle);
            return;
        }

        var warn = g.sendPermissionGranted ? "" :
            '<div class="alert alert-warning small py-2 mt-2 mb-0"><i class="fas fa-exclamation-triangle me-1"></i> Permission to send email was not granted. Reconnect and leave the send permission ticked.</div>';
        el("emGoogleCard").innerHTML = head
            + '<div class="d-flex flex-wrap align-items-center gap-3 mb-3">'
            + '<div class="rounded-circle d-flex align-items-center justify-content-center" style="width:48px;height:48px;font-size:20px;background:rgba(16,185,129,.15);color:#10b981"><i class="fas fa-check"></i></div>'
            + '<div><div class="fw-bold">\u2713 Google Account Connected</div>'
            + '<div class="text-muted small">Gmail: <strong>' + esc(g.email || "unknown") + '</strong>'
            + (g.connectedAt ? ' \u00B7 connected ' + dt(g.connectedAt) : "")
            + (g.connectedBy ? " by " + esc(g.connectedBy) : "") + '</div></div></div>'
            + '<div class="d-flex flex-wrap gap-2">'
            + '<button class="btn btn-outline-success" id="emGoogleTest"><i class="fas fa-paper-plane me-1"></i> Send Test Email</button>'
            + '<button class="btn btn-outline-danger" id="emGoogleDisconnect"><i class="fas fa-unlink me-1"></i> Disconnect Google Account</button>'
            + '</div>'
            + '<div class="form-text mt-2">' + esc(g.message) + '</div>' + warn;
        el("emGoogleTest").addEventListener("click", sendTestEmail);
        el("emGoogleDisconnect").addEventListener("click", disconnectGoogle);
    }

    /** With Google connected the SMTP fields are irrelevant: hide them, but keep their values. */
    function applyAuthMode(googleConnected) {
        document.querySelectorAll(".em-smtp-only").forEach(function (n) { n.classList.toggle("d-none", !!googleConnected); });
        el("emOauthNotice").classList.toggle("d-none", !googleConnected);
        el("emFormHelp").classList.toggle("d-none", !!googleConnected);
        el("emailFormTitle").innerText = googleConnected
            ? "Sender name, test recipient & sending switch"
            : (emailHasSavedCred ? "Update SMTP Settings" : "Configure SMTP");
    }

    /** Hands the browser to Google. The URL is built by the backend, never assembled here. */
    function connectGoogle() {
        var btn = el("emGoogleConnect");
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Opening Google\u2026';
        api("/admin/email-settings/google/connect").then(function (res) {
            if (!res.success || !res.data || !res.data.authorizationUrl) {
                btn.disabled = false;
                btn.innerHTML = '<i class="fab fa-google me-1"></i> Connect Google Account';
                toast(res.message || "Could not start Google sign-in.", false);
                return;
            }
            window.location.assign(res.data.authorizationUrl);
        });
    }

    function disconnectGoogle() {
        if (!confirmAction("Disconnect the Google account?\n\nAccess will be revoked at Google and the stored authorization deleted. "
            + "System emails will stop going through Gmail until you reconnect or configure SMTP.")) { return; }
        el("emGoogleDisconnect").disabled = true;
        api("/admin/email-settings/google/disconnect", { method: "POST" }).then(function (res) {
            handle(res);
            loadEmailSettings();
        });
    }

    /**
     * Reports the outcome of the OAuth round trip. The callback returns only a one-word result in
     * the query string \u2014 never a token or an authorization code \u2014 and it is stripped from
     * the address bar immediately so it cannot be bookmarked, shared or replayed.
     */
    function consumeGoogleCallbackResult() {
        var m = /[?&]google=([a-z_]+)/.exec(window.location.search);
        if (!m) { return null; }
        var clean = window.location.pathname
            + window.location.search.replace(/([?&])google=[a-z_]+&?/, "$1").replace(/[?&]$/, "");
        window.history.replaceState({}, document.title, clean + "#email");
        return m[1];
    }

    function reportGoogleOutcome(outcome) {
        var messages = {
            connected: ["Google account connected.", true],
            denied: ["Google sign-in was cancelled, so nothing was connected.", false],
            invalid_state: ["That Google sign-in link had expired, was already used, or did not come from this panel. Please click Connect Google Account again.", false],
            invalid_callback: ["Google did not return an authorization. Please try connecting again.", false],
            failed: ["The Google account could not be connected \u2014 see the Email Settings page for the reason.", false]
        };
        var m = messages[outcome];
        if (m) { toast(m[0], m[1]); }
    }


    function renderEmailSettings(s) {
        renderEmailStatusCard(s);
        fillEmailForm(s);
    }

    function renderEmailStatusCard(s) {
        var active = s.sendingActive;
        var srcText = s.effectiveSource === "GOOGLE_OAUTH" ? "the connected Google account <strong>" + esc(s.googleEmail || "") + "</strong> (Gmail API)"
            : s.effectiveSource === "ADMIN" ? "the SMTP settings saved on this screen"
            : s.effectiveSource === "ENVIRONMENT" ? "the server environment variables (nothing saved here yet)"
            : s.googleConnected ? "nothing — a Google account is connected but <strong>Enable Email Sending</strong> is switched off below, so emails are only written to the server log"
            : s.recordExists ? "nothing — <strong>Enable Email Sending</strong> is switched off below, so emails are only written to the server log"
            : "nothing — no email account is configured, so emails are only written to the server log";
        var row = function (label, value, cls) { return '<div class="col-6 col-md-4 col-xl-2"><div class="small text-muted">' + label + '</div><div class="fw-bold ' + (cls || "") + '">' + value + '</div></div>'; };
        var sendingRow = row("Email Sending", s.enabled ? (active ? '<span class="badge bg-success">Enabled</span>' : '<span class="badge bg-warning text-dark">Enabled (not active)</span>') : '<span class="badge bg-secondary">Disabled</span>');
        var status = !s.recordExists ? "" : s.googleConnected
            ? '<div class="row g-3 mt-2">' +
                row("Provider", '<i class="fab fa-google me-1"></i> Google Gmail') +
                row("Account", esc(s.googleEmail || "")) +
                row("Transport", '<span class="badge bg-success">Gmail API</span> <small class="text-muted fw-normal">OAuth 2.0 · send only</small>') +
                row("Authorization", '<span class="badge bg-success">Stored securely</span>') +
                sendingRow +
                '</div>'
            : '<div class="row g-3 mt-2">' +
                row("Provider", esc(s.providerLabel || "Custom SMTP")) +
                row("Sender", esc(s.senderEmail)) +
                row("SMTP", '<span class="badge bg-success">Configured</span> <small class="text-muted fw-normal">' + esc(s.smtpHost) + ':' + esc(s.smtpPort) + ' · ' + esc(s.securityMode) + '</small>') +
                row("Authentication", s.smtpUsername ? (!s.smtpPasswordSet ? '<span class="badge bg-danger">Credential missing</span>' : (s.credentialReadable === false ? '<span class="badge bg-danger">Needs re-entry</span>' : '<span class="badge bg-success">Configured securely</span>')) : '<span class="badge bg-secondary">Not required</span>') +
                row("Connection", s.connectionVerifiedAt ? '<span class="badge bg-success">Verified</span> <small class="text-muted fw-normal">' + dt(s.connectionVerifiedAt) + '</small>' : '<span class="badge bg-warning text-dark">Not verified</span>') +
                sendingRow +
                '</div>';
        var testText = !s.recordExists || !s.lastTestedAt ? "" : '<div class="small text-muted mt-2">Last test email: ' + (s.lastTestOk ? '<span class="badge bg-success">delivered</span>' : '<span class="badge bg-danger">failed</span>') + ' ' + dt(s.lastTestedAt) + ' · Updated ' + dt(s.updatedAt) + (s.updatedBy ? " by " + esc(s.updatedBy) : "") + '</div>';
        el("emailStatusCard").innerHTML =
            '<div class="d-flex flex-wrap align-items-center gap-3">' +
            '<div class="rounded-circle d-flex align-items-center justify-content-center" style="width:56px;height:56px;font-size:24px;background:' + (active ? "rgba(16,185,129,.15);color:#10b981" : "rgba(245,158,11,.15);color:#f59e0b") + '"><i class="fas ' + (active ? "fa-envelope-open-text" : "fa-envelope") + '"></i></div>' +
            '<div class="flex-grow-1"><div class="fw-bold" style="font-size:18px">' + (active ? "🟢 EMAIL SYSTEM READY" : "🟡 EMAIL SENDING OFF") + '</div>' +
            '<div class="text-muted">Outgoing email currently uses ' + srcText + '.</div></div></div>' + status + testText;
    }

    function fillEmailForm(s) {
        el("emProvider").value = s.provider || "AUTO";
        el("emSenderName").value = s.senderName || "";
        el("emSenderEmail").value = s.senderEmail || "";
        if (el("emReplyTo")) el("emReplyTo").value = s.replyTo || "";
        if (el("emSendingDomain")) el("emSendingDomain").value = s.sendingDomain || "";
        if (el("emDkimSelector")) el("emDkimSelector").value = s.dkimSelector || "";
        el("emHost").value = s.smtpHost || "";
        el("emPort").value = s.smtpPort || 587;
        el("emUsername").value = s.smtpUsername || "";
        el("emPassword").value = "";
        el("emPassword").type = "password";
        el("emSecurity").value = s.securityMode || "STARTTLS";
        el("emEnabled").checked = !!s.enabled;
        el("emTestRecipient").value = s.testRecipient || "";
        el("emailFormTitle").innerText = s.recordExists ? "Update SMTP Settings" : "Configure SMTP";
        applyAuthMode(!!s.googleConnected);
        // A stored credential that no longer decrypts must not look "saved": open the field and say so.
        emailHasSavedCred = !!(s.recordExists && s.smtpPasswordSet && s.credentialReadable !== false);
        setCredentialState(emailHasSavedCred, s.credentialLabel);
        var unreadable = !!(s.recordExists && s.smtpPasswordSet && s.credentialReadable === false);
        el("emCredWarning").classList.toggle("d-none", !unreadable);
        if (unreadable) {
            el("emPasswordHelp").innerText = "The stored credential cannot be decrypted, so it cannot be reused. Enter the "
                + (s.credentialLabel || "SMTP password") + " again and click Save Email Settings.";
        }
        el("emDetect").classList.add("d-none"); emailDetection = null;
        el("emError").classList.add("d-none"); el("emError").innerText = "";
        el("emPassword").classList.remove("is-invalid");
        el("emConnResult").classList.add("d-none");
        el("emTestResult").classList.add("d-none");
        el("emTestBtn").disabled = !s.recordExists && s.effectiveSource !== "ENVIRONMENT";
        el("emRemoveBtn").classList.toggle("d-none", !s.recordExists);
        applyProviderMode(el("emProvider").value);
    }

    /** Saved credential: show "Saved securely" and hide the input until the admin chooses to replace it. */
    function setCredentialState(saved, label) {
        el("emPasswordLabel").innerText = label ? label : "SMTP Credential / App Password";
        el("emSavedCred").classList.toggle("d-none", !saved);
        el("emSavedCred").classList.toggle("d-flex", saved);
        el("emPasswordGroup").classList.toggle("d-none", saved);
        el("emSavedCredText").innerText = saved && /app password/i.test(label || "") ? "App Password already configured" : "Saved securely";
        el("emPasswordHelp").innerText = saved
            ? "A credential is stored encrypted and is never displayed again. Leave it as is, or click Replace credential to enter a new one."
            : "Your email provider credential / App Password may be required for authenticated sending. For security reasons this application cannot generate or retrieve it — it is stored encrypted and never shown again.";
    }

    /** Custom SMTP = manual host/port; a known provider = standard values (still editable if the provider changes them). */
    function applyProviderMode(code) {
        var custom = code === "CUSTOM";
        el("emHost").placeholder = custom ? "e.g. mail.yourdomain.com" : "filled by Auto Configure";
        el("emAutoLabel").innerText = code === "AUTO" ? "Auto Configure Email" : "Load Provider Settings";
        // The helper is Google-specific, so it only appears for Gmail / Google Workspace.
        el("emGenerateBtn").classList.toggle("d-none", code !== "GMAIL");
    }

    /** Opens the Google App Password helper. Nothing is prefilled and nothing is persisted here. */
    function openAppPasswordHelper() {
        var input = el("gappInput");
        input.value = ""; input.type = "password";
        el("gappToggle").querySelector("i").className = "fas fa-eye";
        el("gappSave").disabled = true;
        el("gappHint").className = "form-text";
        el("gappHint").innerText = "Spaces are ignored — paste it exactly as Google shows it.";
        var account = el("emUsername").value.trim() || el("emSenderEmail").value.trim();
        el("gappAccountHint").innerText = account ? " (" + account + ")" : "";
        gappModal.show();
    }

    /** Hands the pasted value to the existing credential field; the existing Save flow encrypts it. */
    function applyAppPassword() {
        var value = el("gappInput").value.replace(/\s+/g, "");
        if (!value) { el("gappSave").disabled = true; return; }
        setCredentialState(false, el("emPasswordLabel").innerText);
        el("emPassword").value = value;
        el("emPassword").classList.remove("is-invalid");
        el("emError").classList.add("d-none");
        emailHasSavedCred = false;
        gappModal.hide();
        toast("App Password pasted into the credential field. Click Save Email Settings to store it securely.", true);
    }

    function emailFormBody() {
        return {
            provider: el("emProvider").value,
            senderName: el("emSenderName").value.trim(),
            senderEmail: el("emSenderEmail").value.trim(),
            replyTo: el("emReplyTo") ? el("emReplyTo").value.trim() : "",
            sendingDomain: el("emSendingDomain") ? el("emSendingDomain").value.trim() : "",
            dkimSelector: el("emDkimSelector") ? el("emDkimSelector").value.trim() : "",
            smtpHost: el("emHost").value.trim(),
            smtpPort: Number(el("emPort").value),
            smtpUsername: el("emUsername").value.trim(),
            smtpPassword: el("emPasswordGroup").classList.contains("d-none") ? "" : el("emPassword").value,
            securityMode: el("emSecurity").value,
            enabled: el("emEnabled").checked,
            testRecipient: el("emTestRecipient").value.trim()
        };
    }

    function showEmError(msg) { var e = el("emError"); e.innerText = msg; e.classList.remove("d-none"); }

    /** The credential box is empty and nothing usable is stored: say what to do and put the cursor there. */
    function askForCredential() {
        var label = (el("emPasswordLabel").innerText || "SMTP credential").replace(/\s*\(.*\)\s*$/, "");
        setCredentialState(false, el("emPasswordLabel").innerText);
        showEmError("Enter the " + label + " in the credential field below, then try again. "
            + "It is required because this SMTP server authenticates with the username " + (el("emUsername").value.trim() || "you set") + ".");
        el("emPassword").focus();
        el("emPassword").classList.add("is-invalid");
    }

    /** "Auto Configure Email": the server's provider registry decides; the form only displays and fills. */
    function autoConfigureEmail() {
        var email = el("emSenderEmail").value.trim(), provider = el("emProvider").value;
        el("emError").classList.add("d-none");
        if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { showEmError("Please enter a valid email address."); el("emSenderEmail").focus(); return; }
        var btn = el("emAutoBtn"), label = el("emAutoLabel"), old = label.innerText;
        btn.disabled = true; label.innerText = "Detecting…";
        api("/admin/email-settings/detect", { method: "POST", body: { email: email, provider: provider } }).then(function (res) {
            btn.disabled = false; label.innerText = old;
            if (!res.success) { showEmError(res.errors ? Object.values(res.errors).join(" ") : res.message); return; }
            applyDetection(res.data);
        });
    }

    function applyDetection(d) {
        emailDetection = d;
        var box = el("emDetect");
        if (d.detected) {
            el("emProvider").value = d.provider;
            el("emHost").value = d.smtpHost || "";
            el("emPort").value = d.smtpPort || "";
            el("emSecurity").value = d.securityMode || "STARTTLS";
            if (d.smtpUsername) el("emUsername").value = d.smtpUsername;
            else if (d.method === "MANUAL") el("emUsername").value = "";
            if (!el("emSenderName").value.trim()) el("emSenderName").value = "Lord Sai Academy";
            // The credential is never touched: an existing saved one stays, a typed one stays.
            setCredentialState(emailHasSavedCred && el("emPasswordGroup").classList.contains("d-none"), d.credentialLabel);
            box.className = "col-12";
            box.innerHTML = '<div class="alert alert-success small mb-0">' +
                '<div class="fw-bold mb-1"><i class="fas fa-check-circle me-1"></i> Email Provider Detected: ' + esc(d.providerLabel) + ' <span class="text-muted fw-normal">(' + esc(d.providerType) + ', via ' + (d.method === "MX" ? "domain mail records" : d.method === "DOMAIN" ? "email domain" : "your selection") + ')</span></div>' +
                '<div class="fw-bold mb-2"><i class="fas fa-check-circle me-1"></i> SMTP Configuration Automatically Loaded</div>' +
                '<div class="row g-1 mb-2"><div class="col-sm-6"><small class="text-muted">SMTP Host</small><div><code>' + esc(d.smtpHost) + '</code></div></div><div class="col-sm-2"><small class="text-muted">Port</small><div><code>' + esc(d.smtpPort) + '</code></div></div><div class="col-sm-4"><small class="text-muted">Security</small><div><code>' + esc(d.securityMode) + '</code></div></div>' +
                '<div class="col-12"><small class="text-muted">SMTP Username</small><div><code>' + esc(d.smtpUsername || "(issued by the provider — enter it below)") + '</code></div></div></div>' +
                (d.authRequired ? '<div class="mb-1"><i class="fas fa-key me-1"></i> <strong>' + esc(d.credentialLabel) + '</strong> is required to authenticate.</div>' : "") +
                '<ul class="mb-0 ps-3">' + d.instructions.map(function (i) { return '<li>' + esc(i) + '</li>'; }).join("") + '</ul></div>';
        } else {
            el("emProvider").value = "CUSTOM";
            if (d.smtpUsername && !el("emUsername").value.trim()) el("emUsername").value = d.smtpUsername;
            setCredentialState(emailHasSavedCred && el("emPasswordGroup").classList.contains("d-none"), d.credentialLabel);
            box.className = "col-12";
            box.innerHTML = '<div class="alert alert-warning small mb-0"><div class="fw-bold mb-1"><i class="fas fa-exclamation-triangle me-1"></i> ' + esc(d.message) + '</div>' +
                (d.mxRecords && d.mxRecords.length ? '<div class="text-muted">Mail records found for the domain: <code>' + d.mxRecords.map(esc).join("</code>, <code>") + '</code> — they do not match a known provider.</div>' : "") +
                '<ul class="mb-0 ps-3 mt-1">' + d.instructions.map(function (i) { return '<li>' + esc(i) + '</li>'; }).join("") + '</ul></div>';
            el("emHost").focus();
        }
        box.classList.remove("d-none");
        applyProviderMode(el("emProvider").value);
        toast(d.detected ? "✓ " + d.providerLabel + " configuration loaded." : "Provider not detected — choose Custom SMTP.", d.detected);
    }

    function testSmtpConnection() {
        var b = emailFormBody(), out = el("emConnResult");
        el("emError").classList.add("d-none");
        if (!b.smtpHost) { showEmError("Enter the SMTP host first (or click Auto Configure Email)."); return; }
        if (!(b.smtpPort >= 1 && b.smtpPort <= 65535)) { showEmError("SMTP port must be between 1 and 65535."); return; }
        if (b.smtpUsername && !b.smtpPassword && !emailHasSavedCred) { askForCredential(); return; }
        var btn = el("emConnBtn"), label = el("emConnLabel");
        btn.disabled = true; label.innerText = "Connecting…";
        out.className = "small mt-2 text-muted"; out.innerText = "Opening an SMTP session with " + b.smtpHost + ":" + b.smtpPort + " (up to 30 seconds)…"; out.classList.remove("d-none");
        api("/admin/email-settings/test-connection", { method: "POST", body: { smtpHost: b.smtpHost, smtpPort: b.smtpPort, securityMode: b.securityMode, smtpUsername: b.smtpUsername, smtpPassword: b.smtpPassword } }).then(function (r) {
            btn.disabled = false; label.innerText = "Test SMTP Connection";
            var d = r.data || {};
            if (r.success) {
                out.className = "small mt-2 text-success";
                out.innerHTML = '<div><i class="fas fa-check-circle me-1"></i> SMTP connection successful</div>' + (b.smtpUsername ? '<div><i class="fas fa-check-circle me-1"></i> Authentication successful</div>' : "") + '<div class="text-muted">' + (d.elapsedMs != null ? d.elapsedMs + " ms" : "") + ' · ✓ Email configuration verified successfully.</div>';
            } else {
                out.className = "small mt-2 text-danger";
                out.innerHTML = '<div><i class="fas fa-times-circle me-1"></i> ' + (d.connected ? "SMTP connection successful, but" : "SMTP connection failed") + (d.connected ? " authentication failed" : "") + '</div><div>' + esc(r.errors ? Object.values(r.errors).join(" ") : r.message) + '</div>';
            }
            toast(r.success ? "✓ SMTP connection verified." : "✗ SMTP connection failed.", r.success);
            api("/admin/email-settings").then(function (res) { if (res.success) renderEmailStatusCard(res.data); });
        });
    }

    function saveEmailSettings(e) {
        e.preventDefault();
        var body = emailFormBody();
        var errBox = el("emError"); errBox.classList.add("d-none");
        if (!body.senderEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.senderEmail)) { showEmError("Please enter a valid email address."); return; }
        if (!body.smtpHost) { showEmError("SMTP host is missing — click Auto Configure Email or choose Custom SMTP and enter it."); return; }
        if (!(body.smtpPort >= 1 && body.smtpPort <= 65535)) { showEmError("SMTP port must be between 1 and 65535."); return; }
        if (body.smtpUsername && !body.smtpPassword && !emailHasSavedCred) { askForCredential(); return; }
        var btn = el("emSaveBtn"); btn.disabled = true;
        api("/admin/email-settings", { method: "PUT", body: body }).then(function (r) {
            btn.disabled = false;
            if (!r.success) { showEmError(r.errors ? Object.values(r.errors).join(" ") : r.message); return; }
            toast("✓ Email configuration saved successfully.", true); renderEmailSettings(r.data);
        });
    }

    function sendTestEmail() {
        var recipient = el("emTestRecipient").value.trim();
        var out = el("emTestResult");
        if (recipient && recipient.indexOf("@") < 1) { out.className = "small mt-2 text-danger"; out.innerText = "Enter a valid test recipient email address."; out.classList.remove("d-none"); return; }
        var btn = el("emTestBtn"), label = el("emTestLabel");
        btn.disabled = true; label.innerText = "Sending…";
        out.className = "small mt-2 text-muted"; out.innerText = "Sending the test email through the saved settings (this can take up to 30 seconds)…"; out.classList.remove("d-none");
        api("/admin/email-settings/test", { method: "POST", body: { recipient: recipient } }).then(function (r) {
            btn.disabled = false; label.innerText = "Send Test Email";
            out.className = "small mt-2 " + (r.success ? "text-success" : "text-danger");
            out.innerText = (r.success ? "✓ " : "✗ ") + (r.errors ? Object.values(r.errors).join(" ") : (r.message || (r.success ? "Test email sent successfully." : "Test email could not be sent.")));
            toast(out.innerText, r.success);
            api("/admin/email-settings").then(function (res) { if (res.success) renderEmailStatusCard(res.data); });
        });
    }

    function removeEmailSettings() {
        var withGoogle = emailGoogle && emailGoogle.connected;
        if (!confirmAction("Remove the saved email configuration?"
            + (withGoogle ? "\n\nThe connected Google account will also be disconnected and its access revoked." : "")
            + "\n\nSystem emails will fall back to the server environment variables, or stop being sent if none are configured.")) return;
        api("/admin/email-settings", { method: "DELETE" }).then(function (r) { if (handle(r)) renderEmailSettings(r.data); });
    }

    function checkDomainDns() {
        var domain = (el("emSendingDomain") ? el("emSendingDomain").value : "").trim();
        if (!domain) {
            var email = (el("emSenderEmail") ? el("emSenderEmail").value : "").trim();
            if (email.indexOf("@") !== -1) {
                domain = email.split("@")[1].trim();
            }
        }
        if (!domain) {
            showEmError("Enter a verified sending domain or sender email to verify DNS records.");
            if (el("emSendingDomain")) el("emSendingDomain").focus();
            return;
        }
        var selector = (el("emDkimSelector") ? el("emDkimSelector").value : "").trim();
        var btn = el("emCheckDnsBtn");
        var resBox = el("emDnsResults");
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Verifying DNS…';
        resBox.innerHTML = '<div class="text-muted"><span class="spinner-border spinner-border-sm me-1"></span> Querying SPF, DKIM, DMARC, and MX DNS records for <strong>' + esc(domain) + '</strong>…</div>';

        var url = "/admin/email-settings/domain-check?domain=" + encodeURIComponent(domain);
        if (selector) {
            url += "&selector=" + encodeURIComponent(selector);
        }

        api(url).then(function (r) {
            btn.disabled = false;
            btn.innerHTML = '<i class="fas fa-search me-1"></i> Verify Domain DNS';
            if (!r.success || !r.data) {
                resBox.innerHTML = '<div class="alert alert-danger small py-2 mb-0">' + esc(r.message || "Failed to query DNS records.") + '</div>';
                toast(r.message || "DNS check failed.", false);
                return;
            }
            var d = r.data;
            var checks = d.checks || [d.spf, d.dkim, d.dmarc, d.mx].filter(Boolean);
            var allGood = d.allPassed != null ? d.allPassed : d.allPassing;
            var statusBadge = function (st) {
                if (st === "PASS") return '<span class="badge bg-success">PASS</span>';
                if (st === "WARNING") return '<span class="badge bg-warning text-dark">WARNING</span>';
                return '<span class="badge bg-danger">FAIL</span>';
            };
            var rowsHtml = checks.map(function (c) {
                var recs = (c.recordsFound && c.recordsFound.length)
                    ? '<div class="mt-1 text-break"><code>' + c.recordsFound.map(esc).join('</code><br><code>') + '</code></div>'
                    : '<div class="mt-1 text-muted fst-italic">None detected</div>';
                var recHelp = c.recommendedRecord
                    ? '<div class="mt-1 small text-secondary">Recommended: <code class="user-select-all">' + esc(c.recommendedRecord) + '</code></div>'
                    : '';
                return '<tr>'
                    + '<td class="fw-bold align-middle" style="width:90px">' + esc(c.recordType || c.checkType) + '</td>'
                    + '<td class="align-middle" style="width:95px">' + statusBadge(c.status) + '</td>'
                    + '<td><div class="fw-semibold">' + esc(c.message) + '</div>' + recs + recHelp + '</td>'
                    + '</tr>';
            }).join("");

            resBox.innerHTML = '<div class="alert ' + (allGood ? 'alert-success' : 'alert-warning') + ' py-2 mb-2">'
                + '<div class="fw-bold mb-1">' + (allGood ? '<i class="fas fa-check-circle me-1"></i> Domain DNS is fully aligned' : '<i class="fas fa-exclamation-triangle me-1"></i> Domain DNS requires attention') + '</div>'
                + '<div>' + esc(d.summary) + '</div>'
                + '</div>'
                + '<div class="table-responsive"><table class="table table-sm table-bordered mb-0 bg-white">'
                + '<thead class="table-light"><tr><th>Check</th><th>Status</th><th>Details &amp; Records</th></tr></thead>'
                + '<tbody>' + rowsHtml + '</tbody>'
                + '</table></div>';
            toast(allGood ? "✓ Domain DNS records verified successfully." : "DNS check completed with warnings/failures.", allGood);
        }).catch(function () {
            btn.disabled = false;
            btn.innerHTML = '<i class="fas fa-search me-1"></i> Verify Domain DNS';
            resBox.innerHTML = '<div class="alert alert-danger small py-2 mb-0">DNS check failed. Please check network connectivity.</div>';
        });
    }

    // ---- global search ---------------------------------------------------------------------

    var searchTimer;
    function globalSearch(q) {
        var box = el("searchResults");
        if (q.length < 2) { box.style.display = "none"; return; }
        api("/admin/search?q=" + encodeURIComponent(q)).then(function (res) {
            if (!res.success) return;
            var r = res.data, html = "";
            r.students.forEach(function (s) { html += '<a href="#" data-student="' + s.id + '"><i class="fas fa-user-graduate text-primary me-2"></i>' + esc(s.fullName) + ' <small class="text-muted">' + esc(s.studentId || "") + ' · ' + esc(s.email) + '</small></a>'; });
            r.courses.forEach(function (c) { html += '<a href="#" data-goto="courses"><i class="fas fa-book text-success me-2"></i>' + esc(c.courseName) + ' <small class="text-muted">' + esc(c.courseCode) + '</small></a>'; });
            r.payments.forEach(function (p) { html += '<a href="#" data-goto="payments"><i class="fas fa-rupee-sign text-warning me-2"></i>' + esc(p.orderRef) + ' <small class="text-muted">' + esc(p.customerEmail) + ' · ' + inr(p.amount) + ' · ' + esc(p.status) + '</small></a>'; });
            box.innerHTML = html || '<a href="#" class="text-muted">No matches</a>';
            box.style.display = "block";
            bindStudentLinks(box);
            box.querySelectorAll("[data-goto]").forEach(function (a) { a.addEventListener("click", function (e) { e.preventDefault(); show(a.dataset.goto); box.style.display = "none"; }); });
        });
    }

    // ---- boot ------------------------------------------------------------------------------

    document.addEventListener("DOMContentLoaded", function () {
        if (!LSI_Auth.isAuthenticated()) return;
        formModal = new bootstrap.Modal(el("formModal"));
        el("formModalForm").addEventListener("submit", function (e) {
            e.preventDefault();
            if (!formSubmitHandler) return;
            var btn = el("formModalSubmit"); btn.disabled = true;
            Promise.resolve(formSubmitHandler(fieldVals())).finally(function () { btn.disabled = false; });
        });
        LSI_Auth.refreshProfile().then(function (p) { if (p) el("adminName").innerText = p.name; });

        document.querySelectorAll(".adm-nav a[data-view]").forEach(function (a) {
            a.addEventListener("click", function (e) { e.preventDefault(); location.hash = a.dataset.view; show(a.dataset.view); });
        });
        el("sidebarToggle").addEventListener("click", function () { el("sidebar").classList.toggle("open"); });
        el("sidebarBackdrop").addEventListener("click", function () { el("sidebar").classList.remove("open"); });

        el("studentSearch").addEventListener("input", function () { clearTimeout(searchTimer); searchTimer = setTimeout(function () { loadStudents(0); }, 350); });
        el("studentStatusFilter").addEventListener("change", function () { loadStudents(0); });
        el("curriculumCourse").addEventListener("change", function () { loadCurriculum(this.value); });
        el("addModuleBtn").addEventListener("click", function () {
            var courseId = el("curriculumCourse").value;
            if (!courseId) { toast("Select a course first.", false); return; }
            openForm("Add Module", input("moduleName", "Module name", "", { required: true, maxlength: 200 }) + input("description", "Description", "", { type: "textarea" }), function (v) {
                return api("/admin/courses/" + courseId + "/modules", { method: "POST", body: v }).then(function (r) { if (handle(r)) { formModal.hide(); loadCurriculum(courseId); } else formError(r.message); });
            });
        });
        el("enrollCourseFilter").addEventListener("change", function () { loadEnrollments(0); });
        el("paymentFilters").querySelectorAll("button").forEach(function (b) {
            b.addEventListener("click", function () { el("paymentFilters").querySelectorAll("button").forEach(function (x) { x.classList.remove("active"); }); b.classList.add("active"); state.paymentStatus = b.dataset.status; loadPayments(0); });
        });
        el("doubtStatusFilter").addEventListener("change", loadDoubts);
        el("globalSearch").addEventListener("input", function () { var q = this.value.trim(); clearTimeout(searchTimer); searchTimer = setTimeout(function () { globalSearch(q); }, 300); });
        document.addEventListener("click", function (e) { if (!e.target.closest("#searchResults") && !e.target.closest("#globalSearch")) el("searchResults").style.display = "none"; });
        el("gatewayForm").addEventListener("submit", saveGateway);
        el("whatsappForm").addEventListener("submit", saveWhatsAppSettings);
        el("waTestConnBtn").addEventListener("click", testWhatsAppConnection);
        el("whatsappTestForm").addEventListener("submit", sendWhatsAppTest);
        el("emailForm").addEventListener("submit", saveEmailSettings);
        el("emTestBtn").addEventListener("click", sendTestEmail);
        el("emRemoveBtn").addEventListener("click", removeEmailSettings);
        if (el("emCheckDnsBtn")) el("emCheckDnsBtn").addEventListener("click", checkDomainDns);
        el("emAutoBtn").addEventListener("click", autoConfigureEmail);
        el("emConnBtn").addEventListener("click", testSmtpConnection);
        el("emProvider").addEventListener("change", function () { applyProviderMode(this.value); if (this.value !== "AUTO" && el("emSenderEmail").value.trim()) autoConfigureEmail(); });
        el("emSenderEmail").addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); autoConfigureEmail(); } });
        el("emReplaceCred").addEventListener("click", function () { setCredentialState(false, el("emPasswordLabel").innerText); el("emPassword").focus(); });
        el("emPassword").addEventListener("input", function () { this.classList.remove("is-invalid"); el("emError").classList.add("d-none"); });
        gappModal = new bootstrap.Modal(el("gappModal"));
        el("emGenerateBtn").addEventListener("click", openAppPasswordHelper);
        el("gappSave").addEventListener("click", applyAppPassword);
        el("gappInput").addEventListener("input", function () {
            var v = this.value.replace(/\s+/g, "");
            el("gappSave").disabled = v.length === 0;
            var hint = el("gappHint");
            if (!v.length) { hint.className = "form-text"; hint.innerText = "Spaces are ignored — paste it exactly as Google shows it."; }
            else if (v.length === 16) { hint.className = "form-text text-success"; hint.innerText = "✓ 16 characters — that matches a Google App Password."; }
            else { hint.className = "form-text text-warning"; hint.innerText = v.length + " characters. Google App Passwords are 16 — check you copied all of it."; }
        });
        el("gappInput").addEventListener("keydown", function (e) { if (e.key === "Enter" && !el("gappSave").disabled) { e.preventDefault(); applyAppPassword(); } });
        el("gappToggle").addEventListener("click", function () {
            var i = el("gappInput"); i.type = i.type === "password" ? "text" : "password";
            this.querySelector("i").className = i.type === "password" ? "fas fa-eye" : "fas fa-eye-slash";
        });
        // Never leave the pasted value sitting in the DOM after the dialog closes.
        el("gappModal").addEventListener("hidden.bs.modal", function () { el("gappInput").value = ""; el("gappSave").disabled = true; });
        el("emTogglePw").addEventListener("click", function () {
            var p = el("emPassword"); p.type = p.type === "password" ? "text" : "password";
            this.querySelector("i").className = p.type === "password" ? "fas fa-eye" : "fas fa-eye-slash";
        });
        el("storyCategoryFilter").addEventListener("change", loadStories);
        bindReviewModal();

        // Blog list filters & search
        if (el("blogSiteFilter")) el("blogSiteFilter").addEventListener("change", function () {
            state.blogSite = this.value;
            loadBlogCategoryFilterOptions();
            loadBlogs(0);
        });
        if (el("blogCategoryFilter")) el("blogCategoryFilter").addEventListener("change", function () {
            state.blogCategory = this.value;
            loadBlogs(0);
        });
        if (el("blogStatusFilter")) el("blogStatusFilter").addEventListener("change", function () {
            state.blogStatus = this.value;
            loadBlogs(0);
        });
        if (el("blogSearchInput")) el("blogSearchInput").addEventListener("input", function () {
            state.blogSearch = this.value.trim();
            clearTimeout(searchTimer);
            searchTimer = setTimeout(function () { loadBlogs(0); }, 350);
        });
        if (el("blogSearchBtn")) el("blogSearchBtn").addEventListener("click", function () {
            state.blogSearch = el("blogSearchInput").value.trim();
            loadBlogs(0);
        });

        // Category site filter
        if (el("catSiteFilter")) el("catSiteFilter").addEventListener("change", function () {
            state.catSite = this.value;
            loadBlogCategories();
        });

        // Blog form site change
        if (el("blogFormSiteInput")) el("blogFormSiteInput").addEventListener("change", function () {
            populateCategoriesForForm(this.value, null);
        });

        // Auto slug generation from title
        if (el("blogFormTitleInput")) el("blogFormTitleInput").addEventListener("input", function () {
            if (!manualSlugEdit) {
                var s = this.value.toLowerCase().trim()
                    .replace(/[^a-z0-9\s-]/g, "")
                    .replace(/\s+/g, "-")
                    .replace(/-+/g, "-");
                el("blogFormSlugInput").value = s;
            }
        });
        if (el("blogFormSlugInput")) el("blogFormSlugInput").addEventListener("input", function () {
            manualSlugEdit = true;
        });

        // Blog save draft & publish buttons
        if (el("blogSaveDraftBtn")) el("blogSaveDraftBtn").addEventListener("click", function () { saveBlog("DRAFT"); });
        if (el("blogPublishBtn")) el("blogPublishBtn").addEventListener("click", function () { saveBlog("PUBLISHED"); });

        // Blog image file preview & remove
        if (el("blogFormImageFile")) el("blogFormImageFile").addEventListener("change", function () {
            var file = this.files[0];
            if (file) {
                var reader = new FileReader();
                reader.onload = function (e) {
                    el("blogImagePreviewBox").innerHTML = '<img src="' + e.target.result + '" style="max-height:140px;max-width:100%;border-radius:6px;" alt="">';
                    el("blogFormImageRemoveBtn").classList.remove("d-none");
                };
                reader.readAsDataURL(file);
            }
        });
        if (el("blogFormImageRemoveBtn")) el("blogFormImageRemoveBtn").addEventListener("click", function () {
            el("blogFormImageFile").value = "";
            el("blogFormImagePath").value = "";
            el("blogImagePreviewBox").innerHTML = '<span class="text-muted small">No image uploaded</span>';
            this.classList.add("d-none");
        });

        // Blog editor mode tabs (Write vs Preview)
        if (el("blogEditorModeWrite")) el("blogEditorModeWrite").addEventListener("click", function () {
            this.classList.add("active");
            el("blogEditorModePreview").classList.remove("active");
            el("blogFormContentInput").classList.remove("d-none");
            el("blogFormContentPreview").classList.add("d-none");
        });
        if (el("blogEditorModePreview")) el("blogEditorModePreview").addEventListener("click", function () {
            this.classList.add("active");
            el("blogEditorModeWrite").classList.remove("active");
            el("blogFormContentPreview").innerHTML = el("blogFormContentInput").value;
            el("blogFormContentInput").classList.add("d-none");
            el("blogFormContentPreview").classList.remove("d-none");
        });

        // Blog editor toolbar commands
        if (el("blogEditorToolbar")) el("blogEditorToolbar").querySelectorAll("[data-cmd]").forEach(function (b) {
            b.addEventListener("click", function () {
                var cmd = b.dataset.cmd;
                if (cmd === "h2") insertEditorTag("<h2>", "</h2>", "Heading 2");
                else if (cmd === "h3") insertEditorTag("<h3>", "</h3>", "Heading 3");
                else if (cmd === "p") insertEditorTag("<p>", "</p>", "Paragraph text");
                else if (cmd === "bold") insertEditorTag("<strong>", "</strong>", "bold text");
                else if (cmd === "italic") insertEditorTag("<em>", "</em>", "italic text");
                else if (cmd === "underline") insertEditorTag("<u>", "</u>", "underlined text");
                else if (cmd === "ul") insertEditorTag("<ul>\n  <li>", "</li>\n  <li>Second item</li>\n</ul>", "List item");
                else if (cmd === "ol") insertEditorTag("<ol>\n  <li>", "</li>\n  <li>Second step</li>\n</ol>", "Step 1");
                else if (cmd === "quote") insertEditorTag("<blockquote>\n  ", "\n</blockquote>", "Quote text here");
                else if (cmd === "link") {
                    var url = prompt("Enter URL:", "https://");
                    if (url) insertEditorTag('<a href="' + esc(url) + '" target="_blank">', '</a>', "link text");
                } else if (cmd === "img") {
                    var imgUrl = prompt("Enter image URL:", "img/service-1.jpg");
                    if (imgUrl) insertEditorTag('<img src="' + esc(imgUrl) + '" alt="', '" class="img-fluid rounded my-3">', "Image description");
                }
            });
        });

        if (el("blogCategoryFilter")) loadBlogCategoryFilterOptions();

        ["invProduct", "invStatus", "invFrom", "invTo"].forEach(function (id) { el(id).addEventListener("change", function () { loadInvoices(0); }); });
        ["exAppStatus", "exAppCourse"].forEach(function (id) { el(id).addEventListener("change", function () { loadExamApplications(0); }); });
        var exAppTimer; el("exAppSearch").addEventListener("input", function () { clearTimeout(exAppTimer); exAppTimer = setTimeout(function () { loadExamApplications(0); }, 350); });
        el("examCourseFilter").addEventListener("change", loadExams);
        ["exResCourse", "exResExam", "exResPassed"].forEach(function (id) { el(id).addEventListener("change", function () { loadExamResults(0); }); });
        var exResTimer; el("exResSearch").addEventListener("input", function () { clearTimeout(exResTimer); exResTimer = setTimeout(function () { loadExamResults(0); }, 350); });
        el("certCourse").addEventListener("change", function () { loadCertificates(0); });
        var certTimer; el("certSearch").addEventListener("input", function () { clearTimeout(certTimer); certTimer = setTimeout(function () { loadCertificates(0); }, 350); });
        var invTimer; el("invSearch").addEventListener("input", function () { clearTimeout(invTimer); invTimer = setTimeout(function () { loadInvoices(0); }, 350); });

        el("changePasswordForm").addEventListener("submit", function (e) {
            e.preventDefault();
            if (el("cpNew").value !== el("cpConfirm").value) { toast("New passwords do not match.", false); return; }
            LSI_Auth.changePassword(el("cpCurrent").value, el("cpNew").value).then(function (r) {
                if (handle(r)) setTimeout(function () { LSI_Auth.logout(); }, 1200);
            });
        });

        el("changeUserIdForm").addEventListener("submit", function (e) {
            e.preventDefault();
            var session = LSI_Auth.getSession(), email = session && session.profile ? session.profile.email : "";
            var current = email.split("@")[0];
            LSI_Auth.changeUserId(current, el("adminNewUserId").value, el("adminUserIdPassword").value).then(function (r) {
                if (handle(r)) {
                    var changedId = el("adminNewUserId").value.trim().toLowerCase();
                    el("adminUserIdPassword").value = "";
                    el("adminCurrentUserId").value = changedId;
                    var profile = LSI_Auth.getCurrentUser();
                    if (profile) profile.email = String(profile.email).replace(/^[^@]+/, changedId);
                }
            });
        });
        var adminSession = LSI_Auth.getSession();
        if (adminSession && adminSession.profile && adminSession.profile.email) {
            el("adminCurrentUserId").value = adminSession.profile.email.split("@")[0];
        }

        initShareMarketPurchaseToggle();
        window.addEventListener("storage", function (e) {
            if (e.key === SM_PURCHASE_STORAGE_KEY) {
                updateShareMarketPurchaseUI(isShareMarketPurchaseEnabled());
            }
        });

        // A return trip from Google lands here; go straight to Email Settings and say what happened.
        var googleOutcome = consumeGoogleCallbackResult();
        var initial = (location.hash || "#dashboard").replace("#", "");
        if (googleOutcome) { initial = "email"; }
        show(el("view-" + initial) ? initial : "dashboard");
        if (googleOutcome) { reportGoogleOutcome(googleOutcome); }
    });

    window.Admin = { show: show, loadDashboard: loadDashboard, openStudentForm: openStudentForm, openCourseForm: openCourseForm,
        openStoryForm: openStoryForm, openSliderForm: openSliderForm, openReviewForm: openReviewForm, loadReviews: loadReviews, openManualEnroll: openManualEnroll, loadAudit: loadAudit, loadGateway: loadGateway, loadEmailSettings: loadEmailSettings, loadWhatsAppSettings: loadWhatsAppSettings,
        openBlogForm: openBlogForm, openCategoryForm: openCategoryForm, loadBlogs: loadBlogs, loadBlogCategories: loadBlogCategories,
        openEbookForm: openEbookForm, loadEbooks: loadEbooks, loadInvoices: loadInvoices, exportReport: exportReport, printReport: printReport,
        loadExamApplications: loadExamApplications, openExamForm: openExamForm, closeExamQuestions: closeExamQuestions, loadExamResults: loadExamResults,
        openCertificateTemplateForm: openCertificateTemplateForm, useDefaultCertificateTemplate: useDefaultCertificateTemplate, previewCertificateTemplate: previewCertificateTemplate };
})(window);
