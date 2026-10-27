/* SWP page — "Real SWP journeys" case-study charts (swp.html #swp-case-studies).
   Data: js/swp-case-data.js (row-for-row from the source PDFs in docs/).
   Draws one responsive SVG line chart per case: corpus value, cumulative amount
   withdrawn, and the amount invested as a dashed reference line. Crosshair
   tooltip on hover / touch / arrow keys, a whole-journey vs first-5-years toggle,
   and a year-by-year table as the accessible view of the same numbers. */
(function () {
    "use strict";

    var DATA = window.LSI_SWP_CASE_DATA;
    var root = document.getElementById("swp-case-studies");
    if (!DATA || !root) return;

    var CASES = {
        sbi: { invested: 10000000, monthly: 50000 },
        bandhan: { invested: 10000000, monthly: 66667 },
        dsp: { invested: 5000000, monthly: 25000 }
    };
    var COLORS = { corpus: "#0284C7", withdrawn: "#EB6834", ref: "#94A3B8", grid: "#E2E8F0", axis: "#64748B", ink: "#0F172A" };
    var MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    var SVGNS = "http://www.w3.org/2000/svg";

    function parseDate(s) { var p = s.split("-"); return new Date(+p[0], +p[1] - 1, +p[2]); }
    function fmtDate(d) { return d.getDate() + " " + MONTHS[d.getMonth()] + " " + d.getFullYear(); }
    function fmtMonth(d) { return MONTHS[d.getMonth()] + " " + d.getFullYear(); }
    function fmtFull(v) { return "₹" + Math.round(v).toLocaleString("en-IN"); }
    function fmtShort(v) {
        if (v >= 1e7) return "₹" + (v / 1e7).toFixed(2) + " Cr";
        if (v >= 1e5) return "₹" + (v / 1e5).toFixed(1) + " L";
        return fmtFull(v);
    }
    function fmtTick(v) {
        if (v === 0) return "0";
        var n = v >= 1e7 ? v / 1e7 : v / 1e5;
        return "₹" + (Math.round(n * 100) / 100) + (v >= 1e7 ? " Cr" : " L");
    }
    function niceStep(max, count) {
        var raw = max / count, mag = Math.pow(10, Math.floor(Math.log(raw) / Math.LN10)), n = raw / mag;
        return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * mag;
    }
    function el(name, attrs, parent) {
        var node = document.createElementNS(SVGNS, name);
        for (var k in attrs) node.setAttribute(k, attrs[k]);
        if (parent) parent.appendChild(node);
        return node;
    }
    function label(parent, x, y, text, opts) {
        opts = opts || {};
        var t = el("text", {
            x: x, y: y, "text-anchor": opts.anchor || "start",
            "font-size": opts.size || 11, "font-weight": opts.weight || 500,
            fill: opts.fill || COLORS.axis
        }, parent);
        if (opts.halo) {
            t.setAttribute("stroke", "#fff"); t.setAttribute("stroke-width", 4);
            t.setAttribute("stroke-linejoin", "round"); t.setAttribute("paint-order", "stroke");
        }
        t.textContent = text;
        return t;
    }

    var rowsCache = {};
    function rowsFor(key) {
        if (!rowsCache[key]) {
            rowsCache[key] = DATA[key].map(function (r) { return { t: parseDate(r[0]), v: r[1], w: r[2] }; });
        }
        return rowsCache[key];
    }

    function render(panel) {
        var key = panel.getAttribute("data-case");
        var box = panel.querySelector(".swpcase-chart");
        if (!box || !DATA[key]) return;
        var width = Math.floor(box.clientWidth);
        if (width < 120) return;                       // hidden tab: render once it is shown
        if (box._lastWidth === width && box._lastRange === panel._range) return;
        box._lastWidth = width; box._lastRange = panel._range;

        var all = rowsFor(key), cfg = CASES[key];
        var rows = all;
        if (panel._range === "5y") {
            var limit = new Date(all[0].t); limit.setFullYear(limit.getFullYear() + 5);
            rows = all.filter(function (r) { return r.t <= limit; });
        }

        var narrow = width < 480;
        var height = Math.round(Math.max(230, Math.min(340, width * 0.56)));
        var m = { l: narrow ? 52 : 62, r: 12, t: 16, b: 28 };
        var pw = width - m.l - m.r, ph = height - m.t - m.b;
        var t0 = rows[0].t.getTime(), t1 = rows[rows.length - 1].t.getTime();
        var ymax = cfg.invested;
        rows.forEach(function (r) { ymax = Math.max(ymax, r.v, r.w); });
        var step = niceStep(ymax, 5), ytop = Math.ceil(ymax * 1.04 / step) * step;
        var X = function (t) { return m.l + (t - t0) / (t1 - t0) * pw; };
        var Y = function (v) { return m.t + ph - v / ytop * ph; };

        box.innerHTML = "";
        var svg = el("svg", {
            viewBox: "0 0 " + width + " " + height, width: width, height: height,
            role: "img", tabindex: "0", "aria-label": panel.getAttribute("data-summary") || ""
        }, box);

        // Grid + y ticks
        var g = el("g", {}, svg);
        for (var v = 0; v <= ytop + 1; v += step) {
            el("line", { x1: m.l, x2: width - m.r, y1: Y(v), y2: Y(v), stroke: COLORS.grid, "stroke-width": 1, "shape-rendering": "crispEdges" }, g);
            label(g, m.l - 8, Y(v) + 4, fmtTick(v), { anchor: "end" });
        }
        // x ticks (years)
        var y0 = rows[0].t.getFullYear(), y1 = rows[rows.length - 1].t.getFullYear();
        var maxLabels = Math.max(2, Math.floor(pw / (narrow ? 58 : 70)));
        var ystep = [1, 2, 5, 10].filter(function (s) { return (y1 - y0) / s <= maxLabels; })[0] || 10;
        for (var yr = Math.ceil((y0 + 1) / ystep) * ystep; yr <= y1; yr += ystep) {
            var tx = X(new Date(yr, 0, 1).getTime());
            if (tx < m.l + 14 || tx > width - m.r - 14) continue;
            el("line", { x1: tx, x2: tx, y1: m.t + ph, y2: m.t + ph + 4, stroke: COLORS.grid }, g);
            label(g, tx, height - 8, String(yr), { anchor: "middle" });
        }

        // Invested reference (dashed = threshold, not a gridline)
        el("line", { x1: m.l, x2: width - m.r, y1: Y(cfg.invested), y2: Y(cfg.invested), stroke: COLORS.ref, "stroke-width": 1.25, "stroke-dasharray": "5 4" }, svg);

        // Corpus area wash + lines
        var pts = rows.map(function (r) { return X(r.t.getTime()).toFixed(1) + "," + Y(r.v).toFixed(1); });
        el("path", { d: "M" + X(t0) + "," + Y(0) + "L" + pts.join("L") + "L" + X(t1) + "," + Y(0) + "Z", fill: COLORS.corpus, "fill-opacity": 0.08 }, svg);
        var wpts = rows.map(function (r) { return X(r.t.getTime()).toFixed(1) + "," + Y(r.w).toFixed(1); });
        el("path", { d: "M" + wpts.join("L"), fill: "none", stroke: COLORS.withdrawn, "stroke-width": 2, "stroke-linejoin": "round", "stroke-linecap": "round" }, svg);
        el("path", { d: "M" + pts.join("L"), fill: "none", stroke: COLORS.corpus, "stroke-width": 2, "stroke-linejoin": "round", "stroke-linecap": "round" }, svg);

        // Direct labels are measured and placed where they clear both lines, the
        // plot edges and each other; a label with no clean spot is left to the
        // tooltip, legend and facts panel rather than drawn over a line.
        var lines = [
            rows.map(function (r) { return [X(r.t.getTime()), Y(r.v)]; }),
            rows.map(function (r) { return [X(r.t.getTime()), Y(r.w)]; })
        ];
        var placed = [];
        function clear(bb) {
            if (bb.x < m.l + 2 || bb.x + bb.width > width - 2 || bb.y < 0 || bb.y + bb.height > m.t + ph - 2) return false;
            var x0 = bb.x - 2, x1 = bb.x + bb.width + 2, yTop = bb.y - 2, yBot = bb.y + bb.height + 2;
            for (var li = 0; li < lines.length; li++) {
                var line = lines[li];
                for (var i = 0; i < line.length - 1; i++) {
                    var a = line[i], b = line[i + 1];
                    if (b[0] < x0 || a[0] > x1) continue;
                    if (Math.max(a[1], b[1]) >= yTop && Math.min(a[1], b[1]) <= yBot) return false;
                }
            }
            return placed.every(function (p) {
                return bb.x > p.x + p.width + 4 || bb.x + bb.width < p.x - 4 || bb.y > p.y + p.height + 2 || bb.y + bb.height < p.y - 2;
            });
        }
        function place(text, opts, spots, required) {
            var t = label(svg, 0, 0, text, opts), fallback = null;
            for (var i = 0; i < spots.length; i++) {
                t.setAttribute("x", spots[i][0]); t.setAttribute("y", spots[i][1]); t.setAttribute("text-anchor", spots[i][2]);
                var bb = t.getBBox();
                if (clear(bb)) { placed.push(bb); return t; }
                if (!fallback && bb.y >= 0 && bb.x >= m.l && bb.x + bb.width <= width) fallback = spots[i];
            }
            if (required && fallback) {
                t.setAttribute("x", fallback[0]); t.setAttribute("y", fallback[1]); t.setAttribute("text-anchor", fallback[2]);
                placed.push(t.getBBox());
                return t;
            }
            svg.removeChild(t);
            return null;
        }

        // End dots + end labels (corpus value always shown; withdrawn when it fits)
        var last = rows[rows.length - 1], ex = X(t1), eyc = Y(last.v), eyw = Y(last.w);
        el("circle", { cx: ex, cy: eyw, r: 4, fill: COLORS.withdrawn, stroke: "#fff", "stroke-width": 2 }, svg);
        el("circle", { cx: ex, cy: eyc, r: 4, fill: COLORS.corpus, stroke: "#fff", "stroke-width": 2 }, svg);
        place(fmtShort(last.v), { fill: COLORS.ink, weight: 700, size: narrow ? 11 : 12, halo: true },
            [[ex - 8, eyc - 10, "end"], [ex - 8, eyc + 19, "end"], [ex - 14, eyc + 4, "end"]], true);
        place(fmtShort(last.w) + " withdrawn", { fill: COLORS.ink, weight: 600, size: narrow ? 10.5 : 11.5, halo: true },
            [[ex - 8, eyw - 9, "end"], [ex - 8, eyw + 18, "end"]], false);

        // Lowest point — marked only when the corpus fell below the amount invested
        var low = rows[1] || rows[0];
        rows.slice(1).forEach(function (r) { if (r.v < low.v) low = r; });
        if (low.v < cfg.invested) {
            var lx = X(low.t.getTime()), ly = Y(low.v);
            el("circle", { cx: lx, cy: ly, r: 4.5, fill: COLORS.corpus, stroke: "#fff", "stroke-width": 2 }, svg);
            var lowText = "Lowest " + fmtShort(low.v) + " · " + fmtMonth(low.t);
            var lowOpts = { fill: COLORS.ink, weight: 600, size: narrow ? 10.5 : 11.5, halo: true };
            if (!place(lowText, lowOpts, [[lx + 9, ly + 18, "start"], [lx - 9, ly + 18, "end"], [lx, ly + 20, "middle"]], false)) {
                place("Lowest " + fmtShort(low.v), lowOpts, [[lx + 9, ly + 18, "start"], [lx - 9, ly + 18, "end"], [lx, ly + 20, "middle"]], false);
            }
        }

        // Hover / touch / keyboard layer
        var hover = el("g", { "pointer-events": "none", visibility: "hidden" }, svg);
        var cross = el("line", { y1: m.t, y2: m.t + ph, stroke: COLORS.ref, "stroke-width": 1 }, hover);
        var dotW = el("circle", { r: 4.5, fill: COLORS.withdrawn, stroke: "#fff", "stroke-width": 2 }, hover);
        var dotC = el("circle", { r: 4.5, fill: COLORS.corpus, stroke: "#fff", "stroke-width": 2 }, hover);
        var hit = el("rect", { x: m.l, y: m.t, width: pw, height: ph, fill: "transparent" }, svg);

        var tip = document.createElement("div");
        tip.className = "swpcase-tip";
        tip.setAttribute("aria-hidden", "true");
        box.appendChild(tip);
        var live = panel.querySelector(".swpcase-live");
        var current = rows.length - 1;

        function tipRow(color, value, text) {
            var row = document.createElement("div"); row.className = "swpcase-tip-row";
            var key = document.createElement("span"); key.className = "swpcase-key"; key.style.borderColor = color;
            var strong = document.createElement("strong"); strong.textContent = value;
            var span = document.createElement("span"); span.textContent = text;
            row.appendChild(key); row.appendChild(strong); row.appendChild(span);
            return row;
        }
        function show(i) {
            current = Math.max(0, Math.min(rows.length - 1, i));
            var r = rows[current], x = X(r.t.getTime());
            cross.setAttribute("x1", x); cross.setAttribute("x2", x);
            dotC.setAttribute("cx", x); dotC.setAttribute("cy", Y(r.v));
            dotW.setAttribute("cx", x); dotW.setAttribute("cy", Y(r.w));
            hover.setAttribute("visibility", "visible");
            tip.textContent = "";
            var d = document.createElement("div"); d.className = "swpcase-tip-date"; d.textContent = fmtDate(r.t);
            tip.appendChild(d);
            tip.appendChild(tipRow(COLORS.corpus, fmtShort(r.v), "corpus value"));
            tip.appendChild(tipRow(COLORS.withdrawn, fmtShort(r.w), "withdrawn so far"));
            tip.classList.add("show");
            var tw = tip.offsetWidth, left = x + 14;
            if (left + tw > width) left = x - tw - 14;
            tip.style.left = Math.max(0, left) + "px";
            if (live) live.textContent = fmtDate(r.t) + ": corpus " + fmtFull(r.v) + ", withdrawn so far " + fmtFull(r.w);
        }
        function hide() { hover.setAttribute("visibility", "hidden"); tip.classList.remove("show"); }
        function indexAt(clientX) {
            var rect = svg.getBoundingClientRect();
            var t = t0 + ((clientX - rect.left) * (width / rect.width) - m.l) / pw * (t1 - t0);
            var lo = 0, hi = rows.length - 1;
            while (hi - lo > 1) { var mid = (lo + hi) >> 1; if (rows[mid].t.getTime() < t) lo = mid; else hi = mid; }
            return Math.abs(rows[lo].t.getTime() - t) <= Math.abs(rows[hi].t.getTime() - t) ? lo : hi;
        }
        hit.addEventListener("pointermove", function (e) { show(indexAt(e.clientX)); });
        hit.addEventListener("pointerdown", function (e) { show(indexAt(e.clientX)); });
        hit.addEventListener("pointerleave", function (e) { if (e.pointerType === "mouse") hide(); });
        svg.addEventListener("focus", function () { show(current); });
        svg.addEventListener("blur", hide);
        svg.addEventListener("keydown", function (e) {
            var jump = e.shiftKey ? 12 : 1;
            if (e.key === "ArrowRight") show(current + jump);
            else if (e.key === "ArrowLeft") show(current - jump);
            else if (e.key === "Home") show(0);
            else if (e.key === "End") show(rows.length - 1);
            else if (e.key === "Escape") hide();
            else return;
            e.preventDefault();
        });
    }

    function buildYearTable(panel) {
        var key = panel.getAttribute("data-case"), body = panel.querySelector(".swpcase-years tbody");
        if (!body || body.children.length) return;
        var rows = rowsFor(key), inv = CASES[key].invested, byYear = {};
        rows.forEach(function (r) { byYear[r.t.getFullYear()] = r; });   // last entry of each year
        Object.keys(byYear).sort().forEach(function (y) {
            var r = byYear[y], tr = document.createElement("tr");
            var pct = (r.v / inv - 1) * 100;
            [fmtDate(r.t), fmtFull(r.v), fmtFull(r.w), (pct >= 0 ? "+" : "") + pct.toFixed(1) + "%"].forEach(function (txt, i) {
                var td = document.createElement("td"); td.textContent = txt;
                if (i > 0) td.className = "text-end";
                tr.appendChild(td);
            });
            body.appendChild(tr);
        });
    }

    var panels = Array.prototype.slice.call(root.querySelectorAll(".swpcase-panel[data-case]"));
    panels.forEach(function (panel) {
        panel._range = "all";
        buildYearTable(panel);
        Array.prototype.forEach.call(panel.querySelectorAll(".swpcase-range [data-range]"), function (btn) {
            btn.addEventListener("click", function () {
                panel._range = btn.getAttribute("data-range");
                Array.prototype.forEach.call(panel.querySelectorAll(".swpcase-range [data-range]"), function (b) {
                    var on = b === btn;
                    b.classList.toggle("active", on);
                    b.setAttribute("aria-pressed", on ? "true" : "false");
                });
                render(panel);
            });
        });
        render(panel);
    });

    // "Explore the interactive chart" links elsewhere on the page (e.g. the proof
    // cards) open the matching fund tab; the href itself scrolls to the section.
    Array.prototype.forEach.call(document.querySelectorAll("[data-swpcase-tab]"), function (link) {
        link.addEventListener("click", function () {
            var tab = document.getElementById("swpcase-tab-" + link.getAttribute("data-swpcase-tab"));
            if (tab) tab.click();
        });
    });

    // Re-render when a tab becomes visible or the layout width changes.
    if ("ResizeObserver" in window) {
        var ro = new ResizeObserver(function (entries) {
            entries.forEach(function (entry) {
                var panel = entry.target.closest(".swpcase-panel");
                if (panel) render(panel);
            });
        });
        panels.forEach(function (p) { var c = p.querySelector(".swpcase-chart"); if (c) ro.observe(c); });
    } else {
        var pending;
        window.addEventListener("resize", function () {
            cancelAnimationFrame(pending);
            pending = requestAnimationFrame(function () { panels.forEach(render); });
        });
        root.addEventListener("shown.bs.tab", function () { panels.forEach(render); });
    }
})();
