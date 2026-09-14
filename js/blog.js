/**
 * LORD SAI - Public Blog Dynamic Connector (js/blog.js)
 * Connects the existing blog.html page to backend API (/api/public/blogs and /api/public/blog-categories).
 * Respects active website mode (ACADEMY for Share Market vs MUTUAL_FUND for Mutual Fund).
 * Gracefully preserves existing static content if API is unreachable.
 */
(function (window, document) {
    "use strict";

    var apiBase = (typeof LSI_Auth !== "undefined" && LSI_Auth.apiBase)
        ? LSI_Auth.apiBase
        : ((typeof API_BASE !== "undefined") ? API_BASE : "http://localhost:8080/api");

    var state = {
        site: "ACADEMY",
        categoryId: null,
        search: "",
        staticFallbackHtml: null
    };

    function esc(s) {
        return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }

    function formatDate(iso) {
        if (!iso) return "";
        var d = new Date(iso);
        return isNaN(d) ? esc(iso) : d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
    }

    function blogImgSrc(path) {
        if (!path) return "img/service-1.jpg";
        if (path.indexOf("http://") === 0 || path.indexOf("https://") === 0 || path.indexOf("data:") === 0) return path;
        if (path.indexOf("images/") === 0) return apiBase + "/public/" + path;
        return path;
    }

    function getSiteCode() {
        if (typeof LSI_Mode !== "undefined" && LSI_Mode.getMode) {
            return LSI_Mode.getMode() === "mutual-fund" ? "MUTUAL_FUND" : "ACADEMY";
        }
        if (document.body && document.body.classList.contains("mode-mutual-fund")) {
            return "MUTUAL_FUND";
        }
        return "ACADEMY";
    }

    function initReaderModal() {
        if (document.getElementById("publicBlogModal")) return;
        var modalHtml = '<div class="modal fade" id="publicBlogModal" tabindex="-1" aria-hidden="true">' +
            '<div class="modal-dialog modal-dialog-centered modal-lg modal-dialog-scrollable">' +
            '<div class="modal-content border-0 shadow-lg" style="border-radius: 16px; overflow: hidden;">' +
            '<div class="modal-header text-white" style="background: linear-gradient(135deg, var(--lsi-navy, #0A1128), var(--lsi-blue, #1C3F94)); border-bottom: 0;">' +
            '<div>' +
            '<span class="badge bg-light text-dark mb-1" id="publicBlogModalCat"></span>' +
            '<h5 class="modal-title fw-bold text-white mb-0" id="publicBlogModalTitle"></h5>' +
            '</div>' +
            '<button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>' +
            '</div>' +
            '<div class="modal-body p-4" id="publicBlogModalBody">' +
            '<div id="publicBlogModalImgWrap" class="mb-4 text-center d-none">' +
            '<img id="publicBlogModalImg" class="img-fluid rounded-3 shadow-sm" style="max-height: 380px; width: 100%; object-fit: cover;" alt="">' +
            '</div>' +
            '<div class="d-flex flex-wrap align-items-center gap-3 mb-3 text-muted small border-bottom pb-2">' +
            '<span id="publicBlogModalAuthor"><i class="fas fa-user-edit text-primary me-1"></i> <span></span></span>' +
            '<span id="publicBlogModalDate"><i class="far fa-clock text-info me-1"></i> <span></span></span>' +
            '</div>' +
            '<div class="blog-article-content" id="publicBlogModalContent" style="font-size: 1.05rem; line-height: 1.8; color: #2d3748;"></div>' +
            '</div>' +
            '<div class="modal-footer border-0 bg-light py-2 px-4 d-flex justify-content-between">' +
            '<small class="text-muted">Lord Sai Educational Articles</small>' +
            '<button type="button" class="btn btn-secondary btn-sm rounded-pill px-3" data-bs-dismiss="modal">Close</button>' +
            '</div>' +
            '</div>' +
            '</div>' +
            '</div>';
        var div = document.createElement("div");
        div.innerHTML = modalHtml;
        document.body.appendChild(div.firstElementChild);
    }

    function openBlogDetail(slugOrId) {
        initReaderModal();
        var modalEl = document.getElementById("publicBlogModal");
        var modal = (typeof bootstrap !== "undefined" && bootstrap.Modal)
            ? (bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl))
            : null;

        document.getElementById("publicBlogModalTitle").innerText = "Loading article...";
        document.getElementById("publicBlogModalContent").innerHTML = '<div class="text-center py-5"><div class="spinner-border text-primary" role="status"></div></div>';
        document.getElementById("publicBlogModalImgWrap").classList.add("d-none");
        if (modal) modal.show();

        fetch(apiBase + "/public/blogs/" + encodeURIComponent(slugOrId) + "?site=" + state.site)
            .then(function (res) { return res.json(); })
            .then(function (res) {
                if (!res.success || !res.data) {
                    document.getElementById("publicBlogModalContent").innerHTML = '<div class="alert alert-warning">Article not found.</div>';
                    return;
                }
                var b = res.data;
                document.getElementById("publicBlogModalTitle").innerText = b.title;
                document.getElementById("publicBlogModalCat").innerText = b.categoryName || "Article";
                var authorSpan = document.querySelector("#publicBlogModalAuthor span");
                if (authorSpan) authorSpan.innerText = b.author || "Lord Sai Team";
                var dateSpan = document.querySelector("#publicBlogModalDate span");
                if (dateSpan) dateSpan.innerText = formatDate(b.publishedAt);

                if (b.featuredImagePath) {
                    var img = document.getElementById("publicBlogModalImg");
                    img.src = blogImgSrc(b.featuredImagePath);
                    document.getElementById("publicBlogModalImgWrap").classList.remove("d-none");
                } else {
                    document.getElementById("publicBlogModalImgWrap").classList.add("d-none");
                }

                document.getElementById("publicBlogModalContent").innerHTML = b.content;
            })
            .catch(function () {
                document.getElementById("publicBlogModalContent").innerHTML = '<div class="alert alert-danger">Unable to load article. Please try again.</div>';
            });
    }

    function loadPublicCategories() {
        var catContainer = document.getElementById("publicBlogCategories");
        if (!catContainer) return;

        fetch(apiBase + "/public/blog-categories?site=" + state.site)
            .then(function (res) { return res.json(); })
            .then(function (res) {
                if (!res.success || !res.data || res.data.length === 0) return;
                var html = '<button class="btn btn-sm ' + (!state.categoryId ? 'btn-primary' : 'btn-outline-secondary') + ' rounded-pill px-3" data-cat-id="">All Articles</button>';
                res.data.forEach(function (c) {
                    var active = String(state.categoryId) === String(c.id);
                    html += '<button class="btn btn-sm ' + (active ? 'btn-primary' : 'btn-outline-secondary') + ' rounded-pill px-3" data-cat-id="' + c.id + '">' + esc(c.name) + '</button>';
                });
                catContainer.innerHTML = html;

                catContainer.querySelectorAll("[data-cat-id]").forEach(function (btn) {
                    btn.addEventListener("click", function () {
                        catContainer.querySelectorAll("button").forEach(function (b) {
                            b.className = "btn btn-sm btn-outline-secondary rounded-pill px-3";
                        });
                        btn.className = "btn btn-sm btn-primary rounded-pill px-3";
                        state.categoryId = btn.dataset.catId || null;
                        loadPublicBlogs();
                    });
                });
            })
            .catch(function (e) {
                console.warn("Could not load categories from backend:", e);
            });
    }

    function loadPublicBlogs() {
        var wrapper = document.getElementById("publicBlogArticlesWrapper");
        if (!wrapper) return;

        if (!state.staticFallbackHtml) {
            state.staticFallbackHtml = wrapper.innerHTML;
        }

        var qs = "?site=" + state.site;
        if (state.categoryId) qs += "&categoryId=" + encodeURIComponent(state.categoryId);
        if (state.search) qs += "&search=" + encodeURIComponent(state.search);

        fetch(apiBase + "/public/blogs" + qs)
            .then(function (res) { return res.json(); })
            .then(function (res) {
                if (!res.success || !res.data || !res.data.content || res.data.content.length === 0) {
                    if (!state.categoryId && !state.search) {
                        // If no custom filters applied and no dynamic blogs returned, preserve static fallback
                        return;
                    }
                    wrapper.innerHTML = '<div class="text-center py-5 text-muted">' +
                        '<i class="fas fa-search fa-2x mb-3 text-secondary"></i>' +
                        '<h5 class="fw-bold text-dark">No Articles Found</h5>' +
                        '<p class="small">No articles matched your search or category filter. Try clearing filters.</p>' +
                        '<button class="btn btn-sm btn-outline-primary rounded-pill px-3" id="clearBlogFiltersBtn">Show All Articles</button>' +
                        '</div>';
                    var clr = document.getElementById("clearBlogFiltersBtn");
                    if (clr) clr.addEventListener("click", function () {
                        state.categoryId = null;
                        state.search = "";
                        var searchInp = document.getElementById("publicBlogSearchInput");
                        if (searchInp) searchInp.value = "";
                        loadPublicCategories();
                        loadPublicBlogs();
                    });
                    return;
                }

                var blogs = res.data.content;
                var html = "";

                // If first page with no search/filter, highlight first article as Featured Guide
                var startIdx = 0;
                if (!state.search && !state.categoryId && blogs.length > 0) {
                    var f = blogs[0];
                    startIdx = 1;
                    html += '<div class="card p-4 p-lg-5 mb-5 shadow-sm border-0 bg-white" style="border-radius: 16px;">' +
                        '<div class="row g-4 align-items-center">' +
                        '<div class="col-lg-6">' +
                        '<img src="' + esc(blogImgSrc(f.featuredImagePath)) + '" alt="' + esc(f.title) + '" class="img-fluid rounded-3" style="width: 100%; max-height: 320px; object-fit: cover;">' +
                        '</div>' +
                        '<div class="col-lg-6">' +
                        '<span class="badge bg-primary text-uppercase mb-2">' + esc(f.categoryName || "Featured Guide") + '</span>' +
                        '<h2 class="fw-bold text-dark mb-3">' + esc(f.title) + '</h2>' +
                        '<p class="text-muted mb-3" style="line-height: 1.8;">' + esc(f.shortDescription) + '</p>' +
                        '<div class="d-flex align-items-center gap-3 mb-4 text-muted small">' +
                        '<span><i class="fas fa-user-edit text-primary me-1"></i> ' + esc(f.author || "Lord Sai Team") + '</span>' +
                        '<span><i class="far fa-clock text-info me-1"></i> ' + formatDate(f.publishedAt) + '</span>' +
                        '</div>' +
                        '<button type="button" class="btn btn-outline-primary rounded-pill px-4" data-read-blog="' + esc(f.slug || f.id) + '">' +
                        'Read Full Guide' +
                        '</button>' +
                        '</div>' +
                        '</div>' +
                        '</div>';
                }

                // Render remaining articles in grid
                var gridBlogs = blogs.slice(startIdx);
                if (gridBlogs.length > 0) {
                    html += '<div class="row g-4">';
                    gridBlogs.forEach(function (b) {
                        html += '<div class="col-lg-4 col-md-6">' +
                            '<div class="card h-100 shadow-sm border-0 bg-white overflow-hidden" style="border-radius: 12px;">' +
                            '<img src="' + esc(blogImgSrc(b.featuredImagePath)) + '" alt="' + esc(b.title) + '" class="img-fluid" style="height: 200px; object-fit: cover;">' +
                            '<div class="p-4 d-flex flex-column flex-grow-1">' +
                            '<div class="d-flex justify-content-between mb-2">' +
                            '<span class="badge bg-info text-dark">' + esc(b.categoryName || "Education") + '</span>' +
                            '<small class="text-muted"><i class="far fa-clock me-1"></i> ' + formatDate(b.publishedAt) + '</small>' +
                            '</div>' +
                            '<h5 class="fw-bold text-dark mb-2">' + esc(b.title) + '</h5>' +
                            '<p class="text-muted small mb-4" style="line-height: 1.6;">' + esc(b.shortDescription) + '</p>' +
                            '<button type="button" class="btn btn-sm btn-outline-primary rounded-pill mt-auto align-self-start" data-read-blog="' + esc(b.slug || b.id) + '">Read Full Guide</button>' +
                            '</div>' +
                            '</div>' +
                            '</div>';
                    });
                    html += '</div>';
                }

                wrapper.innerHTML = html;

                wrapper.querySelectorAll("[data-read-blog]").forEach(function (btn) {
                    btn.addEventListener("click", function () {
                        openBlogDetail(btn.dataset.readBlog);
                    });
                });
            })
            .catch(function (err) {
                console.warn("Error fetching blogs, preserving static content:", err);
            });
    }

    function init() {
        state.site = getSiteCode();
        initReaderModal();
        loadPublicCategories();
        loadPublicBlogs();

        var searchInp = document.getElementById("publicBlogSearchInput");
        var searchBtn = document.getElementById("publicBlogSearchBtn");
        var debounceTimer;

        if (searchInp) {
            searchInp.addEventListener("input", function () {
                state.search = this.value.trim();
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(loadPublicBlogs, 400);
            });
            searchInp.addEventListener("keypress", function (e) {
                if (e.key === "Enter") {
                    e.preventDefault();
                    state.search = this.value.trim();
                    loadPublicBlogs();
                }
            });
        }
        if (searchBtn) {
            searchBtn.addEventListener("click", function () {
                if (searchInp) state.search = searchInp.value.trim();
                loadPublicBlogs();
            });
        }

        // Check if mode switched dynamically
        window.addEventListener("storage", function (e) {
            if (e.key === "lsi_site_mode") {
                var newSite = getSiteCode();
                if (newSite !== state.site) {
                    state.site = newSite;
                    state.categoryId = null;
                    state.search = "";
                    loadPublicCategories();
                    loadPublicBlogs();
                }
            }
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }

    window.LSI_Blog = {
        reload: function () {
            state.site = getSiteCode();
            loadPublicCategories();
            loadPublicBlogs();
        },
        open: openBlogDetail
    };

})(window, document);

