/* ============================================================
   charts.js — Gráfico de barras semáforo en Canvas (sin
   librerías externas).

   Dibuja los precios del día como barras coloreadas según el
   semáforo (GREEN/AMBER/RED) con gradiente, eje X de horas,
   eje Y de €/kWh, y tooltip con hora local + precio al hacer
   hover.

   Decisiones de diseño:
   - Canvas + devicePixelRatio para nitidez en pantallas retina.
   - Hit-testing manual sobre las barras para el tooltip (no se
     usa ninguna librería).
   - El tooltip es un div HTML posicionado absolutamente sobre el
     canvas (más accesible y sencillo de estilar que dibujar texto
     dentro del canvas).
   ============================================================ */

window.WattWise = window.WattWise || {};

(function (WW) {
  'use strict';

  var $ = window.jQuery;

  var SEMAPHORE_COLORS = {
    GREEN: { base: '#34d399', top: '#10b981' },
    AMBER: { base: '#fbbf24', top: '#d97706' },
    RED:   { base: '#f87171', top: '#dc2626' }
  };

  // Precio de reserva si todos fueran 0 (evita división por cero).
  var EPS = 0.001;

  // Fuente de las etiquetas del eje (heredada o fallback seguro).
  function labelFont(size) {
    var family = 'sans-serif';
    try {
      family = window.getComputedStyle(document.body).fontFamily || family;
    } catch (e) { /* entorno sin getComputedStyle (tests/SSR) */ }
    return size + 'px ' + family;
  }

  /**
   * Dibuja el gráfico.
   * @param {object} opts
   *   canvas  — <canvas> element (o id)
   *   points  — array de { timestamp(iso), price, color }
   *   tooltipEl — div usado como tooltip
   *   animate — si dibuja con animación de aparición
   */
  WW.drawPriceChart = function (opts) {
    var canvas = typeof opts.canvas === 'string'
      ? document.getElementById(opts.canvas) : opts.canvas;
    if (!canvas) { return; }

    var points = opts.points || [];
    var animate = opts.animate !== false;
    var ctx = canvas.getContext('2d');

    // Limpiar tooltip
    var $tip = opts.tooltipEl ? $(opts.tooltipEl) : null;
    if ($tip) { $tip.removeClass('visible'); }

    // Resolución física (retina) frente a CSS.
    var rect = canvas.getBoundingClientRect();
    var dpr = window.devicePixelRatio || 1;
    // Ancho real: respetamos el ancho establecido por CSS.
    var drawW = rect.width || canvas.width;
    var drawH = (opts.height || 320) * dpr;

    // Ajustar el buffer del canvas a la resolución física.
    canvas.width = Math.round(drawW * dpr);
    canvas.height = Math.round((opts.height || 320) * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    var W = drawW;
    var H = opts.height || 320;

    // --- Márgenes para ejes y etiquetas ---
    var pad = { top: 16, right: 14, bottom: 34, left: 52 };

    if (!points.length) {
      ctx.clearRect(0, 0, W, H);
      ctx.font = '13px ' + ctx.font;
      ctx.fillStyle = '#64748b';
      ctx.textAlign = 'center';
      ctx.fillText('Sin datos de precios', W / 2, H / 2);
      return;
    }

    // Precio de referencia para la escala del eje Y (máximo).
    var maxPrice = Math.max.apply(null, points.map(function (p) { return p.price; }));
    if (!isFinite(maxPrice)) { maxPrice = 0; }
    // Dejar un 8% de aire arriba y nunca menos del EPS.
    var yMax = Math.max(maxPrice * 1.08, 0.1);

    var plotW = W - pad.left - pad.right;
    var plotH = H - pad.top - pad.bottom;
    var slotW = plotW / points.length;

    function xOf(i) {
      return pad.left + i * slotW;
    }
    function yOf(price) {
      return pad.top + plotH - (price / yMax) * plotH;
    }

    // Padding lateral dentro de cada slot para que las barras no se toquen.
    var barInset = Math.max(1, slotW * 0.18);

    /* ---- Parte estática: ejes, retícula y etiquetas ---- */
    drawStaticFrame();

    function drawStaticFrame() {
      /* Eje Y */
      ctx.strokeStyle = '#334155';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(pad.left, pad.top);
      ctx.lineTo(pad.left, pad.top + plotH);
      ctx.stroke();

      // Reticula + etiquetas de precio (5 pasos).
      var ySteps = 5;
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      ctx.font = labelFont(10);
      ctx.fillStyle = '#94a3b8';
      ctx.strokeStyle = 'rgba(51, 65, 85, 0.5)';
      ctx.lineWidth = 1;
      for (var s = 0; s <= ySteps; s++) {
        var frac = s / ySteps;
        var val = yMax * frac;
        var yy = pad.top + plotH - frac * plotH;
        ctx.beginPath();
        ctx.moveTo(pad.left, yy);
        ctx.lineTo(pad.left + plotW, yy);
        ctx.stroke();
        ctx.fillText(Number(val).toFixed(2) + ' €', pad.left - 8, yy);
      }

      /* Eje X (horas: 00, 06, 12, 18, 23) */
      ctx.strokeStyle = '#334155';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(pad.left, pad.top + plotH);
      ctx.lineTo(pad.left + plotW, pad.top + plotH);
      ctx.stroke();

      ctx.textBaseline = 'top';
      ctx.textAlign = 'center';
      var n = points.length;
      // Los puntos suelen ser cada 15 min (96) o cada hora (24).
      var step = n > 24 ? 4 : 1;
      for (var idx = 0; idx < n; idx += step) {
        var p = points[idx];
        var hh = WW.formatLocalHour(p.timestamp).slice(0, 2); // 'HH'
        ctx.fillStyle = '#64748b';
        ctx.fillText(hh, xOf(idx) + slotW / 2, pad.top + plotH + 8);
      }
    }

    /* Dibuja las barras con un factor de escala k (0..1) desde abajo. */
    function drawBars(k) {
      for (var i = 0; i < points.length; i++) {
        var pt = points[i];
        var color = SEMAPHORE_COLORS[pt.color] || SEMAPHORE_COLORS.AMBER;
        var bx = xOf(i) + barInset;
        var barW = Math.max(2, slotW - barInset * 2);
        var hFull = pad.top + plotH - yOf(pt.price);
        var hNow = hFull * k;
        var by = pad.top + plotH - hNow;

        // Gradiente vertical: más claro arriba, más intenso abajo.
        var grad = ctx.createLinearGradient(0, by, 0, by + hNow);
        grad.addColorStop(0, color.base);
        grad.addColorStop(1, color.top);

        ctx.fillStyle = grad;
        roundRect(ctx, bx, by, barW, Math.max(1, hNow), Math.min(3, barW / 2));
        ctx.fill();
      }
    }

    /* Animación de entrada (grow-in de barras) o render instantáneo. */
    if (!animate) {
      drawBars(1);
    } else {
      var t0 = null;
      var DUR = 420; // ms, acorde con --t-slow
      (function frame(ts) {
        if (t0 === null) { t0 = ts; }
        var k = Math.min((ts - t0) / DUR, 1);
        // easing salida-suave: 1 - (1-k)^3
        var ease = 1 - Math.pow(1 - k, 3);

        ctx.clearRect(0, 0, W, H);
        drawStaticFrame();
        drawBars(ease);

        if (k < 1) {
          requestAnimationFrame(frame);
        }
      })(performance.now());
    }

    // Capa de interacción (solo del hover; no tocar ahora).
    storeHitTest(canvas, { points: points, xOf: xOf, slotW: slotW,
      padLeft: pad.left, barW: Math.max(2, slotW - barInset * 2),
      yTop: pad.top, yBottom: pad.top + plotH,
      tooltipEl: $tip, formatHorCb: opts.formatHourCb });
  };

  /** Rellena un rectángulo con esquinas redondeadas. */
  function roundRect(ctx, x, y, w, h, r) {
    if (h <= 0) { return; }
    r = Math.min(r, w / 2, h);
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  /* Estado de hit-testing del último gráfico dibujado. */
  var lastHitTest = null;

  function storeHitTest(canvas, data) {
    lastHitTest = { canvas: canvas, data: data };
  }

  /* ------------------------------------------------------------
     Hover / tooltip. Se registra una vez por canvas y usa el
     hit-testing del último gráfico.
     ------------------------------------------------------------ */
  WW.attachChartHover = function (canvas, tooltipEl) {
    var $canvas = $(canvas);
    var tip = tooltipEl;

    $canvas.on('mousemove', function (e) {
      if (!lastHitTest || lastHitTest.canvas !== canvas) { return; }

      var rect = canvas.getBoundingClientRect();
      var mx = e.clientX - rect.left;
      var my = e.clientY - rect.top;
      var data = lastHitTest.data;

      // Detectar sobre qué slot estamos.
      var relX = mx - data.padLeft;
      if (relX < 0) { hide(); return; }
      var idx = Math.floor(relX / data.slotW);
      if (idx < 0 || idx >= data.points.length) { hide(); return; }
      var pt = data.points[idx];
      if (!pt) { hide(); return; }

      // Mostrar tooltip.
      var color = (SEMAPHORE_COLORS[pt.color] || {}).base || '#94a3b8';
      var hour = data.formatHorCb ? data.formatHorCb(pt.timestamp) : pt.timestamp;

      $tip
        .removeClass('visible')
        .html(
          '<div class="tt-time">' + hour + '</div>' +
          '<div class="tt-price" style="color:' + color + '">&nbsp;</div>'
        )
        .find('.tt-price').html(Number(pt.price).toFixed(3) + ' &euro;/kWh').end()
        .addClass('visible');

      // Posicionar (no salirse del canvas).
      var tipW = $tip.outerWidth();
      var tipH = $tip.outerHeight();
      var left = Math.min(Math.max(mx + 14, 0), rect.width - tipW - 4);
      var topPx = Math.max(my - tipH - 10, 2);
      $tip.css({ left: left + 'px', top: topPx + 'px' });
    });

    $canvas.on('mouseleave', function () { hide(); });

    function hide() {
      if (tip) { $(tip).removeClass('visible'); }
    }
  };

})(window.WattWise);
