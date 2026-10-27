/**
 * Loads active Mutual Fund homepage slider images from the public API. Existing
 * local slides in home.html remain available when the API cannot be reached.
 */
(function (window, document) {
    "use strict";

    function getImageUrl(imagePath) {
        if (typeof imagePath !== "string" || !imagePath.trim()) {
            throw new Error("The homepage slider returned an empty image path.");
        }

        if (imagePath.indexOf("images/") === 0) {
            var filename = imagePath.slice("images/".length);
            if (!filename || filename.indexOf("/") !== -1) {
                throw new Error("The homepage slider returned an invalid uploaded image path.");
            }
            var apiBase = (window.LSI_CONFIG && window.LSI_CONFIG.API_BASE) || "/api";
            return apiBase.replace(/\/+$/, "") + "/public/images/" + encodeURIComponent(filename);
        }

        if (imagePath.indexOf("img/") === 0) {
            return imagePath;
        }

        throw new Error("The homepage slider returned an unsupported image path.");
    }

    function renderSlides(slides) {
        var track = document.getElementById("mfCarouselTrack");
        if (!track) return;

        var fragment = document.createDocumentFragment();
        slides.forEach(function (slide, index) {
            var imageUrl = getImageUrl(slide.imagePath);
            var altText = slide.altText || slide.title || "Mutual Fund highlight";
            var container = document.createElement("div");
            container.className = "mf-carousel-slide";
            container.setAttribute("data-slide-index", String(index));

            var background = document.createElement("div");
            background.className = "mf-slide-bg";
            background.style.backgroundImage = "url(" + JSON.stringify(imageUrl) + ")";
            background.setAttribute("aria-hidden", "true");

            var image = document.createElement("div");
            image.className = "mf-slide-image";
            image.style.backgroundImage = "url(" + JSON.stringify(imageUrl) + ")";
            image.setAttribute("role", "img");
            image.setAttribute("aria-label", altText);

            container.appendChild(background);
            container.appendChild(image);
            fragment.appendChild(container);
        });

        track.replaceChildren(fragment);
    }

    var apiBase = (window.LSI_CONFIG && window.LSI_CONFIG.API_BASE) || "/api";
    window.mfSliderReady = window.fetch(apiBase.replace(/\/+$/, "") + "/public/mutual-fund/slider-images")
        .then(function (response) {
            if (!response.ok) {
                throw new Error("Slider request failed with HTTP " + response.status + ".");
            }
            return response.json();
        })
        .then(function (result) {
            if (!result || result.success !== true || !Array.isArray(result.data)) {
                throw new Error("The homepage slider API returned an invalid response.");
            }
            renderSlides(result.data);
        })
        .catch(function (error) {
            console.warn("Could not load Mutual Fund homepage slider images; using local slides.", error);
        });
})(window, document);
