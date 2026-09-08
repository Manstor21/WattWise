/* ============================================================
   jquery-fallback.js — Fallback local si el CDN de jQuery no
   está disponible. Debe cargarse DESPUÉS del <script> del CDN
   (que incluye integridad SRI). Extraído a archivo propio para
   permitir una Content-Security-Policy sin unsafe-inline.
   ============================================================ */
(function () {
  'use strict';
  if (!window.jQuery) {
    document.write('<script src="js/vendor/jquery-3.7.1.min.js"><\/script>');
  }
}());