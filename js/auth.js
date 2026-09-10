/**
 * ============================================================================
 * LORD SAI ACADEMY — AUTHENTICATION & SESSION MANAGEMENT ENGINE (js/auth.js)
 * ============================================================================
 * End-to-end authentication, session persistence, role-based authorization,
 * and security middleware for student, teacher, and admin portals.
 */

(function (window, $) {
    "use strict";

    const STORAGE_KEY_SESSION = "lsi_auth_user_session";
    const STORAGE_KEY_TOKEN = "lsi_auth_token";
    const STORAGE_KEY_REDIRECT = "lsi_auth_redirect_target";

    // Preset verified accounts database
    const VERIFIED_ACCOUNTS = [
        {
            identifiers: ["student@lordsai.com", "student", "lsi-2024", "lsi2024", "9920254354", "rahul.sharma@example.com"],
            password: "student123",
            role: "student",
            profile: {
                id: "LSI-2024-884",
                name: "Rahul Sharma",
                email: "student@lordsai.com",
                mobile: "9920 254 354",
                location: "Pune, Maharashtra",
                batch: "Uran Classroom Batch 2024",
                course: "Stock Market Basics & Technical Analysis",
                mentor: "Vaibhav S. Pawar",
                enrolledDate: "15 Jan 2024",
                attendance: "92%",
                moduleProgress: "Module 4 of 6 (75% Complete)",
                journalEntriesCount: 14,
                avatar: "img/students/video_rahul.jpg"
            },
            redirectUrl: "student-dashboard.html"
        },
        {
            identifiers: ["pooja@lordsai.com", "pooja.deshmukh", "lsi-2024-02"],
            password: "student123",
            role: "student",
            profile: {
                id: "LSI-2024-02",
                name: "Pooja Deshmukh",
                email: "pooja@lordsai.com",
                mobile: "9920 254 354",
                location: "Uran, Navi Mumbai",
                batch: "Navi Mumbai Evening Cohort",
                course: "Swing Trading & Risk Management",
                mentor: "Vaibhav S. Pawar",
                enrolledDate: "10 Feb 2024",
                attendance: "95%",
                moduleProgress: "Module 5 of 6 (85% Complete)",
                journalEntriesCount: 22,
                avatar: "img/students/video_pooja.jpg"
            },
            redirectUrl: "student-dashboard.html"
        },
        {
            identifiers: ["mentor@lordsai.com", "teacher@lordsai.com", "mentor", "teacher", "vaibhav@lordsai.com"],
            password: "mentor123",
            role: "teacher",
            profile: {
                id: "LSI-FAC-01",
                name: "Vaibhav S. Pawar",
                email: "mentor@lordsai.com",
                roleTitle: "Founder & Lead Mentor — AMFI Registered MFD (ARN-280789)",
                location: "Uran, Navi Mumbai",
                activeBatches: ["Uran Batch 2024", "Navi Mumbai Cohort", "Weekend Executive Batch"]
            },
            redirectUrl: "teacher-dashboard.html"
        },
        {
            identifiers: ["admin@lordsai.com", "admin"],
            password: "admin123",
            role: "admin",
            profile: {
                id: "LSI-ADM-01",
                name: "Academy Administrator",
                email: "admin@lordsai.com",
                roleTitle: "LSI System Admin & Registrar"
            },
            redirectUrl: "admin-dashboard.html"
        }
    ];

    const LSI_Auth = {
        /**
         * Authenticate credentials
         * @param {string} identifier - Email or Student ID
         * @param {string} password - User Password
         * @param {boolean} rememberMe - Persist in localStorage
         * @returns {Promise<object>}
         */
        login: function (identifier, password, rememberMe) {
            return new Promise((resolve) => {
                setTimeout(() => {
                    const cleanId = (identifier || "").trim().toLowerCase();
                    const cleanPass = (password || "").trim();

                    if (!cleanId || !cleanPass) {
                        resolve({
                            success: false,
                            message: "Please enter both your Student ID/Email and Password."
                        });
                        return;
                    }

                    // 1. Check in verified preset accounts
                    let matched = VERIFIED_ACCOUNTS.find(acc =>
                        acc.identifiers.includes(cleanId) && acc.password === cleanPass
                    );

                    // 2. Also accept standard student credentials if valid format and password is correct demo
                    if (!matched) {
                        const isStudentFormat = cleanId.includes("@") || cleanId.startsWith("lsi") || /^\d{10}$/.test(cleanId);
                        if (isStudentFormat && (cleanPass === "student123" || cleanPass === "lsi@2024" || cleanPass === "123456")) {
                            matched = {
                                role: "student",
                                profile: {
                                    id: cleanId.toUpperCase(),
                                    name: cleanId.split("@")[0].replace(".", " ").toUpperCase() || "Enrolled Student",
                                    email: cleanId.includes("@") ? cleanId : cleanId + "@student.lordsai.com",
                                    batch: "Uran Classroom Batch 2024",
                                    course: "Stock Market Basics & Technical Analysis",
                                    mentor: "Vaibhav S. Pawar",
                                    attendance: "90%",
                                    moduleProgress: "Module 4 of 6",
                                    journalEntriesCount: 10,
                                    avatar: "img/students/video_rahul.jpg"
                                },
                                redirectUrl: "student-dashboard.html"
                            };
                        }
                    }

                    if (matched) {
                        const token = "lsi_jwt_" + Math.random().toString(36).substring(2) + "_" + Date.now();
                        const sessionData = {
                            token: token,
                            role: matched.role,
                            profile: matched.profile,
                            loginTime: new Date().toISOString(),
                            rememberMe: !!rememberMe
                        };

                        const storage = rememberMe ? localStorage : sessionStorage;
                        localStorage.removeItem(STORAGE_KEY_SESSION);
                        sessionStorage.removeItem(STORAGE_KEY_SESSION);
                        localStorage.removeItem(STORAGE_KEY_TOKEN);
                        sessionStorage.removeItem(STORAGE_KEY_TOKEN);

                        storage.setItem(STORAGE_KEY_SESSION, JSON.stringify(sessionData));
                        storage.setItem(STORAGE_KEY_TOKEN, token);

                        const redirectTarget = sessionStorage.getItem(STORAGE_KEY_REDIRECT) || matched.redirectUrl;
                        sessionStorage.removeItem(STORAGE_KEY_REDIRECT);

                        resolve({
                            success: true,
                            role: matched.role,
                            user: matched.profile,
                            redirectUrl: redirectTarget
                        });
                    } else {
                        resolve({
                            success: false,
                            message: "Invalid email/student ID or password. Please check your credentials and try again."
                        });
                    }
                }, 450);
            });
        },

        /**
         * Get current session data
         * @returns {object|null}
         */
        getSession: function () {
            try {
                let raw = localStorage.getItem(STORAGE_KEY_SESSION) || sessionStorage.getItem(STORAGE_KEY_SESSION);
                return raw ? JSON.parse(raw) : null;
            } catch (e) {
                return null;
            }
        },

        /**
         * Check if a session exists
         * @returns {boolean}
         */
        isAuthenticated: function () {
            const session = this.getSession();
            return !!(session && session.token && session.role);
        },

        /**
         * Get currently logged-in user profile
         * @returns {object|null}
         */
        getCurrentUser: function () {
            const session = this.getSession();
            return session ? session.profile : null;
        },

        /**
         * Get user role
         * @returns {string|null}
         */
        getUserRole: function () {
            const session = this.getSession();
            return session ? session.role : null;
        },

        /**
         * Logout user and redirect
         */
        logout: function () {
            localStorage.removeItem(STORAGE_KEY_SESSION);
            sessionStorage.removeItem(STORAGE_KEY_SESSION);
            localStorage.removeItem(STORAGE_KEY_TOKEN);
            sessionStorage.removeItem(STORAGE_KEY_TOKEN);
            sessionStorage.removeItem(STORAGE_KEY_REDIRECT);
            window.location.href = "student-login.html?logged_out=1";
        },

        /**
         * Route guard: require authorization for protected pages
         * @param {string} requiredRole - e.g. 'student', 'teacher', 'admin'
         * @param {string} fallbackLoginUrl - defaults to 'student-login.html'
         */
        requireAuth: function (requiredRole, fallbackLoginUrl) {
            const loginPage = fallbackLoginUrl || "student-login.html";
            const session = this.getSession();

            if (!session || !session.token) {
                sessionStorage.setItem(STORAGE_KEY_REDIRECT, window.location.pathname.split("/").pop());
                window.location.replace(loginPage + "?auth_required=1");
                return false;
            }

            if (requiredRole && session.role !== requiredRole && session.role !== "admin") {
                if (session.role === "student") {
                    window.location.replace("student-dashboard.html");
                } else if (session.role === "teacher") {
                    window.location.replace("teacher-dashboard.html");
                } else if (session.role === "admin") {
                    window.location.replace("admin-dashboard.html");
                }
                return false;
            }

            return true;
        },

        /**
         * Update UI navigation links across headers dynamically
         */
        updateNavUI: function () {
            const session = this.getSession();
            if (session && session.profile) {
                const targetDashboard = session.role === "student" ? "student-dashboard.html" : (session.role === "teacher" ? "teacher-dashboard.html" : "admin-dashboard.html");
                
                $('.topbar a[href="student-login.html"]').each(function () {
                    $(this).html(
                        '<i class="fas fa-user-check text-warning me-1"></i> ' +
                        session.profile.name.split(" ")[0] + ' (Dashboard)'
                    ).attr('href', targetDashboard);
                });

                $('.navbar-nav a[href="student-login.html"]').each(function () {
                    $(this).text('Dashboard (' + session.profile.name.split(" ")[0] + ')')
                           .attr('href', targetDashboard);
                });
            }
        }
    };

    window.LSI_Auth = LSI_Auth;

    if (typeof $ !== "undefined" && typeof document !== "undefined") {
        $(document).ready(function () {
            LSI_Auth.updateNavUI();
        });
    }

})(typeof window !== "undefined" ? window : globalThis, typeof window !== "undefined" ? window.jQuery : undefined);

