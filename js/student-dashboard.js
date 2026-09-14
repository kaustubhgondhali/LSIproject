/**
 * LORD SAI ACADEMY — STUDENT LMS (js/student-dashboard.js)
 * Application-style learning portal. Every view is rendered from the existing
 * /api/student/** and /api/public/** endpoints; nothing here is mock data.
 */
(function (window) {
    "use strict";

    var api = LSI_Auth.api;
    var doubtModal;
    var state = { overview: null, courses: [], content: {}, publicCourses: [], doubts: [] };

    // ---- helpers ---------------------------------------------------------------------------

    function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
    function el(id) { return document.getElementById(id); }
    function setText(id, v) { var e = el(id); if (e) e.textContent = v == null ? "—" : v; }
    function money(v) { return v == null ? "—" : "₹" + Number(v).toFixed(2); }
    function fmtDate(iso) { if (!iso) return "—"; var d = new Date(iso); return isNaN(d) ? esc(iso) : d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" }); }
    function duration(sec) { if (!sec) return ""; var m = Math.round(sec / 60); return m >= 60 ? Math.floor(m / 60) + "h " + (m % 60) + "m" : m + " min"; }
    function chip(status) {
        var map = { ACTIVE: "ok", COMPLETED: "info", INACTIVE: "muted", CANCELLED: "danger", OPEN: "warn", IN_PROGRESS: "info", RESOLVED: "ok", PENDING_REVIEW: "muted", REVIEWED: "ok", NEEDS_REVISION: "danger" };
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
        var loaders = { discover: renderDiscover, progress: renderProgress, certificates: renderCertificates, resources: renderResources, profile: renderProfile };
        if (loaders[view]) loaders[view]();
    }

    // ---- data loading ----------------------------------------------------------------------

    function loadAll() {
        return Promise.all([api("/student/profile"), api("/student/courses")]).then(function (r) {
            if (r[0].success) state.overview = r[0].data;
            if (r[1].success) state.courses = r[1].data || [];
            renderHeader(); renderDashboard(); renderLearning();
            fillDoubtCourses();
            return loadContent();
        });
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

    // ---- certificates ----------------------------------------------------------------------

    function renderCertificates() {
        var body = el("certificatesBody");
        var active = state.courses.filter(hasAccess);
        if (!active.length) { body.innerHTML = '<div class="lms-card lms-empty"><i class="fas fa-award"></i>Enroll in a course to earn a certificate.</div>'; return; }
        body.innerHTML = '<div class="lms-card">' + active.map(function (c) {
            var done = c.progressPercent >= 100 && c.totalLessons > 0;
            return '<div class="lms-list-item"><div class="ico" style="' + (done ? "background:#DCFCE7;color:#15803D" : "") + '"><i class="fas fa-award"></i></div>' +
                '<div class="txt"><strong>' + esc(c.courseName) + '</strong><small>' + (done ? "Completed — certificate available" : c.completedLessons + " of " + c.totalLessons + " lessons completed (" + c.progressPercent + "%)") + '</small></div>' +
                (done ? '<button class="lms-btn primary sm" data-cert="' + c.courseId + '"><i class="fas fa-print"></i> View / Print</button>'
                      : '<a href="' + learnUrl(c, c.nextLessonId) + '" class="lms-btn ghost sm">Continue</a>') + '</div>';
        }).join("") + '</div>';
        body.querySelectorAll("[data-cert]").forEach(function (b) {
            b.addEventListener("click", function () { printCertificate(state.courses.find(function (x) { return String(x.courseId) === b.dataset.cert; })); });
        });
    }

    function printCertificate(c) {
        var o = state.overview || {};
        var area = el("certificatePrint");
        area.className = "";
        area.innerHTML = '<div class="lms-cert mt-3">' +
            '<img src="img/lord-sai-logo.png" alt="" style="height:70px;margin-bottom:12px">' +
            '<h1>CERTIFICATE OF COMPLETION</h1><div class="text-muted">Lord Sai Investment &amp; Share Market Academy</div>' +
            '<p class="mt-4 mb-0">This certifies that</p><div class="name">' + esc(o.fullName) + '</div>' +
            '<p class="mb-0">Student ID <strong>' + esc(o.studentId || "") + '</strong> has successfully completed all ' + c.totalLessons + ' lessons of</p>' +
            '<h2 style="font-size:22px;margin:14px 0 24px">' + esc(c.courseName) + '</h2>' +
            '<div class="d-flex justify-content-between mt-5 pt-4" style="border-top:1px solid #E5E9F0"><div><strong>Vaibhav S. Pawar</strong><br><small class="text-muted">Founder &amp; Lead Mentor</small></div><div class="text-end"><strong>' + fmtDate(new Date().toISOString()) + '</strong><br><small class="text-muted">Date of issue</small></div></div>' +
            '</div><div class="text-center mt-3 lms-no-print"><button class="lms-btn primary" onclick="window.print()"><i class="fas fa-print"></i> Print / Save as PDF</button> <button class="lms-btn ghost" onclick="document.getElementById(\'certificatePrint\').className=\'d-none\'">Close</button></div>';
        area.scrollIntoView({ behavior: "smooth" });
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
                '<button class="lms-btn primary sm" data-material="' + it.l.id + '" data-name="' + esc(it.l.materialOriginalName || "material.pdf") + '"><i class="fas fa-download"></i> Download</button></div>';
        }).join("");
        body.querySelectorAll("[data-material]").forEach(function (b) { b.addEventListener("click", function () { downloadMaterial(b.dataset.material, b.dataset.name, b); }); });
    }

    /** Fetches with the bearer token (a plain <a href> cannot send the Authorization header). */
    function downloadMaterial(lessonId, name, btn) {
        var session = LSI_Auth.getSession();
        btn.disabled = true;
        fetch(LSI_Auth.apiBase + "/student/lessons/" + lessonId + "/material", { headers: { Authorization: "Bearer " + (session ? session.token : "") } })
            .then(function (r) { if (!r.ok) throw new Error("Download failed (" + r.status + ")"); return r.blob(); })
            .then(function (b) { var url = URL.createObjectURL(b); var a = document.createElement("a"); a.href = url; a.download = name || "material.pdf"; document.body.appendChild(a); a.click(); a.remove(); setTimeout(function () { URL.revokeObjectURL(url); }, 10000); })
            .catch(function (e) { toast(e.message, false); })
            .finally(function () { btn.disabled = false; });
    }

    // ---- profile ---------------------------------------------------------------------------

    function renderProfile() {
        var o = state.overview; if (!o) return;
        var row = function (k, v) { return '<div class="d-flex justify-content-between py-2" style="border-bottom:1px solid #F1F5F9"><span class="text-muted">' + k + '</span><strong class="text-end">' + esc(v || "—") + '</strong></div>'; };
        el("profileCard").innerHTML = '<div class="d-flex align-items-center gap-3 mb-3"><div class="lms-avatar" style="width:56px;height:56px;font-size:20px">' + esc(el("topAvatar").textContent) + '</div><div><strong class="d-block" style="font-size:16px">' + esc(o.fullName) + '</strong><span class="lms-chip ok">Student</span></div></div>' +
            row("Student ID", o.studentId) + row("Email", o.email) + row("Mobile", o.mobile) + row("Batch", o.batch) + row("Location", o.location) + row("Registered", fmtDate(o.registrationDate)) + row("Last login", fmtDate(o.lastLoginAt));
        el("profileCourses").innerHTML = '<strong class="d-block mb-2">Enrollment record</strong>' + (state.courses.length ? '<div class="lms-table-wrap"><table class="lms-table"><thead><tr><th>Course</th><th>Status</th><th>Enrolled</th><th>Progress</th></tr></thead><tbody>' +
            state.courses.map(function (c) { return '<tr><td>' + esc(c.courseName) + '</td><td>' + chip(c.enrollmentStatus) + '</td><td>' + fmtDate(c.enrolledAt) + '</td><td>' + c.progressPercent + '%</td></tr>'; }).join("") + '</tbody></table></div>' : '<div class="text-muted small">No enrollments.</div>');
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
        box.innerHTML = hits.slice(0, 12).map(function (h) { return '<a href="' + h.href + '"' + (h.view ? ' data-view="' + h.view + '"' : "") + '><i class="fas ' + h.i + ' me-2 text-muted"></i>' + esc(h.t) + '<br><small>' + esc(h.s) + '</small></a>'; }).join("") || '<a href="#" class="text-muted">No matches</a>';
        box.style.display = "block";
        bindViewLinks(box);
    }

    // ---- boot ------------------------------------------------------------------------------

    document.addEventListener("DOMContentLoaded", function () {
        if (!LSI_Auth.isAuthenticated()) return;
        doubtModal = new bootstrap.Modal(el("doubtThreadModal"));
        el("newDoubtForm").addEventListener("submit", submitDoubt);
        el("doubtReplyForm").addEventListener("submit", submitReply);

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

        loadAll().then(function () {
            var initial = (location.hash || "#dashboard").replace("#", "");
            show(initial);
        });
        loadDoubts();
    });
})(window);
