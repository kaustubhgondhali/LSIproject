/**
 * LORD SAI ACADEMY — COURSE LEARNING PAGE (js/learn.js)
 * learn.html?course=ID[&lesson=ID]
 */
(function () {
    "use strict";

    var api = LSI_Auth.api;
    var params = new URLSearchParams(window.location.search);
    var courseId = params.get("course");
    var currentLessonId = params.get("lesson");
    var course = null;
    var progressTimer = null;
    var lastReported = -1;
    var ticketIssuedAt = 0, ticketRenewals = 0, currentLesson = null;
    var TICKET_RENEW_AFTER_MS = 24 * 60 * 1000; // server ticket lives 30 min

    function esc(s) {
        return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }
    function el(id) { return document.getElementById(id); }
    function showError(msg) { var e = el("learnError"); e.innerText = msg; e.classList.remove("d-none"); }

    function loadCourse() {
        return api("/student/courses/" + courseId).then(function (res) {
            if (!res.success) { showError(res.message || "Could not load this course."); return null; }
            course = res.data;
            el("courseTitle").innerText = course.courseName;
            updateProgress(course.progressPercent, course.completedLessons, course.totalLessons);
            renderCurriculum();
            if (!currentLessonId) {
                var first = null;
                course.modules.some(function (m) {
                    return m.lessons.some(function (l) { if (!l.completed) { first = l; return true; } return false; });
                });
                if (!first && course.modules.length && course.modules[0].lessons.length) first = course.modules[0].lessons[0];
                if (first) currentLessonId = String(first.id);
            }
            if (currentLessonId) loadLesson(currentLessonId);
            return course;
        });
    }

    function updateProgress(percent, done, total) {
        el("courseProgressBar").style.width = percent + "%";
        el("courseProgressText").innerText = percent + "%";
        el("courseDone").innerText = done;
        el("courseTotal").innerText = total;
        // The exam application itself is gated by the backend; this banner only points to My Exams.
        var banner = el("learnCompleteBanner");
        if (banner) banner.classList.toggle("d-none", !(total > 0 && done >= total));
    }

    function renderCurriculum() {
        var html = course.modules.map(function (m, mi) {
            return '<div class="mb-1">' +
                '<div class="module-head"><span>Module ' + (mi + 1) + ': ' + esc(m.moduleName) + '</span><span>' + m.completedCount + '/' + m.lessonCount + '</span></div>' +
                m.lessons.map(function (l) {
                    var active = String(l.id) === String(currentLessonId);
                    return '<button type="button" class="lesson-item' + (active ? " active" : "") + '" data-lesson="' + l.id + '">' +
                        '<i class="' + (l.completed ? "fas fa-check-circle text-success" : (l.hasVideo ? "far fa-play-circle" : "far fa-file-alt")) + '"></i>' +
                        '<span class="flex-grow-1">' + esc(l.lessonTitle) + '</span>' +
                        (l.hasMaterial ? '<i class="fas fa-file-pdf text-danger small"></i>' : '') +
                        '</button>';
                }).join("") + '</div>';
        }).join("");
        el("curriculum").innerHTML = html || '<div class="text-muted small p-3">No lessons have been published yet.</div>';
        el("curriculum").querySelectorAll("[data-lesson]").forEach(function (b) {
            b.addEventListener("click", function () { loadLesson(b.getAttribute("data-lesson")); });
        });
    }

    function loadLesson(lessonId) {
        currentLessonId = String(lessonId);
        history.replaceState(null, "", "learn.html?course=" + courseId + "&lesson=" + lessonId);
        renderCurriculum();
        api("/student/lessons/" + lessonId).then(function (res) {
            if (!res.success) { showError(res.message); return; }
            var l = res.data;
            currentLesson = l;
            if (window.LSI_Protect) LSI_Protect.setContext(l.courseId, l.id);
            closeMaterial();
            el("lessonModule").innerText = l.moduleName;
            el("lessonTitle").innerText = l.lessonTitle;
            el("lessonDescription").innerText = l.description || "";
            el("lessonCompletedBadge").classList.toggle("d-none", !l.completed);
            el("completeBtn").innerHTML = l.completed ? '<i class="fas fa-undo"></i> Mark as incomplete' : '<i class="fas fa-check"></i> Mark as complete';
            el("completeBtn").dataset.completed = l.completed ? "1" : "0";

            var prev = el("prevLessonBtn"), next = el("nextLessonBtn");
            prev.disabled = !l.previousLessonId; next.disabled = !l.nextLessonId;
            prev.onclick = l.previousLessonId ? function () { loadLesson(l.previousLessonId); } : null;
            next.onclick = l.nextLessonId ? function () { loadLesson(l.nextLessonId); } : null;
            prev.title = l.previousLessonTitle || ""; next.title = l.nextLessonTitle || "";

            var mat = el("materialBtn");
            mat.classList.toggle("d-none", !l.hasMaterial);
            mat.onclick = l.hasMaterial ? function () { openMaterial(l.id, l.materialOriginalName); } : null;

            setupVideo(l);
            if (l.hasMaterial && params.get("material") === "1") {
                params.delete("material");
                openMaterial(l.id, l.materialOriginalName);
            }
        });
    }

    function setupVideo(l) {
        var video = el("lessonVideo");
        var none = el("noVideo");
        video.pause();
        video.removeAttribute("src");
        video.load();
        lastReported = -1;
        if (!l.hasVideo) {
            video.classList.add("d-none"); none.classList.remove("d-none");
            el("fsBtn").classList.add("d-none");
            if (inFullscreen()) toggleFullscreen();
            return;
        }
        video.classList.remove("d-none"); none.classList.add("d-none");
        el("fsBtn").classList.remove("d-none");
        ticketRenewals = 0;
        loadTicket(l.id, function () {
            if (l.watchedPercentage > 0 && l.watchedPercentage < 90) {
                video.addEventListener("loadedmetadata", function seek() {
                    video.removeEventListener("loadedmetadata", seek);
                    if (video.duration) video.currentTime = video.duration * (l.watchedPercentage / 100);
                });
            }
        });
        // Tickets are short-lived on purpose (a copied URL goes stale); renew before they expire
        // and, if one does expire mid-stream, recover at the same position.
        video.onplay = function () {
            if (ticketIssuedAt && Date.now() - ticketIssuedAt > TICKET_RENEW_AFTER_MS) renewTicket(l.id, "refresh");
        };
        video.onerror = function () {
            if (!video.error || !video.src) return;
            if (ticketRenewals < 3) renewTicket(l.id, "error " + video.error.code);
            else showError("The video could not be loaded. Please reload the lesson.");
        };
        video.ontimeupdate = function () {
            if (!video.duration) return;
            var pct = Math.floor(video.currentTime / video.duration * 100);
            if (pct >= lastReported + 10 || (pct >= 90 && lastReported < 90)) {
                lastReported = pct;
                reportProgress(l.id, pct, null);
            }
        };
        video.onended = function () { reportProgress(l.id, 100, true); };
    }

    function reportProgress(lessonId, pct, completed) {
        return api("/student/lessons/" + lessonId + "/progress", {
            method: "POST", body: { watchedPercentage: pct, completed: completed }
        }).then(function (res) {
            if (!res.success) return;
            var p = res.data;
            updateProgress(p.courseProgressPercent, p.courseCompletedLessons, p.courseTotalLessons);
            course.modules.forEach(function (m) {
                var before = m.completedCount;
                m.lessons.forEach(function (x) { if (String(x.id) === String(lessonId)) x.completed = p.completed; });
                m.completedCount = m.lessons.filter(function (x) { return x.completed; }).length;
                if (before !== m.completedCount) renderCurriculum();
            });
            if (String(lessonId) === String(currentLessonId)) {
                el("lessonCompletedBadge").classList.toggle("d-none", !p.completed);
                el("completeBtn").innerHTML = p.completed ? '<i class="fas fa-undo"></i> Mark as incomplete' : '<i class="fas fa-check"></i> Mark as complete';
                el("completeBtn").dataset.completed = p.completed ? "1" : "0";
            }
        });
    }

    // ---- stream tickets ----------------------------------------------------------------------

    function loadTicket(lessonId, onReady) {
        var video = el("lessonVideo");
        return api("/student/lessons/" + lessonId + "/stream-ticket").then(function (res) {
            if (!res.success) { showError(res.message); return false; }
            var base = LSI_Auth.apiBase.replace(/\/api$/, "");
            ticketIssuedAt = Date.now();
            video.src = base + res.data.url;
            video.load();
            if (onReady) onReady();
            return true;
        });
    }

    function renewTicket(lessonId, why) {
        var video = el("lessonVideo");
        if (String(lessonId) !== String(currentLessonId)) return;
        ticketRenewals++;
        var at = video.currentTime || 0, wasPlaying = !video.paused && !video.ended;
        loadTicket(lessonId, function () {
            video.addEventListener("loadedmetadata", function resume() {
                video.removeEventListener("loadedmetadata", resume);
                if (at > 0 && video.duration) video.currentTime = Math.min(at, video.duration - 0.25);
                if (wasPlaying) video.play().catch(function () { /* autoplay policy: user presses play */ });
            });
        });
    }

    // ---- fullscreen (through the wrapper so the watermark stays on screen) --------------------

    function playerShell() { return el("playerShell"); }
    function inFullscreen() {
        return !!(document.fullscreenElement || document.webkitFullscreenElement) || playerShell().classList.contains("lsi-pseudo-fs");
    }
    function toggleFullscreen() {
        var shell = playerShell();
        if (inFullscreen()) {
            if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen();
            else if (document.webkitFullscreenElement && document.webkitExitFullscreen) document.webkitExitFullscreen();
            else exitPseudoFullscreen();
            return;
        }
        var req = shell.requestFullscreen || shell.webkitRequestFullscreen;
        if (req) {
            var p = req.call(shell);
            if (p && p.catch) p.catch(enterPseudoFullscreen);
        } else {
            // iPhone Safari has no element fullscreen: fill the viewport instead, watermark included.
            enterPseudoFullscreen();
        }
    }
    function enterPseudoFullscreen() {
        playerShell().classList.add("lsi-pseudo-fs"); document.body.classList.add("lsi-pseudo-fs-active"); updateFsIcon();
    }
    function exitPseudoFullscreen() {
        playerShell().classList.remove("lsi-pseudo-fs"); document.body.classList.remove("lsi-pseudo-fs-active"); updateFsIcon();
    }
    function updateFsIcon() {
        var i = el("fsBtn").querySelector("i");
        if (i) i.className = inFullscreen() ? "fas fa-compress" : "fas fa-expand";
    }
    function onFullscreenChange() {
        var fsEl = document.fullscreenElement || document.webkitFullscreenElement;
        var video = el("lessonVideo");
        // Firefox still shows the native button; it fullscreens the bare <video>, which would lose
        // the watermark. Swap to the wrapper immediately.
        if (fsEl === video) {
            var exit = document.exitFullscreen || document.webkitExitFullscreen;
            var p = exit.call(document);
            (p && p.then ? p : Promise.resolve()).then(function () {
                var req = playerShell().requestFullscreen || playerShell().webkitRequestFullscreen;
                if (req) { var r = req.call(playerShell()); if (r && r.catch) r.catch(function () { /* needs a gesture */ }); }
            });
        }
        updateFsIcon();
    }

    // ---- handout viewer (PDF rendered in the portal with the student watermark) ---------------

    var materialTask = null;

    function closeMaterial() {
        var v = el("materialViewer");
        if (!v.classList.contains("d-none")) v.classList.add("d-none");
        el("materialPages").innerHTML = "";
        if (materialTask && materialTask.destroy) { try { materialTask.destroy(); } catch (e) { /* ignore */ } }
        materialTask = null;
    }

    function openMaterial(lessonId, name) {
        var viewer = el("materialViewer"), pages = el("materialPages");
        el("materialName").innerText = name || "Handout";
        viewer.classList.remove("d-none");
        pages.innerHTML = '<div class="lsi-pdf-status"><span class="spinner-border spinner-border-sm me-2"></span>Loading handout…</div>';
        viewer.scrollIntoView({ behavior: "smooth", block: "start" });
        if (!window.pdfjsLib) {
            pages.innerHTML = '<div class="lsi-pdf-status">The handout viewer could not be loaded. Please check your connection and reload the page.</div>';
            return;
        }
        pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";
        var session = LSI_Auth.getSession();
        fetch(LSI_Auth.apiBase + "/student/lessons/" + lessonId + "/material", {
            headers: { Authorization: "Bearer " + (session ? session.token : "") }, cache: "no-store"
        }).then(function (r) {
            if (r.status === 401 || r.status === 403) throw new Error("You do not have access to this handout.");
            if (!r.ok) throw new Error("The handout could not be loaded (" + r.status + ").");
            return r.arrayBuffer();
        }).then(function (buf) {
            materialTask = pdfjsLib.getDocument({ data: buf, isEvalSupported: false });
            return materialTask.promise;
        }).then(function (pdf) {
            pages.innerHTML = "";
            var width = Math.max(320, pages.clientWidth - 28);
            var dpr = Math.min(window.devicePixelRatio || 1, 2);
            var idle = window.requestIdleCallback ? function (fn) { window.requestIdleCallback(fn); } : function (fn) { window.setTimeout(fn, 0); };
            var n = 1;
            function next() {
                if (n > pdf.numPages || viewer.classList.contains("d-none")) return;
                pdf.getPage(n).then(function (page) {
                    var scale = width / page.getViewport({ scale: 1 }).width;
                    var vp = page.getViewport({ scale: scale * dpr });
                    var canvas = document.createElement("canvas");
                    canvas.width = Math.floor(vp.width); canvas.height = Math.floor(vp.height);
                    canvas.setAttribute("aria-label", "Page " + n + " of " + pdf.numPages);
                    pages.appendChild(canvas);
                    return page.render({ canvasContext: canvas.getContext("2d"), viewport: vp }).promise.then(function () {
                        if (window.LSI_Protect) LSI_Protect.stampCanvas(canvas);
                        n++;
                        idle(next);
                    });
                }).catch(function () {
                    pages.insertAdjacentHTML("beforeend", '<div class="lsi-pdf-status">Page ' + n + ' could not be rendered.</div>');
                    n++; next();
                });
            }
            next();
        }).catch(function (e) {
            pages.innerHTML = '<div class="lsi-pdf-status">' + esc(e.message || "The handout could not be loaded.") + '</div>';
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (!LSI_Auth.isAuthenticated()) return;
        if (!courseId) { showError("No course selected."); return; }
        LSI_Auth.refreshProfile().then(function (p) {
            if (p) el("navStudentName").innerText = p.name;
        });
        el("completeBtn").addEventListener("click", function () {
            var done = el("completeBtn").dataset.completed === "1";
            reportProgress(currentLessonId, null, !done);
        });
        el("fsBtn").addEventListener("click", toggleFullscreen);
        el("materialCloseBtn").addEventListener("click", closeMaterial);
        document.addEventListener("fullscreenchange", onFullscreenChange);
        document.addEventListener("webkitfullscreenchange", onFullscreenChange);
        document.addEventListener("keydown", function (e) { if (e.key === "Escape" && playerShell().classList.contains("lsi-pseudo-fs")) exitPseudoFullscreen(); });
        var video = el("lessonVideo");
        video.disablePictureInPicture = true;
        video.addEventListener("dblclick", function (e) { e.preventDefault(); toggleFullscreen(); });
        video.addEventListener("contextmenu", function (e) { e.preventDefault(); });
        video.addEventListener("dragstart", function (e) { e.preventDefault(); });
        if (window.LSI_Protect) LSI_Protect.setContext(courseId, currentLessonId);
        loadCourse();
    });
})();
