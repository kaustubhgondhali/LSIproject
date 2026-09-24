/**
 * ============================================================================
 * LORD SAI ACADEMY — AUTHENTICATION CLIENT (js/auth.js)
 * ============================================================================
 * Thin client over the Spring Boot authentication API. The browser only holds the
 * access token; every decision (credentials, account status, single-device rule,
 * role authorization) is made by the backend.
 *
 * Public interface kept identical to the previous version so existing pages work:
 *   LSI_Auth.login(identifier, password, rememberMe, portal) -> Promise<{success, user, redirectUrl, message}>
 *       portal = "STUDENT" (default, Student Admin) | "ADMIN" (Admin Login); the server enforces the role match
 *   LSI_Auth.logout(), LSI_Auth.getSession(), LSI_Auth.getCurrentUser(), LSI_Auth.getUserRole(),
 *   LSI_Auth.isAuthenticated(), LSI_Auth.requireAuth(role, loginUrl), LSI_Auth.updateNavUI()
 * New:
 *   LSI_Auth.api(path, options) -> fetch with bearer token and JSON handling
 *   LSI_Auth.refreshProfile()   -> re-validates the token against /auth/me
 */
(function (window, $) {
    "use strict";

    var STORAGE_KEY_SESSION = "lsi_auth_user_session";
    var STORAGE_KEY_REDIRECT = "lsi_auth_redirect_target";
    var STORAGE_KEY_DEVICE = "lsi_device_binding";
    var API_BASE = (window.LSI_CONFIG && window.LSI_CONFIG.API_BASE) || "/api";

    function storage() {
        return localStorage.getItem(STORAGE_KEY_SESSION) ? localStorage : sessionStorage;
    }

    function readSession() {
        try {
            var raw = localStorage.getItem(STORAGE_KEY_SESSION) || sessionStorage.getItem(STORAGE_KEY_SESSION);
            return raw ? JSON.parse(raw) : null;
        } catch (e) {
            return null;
        }
    }

    function writeSession(session, rememberMe) {
        localStorage.removeItem(STORAGE_KEY_SESSION);
        sessionStorage.removeItem(STORAGE_KEY_SESSION);
        (rememberMe ? localStorage : sessionStorage).setItem(STORAGE_KEY_SESSION, JSON.stringify(session));
    }

    function clearSession() {
        localStorage.removeItem(STORAGE_KEY_SESSION);
        sessionStorage.removeItem(STORAGE_KEY_SESSION);
    }

    function readDeviceBinding() {
        try {
            var raw = localStorage.getItem(STORAGE_KEY_DEVICE);
            return raw ? JSON.parse(raw) : null;
        } catch (e) {
            return null;
        }
    }

    function saveDeviceBinding(deviceId, deviceToken) {
        if (!deviceId || !deviceToken) return;
        try {
            localStorage.setItem(STORAGE_KEY_DEVICE, JSON.stringify({
                deviceId: deviceId,
                deviceToken: deviceToken
            }));
        } catch (e) {}
    }

    function clearDeviceBinding() {
        try {
            localStorage.removeItem(STORAGE_KEY_DEVICE);
        } catch (e) {}
    }

    function toProfile(user) {
        if (!user) {
            return null;
        }
        return {
            id: user.studentId || ("USR-" + user.id),
            userId: user.id,
            name: user.fullName,
            email: user.email,
            mobile: user.mobile || "",
            role: (user.role || "").toLowerCase(),
            studentId: user.studentId || null,
            lastLoginAt: user.lastLoginAt || null
        };
    }

    function dashboardFor(role) {
        if (role === "admin") return "admin-dashboard.html";
        if (role === "automation_admin") return "automation-admin.html";
        return "student-dashboard.html";
    }

    /**
     * fetch() wrapper: attaches the bearer token, sends/receives JSON, and normalises
     * failures into { success:false, message, status }.
     */
    function api(path, options) {
        options = options || {};
        var session = readSession();
        var dev = readDeviceBinding();
        var headers = options.headers || {};
        if (!(options.body instanceof FormData)) {
            headers["Content-Type"] = "application/json";
        }
        headers["Accept"] = "application/json";
        if (session && session.token) {
            headers["Authorization"] = "Bearer " + session.token;
        }
        if (dev && dev.deviceId && dev.deviceToken) {
            headers["X-Device-Id"] = dev.deviceId;
            headers["X-Device-Token"] = dev.deviceToken;
        }
        var init = {
            method: options.method || "GET",
            headers: headers,
            body: options.body instanceof FormData ? options.body
                : (options.body !== undefined ? JSON.stringify(options.body) : undefined)
        };
        return fetch(API_BASE + path, init).then(function (response) {
            return response.text().then(function (text) {
                var data = null;
                try {
                    data = text ? JSON.parse(text) : null;
                } catch (e) {
                    data = null;
                }
                if (!data) {
                    data = { success: response.ok, message: response.ok ? "" : "Unexpected server response." };
                }
                data.status = response.status;
                if (response.status === 401 && session && !options.skipAuthRedirect) {
                    clearSession();
                }
                return data;
            });
        }).catch(function () {
            return { success: false, status: 0, message: "Cannot reach the server. Please check your connection and try again." };
        });
    }

    var LSI_Auth = {
        api: api,
        apiBase: API_BASE,

        login: function (identifier, password, rememberMe, portal) {
            var dev = readDeviceBinding();
            var payload = {
                identifier: (identifier || "").trim(),
                password: password || "",
                portal: portal || "STUDENT"
            };
            if (dev && dev.deviceId && dev.deviceToken) {
                payload.deviceId = dev.deviceId;
                payload.deviceToken = dev.deviceToken;
            }
            return api("/auth/login", {
                method: "POST",
                body: payload,
                skipAuthRedirect: true
            }).then(function (res) {
                if (!res.success || !res.data) {
                    return { success: false, message: res.message || "Invalid email/student ID or password." };
                }
                if (res.data.deviceStatus && res.data.deviceStatus !== "AUTHENTICATED") {
                    return {
                        success: false,
                        deviceRequired: true,
                        deviceStatus: res.data.deviceStatus,
                        tempToken: res.data.tempToken,
                        emailMasked: res.data.emailMasked,
                        registeredDeviceName: res.data.registeredDeviceName,
                        message: res.data.message || res.message
                    };
                }
                if (res.data.deviceId && res.data.deviceToken) {
                    saveDeviceBinding(res.data.deviceId, res.data.deviceToken);
                }
                var profile = toProfile(res.data.user);
                writeSession({
                    token: res.data.accessToken,
                    expiresAt: res.data.expiresAt,
                    role: profile.role,
                    profile: profile,
                    loginTime: new Date().toISOString(),
                    rememberMe: !!rememberMe
                }, rememberMe);

                var redirectTarget = sessionStorage.getItem(STORAGE_KEY_REDIRECT) || res.data.redirectUrl || dashboardFor(profile.role);
                sessionStorage.removeItem(STORAGE_KEY_REDIRECT);
                return { success: true, role: profile.role, user: profile, redirectUrl: redirectTarget };
            });
        },

        /** Confirms the stored token is still valid on the server and refreshes the cached profile. */
        refreshProfile: function () {
            var session = readSession();
            if (!session || !session.token) {
                return Promise.resolve(null);
            }
            return api("/auth/me").then(function (res) {
                if (!res.success || !res.data) {
                    clearSession();
                    return null;
                }
                session.profile = toProfile(res.data);
                session.role = session.profile.role;
                storage().setItem(STORAGE_KEY_SESSION, JSON.stringify(session));
                return session.profile;
            });
        },

        getSession: readSession,

        isAuthenticated: function () {
            var session = readSession();
            return !!(session && session.token && session.role);
        },

        getCurrentUser: function () {
            var session = readSession();
            return session ? session.profile : null;
        },

        getUserRole: function () {
            var session = readSession();
            return session ? session.role : null;
        },

        logout: function (loginUrl) {
            var finish = function () {
                clearSession();
                sessionStorage.removeItem(STORAGE_KEY_REDIRECT);
                var target = loginUrl || "student-login.html";
                // replace() drops the protected page from history, so the browser Back button
                // cannot return to it after logout.
                window.location.replace(target + (target.indexOf("?") >= 0 ? "&" : "?") + "logged_out=1");
            };
            api("/auth/logout", { method: "POST", skipAuthRedirect: true }).then(finish, finish);
        },

        /**
         * Route guard. Redirects immediately if no token is stored, then validates the token with
         * the server in the background and redirects if it has been revoked (logout elsewhere,
         * admin force-logout, password change, expiry).
         */
        requireAuth: function (requiredRole, fallbackLoginUrl) {
            var loginPage = fallbackLoginUrl || "student-login.html";
            var sep = loginPage.indexOf("?") >= 0 ? "&" : "?";
            var session = readSession();

            if (!session || !session.token) {
                sessionStorage.setItem(STORAGE_KEY_REDIRECT, window.location.pathname.split("/").pop());
                window.location.replace(loginPage + sep + "auth_required=1");
                return false;
            }

            // Strict role separation: an admin session cannot open the student dashboard and vice
            // versa — each role is sent to its own dashboard. (APIs enforce the same on the server.)
            // requiredRole may be one role or a list of roles allowed on this page.
            var allowed = Array.isArray(requiredRole) ? requiredRole : (requiredRole ? [requiredRole] : []);
            if (allowed.length && allowed.indexOf(session.role) < 0) {
                window.location.replace(dashboardFor(session.role));
                return false;
            }

            LSI_Auth.refreshProfile().then(function (profile) {
                if (!profile) {
                    sessionStorage.setItem(STORAGE_KEY_REDIRECT, window.location.pathname.split("/").pop());
                    window.location.replace(loginPage + sep + "session_expired=1");
                }
            });
            // A page restored from the back/forward cache (Back button after logout) does not
            // re-run the guard above, so re-check the stored session whenever it is shown again.
            window.addEventListener("pageshow", function (ev) {
                if (ev.persisted && !LSI_Auth.isAuthenticated()) {
                    window.location.replace(loginPage + sep + "auth_required=1");
                }
            });
            return true;
        },

        updateNavUI: function () {
            var session = readSession();
            if (session && session.profile && $) {
                var targetDashboard = dashboardFor(session.role);
                var first = (session.profile.name || "User").split(" ")[0];
                $('.topbar a[href="student-login.html"]').each(function () {
                    $(this).html('<i class="fas fa-user-check text-warning me-1"></i> ' + first + ' (Dashboard)')
                        .attr('href', targetDashboard);
                });
                $('.navbar-nav a[href="student-login.html"]').each(function () {
                    $(this).text('Dashboard (' + first + ')').attr('href', targetDashboard);
                });
            }
        },

        forgotPassword: function (email) {
            return api("/auth/forgot-password", { method: "POST", body: { email: email }, skipAuthRedirect: true });
        },

        checkToken: function (token) {
            return api("/auth/token-check?token=" + encodeURIComponent(token), { skipAuthRedirect: true });
        },

        setPasswordWithToken: function (token, newPassword) {
            return api("/auth/reset-password", { method: "POST", body: { token: token, newPassword: newPassword }, skipAuthRedirect: true });
        },

        changePassword: function (currentPassword, newPassword) {
            return api("/auth/change-password", { method: "POST", body: { currentPassword: currentPassword, newPassword: newPassword } });
        },

        changeUserId: function (currentUserId, newUserId, currentPassword) {
            return api("/auth/change-user-id", { method: "POST", body: {
                currentUserId: currentUserId, newUserId: newUserId, currentPassword: currentPassword
            } });
        },

        registerDevice: function (tempToken, otp, deviceName) {
            var platform = (navigator.userAgentData && navigator.userAgentData.platform)
                ? navigator.userAgentData.platform
                : (navigator.platform || "Desktop");
            return api("/auth/device/register", {
                method: "POST",
                body: {
                    tempToken: tempToken,
                    otp: (otp || "").trim(),
                    deviceName: (deviceName || "").trim() || "My Computer",
                    devicePlatform: platform
                },
                skipAuthRedirect: true
            }).then(function (res) {
                if (!res.success || !res.data) {
                    return { success: false, message: res.message || "Device registration failed." };
                }
                if (res.data.deviceId && res.data.deviceToken) {
                    saveDeviceBinding(res.data.deviceId, res.data.deviceToken);
                }
                var profile = toProfile(res.data.user);
                writeSession({
                    token: res.data.accessToken,
                    expiresAt: res.data.expiresAt,
                    role: profile.role,
                    profile: profile,
                    loginTime: new Date().toISOString(),
                    rememberMe: true
                }, true);
                var redirectTarget = sessionStorage.getItem(STORAGE_KEY_REDIRECT) || res.data.redirectUrl || dashboardFor(profile.role);
                sessionStorage.removeItem(STORAGE_KEY_REDIRECT);
                return { success: true, role: profile.role, user: profile, redirectUrl: redirectTarget };
            });
        },

        linkBrowser: function (tempToken, otp) {
            var platform = (navigator.userAgentData && navigator.userAgentData.platform)
                ? navigator.userAgentData.platform
                : (navigator.platform || "Desktop");
            return api("/auth/device/link-browser", {
                method: "POST",
                body: {
                    tempToken: tempToken,
                    otp: (otp || "").trim(),
                    devicePlatform: platform
                },
                skipAuthRedirect: true
            }).then(function (res) {
                if (!res.success || !res.data) {
                    return { success: false, message: res.message || "Browser authorization failed." };
                }
                if (res.data.deviceId && res.data.deviceToken) {
                    saveDeviceBinding(res.data.deviceId, res.data.deviceToken);
                }
                var profile = toProfile(res.data.user);
                writeSession({
                    token: res.data.accessToken,
                    expiresAt: res.data.expiresAt,
                    role: profile.role,
                    profile: profile,
                    loginTime: new Date().toISOString(),
                    rememberMe: true
                }, true);
                var redirectTarget = sessionStorage.getItem(STORAGE_KEY_REDIRECT) || res.data.redirectUrl || dashboardFor(profile.role);
                sessionStorage.removeItem(STORAGE_KEY_REDIRECT);
                return { success: true, role: profile.role, user: profile, redirectUrl: redirectTarget };
            });
        },

        requestDeviceReset: function (identifier) {
            return api("/auth/device/reset-request", {
                method: "POST",
                body: { identifier: (identifier || "").trim() },
                skipAuthRedirect: true
            });
        },

        confirmDeviceReset: function (tempToken, otp, newDeviceName) {
            var platform = (navigator.userAgentData && navigator.userAgentData.platform)
                ? navigator.userAgentData.platform
                : (navigator.platform || "Desktop");
            return api("/auth/device/reset-confirm", {
                method: "POST",
                body: {
                    tempToken: tempToken,
                    otp: (otp || "").trim(),
                    newDeviceName: (newDeviceName || "").trim() || "My Computer",
                    newDevicePlatform: platform
                },
                skipAuthRedirect: true
            }).then(function (res) {
                if (!res.success || !res.data) {
                    return { success: false, message: res.message || "Device reset failed." };
                }
                if (res.data.deviceId && res.data.deviceToken) {
                    saveDeviceBinding(res.data.deviceId, res.data.deviceToken);
                }
                var profile = toProfile(res.data.user);
                writeSession({
                    token: res.data.accessToken,
                    expiresAt: res.data.expiresAt,
                    role: profile.role,
                    profile: profile,
                    loginTime: new Date().toISOString(),
                    rememberMe: true
                }, true);
                var redirectTarget = sessionStorage.getItem(STORAGE_KEY_REDIRECT) || res.data.redirectUrl || dashboardFor(profile.role);
                sessionStorage.removeItem(STORAGE_KEY_REDIRECT);
                return { success: true, role: profile.role, user: profile, redirectUrl: redirectTarget };
            });
        },

        getDeviceBinding: readDeviceBinding,
        clearDeviceBinding: clearDeviceBinding
    };

    window.LSI_Auth = LSI_Auth;

    if (typeof $ !== "undefined" && typeof document !== "undefined") {
        $(document).ready(function () {
            LSI_Auth.updateNavUI();
        });
    }

})(typeof window !== "undefined" ? window : globalThis, typeof window !== "undefined" ? window.jQuery : undefined);
