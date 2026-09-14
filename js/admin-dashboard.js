/**
 * LORD SAI ACADEMY — MASTER ADMIN DASHBOARD (js/admin-dashboard.js)
 * All data comes from /api/admin/**. Every destructive action asks for confirmation.
 */
(function (window) {
    "use strict";

    var api = LSI_Auth.api;
    var formModal, formSubmitHandler = null;
    var courseCache = [];
    var state = { site: "ACADEMY", paymentStatus: "", studentPage: 0, enrollPage: 0, paymentPage: 0, auditPage: 0, blogPage: 0, blogSite: "", blogCategory: "", blogStatus: "", blogSearch: "", catSite: "" };

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
            STUDENTS: "info text-dark", TEACHERS: "primary", PARENTS: "success", PUBLISHED: "success", UNPUBLISHED: "secondary", APPROVED: "success", DECLINED: "danger" };
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
        var loaders = { dashboard: loadDashboard, students: function () { loadStudents(0); }, courses: loadCourses, curriculum: initCurriculum,
            enrollments: function () { loadEnrollments(0); }, payments: function () { loadPayments(0); }, stories: loadStories, reviews: function () { loadReviews(0); },
            mentoring: loadMentoring, content: loadContent, "mf-slider": loadSlider, blogs: function () { loadBlogs(0); }, "blog-categories": loadBlogCategories,
            gateway: loadGateway, audit: function () { loadAudit(0); }, settings: function () { el("settingsApiBase").innerText = LSI_Auth.apiBase; } };
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
                ["Disabled Students", s.disabledStudents, "fa-user-slash", "rgba(239,68,68,.12)", "#ef4444"]
            ];
            renderPaymentStatus(s);
            updatePendingReviews(s.pendingReviews);
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
            (existing ? "" : '<div class="alert alert-info small py-2">A password setup link will be emailed to the student. No password is shown to you.</div>');
        openForm(existing ? "Edit Student" : "Add Student", f, function (v) {
            return api(existing ? "/admin/students/" + existing.id : "/admin/students", { method: existing ? "PUT" : "POST", body: v }).then(function (res) {
                if (handle(res)) { formModal.hide(); existing ? openStudent(existing.id) : loadStudents(0); }
                else formError(res.errors ? Object.values(res.errors).join(" ") : res.message);
            });
        });
    }

    function openStudent(id) {
        show("student-detail");
        el("studentDetail").innerHTML = '<div class="text-muted">Loading...</div>';
        Promise.all([api("/admin/students/" + id), api("/admin/students/" + id + "/enrollments"), api("/admin/students/" + id + "/payments"),
                     api("/admin/students/" + id + "/sessions")]).then(function (r) {
            if (!r[0].success) { el("studentDetail").innerHTML = '<div class="alert alert-danger">' + esc(r[0].message) + '</div>'; return; }
            var s = r[0].data, enr = r[1].data || [], pay = r[2].data || [], ses = r[3].data || [];
            var disabled = s.accountStatus === "DISABLED";
            el("studentDetail").innerHTML =
                '<div class="adm-card p-4 mb-3"><div class="d-flex flex-wrap justify-content-between align-items-start gap-3">' +
                '<div><h4 class="fw-bold mb-1">' + esc(s.fullName) + ' ' + badge(s.accountStatus) + '</h4>' +
                '<div class="text-muted small">Student ID <strong class="font-monospace text-dark">' + esc(s.studentId || "—") + '</strong> · ' + esc(s.email) + ' · ' + esc(s.mobile || "") + '</div>' +
                '<div class="text-muted small">Batch: ' + esc(s.batch || "—") + ' · Location: ' + esc(s.location || "—") + ' · Registered ' + d(s.registrationDate) + ' · Last login ' + dt(s.lastLoginAt) + '</div></div>' +
                '<div class="d-flex flex-wrap gap-2">' +
                '<button class="btn btn-sm btn-outline-primary" id="sdEdit"><i class="fas fa-edit me-1"></i> Edit</button>' +
                '<button class="btn btn-sm btn-outline-secondary" id="sdReset"><i class="fas fa-key me-1"></i> Send Password Link</button>' +
                '<button class="btn btn-sm btn-outline-warning" id="sdLogout"><i class="fas fa-sign-out-alt me-1"></i> Logout All Devices</button>' +
                '<button class="btn btn-sm ' + (disabled ? "btn-success" : "btn-outline-danger") + '" id="sdToggle">' + (disabled ? '<i class="fas fa-check me-1"></i> Activate' : '<i class="fas fa-ban me-1"></i> Deactivate') + '</button>' +
                '<button class="btn btn-sm btn-outline-danger" id="sdDelete"><i class="fas fa-trash me-1"></i> Delete</button>' +
                '</div></div></div>' +
                '<div class="row g-3">' +
                '<div class="col-lg-6"><div class="adm-card p-3"><div class="d-flex justify-content-between align-items-center mb-2"><h6 class="fw-bold mb-0">Courses & Progress</h6><button class="btn btn-xs btn-primary" id="sdEnroll">+ Enroll in course</button></div>' +
                '<table class="table table-sm mb-0"><thead><tr><th>Course</th><th>Status</th><th>Progress</th><th>Source</th><th></th></tr></thead><tbody>' +
                (enr.map(function (e) { return '<tr><td>' + esc(e.courseName) + '</td><td>' + badge(e.status) + '</td><td>' + e.progressPercent + '% <small class="text-muted">(' + e.completedLessons + '/' + e.totalLessons + ')</small></td><td>' + badge(e.source) + '</td><td>' + enrollmentActions(e) + '</td></tr>'; }).join("") || '<tr><td colspan="5" class="text-muted">Not enrolled in any course.</td></tr>') +
                '</tbody></table></div></div>' +
                '<div class="col-lg-6"><div class="adm-card p-3"><h6 class="fw-bold mb-2">Payment History</h6>' +
                '<table class="table table-sm mb-0"><thead><tr><th>Order</th><th>Course</th><th>Amount</th><th>Status</th><th>Date</th></tr></thead><tbody>' +
                (pay.map(function (p) { return '<tr><td class="font-monospace small">' + esc(p.orderRef) + '</td><td>' + esc(p.courseName) + '</td><td>' + inr(p.amount) + '</td><td>' + badge(p.status) + '</td><td><small>' + dt(p.createdAt) + '</small></td></tr>'; }).join("") || '<tr><td colspan="5" class="text-muted">No payments.</td></tr>') +
                '</tbody></table></div></div>' +
                '<div class="col-lg-6"><div class="adm-card p-3"><h6 class="fw-bold mb-2">Login Sessions</h6>' +
                '<table class="table table-sm mb-0"><thead><tr><th>Device</th><th>IP</th><th>Started</th><th>Last activity</th><th>State</th></tr></thead><tbody>' +
                (ses.slice(0, 10).map(function (x) { return '<tr><td><small class="text-truncate d-inline-block" style="max-width:220px" title="' + esc(x.deviceInfo) + '">' + esc(x.deviceInfo) + '</small></td><td><small>' + esc(x.ipAddress) + '</small></td><td><small>' + dt(x.createdAt) + '</small></td><td><small>' + dt(x.lastActivityAt) + '</small></td><td>' + (x.active ? '<span class="badge bg-success">active</span>' : '<span class="badge bg-secondary">' + esc((x.revokeReason || "ended").toLowerCase()) + '</span>') + '</td></tr>'; }).join("") || '<tr><td colspan="5" class="text-muted">Never logged in.</td></tr>') +
                '</tbody></table></div></div></div>';

            el("sdEdit").onclick = function () { openStudentForm(s); };
            el("sdReset").onclick = function () { if (confirmAction("Email a password link to " + s.email + "? Their current sessions will be ended.")) api("/admin/students/" + id + "/reset-password", { method: "POST" }).then(handle); };
            el("sdLogout").onclick = function () { if (confirmAction("Log this student out of all devices?")) api("/admin/students/" + id + "/force-logout", { method: "POST" }).then(function (r) { if (handle(r)) openStudent(id); }); };
            el("sdToggle").onclick = function () {
                var next = disabled ? "ACTIVE" : "DISABLED";
                if (confirmAction((disabled ? "Activate" : "Deactivate") + " this account?" + (disabled ? "" : " The student will be logged out and cannot sign in."))) api("/admin/students/" + id + "/status?status=" + next, { method: "PATCH" }).then(function (r) { if (handle(r)) openStudent(id); });
            };
            el("sdDelete").onclick = function () { if (confirmAction("Permanently delete " + s.fullName + "? This only works for accounts with no payments or enrollments.")) api("/admin/students/" + id, { method: "DELETE" }).then(function (r) { if (handle(r)) show("students"); }); };
            el("sdEnroll").onclick = function () { openManualEnroll(s); };
            bindEnrollmentActions(el("studentDetail"), function () { openStudent(id); });
        });
    }

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
                return '<tr><td class="font-monospace small">' + esc(x.orderRef) + '</td><td>' + (x.userId ? '<a href="#" data-student="' + x.userId + '">' + esc(x.customerName) + '</a>' : esc(x.customerName)) + '<br><small class="text-muted font-monospace">' + esc(x.studentId || "") + '</small></td><td><small>' + esc(x.customerEmail) + '</small></td><td>' + esc(x.courseName) + '</td><td><strong>' + inr(x.amount) + '</strong></td><td>' + badge(x.paymentMode || "RAZORPAY") + '</td><td>' + esc(x.paymentMethod || "—") + '</td>' +
                    '<td class="font-monospace"><small>' + esc(x.razorpayOrderId) + '</small></td><td class="font-monospace"><small>' + esc(x.razorpayPaymentId || "—") + '</small></td><td>' + badge(x.status) + (x.failureReason ? '<br><small class="text-danger">' + esc(x.failureReason) + '</small>' : '') + '</td><td><small>' + dt(x.createdAt) + '</small></td></tr>';
            }).join("") || '<tr><td colspan="11" class="text-muted text-center py-4">No payments.</td></tr>';
            el("paymentsCount").innerText = p.totalElements + " payment(s)";
            pager("paymentsPager", p, loadPayments);
            bindStudentLinks(el("paymentsBody"));
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
        var f = input("category", "Category", x ? x.category : "STUDENTS", { type: "select", options: STORY_CATEGORIES }) +
            input("title", "Title", x && x.title, { required: true, maxlength: 200, placeholder: "e.g. Rahul Sharma — From beginner to consistent swing trader" }) +
            input("personName", "Person name", x && x.personName, { maxlength: 150 }) +
            input("location", "Location / batch", x && x.location, { maxlength: 150, placeholder: "e.g. From Pune, Maharashtra" }) +
            input("description", "Description", x && x.description, { type: "textarea", rows: 4 }) +
            input("videoUrl", "YouTube embed link (optional)", x && x.videoUrl, { maxlength: 500, placeholder: "https://www.youtube.com/embed/VIDEO_ID", help: "Leave blank if you will upload a video file instead. An uploaded file always takes priority." }) +
            (x ? "" : '<div class="alert alert-info small py-2">After saving, upload the video and a thumbnail, then click Publish to show it on the website.</div>');
        openForm(x ? "Edit Story" : "Add Success Story", f, function (v) {
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

    // ---- website content -------------------------------------------------------------------

    function loadContent() {
        api("/admin/content/" + state.site).then(function (res) {
            if (!handle(res, false)) return;
            var items = res.data || [];
            el("contentBody").innerHTML = items.map(function (c) {
                var val = c.contentType === "IMAGE_PATH" && c.contentValue
                    ? '<img src="' + esc(c.contentValue.indexOf("images/") === 0 ? LSI_Auth.apiBase + "/public/" + c.contentValue : c.contentValue) + '" style="max-height:60px;border-radius:6px;">'
                    : '<div class="text-truncate" style="max-width:420px;" title="' + esc(c.contentValue) + '">' + esc(c.contentValue || "") + '</div>';
                return '<tr><td><strong>' + esc(c.label) + '</strong><br><code class="small">' + esc(c.contentKey) + '</code></td><td>' + val + '</td><td><span class="badge bg-light text-dark border">' + esc(c.contentType) + '</span></td><td><small>' + dt(c.updatedAt) + (c.updatedBy ? "<br>" + esc(c.updatedBy) : "") + '</small></td>' +
                    '<td class="text-nowrap"><div class="btn-group btn-group-sm">' +
                    '<button class="btn btn-outline-primary" data-c="edit" data-id="' + c.id + '"><i class="fas fa-edit"></i></button>' +
                    (c.contentType === "IMAGE_PATH" ? '<button class="btn btn-outline-primary" data-c="image" data-id="' + c.id + '"><i class="fas fa-image"></i></button>' : "") +
                    '<button class="btn btn-outline-danger" data-c="delete" data-id="' + c.id + '"><i class="fas fa-trash"></i></button></div></td></tr>';
            }).join("") || '<tr><td colspan="5" class="text-muted text-center py-4">No content items for this site yet. Click "Add Content Item".</td></tr>';
            el("contentBody").querySelectorAll("[data-c]").forEach(function (b) {
                var c = items.find(function (x) { return String(x.id) === b.dataset.id; });
                b.addEventListener("click", function () { contentAction(b.dataset.c, c); });
            });
        });
    }

    function openContentForm(c) {
        var f = input("label", "Label (what this is, for humans)", c && c.label, { required: true, maxlength: 200, placeholder: "e.g. Home page hero heading" }) +
            input("contentKey", "Key (used by the website)", c && c.contentKey, { required: true, maxlength: 150, placeholder: "e.g. home.hero.title" }) +
            input("contentType", "Type", c ? c.contentType : "TEXT", { type: "select", options: [{ value: "TEXT", label: "Text" }, { value: "HTML", label: "HTML" }, { value: "URL", label: "Link / URL" }, { value: "IMAGE_PATH", label: "Image" }] }) +
            input("contentValue", "Value", c && c.contentValue, { type: "textarea", rows: 4 });
        openForm(c ? "Edit Content Item" : "Add Content Item", f, function (v) {
            return api("/admin/content/" + state.site, { method: "PUT", body: v }).then(function (r) { if (handle(r)) { formModal.hide(); loadContent(); } else formError(r.errors ? Object.values(r.errors).join(" ") : r.message); });
        });
        if (c) el("formModalForm").querySelector('[name="contentKey"]').readOnly = true;
    }

    function contentAction(act, c) {
        if (act === "edit") return openContentForm(c);
        if (act === "delete") { if (confirmAction("Delete '" + c.label + "'?")) api("/admin/content/items/" + c.id, { method: "DELETE" }).then(function (r) { if (handle(r)) loadContent(); }); return; }
        if (act === "image") return openForm("Upload Image — " + c.label, '<div class="mb-3"><input type="file" class="form-control" name="file" accept="image/*" required></div>', function (v) {
            var fd = new FormData(); fd.append("file", v.file);
            return api("/admin/content/items/" + c.id + "/image", { method: "POST", body: fd }).then(function (r) { if (handle(r)) { formModal.hide(); loadContent(); } else formError(r.message); });
        }, { submitLabel: "Upload" });
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
            el("blogFormAuthorInput").value = "Mentor Vaibhav Pawar";
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
        el("siteSwitch").querySelectorAll("button").forEach(function (b) {
            b.addEventListener("click", function () { el("siteSwitch").querySelectorAll("button").forEach(function (x) { x.classList.remove("active"); }); b.classList.add("active"); state.site = b.dataset.site; loadContent(); });
        });
        el("globalSearch").addEventListener("input", function () { var q = this.value.trim(); clearTimeout(searchTimer); searchTimer = setTimeout(function () { globalSearch(q); }, 300); });
        document.addEventListener("click", function (e) { if (!e.target.closest("#searchResults") && !e.target.closest("#globalSearch")) el("searchResults").style.display = "none"; });
        el("gatewayForm").addEventListener("submit", saveGateway);
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

        el("changePasswordForm").addEventListener("submit", function (e) {
            e.preventDefault();
            if (el("cpNew").value !== el("cpConfirm").value) { toast("New passwords do not match.", false); return; }
            LSI_Auth.changePassword(el("cpCurrent").value, el("cpNew").value).then(function (r) {
                if (handle(r)) setTimeout(function () { LSI_Auth.logout(); }, 1200);
            });
        });

        var initial = (location.hash || "#dashboard").replace("#", "");
        show(el("view-" + initial) ? initial : "dashboard");
    });

    window.Admin = { show: show, loadDashboard: loadDashboard, openStudentForm: openStudentForm, openCourseForm: openCourseForm,
        openStoryForm: openStoryForm, openSliderForm: openSliderForm, openReviewForm: openReviewForm, loadReviews: loadReviews, openManualEnroll: openManualEnroll, openContentForm: openContentForm, loadAudit: loadAudit, loadGateway: loadGateway,
        openBlogForm: openBlogForm, openCategoryForm: openCategoryForm, loadBlogs: loadBlogs, loadBlogCategories: loadBlogCategories };
})(window);
