/* ============================================================
   app.js — Inicialización, utilidades compartidas y mini-router
   por páginas.

   El frontend es estático servido por Nginx. Cada HTML carga
   'app.js' que detecta qué página es (por el id del contenedor
   o un atributo data-page en <body>) y lo inicializa.

   Utilities incluidas:
   - fecha/hora a Europe/Madrid con Intl.DateTimeFormat
   - formateo de euros con 2 decimales
   - toasts (notificaciones)
   - helpers de DOM con jQuery
   ============================================================ */

window.WattWise = window.WattWise || {};

(function (WW) {
  'use strict';

  var $ = window.jQuery;

  /* ------------------------------------------------------------
     Configuración incial: token persistido en localStorage.
     Mantenemos sync entre WW.token (api.js) y localStorage.
     ------------------------------------------------------------ */
  var TOKEN_KEY = 'wattwise_token';
  var USER_KEY = 'wattwise_user';

  WW.getToken = function () {
    if (!WW.token) { WW.token = localStorage.getItem(TOKEN_KEY); }
    return WW.token;
  };

  WW.setSession = function (token, user) {
    WW.token = token;
    localStorage.setItem(TOKEN_KEY, token);
    if (user) {
      localStorage.setItem(USER_KEY, JSON.stringify(user));
      WW.user = user;
    }
  };

  WW.getUser = function () {
    if (WW.user) { return WW.user; }
    try {
      WW.user = JSON.parse(localStorage.getItem(USER_KEY) || 'null');
    } catch (e) {
      WW.user = null;
    }
    return WW.user;
  };

  WW.isAuthenticated = function () {
    return !!WW.getToken() && !!WW.getUser();
  };

  WW.clearSession = function () {
    WW.token = null;
    WW.user = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  };

  /* 401 global: cuando la API rechaza un token, limpiamos sesión
     y redirigimos a login (salvo que ya estemos allí). */
  WW.onUnauthorized = function () {
    WW.clearSession();
    var current = window.location.pathname.split('/').pop();
    if (current !== 'login.html') {
      window.location.href = 'login.html';
    }
  };

  /* ------------------------------------------------------------
     Utilidades de formato
     ------------------------------------------------------------ */

  // Zona horaria canónica de España.
  var TZ = 'Europe/Madrid';

  /**
   * Interpreta un timestamp del backend como fecha de JS correctamente.
   *
   * El backend serializa LocalDateTime como '2025-01-05T23:00:00' (UTC por
   * contrato, pero SIN sufijo 'Z'). Si no hay zona horaria explícita en el
   * string, le añadimos 'Z' para que la conversión a Europe/Madrid sea
   * exacta y no dependa de la zona horaria de la máquina del usuario.
   */
  WW.resolveDate = function (iso) {
    if (!iso) { return null; }
    // 'Z' final o desfase tipo +02:00 / +0200 => ya trae zona.
    var hasZone = /(Z|[+-]\d{2}:?\d{2})$/i.test(iso);
    var d = new Date(hasZone ? iso : iso + 'Z');
    // Un string inválido produce un Invalid Date (objeto, no null);
    // lo normalizamos a null para que los formateadores devuelvan '—'.
    return isNaN(d.getTime()) ? null : d;
  };

  // Formateadores de Intl (se crean una vez por rendimiento).
  var fmtHour = new Intl.DateTimeFormat('es-ES', {
    timeZone: TZ, hour: '2-digit', minute: '2-digit'
  });
  var fmtDayHour = new Intl.DateTimeFormat('es-ES', {
    timeZone: TZ, day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit'
  });
  var fmtDate = new Intl.DateTimeFormat('es-ES', {
    timeZone: TZ, day: '2-digit', month: 'long'
  });

  /**
   * Convierte un timestamp ISO-8601 UTC (p. ej. '2025-01-05T23:00:00')
   * a hora local de España y devuelve 'HH:mm'.
   */
  WW.formatLocalHour = function (iso) {
    var d = WW.resolveDate(iso);
    return d ? fmtHour.format(d) : '—';
  };

  WW.formatLocalDayHour = function (iso) {
    var d = WW.resolveDate(iso);
    return d ? fmtDayHour.format(d) : '—';
  };

  WW.formatLocalDate = function (iso) {
    var d = WW.resolveDate(iso);
    return d ? fmtDate.format(d) : '—';
  };

  // € con 2 decimales y símbolo. Maneja null/undefined.
  WW.formatEuro = function (value) {
    if (value === null || value === undefined || isNaN(Number(value))) {
      return '—';
    }
    return Number(value).toFixed(2) + ' €';
  };

  // Guarda números decimales robustos (p. ej. '0,8' -> 0.8)
  WW.parseDecimal = function (str) {
    if (typeof str !== 'string') { return Number(str); }
    return Number(str.replace(',', '.'));
  };

  /* ------------------------------------------------------------
     Toasts (notificaciones en pantalla)
     ------------------------------------------------------------ */
  var toastStackEl = null;

  function ensureToastStack() {
    if (toastStackEl) { return toastStackEl; }
    toastStackEl = document.getElementById('toast-stack');
    if (!toastStackEl) {
      toastStackEl = document.createElement('div');
      toastStackEl.id = 'toast-stack';
      toastStackEl.className = 'notification-stack';
      document.body.appendChild(toastStackEl);
    }
    return toastStackEl;
  }

  /**
   * Muestra una notificación.
   * @param {string} title
   * @param {string} msg
   * @param {string} type  success | error | warning | info
   * @param {number} duration ms (0 = persistente)
   */
  WW.toast = function (title, msg, type, duration) {
    var icons = {
      success: '\u2713', error: '\u26A0', warning: '\u26A0', info: '\u2139'
    };
    var el = $(document.createElement('div'))
      .addClass('toast ' + (type || 'info'))
      .attr('role', 'status')
      .attr('aria-live', 'polite');

    el.append('<span class="toast-icon">' + (icons[type] || icons.info) + '</span>');
    el.append(
      '<div class="toast-body">' +
        '<div class="toast-title"></div>' +
        '<div class="toast-msg"></div>' +
      '</div>'
    );
    el.append('<button class="toast-close" aria-label="Cerrar notificación">&times;</button>');

    el.find('.toast-title').text(title);
    el.find('.toast-msg').text(msg);

    el.find('.toast-close').on('click', function () { dismiss(); });

    function dismiss() {
      el.stop(true, true).fadeOut(200, function () { el.remove(); });
    }

    ensureToastStack().appendChild(el[0]);

    if (duration) {
      setTimeout(dismiss, duration);
    }
    return dismiss;
  };

  WW.toastError = function (msg) {
    return WW.toast('Error', msg, 'error', 6000);
  };

  WW.toastSuccess = function (msg) {
    return WW.toast('Listo', msg, 'success', 4000);
  };

  /* ------------------------------------------------------------
     Helpers DOM / templates
     ------------------------------------------------------------ */

  /**
   * Renderiza una plantilla simple con marcadores {clave}.
   * Escapa valores para prevenir inyección HTML.
   */
  WW.tpl = function (template, data) {
    return template.replace(/\{(\w+)\}/g, function (m, key) {
      var val = data[key];
      if (val === undefined || val === null) { return ''; }
      return WW.escapeHtml(String(val));
    });
  };

  WW.escapeHtml = function (s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  };

  // Muestra/oculta spinner HTML
  WW.loadingBlockEl = function (label) {
    return $('<div class="loading-block"><span class="spinner"></span><span></span></div>')
      .find('span:last').text(label || 'Cargando…').end();
  };

  /* ------------------------------------------------------------
     Router por página
     ------------------------------------------------------------ */
  var pageHandlers = {};

  WW.registerPage = function (name, handler) {
    pageHandlers[name] = handler;
  };

  /**
   * Detecta la página actual leyendo data-page de <body> o del
   * nombre del fichero HTML, y ejecuta su inicializador.
   */
  WW.init = function () {
    var $body = $('body');
    var page = $body.data('page');

    if (!page) {
      var pathname = (window.location && window.location.pathname) || '';
      var file = pathname.split('/').pop().replace('.html', '');
      page = file || 'index';
    }

    // Aplicar token desde localStorage al arranque.
    WW.getToken();

    // Marcar enlace de navegación activo.
    $body.find('.main-nav a[data-nav]').each(function () {
      var target = $(this).attr('data-nav');
      if (target === page) { $(this).addClass('active'); }
    });

    if (pageHandlers[page]) {
      pageHandlers[page]();
    }
  };

  // Autostart cuando el DOM esté listo.
  $(function () {
    WW.init();
  });

})(window.WattWise);
