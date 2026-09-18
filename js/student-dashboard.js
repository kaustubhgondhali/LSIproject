/**
 * LORD SAI ACADEMY — STUDENT LMS (js/student-dashboard.js)
 * Application-style learning portal. Every view is rendered from the existing
 * /api/student/** and /api/public/** endpoints; nothing here is mock data.
 */
(function (window) {
    "use strict";

    var api = LSI_Auth.api;
    var doubtModal, readerModal, applyModal, readerBlobUrl = null, examTimer = null;
    var state = { overview: null, courses: [], content: {}, publicCourses: [], doubts: [], ebooks: [], invoices: [], exams: [], certificates: [], paper: null };

    // ---- helpers ---------------------------------------------------------------------------

    function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
    function el(id) { return document.getElementById(id); }
    function setText(id, v) { var e = el(id); if (e) e.textContent = v == null ? "—" : v; }
    function money(v) { return v == null ? "—" : "₹" + Number(v).toFixed(2); }
    function fmtDate(iso) { if (!iso) return "—"; var d = new Date(iso); return isNaN(d) ? esc(iso) : d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" }); }
    function duration(sec) { if (!sec) return ""; var m = Math.round(sec / 60); return m >= 60 ? Math.floor(m / 60) + "h " + (m % 60) + "m" : m + " min"; }
    function chip(status) {
        var map = { ACTIVE: "ok", COMPLETED: "info", INACTIVE: "muted", CANCELLED: "danger", OPEN: "warn", IN_PROGRESS: "info", RESOLVED: "ok", PENDING_REVIEW: "muted", REVIEWED: "ok", NEEDS_REVISION: "danger", PAID: "ok", SUCCESS: "ok", FAILED: "danger", REFUNDED: "muted", REVOKED: "danger",
            PENDING: "warn", APPROVED: "info", SCHEDULED: "info", REJECTED: "danger", PASSED: "ok", SUBMITTED: "info", EXPIRED: "danger", UPCOMING: "warn", CLOSED: "muted", ELIGIBLE: "ok" };
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

    // ---- navigation ------------------------------------------------------------------------

    function show(view) {
        document.querySelectorAll(".lms-view").forEach(function (v) { v.classList.remove("active"); });
        var target = el("view-" + view);
        if (!target) { view = "dashboard"; target = el("view-dashboard"); }
        target.classList.add("active");
        document.querySelectorAll(".lms-nav a[data-view]").forEach(function (a) { a.classList.toggle("active", a.dataset.view === view); });
        el("lmsSidebar").classList.remove("open"); el("lmsBackdrop").classList.remove("show");
        window.scrollTo({ top: 0 });
        if (view === "exam-take" && !state.paper) { show("exams"); return; }
        var loaders = { discover: renderDiscover, progress: renderProgress, certificates: loadCertificates, resources: renderResources, profile: renderProfile, ebooks: loadEbooks, invoices: loadInvoices, exams: loadExams };
        if (loaders[view]) loaders[view]();
    }

    // ---- data loading ----------------------------------------------------------------------

    function loadAll() {
        return Promise.all([api("/student/profile"), api("/student/courses"), api("/student/ebooks")]).then(function (r) {
            if (r[0].success) state.overview = r[0].data;
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
        });
    }

    function ebookCard(e) {
        var active = e.status === "ACTIVE";
        var cover = e.coverImagePath ? '<div class="thumb" style="background-image:url(\'' + esc(mediaUrl(e.coverImagePath)) + '\');background-size:contain;background-repeat:no-repeat;background-color:#0A1128"></div>'
            : '<div class="thumb placeholder"><i class="fas fa-book"></i></div>';
        return '<div class="lms-card lms-course">' + cover +
            '<div class="body">' +
            '<div class="d-flex justify-content-between align-items-center mb-2">' + chip(active ? "ACTIVE" : "INACTIVE") + '<small class="text-muted">Purchased ' + fmtDate(e.purchasedAt) + '</small></div>' +
            '<h3 class="title">' + esc(e.title) + '</h3>' +
            (e.author ? '<div class="text-muted" style="font-size:12px;margin-bottom:6px"><i class="fas fa-pen-nib me-1"></i>' + esc(e.author) + '</div>' : '') +
            '<p class="desc">' + esc(e.shortDescription || "") + '</p>' +
            '<div class="meta">' + (e.category ? '<span><i class="fas fa-tag me-1"></i>' + esc(e.category) + '</span>' : '') + (e.language ? '<span><i class="fas fa-language me-1"></i>' + esc(e.language) + '</span>' : '') + (e.invoiceNumber ? '<span><i class="fas fa-file-invoice me-1"></i>' + esc(e.invoiceNumber) + '</span>' : '') + '</div>' +
            '<div class="foot">' +
            (active && e.fileAvailable ? '<button type="button" class="lms-btn accent sm" data-read="' + e.ebookId + '"><i class="fas fa-book-open"></i> Read / View</button>'
                : '<span class="text-muted small">' + (active ? "File not available yet — contact the academy" : "Access inactive — contact the academy") + '</span>') +
            (e.invoiceId ? '<button type="button" class="lms-btn ghost sm" data-invoice="' + e.invoiceId + '"><i class="fas fa-download"></i> Invoice</button>' : '') +
            '</div></div></div>';
    }

    function renderEbooks() {
        var grid = el("ebooksGrid"); if (!grid) return;
        setText("navEbookCount", state.ebooks.length || "");
        if (!state.ebooks.length) {
            grid.innerHTML = '<div class="lms-card lms-empty" style="grid-column:1/-1"><i class="fas fa-book"></i>No ebooks yet. <a href="store.html#ebooks">Browse ebooks in the store</a>.</div>';
            return;
        }
        grid.innerHTML = state.ebooks.map(ebookCard).join("");
        grid.querySelectorAll("[data-read]").forEach(function (b) { b.addEventListener("click", function () { openEbook(b.dataset.read); }); });
        grid.querySelectorAll("[data-invoice]").forEach(function (b) { b.addEventListener("click", function () { downloadInvoice(b.dataset.invoice); }); });
    }

    /** Streams the protected PDF with the bearer token and shows it inside the portal (never a plain link). */
    function openEbook(ebookId) {
        var e = state.ebooks.find(function (x) { return String(x.ebookId) === String(ebookId); }) || {};
        setText("ebookReaderTitle", e.title || "Ebook");
        setText("ebookReaderMeta", (e.author ? e.author + " · " : "") + "Licensed to " + ((state.overview && state.overview.studentId) || "you") + " — for personal use inside the Student Portal only");
        el("ebookReaderWatermark").textContent = (state.overview ? state.overview.studentId + " · " + state.overview.email : "");
        el("ebookReaderLoading").style.display = "flex";
        el("ebookReaderFrame").style.display = "none";
        readerModal.show();
        fetchProtected("/student/ebooks/" + ebookId + "/download").then(function (blob) {
            if (readerBlobUrl) URL.revokeObjectURL(readerBlobUrl);
            readerBlobUrl = URL.createObjectURL(blob);
            var frame = el("ebookReaderFrame");
            frame.src = readerBlobUrl + "#toolbar=0";
            frame.style.display = "block";
            el("ebookReaderLoading").style.display = "none";
        }).catch(function (err) {
            el("ebookReaderLoading").innerHTML = '<span class="text-warning"><i class="fas fa-lock me-1"></i> ' + esc(err.message) + '</span>';
        });
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

    // ---- my exams --------------------------------------------------------------------------
    // Everything shown here comes from /api/student/exams; the backend decides eligibility, the
    // exam window, attempts and results. The page only renders the state and the one allowed action.

    function fmtDateTime(iso) { if (!iso) return "—"; var d = new Date(iso); return isNaN(d) ? esc(iso) : d.toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }); }

    function loadExams() {
        var body = el("examsBody");
        return api("/student/exams").then(function (res) {
            if (!res.success) { body.innerHTML = '<div class="lms-card lms-empty text-danger">' + esc(res.message) + '</div>'; return; }
            state.exams = res.data || [];
            var actionable = state.exams.filter(function (x) { return x.canApply || (x.application && (x.application.action === "START" || x.application.action === "RESUME")); }).length;
            setText("navExamCount", actionable || "");
            if (!state.exams.length) { body.innerHTML = '<div class="lms-card lms-empty"><i class="fas fa-file-signature"></i>Complete your course to apply for the exam. Enroll in a course to get started.</div>'; return; }
            body.innerHTML = state.exams.map(examCard).join("");
            body.querySelectorAll("[data-apply]").forEach(function (b) { b.addEventListener("click", function () { openApply(b.dataset.apply); }); });
            body.querySelectorAll("[data-start]").forEach(function (b) { b.addEventListener("click", function () { startExam(b.dataset.start, b); }); });
            body.querySelectorAll("[data-result]").forEach(function (b) { b.addEventListener("click", function () { showAttemptResult(b.dataset.result); }); });
            body.querySelectorAll("[data-cert-view]").forEach(function (b) { b.addEventListener("click", function () { openCertificate(b.dataset.certView, false); }); });
            bindViewLinks(body);
        });
    }

    function examCard(x) {
        var a = x.application, s = a && a.schedule;
        var completed = x.courseCompleted;
        var html = '<div class="lms-card pad mb-3">' +
            '<div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-2"><div><h3 class="title mb-1" style="font-size:16px;font-weight:700">' + esc(x.courseName) + '</h3>' +
            '<small class="text-muted">' + (completed ? '<i class="fas fa-check-circle text-success me-1"></i>Course Completed' : x.completedLessons + ' of ' + x.totalLessons + ' lessons completed (' + x.progressPercent + '%)') + '</small></div>' +
            (a ? chip(a.status === "COMPLETED" && a.result ? a.result : a.status) : (completed ? chip("ELIGIBLE") : chip(x.enrollmentStatus))) + '</div>';

        if (!completed) {
            html += '<div class="mb-2">' + progressBar(x.progressPercent, x.completedLessons, x.totalLessons) + '</div>' +
                '<div class="lms-exam-note">Complete the course before applying for the exam.</div>' +
                '<div class="mt-3"><a href="learn.html?course=' + x.courseId + '" class="lms-btn accent sm"><i class="fas fa-play"></i> Continue course</a></div>';
        } else if (!a || a.status === "REJECTED" || (a.status === "COMPLETED" && a.result === "FAILED" && x.canApply)) {
            if (a) html += applicationBlock(x, a, s);
            html += x.canApply
                ? '<div class="lms-exam-note ok mt-2"><strong>Course Completed ✓</strong><br>' + esc(x.eligibilityMessage) + (x.examAvailable ? '' : ' The academy will assign the exam when it schedules your application.') + '</div>' +
                  '<div class="mt-3"><button type="button" class="lms-btn primary" data-apply="' + x.courseId + '"><i class="fas fa-file-signature"></i> Apply for Exam</button></div>'
                : '<div class="lms-exam-note mt-2">' + esc(x.eligibilityMessage) + '</div>';
        } else {
            html += applicationBlock(x, a, s);
        }
        return html + '</div>';
    }

    function applicationBlock(x, a, s) {
        var kv = '<div class="lms-exam-kv">' +
            '<div><small>Exam</small><strong>' + esc(a.examTitle || "To be assigned") + '</strong></div>' +
            '<div><small>Applied on</small><strong>' + fmtDate(a.appliedAt) + '</strong></div>' +
            (s ? '<div><small>Date</small><strong>' + esc(s.examDate) + '</strong></div><div><small>Time (IST)</small><strong>' + esc(s.startTime) + ' – ' + esc(s.endTime) + '</strong></div>' : '') +
            (a.totalMarks != null ? '<div><small>Total marks</small><strong>' + a.totalMarks + '</strong></div><div><small>Passing marks</small><strong>' + a.passingMarks + '</strong></div>' : '') +
            (a.maxAttempts != null ? '<div><small>Attempts</small><strong>' + a.attemptsUsed + ' / ' + a.maxAttempts + '</strong></div>' : '') +
            '<div><small>Status</small><strong>' + esc(String(a.status === "COMPLETED" && a.result ? a.result : a.status).replace(/_/g, " ")) + (s && a.status === "SCHEDULED" ? ' · window ' + esc(String(s.window).toLowerCase()) : '') + '</strong></div>' +
            '</div>';
        var tone = a.action === "PASSED" ? "ok" : (a.action === "FAILED" || a.action === "REJECTED" || a.action === "CLOSED") ? "danger" : (a.action === "START" || a.action === "RESUME") ? "ok" : "";
        var note = '<div class="lms-exam-note ' + tone + '">' + esc(a.message || "") + '</div>';
        if (s && s.instructions && (a.action === "WAIT" || a.action === "START" || a.action === "RESUME")) note += '<div class="text-muted small mt-2"><strong>Instructions:</strong> ' + esc(s.instructions).replace(/\n/g, "<br>") + '</div>';
        var actions = '';
        if (a.action === "START") actions += '<button type="button" class="lms-btn primary" data-start="' + a.id + '"><i class="fas fa-play"></i> Start Exam</button>';
        if (a.action === "RESUME") actions += '<button type="button" class="lms-btn accent" data-start="' + a.id + '"><i class="fas fa-redo"></i> Continue Exam</button>';
        if (a.certificateId) actions += '<button type="button" class="lms-btn primary" data-cert-view="' + a.certificateId + '"><i class="fas fa-award"></i> View Certificate</button> <a href="#certificates" data-view="certificates" class="lms-btn ghost">Certificates</a>';
        var attempts = (a.attempts || []).filter(function (t) { return t.status !== "IN_PROGRESS"; });
        var table = attempts.length ? '<div class="lms-table-wrap mt-3"><table class="lms-table"><thead><tr><th>Attempt</th><th>Score</th><th>Passing</th><th>Result</th><th>Submitted</th><th></th></tr></thead><tbody>' +
            attempts.map(function (t) { return '<tr><td>' + t.attemptNumber + (a.maxAttempts ? ' / ' + a.maxAttempts : '') + '</td><td><strong>' + t.score + ' / ' + t.totalMarks + '</strong></td><td>' + t.passingMarks + '</td><td>' + chip(t.status === "EXPIRED" ? "EXPIRED" : (t.passed ? "PASSED" : "FAILED")) + '</td><td>' + fmtDateTime(t.submittedAt) + '</td><td><button type="button" class="lms-btn ghost sm" data-result="' + t.id + '">Result</button></td></tr>'; }).join("") + '</tbody></table></div>' : '';
        return kv + note + (actions ? '<div class="d-flex flex-wrap gap-2 mt-3">' + actions + '</div>' : '') + table;
    }

    function openApply(courseId) {
        var x = state.exams.find(function (e) { return String(e.courseId) === String(courseId); }); if (!x) return;
        var o = state.overview || {};
        el("examApplyCourseId").value = x.courseId;
        el("examApplyName").value = o.fullName || "";
        el("examApplyStudentId").value = o.studentId || "";
        el("examApplyCourse").value = x.courseName;
        el("examApplyEmail").value = o.email || "";
        el("examApplyCompletion").value = "Completed (" + x.completedLessons + " of " + x.totalLessons + " lessons)";
        el("examApplyError").classList.add("d-none");
        applyModal.show();
    }

    function submitApply(e) {
        e.preventDefault();
        var btn = el("examApplySubmit"), err = el("examApplyError");
        btn.disabled = true; err.classList.add("d-none");
        api("/student/exams/apply", { method: "POST", body: { courseId: Number(el("examApplyCourseId").value) } }).then(function (res) {
            btn.disabled = false;
            if (!res.success) { err.textContent = res.errors ? Object.values(res.errors).join(" ") : res.message; err.classList.remove("d-none"); return; }
            applyModal.hide(); toast(res.message || "Exam application submitted successfully.");
            loadExams();
        });
    }

    // ---- exam paper ------------------------------------------------------------------------

    function startExam(applicationId, btn) {
        if (btn) btn.disabled = true;
        api("/student/exams/applications/" + applicationId + "/start", { method: "POST" }).then(function (res) {
            if (btn) btn.disabled = false;
            if (!res.success) { toast(res.message, false); loadExams(); return; }
            state.paper = res.data; state.paper.answers = {};
            location.hash = "exam-take"; show("exam-take"); renderPaper();
        });
    }

    function renderPaper() {
        var p = state.paper; if (!p) return;
        var body = el("examPaperBody");
        body.innerHTML = '<div class="lms-card">' +
            '<div class="lms-exam-head"><div><h1 class="lms-page-title mb-0" style="font-size:18px">' + esc(p.examTitle) + '</h1><small class="text-muted">' + esc(p.courseName) + ' · Attempt ' + p.attemptNumber + ' / ' + p.maxAttempts + ' · Total ' + p.totalMarks + ' marks · Passing ' + p.passingMarks + '</small></div>' +
            '<div class="d-flex align-items-center gap-2"><span class="text-muted small d-none d-sm-inline">Time remaining</span><span class="lms-exam-timer" id="examTimer">--:--</span></div></div>' +
            (p.description || p.instructions ? '<div class="px-4 pt-3"><div class="lms-exam-note">' + (p.instructions ? esc(p.instructions).replace(/\n/g, "<br>") : '') + (p.description ? (p.instructions ? '<br>' : '') + esc(p.description).replace(/\n/g, "<br>") : '') + '</div></div>' : '') +
            '<div class="px-4 pt-3 text-muted small">Select one answer per question. Your answers are evaluated by the academy server after you submit. <strong id="examAnswered">0</strong> of ' + p.questions.length + ' answered.</div>' +
            '<form id="examForm">' + p.questions.map(function (q) {
                return '<div class="lms-question" data-q="' + q.id + '"><div class="q"><span class="marks">' + q.marks + ' mark' + (q.marks === 1 ? '' : 's') + '</span><span class="n">Question ' + q.number + '</span>' + esc(q.questionText).replace(/\n/g, "<br>") + '</div>' +
                    ["A", "B", "C", "D"].map(function (k) { return '<label class="lms-option"><input type="radio" name="q' + q.id + '" value="' + k + '"><span class="key">' + k + '.</span><span>' + esc(q["option" + k]) + '</span></label>'; }).join("") + '</div>';
            }).join("") +
            '<div class="p-4 d-flex flex-wrap justify-content-between align-items-center gap-2 border-top"><small class="text-muted">Submitting is final — a submitted attempt cannot be changed.</small><button type="submit" class="lms-btn primary" id="examSubmitBtn"><i class="fas fa-paper-plane"></i> Submit Exam</button></div>' +
            '</form></div>';
        body.querySelectorAll('input[type="radio"]').forEach(function (r) {
            r.addEventListener("change", function () {
                var q = r.closest(".lms-question"); q.querySelectorAll(".lms-option").forEach(function (o) { o.classList.remove("selected"); });
                r.closest(".lms-option").classList.add("selected");
                state.paper.answers[q.dataset.q] = r.value;
                setText("examAnswered", Object.keys(state.paper.answers).length);
            });
        });
        el("examForm").addEventListener("submit", function (e) { e.preventDefault(); submitExam(false); });
        startTimer(p.secondsRemaining);
        window.scrollTo({ top: 0 });
    }

    function startTimer(seconds) {
        clearInterval(examTimer);
        // Server-provided remaining seconds anchor the countdown; the deadline is enforced by the backend anyway.
        var end = Date.now() + Math.max(0, seconds) * 1000;
        function tick() {
            var left = Math.max(0, Math.round((end - Date.now()) / 1000));
            var t = el("examTimer"); if (!t) { clearInterval(examTimer); return; }
            var h = Math.floor(left / 3600), m = Math.floor((left % 3600) / 60), s = left % 60;
            t.textContent = (h ? h + ":" : "") + (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
            t.classList.toggle("low", left <= 120);
            if (left <= 0) { clearInterval(examTimer); submitExam(true); }
        }
        tick(); examTimer = setInterval(tick, 1000);
    }

    function submitExam(auto) {
        var p = state.paper; if (!p) return;
        if (!auto) {
            var unanswered = p.questions.length - Object.keys(p.answers).length;
            if (!window.confirm("Are you sure you want to submit your exam?" + (unanswered ? "\n\n" + unanswered + " question(s) are still unanswered." : ""))) return;
        }
        clearInterval(examTimer);
        var btn = el("examSubmitBtn"); if (btn) btn.disabled = true;
        var answers = p.questions.map(function (q) { return { questionId: q.id, selectedOption: p.answers[q.id] || null }; });
        api("/student/exams/attempts/" + p.attemptId + "/submit", { method: "POST", body: { answers: answers } }).then(function (res) {
            if (!res.success) {
                if (btn) btn.disabled = false;
                toast(res.message || "The exam could not be submitted.", false);
                if (res.status === 409) { state.paper = null; location.hash = "exams"; show("exams"); }
                return;
            }
            state.paper = null;
            renderResult(res.data);
            refreshOverview();
        });
    }

    function showAttemptResult(attemptId) {
        api("/student/exams/attempts/" + attemptId + "/result").then(function (res) {
            if (!res.success) { toast(res.message, false); return; }
            state.paper = { done: true }; location.hash = "exam-take"; show("exam-take"); renderResult(res.data); state.paper = null;
        });
    }

    function renderResult(r) {
        var body = el("examPaperBody");
        body.innerHTML = '<div class="lms-card pad text-center" style="max-width:640px;margin:0 auto">' +
            '<div class="text-muted" style="letter-spacing:2px;font-size:12px;font-weight:700">EXAM RESULT</div>' +
            '<h2 class="lms-page-title mt-2 mb-1">' + esc(r.examTitle) + '</h2><div class="text-muted mb-3">' + esc(r.courseName) + '</div>' +
            '<div class="lms-result-score" style="color:' + (r.passed ? "#15803D" : "#B91C1C") + '">' + r.score + ' <span class="text-muted" style="font-size:18px;font-weight:600">/ ' + r.totalMarks + '</span></div>' +
            '<div class="mt-2 mb-3"><span class="lms-result-badge ' + (r.passed ? "pass" : "fail") + '">' + esc(r.result) + '</span></div>' +
            '<div class="lms-exam-kv text-start" style="max-width:520px;margin:0 auto 14px">' +
            '<div><small>Student</small><strong>' + esc(r.studentName) + '</strong></div><div><small>Student ID</small><strong>' + esc(r.studentId || "—") + '</strong></div>' +
            '<div><small>Passing marks</small><strong>' + r.passingMarks + '</strong></div><div><small>Correct answers</small><strong>' + r.correctCount + ' / ' + r.questionCount + '</strong></div>' +
            '<div><small>Attempt</small><strong>' + r.attemptNumber + ' / ' + r.maxAttempts + '</strong></div><div><small>' + (r.passed ? "Certificate" : "Attempts remaining") + '</small><strong>' + (r.passed ? esc(r.certificateNumber || "Issued") : r.attemptsRemaining) + '</strong></div>' +
            '<div><small>Submitted</small><strong>' + fmtDateTime(r.submittedAt) + '</strong></div></div>' +
            '<div class="lms-exam-note ' + (r.passed ? "ok" : "danger") + ' text-start">' + esc(r.message) + '</div>' +
            '<div class="d-flex flex-wrap justify-content-center gap-2 mt-4">' +
            (r.certificateId ? '<button type="button" class="lms-btn primary" data-cert-view="' + r.certificateId + '"><i class="fas fa-award"></i> View Certificate</button><button type="button" class="lms-btn accent" data-cert-download="' + r.certificateId + '"><i class="fas fa-download"></i> Download Certificate</button>' : '') +
            '<a href="#exams" data-view="exams" class="lms-btn ghost">Back to My Exams</a></div></div>';
        body.querySelectorAll("[data-cert-view]").forEach(function (b) { b.addEventListener("click", function () { openCertificate(b.dataset.certView, false); }); });
        body.querySelectorAll("[data-cert-download]").forEach(function (b) { b.addEventListener("click", function () { openCertificate(b.dataset.certDownload, true); }); });
        bindViewLinks(body);
        window.scrollTo({ top: 0 });
    }

    // ---- certificates ----------------------------------------------------------------------
    // Issued by the backend only for a passed exam attempt; the PDF is streamed with the bearer token.

    function loadCertificates() {
        var body = el("certificatesBody");
        return api("/student/certificates").then(function (res) {
            if (!res.success) { body.innerHTML = '<div class="lms-card lms-empty text-danger">' + esc(res.message) + '</div>'; return; }
            state.certificates = res.data || [];
            setText("navCertCount", state.certificates.length || "");
            if (!state.certificates.length) {
                body.innerHTML = '<div class="lms-card lms-empty"><i class="fas fa-award"></i>No certificate yet. Complete a course, pass its final exam and your certificate will appear here.<br><a href="#exams" data-view="exams" class="lms-btn primary sm mt-3">Go to My Exams</a></div>';
                bindViewLinks(body); return;
            }
            body.innerHTML = '<div class="lms-card">' + state.certificates.map(function (c) {
                return '<div class="lms-list-item"><div class="ico" style="background:#DCFCE7;color:#15803D"><i class="fas fa-award"></i></div>' +
                    '<div class="txt"><strong>' + esc(c.courseName) + '</strong><small><span class="lms-chip ok">Eligible</span> ' + esc(c.certificateNumber) + ' · ' + esc(c.examTitle) + ' · Score ' + c.score + ' / ' + c.totalMarks + ' · Issued ' + fmtDate(c.issuedAt) + '</small></div>' +
                    '<div class="d-flex gap-2 flex-wrap justify-content-end"><button type="button" class="lms-btn primary sm" data-cert-view="' + c.id + '"><i class="fas fa-eye"></i> View</button><button type="button" class="lms-btn ghost sm" data-cert-download="' + c.id + '"><i class="fas fa-download"></i> Download</button></div></div>';
            }).join("") + '</div>';
            body.querySelectorAll("[data-cert-view]").forEach(function (b) { b.addEventListener("click", function () { openCertificate(b.dataset.certView, false); }); });
            body.querySelectorAll("[data-cert-download]").forEach(function (b) { b.addEventListener("click", function () { openCertificate(b.dataset.certDownload, true); }); });
        });
    }

    function openCertificate(id, download) {
        var c = state.certificates.find(function (x) { return String(x.id) === String(id); });
        fetchProtected("/student/certificates/" + id + "/pdf").then(function (blob) {
            var url = URL.createObjectURL(blob);
            if (download) { var a = document.createElement("a"); a.href = url; a.download = (c ? c.certificateNumber : "certificate") + ".pdf"; document.body.appendChild(a); a.click(); a.remove(); }
            else window.open(url, "_blank");
            setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
        }).catch(function (err) { toast(err.message, false); });
    }

    // ---- resources -------------------------------------------------------------------------

    function renderResources() {
        var items = [];
        state.courses.filter(hasAccess).forEach(function (c) {
            var content = state.content[c.courseId]; if (!content) return;
            content.modules.forEach(function (m) { m.lessons.forEach(function (l) { if (l.hasMaterial) items.push({ c: c, m: m, l: l }); }); });
        });
        var body = el("resourcesBody");
        if (!items.length) { body.innerHTML = '<div class="lms-empty"><i class="fas fa-file-pdf"></i>No handouts have been uploaded to your courses yet.</div>'; return; }
        body.innerHTML = items.map(function (it) {
            return '<div class="lms-list-item"><div class="ico" style="color:#DC2626"><i class="fas fa-file-pdf"></i></div>' +
                '<div class="txt"><strong>' + esc(it.l.materialOriginalName || it.l.lessonTitle) + '</strong><small>' + esc(it.c.courseName) + ' · ' + esc(it.m.moduleName) + ' · ' + esc(it.l.lessonTitle) + '</small></div>' +
                '<a href="' + learnUrl(it.c, it.l.id) + '" class="lms-btn ghost sm">Lesson</a>' +
                // Handouts open in the lesson page's watermarked viewer; there is no download.
                '<a href="' + learnUrl(it.c, it.l.id) + '&material=1" class="lms-btn primary sm"><i class="fas fa-book-open"></i> View</a></div>';
        }).join("");
    }

    // ---- profile ---------------------------------------------------------------------------

    function renderProfile() {
        var o = state.overview; if (!o) return;
        var row = function (k, v) { return '<div class="d-flex justify-content-between py-2" style="border-bottom:1px solid #F1F5F9"><span class="text-muted">' + k + '</span><strong class="text-end">' + esc(v || "—") + '</strong></div>'; };
        el("profileCard").innerHTML = '<div class="d-flex align-items-center gap-3 mb-3"><div class="lms-avatar" style="width:56px;height:56px;font-size:20px">' + esc(el("topAvatar").textContent) + '</div><div><strong class="d-block" style="font-size:16px">' + esc(o.fullName) + '</strong><span class="lms-chip ok">Student</span></div></div>' +
            row("Student ID", o.studentId) + row("Email", o.email) + row("Mobile", o.mobile) + row("Batch", o.batch) + row("Location", o.location) + row("Registered", fmtDate(o.registrationDate)) + row("Last login", fmtDate(o.lastLoginAt));
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
                    if (l.materialOriginalName && l.materialOriginalName.toLowerCase().indexOf(q) >= 0) hits.push({ t: l.materialOriginalName, s: "Resource · " + m.moduleName, href: "#resources", view: "resources", i: "fa-file-pdf" });
                });
            });
        });
        state.ebooks.forEach(function (e) {
            if ((e.title || "").toLowerCase().indexOf(q) >= 0) hits.push({ t: e.title, s: "Ebook", href: "#ebooks", view: "ebooks", i: "fa-book" });
        });
        state.certificates.forEach(function (c) {
            if ((c.courseName + " " + c.certificateNumber).toLowerCase().indexOf(q) >= 0) hits.push({ t: c.certificateNumber, s: "Certificate · " + c.courseName, href: "#certificates", view: "certificates", i: "fa-award" });
        });
        if ("my exams".indexOf(q) >= 0) hits.push({ t: "My Exams", s: "Exam applications, schedules and results", href: "#exams", view: "exams", i: "fa-file-signature" });
        box.innerHTML = hits.slice(0, 12).map(function (h) { return '<a href="' + h.href + '"' + (h.view ? ' data-view="' + h.view + '"' : "") + '><i class="fas ' + h.i + ' me-2 text-muted"></i>' + esc(h.t) + '<br><small>' + esc(h.s) + '</small></a>'; }).join("") || '<a href="#" class="text-muted">No matches</a>';
        box.style.display = "block";
        bindViewLinks(box);
    }

    // ---- boot ------------------------------------------------------------------------------

    document.addEventListener("DOMContentLoaded", function () {
        if (!LSI_Auth.isAuthenticated()) return;
        doubtModal = new bootstrap.Modal(el("doubtThreadModal"));
        readerModal = new bootstrap.Modal(el("ebookReaderModal"));
        el("ebookReaderModal").addEventListener("hidden.bs.modal", function () {
            el("ebookReaderFrame").src = "about:blank";
            if (readerBlobUrl) { URL.revokeObjectURL(readerBlobUrl); readerBlobUrl = null; }
        });
        el("newDoubtForm").addEventListener("submit", submitDoubt);
        el("doubtReplyForm").addEventListener("submit", submitReply);
        applyModal = new bootstrap.Modal(el("examApplyModal"));
        el("examApplyForm").addEventListener("submit", submitApply);

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
                if (!r.success) { err.textContent = r.errors ? Object.values(r.errors).join(" ") : r.message; err.classList.remove("d-none"); return; }
                toast(r.message || "Password changed."); setTimeout(function () { LSI_Auth.logout(); }, 1200);
            });
        });

        el("changeUserIdForm").addEventListener("submit", function (e) {
            e.preventDefault();
            var err = el("cuError"); err.classList.add("d-none");
            var fail = function (m) { err.textContent = m; err.classList.remove("d-none"); };
            var mine = String((state.overview && state.overview.studentId) || "").toLowerCase();
            var cur = el("cuCurrent").value.trim(), next = el("cuNew").value.trim(), again = el("cuConfirm").value.trim();
            if (mine && cur.toLowerCase() !== mine) return fail("The current User ID does not match your account.");
            if (next !== again) return fail("The new User IDs do not match.");
            if (mine && next.toLowerCase() === mine) return fail("The new User ID is the same as your current one.");
            var btn = this.querySelector('[type="submit"]'); btn.disabled = true;
            api("/student/change-user-id", { method: "POST", body: { currentUserId: cur, newUserId: next, confirmUserId: again, currentPassword: el("cuPassword").value } }).then(function (r) {
                btn.disabled = false;
                el("cuPassword").value = "";
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
