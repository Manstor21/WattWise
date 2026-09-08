/* ============================================================
   auth.js — Login, registro y logout. JWT persistido en
   sessionStorage.

   Puntos de diseño:
   - Fallback del wrapper api.js: si hay error 401 se redirige a
     login automáticamente (ver app.js onUnauthorized).
   - Validación básica en cliente ANTES de llamar a la API.
   - Al autenticarse se guarda token+user y se va a index.html.
   - También sincroniza la cabecera (Login/Logout según estado).
   ============================================================ */

window.WattWise = window.WattWise || {};

(function (WW) {
  'use strict';

  var $ = window.jQuery;

  /* ------------------------------------------------------------
     Login: lógica de login.html
     ------------------------------------------------------------ */
  function initLogin() {
    // Si ya está logueado, ir directo al dashboard.
    if (WW.isAuthenticated()) {
      window.location.href = 'index.html';
      return;
    }

    var $form = $('#login-form');
    if (!$form.length) { return; }

    $form.on('submit', function (e) {
      e.preventDefault();

      clearErrors($form);
      var username = $.trim($('#login-username').val());
      var password = $('#login-password').val();

      var valid = true;
      if (!username) { showError('#login-username', 'El usuario es obligatorio.'); valid = false; }
      if (!password) { showError('#login-password', 'La contraseña es obligatoria.'); valid = false; }
      if (!valid) { return; }

      setBusy($form, true);
      hideAlert();

      WW.api.login(username, password)
        .done(function (res) {
          // res = { token, user: {id, username, email, role} }
          WW.setSession(res.token, res.user);
          showAlert('success', 'Sesión iniciada. Redirigiendo…');
          setTimeout(function () {
            window.location.href = 'index.html';
          }, 400);
        })
        .fail(function (err) {
          setBusy($form, false);
          showAlert('error', err.message || 'No se pudo iniciar sesión.');
        });
    });

    // Autocompletar estado del botón con credenciales del navegador.
    $('#login-password').on('input', function () {
      if ($(this).val()) { clearError('#login-password'); }
    });
    $('#login-username').on('input', function () {
      if ($(this).val()) { clearError('#login-username'); }
    });
  }

  /* ------------------------------------------------------------
     Registro: lógica de register.html
     ------------------------------------------------------------ */
  function initRegister() {
    if (WW.isAuthenticated()) {
      window.location.href = 'index.html';
      return;
    }

    var $form = $('#register-form');
    if (!$form.length) { return; }

    $form.on('submit', function (e) {
      e.preventDefault();

      clearErrors($form);
      hideAlert();

      var username = $.trim($('#reg-username').val());
      var email = $.trim($('#reg-email').val());
      var password = $('#reg-password').val();
      var confirm = $('#reg-confirm').val();

      var valid = true;

      if (username.length < 3) {
        showError('#reg-username', 'El usuario debe tener al menos 3 caracteres.');
        valid = false;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showError('#reg-email', 'Introduce un email válido.');
        valid = false;
      }
      if (password.length < 8) {
        showError('#reg-password', 'La contraseña debe tener al menos 8 caracteres.');
        valid = false;
      }
      if (password !== confirm) {
        showError('#reg-confirm', 'Las contraseñas no coinciden.');
        valid = false;
      }
      if (!valid) { return; }

      setBusy($form, true);

      WW.api.register(username, email, password)
        .done(function (res) {
          WW.setSession(res.token, res.user);
          showAlert('success', 'Cuenta creada. Redirigiendo…');
          setTimeout(function () {
            window.location.href = 'index.html';
          }, 500);
        })
        .fail(function (err) {
          setBusy($form, false);
          showAlert('error', err.message || 'No se pudo crear la cuenta.');
        });
    });
  }

  /* ------------------------------------------------------------
     Cabecera compartida: estado Login/Logout y menú móvil.
     Se usa en las tres páginas (index/login/register).
     ------------------------------------------------------------ */
  function initHeader() {
    // Botón menú hamburguesa (solo visible en móvil).
    $('#nav-toggle').on('click', function () {
      $('#main-nav').toggleClass('open');
    });

    // Cerrar menú al hacer click en un enlace (móvil).
    $('#main-nav').on('click', 'a', function () {
      $('#main-nav').removeClass('open');
    });

    // Estado autenticado: mostrar usuario y botón Logout.
    // El contenedor auth-area está presente en todas las páginas.
    var $authArea = $('#auth-area');
    if (WW.isAuthenticated()) {
      var user = WW.getUser();
      $authArea
        .html(
          '<span class="nav-user" title="' + WW.escapeHtml(user.email || '') + '">' +
            WW.escapeHtml(user.username) +
          '</span>' +
          '<button class="nav-logout" data-nav="logout" aria-label="Cerrar sesión">Salir</button>'
        );
      $authArea.find('.nav-logout').on('click', function () {
        WW.clearSession();
        WW.toastSuccess('Has cerrado sesión.');
        setTimeout(function () { window.location.reload(); }, 300);
      });
    } else {
      $authArea.html(
        '<a href="login.html" data-nav="login">Entrar</a>' +
        '<a href="register.html" data-nav="register" class="btn btn-primary btn-sm">Registrarse</a>'
      );
    }
  }

  /* ------------------------------------------------------------
     Helpers privados de formulario
     ------------------------------------------------------------ */
  function setBusy($form, busy) {
    var $btn = $form.find('[type="submit"]');
    if (busy) {
      $btn.prop('disabled', true).attr('data-label', $btn.text()).text('Procesando…');
    } else {
      $btn.prop('disabled', false).text($btn.data('label') || $btn.text());
    }
  }

  function showAlert(type, msg) {
    var $box = $('#auth-alert');
    if (!$box.length) { return; }
    $box.removeClass('show error success')
        .addClass('show ' + type)
        .text(msg);
  }

  function hideAlert() {
    var $box = $('#auth-alert');
    if ($box.length) { $box.removeClass('show'); }
  }

  function showError(selector, msg) {
    $(selector).siblings('.form-error').text(msg);
    $(selector).addClass('is-invalid');
  }

  function clearError(selector) {
    $(selector).siblings('.form-error').text('');
    $(selector).removeClass('is-invalid');
  }

  function clearErrors($form) {
    $form.find('.form-error').text('');
    $form.find('.is-invalid').removeClass('is-invalid');
  }

  /* ------------------------------------------------------------
     Registro de handlers de página
     ------------------------------------------------------------ */
  WW.registerPage('login', initLogin);
  WW.registerPage('register', initRegister);

  // La cabecera se inicializa en todas las páginas vía ready global.
  $(function () {
    initHeader();
  });

})(window.WattWise);
