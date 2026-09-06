/* ============================================================
   api.js — Wrapper AJAX de jQuery para toda la comunicación con
   el backend Spring Boot.

   Puntos de diseño:
   - Centraliza la baseUrl y la cabecera de autenticación (JWT).
   - Devuelve jQuery Deferred/promises para integrarse con
     jQuery.ajax y poder encadenar .done/.fail.
   - Traducción uniforme de errores: si el backend devuelve 401
     en un endpoint autenticado redirigimos a login; si la API
     está caída mostramos un mensaje amigable.
   - El formato de error del backend es {timestamp,status,message,
     path} — lo normalizamos a un objeto JS simple.
   ============================================================ */

window.WattWise = window.WattWise || {};

(function (WW) {
  'use strict';

  // Base de la API. En dev el backend corre en :8080; en producción
  // Nginx hará de proxy inverso y la base podrá ser relativa ''.
  WW.baseUrl = WW.baseUrl || 'http://localhost:8080';

  // Almacenar el token actual (se sincroniza con auth.js/localStorage).
  WW.token = WW.token || null;

  // Cabeceras por defecto (content-type JSON siempre).
  function defaultHeaders() {
    var headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    };
    if (WW.token) {
      headers['Authorization'] = 'Bearer ' + WW.token;
    }
    return headers;
  }

  /**
   * Núcleo: hace la petición AJAX y normaliza el resultado.
   *
   * Devuelve una Promise jQuery que:
   *  - se RESUELVE con el body parseado (o undefined en 204),
   *  - se RECHAZA con el error ya normalizado {status, message, ...}
   *    en lugar del raw jqXHR, de modo que los .fail() de la UI pueden
   *    leer directamente err.message.
   *
   * Efectos secundarios globales:
   *  - 401 en petición autenticada => WW.onUnauthorized (sesión inválida)
   *  - WW.onRequestError (hook opcional para telemetría/logging)
   */
  function request(cfg) {
    var dfd = $.Deferred();

    var settings = $.extend({}, cfg, {
      url: WW.baseUrl + cfg.url,
      headers: $.extend(defaultHeaders(), cfg.headers || {}),
      dataType: 'json'
    });

    $.ajax(settings)
      .done(function (data) {
        dfd.resolve(typeof data === 'undefined' ? null : data);
      })
      .fail(function (jqXHR, textStatus, errorThrown) {
        var err = WW.normalizeError(jqXHR, textStatus, errorThrown);

        // 401 en una petición autenticada => la sesión expiró o no es válida.
        if (err.status === 401) {
          WW.onUnauthorized && WW.onUnauthorized(err);
        }

        if (WW.onRequestError) {
          WW.onRequestError(err);
        }

        dfd.reject(err);
      });

    return dfd.promise();
  }

  /**
   * Normaliza el error sea cual sea su forma:
   *  - backend vivo con body JSON {timestamp,status,message,path}
   *  - backend caído (status 0, textStatus 'error') => servicio no disponible
   *  - error de red / CORS
   */
  WW.normalizeError = function (jqXHR, textStatus, errorThrown) {
    var status = jqXHR.status || 0;
    var message = (jqXHR.responseJSON && jqXHR.responseJSON.message) ||
                  errorThrown || textStatus || 'Error desconocido';
    var path = (jqXHR.responseJSON && jqXHR.responseJSON.path) || '';

    // status 0 = no hay respuesta (API caída / red / CORS).
    if (status === 0 || textStatus === 'error' && !errorThrown) {
      return {
        status: 0,
        message: 'No se pudo conectar con el servidor. Comprueba que el backend está en marcha.',
        path: path,
        kind: 'network'
      };
    }
    if (status === 401) {
      return { status: 401, message: message || 'Sesión no válida o expirada.', path: path, kind: 'auth' };
    }
    if (status === 403) {
      return { status: 403, message: message || 'Acceso prohibido.', path: path, kind: 'forbidden' };
    }
    if (status === 404) {
      return { status: 404, message: message || 'Recurso no encontrado.', path: path, kind: 'notfound' };
    }
    if (status >= 500) {
      return { status: status, message: message || 'Error interno del servidor.', path: path, kind: 'server' };
    }
    return { status: status, message: message, path: path, kind: 'validation' };
  };

  /* ------------------------------------------------------------
     AUTH (públicos)
     ------------------------------------------------------------ */
  WW.api = WW.api || {};

  WW.api.register = function (username, email, password) {
    return request({
      type: 'POST',
      url: '/api/auth/register',
      data: JSON.stringify({ username: username, email: email, password: password })
    });
  };

  WW.api.login = function (username, password) {
    return request({
      type: 'POST',
      url: '/api/auth/login',
      data: JSON.stringify({ username: username, password: password })
    });
  };

  /* ------------------------------------------------------------
     PRECIOS (públicos)
     ------------------------------------------------------------ */
  WW.api.today = function () {
    return request({ type: 'GET', url: '/api/prices/today' });
  };

  WW.api.tomorrow = function () {
    return request({ type: 'GET', url: '/api/prices/tomorrow' });
  };

  WW.api.range = function (from, to) {
    return request({
      type: 'GET',
      url: '/api/prices/range?from=' + encodeURIComponent(from) +
            '&to=' + encodeURIComponent(to)
    });
  };

  /* ------------------------------------------------------------
     APARATOS (JWT)
     ------------------------------------------------------------ */
  WW.api.listAppliances = function () {
    return request({ type: 'GET', url: '/api/appliances' });
  };

  WW.api.createAppliance = function (payload) {
    return request({
      type: 'POST',
      url: '/api/appliances',
      data: JSON.stringify(payload)
    });
  };

  WW.api.updateAppliance = function (id, payload) {
    return request({
      type: 'PUT',
      url: '/api/appliances/' + encodeURIComponent(id),
      data: JSON.stringify(payload)
    });
  };

  WW.api.deleteAppliance = function (id) {
    return request({
      type: 'DELETE',
      url: '/api/appliances/' + encodeURIComponent(id)
    });
  };

  /* ------------------------------------------------------------
     RECOMENDACIONES (JWT)
     ------------------------------------------------------------ */
  WW.api.recommendations = function (applianceId) {
    var url = '/api/recommendations';
    if (applianceId) {
      url += '?applianceId=' + encodeURIComponent(applianceId);
    }
    return request({ type: 'GET', url: url });
  };

  /* ------------------------------------------------------------
     ALERTAS (JWT)
     ------------------------------------------------------------ */
  WW.api.getAlertPreferences = function () {
    return request({ type: 'GET', url: '/api/alerts/preferences' });
  };

  WW.api.updateAlertPreferences = function (payload) {
    return request({
      type: 'PUT',
      url: '/api/alerts/preferences',
      data: JSON.stringify(payload)
    });
  };

  WW.api.checkAlerts = function () {
    return request({ type: 'GET', url: '/api/alerts/check' });
  };

})(window.WattWise);
