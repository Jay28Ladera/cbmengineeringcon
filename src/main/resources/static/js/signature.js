/*
 * Signature pad: draw with mouse, pen or finger on <div data-sig-pad><canvas></div>.
 * On submit of the surrounding [data-sig-form], the drawing is trimmed and put in [data-sig-out] as a PNG
 * data URL. An uploaded picture ([data-sig-file]) is sent instead when one is chosen.
 * Used on My profile and on the client's quotation page.
 */
(function () {
  'use strict';

  function setup(pad) {
    if (pad.dataset.ready) return;
    pad.dataset.ready = '1';
    var canvas = pad.querySelector('canvas'), ctx = canvas.getContext('2d');
    var drawing = false, last = null;
    pad.sigDirty = false;

    function fit() {
      // Draw at the device's pixel density so the line is crisp; keep what was drawn when resizing.
      var ratio = Math.max(window.devicePixelRatio || 1, 1), w = canvas.clientWidth, h = canvas.clientHeight;
      if (!w || !h) return;
      var keep = pad.sigDirty ? canvas.toDataURL() : null;
      canvas.width = Math.round(w * ratio); canvas.height = Math.round(h * ratio);
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.strokeStyle = '#0b1524'; ctx.lineWidth = 2.4;
      if (keep) { var img = new Image(); img.onload = function () { ctx.drawImage(img, 0, 0, w, h); }; img.src = keep; }
    }
    function at(e) { var r = canvas.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top }; }

    canvas.addEventListener('pointerdown', function (e) {
      e.preventDefault(); canvas.setPointerCapture(e.pointerId);
      drawing = true; last = at(e);
      ctx.beginPath(); ctx.arc(last.x, last.y, 1.1, 0, Math.PI * 2); ctx.fillStyle = '#0b1524'; ctx.fill();
      mark();
    });
    canvas.addEventListener('pointermove', function (e) {
      if (!drawing) return;
      var p = at(e);
      ctx.beginPath(); ctx.moveTo(last.x, last.y);
      var mid = { x: (last.x + p.x) / 2, y: (last.y + p.y) / 2 };
      ctx.quadraticCurveTo(last.x, last.y, mid.x, mid.y); ctx.lineTo(p.x, p.y); ctx.stroke();
      last = p;
    });
    ['pointerup', 'pointercancel', 'pointerleave'].forEach(function (t) { canvas.addEventListener(t, function () { drawing = false; }); });

    function mark() {
      pad.sigDirty = true; pad.classList.add('has-ink');
      var form = pad.closest('form'), file = form && form.querySelector('[data-sig-file]');
      if (file && file.value) { file.value = ''; showFile(form, ''); }
    }
    pad.sigClear = function () { ctx.clearRect(0, 0, canvas.width, canvas.height); pad.sigDirty = false; pad.classList.remove('has-ink'); };
    pad.sigTrimmed = function () {
      // Crop to the ink plus a margin, so the saved picture has no big empty border.
      var w = canvas.width, h = canvas.height, data = ctx.getImageData(0, 0, w, h).data;
      var x0 = w, y0 = h, x1 = -1, y1 = -1;
      for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) if (data[(y * w + x) * 4 + 3] > 0) {
        if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
      }
      if (x1 < 0) return null;
      var m = 12, out = document.createElement('canvas');
      x0 = Math.max(0, x0 - m); y0 = Math.max(0, y0 - m); x1 = Math.min(w - 1, x1 + m); y1 = Math.min(h - 1, y1 + m);
      out.width = x1 - x0 + 1; out.height = y1 - y0 + 1;
      out.getContext('2d').drawImage(canvas, x0, y0, out.width, out.height, 0, 0, out.width, out.height);
      return out.toDataURL('image/png');
    };
    fit();
    window.addEventListener('resize', fit);
  }

  function showFile(form, name) {
    var label = form.querySelector('[data-sig-file-name]');
    if (!label) return;
    if (!label.dataset.hint) label.dataset.hint = label.textContent;
    label.textContent = name ? 'Using ' + name : label.dataset.hint;
  }

  function init() { document.querySelectorAll('[data-sig-pad]').forEach(setup); }

  document.addEventListener('click', function (e) {
    var b = e.target.closest && e.target.closest('[data-sig-clear]');
    if (!b) return;
    var pad = b.closest('[data-sig-pad]'); if (pad && pad.sigClear) pad.sigClear();
  });
  document.addEventListener('change', function (e) {
    if (!e.target.matches || !e.target.matches('[data-sig-file]')) return;
    var form = e.target.closest('form'), f = e.target.files && e.target.files[0];
    showFile(form, f ? f.name : '');
    if (f) { var pad = form.querySelector('[data-sig-pad]'); if (pad && pad.sigClear) pad.sigClear(); }
  });
  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (!form.matches || !form.matches('[data-sig-form]')) return;
    var pad = form.querySelector('[data-sig-pad]'), out = form.querySelector('[data-sig-out]'), file = form.querySelector('[data-sig-file]');
    if (file && file.value) { if (out) out.value = ''; return; }
    var png = pad && pad.sigDirty ? pad.sigTrimmed() : null;
    if (!png) {
      e.preventDefault(); e.stopImmediatePropagation();
      if (pad) { pad.classList.add('needs-ink'); setTimeout(function () { pad.classList.remove('needs-ink'); }, 1600); }
      var msg = form.querySelector('[data-sig-msg]'); if (msg) msg.hidden = false;
      return;
    }
    out.value = png;
  }, true);

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
  document.addEventListener('app:navigated', init);
})();
