/*
 * Purchasing and inventory pages: dialogs filled from a button's data-f-* attributes, line-item
 * tables (add / remove rows, material picks, live totals), table search and filters.
 * Delegated from `document`, so it works on pages swapped in by app-navigation.js.
 */
(function () {
  'use strict';
  var peso = new Intl.NumberFormat('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  function num(v) { var n = parseFloat(String(v || '').replace(/,/g, '')); return isNaN(n) ? 0 : n; }
  function openDialog(d) { if (window.CBM && window.CBM.openDialog) window.CBM.openDialog(d.id); else if (!d.open) d.showModal(); }

  // ---- dialogs filled from data-f-<field> (data-f-payment-terms → name="paymentTerms")
  document.addEventListener('click', function (e) {
    var b = e.target.closest && e.target.closest('[data-fill]');
    if (!b) return;
    var dlg = document.getElementById(b.getAttribute('data-fill'));
    if (!dlg) return;
    var f = dlg.querySelector('form');
    if (!f.hasAttribute('data-orig-action')) f.setAttribute('data-orig-action', f.getAttribute('action'));
    f.reset();
    f.querySelectorAll('input[type=hidden]').forEach(function (h) { if (h.name !== '_csrf') h.value = ''; });
    f.action = b.getAttribute('data-action') || f.getAttribute('data-orig-action');
    Object.keys(b.dataset).forEach(function (k) {
      if (k.length < 2 || k.charAt(0) !== 'f' || k.charAt(1) !== k.charAt(1).toUpperCase()) return;
      var name = k.charAt(1).toLowerCase() + k.slice(2), el = f.elements[name], v = b.dataset[k];
      if (!el) return;
      if (el.type === 'checkbox') el.checked = v === 'true';
      else el.value = v;
    });
    var isNew = !f.elements.id || !f.elements.id.value;
    f.querySelectorAll('[data-new-only]').forEach(function (x) { x.hidden = !isNew; });
    f.querySelectorAll('[data-edit-only]').forEach(function (x) { x.hidden = isNew; });
    var t = dlg.querySelector('[data-dialog-title]');
    if (t && b.getAttribute('data-title')) t.textContent = b.getAttribute('data-title');
    openDialog(dlg);
  });

  // ---- line tables
  function recalc(table) {
    if (!table) return;
    var total = 0;
    table.querySelectorAll('tbody tr').forEach(function (tr) {
      var q = tr.querySelector('.q'), p = tr.querySelector('.p'), a = tr.querySelector('.amt');
      if (!q || !p || !a) return;
      var v = num(q.value) * num(p.value);
      total += v;
      a.textContent = v ? peso.format(v) : '';
    });
    var t = table.querySelector('[data-lines-total]');
    if (t) t.textContent = peso.format(total);
  }

  document.addEventListener('click', function (e) {
    if (!e.target.closest) return;
    var add = e.target.closest('[data-add-line]');
    if (add) {
      var table = add.closest('form').querySelector('table.line-table');
      var tpl = table.querySelector('template');
      var row = tpl.content.firstElementChild.cloneNode(true);
      table.querySelector('tbody').appendChild(row);
      var first = row.querySelector('select, input:not([type=hidden])');
      if (first) first.focus();
      return;
    }
    var rm = e.target.closest('[data-remove-line]');
    if (rm) {
      var tr = rm.closest('tr'), tb = tr.parentNode, tbl = tr.closest('table');
      if (tb.children.length > 1) tr.remove();
      else tr.querySelectorAll('input:not([type=hidden]), select').forEach(function (i) { i.value = i.tagName === 'SELECT' ? '' : (i.name === 'line_unit' ? 'pc' : ''); });
      recalc(tbl);
    }
  });

  document.addEventListener('change', function (e) {
    var s = e.target;
    if (s.classList && s.classList.contains('material-pick')) {
      var tr = s.closest('tr'), o = s.options[s.selectedIndex];
      var desc = tr.querySelector('[name=line_desc]'), unit = tr.querySelector('[name=line_unit]'), price = tr.querySelector('.p'), oh = tr.querySelector('.onhand');
      if (o && o.value) {
        if (desc && (!desc.value || desc.dataset.auto === '1')) { desc.value = o.getAttribute('data-name') || ''; desc.dataset.auto = '1'; }
        if (unit && o.getAttribute('data-unit')) unit.value = o.getAttribute('data-unit');
        if (price && !price.value && o.getAttribute('data-cost')) price.value = o.getAttribute('data-cost');
        if (oh) oh.textContent = (o.getAttribute('data-onhand') || '0') + ' ' + (o.getAttribute('data-unit') || '');
      } else if (oh) oh.textContent = '—';
      recalc(s.closest('table'));
    }
    if (s.id === 'adjReason') adjSync(s.closest('form'));
    if (s.id === 'p1') { // PO supplier → their usual payment terms when none typed yet
      var terms = document.getElementById('p3'), o2 = s.options[s.selectedIndex];
      if (terms && !terms.value && o2) terms.value = o2.getAttribute('data-terms') || '';
    }
    if (s.name === 'direction' && s.closest('#adjDialog')) adjSync(s.closest('form'));
  });

  document.addEventListener('input', function (e) {
    var t = e.target;
    if (t.classList && (t.classList.contains('q') || t.classList.contains('p'))) recalc(t.closest('table'));
    if (t.name === 'line_desc') t.dataset.auto = '';
    var ft = t.getAttribute && t.getAttribute('data-filter-table');
    if (ft) filter(ft);
  });
  document.addEventListener('change', function (e) {
    var t = e.target.getAttribute && e.target.getAttribute('data-filter-low');
    if (t) filter(t);
  });

  function filter(id) {
    var table = document.getElementById(id);
    if (!table) return;
    var box = document.querySelector('[data-filter-table="' + id + '"]'), low = document.querySelector('[data-filter-low="' + id + '"]');
    var q = box ? box.value.trim().toLowerCase() : '', onlyLow = low && low.checked;
    table.querySelectorAll('tbody tr').forEach(function (tr) {
      tr.hidden = (q && tr.textContent.toLowerCase().indexOf(q) < 0) || (onlyLow && tr.getAttribute('data-low') !== 'true');
    });
  }

  // ---- stock adjustment dialog: direction follows the reason; cost only when adding; job only for returns
  function adjSync(f) {
    if (!f) return;
    var r = f.querySelector('#adjReason'), o = r.options[r.selectedIndex], dir = o.getAttribute('data-dir');
    if (dir) f.querySelectorAll('[name=direction]').forEach(function (x) { x.checked = x.value === dir; x.disabled = true; });
    else f.querySelectorAll('[name=direction]').forEach(function (x) { x.disabled = false; });
    var cur = (f.querySelector('[name=direction]:checked') || {}).value;
    f.querySelectorAll('[data-show-dir]').forEach(function (x) { x.hidden = x.getAttribute('data-show-dir') !== cur; });
    f.querySelectorAll('[data-show-reason]').forEach(function (x) { x.hidden = x.getAttribute('data-show-reason') !== r.value; });
  }
  // disabled radios aren't submitted; re-enable just before submit
  document.addEventListener('submit', function (e) {
    if (e.target.closest && e.target.closest('#adjDialog')) e.target.querySelectorAll('[name=direction]').forEach(function (x) { x.disabled = false; });
  }, true);

  function init() {
    document.querySelectorAll('table.line-table').forEach(function (t) {
      recalc(t);
      t.querySelectorAll('select.material-pick').forEach(function (s) {
        var oh = s.closest('tr').querySelector('.onhand'), o = s.options[s.selectedIndex];
        if (oh && o && o.value) oh.textContent = (o.getAttribute('data-onhand') || '0') + ' ' + (o.getAttribute('data-unit') || '');
      });
    });
    var adj = document.querySelector('#adjDialog form');
    if (adj) adjSync(adj);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
  document.addEventListener('app:navigated', init);
})();

/* Canvass: add / edit one supplier's quote */
(function () {
  'use strict';
  function $(id) { return document.getElementById(id); }
  function show(dlg, editing) {
    dlg.querySelectorAll('[data-new-quote]').forEach(function (x) { x.hidden = editing; });
    dlg.querySelector('[data-edit-quote-name]').hidden = !editing;
    $('q1').disabled = editing; $('qSupplierHidden').disabled = !editing;
    syncNew();
  }
  function syncNew() {
    var sel = $('q1'), box = document.querySelector('[data-new-supplier]');
    if (box && sel) box.hidden = sel.disabled || !!sel.value;
  }
  function recalc(dlg) { var p = dlg.querySelector('.p'); if (p) p.dispatchEvent(new Event('input', { bubbles: true })); }
  document.addEventListener('click', function (e) {
    if (!e.target.closest) return;
    var add = e.target.closest('[data-add-quote]'), ed = e.target.closest('[data-edit-quote]');
    if (!add && !ed) return;
    var dlg = $('quoteDialog'); if (!dlg) return;
    var f = dlg.querySelector('form');
    f.reset();
    dlg.querySelectorAll('.p, .qb').forEach(function (i) { i.value = ''; i.disabled = false; });
    dlg.querySelectorAll('.qna').forEach(function (i) { i.checked = false; });
    $('qTitle').textContent = ed ? 'Edit quote' : 'Add supplier quote';
    show(dlg, !!ed);
    if (ed) {
      $('qName').textContent = ed.getAttribute('data-name');
      $('qSupplierHidden').value = ed.getAttribute('data-supplier');
      $('q3').value = ed.getAttribute('data-ref') || '';
      $('q4').value = ed.getAttribute('data-date') || '';
      $('q5').value = ed.getAttribute('data-days') || '';
      $('q6').value = ed.getAttribute('data-valid') || '';
      $('q7').value = ed.getAttribute('data-terms') || '';
      $('q8').value = ed.getAttribute('data-vat') || '';
      $('q9').value = ed.getAttribute('data-mode') || '';
      $('q10').value = ed.getAttribute('data-termdays') || '';
      $('q11').checked = ed.getAttribute('data-pdc') === 'true';
      var rows = [];
      try { rows = JSON.parse(ed.getAttribute('data-prices') || '[]'); } catch (x) { rows = []; }
      rows.forEach(function (r) {
        var p = dlg.querySelector('.p[data-item="' + r.i + '"]'), b = dlg.querySelector('.qb[data-item="' + r.i + '"]'), n = dlg.querySelector('.qna[data-item="' + r.i + '"]');
        if (p && r.p) p.value = r.p;
        if (b && r.b) b.value = r.b;
        if (n && r.n) { n.checked = true; if (p) { p.value = ''; p.disabled = true; } }
      });
    }
    recalc(dlg);
    if (window.CBM && window.CBM.openDialog) window.CBM.openDialog('quoteDialog'); else dlg.showModal();
  });
  // "N/A": the supplier doesn't carry the item, so no price
  document.addEventListener('change', function (e) {
    if (!e.target.classList || !e.target.classList.contains('qna')) return;
    var p = e.target.closest('tr').querySelector('.p');
    if (p) { if (e.target.checked) p.value = ''; p.disabled = e.target.checked; p.dispatchEvent(new Event('input', { bubbles: true })); }
  });
  document.addEventListener('change', function (e) {
    if (e.target.id !== 'q1') return;
    syncNew();
    var o = e.target.options[e.target.selectedIndex], t = $('q7');
    if (t && !t.value && o && o.getAttribute('data-terms')) t.value = o.getAttribute('data-terms');
  });
})();
