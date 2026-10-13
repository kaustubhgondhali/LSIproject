/**
 * LORD SAI ACADEMY — AUTOMATION ADMIN PANEL (js/automation-admin.js)
 * Students · Fees & Payments · Receipts · Attendance · Batches & Courses · Reports.
 * Every screen reads the same records through /api/automation/**; data is entered once and reused.
 */
(function (window) {
    "use strict";

    var api = LSI_Auth.api;
    var L = null;                       // lookups (batches, courses, modes, …)
    var formModal, formSubmitHandler = null;
    var state = { dashPage: 0, stuPage: 0, rcPage: 0, receipt: null, sheet: null, marks: {}, payStudent: null, report: null, timers: {},
        chPage: 0, commPage: 0, commSelected: {}, commTotal: 0, channelStatus: null, share: null };

    // ---- utils ---------------------------------------------------------------------------------

    function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
    function el(id) { return document.getElementById(id); }
    function inr(v) { return v == null ? "—" : "₹ " + Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 }); }
    function d(iso) { if (!iso) return "—"; var x = new Date(iso); return isNaN(x) ? esc(iso) : x.toLocaleDateString("en-IN", { day: "2-digit", month: "2-digit", year: "numeric" }); }
    function dt(iso) { if (!iso) return "—"; var x = new Date(iso); return isNaN(x) ? esc(iso) : x.toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }); }
    function today() { var t = new Date(); return t.getFullYear() + "-" + String(t.getMonth() + 1).padStart(2, "0") + "-" + String(t.getDate()).padStart(2, "0"); }
    function badge(s) { var v = String(s || "").toLowerCase(); return '<span class="au-badge ' + esc(v) + '">' + esc(String(s || "").replace(/_/g, " ")) + '</span>'; }
    function pct(v) { return v == null ? "—" : v + "%"; }
    function attCell(r) { return r.sessionsTotal ? '<span title="' + r.sessionsPresent + ' of ' + r.sessionsTotal + ' sessions">' + r.sessionsPresent + ' / ' + r.sessionsTotal + ' · <strong style="color:' + (r.attendancePercent < 75 ? "#DC2626" : "#16A34A") + '">' + pct(r.attendancePercent) + '</strong></span>' : '<span class="text-muted">—</span>'; }
    function toast(msg, ok) { var t = document.createElement("div"); t.className = "au-toast " + (ok === false ? "err" : ok === true ? "ok" : ""); t.textContent = msg; el("toastArea").appendChild(t); setTimeout(function () { t.remove(); }, 4500); }
    function handle(res, okMsg) { if (res.success) { if (okMsg !== false) toast(res.message || okMsg || "Saved.", true); return true; } toast(res.errors ? Object.values(res.errors).join(" ") : (res.message || "Something went wrong."), false); return false; }
    function errText(res) { return res.errors ? Object.values(res.errors).join(" ") : (res.message || "Something went wrong."); }
    function qs(obj) { return Object.keys(obj).filter(function (k) { return obj[k] !== "" && obj[k] != null; }).map(function (k) { return k + "=" + encodeURIComponent(obj[k]); }).join("&"); }
    function debounce(key, fn, ms) { clearTimeout(state.timers[key]); state.timers[key] = setTimeout(fn, ms || 300); }
    function opts(list, valueKey, labelFn, selected, blank) { return (blank != null ? '<option value="">' + esc(blank) + '</option>' : "") + list.map(function (x) { var v = valueKey ? x[valueKey] : x; return '<option value="' + esc(v) + '"' + (String(v) === String(selected) ? " selected" : "") + '>' + esc(labelFn ? labelFn(x) : x) + '</option>'; }).join(""); }
    function pager(id, page, onPage) {
        var c = el(id); if (!c) return;
        if (!page || page.totalPages <= 1) { c.innerHTML = page && page.totalElements != null ? '<span>Showing ' + page.numberOfElements + ' of ' + page.totalElements + '</span>' : ""; return; }
        var html = '<span>Showing ' + (page.number * page.size + 1) + ' to ' + (page.number * page.size + page.numberOfElements) + ' of ' + page.totalElements + '</span><div class="pages">';
        html += '<button ' + (page.first ? "disabled" : "") + ' data-p="' + (page.number - 1) + '">&lsaquo;</button>';
        for (var i = Math.max(0, page.number - 2); i < Math.min(page.totalPages, page.number + 3); i++) html += '<button class="' + (i === page.number ? "on" : "") + '" data-p="' + i + '">' + (i + 1) + '</button>';
        html += '<button ' + (page.last ? "disabled" : "") + ' data-p="' + (page.number + 1) + '">&rsaquo;</button></div>';
        c.innerHTML = html;
        c.querySelectorAll("[data-p]").forEach(function (b) { b.addEventListener("click", function () { onPage(Number(b.dataset.p)); }); });
    }
    /** Bearer-authenticated download of any export (csv / xlsx / pdf) — never a plain public link. */
    function downloadFile(path, name) { downloadCsv(path, name); }

    /** Opens the server-rendered printer-friendly page (title, filters, generated time, table, record count) and prints it. */
    function printReportView(path) {
        var s = LSI_Auth.getSession();
        fetch(LSI_Auth.apiBase + path, { headers: { Authorization: "Bearer " + (s ? s.token : "") } }).then(function (r) {
            if (!r.ok) throw new Error("Print view failed (" + r.status + ")"); return r.text();
        }).then(function (html) {
            var w = window.open("", "_blank");
            if (!w) { toast("Allow pop-ups to print.", false); return; }
            w.document.open(); w.document.write(html); w.document.close();
            w.focus(); setTimeout(function () { w.print(); }, 400);
        }).catch(function (e) { toast(e.message, false); });
    }

    /** The current on-screen filters of each record section, so exports contain exactly what is shown. */
    function exportQueryFor(report) {
        var f;
        switch (report) {
            case "students":
                if (el("view-students").classList.contains("active")) { f = studentFilters(); return { q: f.q, batchId: f.batchId, courseId: f.courseId, status: f.status === "ALL" ? "" : f.status, year: f.year, paymentStatus: f.paymentStatus }; }
                return { batchId: el("dashBatch").value, paymentStatus: el("dashPayStatus").value };
            case "payment-history": return { batchId: el("payListBatch").value, mode: el("payListMode").value, from: el("payListFrom").value, to: el("payListTo").value };
            case "receipts": return { q: el("rcQ").value.trim(), from: el("rcFrom").value, to: el("rcTo").value };
            case "attendance-sessions": return { batchId: el("sessBatchFilter").value };
            case "communications": return historyQuery();
            case "pending-fees": return pendingQuery();
            default: return {};
        }
    }
    function runExport(report, fmt) {
        var query = qs(exportQueryFor(report));
        if (fmt === "print") printReportView("/automation/reports/" + report + "/print?" + query);
        else downloadFile("/automation/reports/" + report + "/" + fmt + "?" + query, report + "." + fmt);
    }

    function downloadCsv(path, name) {
        var s = LSI_Auth.getSession();
        fetch(LSI_Auth.apiBase + path, { headers: { Authorization: "Bearer " + (s ? s.token : "") } }).then(function (r) {
            if (!r.ok) throw new Error("Export failed (" + r.status + ")"); return r.blob();
        }).then(function (b) { var u = URL.createObjectURL(b); var a = document.createElement("a"); a.href = u; a.download = name; document.body.appendChild(a); a.click(); a.remove(); setTimeout(function () { URL.revokeObjectURL(u); }, 5000); })
            .catch(function (e) { toast(e.message, false); });
    }

    // ---- modal form ---------------------------------------------------------------------------

    function openForm(title, html, onSubmit, submitLabel, size) {
        el("formModalTitle").innerText = title; el("formModalBody").innerHTML = html;
        el("formModalError").classList.add("d-none"); el("formModalSubmit").innerText = submitLabel || "Save";
        el("formModalDialog").className = "modal-dialog modal-dialog-centered " + (size || "");
        formSubmitHandler = onSubmit; formModal.show();
    }
    function formError(msg) { var e = el("formModalError"); e.innerText = msg; e.classList.remove("d-none"); }
    function vals() { var o = {}; el("formModalForm").querySelectorAll("[name]").forEach(function (i) { o[i.name] = i.type === "checkbox" ? i.checked : i.value; }); return o; }
    function fld(name, label, value, o) {
        o = o || {};
        var attrs = (o.required ? " required" : "") + (o.max ? ' maxlength="' + o.max + '"' : "") + (o.placeholder ? ' placeholder="' + esc(o.placeholder) + '"' : "") + (o.step ? ' step="' + o.step + '"' : "") + (o.min != null ? ' min="' + o.min + '"' : "") +
            (o.maxv != null ? ' max="' + esc(o.maxv) + '"' : "") + (o.pattern ? ' pattern="' + esc(o.pattern) + '"' : "") + (o.title ? ' title="' + esc(o.title) + '"' : "");
        var inner;
        if (o.type === "select") inner = '<select name="' + name + '"' + attrs + '>' + o.options + '</select>';
        else if (o.type === "textarea") inner = '<textarea name="' + name + '" rows="3"' + attrs + '>' + esc(value || "") + '</textarea>';
        else if (o.type === "checkbox") inner = '<label class="d-flex align-items-center gap-2 fw-normal" style="font-size:14px;color:inherit"><input type="checkbox" name="' + name + '" style="width:auto"' + (value ? " checked" : "") + '> ' + esc(label) + '</label>';
        else inner = '<input type="' + (o.type || "text") + '" name="' + name + '" value="' + esc(value == null ? "" : value) + '"' + attrs + (o.readonly ? ' readonly class="readonly"' : "") + '>';
        return '<div class="fld">' + (o.type === "checkbox" ? "" : '<label>' + esc(label) + '</label>') + inner + (o.hint ? '<div class="hint">' + esc(o.hint) + '</div>' : "") + '</div>';
    }

    // ---- navigation ---------------------------------------------------------------------------

    function show(view, arg) {
        document.querySelectorAll(".au-view").forEach(function (v) { v.classList.remove("active"); });
        var t = el("view-" + view); if (t) t.classList.add("active");
        document.querySelectorAll(".au-nav a[data-view]").forEach(function (a) { a.classList.toggle("active", a.dataset.view === view); });
        el("sidebar").classList.remove("open"); el("backdrop").classList.remove("show");
        window.scrollTo(0, 0);
        var loaders = { dashboard: loadDashboard, students: function () { loadStudents(0); }, student: function () { openStudent(arg); },
            payments: loadPaymentsView, receipts: function () { loadReceipts(0, arg); }, attendance: loadAttendanceView,
            masters: loadMasters, reports: runReport,
            communication: loadCommunicationView, "comm-history": function () { loadHistory(0); },
            "pending-fees": loadPendingFees, "course-price": loadCoursePrice,
            settings: loadStudentIdSeries };
        if (loaders[view]) loaders[view]();
    }
    function route() {
        var h = (location.hash || "#dashboard").slice(1).split("/");
        if (h[0] === "student" && h[1]) show("student", h[1]);
        else if (h[0] === "receipts" && h[1]) show("receipts", h[1]);
        else show(h[0] && el("view-" + h[0]) ? h[0] : "dashboard");   // unknown / removed views (e.g. old #import links) open the dashboard
    }
    function go(view, arg) { location.hash = view + (arg ? "/" + arg : ""); }

    // ---- lookups --------------------------------------------------------------------------------

    function loadLookups() {
        return api("/automation/lookups").then(function (res) {
            if (!handle(res, false)) return null;
            L = res.data;
            var active = L.batches.filter(function (b) { return b.active; });
            var courses = L.courses.filter(function (c) { return c.active; });
            var modes = L.paymentModes.filter(function (m) { return m.active; });
            ["dashBatch", "stuBatch", "payListBatch", "sessBatchFilter", "repBatch", "pfBatch"].forEach(function (id) { el(id).innerHTML = opts(L.batches, "id", function (b) { return b.name + (b.active ? "" : " (inactive)"); }, "", id === "dashBatch" ? "All Batches" : "All"); });
            el("attBatch").innerHTML = opts(active, "id", function (b) { return b.name + (b.schedule ? " · " + b.schedule : ""); }, "", "Select batch");
            ["stuCourse", "repCourse", "pfCourse"].forEach(function (id) { el(id).innerHTML = opts(L.courses, "id", function (c) { return c.name; }, "", "All"); });
            el("payMode").innerHTML = opts(modes, "code", function (m) { return m.label; }, "CASH", "Select mode");
            ["payListMode", "repMode"].forEach(function (id) { el(id).innerHTML = opts(L.paymentModes, "code", function (m) { return m.label; }, "", "All Modes"); });
            el("attType").innerHTML = opts(L.sessionTypes.filter(function (t) { return t.active; }), "code", function (t) { return t.label; }, "CLASS");
            el("stuYear").innerHTML = opts(L.admissionYears, null, null, "", "All");
            return L;
        });
    }

    // ---- dashboard ------------------------------------------------------------------------------

    function loadDashboard() {
        api("/automation/dashboard").then(function (res) {
            if (!handle(res, false)) return;
            var s = res.data;
            var cards = [
                ["Total Students", s.activeStudents, "fa-users", "c-blue"],
                ["Total Fees", inr(s.totalFees), "fa-rupee-sign", "c-green"],
                ["Total Paid", inr(s.totalPaid), "fa-rupee-sign", "c-red"],
                ["Total Balance", inr(s.totalBalance), "fa-hourglass-half", "c-amber"],
                ["Paid Students", s.paidStudents, "fa-check-circle", "c-green"],
                ["Partial Payment", s.partialStudents, "fa-adjust", "c-amber"],
                ["Pending Payment", s.pendingStudents, "fa-exclamation-circle", "c-red"],
                ["Attendance Rate", s.attendanceRate == null ? "—" : s.attendanceRate + "%", "fa-calendar-check", "c-teal"]
            ];
            el("statCards").innerHTML = cards.map(function (c) { return '<div class="au-stat ' + c[3] + '"><div class="ico"><i class="fas ' + c[2] + '"></i></div><div><div class="lbl">' + c[0] + '</div><div class="val">' + esc(c[1]) + '</div></div></div>'; }).join("");
            var att = [];
            s.pendingBalances.slice(0, 5).forEach(function (r) { att.push({ t: esc(r.fullName) + " · balance " + inr(r.balance), s: r.studentId + " · " + esc(r.batchName || ""), id: r.id, cls: "pending" }); });
            s.lowAttendance.slice(0, 5).forEach(function (r) { att.push({ t: esc(r.fullName) + " · attendance " + pct(r.attendancePercent), s: r.studentId + " · " + r.sessionsPresent + "/" + r.sessionsTotal + " sessions", id: r.id, cls: "partial" }); });
            el("dashAttention").innerHTML = att.length ? att.map(function (a) { return '<div class="au-notif-row d-flex justify-content-between align-items-center p-2 border-bottom" style="cursor:pointer" data-student="' + a.id + '"><div><div class="fw-semibold" style="font-size:13px">' + a.t + '</div><small class="text-muted">' + a.s + '</small></div><i class="fas fa-chevron-right text-muted"></i></div>'; }).join("") : '<div class="au-empty">Everything is up to date.</div>';
            bindStudentLinks(el("dashAttention"));
            var n = s.studentsNeedingReview + s.lowAttendanceStudents;
            el("bellCount").innerText = n; el("bellCount").classList.toggle("d-none", n === 0);
            el("notifPanel").innerHTML = (s.studentsNeedingReview ? '<div class="item" data-go="students"><strong>' + s.studentsNeedingReview + '</strong> imported record(s) need review (flagged on the student)</div>' : "") +
                (s.lowAttendanceStudents ? '<div class="item" data-go="reports"><strong>' + s.lowAttendanceStudents + '</strong> student(s) below 75% attendance</div>' : "") +
                (s.pendingStudents ? '<div class="item" data-go="students"><strong>' + s.pendingStudents + '</strong> student(s) with no payment yet</div>' : "") +
                '<div class="item text-muted">Collected today ' + inr(s.collectedToday) + ' · this month ' + inr(s.collectedThisMonth) + ' · ' + s.receiptsIssued + ' receipts issued</div>';
            el("notifPanel").querySelectorAll("[data-go]").forEach(function (i) { i.addEventListener("click", function () { el("notifPanel").style.display = "none"; go(i.dataset.go); }); });
            if (s.recentPayments.length && s.recentPayments[0].receiptId) {
                api("/automation/receipts/" + s.recentPayments[0].receiptId).then(function (r) { if (r.success) el("dashReceipt").innerHTML = receiptHtml(r.data, true); });
            }
        });
        loadDashStudents(0);
    }

    function loadDashStudents(page) {
        state.dashPage = page;
        api("/automation/students?" + qs({ batchId: el("dashBatch").value, paymentStatus: el("dashPayStatus").value, page: page, size: 10 })).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data, base = p.number * p.size;
            el("dashStudentsBody").innerHTML = p.content.map(function (r, i) {
                return '<tr><td>' + (base + i + 1) + '</td><td class="mono">' + esc(r.studentId) + '</td><td><a href="#student/' + r.id + '" class="au-link">' + esc(r.fullName) + '</a>' + (r.reviewNote ? ' <i class="fas fa-flag text-warning" title="' + esc(r.reviewNote) + '"></i>' : "") + '</td><td>' + esc(r.batchName || "—") + '</td><td class="num">' + feeCell(r) + '</td><td class="num">' + inr(r.totalPaid) + '</td><td class="num">' + inr(r.balance) + '</td><td>' + badge(r.paymentStatus) + '</td><td>' + attCell(r) + '</td><td><button class="au-view-btn" data-student="' + r.id + '">View</button></td></tr>';
            }).join("") || '<tr><td colspan="10" class="au-empty">No students yet. Click Add Student.</td></tr>';
            bindStudentLinks(el("dashStudentsBody"));
            pager("dashPager", p, loadDashStudents);
        });
    }

    function bindStudentLinks(root) { root.querySelectorAll("[data-student]").forEach(function (b) { b.addEventListener("click", function (e) { e.preventDefault(); go("student", b.dataset.student); }); }); }

    // ---- students -------------------------------------------------------------------------------

    function studentFilters() { return { q: el("stuQ").value.trim(), batchId: el("stuBatch").value, courseId: el("stuCourse").value, paymentStatus: el("stuPay").value, year: el("stuYear").value, status: el("stuStatus").value }; }

    function loadStudents(page) {
        state.stuPage = page;
        var f = studentFilters(); f.page = page; f.size = 25;
        api("/automation/students?" + qs(f)).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("studentsCount").innerText = p.totalElements + " student(s)";
            el("studentsBody").innerHTML = p.content.map(function (r) {
                return '<tr><td class="mono">' + esc(r.studentId) + '</td><td><a href="#student/' + r.id + '" class="au-link">' + esc(r.fullName) + '</a>' + (r.reviewNote ? ' <i class="fas fa-flag text-warning" title="' + esc(r.reviewNote) + '"></i>' : "") + '</td><td>' + esc(r.batchName || "—") + '</td><td>' + esc(r.courseName || "—") + '</td><td>' + (r.admissionDate ? d(r.admissionDate) : '<span class="text-danger" title="Needs correction">' + esc(r.admissionDateRaw || "—") + '</span>') + '</td><td>' + esc(r.mobile || "—") + '</td><td class="num">' + feeCell(r) + '</td><td class="num">' + inr(r.totalPaid) + '</td><td class="num">' + inr(r.balance) + '</td><td>' + badge(r.paymentStatus) + (r.status === "ARCHIVED" ? " " + badge("ARCHIVED") : "") + '</td><td>' + attCell(r) + '</td>' +
                    '<td class="text-nowrap"><button class="au-view-btn" data-student="' + r.id + '">View</button> <button class="au-btn light sm" data-pay="' + r.id + '" title="Record payment"><i class="fas fa-rupee-sign"></i></button> <button class="au-btn light sm" data-edit="' + r.id + '" title="Edit"><i class="fas fa-edit"></i></button></td></tr>';
            }).join("") || '<tr><td colspan="12" class="au-empty">No students match these filters.</td></tr>';
            bindStudentLinks(el("studentsBody"));
            el("studentsBody").querySelectorAll("[data-pay]").forEach(function (b) { b.addEventListener("click", function () { startPaymentFor(b.dataset.pay); }); });
            el("studentsBody").querySelectorAll("[data-edit]").forEach(function (b) { b.addEventListener("click", function () { api("/automation/students/" + b.dataset.edit).then(function (r) { if (r.success) openStudentForm(r.data); }); }); });
            pager("studentsPager", p, loadStudents);
        });
    }

    // ---- student admission form --------------------------------------------------------------------
    // Name, date, mobile, email, address and course are the existing student fields; the rest of the
    // admission form is optional (older and imported records have none of it) and is validated by the API.

    var ADMISSION = {
        gender: [{ v: "MALE", l: "Male" }, { v: "FEMALE", l: "Female" }, { v: "OTHER", l: "Other" }],
        employment: [{ v: "EMPLOYED", l: "Employed" }, { v: "SELF_EMPLOYED", l: "Self-Employed" }, { v: "UNEMPLOYED", l: "Unemployed" }, { v: "STUDENT", l: "Student" }, { v: "OTHER", l: "Other" }],
        yesNo: [{ v: "YES", l: "Yes" }, { v: "NO", l: "No" }],
        payment: [{ v: "CREDIT_CARD", l: "Credit Card" }, { v: "BANK_TRANSFER", l: "Bank Transfer" }, { v: "CASH", l: "Cash" }]
    };
    // Sent only when filled in; a blank value clears the saved one.
    var ADMISSION_OPTIONAL = ["dateOfBirth", "gender", "yearOfCompletion", "employmentStatus", "annualIncome", "stockMarketExperience",
        "stockMarketYears", "tradingExperience", "tradingYears", "preferredStartDate", "paymentMethod"];
    var NAME_PATTERN = "[\\p{L}][\\p{L}\\p{M} .'\\-]*";
    /** 12 digits, not starting with 0 or 1 (spaces allowed, as printed on the card); the server also checks the check digit. */
    var AADHAAR_PATTERN = "[2-9][0-9]{3} ?[0-9]{4} ?[0-9]{4}";

    /** Sr. No. is the serial of the existing Student ID series (LSA/2026/0016 -> 0016), never a second identifier. */
    function srNo(studentId) { var m = String(studentId || "").match(/(\d+)$/); return m ? m[1] : ""; }
    function choice(list, selected) { return opts(list, "v", function (x) { return x.l; }, selected == null ? "" : selected, "Select"); }
    function optLabel(list, v) { var x = list.find(function (i) { return i.v === v; }); return x ? x.l : v; }
    function yesNo(b) { return b === true ? "YES" : b === false ? "NO" : ""; }
    function formSection(title) { return '<div class="au-sec">' + esc(title) + '</div>'; }

    function openStudentForm(existing) {
        var course = existing ? existing.courseId : (L.courses[0] ? L.courses[0].id : "");
        var a = (existing && existing.admission) || {};
        var yearMax = new Date().getFullYear() + 10;
        var yearsOpts = { type: "number", min: 0, maxv: 60, step: "0.1", placeholder: "e.g. 2" };
        // Address and E-mail ID are compulsory for a new admission; editing an older record keeps them optional.
        var adding = !existing;
        var html =
            '<div class="hint mb-3">' + (adding ? "Required: Full name, Date, Address, Contact No., E-mail ID, Aadhaar Number, Course and Batch." : "Required: Full name, Date, Contact No., Aadhaar Number, Course and Batch.") + ' The other admission details can be filled in now or later.</div>' +
            formSection("Personal Information") +
            '<div class="row-2">' + fld("srNo", "Sr. No.", srNo(existing ? existing.studentId : L.nextStudentId), { readonly: true, hint: existing ? "" : "Follows the Student ID series." }) +
            (existing ? fld("studentId", "Student ID", existing.studentId, { readonly: true }) : fld("studentId", "Student ID (generated automatically)", L.nextStudentId, { readonly: true, hint: "Assigned on save — never typed by hand." })) + '</div>' +
            (adding ? '<div class="au-series-bar"><button type="button" class="au-btn light sm" id="sfSeriesToggle"><i class="fas fa-sliders-h"></i> Change Student ID series</button></div>' +
                '<div class="au-series d-none" id="sfSeries"><label for="sfSeriesInput">Next Student ID</label>' +
                '<div class="d-flex flex-wrap gap-2"><input type="text" id="sfSeriesInput" maxlength="30" autocomplete="off" style="flex:1 1 220px" placeholder="e.g. LSA/2026/0100 or LSA-B3-0001">' +
                '<button type="button" class="au-btn primary sm" id="sfSeriesApply">Set series</button><button type="button" class="au-btn ghost sm" id="sfSeriesCancel">Cancel</button></div>' +
                '<div class="hint">Type the Student ID the next student should get; later students continue from it (…0101, …0102). It must be higher than any ID already used with that prefix, so no ID is ever issued twice. Existing Student IDs do not change.</div></div>' : "") +
            '<div class="row-2">' + fld("admissionDate", "Date (admission date)", existing ? (existing.admissionDate || "") : today(), { type: "date", required: true, hint: existing && existing.admissionDateRaw ? "Workbook value '" + existing.admissionDateRaw + "' could not be read — enter the correct date." : "" }) +
            fld("fullName", "Full name", existing && existing.fullName, { required: true, max: 150, pattern: NAME_PATTERN, title: "Letters, spaces, dots, apostrophes and hyphens only." }) + '</div>' +
            fld("address", adding ? "Address" : "Address (optional)", existing && existing.address, { type: "textarea", max: 500, required: adding }) +
            '<div class="row-2">' + fld("dateOfBirth", "Date of Birth", a.dateOfBirth, { type: "date", maxv: today() }) +
            fld("gender", "Gender", null, { type: "select", options: choice(ADMISSION.gender, a.gender) }) + '</div>' +
            '<div class="row-2">' + fld("mobile", "Contact No. (10-digit mobile)", existing && existing.mobile, { required: true, max: 10, placeholder: "98765 43210" }) +
            fld("email", adding ? "E-mail ID" : "E-mail ID (optional)", existing && existing.email, { type: "email", max: 190, required: adding }) + '</div>' +
            '<div class="row-2">' + aadhaarField(existing) + '<div></div></div>' +

            formSection("Educational Background") +
            '<div class="row-2">' + fld("highestQualification", "Highest Qualification", a.highestQualification, { max: 100, placeholder: "e.g. B.Com, HSC" }) +
            fld("instituteName", "Name of the Institute", a.instituteName, { max: 150 }) + '</div>' +
            '<div class="row-2">' + fld("yearOfCompletion", "Year of Completion", a.yearOfCompletion, { type: "number", min: 1950, maxv: yearMax, step: "1", placeholder: "e.g. 2019" }) + '<div></div></div>' +

            formSection("Employment Information") +
            '<div class="row-2">' + fld("employmentStatus", "Current Employment Status", null, { type: "select", options: choice(ADMISSION.employment, a.employmentStatus) }) +
            fld("currentEmployer", "Current Employer (if applicable)", a.currentEmployer, { max: 150 }) + '</div>' +
            '<div class="row-2">' + fld("jobTitle", "Job Title/Role (if applicable)", a.jobTitle, { max: 100 }) +
            fld("annualIncome", "Annual Income (in INR)", a.annualIncome, { type: "number", min: 0, step: "0.01", placeholder: "e.g. 500000" }) + '</div>' +

            formSection("Trading Experience") +
            '<div class="row-2">' + fld("stockMarketExperience", "Stock Market Experience", null, { type: "select", options: choice(ADMISSION.yesNo, yesNo(a.stockMarketExperience)) }) +
            fld("stockMarketYears", "Number of Years", a.stockMarketYears, yearsOpts) + '</div>' +
            '<div class="row-2">' + fld("tradingExperience", "Previous Trading Experience", null, { type: "select", options: choice(ADMISSION.yesNo, yesNo(a.tradingExperience)) }) +
            fld("tradingYears", "Number of Years", a.tradingYears, yearsOpts) + '</div>' +
            '<div class="row-2">' + fld("tradingPlatforms", "Trading Platform(s) Used (if applicable)", a.tradingPlatforms, { max: 255, placeholder: "e.g. Zerodha Kite, Upstox" }) +
            fld("tradingStrategies", "Trading Strategies Employed (if applicable)", a.tradingStrategies, { max: 500 }) + '</div>' +

            formSection("Course Information") +
            '<div class="row-2">' + fld("courseId", "Course Applied For", null, { type: "select", required: true, options: opts(L.courses.filter(function (c) { return c.active || (existing && c.id === existing.courseId); }), "id", function (c) { return c.name + " (" + inr(c.defaultFee) + ")"; }, course, "Select course") }) +
            fld("batchId", "Batch", null, { type: "select", required: true, options: opts(L.batches.filter(function (b) { return b.active || (existing && b.id === existing.batchId); }), "id", function (b) { return b.name + (b.schedule ? " · " + b.schedule : ""); }, existing ? existing.batchId : "", "Select batch") }) + '</div>' +
            '<div class="row-2">' + fld("preferredStartDate", "Preferred Start Date", a.preferredStartDate, { type: "date" }) +
            fld("paymentMethod", "Payment Method", null, { type: "select", options: choice(ADMISSION.payment, a.paymentMethod) }) + '</div>' +
            '<div class="row-2">' + fld("courseFee", "Course fee (₹)", existing ? existing.courseFee : "", { type: "number", step: "0.01", min: 0, hint: "Blank = the course's default fee." }) + '<div></div></div>' +
            (existing && existing.reviewNote ? '<div class="alert alert-warning small py-2 mb-0"><i class="fas fa-flag me-1"></i> Review note from import: ' + esc(existing.reviewNote) + '<br>Saving this form clears the flag.</div>' : "");
        openForm(existing ? "Edit Student" : "Add Student", html, function (v) {
            v.mobile = String(v.mobile || "").replace(/\D/g, "");
            v.aadhaarNo = String(v.aadhaarNo || "").replace(/[\s-]/g, "");   // blank when editing = keep the saved number
            if (v.courseFee === "") delete v.courseFee;
            delete v.studentId; delete v.srNo;
            ["stockMarketExperience", "tradingExperience"].forEach(function (k) { if (v[k] !== "") v[k] = v[k] === "YES"; });
            ADMISSION_OPTIONAL.forEach(function (k) { if (v[k] === "") delete v[k]; });
            return api(existing ? "/automation/students/" + existing.id : "/automation/students", { method: existing ? "PUT" : "POST", body: v }).then(function (res) {
                if (handle(res)) { formModal.hide(); loadLookups(); existing ? openStudent(existing.id) : go("student", res.data.id); }
                else formError(errText(res));
            });
        }, existing ? "Save Changes" : "Create Student", "modal-lg");
        var form = el("formModalForm");
        form.querySelector('[name="courseId"]').addEventListener("change", function () {
            var c = L.courses.find(function (x) { return String(x.id) === this.value; }.bind(this));
            var fee = form.querySelector('[name="courseFee"]');
            if (c && !fee.value) fee.placeholder = "Default " + c.defaultFee;
        });
        // "Number of Years" only applies when the answer is Yes.
        [["stockMarketExperience", "stockMarketYears"], ["tradingExperience", "tradingYears"]].forEach(function (p) {
            var answer = form.querySelector('[name="' + p[0] + '"]'), years = form.querySelector('[name="' + p[1] + '"]');
            function sync() { var on = answer.value === "YES"; years.disabled = !on; if (!on) years.value = ""; }
            answer.addEventListener("change", sync); sync();
        });
        if (adding) bindStudentIdSeries(form);
    }

    /**
     * Add Student > "Change Student ID series": saves the typed next ID as the series (same
     * endpoint as Settings > Student ID Series) and refreshes the ID shown in the open form.
     * The inputs have no name, so they are never sent with the student itself.
     */
    function bindStudentIdSeries(form) {
        var box = el("sfSeries"), input = el("sfSeriesInput"), apply = el("sfSeriesApply");
        el("sfSeriesToggle").addEventListener("click", function () {
            box.classList.toggle("d-none");
            if (!box.classList.contains("d-none")) { input.value = form.querySelector('[name="studentId"]').value; input.focus(); input.select(); }
        });
        el("sfSeriesCancel").addEventListener("click", function () { box.classList.add("d-none"); });
        function save() {
            var next = input.value.trim();
            if (!next) { formError("Enter the next Student ID, e.g. LSA/2026/0100."); return; }
            apply.disabled = true;
            api("/automation/settings/student-id-sequence", { method: "PUT", body: { nextStudentId: next } }).then(function (r) {
                apply.disabled = false;
                if (!r.success) { formError(errText(r)); return; }
                L.nextStudentId = r.data.nextStudentId;
                form.querySelector('[name="studentId"]').value = r.data.nextStudentId;
                form.querySelector('[name="srNo"]').value = srNo(r.data.nextStudentId);
                box.classList.add("d-none");
                el("formModalError").classList.add("d-none");
                toast(r.message || "Student ID series updated.", true);
            });
        }
        apply.addEventListener("click", save);
        // Enter here sets the series; it must not submit (create) the student.
        input.addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); save(); } });
    }

    /** Admission tab of the student profile: every admission-form field, grouped like the form. */
    function admissionHtml(s) {
        var a = s.admission || {};
        var exp = function (flag, n) { return flag == null ? null : flag ? "Yes" + (n != null ? " · " + n + (Number(n) === 1 ? " year" : " years") : "") : "No"; };
        var groups = [
            ["Personal Information", [["Sr. No.", srNo(s.studentId)], ["Date", s.admissionDate ? d(s.admissionDate) : (s.admissionDateRaw ? "⚠ " + s.admissionDateRaw : null)], ["Full Name", s.fullName], ["Address", s.address],
                ["Date of Birth", a.dateOfBirth ? d(a.dateOfBirth) : null], ["Gender", optLabel(ADMISSION.gender, a.gender)], ["Contact No.", s.mobile], ["E-mail ID", s.email]]],
            ["Educational Background", [["Highest Qualification", a.highestQualification], ["Name of the Institute", a.instituteName], ["Year of Completion", a.yearOfCompletion]]],
            ["Employment Information", [["Current Employment Status", optLabel(ADMISSION.employment, a.employmentStatus)], ["Current Employer", a.currentEmployer], ["Job Title/Role", a.jobTitle],
                ["Annual Income (INR)", a.annualIncome != null ? inr(a.annualIncome) : null]]],
            ["Trading Experience", [["Stock Market Experience", exp(a.stockMarketExperience, a.stockMarketYears)], ["Previous Trading Experience", exp(a.tradingExperience, a.tradingYears)],
                ["Trading Platform(s) Used", a.tradingPlatforms], ["Trading Strategies Employed", a.tradingStrategies]]],
            ["Course Information", [["Course Applied For", s.courseName], ["Batch", s.batchName], ["Preferred Start Date", a.preferredStartDate ? d(a.preferredStartDate) : null],
                ["Payment Method", optLabel(ADMISSION.payment, a.paymentMethod)]]]
        ];
        var notFilled = Object.keys(a).every(function (k) { return a[k] == null; });
        return (notFilled ? '<div class="alert alert-info small py-2"><i class="fas fa-info-circle me-1"></i> The admission form details have not been filled in for this student yet — use <strong>Edit</strong> to add them.</div>' : "") +
            groups.map(function (g) {
                return formSection(g[0]) + '<div class="au-kv mb-3">' + g[1].map(function (x) { return '<div><span>' + x[0] + '</span><strong>' + esc(x[1] == null || x[1] === "" ? "—" : x[1]) + '</strong></div>'; }).join("") + '</div>';
            }).join("");
    }

    // ---- student profile ---------------------------------------------------------------------------

    function openStudent(id) {
        el("studentProfile").innerHTML = '<div class="au-empty">Loading…</div>';
        api("/automation/students/" + id + "/profile").then(function (res) {
            if (!res.success) { el("studentProfile").innerHTML = '<div class="au-error">' + esc(res.message) + '</div>'; return; }
            var p = res.data, s = p.student, f = p.fees, a = p.attendance;
            var head = '<div class="au-card pad mb-3"><div class="d-flex flex-wrap justify-content-between align-items-start gap-3">' +
                '<div><div class="d-flex align-items-center gap-2 flex-wrap"><h4 class="fw-bold mb-0" style="color:var(--au-navy)">' + esc(s.fullName) + '</h4>' + badge(s.paymentStatus) + (s.status === "ARCHIVED" ? badge("ARCHIVED") : "") + '</div>' +
                '<div class="text-muted mt-1">Student ID <strong class="mono" style="color:var(--au-navy)">' + esc(s.studentId) + '</strong> · Batch <strong>' + esc(s.batchName || "—") + '</strong> · ' + esc(s.courseName || "—") + (p.fees.feeReceiptNo ? ' · Fee Receipt No <strong class="mono" style="color:var(--au-navy)">' + esc(p.fees.feeReceiptNo) + '</strong>' : "") + '</div>' +
                (s.reviewNote ? '<div class="alert alert-warning small py-2 mt-2 mb-0"><i class="fas fa-flag me-1"></i> Needs review: ' + esc(s.reviewNote) + ' — open Edit to correct it.</div>' : "") + '</div>' +
                '<div class="au-actions"><button class="au-btn ghost" id="spEdit"><i class="fas fa-edit"></i> Edit</button><button class="au-btn ghost" id="spDiscount" title="Give this student a discount on the course fee"><i class="fas fa-percent"></i> Discount</button><button class="au-btn success" id="spPay"><i class="fas fa-rupee-sign"></i> Record Payment</button><button class="au-btn primary" id="spReceipt"' + (p.receipts.length ? "" : " disabled") + '><i class="fas fa-file-invoice"></i> Latest Receipt</button><button class="au-btn ghost" id="spAtt"><i class="fas fa-calendar-check"></i> Mark Attendance</button>' +
                '<button class="au-btn ' + (s.status === "ARCHIVED" ? "success" : "danger") + '" id="spArchive">' + (s.status === "ARCHIVED" ? '<i class="fas fa-undo"></i> Restore' : '<i class="fas fa-archive"></i> Archive') + '</button></div></div></div>';
            var tabs = ["Overview", "Admission", "Fees", "Ebook Login", "Payments", "Receipts", "Attendance", "Course", "Documents", "Activity"];
            var body = '<div class="au-card"><div class="au-tabs">' + tabs.map(function (t, i) { return '<button data-tab="' + i + '" class="' + (i === 0 ? "on" : "") + '">' + t + '</button>'; }).join("") + '</div><div class="p-3" id="spTab"></div></div>';
            el("studentProfile").innerHTML = head + body;
            var panes = [
                function () {
                    var kv = [["Student ID", s.studentId], ["Name", s.fullName], ["Batch", s.batchName], ["Admission Date", s.admissionDate ? d(s.admissionDate) : (s.admissionDateRaw ? "⚠ " + s.admissionDateRaw : "—")], ["Mobile", s.mobile], ["Email", s.email], ["Aadhaar", s.aadhaarMasked || "Not recorded yet — add it with Edit"], ["Address", s.address], ["Course", s.courseName],
                        ["Course Fee", inr(s.courseFee)]].concat(s.discount > 0 ? [["Discount", "− " + inr(s.discount)], ["Fee after Discount", inr(s.netFee)]] : []).concat([ ["Paid", inr(s.totalPaid)], ["Balance", inr(s.balance)], ["Payment Status", s.paymentStatus], ["Attendance", a.sessionsTotal ? a.present + a.late + " / " + (a.sessionsTotal - a.excused) + " · " + pct(a.percent) : "No sessions yet"],
                        ["Last Payment", s.lastPaymentDate ? d(s.lastPaymentDate) : "—"], ["Last Attendance", a.lastAttendance ? d(a.lastAttendance) : "—"], ["Consecutive Absences", a.consecutiveAbsences], ["Record Source", s.source + " · created " + dt(s.createdAt)]]);
                    return '<div class="au-kv">' + kv.map(function (x) { return '<div><span>' + x[0] + '</span><strong>' + esc(x[1] == null || x[1] === "" ? "—" : x[1]) + '</strong></div>'; }).join("") + '</div>';
                },
                function () { return admissionHtml(s); },
                function () {
                    var paidPct = f.netFee > 0 ? Math.min(100, Math.round(f.totalPaid * 100 / f.netFee)) : 0;
                    return feeStatsHtml(f) + discountBoxHtml(f) +
                        '<div class="au-progress mb-3"><span style="width:' + paidPct + '%"></span></div>' +
                        '<div class="au-table-wrap"><table class="au-table"><thead><tr><th>Installment</th><th>Status</th><th>Date</th><th class="num">Amount</th><th>Payment No</th><th>Receipt No</th></tr></thead><tbody>' + f.installments.map(function (i) { return '<tr><td>' + esc(i.label) + '</td><td>' + (i.paid ? badge("PAID") : (i.installmentNo === f.nextInstallmentNo ? badge("PENDING") : '<span class="text-muted">—</span>')) + '</td><td>' + (i.paymentDate ? d(i.paymentDate) : "—") + '</td><td class="num">' + (i.amount != null ? inr(i.amount) : "—") + '</td><td class="mono">' + esc(i.paymentNo || "—") + '</td><td>' + (i.receiptId ? '<a href="#receipts/' + i.receiptId + '" class="au-link mono">' + esc(i.receiptNo) + '</a>' : "—") + '</td></tr>'; }).join("") + '</tbody></table></div>' +
                        (f.feeReceiptNo ? '<div class="text-muted small mb-2"><i class="fas fa-hashtag me-1"></i>Fee Receipt No. <strong class="mono">' + esc(f.feeReceiptNo) + '</strong> — the same number is printed on every installment receipt for this student (' + f.installmentsRecorded + ' recorded so far).</div>' : "") +
                        (f.nextInstallmentNo ? '<div class="mt-3"><button class="au-btn success" id="spPay2"><i class="fas fa-plus"></i> Record ' + esc(f.installments[f.nextInstallmentNo - 1] ? f.installments[f.nextInstallmentNo - 1].label : "next installment") + '</button></div>' : '<div class="mt-3 text-success fw-semibold"><i class="fas fa-check-circle"></i> Fully paid.</div>');
                },
                function () { return '<div id="spEbook"><div class="au-empty">Loading…</div></div>'; },
                function () { return paymentsTable(p.payments, true); },
                function () { return p.receipts.length ? '<div class="au-table-wrap"><table class="au-table"><thead><tr><th>Receipt No</th><th>Payment No</th><th>Date</th><th>Installment</th><th class="num">Paid</th><th class="num">Balance</th><th>Mode</th><th>Emailed</th><th></th></tr></thead><tbody>' + p.receipts.map(function (r) { return '<tr><td class="mono">' + esc(r.receiptNo) + '</td><td class="mono">' + esc(r.paymentNo) + '</td><td>' + d(r.paymentDate) + '</td><td>' + esc(ordinal(r.installmentNo)) + ' Installment</td><td class="num">' + inr(r.amountPaid) + '</td><td class="num">' + inr(r.balance) + '</td><td>' + esc(r.paymentMode) + '</td><td>' + (r.emailedAt ? d(r.emailedAt) : "—") + '</td><td><button class="au-view-btn" data-receipt="' + r.id + '">Open</button></td></tr>'; }).join("") + '</tbody></table></div>' : '<div class="au-empty">No receipts yet.</div>'; },
                function () {
                    return '<div class="row g-3 mb-3"><div class="col-md-3"><div class="au-stat c-teal"><div class="ico"><i class="fas fa-calendar-check"></i></div><div><div class="lbl">Attendance</div><div class="val">' + (a.sessionsTotal ? a.present + a.late + " / " + (a.sessionsTotal - a.excused) : "—") + '</div></div></div></div><div class="col-md-3"><div class="au-stat ' + (a.percent != null && a.percent < 75 ? "c-red" : "c-green") + '"><div class="ico"><i class="fas fa-percent"></i></div><div><div class="lbl">Rate</div><div class="val">' + pct(a.percent) + '</div></div></div></div><div class="col-md-3"><div class="au-stat c-amber"><div class="ico"><i class="fas fa-user-times"></i></div><div><div class="lbl">Absent</div><div class="val">' + a.absent + '</div></div></div></div><div class="col-md-3"><div class="au-stat c-gray"><div class="ico"><i class="fas fa-redo"></i></div><div><div class="lbl">Consecutive absences</div><div class="val">' + a.consecutiveAbsences + '</div></div></div></div></div>' +
                        (p.attendanceHistory.length ? '<div class="au-table-wrap"><table class="au-table"><thead><tr><th>Date</th><th>Batch</th><th>Type</th><th>Status</th></tr></thead><tbody>' + p.attendanceHistory.map(function (h) { return '<tr><td>' + d(h.sessionDate) + '</td><td>' + esc(h.batchName) + '</td><td>' + esc(h.sessionType) + '</td><td>' + badge(h.status) + '</td></tr>'; }).join("") + '</tbody></table></div>' : '<div class="au-empty">No attendance recorded yet.</div>');
                },
                function () { var c = L.courses.find(function (x) { return x.id === s.courseId; }); return '<div class="au-kv"><div><span>Course</span><strong>' + esc(s.courseName || "—") + '</strong></div><div><span>Batch</span><strong>' + esc(s.batchName || "—") + '</strong></div><div><span>Course fee for this student</span><strong>' + inr(s.courseFee) + '</strong></div><div><span>Course default fee</span><strong>' + (c ? inr(c.defaultFee) : "—") + '</strong></div><div><span>Duration</span><strong>' + esc(c && c.duration ? c.duration : "—") + '</strong></div></div>'; },
                function () { return '<div class="au-empty"><i class="fas fa-folder-open fa-2x mb-2 d-block"></i>Document storage is not part of the office workbook yet.<br><small>Receipts are available under the Receipts tab; other documents can be added in a later phase.</small></div>'; },
                function () { return p.activity.length ? '<div class="au-table-wrap"><table class="au-table"><thead><tr><th>When</th><th>Action</th><th>Details</th><th>By</th></tr></thead><tbody>' + p.activity.map(function (x) { return '<tr><td class="text-nowrap">' + dt(x.at) + '</td><td>' + badge(x.action.replace(/^ACAD_/, "")) + '</td><td>' + esc(x.description) + '</td><td>' + esc(x.actor) + '</td></tr>'; }).join("") + '</tbody></table></div>' : '<div class="au-empty">No activity yet.</div>'; }
            ];
            function showTab(i) {
                el("studentProfile").querySelectorAll("[data-tab]").forEach(function (b) { b.classList.toggle("on", Number(b.dataset.tab) === i); });
                el("spTab").innerHTML = panes[i]();
                el("spTab").querySelectorAll("[data-receipt]").forEach(function (b) { b.addEventListener("click", function () { go("receipts", b.dataset.receipt); }); });
                var pay2 = el("spPay2"); if (pay2) pay2.addEventListener("click", function () { startPaymentFor(s.id); });
                var disc2 = el("spDiscount2"); if (disc2) disc2.addEventListener("click", function () { openDiscountForm(p.fees, function () { openStudent(id); }); });
                var discOff = el("spDiscountRemove"); if (discOff) discOff.addEventListener("click", function () { removeDiscount(p.fees, function () { openStudent(id); }); });
                bindPaymentRowActions(el("spTab"), function () { openStudent(id); });
                if (i === 3) loadEbookAccess(s.id);
            }
            el("studentProfile").querySelectorAll("[data-tab]").forEach(function (b) { b.addEventListener("click", function () { showTab(Number(b.dataset.tab)); }); });
            showTab(0);
            el("spEdit").addEventListener("click", function () { openStudentForm(s); });
            el("spDiscount").addEventListener("click", function () { openDiscountForm(p.fees, function () { openStudent(s.id); }); });
            el("spPay").addEventListener("click", function () { startPaymentFor(s.id); });
            el("spReceipt").addEventListener("click", function () { if (p.receipts.length) go("receipts", p.receipts[0].id); });
            el("spAtt").addEventListener("click", function () { go("attendance"); setTimeout(function () { el("attBatch").value = s.batchId || ""; }, 300); });
            el("spArchive").addEventListener("click", function () {
                var arch = s.status !== "ARCHIVED";
                if (!confirm(arch ? "Archive " + s.fullName + "? Payments, receipts and attendance are kept; the student is hidden from active lists." : "Restore " + s.fullName + " to the active list?")) return;
                api("/automation/students/" + s.id + "/archive?archived=" + arch, { method: "PATCH" }).then(function (r) { if (handle(r)) openStudent(s.id); });
            });
        });
    }
    /** The fee the student has to pay; with a discount, a note that it is after the discount. */
    function feeCell(r) {
        return inr(r.netFee != null ? r.netFee : r.courseFee) + (r.discount > 0 ? '<br><small class="text-success" title="Course fee ' + inr(r.courseFee) + ' less ' + inr(r.discount) + ' discount">after ' + inr(r.discount) + ' discount</small>' : "");
    }

    /** Add / Edit Student: required unless a number is already saved (the saved one is only ever shown masked). */
    function aadhaarField(existing) {
        var saved = existing && existing.aadhaarMasked;
        return fld("aadhaarNo", saved ? "Aadhaar Number (saved: " + saved + ")" : "Aadhaar Number", "", {
            required: !saved, max: 14, pattern: AADHAAR_PATTERN, title: "12-digit Aadhaar number (spaces allowed)",
            placeholder: saved ? "Leave blank to keep the saved number" : "12-digit number on the Aadhaar card",
            hint: existing && !saved ? "This older record has no Aadhaar number yet — please add it now." : "" });
    }

    function feeStatsHtml(f) {
        var disc = f.discount > 0;
        var stat = function (cls, icon, lbl, val) { return '<div class="col"><div class="au-stat ' + cls + '"><div class="ico"><i class="fas ' + icon + '"></i></div><div><div class="lbl">' + lbl + '</div><div class="val">' + val + '</div></div></div></div>'; };
        return '<div class="row row-cols-1 row-cols-sm-2 row-cols-lg-' + (disc ? 5 : 3) + ' g-3 mb-3 au-fee-stats">' + stat("c-green", "fa-rupee-sign", "Course Fee", inr(f.courseFee)) +
            (disc ? stat("c-teal", "fa-percent", "Discount", "− " + inr(f.discount)) + stat("c-green", "fa-tag", "Fee after Discount", inr(f.netFee)) : "") +
            stat("c-blue", "fa-check", "Total Paid", inr(f.totalPaid)) + stat("c-amber", "fa-hourglass-half", "Balance", inr(f.balance)) + '</div>';
    }

    function discountBoxHtml(f) {
        var on = f.discount > 0;
        return '<div class="au-discount mb-3"><div>' + (on
            ? '<i class="fas fa-percent me-1"></i><strong>Discount ' + inr(f.discount) + '</strong> — fee after discount <strong>' + inr(f.netFee) + '</strong>' +
              (f.discountReason ? '<div class="small text-muted">Reason: ' + esc(f.discountReason) + '</div>' : "") +
              (f.discountBy ? '<div class="small text-muted">Given by ' + esc(f.discountBy) + (f.discountAt ? ' on ' + dt(f.discountAt) : "") + '</div>' : "")
            : '<span class="text-muted"><i class="fas fa-percent me-1"></i>No discount. The full course fee applies.</span>') + '</div>' +
            '<div class="au-actions"><button type="button" class="au-btn light sm" id="spDiscount2"><i class="fas fa-percent"></i> ' + (on ? "Change discount" : "Give discount") + '</button>' +
            (on ? '<button type="button" class="au-btn light sm" id="spDiscountRemove"><i class="fas fa-times text-danger"></i> Remove</button>' : "") + '</div></div>';
    }

    /**
     * Give / change one student's discount: rupees or a percentage of the course fee, with a reason
     * (kept for the office, not printed). It can be at most what the student still owes.
     */
    function openDiscountForm(f, done) {
        var owed = Math.max(0, Number(f.courseFee) - Number(f.totalPaid));
        var html = '<div class="au-kv mb-3" style="grid-template-columns:repeat(3,1fr)"><div><span>Course Fee</span><strong>' + inr(f.courseFee) + '</strong></div><div><span>Paid so far</span><strong>' + inr(f.totalPaid) + '</strong></div><div><span>Discount can be up to</span><strong>' + inr(owed) + '</strong></div></div>' +
            '<div class="row-2">' + fld("dType", "Discount in", null, { type: "select", options: '<option value="amount">Rupees (₹)</option><option value="percent">Percent (%) of the course fee</option>' }) +
            fld("dValue", "Discount", f.discount > 0 ? f.discount : "", { type: "number", min: 0.01, step: "0.01", required: true }) + '</div>' +
            fld("reason", "Reason (for the office's records; not printed on the receipt)", f.discountReason || "", { max: 200, required: true, placeholder: "e.g. Sibling discount, early full payment" }) +
            '<div class="alert alert-light border small mb-0" id="dPreview"></div>';
        openForm((f.discount > 0 ? "Change Discount — " : "Give Discount — ") + f.fullName, html, function (v) {
            var body = { reason: v.reason };
            body[v.dType === "percent" ? "percent" : "amount"] = Number(v.dValue);
            return api("/automation/students/" + f.studentId + "/discount", { method: "PUT", body: body }).then(function (r) {
                if (handle(r)) { formModal.hide(); if (done) done(); } else formError(errText(r));
            });
        }, "Save Discount");
        var form = el("formModalForm");
        function preview() {
            var type = form.querySelector('[name="dType"]').value, val = Number(form.querySelector('[name="dValue"]').value) || 0;
            var amt = type === "percent" ? Math.round(Number(f.courseFee) * val) / 100 : val;
            var net = Number(f.courseFee) - amt;
            el("dPreview").innerHTML = amt > owed
                ? '<span class="text-danger"><i class="fas fa-exclamation-triangle me-1"></i>The discount can be at most ' + inr(owed) + ' (what the student still owes).</span>'
                : 'Course fee ' + inr(f.courseFee) + ' − discount ' + inr(amt) + ' = <strong>' + inr(net) + ' to pay</strong>. Paid so far ' + inr(f.totalPaid) +
                  ' → balance <strong>' + inr(net - Number(f.totalPaid)) + '</strong>. All of this student\'s receipts will show the discount.';
        }
        form.querySelectorAll('[name="dType"],[name="dValue"]').forEach(function (i) { i.addEventListener("input", preview); i.addEventListener("change", preview); });
        preview();
    }

    function removeDiscount(f, done) {
        if (!confirm("Remove the " + inr(f.discount) + " discount for " + f.fullName + "? The fee goes back to " + inr(f.courseFee) + " and the receipts are updated.")) return;
        api("/automation/students/" + f.studentId + "/discount", { method: "DELETE" }).then(function (r) { if (handle(r) && done) done(); });
    }

    /** Receipt lines for a discount: Discount and Fee after Discount, right under Course Fee. */
    function receiptDiscountRows(r) {
        if (!(r.discount > 0)) return "";
        return '<tr><td>Discount</td><td class="num">− ' + Number(r.discount).toLocaleString("en-IN") + '</td></tr><tr><td><strong>Fee after Discount (to pay)</strong></td><td class="num"><strong>' + Number(r.netFee).toLocaleString("en-IN") + '</strong></td></tr>';
    }

    // ---- free ebook login (Student Panel, ebook only) -----------------------------------------------
    // Made automatically when the first installment is recorded. The office sees the first password
    // until the student changes it, and can share, reset, or block the ebook.

    function loadEbookAccess(studentId) {
        api("/automation/students/" + studentId + "/ebook-access").then(function (res) {
            var box = el("spEbook"); if (!box) return;
            if (!res.success) { box.innerHTML = '<div class="au-error">' + esc(errText(res)) + '</div>'; return; }
            renderEbookAccess(res.data, studentId);
        });
    }

    function ebookLoginText(a) {
        return "Dear " + a.fullName + ",\nYour free ebook login for the LORD SAI Student Panel:\n" +
            "Sign in: " + a.loginUrl + "\nUser ID: " + a.userId + "\n" +
            (a.password ? "Password: " + a.password + "\n" : "Password: the one you set\n") +
            "After signing in, open My Ebooks to read the ebook." + (a.password ? " Please change your password in Settings." : "") +
            "\n\nRegards,\nLORD SAI Investment & Share Market Academy";
    }

    function renderEbookAccess(a, studentId) {
        var box = el("spEbook"); if (!box) return;
        var head = '<div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3"><div><h6 class="fw-bold mb-0">Free ebook login (Student Panel)</h6>' +
            '<small class="text-muted">Made when the first installment is paid. It opens only the ebook — never courses.</small></div>' +
            (a.state === "ACTIVE" || a.state === "BLOCKED" ? badge(a.blocked ? "BLOCKED" : "ACTIVE") : "") + '</div>';
        var blockBtn = '<button type="button" class="au-btn ' + (a.blocked ? "success" : "danger") + ' sm" data-eb="block">' + (a.blocked ? '<i class="fas fa-unlock"></i> Unblock ebook' : '<i class="fas fa-ban"></i> Block ebook') + '</button>';
        var html;
        if (a.state === "NO_PAYMENT" || a.state === "NEEDS_EMAIL" || a.state === "READY") {
            html = head + '<div class="au-ebook-state ' + (a.state === "NEEDS_EMAIL" ? "warn" : "") + '"><i class="fas ' + (a.state === "READY" ? "fa-user-plus" : a.state === "NEEDS_EMAIL" ? "fa-envelope" : "fa-hourglass-half") + ' me-2"></i>' + esc(a.message) + '</div>' +
                (a.blocked ? '<div class="small text-danger mt-2"><i class="fas fa-ban me-1"></i>Ebook access is blocked: the login will be made blocked.</div>' : "") +
                '<div class="au-actions mt-3">' + (a.state === "READY" ? '<button type="button" class="au-btn primary" data-eb="create"><i class="fas fa-user-plus"></i> Create ebook login</button>' : "") +
                (a.state === "NEEDS_EMAIL" ? '<button type="button" class="au-btn primary" data-eb="edit"><i class="fas fa-edit"></i> Add email (Edit)</button>' : "") + blockBtn + '</div>';
        } else {
            var pwd = a.password ? '<span class="mono au-secret">' + esc(a.password) + '</span>'
                : a.linkedAccount ? '<span class="text-muted">Their own website password (this email already had a login)</span>'
                : '<span class="text-muted">The student has set their own password</span>';
            var books = (a.ebooks || []).length ? a.ebooks.map(function (b) { return '<div>' + esc(b.title) + ' ' + badge(b.status === "ACTIVE" ? "ACTIVE" : "BLOCKED") + (b.source && b.source !== "ACADEMY" ? ' <small class="text-muted">(' + (b.source === "PAYMENT" ? "bought online" : "given by Main Admin") + ')</small>' : "") + '</div>'; }).join("")
                : '<span class="text-muted">No ebook is on sale yet — it is added here automatically once one is.</span>';
            var kv = [["Student ID", '<span class="mono">' + esc(a.studentId) + '</span>'], ["User ID (sign in)", '<span class="mono au-secret">' + esc(a.userId || "—") + '</span>'], ["Password", pwd],
                ["Email", esc(a.email || "—")], ["Sign-in page", '<span class="small">' + esc(a.loginUrl) + '</span>'], ["Created", a.createdAt ? dt(a.createdAt) : "—"],
                ["Login details emailed", a.emailedAt ? dt(a.emailedAt) : '<span class="text-danger">Not emailed yet</span>'], ["Last sign-in", a.lastLoginAt ? dt(a.lastLoginAt) : "Not yet"],
                ["Device", esc(a.deviceName || "No device yet")], ["Ebook", books]];
            html = head + (a.blocked ? '<div class="alert alert-danger small py-2"><i class="fas fa-ban me-1"></i>Ebook access is blocked. The student sees: “Your ebook access is blocked. Please contact the academy.”</div>' : "") +
                '<div class="au-kv au-ebook-kv">' + kv.map(function (x) { return '<div><span>' + x[0] + '</span><strong>' + x[1] + '</strong></div>'; }).join("") + '</div>' +
                '<div class="au-actions mt-3">' +
                '<button type="button" class="au-btn light sm" data-eb="copy"><i class="fas fa-copy"></i> Copy login details</button>' +
                '<button type="button" class="au-btn light sm" data-eb="whatsapp"' + (a.mobile ? "" : " disabled title=\"No mobile number\"") + '><i class="fab fa-whatsapp text-success"></i> Send on WhatsApp</button>' +
                '<button type="button" class="au-btn light sm" data-eb="print"><i class="fas fa-print"></i> Print</button>' +
                (a.password ? '<button type="button" class="au-btn light sm" data-eb="email"><i class="fas fa-envelope"></i> Email login details</button>' : "") +
                '<button type="button" class="au-btn light sm" data-eb="reset-password"><i class="fas fa-key"></i> Reset password</button>' +
                '<button type="button" class="au-btn light sm" data-eb="reset-device"><i class="fas fa-mobile-alt"></i> Reset device</button>' + blockBtn + '</div>';
        }
        box.innerHTML = html;
        box.querySelectorAll("[data-eb]").forEach(function (b) { b.addEventListener("click", function () { ebookAction(b.dataset.eb, a, studentId); }); });
    }

    function ebookAction(action, a, studentId) {
        var base = "/automation/students/" + studentId + "/ebook-access";
        var done = function (res) { if (handle(res)) renderEbookAccess(res.data, studentId); };
        if (action === "edit") {
            api("/automation/students/" + studentId).then(function (r) { if (r.success) openStudentForm(r.data); });
        } else if (action === "create") {
            api(base, { method: "POST" }).then(done);
        } else if (action === "copy") {
            var text = ebookLoginText(a);
            (navigator.clipboard && navigator.clipboard.writeText ? navigator.clipboard.writeText(text) : Promise.reject()).then(function () { toast("Login details copied.", true); }, function () { window.prompt("Copy the login details:", text); });
        } else if (action === "whatsapp") {
            if (!LSI_WhatsAppChat.open(a.mobile, ebookLoginText(a))) toast("Allow pop-ups for this site to open WhatsApp.", false);
        } else if (action === "print") {
            printEbookSlip(a);
        } else if (action === "email") {
            api(base + "/email", { method: "POST" }).then(done);
        } else if (action === "reset-password") {
            if (confirm("Make a new password for " + a.fullName + "? The old password stops working and the student is signed out.")) api(base + "/reset-password", { method: "POST" }).then(done);
        } else if (action === "reset-device") {
            if (confirm("Reset " + a.fullName + "'s device? They can then sign in on a new phone or computer (and are signed out now).")) api(base + "/reset-device", { method: "POST" }).then(done);
        } else if (action === "block") {
            var block = !a.blocked;
            if (confirm(block ? "Block " + a.fullName + "'s ebook access? They will see \"Your ebook access is blocked. Please contact the academy.\"" : "Unblock " + a.fullName + "'s ebook access?")) {
                api(base + "/blocked?blocked=" + block, { method: "PATCH" }).then(done);
            }
        }
    }

    /** A small printable slip with the login details, to hand to the student. */
    function printEbookSlip(a) {
        var w = window.open("", "_blank");
        if (!w) { toast("Allow pop-ups to print the login slip.", false); return; }
        var row = function (k, v) { return '<tr><td style="padding:6px 12px;color:#475569">' + k + '</td><td style="padding:6px 12px;font-weight:700;font-family:Consolas,monospace">' + esc(v) + '</td></tr>'; };
        w.document.write('<!doctype html><html><head><meta charset="utf-8"><title>Ebook login — ' + esc(a.fullName) + '</title></head>' +
            '<body style="font-family:Segoe UI,Arial,sans-serif;color:#0f172a;margin:32px"><div style="max-width:520px;border:2px solid #1B2A47;border-radius:10px;padding:22px">' +
            '<div style="display:flex;align-items:center;gap:12px;margin-bottom:12px"><img src="' + location.href.replace(/[^/]*$/, "") + 'img/lord-sai-logo.png" style="height:44px" alt=""><div><div style="font-weight:800;color:#1B2A47">LORD SAI INVESTMENT AND SHARE MARKET ACADEMY</div><div style="font-size:12px;color:#64748B">Free ebook login — Student Panel</div></div></div>' +
            '<p style="margin:0 0 10px">Dear <strong>' + esc(a.fullName) + '</strong>, use these details to read the academy ebook:</p>' +
            '<table style="border-collapse:collapse;width:100%;background:#F8FAFC;border:1px solid #E2E8F0">' + row("Sign in at", a.loginUrl) + row("User ID", a.userId) + row("Password", a.password || "(the one you set)") + row("Student ID", a.studentId) + '</table>' +
            '<p style="font-size:13px;color:#475569;margin:12px 0 0">After signing in, open <strong>My Ebooks</strong>. Please change your password in Settings and do not share it.</p></div>' +
            '<script>window.onload=function(){window.print();}<\/script></body></html>');
        w.document.close();
    }

    function ordinal(n) { var m = n % 100; return n + ((m >= 11 && m <= 13) ? "th" : ["th", "st", "nd", "rd"][n % 10] || "th"); }

    // ---- fees & payments ----------------------------------------------------------------------------

    function loadPaymentsView() { if (!el("payDate").value) el("payDate").value = today(); loadPaymentsList(); }

    function startPaymentFor(studentId) {
        go("payments");
        setTimeout(function () { api("/automation/students/" + studentId).then(function (r) { if (r.success) selectPayStudent(r.data); }); }, 200);
    }

    function selectPayStudent(s) {
        el("payStudentId").value = s.id; el("payStudentQ").value = s.studentId + " — " + s.fullName;
        el("payStudentResults").style.display = "none";
        api("/automation/students/" + s.id + "/fees").then(function (res) {
            if (!handle(res, false)) return;
            var f = res.data; state.payStudent = f;
            el("payStudentCard").classList.remove("d-none");
            el("payStudentCard").innerHTML = '<div class="au-card pad" style="background:#F8FAFF"><div class="au-kv" style="grid-template-columns:repeat(3,1fr)">' +
                '<div><span>Student ID</span><strong class="mono">' + esc(f.studentCode) + '</strong></div><div><span>Name</span><strong>' + esc(f.fullName) + '</strong></div><div><span>Batch</span><strong>' + esc(f.batchName || "—") + '</strong></div>' +
                '<div><span>Course</span><strong>' + esc(f.courseName || "—") + '</strong></div><div><span>' + (f.discount > 0 ? "Fee to pay (after discount)" : "Course Fee") + '</span><strong>' + inr(f.netFee) + (f.discount > 0 ? ' <small class="text-success">(' + inr(f.courseFee) + ' − ' + inr(f.discount) + ')</small>' : "") + '</strong></div><div><span>Paid / Balance</span><strong>' + inr(f.totalPaid) + ' / <span style="color:' + (f.balance > 0 ? "#DC2626" : "#16A34A") + '">' + inr(f.balance) + '</span> ' + badge(f.paymentStatus) + '</strong></div></div>' +
                payDiscountHtml(f) + '</div>';
            bindPayDiscount(f);
            renderPayDiscountBox(f);
            el("payInstallment").innerHTML = f.installments.map(function (i) { return '<option value="' + i.installmentNo + '"' + (i.paid ? " disabled" : "") + (i.installmentNo === f.nextInstallmentNo ? " selected" : "") + '>' + esc(i.label) + (i.paid ? " — paid " + inr(i.amount) + " on " + d(i.paymentDate) : "") + '</option>'; }).join("") + '<option value="' + (f.installments.length + 1) + '">' + ordinal(f.installments.length + 1) + ' Installment</option>';
            el("payInstallmentHint").innerText = f.nextInstallmentNo ? "Next due: " + f.installments[f.nextInstallmentNo - 1].label : "All fees paid — no further installment is due.";
            el("payAmount").value = f.balance > 0 ? f.balance : ""; el("payAmount").max = f.balance;
            el("payAmountHint").innerText = "Outstanding balance " + inr(f.balance) + " — the amount cannot exceed it.";
            el("paySubmit").disabled = !(f.balance > 0);
        });
    }

    /** Discount at first installment: apply before saving the payment so the receipt shows the reduced fee. */
    function payDiscountHtml(f) {
        if (Number(f.totalPaid) > 0) return "";
        var on = f.discount > 0;
        return '<div class="au-discount mt-3 mb-0"><div>' + (on
            ? '<i class="fas fa-percent me-1"></i><strong>Discount ' + inr(f.discount) + '</strong> — fee after discount <strong>' + inr(f.netFee) + '</strong>' +
              (f.discountReason ? '<div class="small text-muted">Reason: ' + esc(f.discountReason) + '</div>' : "")
            : '<span><i class="fas fa-percent me-1"></i><strong>First installment</strong> — give a discount now if needed (before saving the payment).</span>') +
            '</div><div class="au-actions">' +
            '<button type="button" class="au-btn primary sm" id="payDiscountBtn"><i class="fas fa-percent"></i> ' + (on ? "Change discount" : "Give discount") + '</button>' +
            (on ? '<button type="button" class="au-btn light sm" id="payDiscountRemove"><i class="fas fa-times text-danger"></i> Remove</button>' : "") +
            '</div></div>';
    }

    function bindPayDiscount(f) {
        var refresh = function () { selectPayStudent({ id: f.studentId, studentId: f.studentCode, fullName: f.fullName }); };
        var btn = el("payDiscountBtn");
        if (btn) btn.addEventListener("click", function () { openDiscountForm(f, refresh); });
        var rem = el("payDiscountRemove");
        if (rem) rem.addEventListener("click", function () { removeDiscount(f, refresh); });
    }

    function renderPayDiscountBox(f) {
        var box = el("payDiscountBox");
        if (!box) return;
        // Discount UI is rendered inside the student card; keep this slot empty.
        box.classList.add("d-none");
        box.innerHTML = "";
    }

    function resetPayForm() { el("payForm").reset(); el("payStudentId").value = ""; el("payStudentCard").classList.add("d-none"); var pdb = el("payDiscountBox"); if (pdb) { pdb.classList.add("d-none"); pdb.innerHTML = ""; } el("payInstallment").innerHTML = ""; el("payInstallmentHint").innerText = ""; el("payAmountHint").innerText = ""; el("payError").classList.add("d-none"); el("payDate").value = today(); el("paySubmit").disabled = false; state.payStudent = null; }

    function submitPayment(e) {
        e.preventDefault();
        var err = el("payError"); err.classList.add("d-none");
        if (!el("payStudentId").value) { err.innerText = "Select a student first."; err.classList.remove("d-none"); return; }
        var body = { studentId: Number(el("payStudentId").value), installmentNo: Number(el("payInstallment").value), paymentDate: el("payDate").value, amount: Number(el("payAmount").value), paymentMode: el("payMode").value, referenceNo: el("payRef").value.trim(), notes: el("payNotes").value.trim() };
        el("paySubmit").disabled = true;
        api("/automation/payments", { method: "POST", body: body }).then(function (res) {
            el("paySubmit").disabled = false;
            if (!res.success) { err.innerText = errText(res); err.classList.remove("d-none"); return; }
            toast(res.message, true);
            resetPayForm(); loadPaymentsList(); loadLookups();
            go("receipts", res.data.receiptId);
        });
    }

    function paymentsTable(list, compact) {
        return list.length ? '<div class="au-table-wrap"><table class="au-table"><thead><tr><th>Date</th><th>Payment No</th>' + (compact ? "" : "<th>Student</th>") + '<th>Installment</th><th class="num">Amount</th><th>Mode</th><th>Reference</th><th>Receipt</th><th></th></tr></thead><tbody>' + list.map(function (p) {
            return '<tr><td>' + (p.paymentDate ? d(p.paymentDate) : '<span class="text-danger" title="No date in workbook">no date</span>') + '</td><td class="mono">' + esc(p.paymentNo) + '</td>' + (compact ? "" : '<td><a href="#student/' + p.studentId + '" class="au-link">' + esc(p.studentName) + '</a><br><small class="mono text-muted">' + esc(p.studentCode) + '</small></td>') + '<td>' + esc(p.installmentLabel) + (p.reviewNote ? ' <i class="fas fa-flag text-warning" title="' + esc(p.reviewNote) + '"></i>' : "") + '</td><td class="num">' + inr(p.amount) + '</td><td>' + esc(p.paymentMode) + '</td><td>' + esc(p.referenceNo || "—") + '</td><td>' + (p.receiptId ? '<a href="#receipts/' + p.receiptId + '" class="au-link mono">' + esc(p.receiptNo) + '</a>' : "—") + '</td>' +
                '<td class="text-nowrap"><button class="au-btn light sm" data-pedit="' + p.id + '" title="Edit"><i class="fas fa-edit"></i></button> <button class="au-btn light sm" data-pdel="' + p.id + '" title="Delete"><i class="fas fa-trash text-danger"></i></button></td></tr>';
        }).join("") + '</tbody></table></div>' : '<div class="au-empty">No payments recorded.</div>';
    }

    function bindPaymentRowActions(root, after) {
        root.querySelectorAll("[data-pedit]").forEach(function (b) { b.addEventListener("click", function () { api("/automation/payments/" + b.dataset.pedit).then(function (r) { if (r.success) openPaymentEdit(r.data, after); }); }); });
        root.querySelectorAll("[data-pdel]").forEach(function (b) { b.addEventListener("click", function () { if (!confirm("Delete this payment? Its receipt will be voided and the student's balance recalculated.")) return; api("/automation/payments/" + b.dataset.pdel, { method: "DELETE" }).then(function (r) { if (handle(r)) { after(); loadLookups(); } }); }); });
    }

    function openPaymentEdit(p, after) {
        var html = '<div class="mb-2 text-muted small">' + esc(p.studentCode) + ' — ' + esc(p.studentName) + ' · ' + esc(p.paymentNo) + (p.reviewNote ? '<div class="alert alert-warning small py-2 mt-2 mb-0"><i class="fas fa-flag me-1"></i> ' + esc(p.reviewNote) + ' — saving clears the flag.</div>' : "") + '</div>' +
            '<div class="row-2">' + fld("installmentNo", "Installment", p.installmentNo, { type: "number", min: 1, required: true }) + fld("amount", "Amount (₹)", p.amount, { type: "number", step: "0.01", min: 1, required: true }) + '</div>' +
            '<div class="row-2">' + fld("paymentDate", "Payment date", p.paymentDate || "", { type: "date", required: true }) + fld("paymentMode", "Mode", null, { type: "select", required: true, options: opts(L.paymentModes, "code", function (m) { return m.label; }, p.paymentMode) }) + '</div>' +
            '<div class="row-2">' + fld("referenceNo", "Reference", p.referenceNo, { max: 100 }) + fld("notes", "Notes", p.notes, { max: 500 }) + '</div>';
        openForm("Edit Payment", html, function (v) {
            v.installmentNo = Number(v.installmentNo); v.amount = Number(v.amount);
            return api("/automation/payments/" + p.id, { method: "PUT", body: v }).then(function (r) { if (handle(r)) { formModal.hide(); after(); loadLookups(); } else formError(errText(r)); });
        });
    }

    function loadPaymentsList() {
        api("/automation/payments?" + qs({ batchId: el("payListBatch").value, mode: el("payListMode").value, from: el("payListFrom").value, to: el("payListTo").value })).then(function (res) {
            if (!handle(res, false)) return;
            var list = res.data.slice(0, 100);
            el("paymentsBody").innerHTML = list.map(function (p) {
                return '<tr><td>' + (p.paymentDate ? d(p.paymentDate) : '<span class="text-danger">no date</span>') + '</td><td class="mono">' + esc(p.paymentNo) + '</td><td><a href="#student/' + p.studentId + '" class="au-link">' + esc(p.studentName) + '</a><br><small class="mono text-muted">' + esc(p.studentCode) + '</small></td><td>' + esc(p.installmentLabel.replace(" Installment", "")) + (p.reviewNote ? ' <i class="fas fa-flag text-warning" title="' + esc(p.reviewNote) + '"></i>' : "") + '</td><td class="num">' + inr(p.amount) + '</td><td>' + esc(p.paymentMode) + '</td><td>' + (p.receiptId ? '<a href="#receipts/' + p.receiptId + '" class="au-link mono">' + esc(p.receiptNo) + '</a>' : "—") + '</td><td class="text-nowrap"><button class="au-btn light sm" data-pedit="' + p.id + '"><i class="fas fa-edit"></i></button> <button class="au-btn light sm" data-pdel="' + p.id + '"><i class="fas fa-trash text-danger"></i></button></td></tr>';
            }).join("") || '<tr><td colspan="8" class="au-empty">No payments match.</td></tr>';
            bindPaymentRowActions(el("paymentsBody"), loadPaymentsList);
        });
    }

    // ---- pending fees ----------------------------------------------------------------------------------
    // Every active student who still owes money (fee after any discount, less what was paid), with
    // contact details and a one-click reminder. Reminders are recorded in Message History.

    function pendingQuery() {
        return { q: el("pfQ").value.trim(), batchId: el("pfBatch").value, courseId: el("pfCourse").value, paymentStatus: el("pfStatus").value };
    }

    function loadPendingFees() {
        api("/automation/pending-fees?" + qs(pendingQuery())).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            state.pending = p.rows;
            var partly = p.rows.filter(function (r) { return r.paymentStatus === "PARTIAL"; }).length;
            el("pfStats").innerHTML = [["Students with a balance", p.students, "fa-users", "c-blue"], ["Total pending", inr(p.totalBalance), "fa-hourglass-half", "c-amber"],
                ["Nothing paid yet", p.students - partly, "fa-exclamation-circle", "c-red"], ["Part paid", partly, "fa-adjust", "c-green"]].map(function (c) {
                return '<div class="col-6 col-lg-3"><div class="au-stat ' + c[3] + '"><div class="ico"><i class="fas ' + c[2] + '"></i></div><div><div class="lbl">' + c[0] + '</div><div class="val">' + esc(c[1]) + '</div></div></div></div>';
            }).join("");
            el("pfBody").innerHTML = p.rows.map(function (r) {
                return '<tr><td><a href="#student/' + r.id + '" class="au-link fw-semibold">' + esc(r.fullName) + '</a><br><small class="mono text-muted">' + esc(r.studentId) + '</small> ' + badge(r.paymentStatus) +
                    '<div class="small text-muted">' + esc(r.batchName || "—") + (r.courseName ? " · " + esc(r.courseName) : "") + '</div></td>' +
                    '<td class="pf-contact">' + (r.mobile ? '<div><i class="fas fa-phone-alt"></i><a href="tel:' + esc(r.mobile) + '" class="au-link">' + esc(r.mobile) + '</a></div>' : '<div class="text-danger">No mobile number</div>') +
                    (r.email ? '<div><i class="fas fa-envelope"></i><span>' + esc(r.email) + '</span></div>' : '<div class="text-muted">No email address</div>') +
                    (r.address ? '<div class="text-muted"><i class="fas fa-map-marker-alt"></i><span>' + esc(r.address) + '</span></div>' : "") + '</td>' +
                    '<td class="num">' + inr(r.netFee) + (r.discount > 0 ? '<br><small class="text-success">after ' + inr(r.discount) + ' discount</small>' : "") + '</td>' +
                    '<td class="num">' + inr(r.totalPaid) + '<br><small class="text-muted">' + r.installmentsPaid + ' installment' + (r.installmentsPaid === 1 ? "" : "s") + '</small></td>' +
                    '<td class="num"><strong class="text-danger">' + inr(r.balance) + '</strong></td>' +
                    '<td class="pf-when"><div>Paid: ' + (r.lastPaymentDate ? d(r.lastPaymentDate) : '<span class="text-muted">never</span>') + '</div>' +
                    '<div class="text-muted">Reminded: ' + (r.lastReminderAt ? dt(r.lastReminderAt) : "not yet") + '</div></td>' +
                    '<td><div class="pf-actions">' + (r.mobile ? '<button class="au-btn light sm" data-pf-wa="' + r.id + '" title="Open WhatsApp with a fee reminder typed — you press Send"><i class="fab fa-whatsapp text-success"></i> WhatsApp</button> ' : "") +
                    (r.email ? '<button class="au-btn light sm" data-pf-email="' + r.id + '" title="Email a fee reminder now"><i class="fas fa-envelope"></i> Email</button>' : "") + '</div></td></tr>';
            }).join("") || '<tr><td colspan="7" class="au-empty">No pending fees — every active student matching these filters has paid in full.</td></tr>';
            el("pfBody").querySelectorAll("[data-pf-wa]").forEach(function (b) { b.addEventListener("click", function () { remindFee(Number(b.dataset.pfWa), "WHATSAPP", b); }); });
            el("pfBody").querySelectorAll("[data-pf-email]").forEach(function (b) { b.addEventListener("click", function () { remindFee(Number(b.dataset.pfEmail), "EMAIL", b); }); });
        });
    }

    /** One fee reminder. WhatsApp: the tab is reserved inside the click, then pointed at the chat with the reminder typed. */
    function remindFee(id, channel, btn) {
        var r = (state.pending || []).filter(function (x) { return x.id === id; })[0];
        if (channel === "EMAIL" && !confirm("Email a fee reminder to " + (r ? r.fullName + " (" + r.email + ")" : "this student") + " now?")) return;
        var wa = channel === "WHATSAPP" ? LSI_WhatsAppChat.prepare() : null;
        if (btn) btn.disabled = true;
        api("/automation/pending-fees/" + id + "/remind", { method: "POST", body: { channel: channel } }).then(function (res) {
            if (btn) btn.disabled = false;
            if (wa) {
                var c = res.success && res.data && res.data.whatsappChat;
                if (c) { LSI_WhatsAppChat.openPrepared(wa, c.phone, c.text); toast("WhatsApp opened with the fee reminder — press Send there.", true); }
                else { LSI_WhatsAppChat.cancel(wa); toast(errText(res) || "WhatsApp could not be opened.", false); }
            } else if (res.success) {
                toast(res.data.message, res.data.status === "SENT");
            } else {
                toast(errText(res), false);
            }
            loadPendingFees();
        });
    }

    // ---- course price (the academy's own prices; website prices are separate) ---------------------------

    function loadCoursePrice() {
        loadLookups().then(function (l) {
            if (!l) return;
            el("cpBody").innerHTML = l.courses.map(function (c) {
                return '<tr><td><strong>' + esc(c.name) + '</strong></td><td class="num"><strong>' + inr(c.defaultFee) + '</strong></td><td>' + esc(c.duration || "—") + '</td><td class="num">' + c.students + '</td><td>' + badge(c.active ? "ACTIVE" : "ARCHIVED") + '</td>' +
                    '<td><button class="au-btn primary sm" data-cp="' + c.id + '"><i class="fas fa-tag"></i> Change price</button></td></tr>';
            }).join("") || '<tr><td colspan="6" class="au-empty">No courses yet — add one under Batches &amp; Courses.</td></tr>';
            el("cpBody").querySelectorAll("[data-cp]").forEach(function (b) {
                b.addEventListener("click", function () { openCoursePriceForm(l.courses.filter(function (x) { return String(x.id) === b.dataset.cp; })[0]); });
            });
        });
    }

    function openCoursePriceForm(c) {
        openForm("Course Price — " + c.name, '<div class="hint mb-3">Current price: <strong>' + inr(c.defaultFee) + '</strong>. The new price is used for <strong>new admissions</strong>; the ' + c.students +
            ' student(s) already admitted keep the fee they were admitted with. Website course prices are not changed.</div>' +
            fld("price", "New price (₹)", c.defaultFee, { type: "number", min: 0, step: "0.01", required: true }), function (v) {
            return api("/automation/courses/" + c.id + "/price", { method: "PATCH", body: { price: Number(v.price) } }).then(function (r) {
                if (handle(r)) { formModal.hide(); loadCoursePrice(); } else formError(errText(r));
            });
        }, "Save Price");
    }

    // ---- receipts ---------------------------------------------------------------------------------

    function receiptHtml(r, compact) {
        var inst = ordinal(r.installmentNo) + " Installment";
        return '<div class="au-receipt">' +
            '<div class="rh"><img src="img/lord-sai-logo.png" alt=""><div><div class="n">' + esc(r.academyName) + '</div><div class="s">' + esc(r.academyTagline) + '</div></div></div>' +
            '<div class="addr">' + esc(r.academyAddress) + '<br>Email: ' + esc(r.academyEmail) + ' · Mob: ' + esc(r.academyPhone) + '</div>' +
            '<div class="text-center"><span class="title">FEE RECEIPT</span></div>' +
            '<div class="meta d-flex justify-content-between flex-wrap"><div><b>Receipt No.</b>: <strong class="mono">' + esc(r.receiptNo) + '</strong></div><div><b style="min-width:auto">Payment No.</b>: <span class="mono">' + esc(r.paymentNo) + '</span></div><div><b style="min-width:auto">Date</b>: ' + d(r.paymentDate || r.issuedAt) + '</div></div>' +
            '<div class="meta"><div><b>Student ID</b>: <span class="mono">' + esc(r.studentCode) + '</span></div><div><b>Student Name</b>: ' + esc(r.studentName) + '</div><div><b>Mobile No.</b>: ' + esc(r.studentMobile || "—") + '</div><div><b>Course</b>: ' + esc(r.courseName || "—") + '</div><div><b>Batch / Class</b>: ' + esc(r.batchName || "—") + (r.batchSchedule ? " (" + esc(r.batchSchedule) + ")" : "") + '</div><div><b>Installment</b>: ' + esc(inst) + '</div><div><b>Payment Mode</b>: ' + esc(r.paymentMode) + '</div>' + (r.referenceNo ? '<div><b>Transaction Ref.</b>: ' + esc(r.referenceNo) + '</div>' : "") + '</div>' +
            '<table><thead><tr><th>Particulars</th><th class="num">Amount (₹)</th></tr></thead><tbody><tr><td>Course Fee</td><td class="num">' + Number(r.totalFee).toLocaleString("en-IN") + '</td></tr>' + receiptDiscountRows(r) + '<tr><td>Amount Paid (this receipt)</td><td class="num">' + Number(r.amountPaid).toLocaleString("en-IN") + '</td></tr><tr><td>Total Paid to Date</td><td class="num">' + Number(r.totalPaid).toLocaleString("en-IN") + '</td></tr><tr><td><strong>Balance Amount</strong></td><td class="num"><strong>' + Number(r.balance).toLocaleString("en-IN") + '</strong></td></tr></tbody></table>' +
            '<div class="words"><strong>Amount in Words:</strong> ' + esc(r.amountInWords) + '</div>' +
            receiptHistoryHtml(r.history) +
            '<div class="text-center small" style="color:#334155">Thank you for being a part of our academy!</div>' +
            '<div class="sig"><img class="stamp" src="img/stamp-lord-sai.png" alt="Academy stamp"><div class="box"><img src="img/signature-vaibhav-pawar.png" alt="Signature"><div class="line"><strong>' + esc(r.signatory.split(",")[0]) + '</strong><br><small>' + esc((r.signatory.split(",")[1] || "").trim()) + '</small></div></div></div>' +
            (compact ? "" : '<div class="text-muted small mt-2 au-no-print">Issued ' + dt(r.issuedAt) + ' by ' + esc(r.issuedBy) + (r.emailedAt ? ' · emailed ' + dt(r.emailedAt) : "") + '</div>') + '</div>';
    }

    /** Every installment paid up to this receipt (shown once there is an earlier one); the receipt's own line is marked. */
    function receiptHistoryHtml(history) {
        if (!history || history.length < 2) return "";
        // Four columns (the payment number sits under the installment) so the amount stays in view in the narrow preview.
        return '<div class="hist-title">Payment History</div><div class="hist-wrap"><table class="hist"><thead><tr><th>Installment</th><th>Payment Date</th><th>Mode</th><th class="num">Amount (₹)</th></tr></thead><tbody>' +
            history.map(function (h) {
                return '<tr' + (h.current ? ' class="cur"' : '') + '><td>' + esc(ordinal(h.installmentNo)) + ' Installment' + (h.current ? ' <span class="this">(this receipt)</span>' : '') +
                    '<div class="pno mono">' + esc(h.paymentNo) + '</div></td><td class="dt">' + (h.paymentDate ? d(h.paymentDate) : "—") + '</td><td>' + esc(h.paymentMode) + '</td><td class="num">' + Number(h.amount).toLocaleString("en-IN") + '</td></tr>';
            }).join("") + '</tbody></table></div>';
    }

    function loadReceipts(page, selectId) {
        state.rcPage = page;
        api("/automation/receipts?" + qs({ q: el("rcQ").value.trim(), from: el("rcFrom").value, to: el("rcTo").value, page: page, size: 15 })).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("receiptsBody").innerHTML = p.content.map(function (r) {
                return '<tr' + (state.receipt && state.receipt.id === r.id ? ' style="background:#EEF4FF"' : "") + '><td class="mono">' + esc(r.receiptNo) + '</td><td>' + d(r.paymentDate) + '</td><td><a href="#student/' + r.studentId + '" class="au-link">' + esc(r.studentName) + '</a><br><small class="mono text-muted">' + esc(r.studentCode) + '</small></td><td class="num">' + inr(r.amountPaid) + '</td><td class="num">' + inr(r.balance) + '</td><td>' + esc(r.paymentMode) + '</td><td>' + (r.emailedAt ? '<i class="fas fa-check text-success"></i>' : "—") + '</td><td><button class="au-view-btn" data-rc="' + r.id + '">Preview</button></td></tr>';
            }).join("") || '<tr><td colspan="8" class="au-empty">No receipts yet — they are generated automatically when a payment is recorded.</td></tr>';
            el("receiptsBody").querySelectorAll("[data-rc]").forEach(function (b) { b.addEventListener("click", function () { showReceipt(b.dataset.rc); }); });
            pager("receiptsPager", p, function (n) { loadReceipts(n); });
            if (selectId) showReceipt(selectId);
        });
    }

    function showReceipt(id) {
        api("/automation/receipts/" + id).then(function (res) {
            if (!handle(res, false)) return;
            state.receipt = res.data;
            el("receiptPreview").innerHTML = receiptHtml(res.data, false);
            ["rcPrint", "rcPdf"].forEach(function (i) { el(i).disabled = false; });
            el("rcEmail").disabled = !res.data.studentEmail;
            el("rcEmail").title = res.data.studentEmail ? "Email to " + res.data.studentEmail : "Student has no email address on record";
            el("receiptsBody").querySelectorAll("tr").forEach(function (tr) { tr.style.background = ""; });
        });
    }

    function printReceipt() {
        if (!state.receipt) return;
        el("receiptPrintArea").innerHTML = receiptHtml(state.receipt, true);
        el("receiptPrintArea").classList.remove("d-none");
        document.body.classList.add("au-print-receipt");
        var done = function () { document.body.classList.remove("au-print-receipt"); el("receiptPrintArea").classList.add("d-none"); window.removeEventListener("afterprint", done); };
        window.addEventListener("afterprint", done);
        // Print only once the logo and signature images are loaded, or they can be missing on paper.
        var imgs = [].slice.call(el("receiptPrintArea").querySelectorAll("img"));
        Promise.all(imgs.map(function (i) {
            return i.complete ? null : new Promise(function (res) { i.onload = i.onerror = res; });
        })).then(function () { window.print(); });
    }

    // ---- attendance --------------------------------------------------------------------------------

    function loadAttendanceView() { if (!el("attDate").value) el("attDate").value = today(); loadSessions(); }

    function openSheet(e) {
        e.preventDefault();
        var body = { batchId: Number(el("attBatch").value), sessionDate: el("attDate").value, sessionType: el("attType").value, instructor: el("attInstructor").value.trim() };
        if (!body.batchId) { toast("Select a batch.", false); return; }
        api("/automation/attendance/sessions", { method: "POST", body: body }).then(function (res) { if (handle(res, false)) renderSheet(res.data); loadSessions(); });
    }

    function renderSheet(sheet) {
        state.sheet = sheet; state.marks = {};
        sheet.rows.forEach(function (r) { state.marks[r.studentId] = r.status; });
        el("sheetCard").classList.remove("d-none");
        el("sheetTitle").innerText = sheet.session.batchName + " — " + d(sheet.session.sessionDate);
        el("sheetSub").innerText = sheet.session.sessionType + (sheet.session.instructor ? " · " + sheet.session.instructor : "") + " · " + sheet.rows.length + " students";
        drawSheet();
        el("sheetCard").scrollIntoView({ behavior: "smooth", block: "start" });
    }

    function drawSheet() {
        var s = state.sheet;
        var html = '<div class="au-att-row head"><div>Student ID</div><div>Name</div><div>Attendance so far</div><div>Status</div></div>' + s.rows.map(function (r) {
            var st = state.marks[r.studentId];
            var seg = ["P", "A", "L", "E"].map(function (k) { var full = { P: "PRESENT", A: "ABSENT", L: "LATE", E: "EXCUSED" }[k]; return '<button type="button" data-mark="' + r.studentId + '" data-status="' + full + '" class="' + (st === full ? "on-" + k : "") + '" title="' + full + '">' + (k === "P" ? "Present" : k === "A" ? "Absent" : k === "L" ? "Late" : "Excused") + '</button>'; }).join("");
            return '<div class="au-att-row"><div class="mono">' + esc(r.studentCode) + '</div><div><strong>' + esc(r.fullName) + '</strong></div><div class="small text-muted">' + (r.sessionsTotal ? r.sessionsPresent + "/" + r.sessionsTotal + " · " + pct(r.attendancePercent) : "new") + '</div><div class="au-seg">' + seg + '</div></div>';
        }).join("");
        el("sheetRows").innerHTML = html || '<div class="au-empty">No active students in this batch. Assign students to the batch first.</div>';
        el("sheetRows").querySelectorAll("[data-mark]").forEach(function (b) { b.addEventListener("click", function () { state.marks[b.dataset.mark] = state.marks[b.dataset.mark] === b.dataset.status ? null : b.dataset.status; drawSheet(); }); });
        var vals = Object.values(state.marks), p = vals.filter(function (v) { return v === "PRESENT" || v === "LATE"; }).length, a = vals.filter(function (v) { return v === "ABSENT"; }).length, u = vals.filter(function (v) { return !v; }).length;
        el("sheetSummary").innerText = p + " present · " + a + " absent · " + u + " unmarked. Changes are kept only after Save Attendance.";
    }

    function setAll(status) { if (!state.sheet) return; state.sheet.rows.forEach(function (r) { state.marks[r.studentId] = status; }); drawSheet(); }

    function saveAttendance() {
        if (!state.sheet) return;
        var marks = state.sheet.rows.map(function (r) { return { studentId: r.studentId, status: state.marks[r.studentId] || null }; });
        api("/automation/attendance/save", { method: "POST", body: { sessionId: state.sheet.session.id, marks: marks } }).then(function (res) { if (handle(res)) { renderSheet(res.data); loadSessions(); } });
    }

    function loadSessions() {
        api("/automation/attendance/sessions?" + qs({ batchId: el("sessBatchFilter").value })).then(function (res) {
            if (!handle(res, false)) return;
            el("sessionsBody").innerHTML = res.data.slice(0, 60).map(function (s) {
                return '<tr><td>' + d(s.sessionDate) + '</td><td>' + esc(s.batchName) + '</td><td>' + esc(s.sessionType) + '</td><td class="num text-success">' + s.present + '</td><td class="num text-danger">' + s.absent + '</td><td class="text-nowrap"><button class="au-view-btn" data-sess="' + s.id + '">Open</button> <button class="au-btn light sm" data-sdel="' + s.id + '" title="Delete session"><i class="fas fa-trash text-danger"></i></button></td></tr>';
            }).join("") || '<tr><td colspan="6" class="au-empty">No sessions yet. Open a sheet to start.</td></tr>';
            el("sessionsBody").querySelectorAll("[data-sess]").forEach(function (b) { b.addEventListener("click", function () { api("/automation/attendance/sessions/" + b.dataset.sess).then(function (r) { if (handle(r, false)) { renderSheet(r.data); el("attBatch").value = r.data.session.batchId; el("attDate").value = r.data.session.sessionDate; } }); }); });
            el("sessionsBody").querySelectorAll("[data-sdel]").forEach(function (b) { b.addEventListener("click", function () { if (!confirm("Delete this session and all its attendance marks?")) return; api("/automation/attendance/sessions/" + b.dataset.sdel, { method: "DELETE" }).then(function (r) { if (handle(r)) { loadSessions(); if (state.sheet && String(state.sheet.session.id) === b.dataset.sdel) el("sheetCard").classList.add("d-none"); } }); }); });
        });
    }

    // ---- settings: Student ID series ---------------------------------------------------------------

    function renderStudentIdSeries(info) {
        el("sidCurrentNext").textContent = info.nextStudentId;
        el("sidHighestExisting").textContent = info.highestExistingStudentId || "None issued yet";
        el("sidNewNumber").min = info.highestExistingNumber + 1;
        el("sidNewNumber").placeholder = "e.g. " + info.nextNumber;
    }

    function loadStudentIdSeries() {
        api("/automation/settings/student-id-sequence").then(function (res) {
            if (!handle(res, false)) return;
            renderStudentIdSeries(res.data);
        });
    }

    // ---- masters ----------------------------------------------------------------------------------

    function loadMasters() {
        loadLookups().then(function (l) {
            if (!l) return;
            el("batchesBody").innerHTML = l.batches.map(function (b) { return '<tr><td><strong>' + esc(b.name) + '</strong></td><td>' + esc(b.schedule || "—") + '</td><td>' + (b.startDate ? d(b.startDate) : "—") + '</td><td class="num">' + b.students + '</td><td>' + badge(b.active ? "ACTIVE" : "ARCHIVED") + '</td><td class="text-nowrap"><button class="au-btn light sm" data-bedit="' + b.id + '"><i class="fas fa-edit"></i></button> <button class="au-btn light sm" data-btoggle="' + b.id + '" data-active="' + !b.active + '">' + (b.active ? "Deactivate" : "Activate") + '</button></td></tr>'; }).join("");
            el("coursesBody").innerHTML = l.courses.map(function (c) { return '<tr><td><strong>' + esc(c.name) + '</strong></td><td class="num">' + inr(c.defaultFee) + '</td><td>' + esc(c.duration || "—") + '</td><td class="num">' + c.students + '</td><td>' + badge(c.active ? "ACTIVE" : "ARCHIVED") + '</td><td><button class="au-btn light sm" data-cedit="' + c.id + '"><i class="fas fa-edit"></i></button></td></tr>'; }).join("");
            var mrow = function (m) { return '<tr><td class="mono">' + esc(m.code) + '</td><td>' + esc(m.label) + '</td><td>' + badge(m.active ? "ACTIVE" : "ARCHIVED") + '</td><td><button class="au-btn light sm" data-medit="' + m.id + '" data-cat="' + esc(m.category) + '"><i class="fas fa-edit"></i></button></td></tr>'; };
            el("modesBody").innerHTML = l.paymentModes.map(mrow).join("");
            el("typesBody").innerHTML = l.sessionTypes.map(mrow).join("");
            el("view-masters").querySelectorAll("[data-bedit]").forEach(function (b) { b.addEventListener("click", function () { openBatchForm(l.batches.find(function (x) { return String(x.id) === b.dataset.bedit; })); }); });
            el("view-masters").querySelectorAll("[data-btoggle]").forEach(function (b) { b.addEventListener("click", function () { api("/automation/batches/" + b.dataset.btoggle + "/active?active=" + b.dataset.active, { method: "PATCH" }).then(function (r) { if (handle(r)) loadMasters(); }); }); });
            el("view-masters").querySelectorAll("[data-cedit]").forEach(function (b) { b.addEventListener("click", function () { openCourseForm(l.courses.find(function (x) { return String(x.id) === b.dataset.cedit; })); }); });
            el("view-masters").querySelectorAll("[data-medit]").forEach(function (b) { b.addEventListener("click", function () { var all = l.paymentModes.concat(l.sessionTypes, l.installments); openMasterForm(b.dataset.cat, all.find(function (x) { return String(x.id) === b.dataset.medit; })); }); });
        });
    }
    function openBatchForm(b) {
        openForm(b ? "Edit Batch" : "Add Batch", fld("name", "Batch name", b && b.name, { required: true, max: 50, placeholder: "e.g. 4TH" }) + fld("schedule", "Schedule", b && b.schedule, { max: 100, placeholder: "e.g. Sunday 10am" }) + fld("startDate", "Start date", b && b.startDate, { type: "date" }) + fld("active", "Active", b ? b.active : true, { type: "checkbox" }), function (v) {
            return api(b ? "/automation/batches/" + b.id : "/automation/batches", { method: b ? "PUT" : "POST", body: v }).then(function (r) { if (handle(r)) { formModal.hide(); loadMasters(); } else formError(errText(r)); });
        });
    }
    function openCourseForm(c) {
        openForm(c ? "Edit Course" : "Add Course", fld("name", "Course name", c && c.name, { required: true, max: 150 }) + fld("defaultFee", "Default fee (₹)", c ? c.defaultFee : "", { type: "number", step: "0.01", min: 0, required: true }) + fld("duration", "Duration", c && c.duration, { max: 100, placeholder: "e.g. 3 months" }) + fld("active", "Active", c ? c.active : true, { type: "checkbox" }), function (v) {
            v.defaultFee = Number(v.defaultFee);
            return api(c ? "/automation/courses/" + c.id : "/automation/courses", { method: c ? "PUT" : "POST", body: v }).then(function (r) { if (handle(r)) { formModal.hide(); loadMasters(); } else formError(errText(r)); });
        });
    }
    function openMasterForm(category, m) {
        openForm((m ? "Edit " : "Add ") + (category === "PAYMENT_MODE" ? "Payment Mode" : "Session Type"), '<input type="hidden" name="category" value="' + esc(category) + '">' + fld("code", "Code (UPPER_CASE)", m && m.code, { required: true, max: 40, placeholder: "e.g. NEFT" }) + fld("label", "Label", m && m.label, { required: true, max: 100 }) + fld("active", "Active", m ? m.active : true, { type: "checkbox" }), function (v) {
            v.code = String(v.code).trim().toUpperCase().replace(/\s+/g, "_");
            return api(m ? "/automation/master/" + m.id : "/automation/master", { method: m ? "PUT" : "POST", body: v }).then(function (r) { if (handle(r)) { formModal.hide(); loadMasters(); } else formError(errText(r)); });
        });
    }

    // ---- reports -----------------------------------------------------------------------------------

    function reportQuery() {
        var range = el("repRange").value, from = el("repFrom").value, to = el("repTo").value, t = new Date();
        if (range === "today") { from = to = today(); }
        else if (range === "week") { var s = new Date(t); s.setDate(t.getDate() - ((t.getDay() + 6) % 7)); from = s.toISOString().slice(0, 10); to = today(); }
        else if (range === "month") { from = today().slice(0, 8) + "01"; to = today(); }
        else if (range === "") { from = to = ""; }
        return qs({ batchId: el("repBatch").value, courseId: el("repCourse").value, mode: el("repMode").value, from: from, to: to, threshold: el("repThreshold").value });
    }
    function runReport() {
        api("/automation/reports/" + el("repType").value + "?" + reportQuery()).then(function (res) {
            if (!handle(res, false)) return;
            var t = res.data; state.report = t;
            el("reportTitle").innerHTML = '<h5 class="fw-bold mb-0" style="color:var(--au-navy)">' + esc(t.title) + '</h5><small class="text-muted">' + t.rows.length + ' row(s) · generated ' + dt(new Date().toISOString()) + '</small>';
            el("reportTable").querySelector("thead").innerHTML = '<tr>' + t.columns.map(function (c) { return '<th>' + esc(c) + '</th>'; }).join("") + '</tr>';
            el("reportTable").querySelector("tbody").innerHTML = t.rows.map(function (r) { return '<tr>' + r.map(function (c) { return '<td>' + (typeof c === "number" ? '<span class="num">' + Number(c).toLocaleString("en-IN") + '</span>' : esc(c)) + '</td>'; }).join("") + '</tr>'; }).join("") || '<tr><td colspan="' + t.columns.length + '" class="au-empty">No rows for these filters.</td></tr>';
            el("reportTotals").innerHTML = Object.keys(t.totals).map(function (k) { var v = t.totals[k]; return '<span class="me-3"><strong>' + esc(k) + ':</strong> ' + (typeof v === "object" ? esc(JSON.stringify(v)) : typeof v === "number" ? Number(v).toLocaleString("en-IN") : esc(v)) + '</span>'; }).join("");
        });
    }

    // ---- automation: email & whatsapp (recipients are the academy's own students) ----------------------

    function commFilter() { return { q: el("cfQ").value.trim(), batchId: el("cfBatch").value, courseId: el("cfCourse").value, status: el("cfStatus").value }; }
    function historyQuery() { return { q: el("chQ").value.trim(), channel: el("chChannel").value, status: el("chStatus").value, from: el("chFrom").value, to: el("chTo").value }; }
    function renderSelCount() { el("commSelCount").innerText = Object.keys(state.commSelected).length; }
    function toggleSel(id, on, name) { if (on) state.commSelected[id] = name || true; else delete state.commSelected[id]; renderSelCount(); }

    function loadCommunicationView() {
        api("/automation/communications/status").then(function (res) {
            if (!res.success) { el("commStatus").innerHTML = '<div class="p-3 text-danger small">' + esc(res.message) + '</div>'; return; }
            var st = res.data; state.channelStatus = st;
            var ch = function (icon, name, ok, label, msg) { return '<div class="ch"><i class="' + icon + ' ' + (ok ? "text-success" : "text-danger") + '"></i><div><div class="t">' + name + ' <span class="au-badge ' + (ok ? "active" : "pending") + '">' + (ok ? esc(label) : "Not configured") + '</span></div><div class="s">' + esc(msg) + '</div></div></div>'; };
            el("commStatus").innerHTML = '<div class="au-comm-status">' + ch("fas fa-envelope", "Email", st.emailConfigured, "Configured · " + st.emailSource, st.emailMessage) + ch("fab fa-whatsapp", "WhatsApp", st.whatsappConfigured, "Ready · " + st.whatsappProvider, st.whatsappMessage) + '</div>';
        });
        // Batches and courses come from the Automation Admin's own master data (already loaded in L).
        if (L) {
            el("cfBatch").innerHTML = opts(L.batches, "id", function (b) { return b.name + (b.active ? "" : " (inactive)"); }, el("cfBatch").value, "All");
            el("cfCourse").innerHTML = opts(L.courses, "id", function (c) { return c.name; }, el("cfCourse").value, "All");
        }
        loadCommStudents(0);
    }

    function loadCommStudents(page) {
        state.commPage = page;
        var q = commFilter(); q.page = page; q.size = 25;
        api("/automation/communications/students?" + qs(q)).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data; state.commTotal = p.totalElements;
            el("commStudentsCount").innerText = p.totalElements + " student(s) match the current filters";
            el("commStudentsBody").innerHTML = p.content.map(function (r) {
                var sel = !!state.commSelected[r.id];
                return '<tr><td><input type="checkbox" class="au-check" data-sel="' + r.id + '" data-name="' + esc(r.fullName) + '"' + (sel ? " checked" : "") + '></td><td class="mono">' + esc(r.studentId || "—") + '</td><td><strong>' + esc(r.fullName) + '</strong></td><td>' + esc(r.mobile || "—") + '</td><td>' + esc(r.email || "—") + '</td><td><small>' + esc(r.batch || "—") + '</small></td><td><small>' + esc(r.course || "—") + '</small></td><td>' + badge(r.status) + '</td>' +
                    '<td class="text-nowrap"><button class="au-btn light sm" data-one="' + r.id + '" data-ch="EMAIL" data-name="' + esc(r.fullName) + '" title="Send email"' + (r.email ? "" : " disabled") + '><i class="fas fa-envelope"></i> Email</button> <button class="au-btn light sm" data-one="' + r.id + '" data-ch="WHATSAPP" data-name="' + esc(r.fullName) + '" title="Send WhatsApp"' + (r.mobile ? "" : " disabled") + '><i class="fab fa-whatsapp text-success"></i> WhatsApp</button> <button class="au-btn light sm" data-rs-student="' + r.id + '" title="Send a payment receipt by WhatsApp + Email"><i class="fas fa-file-invoice"></i> Receipt</button></td></tr>';
            }).join("") || '<tr><td colspan="9" class="au-empty">No students match these filters.</td></tr>';
            el("commCheckAll").checked = false;
            pager("commStudentsPager", p, loadCommStudents);
            el("commStudentsBody").querySelectorAll("[data-sel]").forEach(function (c) { c.addEventListener("change", function () { toggleSel(c.dataset.sel, c.checked, c.dataset.name); }); });
            el("commStudentsBody").querySelectorAll("[data-one]").forEach(function (b) { b.addEventListener("click", function () { openCompose({ studentIds: [Number(b.dataset.one)] }, b.dataset.name, b.dataset.ch); }); });
            el("commStudentsBody").querySelectorAll("[data-rs-student]").forEach(function (b) { b.addEventListener("click", function () { pickShareStudent(Number(b.dataset.rsStudent)); el("rsCard").scrollIntoView({ behavior: "smooth", block: "start" }); }); });
        });
    }

    function selectAllFiltered() {
        var q = commFilter(); q.page = 0; q.size = 200;
        api("/automation/communications/students?" + qs(q)).then(function (res) {
            if (!handle(res, false)) return;
            res.data.content.forEach(function (r) { state.commSelected[r.id] = r.fullName; });
            if (res.data.totalElements > 200) toast("Selected the first 200; use \"Send to all filtered\" for larger sets.", true);
            renderSelCount(); loadCommStudents(state.commPage);
        });
    }

    /** Compose -> preview (recipient count) -> confirm -> send (batched on the server). */
    function openCompose(target, who, channel) {
        var html = '<div class="alert alert-light border small py-2 mb-3"><strong>To:</strong> ' + esc(who) + '</div>' +
            '<div class="fld"><label>Channel</label><div class="d-flex gap-3">' +
            '<label class="d-flex align-items-center gap-1 fw-normal"><input type="checkbox" name="chEmail" ' + (channel !== "WHATSAPP" ? "checked" : "") + '> Email</label>' +
            '<label class="d-flex align-items-center gap-1 fw-normal"><input type="checkbox" name="chWa" ' + (channel === "WHATSAPP" ? "checked" : "") + '> WhatsApp</label>' +
            '<label class="d-flex align-items-center gap-1 fw-normal"><input type="checkbox" name="chBoth"> Both</label></div>' +
            (state.channelStatus && !state.channelStatus.whatsappConfigured ? '<div class="hint text-danger">' + esc(state.channelStatus.whatsappMessage) + '</div>' : '') + '</div>' +
            fld("subject", "Subject (required for email)", "", { max: 200 }) +
            fld("message", "Message", "", { type: "textarea", rows: 6, required: true }) +
            '<div class="fld"><label>Attachment <span class="fw-normal">(optional, PDF / image)</span></label><input type="file" name="attachment" accept="application/pdf,image/jpeg,image/png,image/webp"></div>';
        openForm("Send Message", html, function (v) {
            var both = el("formModalForm").querySelector('[name="chBoth"]').checked;
            var channels = [];
            if (both || v.chEmail) channels.push("EMAIL");
            if (both || v.chWa) channels.push("WHATSAPP");
            if (!channels.length) { formError("Choose Email, WhatsApp or Both."); return Promise.resolve(); }
            var fileInput = el("formModalForm").querySelector('[name="attachment"]');
            var file = fileInput && fileInput.files[0];
            var body = Object.assign({}, target, { channels: channels, subject: v.subject, message: v.message });
            return api("/automation/communications/preview", { method: "POST", body: body }).then(function (pv) {
                if (!pv.success) { formError(errText(pv)); return; }
                var p = pv.data;
                var msg = "You are about to send this message to " + p.recipients + " student(s).\n\nEmail: " + (channels.indexOf("EMAIL") >= 0 ? "YES (" + p.emailRecipients + " with an address)" : "NO") + "\nWhatsApp: " + (channels.indexOf("WHATSAPP") >= 0 ? "YES (" + p.whatsappRecipients + " with a mobile number) — each chat opens on this computer with the message ready; you press Send" : "NO") + "\n\nEmails are sent in controlled batches. Confirm?";
                if (!confirm(msg)) return;
                var upload = file ? (function () { var fd = new FormData(); fd.append("file", file); return api("/automation/communications/attachments", { method: "POST", body: fd }); })() : Promise.resolve({ success: true, data: null });
                return upload.then(function (up) {
                    if (!up.success) { formError(errText(up)); return; }
                    if (up.data) { body.attachmentPath = up.data.path; body.attachmentName = up.data.name; }
                    return api("/automation/communications/send", { method: "POST", body: body }).then(function (r) {
                        if (!handle(r)) { formError(errText(r)); return; }
                        formModal.hide(); state.commSelected = {}; renderSelCount();
                        if (r.data && r.data.recipients > 1 && r.data.emailQueued > 0) watchBatch(r.data.batchRef);
                        if (r.data && r.data.whatsappChats && r.data.whatsappChats.length) setTimeout(function () { showWhatsAppChats(r.data.whatsappChats); }, 350);
                    });
                });
            });
        }, "Send", "modal-lg");
    }

    function watchBatch(batchRef) {
        var tries = 0;
        var tick = function () {
            api("/automation/communications/batches/" + batchRef).then(function (r) {
                if (!r.success) return;
                var b = r.data;
                if (b.pending > 0 && tries++ < 120) { setTimeout(tick, 3000); return; }
                toast("Emails " + batchRef + ": " + b.sent + " sent, " + b.failed + " failed" + (b.pending ? ", " + b.pending + " still pending" : "") + ". See Message History.", b.failed === 0);
            });
        };
        setTimeout(tick, 1500);
    }

    // ---- automation: one-click payment receipt (WhatsApp + Email) -------------------------------------
    // The receipt is the existing one: emailed as its PDF, and on WhatsApp as a picture of that PDF
    // (copied by the click, pasted in the chat with Ctrl + V). The server takes the addresses from the
    // receipt's own student. Each channel shows its real outcome — never "Sent" unless it was.

    var SHARE_STATUS = {
        SENT: ["fas fa-check-circle", "text-success", "Sent"],
        OPENED: ["fab fa-whatsapp", "text-success", "Opened in WhatsApp — press Ctrl + V, then Send"],
        FAILED: ["fas fa-times-circle", "text-danger", "Failed"],
        NOT_CONFIGURED: ["fas fa-exclamation-circle", "text-warning", "Not configured"],
        MISSING_RECIPIENT: ["fas fa-exclamation-triangle", "text-danger", "Not sent"]
    };

    function pickShareStudent(id) {
        el("rsStudentResults").style.display = "none";
        dropPicture(state.share);
        var sh = state.share = { studentId: id, student: null, receipts: [], receiptId: null, busy: false, result: null, chat: null, picture: null, wa: null };
        el("rsBody").innerHTML = '<div class="au-empty">Loading…</div>';
        Promise.all([api("/automation/students/" + id), api("/automation/receipts?" + qs({ studentId: id, size: 100 }))]).then(function (r) {
            if (state.share !== sh) return;
            if (!r[0].success) { el("rsBody").innerHTML = '<div class="au-error">' + esc(r[0].message) + '</div>'; return; }
            sh.student = r[0].data;
            sh.receipts = r[1].success ? r[1].data.content : [];
            el("rsStudentQ").value = sh.student.studentId + " — " + sh.student.fullName;
            // A single receipt is not a choice; with several, the admin must pick one.
            if (sh.receipts.length === 1) sh.receiptId = sh.receipts[0].id;
            renderShare();
            if (sh.receiptId) loadShareChat(sh);
        });
    }

    /** The WhatsApp line and the receipt picture are fetched before the click, so the click can copy and open at once. */
    function loadShareChat(sh) {
        var forReceipt = sh.receiptId;
        var current = function () { return state.share === sh && sh.receiptId === forReceipt; };
        sh.chat = { loading: true };
        dropPicture(sh);
        sh.picture = { loading: true };
        renderShare();
        api("/automation/communications/receipts/" + forReceipt + "/whatsapp").then(function (res) {
            if (!current()) return;
            sh.chat = res.success ? res.data : { available: false, reason: res.message || "The WhatsApp message could not be prepared." };
            renderShare();
        });
        fetchReceiptPicture(forReceipt).then(function (blob) {
            if (!current()) return;
            var r = sh.receipts.filter(function (x) { return String(x.id) === String(forReceipt); })[0];
            sh.picture = { blob: blob, url: URL.createObjectURL(blob), name: receiptPictureName(r) };
            renderShare();
        }, function () {
            if (!current()) return;
            sh.picture = { error: "The receipt picture could not be prepared." };
            renderShare();
        });
    }

    /** The receipt as a picture (PNG), bearer-authenticated like the PDF preview — never a public link. */
    function fetchReceiptPicture(receiptId) {
        var s = LSI_Auth.getSession();
        return fetch(LSI_Auth.apiBase + "/automation/communications/receipts/" + receiptId + "/image", { headers: { Authorization: "Bearer " + (s ? s.token : "") } }).then(function (r) {
            if (!r.ok) throw new Error("The receipt picture could not be prepared (" + r.status + ").");
            return r.blob();
        });
    }

    /** Same name as the server gives it: Fee-Receipt-<receipt no>-<payment no>.png */
    function receiptPictureName(r) {
        var safe = function (s) { return s == null ? "NA" : String(s).replace(/[^A-Za-z0-9-]/g, "-"); };
        return r ? "Fee-Receipt-" + safe(r.receiptNo) + "-" + safe(r.paymentNo) + ".png" : "Fee-Receipt.png";
    }

    function dropPicture(sh) {
        if (sh && sh.picture && sh.picture.url) URL.revokeObjectURL(sh.picture.url);
        if (sh) sh.picture = null;
    }

    /**
     * Copies the receipt picture, then opens the student's chat with the line typed. Copy first: the
     * clipboard only works while this page is in front, and WhatsApp takes the front. A browser that
     * cannot copy pictures gets the picture downloaded instead, to attach in WhatsApp.
     * Resolves { opened: boolean, picture: "copied" | "saved" | "none" }.
     */
    function openChatWithPicture(chat, picture, fileName) {
        var copying = picture ? LSI_WhatsAppChat.copyPicture(picture) : Promise.resolve(false);
        return copying.then(function (copied) {
            var how = copied ? "copied" : (picture && LSI_WhatsAppChat.savePicture(picture, fileName) ? "saved" : "none");
            var opened = LSI_WhatsAppChat.open(chat.phone, chat.text);
            if (!opened) toast("The browser blocked WhatsApp — allow pop-ups for this site, then use \"Open WhatsApp again\".", false);
            return { opened: opened, picture: how };
        });
    }

    function pictureToast(how) {
        return how === "copied" ? "WhatsApp opened — press Ctrl + V to paste the receipt picture, then Send."
            : how === "saved" ? "WhatsApp opened — the receipt picture was downloaded: attach it in the chat, then Send."
            : "WhatsApp opened — the receipt picture could not be prepared; attach the receipt PDF instead.";
    }

    /** The one step left in WhatsApp, said plainly after the click. */
    function pictureStepHtml(wa) {
        if (!wa) return "";
        if (wa.picture === "copied") return '<div class="rs-steps"><i class="fas fa-paste me-1"></i>The receipt picture is copied. In the WhatsApp chat press <kbd>Ctrl</kbd> + <kbd>V</kbd>, then <strong>Send</strong>.</div>';
        if (wa.picture === "saved") return '<div class="rs-steps warn"><i class="fas fa-download me-1"></i>This browser could not copy the picture, so it was downloaded. In WhatsApp click the attach button (📎 or +) → <strong>Photos &amp; videos</strong>, choose it, then <strong>Send</strong>.</div>';
        return '<div class="rs-steps warn"><i class="fas fa-exclamation-triangle me-1"></i>The receipt picture could not be prepared. Attach the receipt PDF from "Preview" in WhatsApp (📎 or + → Document).</div>';
    }

    function renderShare() {
        var sh = state.share; if (!sh || !sh.student) return;
        var s = sh.student;
        var contact = function (icon, value, missing) { return '<div class="rs-contact"><i class="' + icon + ' me-1"></i>' + (value ? esc(value) : '<span class="text-danger">' + missing + '</span>') + '</div>'; };
        var html = '<div class="rs-student"><div><strong>' + esc(s.fullName) + '</strong> <span class="mono text-muted">' + esc(s.studentId) + '</span></div>' +
            contact("fab fa-whatsapp text-success", s.mobile, "No mobile number — WhatsApp cannot be sent") +
            contact("fas fa-envelope text-muted", s.email, "No email address — email cannot be sent") + '</div>';
        if (!sh.receipts.length) {
            html += '<div class="au-empty">No payment receipts for this student yet — a receipt is created when a payment is recorded.</div>';
        } else {
            html += '<div class="au-table-wrap"><table class="au-table"><thead><tr><th style="width:36px"></th><th>Receipt No</th><th>Payment No</th><th>Installment</th><th>Payment Date</th><th class="num">Amount</th><th></th></tr></thead><tbody>' +
                sh.receipts.map(function (r) {
                    var on = String(sh.receiptId) === String(r.id);
                    return '<tr class="rs-row' + (on ? " on" : "") + '" data-rs="' + r.id + '"><td><input type="radio" name="rsReceipt" value="' + r.id + '"' + (on ? " checked" : "") + (sh.busy ? " disabled" : "") + ' aria-label="Select receipt ' + esc(r.paymentNo) + '"></td>' +
                        '<td class="mono">' + esc(r.receiptNo) + '</td><td class="mono">' + esc(r.paymentNo) + '</td><td>' + esc(ordinal(r.installmentNo)) + ' Installment</td><td>' + d(r.paymentDate) + '</td><td class="num">' + inr(r.amountPaid) + '</td>' +
                        '<td><button type="button" class="au-btn light sm" data-rs-pdf="' + r.id + '" title="Open the PDF exactly as it will be sent"><i class="fas fa-file-pdf"></i> Preview</button></td></tr>';
                }).join("") + '</tbody></table></div>' +
                (sh.receiptId ? '' : '<div class="hint mt-2">This student has ' + sh.receipts.length + ' receipts — select the one to send.</div>') +
                (sh.receiptId ? shareChatHtml(sh) : '') +
                '<div class="d-flex flex-wrap align-items-center gap-2 mt-3"><button type="button" class="au-btn success" id="rsSend"' + (sh.receiptId && !sh.busy && !(sh.chat && sh.chat.loading) && !(sh.chat && sh.chat.available && sh.picture && sh.picture.loading) ? "" : " disabled") + '>' +
                (sh.busy ? '<span class="spinner-border spinner-border-sm"></span> Sending…' : '<i class="fas fa-envelope"></i><i class="fab fa-whatsapp"></i> Email + WhatsApp Receipt') + '</button>' +
                '<small class="text-muted">One click emails the receipt PDF, copies the receipt picture and opens the student\'s WhatsApp chat — press Ctrl + V there, then Send.</small></div>';
        }
        html += '<div id="rsResult">' + (sh.result ? shareResultHtml(sh.result, sh.wa) : "") + '</div>';
        el("rsBody").innerHTML = html;
        el("rsBody").querySelectorAll("[data-rs]").forEach(function (row) {
            row.addEventListener("click", function (e) {
                if (sh.busy || e.target.closest("[data-rs-pdf]")) return;
                sh.receiptId = Number(row.dataset.rs); sh.result = null; renderShare(); loadShareChat(sh);
            });
        });
        el("rsBody").querySelectorAll("[data-rs-pdf]").forEach(function (b) { b.addEventListener("click", function () { openReceiptPdf(b.dataset.rsPdf); }); });
        var send = el("rsSend"); if (send) send.addEventListener("click", sendShareReceipt);
        var again = el("rsWaAgain"); if (again) again.addEventListener("click", function () {
            var c = sh.result && sh.result.whatsappChat, p = sh.picture || {};
            if (c) openChatWithPicture(c, p.blob, p.name).then(function (wa) { if (wa.opened) toast(pictureToast(wa.picture), wa.picture !== "none"); });
        });
    }

    /** What WhatsApp will get: the receipt picture (pasted) with one short line. */
    function shareChatHtml(sh) {
        var c = sh.chat, p = sh.picture || {};
        if (!c || c.loading) return '<div class="rs-wa"><div class="t"><span class="spinner-border spinner-border-sm"></span> Preparing the WhatsApp message…</div></div>';
        if (!c.available) return '<div class="rs-wa" style="background:#fef2f2;border-color:#fecaca"><div class="t text-danger"><i class="fab fa-whatsapp"></i> WhatsApp: ' + esc(c.reason) + '</div><div class="small text-muted">The email is still sent.</div></div>';
        var picture = p.url ? '<a href="' + esc(p.url) + '" target="_blank" rel="noopener" title="See the picture full size"><img class="rs-pic" src="' + esc(p.url) + '" alt="Receipt picture"></a>'
            : p.error ? '<div class="rs-pic-note text-danger"><i class="fas fa-exclamation-triangle me-1"></i>' + esc(p.error) + ' WhatsApp will open with the line only.</div>'
            : '<div class="rs-pic-note text-muted"><span class="spinner-border spinner-border-sm"></span> Preparing the receipt picture…</div>';
        return '<div class="rs-wa"><div class="t"><i class="fab fa-whatsapp"></i> WhatsApp to ' + esc(c.mobile) + ' — the receipt picture with this line</div>' +
            '<div class="rs-wa-body">' + picture + '<div class="rs-wa-text"><pre>' + esc(c.text) + '</pre>' +
            '<div class="rs-steps">The button copies the picture and opens the chat. In WhatsApp press <kbd>Ctrl</kbd> + <kbd>V</kbd>, then <strong>Send</strong>.</div></div></div></div>';
    }

    function sendShareReceipt() {
        var sh = state.share;
        if (!sh || !sh.receiptId || sh.busy) return;       // a second click while sending does nothing
        var chat = sh.chat && sh.chat.available ? sh.chat : null, p = sh.picture || {};
        sh.busy = true; sh.result = null; sh.wa = null; renderShare();
        // From this click: the picture is copied, then WhatsApp opens with the line prepared beforehand.
        (chat ? openChatWithPicture(chat, p.blob, p.name) : Promise.resolve(null)).then(function (wa) {
            sh.wa = wa;
            return api("/automation/communications/receipts/" + sh.receiptId + "/share", { method: "POST" });
        }).then(function (res) {
            sh.busy = false;
            sh.result = res.success ? res.data : { error: res.message || "Something went wrong." };
            if (state.share === sh) renderShare();
        });
    }

    function shareResultHtml(r, wa) {
        if (r.error) return '<div class="au-error mt-3"><strong>The receipt was not sent.</strong> ' + esc(r.error) + '</div>';
        var line = function (name, c) {
            var m = SHARE_STATUS[c.status] || SHARE_STATUS.FAILED;
            return '<div class="rs-line" data-channel="' + esc(c.channel) + '" data-status="' + esc(c.status) + '"><i class="' + m[0] + ' ' + m[1] + '"></i><div><strong>' + name + ':</strong> <span class="' + m[1] + ' fw-semibold">' + m[2] + '</span><div class="text-muted small">' + esc(c.message) + '</div></div></div>';
        };
        return '<div class="rs-result mt-3"><div class="fw-bold mb-1" style="color:var(--au-navy)">Receipt Sharing Result</div>' +
            '<div class="small mb-2">Student: <strong>' + esc(r.studentName) + '</strong> <span class="mono">' + esc(r.studentCode) + '</span> · Receipt <strong class="mono">' + esc(r.receiptNo) + '</strong> · Payment <span class="mono">' + esc(r.paymentNo) + '</span> · ' + inr(r.amountPaid) + '</div>' +
            line("Email", r.email) + line("WhatsApp", r.whatsapp) +
            (r.whatsappChat ? pictureStepHtml(wa) + '<div class="mt-1"><button type="button" class="au-btn light sm" id="rsWaAgain" title="Copies the receipt picture again and reopens the chat"><i class="fab fa-whatsapp text-success"></i> Open WhatsApp again</button></div>' : '') +
            '<div class="small mt-2"><a href="#comm-history" class="au-link">View in Message History</a></div></div>';
    }

    /** Opens the receipt PDF (bearer-authenticated, never a public link) in a new tab. */
    function openReceiptPdf(id) {
        var w = window.open("", "_blank");
        var s = LSI_Auth.getSession();
        fetch(LSI_Auth.apiBase + "/automation/communications/receipts/" + id + "/pdf", { headers: { Authorization: "Bearer " + (s ? s.token : "") } }).then(function (r) {
            if (!r.ok) throw new Error("The receipt PDF could not be opened (" + r.status + ").");
            return r.blob();
        }).then(function (b) {
            var u = URL.createObjectURL(b);
            if (w) w.location.href = u; else toast("Allow pop-ups to preview the receipt.", false);
            setTimeout(function () { URL.revokeObjectURL(u); }, 60000);
        }).catch(function (e) { if (w) w.close(); toast(e.message, false); });
    }

    // ---- automation: message history ------------------------------------------------------------------

    function loadHistory(page) {
        state.chPage = page;
        var q = historyQuery(); q.page = page; q.size = 25;
        api("/automation/communications/history?" + qs(q)).then(function (res) {
            if (!handle(res, false)) return;
            var p = res.data;
            el("chCount").innerText = p.totalElements + " message(s)";
            el("chBody").innerHTML = p.content.map(function (c) {
                var isWa = c.channel === "WHATSAPP";
                var retryable = !isWa && c.status === "FAILED";
                return '<tr><td class="text-nowrap">' + dt(c.createdAt) + '</td><td>' + esc(c.studentName || "—") + '<br><small class="text-muted">' + esc(c.recipient) + '</small></td><td class="mono">' + esc(c.studentCode || "—") + '</td><td>' + badge(c.channel) + '</td><td><small>' + esc(c.subject || "—") + '</small></td><td><small>' + esc(c.messageType.replace(/_/g, " ")) + '</small></td><td>' + badge(c.status) + '</td><td><small>' + esc(c.provider || "—") + '</small></td><td class="mono"><small>' + esc(c.providerMessageId || "—") + '</small></td><td><small class="text-danger">' + esc(c.errorReason || "") + '</small></td><td><small>' + esc(c.sentBy) + '</small></td>' +
                    '<td class="text-nowrap"><button class="au-view-btn" data-hv="' + c.id + '">View</button> ' + (retryable ? '<button class="au-btn light sm" data-hr="' + c.id + '"><i class="fas fa-redo"></i> Retry</button>' : '') +
                    (isWa ? '<button class="au-btn light sm" data-hw="' + c.id + '"' + (c.messageType === "FEE_RECEIPT" && c.receiptId
                        ? ' data-hw-receipt="' + c.receiptId + '" data-hw-name="' + esc((c.attachmentName || "Fee-Receipt.png").replace(/\.pdf$/i, ".png")) + '" title="Copy the receipt picture and open this chat in WhatsApp"'
                        : ' title="Open this chat in WhatsApp with the message typed"') + '><i class="fab fa-whatsapp text-success"></i> ' + (c.status === "READY" ? "Open" : "Open again") + '</button>' : '') + '</td></tr>';
            }).join("") || '<tr><td colspan="12" class="au-empty">No messages match these filters.</td></tr>';
            pager("chPager", p, loadHistory);
            el("chBody").querySelectorAll("[data-hv]").forEach(function (b) { b.addEventListener("click", function () { viewMessage(b.dataset.hv); }); });
            el("chBody").querySelectorAll("[data-hr]").forEach(function (b) { b.addEventListener("click", function () { api("/automation/communications/history/" + b.dataset.hr + "/retry", { method: "POST" }).then(function (r) { handle(r); loadHistory(state.chPage); }); }); });
            el("chBody").querySelectorAll("[data-hw]").forEach(function (b) { b.addEventListener("click", function () { reopenWhatsApp(b.dataset.hw, b.dataset.hwReceipt, b.dataset.hwName, function () { loadHistory(state.chPage); }); }); });
        });
    }

    /** Opens a WhatsApp chat from the history; for a receipt the picture is copied again first. */
    function reopenWhatsApp(id, receiptId, pictureName, done) {
        if (receiptId) {
            // The picture is fetched and copied while this page is still in front; then the chat opens.
            var picture = fetchReceiptPicture(receiptId);
            var copying = LSI_WhatsAppChat.copyPicture(picture);   // started inside the click
            Promise.all([copying, picture.then(null, function () { return null; }),
                api("/automation/communications/history/" + id + "/retry", { method: "POST" })]).then(function (x) {
                var copied = x[0], blob = x[1], r = x[2];
                var c = r.success && r.data && r.data.whatsappChat;
                if (!c) { toast(errText(r) || "WhatsApp could not be opened.", false); if (done) done(); return; }
                var how = copied ? "copied" : (blob && LSI_WhatsAppChat.savePicture(blob, pictureName) ? "saved" : "none");
                if (LSI_WhatsAppChat.open(c.phone, c.text)) toast(pictureToast(how), how !== "none");
                else toast("The browser blocked WhatsApp — allow pop-ups for this site, then click again.", false);
                if (done) done();
            });
            return;
        }
        var h = LSI_WhatsAppChat.prepare();   // inside the click, so the browser allows the tab
        api("/automation/communications/history/" + id + "/retry", { method: "POST" }).then(function (r) {
            var c = r.success && r.data && r.data.whatsappChat;
            if (c) { LSI_WhatsAppChat.openPrepared(h, c.phone, c.text); toast("WhatsApp opened — press Send there.", true); }
            else { LSI_WhatsAppChat.cancel(h); toast(errText(r) || "WhatsApp could not be opened.", false); }
            if (done) done();
        });
    }

    /**
     * After a send: one WhatsApp chat per student, opened one at a time on this computer (browsers and
     * WhatsApp do not allow opening many chats at once). Each opened chat is recorded in Message History.
     */
    function showWhatsAppChats(chats) {
        var rows = chats.map(function (c, i) {
            return '<tr data-wq="' + i + '"><td><strong>' + esc(c.studentName) + '</strong><br><small class="mono text-muted">' + esc(c.studentCode || "") + '</small></td><td class="mono">' + esc(c.mobile) + '</td>' +
                '<td class="text-end"><button type="button" class="au-btn success sm" data-wq-open="' + i + '"><i class="fab fa-whatsapp"></i> Open</button></td></tr>';
        }).join("");
        var html = '<div class="alert alert-light border small py-2 mb-2"><i class="fab fa-whatsapp text-success me-1"></i> Each button opens that student\'s WhatsApp chat on this computer with the message typed. <strong>Press Send in WhatsApp</strong>, come back, then open the next one.</div>' +
            '<div class="d-flex justify-content-between align-items-center mb-2"><small class="text-muted"><span id="wqDone">0</span> of ' + chats.length + ' opened</small><button type="button" class="au-btn primary sm" id="wqNext"><i class="fas fa-forward"></i> Open next</button></div>' +
            '<div class="au-table-wrap" style="max-height:340px;overflow:auto"><table class="au-table wa-queue"><tbody>' + rows + '</tbody></table></div>' +
            '<div class="mt-2"><strong class="small">Message</strong><div class="border rounded p-2 bg-light small" style="white-space:pre-wrap;max-height:140px;overflow:auto">' + esc(chats[0].text) + '</div></div>';
        openForm("WhatsApp — open each chat and press Send", html, function () { formModal.hide(); return Promise.resolve(); }, "Done", "modal-lg");
        var form = el("formModalForm"), opened = {};
        function openOne(i) {
            var c = chats[i];
            if (!LSI_WhatsAppChat.open(c.phone, c.text)) { toast("Allow pop-ups for this site to open WhatsApp.", false); return; }
            api("/automation/communications/history/" + c.logId + "/opened", { method: "POST" });
            opened[i] = true;
            var tr = form.querySelector('[data-wq="' + i + '"]'); if (tr) { tr.classList.add("done"); tr.querySelector("button").innerHTML = '<i class="fas fa-check"></i> Opened'; }
            el("wqDone").innerText = Object.keys(opened).length;
        }
        form.querySelectorAll("[data-wq-open]").forEach(function (b) { b.addEventListener("click", function () { openOne(Number(b.dataset.wqOpen)); }); });
        el("wqNext").addEventListener("click", function () {
            for (var i = 0; i < chats.length; i++) { if (!opened[i]) { openOne(i); return; } }
            toast("All chats have been opened.", true);
        });
    }

    function viewMessage(id) {
        api("/automation/communications/history/" + id).then(function (res) {
            if (!handle(res, false)) return;
            var c = res.data;
            var kv = [["Date", dt(c.createdAt)], ["Student", esc(c.studentName || "—") + " · " + esc(c.studentCode || "")], ["Recipient", esc(c.recipient)], ["Channel", esc(c.channel)], ["Type", esc(c.messageType)], ["Status", esc(c.status)], ["Provider", esc(c.provider || "—")], ["Provider message ID", esc(c.providerMessageId || "—")], ["Attempts", c.attempts], ["Sent by", esc(c.sentBy)]];
            if (c.attachmentName) kv.push(["Attachment", esc(c.attachmentName)]);
            if (c.errorReason) kv.push(["Error", '<span class="text-danger">' + esc(c.errorReason) + '</span>']);
            openForm("Message #" + c.id, '<div class="au-kv">' + kv.map(function (x) { return '<div><span>' + x[0] + '</span><strong>' + x[1] + '</strong></div>'; }).join("") + '</div>' +
                '<div class="mt-3"><strong class="small">Subject</strong><div>' + esc(c.subject || "—") + '</div></div><div class="mt-2"><strong class="small">Message</strong><div class="border rounded p-2 bg-light" style="white-space:pre-wrap;font-size:13px">' + esc(c.body || "") + '</div></div>',
                function () { formModal.hide(); return Promise.resolve(); }, "Close");
        });
    }

    // ---- global search / notifications / quick actions --------------------------------------------

    function globalSearch(q) {
        var box = el("globalSearchResults");
        if (q.length < 2) { box.style.display = "none"; return; }
        api("/automation/search?q=" + encodeURIComponent(q)).then(function (res) {
            if (!res.success) return;
            box.innerHTML = res.data.length ? res.data.map(function (h) { return '<div class="hit" data-student="' + h.id + '"><div><strong>' + esc(h.fullName) + '</strong><small>' + esc(h.studentId) + ' · ' + esc(h.batchName || "—") + ' · ' + esc(h.mobile || "") + '</small></div><div>' + badge(h.paymentStatus) + '</div></div>'; }).join("") : '<div class="p-3 text-muted small">No student matches "' + esc(q) + '".</div>';
            box.style.display = "block";
            box.querySelectorAll("[data-student]").forEach(function (b) { b.addEventListener("click", function () { box.style.display = "none"; el("globalSearch").value = ""; go("student", b.dataset.student); }); });
        });
    }

    function quick(action) {
        if (action === "add-student") openStudentForm(null);
        else if (action === "record-payment") { go("payments"); setTimeout(function () { el("payStudentQ").focus(); }, 250); }
        else if (action === "generate-receipt") { go("payments"); toast("Receipts are generated automatically when you save a payment.", true); setTimeout(function () { el("payStudentQ").focus(); }, 250); }
        else if (action === "mark-attendance") go("attendance");
        else if (action === "search-student") { el("globalSearch").focus(); }
        else if (action === "export-report") go("reports");
    }

    // ---- init --------------------------------------------------------------------------------------

    document.addEventListener("DOMContentLoaded", function () {
        if (!LSI_Auth.isAuthenticated()) return;
        formModal = new bootstrap.Modal(el("formModal"));
        el("formModalForm").addEventListener("submit", function (e) { e.preventDefault(); if (!formSubmitHandler) return; var b = el("formModalSubmit"); b.disabled = true; Promise.resolve(formSubmitHandler(vals())).finally(function () { b.disabled = false; }); });
        LSI_Auth.refreshProfile().then(function (p) { if (p) { el("adminName").innerText = p.name; document.querySelector(".au-user .role").innerText = p.role === "admin" ? "Main Admin (viewing)" : "Automation Admin"; } });

        document.querySelectorAll("[data-view]").forEach(function (a) { a.addEventListener("click", function (e) { e.preventDefault(); go(a.dataset.view); }); });
        window.addEventListener("hashchange", route);
        el("menuBtn").addEventListener("click", function () { el("sidebar").classList.toggle("open"); el("backdrop").classList.toggle("show"); });
        el("backdrop").addEventListener("click", function () { el("sidebar").classList.remove("open"); el("backdrop").classList.remove("show"); });
        // ---- Settings: User ID / password / logout. The User ID is the local part of the account
        // email (what /api/auth/login accepts as a short username); every change goes through the
        // backend, which re-verifies the current password and the account's role.
        var AUTOMATION_LOGIN = "student-login.html?portal=automation";
        function showAutomationAccount(profile) {
            var email = profile && profile.email ? String(profile.email) : "";
            el("automationCurrentUserId").value = email.split("@")[0];
            var who = el("automationSessionUser"); if (who && profile) who.textContent = (profile.name || "Automation Admin") + " (" + email.split("@")[0] + ")";
        }
        showAutomationAccount(LSI_Auth.getCurrentUser());
        el("automationUserIdForm").addEventListener("submit", function (e) {
            e.preventDefault();
            var current = el("automationCurrentUserId").value.trim();
            var next = el("automationNewUserId").value.trim();
            if (!/^[A-Za-z0-9][A-Za-z0-9._-]{3,19}$/.test(next)) { toast("User ID must be 4-20 characters: letters, numbers, dot, underscore or hyphen.", false); return; }
            if (next.toLowerCase() === current.toLowerCase()) { toast("The new User ID is the same as your current one.", false); return; }
            var btn = el("automationUserIdSubmit"); btn.disabled = true;
            LSI_Auth.changeUserId(current, next, el("automationUserIdPassword").value).then(function (r) {
                btn.disabled = false;
                el("automationUserIdPassword").value = "";
                if (r.status === 401) { toast("Your session has expired. Please log in again.", false); setTimeout(function () { LSI_Auth.logout(AUTOMATION_LOGIN); }, 900); return; }
                if (!handle(r)) return;
                el("automationNewUserId").value = "";
                // Re-read the account from the server so the cached session (and this form) carry the new ID.
                LSI_Auth.refreshProfile().then(function (p) { showAutomationAccount(p || LSI_Auth.getCurrentUser()); });
            });
        });
        el("automationPasswordForm").addEventListener("submit", function (e) {
            e.preventDefault();
            var next = el("automationNewPassword").value;
            if (next !== el("automationConfirmPassword").value) { toast("Passwords do not match.", false); return; }
            if (next.length < 8 || next.length > 72 || !/[A-Za-z]/.test(next) || !/[0-9]/.test(next)) { toast("New password must be 8-72 characters and contain at least one letter and one number.", false); return; }
            if (next === el("automationCurrentPassword").value) { toast("The new password must be different from the current password.", false); return; }
            var btn = el("automationPasswordSubmit"); btn.disabled = true;
            LSI_Auth.changePassword(el("automationCurrentPassword").value, next).then(function (r) {
                btn.disabled = false;
                if (r.status === 401) { toast("Your session has expired. Please log in again.", false); setTimeout(function () { LSI_Auth.logout(AUTOMATION_LOGIN); }, 900); return; }
                if (handle(r)) { el("automationPasswordForm").reset(); setTimeout(function () { LSI_Auth.logout(AUTOMATION_LOGIN); }, 1200); }
            });
        });
        el("automationLogoutBtn").addEventListener("click", function () { LSI_Auth.logout(AUTOMATION_LOGIN); });

        // ---- Student ID series (Settings) ---------------------------------------------------
        el("studentIdSeriesForm").addEventListener("submit", function (e) {
            e.preventDefault();
            var next = Number(el("sidNewNumber").value);
            if (!next || next < 1 || !Number.isInteger(next)) { toast("Enter a whole number of 1 or greater.", false); return; }
            var btn = el("studentIdSeriesSubmit"); btn.disabled = true;
            api("/automation/settings/student-id-sequence", { method: "PUT", body: { nextNumber: next } }).then(function (r) {
                btn.disabled = false;
                if (handle(r)) { el("sidNewNumber").value = ""; renderStudentIdSeries(r.data); }
            });
        });
        document.querySelectorAll("[data-quick]").forEach(function (b) { b.addEventListener("click", function () { quick(b.dataset.quick); }); });

        el("globalSearch").addEventListener("input", function () { var q = this.value.trim(); debounce("gs", function () { globalSearch(q); }, 250); });
        document.addEventListener("click", function (e) { if (!e.target.closest(".au-search")) el("globalSearchResults").style.display = "none"; if (!e.target.closest("#bellBtn") && !e.target.closest("#notifPanel")) el("notifPanel").style.display = "none"; if (!e.target.closest("#payStudentQ") && !e.target.closest("#payStudentResults")) el("payStudentResults").style.display = "none"; });
        el("bellBtn").addEventListener("click", function () { var p = el("notifPanel"); p.style.display = p.style.display === "block" ? "none" : "block"; });

        // dashboard
        el("dashBatch").addEventListener("change", function () { loadDashStudents(0); });
        el("dashPayStatus").addEventListener("change", function () { loadDashStudents(0); });
        // students
        ["stuBatch", "stuCourse", "stuPay", "stuYear", "stuStatus"].forEach(function (id) { el(id).addEventListener("change", function () { loadStudents(0); }); });
        el("stuQ").addEventListener("input", function () { debounce("stu", function () { loadStudents(0); }, 350); });
        // payments
        el("payStudentQ").addEventListener("input", function () {
            var q = this.value.trim(); el("payStudentId").value = ""; el("payStudentCard").classList.add("d-none"); state.payStudent = null;
            debounce("pay", function () {
                if (q.length < 2) { el("payStudentResults").style.display = "none"; return; }
                api("/automation/search?q=" + encodeURIComponent(q)).then(function (res) {
                    if (!res.success) return;
                    var box = el("payStudentResults");
                    box.innerHTML = res.data.length ? res.data.map(function (h) { return '<div class="hit" data-pick="' + h.id + '"><div><strong>' + esc(h.fullName) + '</strong><small>' + esc(h.studentId) + ' · ' + esc(h.batchName || "—") + ' · ' + esc(h.mobile || "") + '</small></div><div>' + badge(h.paymentStatus) + '<small class="d-block text-end">' + inr(h.balance) + ' due</small></div></div>'; }).join("") : '<div class="p-3 text-muted small">No match.</div>';
                    box.style.display = "block";
                    box.querySelectorAll("[data-pick]").forEach(function (b) { b.addEventListener("click", function () { api("/automation/students/" + b.dataset.pick).then(function (r) { if (r.success) selectPayStudent(r.data); }); }); });
                });
            }, 250);
        });
        // email & whatsapp: one-click payment receipt — student picker (same search as the payment form)
        el("rsStudentQ").addEventListener("input", function () {
            var q = this.value.trim();
            debounce("rs", function () {
                if (q.length < 2) { el("rsStudentResults").style.display = "none"; return; }
                api("/automation/search?q=" + encodeURIComponent(q)).then(function (res) {
                    if (!res.success) return;
                    var box = el("rsStudentResults");
                    box.innerHTML = res.data.length ? res.data.map(function (h) { return '<div class="hit" data-rs-pick="' + h.id + '"><div><strong>' + esc(h.fullName) + '</strong><small>' + esc(h.studentId) + ' · ' + esc(h.batchName || "—") + ' · ' + esc(h.mobile || "no mobile") + '</small></div></div>'; }).join("") : '<div class="p-3 text-muted small">No match.</div>';
                    box.style.display = "block";
                    box.querySelectorAll("[data-rs-pick]").forEach(function (b) { b.addEventListener("click", function () { pickShareStudent(Number(b.dataset.rsPick)); }); });
                });
            }, 250);
        });
        document.addEventListener("click", function (e) { if (!e.target.closest("#rsStudentQ") && !e.target.closest("#rsStudentResults")) el("rsStudentResults").style.display = "none"; });
        el("payForm").addEventListener("submit", submitPayment);
        el("payReset").addEventListener("click", resetPayForm);
        ["payListBatch", "payListMode", "payListFrom", "payListTo"].forEach(function (id) { el(id).addEventListener("change", loadPaymentsList); });
        el("payListRefresh").addEventListener("click", loadPaymentsList);
        // receipts
        el("rcRefresh").addEventListener("click", function () { loadReceipts(0); });
        el("rcQ").addEventListener("input", function () { debounce("rc", function () { loadReceipts(0); }, 350); });
        ["rcFrom", "rcTo"].forEach(function (id) { el(id).addEventListener("change", function () { loadReceipts(0); }); });
        el("rcPrint").addEventListener("click", printReceipt);
        el("rcPdf").addEventListener("click", function () { toast("Choose \"Save as PDF\" as the printer to download the receipt.", true); printReceipt(); });
        el("rcEmail").addEventListener("click", function () { if (!state.receipt) return; el("rcEmail").disabled = true; api("/automation/receipts/" + state.receipt.id + "/email", { method: "POST" }).then(function (r) { el("rcEmail").disabled = false; if (handle(r)) showReceipt(state.receipt.id); }); });
        // attendance
        el("sessionForm").addEventListener("submit", openSheet);
        el("allPresent").addEventListener("click", function () { setAll("PRESENT"); });
        el("allAbsent").addEventListener("click", function () { setAll("ABSENT"); });
        el("clearAtt").addEventListener("click", function () { setAll(null); });
        el("saveAtt").addEventListener("click", saveAttendance);
        el("sessBatchFilter").addEventListener("change", loadSessions);
        // masters
        el("addBatchBtn").addEventListener("click", function () { openBatchForm(null); });
        el("addCourseBtn").addEventListener("click", function () { openCourseForm(null); });
        document.querySelectorAll("[data-master]").forEach(function (b) { b.addEventListener("click", function () { openMasterForm(b.dataset.master, null); }); });
        // reports
        el("repRun").addEventListener("click", runReport);
        el("repType").addEventListener("change", runReport);
        el("repRange").addEventListener("change", function () { var custom = this.value === "custom"; el("repFrom").disabled = el("repTo").disabled = !custom && this.value !== ""; });
        el("repCsv").addEventListener("click", function () { downloadCsv("/automation/reports/" + el("repType").value + "/csv?" + reportQuery(), el("repType").value + ".csv"); });
        el("repXls").addEventListener("click", function () { downloadFile("/automation/reports/" + el("repType").value + "/xlsx?" + reportQuery(), el("repType").value + ".xlsx"); });
        el("repPdf").addEventListener("click", function () { downloadFile("/automation/reports/" + el("repType").value + "/pdf?" + reportQuery(), el("repType").value + ".pdf"); });
        el("repPrint").addEventListener("click", function () { printReportView("/automation/reports/" + el("repType").value + "/print?" + reportQuery()); });
        // Print / PDF / Excel toolbars on every record section.
        document.querySelectorAll(".au-export").forEach(function (box) {
            box.querySelectorAll("[data-fmt]").forEach(function (b) { b.addEventListener("click", function () { runExport(box.dataset.export, b.dataset.fmt); }); });
        });
        // communication
        ["cfBatch", "cfCourse", "cfStatus"].forEach(function (id) { el(id).addEventListener("change", function () { loadCommStudents(0); }); });
        el("cfQ").addEventListener("input", function () { debounce("cf", function () { loadCommStudents(0); }, 350); });
        el("commCheckAll").addEventListener("change", function () { var on = this.checked; el("commStudentsBody").querySelectorAll("[data-sel]").forEach(function (c) { c.checked = on; toggleSel(c.dataset.sel, on, c.dataset.name); }); });
        el("commSelectAll").addEventListener("click", selectAllFiltered);
        el("commClearSel").addEventListener("click", function () { state.commSelected = {}; renderSelCount(); loadCommStudents(state.commPage); });
        el("commSendSelected").addEventListener("click", function () { var ids = Object.keys(state.commSelected).map(Number); if (!ids.length) { toast("Select at least one student.", false); return; } openCompose({ studentIds: ids }, ids.length + " selected student(s)"); });
        el("commSendFiltered").addEventListener("click", function () { openCompose({ filter: commFilter() }, "all " + state.commTotal + " student(s) matching the current filters"); });
        // pending fees
        ["pfBatch", "pfCourse", "pfStatus"].forEach(function (id) { el(id).addEventListener("change", loadPendingFees); });
        el("pfQ").addEventListener("input", function () { debounce("pf", loadPendingFees, 350); });
        el("pfRefresh").addEventListener("click", loadPendingFees);
        // history
        ["chChannel", "chStatus", "chFrom", "chTo"].forEach(function (id) { el(id).addEventListener("change", function () { loadHistory(0); }); });
        el("chQ").addEventListener("input", function () { debounce("ch", function () { loadHistory(0); }, 350); });
        el("chRefresh").addEventListener("click", function () { loadHistory(state.chPage); });

        loadLookups().then(route);
    });
})(window);
