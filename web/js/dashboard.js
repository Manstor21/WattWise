/* ============================================================
   dashboard.js — Controlador del dashboard (index.html).

   Responsabilidades:
   - Pestañas HOY / MAÑANA con gráfico semáforo + resumen.
   - Carga de recomendaciones por electrodoméstico.
   - CRUD de electrodomésticos (formulario + lista).
   - Preferencias y comprobación de alertas.

   El dashboard funciona sin login: solo muestra los precios
   públicos; las secciones privadas piden autenticación.

   Diseño: las cards privadas se construyen dinámicamente según
   el estado de autenticación (guest => invitación a loguearse;
   autenticado => formularios + listas).
   ============================================================ */

window.WattWise = window.WattWise || {};

(function (WW) {
  'use strict';

  var $ = window.jQuery;

  /* ------------------------------------------------------------
     Catálogo de valores típicos por tipo de aparato (kWh/ciclo),
     que auto-rellenamos si el usuario no los indica.
     ------------------------------------------------------------ */
  var APPLIANCE_CATALOG = {
    WASHING_MACHINE: { kwh: 0.8,  minutes: 120, watts: 2200, label: 'Lavadora' },
    DISHWASHER:      { kwh: 1.2,  minutes: 135, watts: 2000, label: 'Lavavajillas' },
    EV_CHARGER:      { kwh: 15,   minutes: 180, watts: 7400, label: 'Cargador VE' },
    DRYER:           { kwh: 1.8,  minutes: 90,  watts: 2500, label: 'Secadora' },
    POOL_PUMP:       { kwh: 2.0,  minutes: 360, watts: 1100, label: 'Bomba piscina' },
    AC:              { kwh: 1.5,  minutes: 150, watts: 1200, label: 'Aire acondicionado' },
    OTHER:           { kwh: 1.0,  minutes: 120, watts: 1000, label: 'Otro' }
  };
  WW.APPLIANCE_CATALOG = APPLIANCE_CATALOG;
  WW.APPLIANCE_TYPES = Object.keys(APPLIANCE_CATALOG);

  // Estado del dashboard.
  var state = {
    today: null,
    tomorrow: null,
    activeTab: 'today',
    current: [],
    recs: [],
    appliances: [],
    authOff: null
  };

  /* ------------------------------------------------------------
     Helpers de representación de precios
     ------------------------------------------------------------ */

  function toChartPoint(dto) {
    return { timestamp: dto.timestamp, price: Number(dto.totalEurPerKwh), color: dto.color };
  }

  function computeSummary(dtos) {
    if (!dtos || !dtos.length) { return null; }
    var min = dtos[0], max = dtos[0], sum = 0;
    for (var i = 0; i < dtos.length; i++) {
      var v = Number(dtos[i].totalEurPerKwh);
      sum += v;
      if (v < Number(min.totalEurPerKwh)) { min = dtos[i]; }
      if (v > Number(max.totalEurPerKwh)) { max = dtos[i]; }
    }
    return { cheapest: min, mostExpensive: max, average: sum / dtos.length, count: dtos.length };
  }

  /* ------------------------------------------------------------
     Inicialización principal
     ------------------------------------------------------------ */
  function initDashboard() {
    setupTabs();

    // Fecha mostrada en el subtítulo.
    $('#page-date').text(WW.formatLocalDate(new Date().toISOString()));

    var $body = $('#prices-card').find('.panel-body');
    $body.html(WW.loadingBlockEl('Cargando precios…'));

    loadToday();
    loadTomorrow();

    if (WW.isAuthenticated()) {
      showAuthorized();
    } else {
      showGuestBanner();
    }

    // Navegación interna (scroll) del menú.
    $('#main-nav').on('click', 'a[data-scroll]', function (e) {
      e.preventDefault();
      var target = $(this).attr('data-scroll');
      var $card = $('#appliances-card, #alerts-card').filter('#' + target);
      if ($card.length) { $card[0].scrollIntoView({ behavior: 'smooth' }); }
    });

    // Redibujar el gráfico al cambiar el tamaño.
    var resizeTimer = null;
    $(window).on('resize', function () {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(function () {
        if (state.current.length && document.getElementById('price-chart')) {
          WW.drawPriceChart({
            canvas: document.getElementById('price-chart'),
            points: state.current,
            tooltipEl: document.getElementById('price-chart-tooltip'),
            animate: false,
            formatHourCb: WW.formatLocalDayHour
          });
        }
      }, 150);
    });
  }

  function setupTabs() {
    $('#tab-today, #tab-tomorrow').on('click', function () {
      switchTab($(this).attr('data-tab'));
    });
  }

  function switchTab(tab) {
    if (state.activeTab === tab) { return; }
    state.activeTab = tab;
    $('#tab-today, #tab-tomorrow').removeClass('active');
    $('#tab-' + tab).addClass('active');

    if (tab === 'tomorrow') {
      if (state.tomorrow && state.tomorrow.length) { renderPrices(state.tomorrow); }
      else { renderLoadError('A\u00fan no se han publicado los precios de ma\u00f1ana.'); }
    } else {
      if (state.today && state.today.length) { renderPrices(state.today); }
      else { renderLoadError('No se pudieron cargar los precios de hoy.'); }
    }
  }

  /* ------------------------------------------------------------
     Carga de precios
     ------------------------------------------------------------ */
  function loadToday() {
    WW.api.today()
      .done(function (dtos) {
        state.today = Array.isArray(dtos) ? dtos : [];
        if (state.activeTab === 'today' && state.today.length) { renderPrices(state.today); }
      })
      .fail(function (err) {
        if (state.activeTab === 'today') {
          renderLoadError(err.kind === 'network'
            ? 'No se pudo conectar con la API de precios.'
            : 'No se pudieron cargar los precios de hoy.');
        }
      });
  }

  function loadTomorrow() {
    WW.api.tomorrow()
      .done(function (dtos) {
        state.tomorrow = Array.isArray(dtos) ? dtos : [];
        // Si el usuario ya estaba mirando la pestaña de mañana y los datos
        // llegan después, renderizamos para no dejar la pestaña vacía.
        if (state.activeTab === 'tomorrow' && state.tomorrow.length) {
          renderPrices(state.tomorrow);
        }
      })
      .fail(function () { state.tomorrow = []; });
  }

  /* ------------------------------------------------------------
     Render del panel de precios
     ------------------------------------------------------------ */
  function renderPrices(dtos) {
    if (!dtos.length) { renderLoadError('No hay datos de precios para este d\u00eda.'); return; }

    state.current = dtos.map(toChartPoint);
    var summary = computeSummary(dtos);

    $('#prices-card').find('.panel-body').html(
      '<div class="summary-grid">' +
        '<div class="summary-item best">' +
          '<div class="summary-label">M\u00e1s barata</div>' +
          '<div class="summary-value">' + WW.formatEuro(summary.cheapest.totalEurPerKwh) + '</div>' +
          '<div class="summary-hint">' + WW.formatLocalHour(summary.cheapest.timestamp) + ' h</div>' +
        '</div>' +
        '<div class="summary-item worst">' +
          '<div class="summary-label">M\u00e1s cara</div>' +
          '<div class="summary-value">' + WW.formatEuro(summary.mostExpensive.totalEurPerKwh) + '</div>' +
          '<div class="summary-hint">' + WW.formatLocalHour(summary.mostExpensive.timestamp) + ' h</div>' +
        '</div>' +
        '<div class="summary-item">' +
          '<div class="summary-label">Precio medio</div>' +
          '<div class="summary-value">' + WW.formatEuro(summary.average) + '</div>' +
          '<div class="summary-hint"><small>IVA incluido</small></div>' +
        '</div>' +
      '</div>' +
      '<div class="chart-wrap">' +
        '<canvas id="price-chart" class="chart-canvas" aria-label="Gr\u00e1fico de precios por hora" role="img"></canvas>' +
        '<div id="price-chart-tooltip" class="chart-tooltip"></div>' +
      '</div>' +
      '<div class="chart-legend-inline">' +
        '<span class="legend-item"><span class="semaphore-dot green"></span> Econ\u00f3mico</span>' +
        '<span class="legend-item"><span class="semaphore-dot amber"></span> Medio</span>' +
        '<span class="legend-item"><span class="semaphore-dot red"></span> Caro</span>' +
      '</div>'
    );

    WW.drawPriceChart({
      canvas: document.getElementById('price-chart'),
      points: state.current,
      tooltipEl: document.getElementById('price-chart-tooltip'),
      animate: true,
      formatHourCb: WW.formatLocalDayHour
    });
    WW.attachChartHover(
      document.getElementById('price-chart'),
      document.getElementById('price-chart-tooltip')
    );
  }

  function renderLoadError(msg) {
    $('#prices-card').find('.panel-body').html(
      '<div class="load-error" role="alert">' +
        '<span class="icon">\u26A0</span>' + msg +
        '<div style="margin-top:10px"><button class="btn btn-ghost btn-sm" id="retry-prices">Reintentar</button></div>' +
      '</div>'
    );
    $('#retry-prices').on('click', function () {
      $('#prices-card .panel-body').html(WW.loadingBlockEl('Cargando precios…'));
      if (state.activeTab === 'tomorrow') {
        loadTomorrow();
        // tras cargar mañana volvemos a renderizar
        setTimeout(function () {
          if (state.tomorrow.length) { renderPrices(state.tomorrow); }
          else { renderLoadError('A\u00fan no se han publicado los precios de ma\u00f1ana.'); }
        }, 300);
      } else {
        loadToday();
      }
    });
  }

  /* ------------------------------------------------------------
     Secciones privadas
     ------------------------------------------------------------ */

  function showGuestBanner() {
    // Como invitado una sola invitación a loguearse es suficiente;
    // las otras dos secciones privadas se eliminan.
    $('#recs-card, #alerts-card').remove();
    $('.dashboard-grid').removeClass('with-aside');

    var title = $('#appliances-card').attr('data-title') || 'Tu espacio';
    $('#appliances-card')
      .addClass('card')
      .html(
        '<div class="card-header"><h2 class="card-title">' + WW.escapeHtml(title) + '</h2></div>' +
        '<div class="empty-state">' +
          '<div class="big-icon">\U0001F512</div>' +
          '<p>Inicia sesi\u00f3n para ver recomendaciones y gestionar tus aparatos.</p>' +
          '<p style="margin-top:12px">' +
            '<a class="btn btn-primary" href="login.html">Iniciar sesi\u00f3n</a> ' +
            '<a class="btn btn-ghost" href="register.html">Crear cuenta</a>' +
          '</p>' +
        '</div>'
      );
  }

  function showAuthorized() {
    // Asegurar clases de layout.
    $('#appliances-card').addClass('card');
    buildAppliancesCard();
    buildRecsCard();
    buildAlertsCard();
  }

  /* ------------------------------------------------------------
     VISTA DE APARATOS (CRUD)
     ------------------------------------------------------------ */
  function buildAppliancesCard() {
    var $card = $('#appliances-card');
    $card.html(
      '<div class="card-header">' +
        '<h2 class="card-title"><span class="dot" style="background:var(--accent)"></span>Mis aparatos</h2>' +
      '</div>' +
      '<div class="grid-2col">' +
        /* Columna lista */
        '<div class="card">' +
          '<div class="appliance-list"></div>' +
        '</div>' +
        /* Columna formulario */
        '<form class="card" id="appliance-form">' +
          '<h3 class="form-title" style="margin-bottom:16px">A\u00f1adir aparato</h3>' +
          '<div class="form-group">' +
            '<label class="form-label" for="appliance-name">Nombre</label>' +
            '<input class="form-control" id="appliance-name" type="text" placeholder="Ej. Lavadora" autocomplete="off">' +
            '<div class="form-error"></div>' +
          '</div>' +
          '<div class="form-group">' +
            '<label class="form-label" for="appliance-type">Tipo</label>' +
            '<select class="form-control" id="appliance-type"></select>' +
            '<div class="form-error"></div>' +
          '</div>' +
          '<div class="form-group">' +
            '<label class="form-label" for="appliance-watts">Potencia (W)</label>' +
            '<input class="form-control" id="appliance-watts" type="number" min="1" step="1" placeholder="Auto">' +
            '<div class="form-error"></div>' +
          '</div>' +
          '<div class="form-group">' +
            '<label class="form-label" for="appliance-kwh">Consumo ciclo (kWh) <span class="hint">(auto por tipo)</span></label>' +
            '<input class="form-control" id="appliance-kwh" type="number" step="0.1" min="0">' +
            '<div class="form-error"></div>' +
          '</div>' +
          '<div class="form-group">' +
            '<label class="form-label" for="appliance-minutes">Duraci\u00f3n ciclo (min)</label>' +
            '<input class="form-control" id="appliance-minutes" type="number" min="1" step="1">' +
            '<div class="form-error"></div>' +
          '</div>' +
          '<button type="submit" class="btn btn-primary btn-block">A\u00f1adir</button>' +
        '</form>' +
      '</div>'
    );

    // Poblar select de tipos.
    var $type = $('#appliance-type');
    WW.APPLIANCE_TYPES.forEach(function (k) {
      $type.append('<option value="' + k + '">' + WW.escapeHtml(APPLIANCE_CATALOG[k].label) + '</option>');
    });

    // Autocompletar valores típicos al elegir tipo (si el campo no se ha
    // editado a mano).
    $type.on('change', function () {
      var cat = APPLIANCE_CATALOG[$type.val()];
      if (!cat) { return; }
      $('#appliance-kwh').val(cat.kwh);
      $('#appliance-minutes').val(cat.minutes);
      $('#appliance-watts').val(cat.watts);
    });

    // Submit.
    $('#appliance-form').on('submit', function (e) {
      e.preventDefault();
      submitApplianceForm();
    });

    // Delegación de acciones de lista.
    $card.on('click', '[data-action]', function (e) {
      e.preventDefault();
      var action = $(this).attr('data-action');
      var id = $(this).attr('data-id');
      if (action === 'edit') { editAppliance(id); }
      else if (action === 'delete') {
        var row = $(this).closest('.appliance-item');
        deleteAppliance(id, row.find('.appliance-name').text());
      } else if (action === 'toggle') { toggleAppliance(id); }
    });

    loadAppliances();
  }

  function renderAppliances() {
    var $list = $('#appliances-card .appliance-list');
    if (!state.appliances.length) {
      $list.html('<div class="empty-state">A\u00fan no tienes aparatos registrados.</div>');
      return;
    }
    var html = state.appliances.map(function (a) {
      var cat = APPLIANCE_CATALOG[a.type] || APPLIANCE_CATALOG.OTHER;
      var active = a.isActive !== false;
      return (
        '<div class="appliance-item" data-id="' + a.id + '">' +
          '<div>' +
            '<div class="appliance-name" style="font-weight:600">' + WW.escapeHtml(a.name) +
              ' <span class="appliance-type-chip">' + WW.escapeHtml(cat.label) + '</span>' +
            '</div>' +
            '<div class="appliance-meta">' +
              '<span>' + WW.escapeHtml((a.powerWatts || 0)) + ' W</span>' +
              '<span>' + (a.avgCycleKwh != null ? a.avgCycleKwh : '—') + ' kWh</span>' +
              '<span>' + (a.estimatedCycleMinutes || '—') + ' min</span>' +
              '<span class="pill ' + (active ? 'green' : 'neutral') + '">' +
                (active ? 'Activo' : 'Inactivo') + '</span>' +
            '</div>' +
          '</div>' +
          '<div class="appliance-actions">' +
            '<button class="btn btn-sm btn-ghost" data-action="toggle" data-id="' + a.id + '">' +
              (active ? 'Desactivar' : 'Activar') + '</button>' +
            '<button class="btn btn-sm btn-ghost" data-action="edit" data-id="' + a.id + '">Editar</button>' +
            '<button class="btn btn-sm btn-danger" data-action="delete" data-id="' + a.id + '">Eliminar</button>' +
          '</div>' +
        '</div>'
      );
    }).join('');
    $list.html('<div class="appliance-list stagger">' + html + '</div>');
  }

  function loadAppliances() {
    WW.api.listAppliances()
      .done(function (list) {
        state.appliances = list || [];
        renderAppliances();
      })
      .fail(function () {
        $('#appliances-card .appliance-list')
          .html('<div class="load-error" role="alert">No se pudieron cargar tus aparatos.</div>');
      });
  }

  function submitApplianceForm() {
    var $form = $('#appliance-form');
    clearFormErrors($form);

    var name = $.trim($('#appliance-name').val());
    var type = $('#appliance-type').val();
    var watts = $('#appliance-watts').val();
    var kwh = WW.parseDecimal($('#appliance-kwh').val());
    var minutes = $('#appliance-minutes').val();

    if (!name) { showFormError('#appliance-name', 'El nombre es obligatorio.'); return; }

    var payload = {
      name: name,
      type: type,
      powerWatts: watts ? Number(watts) : undefined,
      avgCycleKwh: !isNaN(kwh) ? kwh : undefined,
      estimatedCycleMinutes: minutes ? Number(minutes) : undefined,
      isActive: true
    };

    var editingId = $form.data('editing-id');
    var promise = editingId
      ? WW.api.updateAppliance(editingId, payload)
      : WW.api.createAppliance(payload);

    setFormBusy(true);
    promise
      .done(function () {
        WW.toastSuccess(editingId ? 'Aparato actualizado.' : 'Aparato a\u00f1adido.');
        resetApplianceForm();
        loadAppliances();
        loadRecommendations();
      })
      .fail(function (err) {
        WW.toastError(err.message || 'No se pudo guardar el aparato.');
        setFormBusy(false);
      });
  }

  function editAppliance(id) {
    var a = state.appliances.find(function (x) { return String(x.id) === String(id); });
    if (!a) { return; }
    $('#appliance-form').data('editing-id', a.id);
    $('#appliance-name').val(a.name);
    $('#appliance-type').val(a.type);
    $('#appliance-watts').val(a.powerWatts || '');
    $('#appliance-kwh').val(a.avgCycleKwh != null ? a.avgCycleKwh : '');
    $('#appliance-minutes').val(a.estimatedCycleMinutes || '');
    $('#appliances-card .form-title').text('Editar aparato');
    $('#appliance-form')[0].scrollIntoView({ behavior: 'smooth' });
  }

  function resetApplianceForm() {
    var $form = $('#appliance-form');
    $form[0].reset();
    $form.removeData('editing-id');
    $form.find('.form-title').text('A\u00f1adir aparato');
    $('#appliance-type').trigger('change');
  }

  function deleteAppliance(id, name) {
    if (!window.confirm('¿Eliminar \u00ab' + name + '\u00bb?')) { return; }
    WW.api.deleteAppliance(id)
      .done(function () {
        WW.toastSuccess('Aparato eliminado.');
        loadAppliances();
        loadRecommendations();
      })
      .fail(function (err) { WW.toastError(err.message || 'No se pudo eliminar.'); });
  }

  function toggleAppliance(id) {
    var a = state.appliances.find(function (x) { return String(x.id) === String(id); });
    if (!a) { return; }
    WW.api.updateAppliance(id, {
      name: a.name, type: a.type,
      powerWatts: a.powerWatts, avgCycleKwh: a.avgCycleKwh,
      estimatedCycleMinutes: a.estimatedCycleMinutes,
      isActive: a.isActive === false
    }).done(function () { loadAppliances(); })
      .fail(function (err) { WW.toastError(err.message || 'No se pudo actualizar.'); });
  }

  /* ------------------------------------------------------------
     VISTA DE RECOMENDACIONES
     ------------------------------------------------------------ */
  function buildRecsCard() {
    var $card = $('#recs-card');
    $card.html(
      '<div class="card-header">' +
        '<h2 class="card-title"><span class="dot" style="background:var(--success)"></span>Recomendaciones</h2>' +
      '</div>' +
      '<div class="rec-list"></div>'
    );
    $card.addClass('card');
    loadRecommendations();
  }

  function loadRecommendations() {
    if (!WW.isAuthenticated()) { return; }
    var $list = $('#recs-card .rec-list');
    $list.html(WW.loadingBlockEl('Calculando recomendaciones…'));
    WW.api.recommendations()
      .done(function (recs) {
        state.recs = recs || [];
        renderRecommendations();
      })
      .fail(function (err) {
        if (err.status === 401) { return; } // redirige app.js
        $list.html('<div class="load-error" role="alert">No se pudieron calcular las recomendaciones.</div>');
      });
  }

  function renderRecommendations() {
    var $list = $('#recs-card .rec-list');
    if (!state.recs.length) {
      $list.html(
        '<div class="empty-state">' +
          '<div class="big-icon">\U0001F4CB</div>' +
          'A\u00f1ade un electrodom\u00e9stico activo para recibir recomendaciones de cu\u00e1ndo usarlo.' +
        '</div>'
      );
      return;
    }
    var html = state.recs.map(function (r) {
      var app = r.appliance || {};
      var semClass = (r.semaphore || 'AMBER').toLowerCase();
      return (
        '<div class="rec-card ' + semClass + '">' +
          '<div class="rec-header">' +
            '<span class="rec-appliance-name">' + WW.escapeHtml(app.name || '') + '</span>' +
            '<span class="pill ' + semClass + '"><span class="semaphore-dot ' + semClass + '"></span>' +
              WW.escapeHtml(r.semaphore || '') + '</span>' +
          '</div>' +
          '<div class="rec-window">' + WW.formatLocalDayHour(r.recommendedStart) + ' \u2192 ' +
            WW.formatLocalDayHour(r.recommendedEnd) + '</div>' +
          '<div class="rec-stats">' +
            '<div class="rec-stat">Precio recomendado<b>' + WW.formatEuro(r.recommendedPriceEurPerKwh) + '</b></div>' +
            '<div class="rec-stat">Coste estimado<b>' + WW.formatEuro(r.estimatedCostEur) + '</b></div>' +
            '<div class="rec-stat">Peor caso<b>' + WW.formatEuro(r.worstCaseCostEur) + '</b></div>' +
            '<div class="rec-stat">Ahorro<b>' + WW.formatEuro(r.estimatedSavingsEur) + ' <small>(' +
              Number(r.savingsPercentage || 0).toFixed(0) + '%)</small></b></div>' +
          '</div>' +
          '<div class="rec-savings">Ahorra hasta ' + Number(r.savingsPercentage || 0).toFixed(0) +
            '% frente al peor momento</div>' +
        '</div>'
      );
    }).join('');
    $list.html('<div class="stagger">' + html + '</div>');
  }

  /* ------------------------------------------------------------
     VISTA DE ALERTAS
     ------------------------------------------------------------ */
  function buildAlertsCard() {
    var $card = $('#alerts-card');
    $card.addClass('card');
    $card.html(
      '<div class="card-header">' +
        '<h2 class="card-title"><span class="dot" style="background:var(--amber)"></span>Alertas de precios</h2>' +
      '</div>' +
      '<form id="alert-form">' +
        '<div class="form-group">' +
          '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">' +
            '<label class="form-label" style="margin:0" for="alert-active">Alertas activadas</label>' +
            '<label class="switch"><input type="checkbox" id="alert-active" checked>' +
              '<span class="slider"></span></label>' +
          '</div>' +
          '<p class="form-hint" style="color:var(--text-muted);font-size:var(--fs-xs);margin-bottom:var(--space-4)">' +
            'Te avisamos cuando el mejor precio del d\u00eda est\u00e9 muy por debajo de la media.' +
          '</p>' +
        '</div>' +
        '<div class="form-group">' +
          '<label class="form-label" for="alert-threshold">Umbral por debajo de la media: ' +
            '<span class="range-output" id="threshold-output">20%</span></label>' +
          '<input type="range" id="alert-threshold" min="0" max="100" step="1" value="20" ' +
            'aria-label="Umbral porcentual por debajo de la media">' +
          '<div class="form-error"></div>' +
        '</div>' +
        '<button type="submit" class="btn btn-primary">Guardar preferencias</button>' +
        '<button type="button" class="btn btn-ghost" id="check-alerts-btn">Comprobar alertas ahora</button>' +
      '</form>'
    );

    // Cargar preferencias actuales.
    WW.api.getAlertPreferences()
      .done(function (pref) {
        var p = pref || {};
        if (p.thresholdPctBelowMean != null) {
          $('#alert-threshold').val(Number(p.thresholdPctBelowMean));
          updateThresholdLabel();
        }
        if (typeof p.isActive === 'boolean') { $('#alert-active').prop('checked', p.isActive); }
      })
      .fail(function () {
        // No hay preferencias previas -> defaults ya puestos.
        updateThresholdLabel();
      });

    $('#alert-threshold').on('input', updateThresholdLabel);

    $('#alert-form').on('submit', function (e) {
      e.preventDefault();
      WW.api.updateAlertPreferences({
        thresholdPctBelowMean: Number($('#alert-threshold').val()),
        isActive: $('#alert-active').is(':checked')
      }).done(function () {
        WW.toastSuccess('Preferencias de alertas guardadas.');
      }).fail(function (err) {
        WW.toastError(err.message || 'No se pudieron guardar.');
      });
    });

    $('#check-alerts-btn').on('click', function () { checkAlertsNow($(this)); });
  }

  function updateThresholdLabel() {
    $('#threshold-output').text($('#alert-threshold').val() + '%');
  }

  function checkAlertsNow($btn) {
    $btn.prop('disabled', true).text('Comprobando…');
    WW.api.checkAlerts()
      .done(function (alerts) {
        $btn.prop('disabled', false).text('Comprobar alertas ahora');
        if (!alerts || !alerts.length) {
          WW.toast('Sin alertas', 'No se ha disparado ninguna alerta hoy.', 'info', 4000);
          return;
        }
        (Array.isArray(alerts) ? alerts : []).forEach(function (a) {
          if (/anomaly/i.test(a)) { WW.toast('Anomal\u00eda detectada', a, 'warning', 8000); }
          else { WW.toast('Precio atractivo', a, 'success', 8000); }
        });
      })
      .fail(function (err) {
        $btn.prop('disabled', false).text('Comprobar alertas ahora');
        WW.toastError(err.message || 'No se pudieron comprobar las alertas.');
      });
  }

  /* ------------------------------------------------------------
     Helpers de formulario
     ------------------------------------------------------------ */
  function showFormError(selector, msg) {
    $(selector).siblings('.form-error').text(msg);
    $(selector).addClass('is-invalid');
  }
  function clearFormErrors($form) {
    $form.find('.form-error').text('');
    $form.find('.is-invalid').removeClass('is-invalid');
  }
  function setFormBusy(busy) {
    var $btn = $('#appliance-form').find('[type="submit"]');
    if (busy) { $btn.prop('disabled', true).text('Guardando…'); }
    else { $btn.prop('disabled', false).text($('#appliance-form').data('editing-id') ? 'Guardar cambios' : 'Añadir'); }
  }

  /* ------------------------------------------------------------
     Registro del handler de página index
     ------------------------------------------------------------ */
  WW.registerPage('index', initDashboard);

})(window.WattWise);
