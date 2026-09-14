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
            mat.onclick = l.hasMaterial ? function () { downloadMaterial(l.id, l.materialOriginalName); } : null;

            setupVideo(l);
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
            return;
        }
        video.classList.remove("d-none"); none.classList.add("d-none");
        api("/student/lessons/" + l.id + "/stream-ticket").then(function (res) {
            if (!res.success) { showError(res.message); return; }
            var base = LSI_Auth.apiBase.replace(/\/api$/, "");
            video.src = base + res.data.url;
            video.load();
            if (l.watchedPercentage > 0 && l.watchedPercentage < 90) {
                video.addEventListener("loadedmetadata", function seek() {
                    video.removeEventListener("loadedmetadata", seek);
                    if (video.duration) video.currentTime = video.duration * (l.watchedPercentage / 100);
                });
            }
        });
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

    function downloadMaterial(lessonId, name) {
        var session = LSI_Auth.getSession();
        fetch(LSI_Auth.apiBase + "/student/lessons/" + lessonId + "/material", {
            headers: { Authorization: "Bearer " + (session ? session.token : "") }
        }).then(function (r) {
            if (!r.ok) throw new Error("Download failed");
            return r.blob();
        }).then(function (b) {
            var url = URL.createObjectURL(b);
            var a = document.createElement("a");
            a.href = url; a.download = name || "material.pdf"; document.body.appendChild(a); a.click(); a.remove();
            setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
        }).catch(function (e) { alert(e.message); });
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
        loadCourse();
    });
})();
