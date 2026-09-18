/**
 * LORD SAI INVESTMENT & SHARE MARKET ACADEMY
 * PRODUCT OFFERINGS DATA & DATE-SENSITIVE OFFERING HANDLER (js/offerings-data.js)
 *
 * Source: NJ Product Offerings (September 2026)
 * Facilitates straightforward administrative updates to NFO and IPO schedules
 * without restructuring HTML layout or CSS.
 */

(function (window) {
    "use strict";

    const LSI_Offerings = {
        // Date-sensitive September 2026 New Fund Offers (NFOs)
        nfos: [
            {
                id: "nfo-jioblackrock",
                fundHouse: "Jio BlackRock Mutual Fund",
                schemeName: "JioBlackRock Balanced Advantage Fund",
                category: "Hybrid: Balanced Advantage",
                startDate: "2026-09-11",
                endDate: "2026-09-25",
                datesDisplay: "11th Sept 2026 – 25th Sept 2026",
                logo: "img/mf-logos/jio-blackrock-mutual-fund.png"
            },
            {
                id: "nfo-navi",
                fundHouse: "Navi Mutual Fund",
                schemeName: "Navi Nifty REITs & Realty Index Fund",
                category: "Index Fund: REITs & Realty",
                startDate: "2026-09-01",
                endDate: "2026-09-10",
                datesDisplay: "01st Sept 2026 – 10th Sept 2026",
                logo: "img/mf-logos/navi-mutual-fund.png"
            },
            {
                id: "nfo-shriram",
                fundHouse: "Shriram Mutual Fund",
                schemeName: "Shriram Gold ETF Passive FOF",
                category: "Commodity: Passive Fund of Funds",
                startDate: "2026-09-11",
                endDate: "2026-09-24",
                datesDisplay: "11th Sept 2026 – 24th Sept 2026",
                logo: "img/mf-logos/shriram-mutual-fund.png"
            }
        ],

        // SIF (Specialized Investment Funds) NFO - September 2026
        sifNfo: {
            id: "sif-nfo-altiva",
            brand: "Altiva",
            schemeName: "Altiva Equity Long-Short Fund",
            category: "Specialized Investment Fund: Long-Short Strategy",
            startDate: "2026-09-10",
            endDate: "2026-09-24",
            datesDisplay: "10th Sept 2026 – 24th Sept 2026"
        },

        // Date-sensitive September 2026 IPOs
        ipos: [
            {
                id: "ipo-deepa",
                companyName: "Deepa Jewellers",
                type: "SME / Mainboard IPO",
                startDate: "2026-09-01",
                endDate: "2026-09-03",
                datesDisplay: "1st Sept 2026 – 3rd Sept 2026"
            },
            {
                id: "ipo-rays",
                companyName: "Rays of Belief",
                type: "IPO Offering",
                startDate: "2026-09-01",
                endDate: "2026-09-03",
                datesDisplay: "1st Sept 2026 – 3rd Sept 2026"
            },
            {
                id: "ipo-pranav",
                companyName: "Pranav Constructions",
                type: "Infrastructure & Real Estate IPO",
                startDate: "2026-09-07",
                endDate: "2026-09-09",
                datesDisplay: "7th Sept 2026 – 9th Sept 2026"
            },
            {
                id: "ipo-arc",
                companyName: "Asset Reconstruction Co. (India)",
                type: "Financial Services IPO",
                startDate: "2026-09-09",
                endDate: "2026-09-11",
                datesDisplay: "9th Sept 2026 – 11th Sept 2026"
            },
            {
                id: "ipo-veegaland",
                companyName: "Veegaland Developers",
                type: "Commercial & Residential Realty IPO",
                startDate: "2026-09-10",
                endDate: "2026-09-15",
                datesDisplay: "10th Sept 2026 – 15th Sept 2026"
            }
        ],

        getStatus: function (startDateStr, endDateStr) {
            const now = new Date();
            const start = new Date(startDateStr + "T00:00:00");
            const end = new Date(endDateStr + "T23:59:59");

            if (now < start) {
                return {
                    status: "upcoming",
                    badgeClass: "badge bg-info text-dark",
                    labelEn: "Upcoming Offering",
                    labelMr: "आगामी योजना",
                    labelHi: "आगामी योजना"
                };
            } else if (now >= start && now <= end) {
                return {
                    status: "open",
                    badgeClass: "badge bg-success text-white",
                    labelEn: "Open Now",
                    labelMr: "सध्या सुरू आहे",
                    labelHi: "वर्तमान में खुला"
                };
            } else {
                return {
                    status: "closed",
                    badgeClass: "badge bg-secondary text-white",
                    labelEn: "Closed / Track NAV",
                    labelMr: "मुदत समाप्त / NAV ट्रॅक करा",
                    labelHi: "समाप्त / NAV ट्रैक करें"
                };
            }
        },

        initBadges: function () {
            const elements = document.querySelectorAll("[data-offering-dates]");
            const lang = (window.LSI_CurrentLang && window.LSI_CurrentLang()) || localStorage.getItem("lsi_lang") || "en";
            elements.forEach(function (el) {
                const datesAttr = el.getAttribute("data-offering-dates");
                if (!datesAttr) return;
                const parts = datesAttr.split(":");
                if (parts.length === 2) {
                    const start = parts[0].trim();
                    const end = parts[1].trim();
                    const info = LSI_Offerings.getStatus(start, end);
                    
                    const badgeEl = el.querySelector(".offering-dynamic-badge");
                    if (badgeEl) {
                        badgeEl.className = "offering-dynamic-badge " + info.badgeClass;
                        badgeEl.setAttribute("data-status", info.status);
                        if (lang === "mr") {
                            badgeEl.textContent = info.labelMr;
                        } else if (lang === "hi") {
                            badgeEl.textContent = info.labelHi;
                        } else {
                            badgeEl.textContent = info.labelEn;
                        }
                    }
                }
            });
        }
    };

    window.LSI_Offerings = LSI_Offerings;

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", LSI_Offerings.initBadges);
    } else {
        LSI_Offerings.initBadges();
    }

    window.addEventListener("lsi:langChanged", function () {
        if (window.LSI_Offerings && window.LSI_Offerings.initBadges) {
            window.LSI_Offerings.initBadges();
        }
    });

})(window);
