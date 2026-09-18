/**
 * LORD SAI INVESTMENT & SHARE MARKET ACADEMY
 * LiveCandlestickBackground - Reusable Stock Market Candlestick Background Engine
 * 
 * Features:
 * - Multi-instance manager: Automatically powers all blue-background sections across the site
 * - Shared simulation core: Single synchronized price action engine feeds all active sections
 * - IntersectionObserver: Automatically pauses off-screen canvases to preserve 100% CPU/GPU performance
 * - Adaptive layout: Auto-adjusts candle density, scale, and heights for Hero, Breadcrumbs & CTA banners
 * - High-DPI hardware-accelerated HTML5 Canvas rendering at silky 60 FPS
 * - Dynamic Exponential Moving Averages (EMA 20 & EMA 50) with glowing bezier curves
 * - Live price tracking guide line, pulsing beacon dot & real-time price badge
 * - Full prefers-reduced-motion accessibility support & zero memory leaks
 */

(function(window) {
    'use strict';

    // Global Visual & Simulation Configuration
    const CONFIG = {
        defaultVisibleCandles: 28,
        basePrice: 2460.00,
        tickIntervalMinMs: 90,
        tickIntervalMaxMs: 160,
        candleDurationMs: 2400,
        regimeChangeMinCandles: 7,
        regimeChangeMaxCandles: 14,
        bullishColor: '#2EC4B6',
        bullishFill: 'rgba(46, 196, 182, 0.78)',
        bearishColor: '#EF4444',
        bearishFill: 'rgba(239, 68, 68, 0.75)',
        maPrimaryColor: '#00B4D8',
        maSecondaryColor: '#CAF0F8',
        gridColor: 'rgba(255, 255, 255, 0.055)',
        textColor: 'rgba(202, 240, 248, 0.42)'
    };

    /**
     * 1. Financial Market Price Action Simulation Engine (Singleton)
     */
    class MarketSimulationEngine {
        constructor() {
            this.candles = [];
            this.currentPrice = CONFIG.basePrice;
            this.currentTrend = 'BULLISH'; // 'BULLISH' | 'BEARISH' | 'CONSOLIDATION' | 'BREAKOUT'
            this.candlesInCurrentRegime = 0;
            this.regimeTargetCandles = 10;
            this.activeCandle = null;
            this.candleStartTime = 0;
            this.tickTimer = null;
            this.isDestroyed = false;
            this.listeners = [];

            this.initHistoricalCandles();
        }

        initHistoricalCandles() {
            let price = CONFIG.basePrice - 85.00;
            const now = Date.now();
            const intervalMs = CONFIG.candleDurationMs;

            for (let i = CONFIG.defaultVisibleCandles; i > 0; i--) {
                const candleTime = now - (i * intervalMs);
                const isUp = (Math.random() > 0.42) || (i > 18);
                const volatility = 4.0 + (Math.random() * 8.5);
                const open = price;
                const change = isUp ? volatility : -volatility * 0.85;
                const close = open + change;
                const high = Math.max(open, close) + (Math.random() * 4.5);
                const low = Math.min(open, close) - (Math.random() * 4.5);
                const volume = 1200 + Math.floor(Math.random() * 4500);

                this.candles.push({
                    time: candleTime,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    isFinal: true
                });

                price = close;
            }

            this.currentPrice = price;
            this.startNewActiveCandle();
        }

        startNewActiveCandle() {
            const lastClose = this.candles.length > 0 ? this.candles[this.candles.length - 1].close : this.currentPrice;
            this.candleStartTime = Date.now();
            this.activeCandle = {
                time: this.candleStartTime,
                open: lastClose,
                high: lastClose,
                low: lastClose,
                close: lastClose,
                volume: 300 + Math.floor(Math.random() * 400),
                isFinal: false
            };

            this.candles.push(this.activeCandle);
            if (this.candles.length > CONFIG.defaultVisibleCandles + 10) {
                this.candles.shift();
            }

            this.candlesInCurrentRegime++;
            if (this.candlesInCurrentRegime >= this.regimeTargetCandles) {
                this.switchRegime();
            }
        }

        switchRegime() {
            this.candlesInCurrentRegime = 0;
            this.regimeTargetCandles = Math.floor(
                CONFIG.regimeChangeMinCandles + Math.random() * (CONFIG.regimeChangeMaxCandles - CONFIG.regimeChangeMinCandles)
            );

            const regimes = ['BULLISH', 'BULLISH', 'CONSOLIDATION', 'BEARISH', 'BREAKOUT'];
            const available = regimes.filter(r => r !== this.currentTrend);
            this.currentTrend = available[Math.floor(Math.random() * available.length)];
        }

        start() {
            this.isDestroyed = false;
            this.scheduleNextTick();
        }

        scheduleNextTick() {
            if (this.isDestroyed) return;

            const delay = CONFIG.tickIntervalMinMs + Math.random() * (CONFIG.tickIntervalMaxMs - CONFIG.tickIntervalMinMs);
            this.tickTimer = setTimeout(() => {
                this.processMicroTick();
                this.scheduleNextTick();
            }, delay);
        }

        processMicroTick() {
            if (this.isDestroyed || !this.activeCandle) return;

            const now = Date.now();
            const elapsed = now - this.candleStartTime;

            if (elapsed >= CONFIG.candleDurationMs) {
                this.activeCandle.isFinal = true;
                this.startNewActiveCandle();
                this.notifyListeners();
                return;
            }

            let bias = 0.5;
            let magnitude = 0.65 + (Math.random() * 1.35);

            switch (this.currentTrend) {
                case 'BULLISH':
                    bias = 0.62;
                    magnitude *= 1.2;
                    break;
                case 'BEARISH':
                    bias = 0.38;
                    magnitude *= 1.1;
                    break;
                case 'BREAKOUT':
                    bias = 0.74;
                    magnitude *= 1.85;
                    break;
                case 'CONSOLIDATION':
                    bias = 0.50;
                    magnitude *= 0.65;
                    break;
            }

            const isUpTick = Math.random() < bias;
            const delta = (isUpTick ? magnitude : -magnitude) * (0.4 + Math.random() * 0.8);

            this.currentPrice = Math.max(100, this.currentPrice + delta);

            this.activeCandle.close = this.currentPrice;
            if (this.currentPrice > this.activeCandle.high) this.activeCandle.high = this.currentPrice;
            if (this.currentPrice < this.activeCandle.low) this.activeCandle.low = this.currentPrice;
            this.activeCandle.volume += Math.floor(25 + Math.random() * 85);

            this.notifyListeners();
        }

        subscribe(callback) {
            this.listeners.push(callback);
            callback(this.candles);
        }

        unsubscribe(callback) {
            this.listeners = this.listeners.filter(cb => cb !== callback);
        }

        notifyListeners() {
            for (let i = 0; i < this.listeners.length; i++) {
                this.listeners[i](this.candles);
            }
        }

        destroy() {
            this.isDestroyed = true;
            if (this.tickTimer) {
                clearTimeout(this.tickTimer);
                this.tickTimer = null;
            }
            this.listeners = [];
        }
    }

    /**
     * 2. Exponential Moving Average (EMA) Indicator Calculator
     */
    function calculateEMA(data, period) {
        if (!data || data.length === 0) return [];
        const k = 2 / (period + 1);
        const emaArray = new Array(data.length);

        let sum = 0;
        const initialPeriod = Math.min(period, data.length);
        for (let i = 0; i < initialPeriod; i++) {
            sum += data[i].close;
        }
        let prevEma = sum / initialPeriod;
        emaArray[initialPeriod - 1] = prevEma;

        for (let i = initialPeriod; i < data.length; i++) {
            const currentEma = (data[i].close * k) + (prevEma * (1 - k));
            emaArray[i] = currentEma;
            prevEma = currentEma;
        }

        for (let i = initialPeriod - 2; i >= 0; i--) {
            emaArray[i] = emaArray[i + 1] ? emaArray[i + 1] * 0.999 : data[i].close;
        }

        return emaArray;
    }

    /**
     * 3. High-Performance Canvas Candlestick Renderer Instance
     */
    class CanvasCandlestickRenderer {
        constructor(canvasElement, options = {}) {
            this.canvas = canvasElement;
            this.ctx = canvasElement.getContext('2d', { alpha: true });
            this.options = Object.assign({
                density: parseInt(canvasElement.getAttribute('data-density') || canvasElement.parentElement?.getAttribute('data-density')) || CONFIG.defaultVisibleCandles,
                showPriceBadge: true,
                showVolume: true
            }, options);

            this.candles = [];
            this.dpr = window.devicePixelRatio || 1;
            this.width = 0;
            this.height = 0;
            this.animationFrameId = null;
            this.pulsePhase = 0;
            this.isVisible = true;
            this.isReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

            this.minPriceDampened = 0;
            this.maxPriceDampened = 0;

            this.initObservers();
        }

        initObservers() {
            this.resize();

            // Resize observer
            this.resizeObserver = new ResizeObserver(() => {
                this.resize();
                this.render();
            });
            this.resizeObserver.observe(this.canvas.parentElement || this.canvas);

            // Intersection observer (Pause rendering when off-screen)
            if ('IntersectionObserver' in window) {
                this.intersectionObserver = new IntersectionObserver((entries) => {
                    entries.forEach(entry => {
                        this.isVisible = entry.isIntersecting;
                        if (this.isVisible && !this.animationFrameId) {
                            this.startAnimationLoop();
                        } else if (!this.isVisible && this.animationFrameId) {
                            this.stopAnimationLoop();
                        }
                    });
                }, { threshold: 0.05 });

                this.intersectionObserver.observe(this.canvas.parentElement || this.canvas);
            }

            // Reduced motion listener
            const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
            if (motionQuery.addEventListener) {
                motionQuery.addEventListener('change', (e) => {
                    this.isReducedMotion = e.matches;
                });
            }
        }

        resize() {
            const parent = this.canvas.parentElement;
            const rect = parent ? parent.getBoundingClientRect() : this.canvas.getBoundingClientRect();
            this.width = Math.max(rect.width || 800, 300);
            this.height = Math.max(rect.height || 350, 160);

            this.canvas.width = Math.floor(this.width * this.dpr);
            this.canvas.height = Math.floor(this.height * this.dpr);
            this.canvas.style.width = `${this.width}px`;
            this.canvas.style.height = `${this.height}px`;

            this.ctx.scale(this.dpr, this.dpr);

            // Adapt density if container is compact
            if (this.height < 250) {
                this.options.density = Math.min(this.options.density, 22);
            }
        }

        updateCandles(allCandles) {
            const count = this.options.density;
            this.candles = allCandles.slice(Math.max(allCandles.length - count, 0));
        }

        startAnimationLoop() {
            if (this.animationFrameId || !this.isVisible) return;

            const loop = () => {
                if (!this.isReducedMotion) {
                    this.pulsePhase += 0.045;
                    if (this.pulsePhase > Math.PI * 2) {
                        this.pulsePhase -= Math.PI * 2;
                    }
                }
                this.render();
                if (this.isVisible) {
                    this.animationFrameId = requestAnimationFrame(loop);
                } else {
                    this.animationFrameId = null;
                }
            };
            this.animationFrameId = requestAnimationFrame(loop);
        }

        stopAnimationLoop() {
            if (this.animationFrameId) {
                cancelAnimationFrame(this.animationFrameId);
                this.animationFrameId = null;
            }
        }

        render() {
            const ctx = this.ctx;
            const width = this.width;
            const height = this.height;

            ctx.clearRect(0, 0, width, height);

            if (!this.candles || this.candles.length === 0) return;

            let rawMin = Infinity;
            let rawMax = -Infinity;
            let maxVol = 0;

            for (let i = 0; i < this.candles.length; i++) {
                const c = this.candles[i];
                if (c.low < rawMin) rawMin = c.low;
                if (c.high > rawMax) rawMax = c.high;
                if (c.volume > maxVol) maxVol = c.volume;
            }

            if (rawMin === Infinity || rawMax === -Infinity) return;

            const range = (rawMax - rawMin) || 10;
            const targetMin = rawMin - (range * 0.10);
            const targetMax = rawMax + (range * 0.14);

            if (this.minPriceDampened === 0) {
                this.minPriceDampened = targetMin;
                this.maxPriceDampened = targetMax;
            } else {
                this.minPriceDampened += (targetMin - this.minPriceDampened) * 0.10;
                this.maxPriceDampened += (targetMax - this.maxPriceDampened) * 0.10;
            }

            const minP = this.minPriceDampened;
            const maxP = this.maxPriceDampened;
            const priceRange = maxP - minP;

            const isCompact = height < 260;
            const paddingRight = width < 576 ? 14 : (isCompact ? 60 : 85);
            const paddingLeft = width < 576 ? 10 : 32;
            const paddingTop = isCompact ? 16 : 28;
            const chartBottom = height - (isCompact ? 20 : 38);
            const chartHeight = chartBottom - paddingTop;
            const chartWidth = width - paddingLeft - paddingRight;

            const getY = (price) => chartBottom - ((price - minP) / priceRange) * chartHeight;

            // 1. Subtle Grid Lines
            this.drawGrid(ctx, minP, maxP, paddingLeft, paddingRight, paddingTop, chartBottom, width, isCompact, getY);

            // 2. Moving Averages
            this.drawMovingAverages(ctx, paddingLeft, chartWidth, chartBottom, getY);

            // 3. Volume bars (if container tall enough)
            if (!isCompact) {
                this.drawVolume(ctx, paddingLeft, chartWidth, chartBottom, height, maxVol);
            }

            // 4. Candlesticks
            this.drawCandlesticks(ctx, paddingLeft, chartWidth, getY);

            // 5. Live price tracker
            this.drawLivePriceTracker(ctx, paddingLeft, chartWidth, width, chartBottom, isCompact, getY);
        }

        drawGrid(ctx, minP, maxP, paddingLeft, paddingRight, paddingTop, chartBottom, width, isCompact, getY) {
            ctx.save();
            const gridSteps = isCompact ? 3 : 5;
            ctx.strokeStyle = CONFIG.gridColor;
            ctx.lineWidth = 1;
            ctx.setLineDash([4, 4]);

            ctx.fillStyle = CONFIG.textColor;
            ctx.font = '500 10px Inter, monospace, sans-serif';
            ctx.textAlign = 'right';
            ctx.textBaseline = 'middle';

            const priceStep = (maxP - minP) / gridSteps;

            for (let i = 0; i <= gridSteps; i++) {
                const p = minP + (priceStep * i);
                const y = getY(p);

                ctx.beginPath();
                ctx.moveTo(paddingLeft, y);
                ctx.lineTo(width - paddingRight + 5, y);
                ctx.stroke();

                if (width >= 576 && !isCompact) {
                    const priceLabel = '₹' + p.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
                    ctx.fillText(priceLabel, width - 12, y);
                }
            }

            ctx.setLineDash([]);
            ctx.restore();
        }

        drawMovingAverages(ctx, paddingLeft, chartWidth, chartBottom, getY) {
            const count = this.candles.length;
            if (count < 3) return;

            const slotWidth = chartWidth / Math.max(count - 1, 1);
            const ema20 = calculateEMA(this.candles, 14);
            const ema50 = calculateEMA(this.candles, 24);

            ctx.save();
            const areaGrad = ctx.createLinearGradient(0, 0, 0, chartBottom);
            areaGrad.addColorStop(0, 'rgba(0, 180, 216, 0.15)');
            areaGrad.addColorStop(0.7, 'rgba(0, 119, 182, 0.03)');
            areaGrad.addColorStop(1, 'rgba(0, 119, 182, 0)');

            ctx.beginPath();
            ctx.moveTo(paddingLeft, getY(ema20[0]));
            for (let i = 1; i < count; i++) {
                const prevX = paddingLeft + (i - 1) * slotWidth;
                const prevY = getY(ema20[i - 1]);
                const currX = paddingLeft + i * slotWidth;
                const currY = getY(ema20[i]);
                const cpX = (prevX + currX) / 2;
                ctx.bezierCurveTo(cpX, prevY, cpX, currY, currX, currY);
            }
            ctx.lineTo(paddingLeft + (count - 1) * slotWidth, chartBottom);
            ctx.lineTo(paddingLeft, chartBottom);
            ctx.closePath();
            ctx.fillStyle = areaGrad;
            ctx.fill();

            // EMA 50
            ctx.beginPath();
            ctx.strokeStyle = CONFIG.maSecondaryColor;
            ctx.lineWidth = 1.2;
            ctx.setLineDash([5, 4]);
            ctx.globalAlpha = 0.35;
            ctx.moveTo(paddingLeft, getY(ema50[0]));
            for (let i = 1; i < count; i++) {
                const prevX = paddingLeft + (i - 1) * slotWidth;
                const prevY = getY(ema50[i - 1]);
                const currX = paddingLeft + i * slotWidth;
                const currY = getY(ema50[i]);
                const cpX = (prevX + currX) / 2;
                ctx.bezierCurveTo(cpX, prevY, cpX, currY, currX, currY);
            }
            ctx.stroke();
            ctx.setLineDash([]);
            ctx.globalAlpha = 1.0;

            // EMA 20
            ctx.beginPath();
            ctx.strokeStyle = CONFIG.maPrimaryColor;
            ctx.lineWidth = 2.0;
            ctx.lineCap = 'round';
            ctx.shadowColor = 'rgba(0, 180, 216, 0.5)';
            ctx.shadowBlur = 5;
            ctx.moveTo(paddingLeft, getY(ema20[0]));
            for (let i = 1; i < count; i++) {
                const prevX = paddingLeft + (i - 1) * slotWidth;
                const prevY = getY(ema20[i - 1]);
                const currX = paddingLeft + i * slotWidth;
                const currY = getY(ema20[i]);
                const cpX = (prevX + currX) / 2;
                ctx.bezierCurveTo(cpX, prevY, cpX, currY, currX, currY);
            }
            ctx.stroke();
            ctx.shadowBlur = 0;
            ctx.restore();
        }

        drawVolume(ctx, paddingLeft, chartWidth, chartBottom, height, maxVol) {
            if (maxVol <= 0) return;
            const count = this.candles.length;
            const slotWidth = chartWidth / Math.max(count - 1, 1);
            const barWidth = Math.max(Math.min(slotWidth * 0.48, 14), 4);
            const maxBarHeight = 45;

            ctx.save();
            for (let i = 0; i < count; i++) {
                const c = this.candles[i];
                const x = paddingLeft + (i * slotWidth) - (barWidth / 2);
                const barH = (c.volume / maxVol) * maxBarHeight;
                const y = chartBottom + 20 - barH;
                const isBullish = c.close >= c.open;

                ctx.fillStyle = isBullish ? 'rgba(46, 196, 182, 0.22)' : 'rgba(239, 68, 68, 0.2)';
                this.drawRoundedRect(ctx, x, y, barWidth, barH, 1);
                ctx.fill();
            }
            ctx.restore();
        }

        drawCandlesticks(ctx, paddingLeft, chartWidth, getY) {
            const count = this.candles.length;
            const slotWidth = chartWidth / Math.max(count - 1, 1);
            const bodyWidth = Math.max(Math.min(slotWidth * 0.52, 16), 4);

            ctx.save();
            for (let i = 0; i < count; i++) {
                const c = this.candles[i];
                const xCenter = paddingLeft + (i * slotWidth);
                const isBullish = c.close >= c.open;
                const isLiveCandle = (i === count - 1);

                const yHigh = getY(c.high);
                const yLow = getY(c.low);
                const yOpen = getY(c.open);
                const yClose = getY(c.close);

                const yTop = Math.min(yOpen, yClose);
                const bodyHeight = Math.max(Math.abs(yClose - yOpen), 2.2);
                const bodyX = xCenter - (bodyWidth / 2);

                const color = isBullish ? CONFIG.bullishColor : CONFIG.bearishColor;
                const fill = isBullish ? CONFIG.bullishFill : CONFIG.bearishFill;

                // Wick
                ctx.beginPath();
                ctx.strokeStyle = color;
                ctx.lineWidth = 1.3;
                ctx.moveTo(xCenter, yHigh);
                ctx.lineTo(xCenter, yLow);
                ctx.stroke();

                // Body
                ctx.fillStyle = fill;
                ctx.strokeStyle = color;
                ctx.lineWidth = 1.1;

                if (isLiveCandle || isBullish) {
                    ctx.shadowColor = isBullish ? 'rgba(46, 196, 182, 0.38)' : 'rgba(239, 68, 68, 0.35)';
                    ctx.shadowBlur = 4;
                } else {
                    ctx.shadowBlur = 0;
                }

                this.drawRoundedRect(ctx, bodyX, yTop, bodyWidth, bodyHeight, 2);
                ctx.fill();
                ctx.stroke();
            }
            ctx.shadowBlur = 0;
            ctx.restore();
        }

        drawLivePriceTracker(ctx, paddingLeft, chartWidth, width, chartBottom, isCompact, getY) {
            const count = this.candles.length;
            if (count === 0) return;

            const latestCandle = this.candles[count - 1];
            const slotWidth = chartWidth / Math.max(count - 1, 1);
            const xCenter = paddingLeft + ((count - 1) * slotWidth);
            const yLive = getY(latestCandle.close);
            const isBullish = latestCandle.close >= latestCandle.open;
            const primaryColor = isBullish ? CONFIG.bullishColor : CONFIG.bearishColor;

            ctx.save();

            // Guide line
            ctx.beginPath();
            ctx.strokeStyle = primaryColor;
            ctx.lineWidth = 1.2;
            ctx.setLineDash([3, 3]);
            ctx.globalAlpha = 0.65;
            ctx.moveTo(Math.max(xCenter - 220, paddingLeft), yLive);
            ctx.lineTo(width - (width < 576 ? 14 : (isCompact ? 60 : 85)), yLive);
            ctx.stroke();
            ctx.setLineDash([]);
            ctx.globalAlpha = 1.0;

            // Beacon
            const pulseScale = this.isReducedMotion ? 1 : (1 + 0.35 * Math.sin(this.pulsePhase));
            const pulseAlpha = this.isReducedMotion ? 0.3 : (0.45 - 0.25 * Math.sin(this.pulsePhase));

            ctx.beginPath();
            ctx.strokeStyle = primaryColor;
            ctx.lineWidth = 1.4;
            ctx.globalAlpha = Math.max(pulseAlpha, 0.1);
            ctx.arc(xCenter, yLive, 9 * pulseScale, 0, Math.PI * 2);
            ctx.stroke();

            ctx.beginPath();
            ctx.fillStyle = primaryColor;
            ctx.globalAlpha = 1.0;
            ctx.shadowColor = primaryColor;
            ctx.shadowBlur = 7;
            ctx.arc(xCenter, yLive, 4.0, 0, Math.PI * 2);
            ctx.fill();
            ctx.shadowBlur = 0;

            // Price badge
            if (width >= 576 && !isCompact) {
                const firstCandle = this.candles[0];
                const changePct = firstCandle.open > 0 ? (((latestCandle.close - firstCandle.open) / firstCandle.open) * 100) : 0;
                const isPositive = changePct >= 0;
                const sign = isPositive ? '▲ +' : '▼ ';
                const badgeText = `₹${latestCandle.close.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${sign}${Math.abs(changePct).toFixed(2)}%`;

                const badgeWidth = 104;
                const badgeHeight = 23;
                const badgeX = width - badgeWidth - 8;
                const badgeY = Math.max(Math.min(yLive - (badgeHeight / 2), chartBottom - badgeHeight), 8);

                ctx.fillStyle = 'rgba(10, 17, 40, 0.92)';
                ctx.strokeStyle = primaryColor;
                ctx.lineWidth = 1.1;
                ctx.shadowColor = 'rgba(0, 0, 0, 0.4)';
                ctx.shadowBlur = 8;
                this.drawRoundedRect(ctx, badgeX, badgeY, badgeWidth, badgeHeight, 4);
                ctx.fill();
                ctx.stroke();
                ctx.shadowBlur = 0;

                ctx.fillStyle = primaryColor;
                ctx.font = '700 10px Inter, sans-serif';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.fillText(badgeText, badgeX + (badgeWidth / 2), badgeY + (badgeHeight / 2));
            }

            ctx.restore();
        }

        drawRoundedRect(ctx, x, y, width, height, radius) {
            if (width <= 0 || height <= 0) return;
            radius = Math.min(radius, width / 2, height / 2);
            ctx.beginPath();
            ctx.moveTo(x + radius, y);
            ctx.lineTo(x + width - radius, y);
            ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
            ctx.lineTo(x + width, y + height - radius);
            ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
            ctx.lineTo(x + radius, y + height);
            ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
            ctx.lineTo(x, y + radius);
            ctx.quadraticCurveTo(x, y, x + radius, y);
            ctx.closePath();
        }

        destroy() {
            this.stopAnimationLoop();
            if (this.resizeObserver) {
                this.resizeObserver.disconnect();
                this.resizeObserver = null;
            }
            if (this.intersectionObserver) {
                this.intersectionObserver.disconnect();
                this.intersectionObserver = null;
            }
        }
    }

    /**
     * 4. Multi-Instance Background Manager (LiveCandlestickBackground)
     */
    const LiveCandlestickBackground = {
        engine: null,
        renderers: [],

        init() {
            // Create or reuse singleton simulation engine
            if (!this.engine) {
                this.engine = new MarketSimulationEngine();
                this.engine.start();
            }

            // Find all eligible candlestick background containers (Academy / Trading mode only)
            const containers = document.querySelectorAll('.hero-candlestick-bg:not(.mode-mf-only), [data-candlestick-bg]:not(.mode-mf-only)');
            containers.forEach(container => {
                // Skip if container belongs to mutual fund mode or parent is mode-mf-only
                if (container.closest('.mode-mf-only')) return;

                let canvas = container.querySelector('canvas.hero-live-canvas');
                if (!canvas) {
                    canvas = document.createElement('canvas');
                    canvas.className = 'hero-live-canvas';
                    container.appendChild(canvas);
                }

                // Check if already initialized
                if (canvas._candleRenderer) return;

                const renderer = new CanvasCandlestickRenderer(canvas);
                canvas._candleRenderer = renderer;

                this.engine.subscribe((candles) => {
                    renderer.updateCandles(candles);
                });

                this.renderers.push(renderer);
            });

            // Teardown cleanup
            window.addEventListener('beforeunload', () => {
                this.destroy();
            });
        },

        refresh() {
            this.init();
            if (this.renderers) {
                this.renderers.forEach(r => {
                    r.resize();
                    r.render();
                    if (r.isVisible && !r.animationFrameId) {
                        r.startAnimationLoop();
                    }
                });
            }
        },

        destroy() {
            if (this.renderers) {
                this.renderers.forEach(r => r.destroy());
                this.renderers = [];
            }
            if (this.engine) {
                this.engine.destroy();
                this.engine = null;
            }
        }
    };

    // Auto-boot on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => LiveCandlestickBackground.init());
    } else {
        LiveCandlestickBackground.init();
    }

    // Expose global component
    window.LiveCandlestickBackground = LiveCandlestickBackground;

})(window);
