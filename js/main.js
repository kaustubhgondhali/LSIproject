(function ($) {
    "use strict";

    // Spinner
    var spinner = function () {
        setTimeout(function () {
            if ($('#spinner').length > 0) {
                $('#spinner').removeClass('show');
            }
        }, 1);
    };
    spinner(0);
    
    // Initiate the wowjs
    if (typeof WOW !== 'undefined') {
        new WOW().init();
    }

    // Sticky Navbar
    $(window).scroll(function () {
        if ($(this).scrollTop() > 45) {
            $('.navbar').addClass('sticky-top');
        } else {
            $('.navbar').removeClass('sticky-top');
        }
    });

    // Back to top button
    $(window).scroll(function () {
        if ($(this).scrollTop() > 300) {
            $('.back-to-top').fadeIn('slow');
        } else {
            $('.back-to-top').fadeOut('slow');
        }
    });
    $('.back-to-top').click(function () {
        $('html, body').animate({scrollTop: 0}, 800, 'easeInOutExpo');
        return false;
    });

    // Testimonial Carousel
    if ($(".testimonial-carousel-v2").length > 0 && typeof $.fn.owlCarousel !== 'undefined') {
        $(".testimonial-carousel-v2").owlCarousel({
            autoplay: true,
            smartSpeed: 1000,
            center: false,
            dots: true,
            loop: true,
            margin: 24,
            nav: false,
            responsiveClass: true,
            responsive: {
                0: { items: 1 },
                768: { items: 2 },
                1200: { items: 3 }
            }
        });
    }

    // ==========================================
    // 1. POSITION SIZING & RISK CALCULATOR
    // ==========================================
    function calculatePositionSize() {
        var capital = parseFloat($('#calc-capital').val()) || 100000;
        var riskPercent = parseFloat($('#calc-risk-pct').val()) || 2;
        var entryPrice = parseFloat($('#calc-entry').val()) || 500;
        var slPrice = parseFloat($('#calc-sl').val()) || 485;
        var targetPrice = parseFloat($('#calc-target').val()) || 545;

        // Update range labels
        $('#risk-pct-label').text(riskPercent + '%');

        var maxRiskAmount = capital * (riskPercent / 100);
        var slDistance = Math.abs(entryPrice - slPrice);

        if (slDistance > 0 && entryPrice > 0) {
            var quantity = Math.floor(maxRiskAmount / slDistance);
            var tradeValue = (quantity * entryPrice);
            var targetDistance = Math.abs(targetPrice - entryPrice);
            var potentialProfit = quantity * targetDistance;
            var riskRewardRatio = (targetDistance / slDistance).toFixed(2);

            $('#res-quantity').text(quantity.toLocaleString('en-IN') + ' Qty');
            $('#res-max-risk').text('₹' + Math.round(maxRiskAmount).toLocaleString('en-IN'));
            $('#res-potential-profit').text('₹' + Math.round(potentialProfit).toLocaleString('en-IN'));
            $('#res-rr-ratio').text('1 : ' + riskRewardRatio);
            $('#res-trade-capital').text('₹' + Math.round(tradeValue).toLocaleString('en-IN'));
        }
    }

    // Bind Calculator Events
    $(document).on('input change', '#calc-capital, #calc-risk-pct, #calc-entry, #calc-sl, #calc-target', function() {
        calculatePositionSize();
    });

    // Preset Strategy buttons for calculator
    $(document).on('click', '.calc-preset-btn', function() {
        var capital = $(this).data('capital');
        var entry = $(this).data('entry');
        var sl = $(this).data('sl');
        var target = $(this).data('target');

        $('#calc-capital').val(capital);
        $('#calc-entry').val(entry);
        $('#calc-sl').val(sl);
        $('#calc-target').val(target);
        calculatePositionSize();
    });

    // ==========================================
    // 2. SIP & COMPOUND WEALTH CALCULATOR
    // ==========================================
    function calculateCompoundWealth() {
        // Support both old and new slider IDs
        var monthlyInvestment = parseFloat($('#sipMonthlyRange').val()) || parseFloat($('#sip-monthly').val()) || 5000;
        var returnRate = parseFloat($('#sipRateRange').val()) || parseFloat($('#sip-rate').val()) || 12;
        var years = parseFloat($('#sipYearsRange').val()) || parseFloat($('#sip-years').val()) || 10;

        // Update labels
        $('#sipMonthlyVal').text('₹' + monthlyInvestment.toLocaleString('en-IN'));
        $('#sipRateVal').text(returnRate + '%');
        $('#sipYearsVal').text(years + ' Years');

        $('#sip-monthly-lbl').text('₹' + monthlyInvestment.toLocaleString('en-IN'));
        $('#sip-rate-lbl').text(returnRate + '% p.a.');
        $('#sip-years-lbl').text(years + ' Years');

        var totalMonths = years * 12;
        var monthlyRate = (returnRate / 100) / 12;
        var totalInvested = monthlyInvestment * totalMonths;

        // Future Value Formula: P * [((1 + r)^n - 1) / r] * (1 + r)
        var futureValue = monthlyInvestment * ((Math.pow(1 + monthlyRate, totalMonths) - 1) / monthlyRate) * (1 + monthlyRate);
        var wealthGained = futureValue - totalInvested;

        // Update display on new index.html widget
        $('#sipFutureValue').text('₹' + Math.round(futureValue).toLocaleString('en-IN'));
        $('#sipInvestedAmount').text('₹' + Math.round(totalInvested).toLocaleString('en-IN'));
        $('#sipEstReturns').text('₹' + Math.round(wealthGained).toLocaleString('en-IN'));

        // Old IDs fallback
        $('#sip-res-invested').text('₹' + Math.round(totalInvested).toLocaleString('en-IN'));
        $('#sip-res-gain').text('₹' + Math.round(wealthGained).toLocaleString('en-IN'));
        $('#sip-res-total').text('₹' + Math.round(futureValue).toLocaleString('en-IN'));
    }

    $(document).on('input change', '#sipMonthlyRange, #sipRateRange, #sipYearsRange, #sip-monthly, #sip-rate, #sip-years', function() {
        calculateCompoundWealth();
    });

    // ==========================================
    // 2B. SWP CASH-FLOW & LONGEVITY CALCULATOR
    // ==========================================
    function calculateSWPCashflow() {
        if (!$('#swpCorpusRange').length) return;

        var initialCorpus = parseFloat($('#swpCorpusRange').val()) || 2500000;
        var monthlyWithdrawal = parseFloat($('#swpMonthlyRange').val()) || 15000;
        var annualRate = parseFloat($('#swpRateRange').val()) || 8.5;
        var years = parseFloat($('#swpYearsRange').val()) || 10;

        // Update slider labels
        $('#swpCorpusVal').text('₹' + Math.round(initialCorpus).toLocaleString('en-IN'));
        $('#swpMonthlyVal').text('₹' + Math.round(monthlyWithdrawal).toLocaleString('en-IN'));
        $('#swpRateVal').text(annualRate + '%');
        $('#swpYearsVal').text(years + ' Years');

        var totalMonths = years * 12;
        var monthlyRate = (annualRate / 100) / 12;
        var corpus = initialCorpus;
        var totalWithdrawn = 0;
        var exhaustedMonth = null;

        for (var m = 1; m <= totalMonths; m++) {
            corpus = corpus * (1 + monthlyRate);
            if (corpus >= monthlyWithdrawal) {
                corpus -= monthlyWithdrawal;
                totalWithdrawn += monthlyWithdrawal;
            } else {
                totalWithdrawn += corpus;
                corpus = 0;
                exhaustedMonth = m;
                break;
            }
        }

        // Annual withdrawal percentage of initial corpus
        var annualWithdrawalRate = ((monthlyWithdrawal * 12) / initialCorpus) * 100;

        $('#swpTotalWithdrawn').text('₹' + Math.round(totalWithdrawn).toLocaleString('en-IN'));
        $('#swpRemainingCorpus').text('₹' + Math.round(corpus).toLocaleString('en-IN'));
        $('#swpWithdrawalRate').text(annualWithdrawalRate.toFixed(1) + '% p.a.');

        // Sustainability status badge
        var badgeEl = $('#swpSustainabilityBadge');
        if (badgeEl.length) {
            if (exhaustedMonth !== null) {
                var exhaustedYears = (exhaustedMonth / 12).toFixed(1);
                badgeEl.attr('class', 'badge bg-danger text-uppercase px-3 py-1')
                       .html('<i class="fas fa-exclamation-triangle me-1"></i> Corpus Depleted in ' + exhaustedYears + ' Yrs');
            } else if (annualWithdrawalRate <= 7.0) {
                badgeEl.attr('class', 'badge bg-success text-uppercase px-3 py-1')
                       .html('<i class="fas fa-shield-alt me-1"></i> Highly Sustainable Rate');
            } else if (annualWithdrawalRate <= 9.0) {
                badgeEl.attr('class', 'badge bg-primary text-uppercase px-3 py-1')
                       .html('<i class="fas fa-check-circle me-1"></i> Moderate / Balanced Rate');
            } else {
                badgeEl.attr('class', 'badge bg-warning text-dark text-uppercase px-3 py-1')
                       .html('<i class="fas fa-exclamation-circle me-1"></i> Aggressive Rate (Monitor Closely)');
            }
        }
    }

    $(document).on('input change', '#swpCorpusRange, #swpMonthlyRange, #swpRateRange, #swpYearsRange', function() {
        calculateSWPCashflow();
    });

    // ==========================================
    // 3. MASTERCLASS COUNTDOWN TIMER
    // ==========================================
    function initWebinarCountdown() {
        // Set target date: 4 days from now at 11:00 AM
        var now = new Date();
        var targetDate = new Date();
        targetDate.setDate(now.getDate() + 3);
        targetDate.setHours(11, 0, 0, 0);

        function updateCountdown() {
            var currentTime = new Date().getTime();
            var difference = targetDate.getTime() - currentTime;

            if (difference > 0) {
                var days = Math.floor(difference / (1000 * 60 * 60 * 24));
                var hours = Math.floor((difference % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
                var minutes = Math.floor((difference % (1000 * 60 * 60)) / (1000 * 60));
                var seconds = Math.floor((difference % (1000 * 60)) / 1000);

                $('#timer-days').text(days < 10 ? '0' + days : days);
                $('#timer-hours').text(hours < 10 ? '0' + hours : hours);
                $('#timer-mins').text(minutes < 10 ? '0' + minutes : minutes);
                $('#timer-secs').text(seconds < 10 ? '0' + seconds : seconds);
            } else {
                $('#timer-days').text('00');
                $('#timer-hours').text('00');
                $('#timer-mins').text('00');
                $('#timer-secs').text('00');
            }
        }

        updateCountdown();
        setInterval(updateCountdown, 1000);
    }

    // ==========================================
    // 4. FREE DEMO REGISTRATION & LEAD MODAL
    // ==========================================
    $(document).on('submit', '#demoRegistrationForm, #webinarBannerForm, #contactInquiryForm', function(e) {
        e.preventDefault();
        var form = $(this);
        var submitBtn = form.find('button[type="submit"]');
        var originalText = submitBtn.html();

        submitBtn.html('<i class="fas fa-spinner fa-spin me-2"></i> Registering...').prop('disabled', true);

        setTimeout(function() {
            submitBtn.html('<i class="fas fa-check-circle me-2"></i> Request Noted').removeClass('btn-primary').addClass('btn-success');

            // NOTE: this form is frontend-only (no backend persistence or notification exists yet), so the
            // message must not claim a seat was reserved. It directs the visitor to the real contact channel.
            var successMsg = $('<div class="alert alert-success mt-3 shadow-sm"><i class="fas fa-check-circle me-2"></i> <strong>Thank you!</strong> To confirm your free strategy session seat, please message us on <a href="https://wa.me/919920254354?text=Hi%20Lord%20Sai%20Academy,%20I%20want%20to%20book%20a%20free%20strategy%20session" target="_blank" rel="noopener" class="alert-link">WhatsApp (+91 99202 54354)</a> or call us — our mentor team will share the joining link.</div>');
            form.append(successMsg);

            setTimeout(function() {
                if ($('#demoModal').length > 0) {
                    $('#demoModal').modal('hide');
                }
            }, 3000);
        }, 1200);
    });

    // ==========================================
    // SEAMLESS CONTINUOUS LIVE DISCLAIMER TICKER CONTROLLER
    // ==========================================
    // Calibrates animation duration to achieve a calm, readable, uniform speed
    // of ~30 pixels per second across all screen sizes and text lengths.
    var TARGET_TICKER_SPEED_PPS = 30; // pixels per second

    function updateSingleTickerSpeed(contentEl, customPropName) {
        if (!contentEl) return;
        var firstItem = contentEl.firstElementChild;
        if (!firstItem) return;

        // Measure rendered width of one complete repeating item block
        var itemWidth = firstItem.getBoundingClientRect().width || firstItem.offsetWidth;
        if (itemWidth > 50) {
            var duration = Math.round(itemWidth / TARGET_TICKER_SPEED_PPS);
            duration = Math.max(duration, 30);
            contentEl.style.setProperty(customPropName, duration + 's');
            contentEl.style.animationDuration = duration + 's';
        }
    }

    function refreshAllDisclaimerTickers() {
        var academyContent = document.querySelectorAll('.academy-ticker-content');
        academyContent.forEach(function(el) {
            updateSingleTickerSpeed(el, '--academy-ticker-dur');
        });

        var mfContent = document.querySelectorAll('.mf-ticker-content');
        mfContent.forEach(function(el) {
            updateSingleTickerSpeed(el, '--mf-ticker-dur');
        });
    }

    function attachHoverPauseHandlers() {
        var tracks = document.querySelectorAll('.academy-ticker-track-container, .mf-ticker-track-container');
        tracks.forEach(function(track) {
            if (track.getAttribute('data-ticker-hover-bound') === 'true') return;
            track.setAttribute('data-ticker-hover-bound', 'true');

            var content = track.querySelector('.academy-ticker-content, .mf-ticker-content');
            if (!content) return;

            track.addEventListener('mouseenter', function() {
                content.style.setProperty('animation-play-state', 'paused', 'important');
            }, { passive: true });

            track.addEventListener('mouseleave', function() {
                content.style.removeProperty('animation-play-state');
            }, { passive: true });
        });
    }

    var _disclaimerTickerInitDone = false;
    function initLiveDisclaimerTicker() {
        refreshAllDisclaimerTickers();
        attachHoverPauseHandlers();

        if (!_disclaimerTickerInitDone) {
            _disclaimerTickerInitDone = true;

            // Recalculate once web fonts finish loading for accurate text width
            if (document.fonts && document.fonts.ready) {
                document.fonts.ready.then(function() {
                    refreshAllDisclaimerTickers();
                });
            }

            // Single debounced resize listener to avoid thrashing during drag
            var resizeDebounce = null;
            window.addEventListener('resize', function() {
                clearTimeout(resizeDebounce);
                resizeDebounce = setTimeout(function() {
                    refreshAllDisclaimerTickers();
                    attachHoverPauseHandlers();
                }, 250);
            }, { passive: true });
        }
    }

    // Expose globally for mode switching & external lifecycle hooks
    window.initLiveDisclaimerTicker = initLiveDisclaimerTicker;

    // Auto-initialize immediately if DOM is ready or listen for DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initLiveDisclaimerTicker);
    } else {
        initLiveDisclaimerTicker();
    }

    // ==========================================
    // MUTUAL FUND HERO — CONTINUOUS AUTO-SLIDING CAROUSEL
    // ==========================================
    function initMfHeroSlider() {
        var track = document.getElementById('mfCarouselTrack');
        var viewport = document.getElementById('mfCarouselViewport');
        var sliderWrapper = document.getElementById('mf-hero');
        if (!track || !viewport || !sliderWrapper) return;

        // Prevent duplicate initialization (call track.__mfSliderDestroy() first to re-initialise
        // on a new slide set — js/mf-slider.js does this when admin-managed slides arrive late).
        if (track.getAttribute('data-slider-initialized') === 'true') return;
        track.setAttribute('data-slider-initialized', 'true');

        var originalSlides = Array.from(track.querySelectorAll('.mf-carousel-slide:not(.mf-clone)'));
        var slideCount = originalSlides.length;
        if (slideCount <= 1) return;

        var slideDuration = 4000; // 4.0 seconds per slide
        var transitionSpeed = 800; // 800ms horizontal sliding transition
        var isTransitioning = false;
        var autoplayTimer = null;
        var transitionTimeout = null;

        // Clone first and last slides for continuous infinite looping
        var firstClone = originalSlides[0].cloneNode(true);
        var lastClone = originalSlides[slideCount - 1].cloneNode(true);
        firstClone.classList.add('mf-clone');
        lastClone.classList.add('mf-clone');
        firstClone.setAttribute('aria-hidden', 'true');
        lastClone.setAttribute('aria-hidden', 'true');

        track.appendChild(firstClone);
        track.insertBefore(lastClone, track.firstChild);

        var totalTrackSlides = track.querySelectorAll('.mf-carousel-slide').length; // slideCount + 2

        // Current track index: 1 = Slide 0, 2 = Slide 1, 3 = Slide 2, 4 = firstClone
        var currentTrackIndex = 1;

        // Generate / bind clean circular dots
        var $dotsContainer = $('#mfSliderDots');
        $dotsContainer.empty();
        for (var i = 0; i < slideCount; i++) {
            var $dot = $('<button type="button" class="mf-dot" data-slide="' + i + '" aria-label="Slide ' + (i + 1) + '" role="tab"></button>');
            if (i === 0) {
                $dot.addClass('active').attr('aria-selected', 'true');
            } else {
                $dot.attr('aria-selected', 'false');
            }
            $dotsContainer.append($dot);
        }
        var $dots = $dotsContainer.find('.mf-dot');

        function setTrackPosition(index, animate) {
            if (animate) {
                track.style.transition = 'transform ' + transitionSpeed + 'ms cubic-bezier(0.25, 1, 0.5, 1)';
            } else {
                track.style.transition = 'none';
            }
            track.style.transform = 'translate3d(-' + (index * 100) + '%, 0, 0)';
        }

        // Initialize position at first original slide
        setTrackPosition(currentTrackIndex, false);

        function updateDots(realIndex) {
            $dots.removeClass('active').attr('aria-selected', 'false');
            $dots.eq(realIndex).addClass('active').attr('aria-selected', 'true');
        }

        function handleTransitionComplete() {
            isTransitioning = false;
            if (transitionTimeout) {
                clearTimeout(transitionTimeout);
                transitionTimeout = null;
            }

            // If reached first clone (past last slide), snap instantly to Slide 0
            if (currentTrackIndex >= totalTrackSlides - 1) {
                currentTrackIndex = 1;
                setTrackPosition(currentTrackIndex, false);
                void track.offsetHeight; // Force reflow
            }
            // If reached last clone (before first slide), snap instantly to last slide
            else if (currentTrackIndex <= 0) {
                currentTrackIndex = slideCount;
                setTrackPosition(currentTrackIndex, false);
                void track.offsetHeight; // Force reflow
            }
        }

        function slideNext() {
            if (isTransitioning) return;
            isTransitioning = true;
            currentTrackIndex++;
            setTrackPosition(currentTrackIndex, true);

            var realIndex = (currentTrackIndex - 1) % slideCount;
            updateDots(realIndex);

            if (transitionTimeout) clearTimeout(transitionTimeout);
            transitionTimeout = setTimeout(handleTransitionComplete, transitionSpeed + 30);
        }

        function slidePrev() {
            if (isTransitioning) return;
            isTransitioning = true;
            currentTrackIndex--;
            setTrackPosition(currentTrackIndex, true);

            var realIndex = (currentTrackIndex - 1 + slideCount) % slideCount;
            updateDots(realIndex);

            if (transitionTimeout) clearTimeout(transitionTimeout);
            transitionTimeout = setTimeout(handleTransitionComplete, transitionSpeed + 30);
        }

        function goToSlide(targetRealIndex) {
            var targetTrackIndex = targetRealIndex + 1;
            if (targetTrackIndex === currentTrackIndex) return;

            isTransitioning = true;
            currentTrackIndex = targetTrackIndex;
            setTrackPosition(currentTrackIndex, true);
            updateDots(targetRealIndex);

            if (transitionTimeout) clearTimeout(transitionTimeout);
            transitionTimeout = setTimeout(handleTransitionComplete, transitionSpeed + 30);

            startAutoplay();
        }

        function onTrackTransitionEnd(e) {
            if (e.target !== track) return;
            handleTransitionComplete();
        }
        track.addEventListener('transitionend', onTrackTransitionEnd);

        function startAutoplay() {
            stopAutoplay();
            autoplayTimer = setInterval(function() {
                slideNext();
            }, slideDuration);
        }

        function stopAutoplay() {
            if (autoplayTimer) {
                clearInterval(autoplayTimer);
                autoplayTimer = null;
            }
        }

        // Arrow navigation clicks (namespaced so destroy() can unbind exactly these)
        $('#mfSlideNext').on('click.mfSlider', function(e) {
            e.preventDefault();
            slideNext();
            startAutoplay();
        });

        $('#mfSlidePrev').on('click.mfSlider', function(e) {
            e.preventDefault();
            slidePrev();
            startAutoplay();
        });

        // Dot navigation clicks
        $dotsContainer.on('click.mfSlider', '.mf-dot', function(e) {
            e.preventDefault();
            var targetIdx = parseInt($(this).data('slide'), 10);
            if (!isNaN(targetIdx)) {
                goToSlide(targetIdx);
            }
        });

        // Touch swipe support for mobile & tablet
        var touchStartX = 0;
        var touchEndX = 0;

        function onTouchStart(e) {
            touchStartX = e.changedTouches[0].screenX;
        }
        function onTouchEnd(e) {
            touchEndX = e.changedTouches[0].screenX;
            var swipeThreshold = 40;
            if (touchStartX - touchEndX > swipeThreshold) {
                slideNext();
                startAutoplay();
            } else if (touchEndX - touchStartX > swipeThreshold) {
                slidePrev();
                startAutoplay();
            }
        }
        sliderWrapper.addEventListener('touchstart', onTouchStart, { passive: true });
        sliderWrapper.addEventListener('touchend', onTouchEnd, { passive: true });

        // Page Visibility API — Resume when tab is active
        function onVisibilityChange() {
            if (document.hidden) {
                stopAutoplay();
            } else {
                startAutoplay();
            }
        }
        document.addEventListener('visibilitychange', onVisibilityChange);

        // Teardown: stops timers, unbinds this instance's listeners and removes its clones so the
        // engine can be re-run against a different slide set without stale counts/positions.
        track.__mfSliderDestroy = function() {
            stopAutoplay();
            if (transitionTimeout) { clearTimeout(transitionTimeout); transitionTimeout = null; }
            track.removeEventListener('transitionend', onTrackTransitionEnd);
            sliderWrapper.removeEventListener('touchstart', onTouchStart);
            sliderWrapper.removeEventListener('touchend', onTouchEnd);
            document.removeEventListener('visibilitychange', onVisibilityChange);
            $('#mfSlideNext, #mfSlidePrev').off('.mfSlider');
            $dotsContainer.off('.mfSlider');
            Array.prototype.forEach.call(track.querySelectorAll('.mf-carousel-slide.mf-clone'), function(c) { c.parentNode.removeChild(c); });
            track.style.transition = 'none';
            track.style.transform = '';
            track.removeAttribute('data-slider-initialized');
            track.__mfSliderDestroy = null;
        };

        // Start automatic continuous slideshow immediately
        startAutoplay();
    }

    // Exposed (like initLiveDisclaimerTicker) so js/mf-slider.js can re-initialise after swapping slides
    window.initMfHeroSlider = initMfHeroSlider;

    // =========================================================================
    // LIVE MUTUAL FUND MARKET DASHBOARD & WEALTH COMPOUNDING ENGINE
    // =========================================================================
    var FUND_DATA = [
        {
            id: 0,
            name: "Large Cap Bluechip Fund",
            category: "Equity • Large Cap",
            catClass: "equity",
            baseNav: 216.35,
            currentNav: 216.35,
            oneDayReturn: "+1.08%",
            oneYearReturn: "+18.4%",
            aum: "₹42,850 Cr",
            rating: "★★★★★",
            fiveYearSipValue: "₹8,46,200",
            trajectoryTitle: "Large Cap Bluechip Fund — NAV Growth Trajectory",
            points: [100, 108, 105, 118, 126, 134, 142, 158, 172, 185, 202, 216.35]
        },
        {
            id: 1,
            name: "Flexi Cap Wealth Fund",
            category: "Equity • Flexi Cap",
            catClass: "equity",
            baseNav: 158.90,
            currentNav: 158.90,
            oneDayReturn: "+1.45%",
            oneYearReturn: "+22.6%",
            aum: "₹38,200 Cr",
            rating: "★★★★★",
            fiveYearSipValue: "₹9,18,500",
            trajectoryTitle: "Flexi Cap Wealth Fund — Multi-Cap Alpha Trajectory",
            points: [80, 86, 84, 98, 106, 114, 122, 132, 140, 148, 152, 158.90]
        },
        {
            id: 2,
            name: "Balanced Advantage Fund",
            category: "Dynamic Hybrid",
            catClass: "hybrid",
            baseNav: 94.76,
            currentNav: 94.76,
            oneDayReturn: "+0.82%",
            oneYearReturn: "+14.2%",
            aum: "₹29,100 Cr",
            rating: "★★★★★",
            fiveYearSipValue: "₹7,65,400",
            trajectoryTitle: "Balanced Advantage Fund — Dynamic Equity/Debt Allocation",
            points: [60, 63, 65, 68, 72, 75, 78, 82, 85, 88, 91, 94.76]
        },
        {
            id: 3,
            name: "Corporate Bond Debt Fund",
            category: "Debt • Short Duration",
            catClass: "debt",
            baseNav: 48.65,
            currentNav: 48.65,
            oneDayReturn: "+0.03%",
            oneYearReturn: "+7.85%",
            aum: "₹16,400 Cr",
            rating: "★★★★☆",
            fiveYearSipValue: "₹6,72,000",
            trajectoryTitle: "Corporate Bond Debt Fund — Capital Preservation & Steady Accrual",
            points: [38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 48.65]
        }
    ];

    var activeFundIndex = 0;
    var isMfVisible = true;

    // ==========================================================================
    // REALISTIC PROCEDURAL MUTUAL FUND INVESTMENT GROWTH TREE SIMULATION
    // Metaphor: Investment (Seed) -> Habit (Sprout) -> Compounding (Tree) -> Wealth (Gold Coins)
    // ==========================================================================
    function initMfInvestmentGrowthTree() {
        var canvases = document.querySelectorAll('.mf-growth-tree-canvas, #mfInvestmentTreeCanvas');
        if (canvases.length === 0) return;

        // --------------------------------------------------------------------
        // Environment capability detection (keeps things lightweight on
        // low-powered / small-screen devices, per performance requirements)
        // --------------------------------------------------------------------
        var isSmallScreen = window.innerWidth < 480;
        var isLowPower = isSmallScreen || (navigator.hardwareConcurrency && navigator.hardwareConcurrency <= 2);
        var supportsFilter = (function() {
            try {
                var c = document.createElement('canvas').getContext('2d');
                return c && ('filter' in c);
            } catch (e) { return false; }
        })();
        var useDepthBlur = supportsFilter && !isLowPower;

        // Deterministic seeded PRNG so the "organic" jitter is stable across
        // a session/reload but still varies subtly per canvas instance —
        // avoids perfectly identical, mathematically-symmetrical geometry.
        function makeRng(seed) {
            var s = seed >>> 0;
            return function() {
                s |= 0; s = (s + 0x6D2B79F5) | 0;
                var t = Math.imul(s ^ (s >>> 15), 1 | s);
                t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
                return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
            };
        }

        function clamp(v, min, max) { return Math.max(min, Math.min(max, v)); }
        function lerp(a, b, t) { return a + (b - a) * t; }
        function easeOutCubic(x) { return 1 - Math.pow(1 - x, 3); }
        function easeInOutSine(x) { return -(Math.cos(Math.PI * x) - 1) / 2; }
        function easeOutBack(x) {
            var c1 = 1.70158;
            var c3 = c1 + 1;
            return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
        }

        // --------------------------------------------------------------------
        // Procedural tree skeleton (topology unchanged — only the visual
        // treatment is upgraded). Each entry grows from its parent node.
        // --------------------------------------------------------------------
        var branchTemplate = [
            { id: 0, parent: -1, x0: 0.50, y0: 0.86, x1: 0.50, y1: 0.68, thick: 15, startGrowth: 0.24, endGrowth: 0.42, isTrunk: true },
            { id: 1, parent: 0, x0: 0.50, y0: 0.68, x1: 0.50, y1: 0.50, thick: 10.5, startGrowth: 0.34, endGrowth: 0.52, isTrunk: true },
            { id: 2, parent: 1, x0: 0.50, y0: 0.50, x1: 0.50, y1: 0.32, thick: 7, startGrowth: 0.44, endGrowth: 0.60 },

            { id: 3, parent: 1, x0: 0.50, y0: 0.64, x1: 0.36, y1: 0.50, thick: 8, startGrowth: 0.38, endGrowth: 0.56 },
            { id: 4, parent: 3, x0: 0.36, y0: 0.50, x1: 0.23, y1: 0.42, thick: 5.4, startGrowth: 0.48, endGrowth: 0.64 },
            { id: 5, parent: 3, x0: 0.36, y0: 0.50, x1: 0.31, y1: 0.33, thick: 4.4, startGrowth: 0.50, endGrowth: 0.66 },
            { id: 6, parent: 4, x0: 0.23, y0: 0.42, x1: 0.14, y1: 0.35, thick: 3.1, startGrowth: 0.56, endGrowth: 0.72 },

            { id: 7, parent: 1, x0: 0.50, y0: 0.62, x1: 0.64, y1: 0.49, thick: 8, startGrowth: 0.38, endGrowth: 0.56 },
            { id: 8, parent: 7, x0: 0.64, y0: 0.49, x1: 0.77, y1: 0.40, thick: 5.4, startGrowth: 0.48, endGrowth: 0.64 },
            { id: 9, parent: 7, x0: 0.64, y0: 0.49, x1: 0.69, y1: 0.32, thick: 4.4, startGrowth: 0.50, endGrowth: 0.66 },
            { id: 10, parent: 8, x0: 0.77, y0: 0.40, x1: 0.86, y1: 0.33, thick: 3.1, startGrowth: 0.56, endGrowth: 0.72 },

            { id: 11, parent: 2, x0: 0.50, y0: 0.40, x1: 0.41, y1: 0.23, thick: 4.7, startGrowth: 0.50, endGrowth: 0.68 },
            { id: 12, parent: 2, x0: 0.50, y0: 0.40, x1: 0.59, y1: 0.23, thick: 4.7, startGrowth: 0.50, endGrowth: 0.68 },
            { id: 13, parent: 2, x0: 0.50, y0: 0.32, x1: 0.50, y1: 0.17, thick: 3.7, startGrowth: 0.54, endGrowth: 0.70 }
        ];

        // Foliage described as clusters (a branch tip sprouts a small,
        // irregular cluster of leaves rather than one uniform leaf) —
        // expanded into individual leaves per-instance with seeded jitter.
        var leafClusterSpecs = [
            { branch: 4, tNode: 0.55, angle: -0.55, spread: 0.9, count: 3, sizeMin: 13, sizeMax: 19, start: 0.46, end: 0.62, layer: 'mid' },
            { branch: 4, tNode: 1.0, angle: -0.95, spread: 1.1, count: 4, sizeMin: 15, sizeMax: 23, start: 0.50, end: 0.66, layer: 'front' },
            { branch: 6, tNode: 0.55, angle: -0.75, spread: 0.9, count: 3, sizeMin: 14, sizeMax: 20, start: 0.58, end: 0.72, layer: 'back' },
            { branch: 6, tNode: 1.0, angle: -1.2, spread: 1.2, count: 4, sizeMin: 16, sizeMax: 24, start: 0.60, end: 0.74, layer: 'front' },
            { branch: 5, tNode: 0.65, angle: -0.3, spread: 0.8, count: 3, sizeMin: 14, sizeMax: 20, start: 0.52, end: 0.68, layer: 'front' },
            { branch: 5, tNode: 1.0, angle: -0.5, spread: 1.0, count: 3, sizeMin: 16, sizeMax: 22, start: 0.54, end: 0.70, layer: 'back' },

            { branch: 8, tNode: 0.55, angle: 0.55, spread: 0.9, count: 3, sizeMin: 13, sizeMax: 19, start: 0.46, end: 0.62, layer: 'mid' },
            { branch: 8, tNode: 1.0, angle: 0.95, spread: 1.1, count: 4, sizeMin: 15, sizeMax: 23, start: 0.50, end: 0.66, layer: 'front' },
            { branch: 10, tNode: 0.55, angle: 0.75, spread: 0.9, count: 3, sizeMin: 14, sizeMax: 20, start: 0.58, end: 0.72, layer: 'back' },
            { branch: 10, tNode: 1.0, angle: 1.2, spread: 1.2, count: 4, sizeMin: 16, sizeMax: 24, start: 0.60, end: 0.74, layer: 'front' },
            { branch: 9, tNode: 0.65, angle: 0.3, spread: 0.8, count: 3, sizeMin: 14, sizeMax: 20, start: 0.52, end: 0.68, layer: 'front' },
            { branch: 9, tNode: 1.0, angle: 0.5, spread: 1.0, count: 3, sizeMin: 16, sizeMax: 22, start: 0.54, end: 0.70, layer: 'back' },

            { branch: 11, tNode: 0.55, angle: -0.35, spread: 0.9, count: 3, sizeMin: 15, sizeMax: 21, start: 0.54, end: 0.70, layer: 'mid' },
            { branch: 11, tNode: 1.0, angle: -0.8, spread: 1.1, count: 4, sizeMin: 17, sizeMax: 25, start: 0.56, end: 0.72, layer: 'front' },
            { branch: 12, tNode: 0.55, angle: 0.35, spread: 0.9, count: 3, sizeMin: 15, sizeMax: 21, start: 0.54, end: 0.70, layer: 'mid' },
            { branch: 12, tNode: 1.0, angle: 0.8, spread: 1.1, count: 4, sizeMin: 17, sizeMax: 25, start: 0.56, end: 0.72, layer: 'front' },
            { branch: 13, tNode: 0.6, angle: -0.15, spread: 0.7, count: 3, sizeMin: 16, sizeMax: 22, start: 0.56, end: 0.72, layer: 'front' },
            { branch: 13, tNode: 1.0, angle: 0.0, spread: 0.9, count: 4, sizeMin: 18, sizeMax: 26, start: 0.58, end: 0.74, layer: 'mid' },

            { branch: 3, tNode: 0.82, angle: -0.8, spread: 0.6, count: 2, sizeMin: 14, sizeMax: 18, start: 0.44, end: 0.60, layer: 'back' },
            { branch: 7, tNode: 0.82, angle: 0.8, spread: 0.6, count: 2, sizeMin: 14, sizeMax: 18, start: 0.44, end: 0.60, layer: 'back' },
            { branch: 1, tNode: 0.62, angle: -0.9, spread: 0.5, count: 2, sizeMin: 13, sizeMax: 17, start: 0.36, end: 0.52, layer: 'back' },
            { branch: 1, tNode: 0.72, angle: 0.9, spread: 0.5, count: 2, sizeMin: 13, sizeMax: 17, start: 0.36, end: 0.52, layer: 'back' }
        ];

        var leafPalette = ['#0E9F6E', '#10B981', '#22C55E', '#34D399', '#059669'];

        // Gold coins — deliberately few, so wealth reads as an accent rather
        // than clutter (per "controlled and premium" requirement).
        var coinSpecs = [
            { branch: 6, tNode: 1.0, hangLen: 15, radius: 13, symbol: '₹', start: 0.66, end: 0.78, delay: 0.0 },
            { branch: 5, tNode: 1.0, hangLen: 17, radius: 14, symbol: '₹', start: 0.69, end: 0.81, delay: 1.4 },
            { branch: 13, tNode: 1.0, hangLen: 19, radius: 15.5, symbol: '₹', start: 0.72, end: 0.84, delay: 0.5, isCrown: true },
            { branch: 11, tNode: 0.9, hangLen: 14, radius: 12.5, symbol: '₹', start: 0.71, end: 0.83, delay: 2.1 },
            { branch: 10, tNode: 1.0, hangLen: 16, radius: 13.5, symbol: '₹', start: 0.68, end: 0.80, delay: 0.9 },
            { branch: 9, tNode: 1.0, hangLen: 17, radius: 14, symbol: '₹', start: 0.70, end: 0.82, delay: 2.6 },
            { branch: 12, tNode: 0.9, hangLen: 14, radius: 12.5, symbol: '₹', start: 0.73, end: 0.85, delay: 1.8 }
        ];

        var roots = [
            { x0: 0.50, y0: 0.86, x1: 0.42, y1: 0.92, x2: 0.34, y2: 0.97, thick: 3.4, start: 0.08, end: 0.36 },
            { x0: 0.50, y0: 0.86, x1: 0.58, y1: 0.92, x2: 0.66, y2: 0.97, thick: 3.4, start: 0.08, end: 0.36 },
            { x0: 0.50, y0: 0.88, x1: 0.47, y1: 0.94, x2: 0.44, y2: 0.98, thick: 2.4, start: 0.14, end: 0.42 },
            { x0: 0.50, y0: 0.88, x1: 0.53, y1: 0.94, x2: 0.56, y2: 0.98, thick: 2.4, start: 0.14, end: 0.42 }
        ];

        // --------------------------------------------------------------------
        // Shared drawing helpers
        // --------------------------------------------------------------------

        // A softly rounded, slightly asymmetric leaf with a gradient body,
        // a gentle midrib and a small specular highlight — reads as organic
        // rather than a stamped diagram shape.
        function drawSingleLeaf(ctx, x, y, size, colorMain, colorHi, asym) {
            if (size <= 1) return;
            asym = asym || 0;
            ctx.beginPath();
            ctx.moveTo(x, y);
            ctx.quadraticCurveTo(x - size * (0.52 + asym), y - size * 0.62, x, y - size);
            ctx.quadraticCurveTo(x + size * (0.58 - asym), y - size * 0.66, x, y);
            ctx.closePath();

            var lGrad = ctx.createLinearGradient(x - size * 0.5, y, x + size * 0.4, y - size);
            lGrad.addColorStop(0, colorMain);
            lGrad.addColorStop(1, colorHi);
            ctx.fillStyle = lGrad;
            ctx.fill();

            ctx.beginPath();
            ctx.moveTo(x, y);
            ctx.lineTo(x, y - size * 0.84);
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.28)';
            ctx.lineWidth = 1;
            ctx.stroke();

            // small specular fleck for a soft highlight, not a glossy sheen
            ctx.beginPath();
            ctx.ellipse(x - size * 0.16, y - size * 0.55, size * 0.10, size * 0.18, -0.5, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(255, 255, 255, 0.18)';
            ctx.fill();
        }

        function renderLeaf(ctx, leaf, nodePositions, T, windTime) {
            if (T < leaf.startGrowth) return;
            var lProg = clamp((T - leaf.startGrowth) / (leaf.endGrowth - leaf.startGrowth), 0, 1);
            var lEase = easeOutBack(lProg);
            var bPos = nodePositions[leaf.branch];
            if (!bPos || bPos.prog <= 0.3) return;

            var lx = lerp(bPos.pX, bPos.x, leaf.tNode);
            var ly = lerp(bPos.pY, bPos.y, leaf.tNode);
            var sway = Math.sin(windTime * leaf.swaySpeed + leaf.swayPhase) * 0.22;

            ctx.save();
            ctx.globalAlpha *= leaf.layer === 'back' ? 0.72 : 1.0;
            ctx.translate(lx, ly);
            ctx.rotate(leaf.angle + sway);
            drawSingleLeaf(ctx, 0, 0, leaf.size * lEase, leaf.color, '#6EE7B7', leaf.asym);
            ctx.restore();
        }

        var instances = [];

        canvases.forEach(function(canvas, canvasIndex) {
            var ctx = canvas.getContext('2d');
            if (!ctx) return;

            var rng = makeRng(0xA53F91 + canvasIndex * 7919);

            // Build a per-instance, gently jittered copy of the tree so the
            // silhouette is never perfectly symmetrical/mathematical.
            var branchTree = branchTemplate.map(function(b) {
                return {
                    id: b.id, parent: b.parent, thick: b.thick,
                    startGrowth: b.startGrowth, endGrowth: b.endGrowth, isTrunk: b.isTrunk,
                    x0: b.x0, y0: b.y0,
                    x1: b.x1 + (rng() - 0.5) * 0.025,
                    y1: b.y1 + (rng() - 0.5) * 0.015,
                    bend: (rng() - 0.5) * 14,
                    bark: b.isTrunk ? (function() {
                        var marks = [];
                        var n = 5 + Math.floor(rng() * 3);
                        for (var i = 0; i < n; i++) {
                            marks.push({ t: 0.15 + rng() * 0.7, side: rng() > 0.5 ? 1 : -1, len: 3 + rng() * 4, tilt: (rng() - 0.5) * 0.8 });
                        }
                        return marks;
                    })() : null
                };
            });

            var leaves = [];
            leafClusterSpecs.forEach(function(spec) {
                for (var k = 0; k < spec.count; k++) {
                    leaves.push({
                        branch: spec.branch,
                        tNode: clamp(spec.tNode + (rng() - 0.5) * 0.22, 0.05, 1),
                        angle: spec.angle + (rng() - 0.5) * spec.spread,
                        size: spec.sizeMin + rng() * (spec.sizeMax - spec.sizeMin),
                        startGrowth: spec.start + rng() * 0.025,
                        endGrowth: spec.end + rng() * 0.025,
                        color: leafPalette[Math.floor(rng() * leafPalette.length)],
                        layer: spec.layer,
                        swayPhase: rng() * Math.PI * 2,
                        swaySpeed: 0.55 + rng() * 0.7,
                        asym: (rng() - 0.5) * 0.18
                    });
                }
            });

            var particleCount = isLowPower ? 14 : 30;
            var particles = [];
            for (var p = 0; p < particleCount; p++) {
                particles.push({
                    x: rng(), y: rng(),
                    radius: 1.1 + rng() * 2.0,
                    speedY: 0.00035 + rng() * 0.0007,
                    speedX: (rng() - 0.5) * 0.0005,
                    alpha: 0.2 + rng() * 0.55,
                    pulse: rng() * Math.PI * 2
                });
            }

            // A handful of very soft, blurred foreground/background leaf
            // silhouettes for a lightweight parallax/depth cue.
            var depthLeaves = [];
            if (useDepthBlur) {
                for (var d = 0; d < 5; d++) {
                    depthLeaves.push({
                        x: rng(), y: 0.2 + rng() * 0.5,
                        size: 20 + rng() * 26,
                        layer: d % 2 === 0 ? 'bg' : 'fg',
                        drift: (rng() - 0.5) * 0.0004,
                        angle: rng() * Math.PI * 2,
                        alpha: 0.10 + rng() * 0.10
                    });
                }
            }

            var inst = {
                canvas: canvas, ctx: ctx,
                width: 0, height: 0, isVisible: true,
                branchTree: branchTree, leaves: leaves, particles: particles, depthLeaves: depthLeaves
            };

            function resize() {
                var rect = canvas.parentElement.getBoundingClientRect();
                var dpr = window.devicePixelRatio || 1;
                inst.width = rect.width || window.innerWidth;
                inst.height = rect.height || 260;
                canvas.width = inst.width * dpr;
                canvas.height = inst.height * dpr;
                ctx.resetTransform();
                ctx.scale(dpr, dpr);
            }

            window.addEventListener('resize', resize);
            resize();

            if ('IntersectionObserver' in window) {
                var observer = new IntersectionObserver(function(entries) {
                    entries.forEach(function(entry) {
                        inst.isVisible = entry.isIntersecting;
                    });
                }, { threshold: 0.05 });
                observer.observe(canvas);
            }

            instances.push(inst);
        });

        var loopDuration = 17000; // slightly longer cycle for a calmer, premium pace
        var startTime = performance.now();

        function animate(now) {
            var elapsed = now - startTime;
            var T = (elapsed % loopDuration) / loopDuration;
            var windTime = now * 0.0022;

            // Smooth recede-to-seed at the tail of the loop instead of an
            // abrupt cut: the mature tree gently fades and settles inward
            // before the next seed appears, so the cycle reads as continuous.
            var recedeStart = 0.90;
            var globalAlpha = 1.0;
            var receedeScale = 1.0;
            if (T > recedeStart) {
                var rT = easeInOutSine(clamp((T - recedeStart) / (1 - recedeStart), 0, 1));
                globalAlpha = 1.0 - rT;
                receedeScale = 1.0 - rT * 0.12;
            }

            instances.forEach(function(inst) {
                if (!inst.isVisible) return;

                var ctx = inst.ctx;
                var width = inst.width;
                var height = inst.height;
                var branchTree = inst.branchTree;
                var leaves = inst.leaves;

                ctx.clearRect(0, 0, width, height);

                // 1. Soft cinematic ambient light — warm above, cool below,
                // kept subtle so the scene stays calm rather than glowing.
                var sunGrad = ctx.createRadialGradient(width * 0.5, height * 0.08, 8, width * 0.5, height * 0.45, width * 0.6);
                sunGrad.addColorStop(0, 'rgba(251, 191, 36, 0.14)');
                sunGrad.addColorStop(0.45, 'rgba(46, 196, 182, 0.06)');
                sunGrad.addColorStop(1, 'rgba(3, 31, 28, 0)');
                ctx.fillStyle = sunGrad;
                ctx.fillRect(0, 0, width, height);

                // 2. Background depth layer — soft blurred foliage silhouettes
                if (useDepthBlur) {
                    ctx.save();
                    ctx.filter = 'blur(7px)';
                    inst.depthLeaves.forEach(function(dl) {
                        if (dl.layer !== 'bg') return;
                        dl.x += dl.drift;
                        if (dl.x < -0.1) dl.x = 1.1; if (dl.x > 1.1) dl.x = -0.1;
                        ctx.save();
                        ctx.globalAlpha = dl.alpha * globalAlpha;
                        ctx.translate(dl.x * width, dl.y * height);
                        ctx.rotate(dl.angle);
                        drawSingleLeaf(ctx, 0, 0, dl.size, '#0E6B5C', '#1E8A73', 0);
                        ctx.restore();
                    });
                    ctx.filter = 'none';
                    ctx.restore();
                }

                // 3. Floating golden motes (wealth particles)
                inst.particles.forEach(function(pt) {
                    pt.y -= pt.speedY;
                    pt.x += Math.sin(now * 0.001 + pt.pulse) * pt.speedX;
                    if (pt.y < 0.05) pt.y = 0.95;
                    if (pt.x < 0.05) pt.x = 0.95;
                    if (pt.x > 0.95) pt.x = 0.05;

                    var px = pt.x * width;
                    var py = pt.y * height;
                    var pAlpha = (pt.alpha * (0.55 + 0.4 * Math.sin(now * 0.0028 + pt.pulse))) * Math.min(1, T * 2.5) * globalAlpha;

                    ctx.beginPath();
                    ctx.arc(px, py, pt.radius, 0, Math.PI * 2);
                    ctx.fillStyle = 'rgba(251, 191, 36, ' + pAlpha + ')';
                    ctx.shadowColor = 'rgba(245, 158, 11, 0.55)';
                    ctx.shadowBlur = 5;
                    ctx.fill();
                    ctx.shadowBlur = 0;
                });

                var groundY = height * 0.88;
                var centerX = width * 0.50;
                var treeSpan = Math.min(width * 0.85, Math.max(340, height * 2.6));
                var seedGroundY = groundY - 12;

                ctx.save();
                ctx.globalAlpha = globalAlpha;
                // Gentle inward settle as the cycle recedes (point 10)
                ctx.translate(centerX, seedGroundY);
                ctx.scale(receedeScale, receedeScale);
                ctx.translate(-centerX, -seedGroundY);

                // 4. Soil bed
                ctx.beginPath();
                ctx.moveTo(0, height);
                ctx.lineTo(0, groundY + 12);
                ctx.quadraticCurveTo(width * 0.25, groundY - 6, width * 0.50, groundY - 12);
                ctx.quadraticCurveTo(width * 0.75, groundY - 6, width, groundY + 12);
                ctx.lineTo(width, height);
                ctx.closePath();
                var soilGrad = ctx.createLinearGradient(0, groundY - 12, 0, height);
                soilGrad.addColorStop(0, '#0E493F');
                soilGrad.addColorStop(0.25, '#082D27');
                soilGrad.addColorStop(1, '#021614');
                ctx.fillStyle = soilGrad;
                ctx.fill();
                ctx.strokeStyle = 'rgba(46, 196, 182, 0.30)';
                ctx.lineWidth = 1.6;
                ctx.stroke();

                // Underground roots
                if (T > 0.08) {
                    roots.forEach(function(r) {
                        if (T < r.start) return;
                        var rProg = clamp((T - r.start) / (r.end - r.start), 0, 1);
                        var re = easeOutCubic(rProg);
                        var rx0 = centerX + (r.x0 - 0.5) * treeSpan, ry0 = r.y0 * height;
                        var rx1 = centerX + (r.x1 - 0.5) * treeSpan, ry1 = r.y1 * height;
                        var rx2 = centerX + (r.x2 - 0.5) * treeSpan, ry2 = r.y2 * height;
                        var curX1 = rx0 + (rx1 - rx0) * Math.min(1, re * 1.5);
                        var curY1 = ry0 + (ry1 - ry0) * Math.min(1, re * 1.5);
                        var curX2 = curX1 + (rx2 - rx1) * Math.max(0, re - 0.5) * 2;
                        var curY2 = curY1 + (ry2 - ry1) * Math.max(0, re - 0.5) * 2;
                        ctx.beginPath();
                        ctx.moveTo(rx0, ry0);
                        ctx.quadraticCurveTo(curX1, curY1, curX2, curY2);
                        ctx.strokeStyle = 'rgba(245, 158, 11, ' + (0.32 * re) + ')';
                        ctx.lineWidth = r.thick * re;
                        ctx.lineCap = 'round';
                        ctx.stroke();
                    });
                }

                // Swaying grass
                var grassCount = isLowPower ? 10 : 16;
                for (var g = 0; g < grassCount; g++) {
                    var gx = centerX + ((g / grassCount) - 0.5) * (treeSpan * 0.75);
                    var gy = groundY - 8 + Math.sin(g * 1.2) * 4;
                    var gHeight = 9 + Math.sin(g * 2.3) * 5;
                    var gWind = Math.sin(windTime + g * 0.8) * 4;
                    ctx.beginPath();
                    ctx.moveTo(gx, gy);
                    ctx.quadraticCurveTo(gx + gWind * 0.5, gy - gHeight * 0.5, gx + gWind, gy - gHeight);
                    ctx.strokeStyle = (g % 2 === 0) ? '#10B981' : '#34D399';
                    ctx.lineWidth = 1.5;
                    ctx.stroke();
                }

                // 5. STAGE 1 — Seed (fades gently into the sprout stage rather
                // than disappearing abruptly)
                if (T <= 0.18) {
                    var seedDropProg = clamp(T / 0.10, 0, 1);
                    var seedY = (height * 0.22) + (seedGroundY - height * 0.22) * easeOutCubic(seedDropProg);
                    var seedScale = T < 0.11 ? 1.0 : clamp(1.0 - (T - 0.11) / 0.07, 0, 1);

                    if (seedScale > 0) {
                        ctx.save();
                        ctx.globalAlpha *= seedScale;
                        ctx.translate(centerX, seedY);
                        ctx.scale(seedScale, seedScale);

                        var sAura = ctx.createRadialGradient(0, 0, 2, 0, 0, 16);
                        sAura.addColorStop(0, 'rgba(251, 191, 36, 0.85)');
                        sAura.addColorStop(0.5, 'rgba(245, 158, 11, 0.35)');
                        sAura.addColorStop(1, 'rgba(245, 158, 11, 0)');
                        ctx.fillStyle = sAura;
                        ctx.beginPath();
                        ctx.arc(0, 0, 16, 0, Math.PI * 2);
                        ctx.fill();

                        ctx.beginPath();
                        ctx.ellipse(0, 0, 5.5, 8.5, Math.PI / 10, 0, Math.PI * 2);
                        var sGrad = ctx.createLinearGradient(-4, -6, 4, 6);
                        sGrad.addColorStop(0, '#FDE68A');
                        sGrad.addColorStop(0.5, '#F59E0B');
                        sGrad.addColorStop(1, '#92400E');
                        ctx.fillStyle = sGrad;
                        ctx.fill();
                        ctx.strokeStyle = '#FEF08A';
                        ctx.lineWidth = 1.2;
                        ctx.stroke();
                        ctx.restore();

                        if (seedDropProg >= 0.8) {
                            var ripProg = (seedDropProg - 0.8) / 0.2;
                            ctx.beginPath();
                            ctx.ellipse(centerX, seedGroundY + 2, ripProg * 26, ripProg * 7, 0, 0, Math.PI * 2);
                            ctx.strokeStyle = 'rgba(245, 158, 11, ' + (1 - ripProg) * 0.75 + ')';
                            ctx.lineWidth = 2;
                            ctx.stroke();
                        }
                    }
                }

                // 6. STAGE 2 — Sprout
                if (T >= 0.11 && T < 0.30) {
                    var sproutProg = clamp((T - 0.11) / 0.15, 0, 1);
                    var spEased = easeOutBack(sproutProg);
                    var spWind = Math.sin(windTime * 1.4) * 4 * spEased;
                    var spHeight = 34 * spEased;

                    ctx.beginPath();
                    ctx.moveTo(centerX, seedGroundY);
                    ctx.quadraticCurveTo(centerX + spWind * 0.5, seedGroundY - spHeight * 0.6, centerX + spWind, seedGroundY - spHeight);
                    ctx.strokeStyle = '#34D399';
                    ctx.lineWidth = 3.4;
                    ctx.lineCap = 'round';
                    ctx.stroke();

                    var leafUnfurl = clamp((sproutProg - 0.3) / 0.7, 0, 1);
                    if (leafUnfurl > 0) {
                        var tipX = centerX + spWind, tipY = seedGroundY - spHeight;
                        var leafSize = 13 * easeOutBack(leafUnfurl);
                        ctx.save();
                        ctx.translate(tipX, tipY);
                        ctx.rotate(-0.8 + Math.sin(windTime) * 0.15);
                        drawSingleLeaf(ctx, 0, 0, leafSize, '#10B981', '#34D399', 0);
                        ctx.restore();
                        ctx.save();
                        ctx.translate(tipX, tipY);
                        ctx.rotate(0.8 + Math.sin(windTime + 1) * 0.15);
                        drawSingleLeaf(ctx, 0, 0, leafSize, '#10B981', '#6EE7B7', 0);
                        ctx.restore();
                    }
                }

                // 7. STAGE 3 — Mature tree: trunk, branches, leaves, coins
                if (T >= 0.24) {
                    var nodePositions = [];

                    branchTree.forEach(function(b) {
                        var bProg = clamp((T - b.startGrowth) / (b.endGrowth - b.startGrowth), 0, 1);
                        var bEase = easeOutCubic(bProg);

                        var pX = (b.parent === -1) ? centerX : nodePositions[b.parent].x;
                        var pY = (b.parent === -1) ? seedGroundY : nodePositions[b.parent].y;

                        var targetX = centerX + (b.x1 - 0.5) * treeSpan;
                        var targetY = b.y1 * height;

                        var heightFactor = (1 - b.y1);
                        var bWind = Math.sin(windTime * 1.15 + b.id * 0.6) * (11 * heightFactor) * bEase;

                        var curX = pX + (targetX + bWind - pX) * bEase;
                        var curY = pY + (targetY - pY) * bEase;

                        nodePositions[b.id] = { x: curX, y: curY, pX: pX, pY: pY, prog: bProg, ease: bEase };

                        if (bProg > 0) {
                            // Organic curved branch with an asymmetric bend and
                            // a natural taper from base to tip.
                            var midX = (pX + curX) / 2 + (b.bend || 0) * bEase;
                            var midY = (pY + curY) / 2;
                            var segs = b.isTrunk ? 10 : 6;
                            var baseW = Math.max(1.6, b.thick * bEase);

                            ctx.beginPath();
                            var prevX = pX, prevY = pY;
                            for (var s = 1; s <= segs; s++) {
                                var st = s / segs;
                                var qx = (1 - st) * (1 - st) * pX + 2 * (1 - st) * st * midX + st * st * curX;
                                var qy = (1 - st) * (1 - st) * pY + 2 * (1 - st) * st * midY + st * st * curY;
                                var w = baseW * lerp(1.0, 0.55, st);
                                ctx.beginPath();
                                ctx.moveTo(prevX, prevY);
                                ctx.lineTo(qx, qy);
                                var segGrad = ctx.createLinearGradient(prevX, prevY, qx, qy);
                                segGrad.addColorStop(0, '#0F3D35');
                                segGrad.addColorStop(0.5, '#1E6B5D');
                                segGrad.addColorStop(1, '#2D8A77');
                                ctx.strokeStyle = segGrad;
                                ctx.lineWidth = w;
                                ctx.lineCap = 'round';
                                ctx.stroke();
                                prevX = qx; prevY = qy;
                            }

                            // Subtle bark texture on the trunk only, drawn from
                            // precomputed marks so it stays stable frame to frame.
                            if (b.bark && bProg > 0.5) {
                                var barkAlpha = clamp((bProg - 0.5) / 0.3, 0, 1) * 0.35;
                                b.bark.forEach(function(mk) {
                                    var mx = pX + (curX - pX) * mk.t;
                                    var my = pY + (curY - pY) * mk.t;
                                    ctx.save();
                                    ctx.translate(mx, my);
                                    ctx.rotate(mk.tilt);
                                    ctx.beginPath();
                                    ctx.moveTo(-mk.len * 0.5 * mk.side, 0);
                                    ctx.lineTo(mk.len * 0.5 * mk.side, mk.len * 1.4);
                                    ctx.strokeStyle = 'rgba(6, 30, 26, ' + barkAlpha + ')';
                                    ctx.lineWidth = 1;
                                    ctx.stroke();
                                    ctx.restore();
                                });
                            }
                        }
                    });

                    // Background leaves first (depth ordering), then mid/front
                    leaves.forEach(function(leaf) {
                        if (leaf.layer !== 'back') return;
                        renderLeaf(ctx, leaf, nodePositions, T, windTime);
                    });
                    leaves.forEach(function(leaf) {
                        if (leaf.layer === 'back') return;
                        renderLeaf(ctx, leaf, nodePositions, T, windTime);
                    });

                    // Gold coins — appear only once their branch/foliage has
                    // matured, with a soft scale + fade + gentle rotate-in.
                    coinSpecs.forEach(function(coin) {
                        if (T < coin.start) return;
                        var coinProg = clamp((T - coin.start) / (coin.end - coin.start), 0, 1);
                        var coinEase = easeOutBack(coinProg);
                        var bPos = nodePositions[coin.branch];
                        if (!bPos || bPos.prog <= 0.55) return;

                        var nodeX = lerp(bPos.pX, bPos.x, coin.tNode);
                        var nodeY = lerp(bPos.pY, bPos.y, coin.tNode);
                        var hangWind = Math.sin(windTime * 1.3 + coin.delay) * 4;
                        var coinX = nodeX + hangWind;
                        var coinY = nodeY + coin.hangLen * coinEase;
                        var coinR = coin.radius * coinEase;
                        if (coinR <= 1) return;

                        var appearRotate = (1 - clamp(coinProg / 0.6, 0, 1)) * 0.6;

                        ctx.beginPath();
                        ctx.moveTo(nodeX, nodeY);
                        ctx.quadraticCurveTo(nodeX + hangWind * 0.5, (nodeY + coinY) / 2, coinX, coinY - coinR);
                        ctx.strokeStyle = '#D97706';
                        ctx.lineWidth = 1.3;
                        ctx.stroke();

                        ctx.save();
                        ctx.translate(coinX, coinY);
                        ctx.rotate(appearRotate);
                        ctx.globalAlpha *= clamp(coinProg / 0.5, 0, 1);

                        ctx.beginPath();
                        ctx.ellipse(0, coinR + 3, coinR * 0.72, coinR * 0.22, 0, 0, Math.PI * 2);
                        ctx.fillStyle = 'rgba(0, 0, 0, 0.30)';
                        ctx.fill();

                        ctx.beginPath();
                        ctx.arc(0, 0, coinR, 0, Math.PI * 2);
                        var rimGrad = ctx.createLinearGradient(-coinR, -coinR, coinR, coinR);
                        rimGrad.addColorStop(0, '#FFFBEB');
                        rimGrad.addColorStop(0.3, '#F59E0B');
                        rimGrad.addColorStop(0.7, '#D97706');
                        rimGrad.addColorStop(1, '#78350F');
                        ctx.fillStyle = rimGrad;
                        ctx.shadowColor = 'rgba(245, 158, 11, 0.45)';
                        ctx.shadowBlur = 6;
                        ctx.fill();
                        ctx.shadowBlur = 0;

                        ctx.beginPath();
                        ctx.arc(0, 0, coinR * 0.80, 0, Math.PI * 2);
                        var innerGrad = ctx.createRadialGradient(0, 0, 1, 0, 0, coinR * 0.80);
                        innerGrad.addColorStop(0, '#FEF08A');
                        innerGrad.addColorStop(0.7, '#F59E0B');
                        innerGrad.addColorStop(1, '#B45309');
                        ctx.fillStyle = innerGrad;
                        ctx.fill();
                        ctx.strokeStyle = 'rgba(255, 255, 255, 0.45)';
                        ctx.lineWidth = 1;
                        ctx.stroke();

                        ctx.fillStyle = '#78350F';
                        ctx.font = 'bold ' + Math.round(coinR * 0.9) + 'px Inter, sans-serif';
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.fillText(coin.symbol, 0.5, 0.5);
                        ctx.fillStyle = '#FFFBEB';
                        ctx.fillText(coin.symbol, 0, 0);

                        // one quiet glint per coin cycle — no continuous spin
                        var glintPhase = (now * 0.0022 + coin.delay) % (Math.PI * 2);
                        var glintAlpha = Math.max(0, Math.sin(glintPhase) - 0.72) * 3.4;
                        if (glintAlpha > 0.05 && coinProg >= 0.85) {
                            ctx.save();
                            ctx.translate(coinR * 0.45, -coinR * 0.45);
                            ctx.fillStyle = 'rgba(255, 255, 255, ' + glintAlpha + ')';
                            var starSize = 4 + glintAlpha * 3.5;
                            ctx.beginPath();
                            ctx.moveTo(0, -starSize);
                            ctx.lineTo(starSize * 0.25, -starSize * 0.25);
                            ctx.lineTo(starSize, 0);
                            ctx.lineTo(starSize * 0.25, starSize * 0.25);
                            ctx.lineTo(0, starSize);
                            ctx.lineTo(-starSize * 0.25, starSize * 0.25);
                            ctx.lineTo(-starSize, 0);
                            ctx.lineTo(-starSize * 0.25, -starSize * 0.25);
                            ctx.closePath();
                            ctx.fill();
                            ctx.restore();
                        }

                        ctx.restore();
                    });
                }

                ctx.restore();

                // 8. Foreground depth layer — a couple of soft, blurred, drifting
                // leaf silhouettes close to camera for a lightweight parallax cue
                if (useDepthBlur) {
                    ctx.save();
                    ctx.filter = 'blur(4px)';
                    inst.depthLeaves.forEach(function(dl) {
                        if (dl.layer !== 'fg') return;
                        dl.x += dl.drift * 1.6;
                        if (dl.x < -0.15) dl.x = 1.15; if (dl.x > 1.15) dl.x = -0.15;
                        ctx.save();
                        ctx.globalAlpha = (dl.alpha + 0.05) * globalAlpha;
                        ctx.translate(dl.x * width, (dl.y * 0.6 + 0.55) * height);
                        ctx.rotate(dl.angle + windTime * 0.1);
                        drawSingleLeaf(ctx, 0, 0, dl.size * 0.8, '#052E27', '#0E6B5C', 0);
                        ctx.restore();
                    });
                    ctx.filter = 'none';
                    ctx.restore();
                }
            });

            requestAnimationFrame(animate);
        }

        requestAnimationFrame(animate);
    }

    // Ambient Background Wealth Compounding Wave (for Breadcrumbs & CTAs)
    function initMfBackgroundWaves() {
        var canvases = document.querySelectorAll('.mf-live-wave-canvas');
        if (canvases.length === 0) return;

        var waveInstances = [];

        canvases.forEach(function(canvas) {
            var ctx = canvas.getContext('2d');
            var width, height;

            function resize() {
                var rect = canvas.parentElement.getBoundingClientRect();
                var dpr = window.devicePixelRatio || 1;
                width = rect.width || window.innerWidth;
                height = rect.height || 220;
                canvas.width = width * dpr;
                canvas.height = height * dpr;
                ctx.resetTransform();
                ctx.scale(dpr, dpr);
            }

            window.addEventListener('resize', resize);
            resize();

            waveInstances.push({ canvas: canvas, ctx: ctx, getWidth: function() { return width; }, getHeight: function() { return height; } });
        });

        var phase = 0;
        function renderWaves() {
            phase += 0.018;

            waveInstances.forEach(function(inst) {
                var ctx = inst.ctx;
                var width = inst.getWidth();
                var height = inst.getHeight();

                ctx.clearRect(0, 0, width, height);

                // Draw 2 Layered Flowing Wealth Compounding Curves
                var waves = [
                    { speed: 1.0, amp: 26, freq: 0.003, color: 'rgba(46, 196, 182, 0.22)', yOffset: height * 0.65 },
                    { speed: 1.4, amp: 18, freq: 0.0045, color: 'rgba(15, 159, 144, 0.16)', yOffset: height * 0.75 }
                ];

                waves.forEach(function(w) {
                    ctx.beginPath();
                    ctx.moveTo(0, height);
                    ctx.lineTo(0, w.yOffset + Math.sin(phase * w.speed) * w.amp);

                    for (var x = 0; x <= width; x += 15) {
                        var y = w.yOffset + Math.sin(x * w.freq + phase * w.speed) * w.amp + (x / width) * -25;
                        ctx.lineTo(x, y);
                    }

                    ctx.lineTo(width, height);
                    ctx.closePath();
                    ctx.fillStyle = w.color;
                    ctx.fill();
                });
            });

            requestAnimationFrame(renderWaves);
        }

        renderWaves();
    }

    // Initialize all modules on DOM ready
    $(document).ready(function() {
        calculatePositionSize();
        calculateCompoundWealth();
        calculateSWPCashflow();
        initWebinarCountdown();
        initLiveDisclaimerTicker();
        // js/mf-slider.js (loaded before this file) may still be fetching admin-managed slide
        // images; wait for it so the carousel initializes with the final slide list. If that
        // script is missing or its fetch fails, mfSliderReady is undefined/resolves harmlessly
        // and the existing hardcoded slides in home.html are used exactly as before.
        if (window.mfSliderReady && typeof window.mfSliderReady.then === "function") {
            window.mfSliderReady.then(initMfHeroSlider);
        } else {
            initMfHeroSlider();
        }
        initMfInvestmentGrowthTree();
        initMfBackgroundWaves();
    });

})(jQuery);
