/**
 * LORD SAI ACADEMY — API CONFIGURATION (js/api-config.js)
 * Single place to point the static frontend at the Spring Boot backend.
 * Load this before js/auth.js on every page.
 */
(function (window) {
    "use strict";

    var explicit = window.LSI_API_BASE_OVERRIDE;
    var host = window.location.hostname;
    var isLocal = host === "localhost" || host === "127.0.0.1" || host === "" || window.location.protocol === "file:";

    window.LSI_CONFIG = {
        // Local development: backend runs on :8080. In production, serve the API under /api on the
        // same domain (reverse proxy) or set window.LSI_API_BASE_OVERRIDE before this script.
        API_BASE: explicit || (isLocal ? "http://localhost:8080/api" : "/api"),
        RAZORPAY_CHECKOUT_JS: "https://checkout.razorpay.com/v1/checkout.js",
        BRAND_NAME: "Lord Sai Investment & Share Market Academy",
        BRAND_LOGO: "img/logo.png",
        BRAND_COLOR: "#0F9F90"
    };
})(window);
