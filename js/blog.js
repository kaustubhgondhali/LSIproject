/**
 * LORD SAI - Blog search & category filter (js/blog.js)
 * Filters the articles already on blog.html: the category buttons (#publicBlogCategories,
 * data-blog-filter) and the search box (#publicBlogSearchInput) show only the matching
 * articles ([data-blog-topics] inside #publicBlogArticlesWrapper). Everything runs in the
 * browser; there is no server.
 */
(function (window, document) {
    "use strict";

    var state = { topic: "all", search: "" };
    var items = [];

    function norm(s) { return String(s || "").replace(/\s+/g, " ").trim().toLowerCase(); }

    function emptyState(wrapper) {
        var box = document.getElementById("publicBlogEmpty");
        if (box) return box;
        box = document.createElement("div");
        box.id = "publicBlogEmpty";
        box.className = "text-center py-5 text-muted d-none";
        box.innerHTML = '<i class="fas fa-search fa-2x mb-3 text-secondary"></i>' +
            '<h5 class="fw-bold text-dark">No Articles Found</h5>' +
            '<p class="small">No articles matched your search or category filter. Try clearing filters.</p>' +
            '<button type="button" class="btn btn-sm btn-outline-primary rounded-pill px-3" id="clearBlogFiltersBtn">Show All Articles</button>';
        wrapper.appendChild(box);
        box.querySelector("#clearBlogFiltersBtn").addEventListener("click", function () {
            var searchInp = document.getElementById("publicBlogSearchInput");
            if (searchInp) searchInp.value = "";
            state.search = "";
            selectTopic("all");
        });
        return box;
    }

    function apply() {
        var wrapper = document.getElementById("publicBlogArticlesWrapper");
        if (!wrapper) return;
        var q = norm(state.search), visible = 0;
        items.forEach(function (it) {
            var topics = (it.el.getAttribute("data-blog-topics") || "").split(/\s+/);
            var topicOk = state.topic === "all" || topics.indexOf(state.topic) !== -1;
            // Match the English text and the text currently shown (Hindi / Marathi when translated).
            var searchOk = !q || it.english.indexOf(q) !== -1 || norm(it.el.textContent).indexOf(q) !== -1;
            var show = topicOk && searchOk;
            it.el.classList.toggle("d-none", !show);
            if (show) visible++;
        });
        emptyState(wrapper).classList.toggle("d-none", visible > 0);
    }

    function selectTopic(topic) {
        state.topic = topic || "all";
        document.querySelectorAll("#publicBlogCategories [data-blog-filter]").forEach(function (btn) {
            var active = btn.getAttribute("data-blog-filter") === state.topic;
            btn.className = "btn btn-sm " + (active ? "btn-primary" : "btn-outline-secondary") + " rounded-pill px-3";
            btn.setAttribute("aria-pressed", active ? "true" : "false");
        });
        apply();
    }

    function init() {
        var wrapper = document.getElementById("publicBlogArticlesWrapper");
        if (!wrapper) return;
        items = Array.prototype.map.call(wrapper.querySelectorAll("[data-blog-topics]"), function (el) {
            return { el: el, english: norm(el.textContent) };
        });

        document.querySelectorAll("#publicBlogCategories [data-blog-filter]").forEach(function (btn) {
            btn.setAttribute("type", "button");
            btn.addEventListener("click", function () { selectTopic(btn.getAttribute("data-blog-filter")); });
        });

        var searchInp = document.getElementById("publicBlogSearchInput");
        var searchBtn = document.getElementById("publicBlogSearchBtn");
        var debounceTimer;
        if (searchInp) {
            searchInp.addEventListener("input", function () {
                state.search = this.value;
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(apply, 250);
            });
            searchInp.addEventListener("keydown", function (e) {
                if (e.key === "Enter") {
                    e.preventDefault();
                    state.search = this.value;
                    apply();
                }
            });
        }
        if (searchBtn) {
            searchBtn.addEventListener("click", function () {
                if (searchInp) state.search = searchInp.value;
                apply();
            });
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})(window, document);
