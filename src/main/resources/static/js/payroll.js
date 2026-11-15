/*
 * HR payroll pages: Employees, Payroll Periods, Time Sheets, Pay Sheets and Loans.
 * Handlers are delegated from `document` (pages are swapped in by app-navigation.js);
 * per-page setup runs on load and after every in-app navigation.
 */
(function () {
  'use strict';

  var peso = new Intl.NumberFormat('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  function money(v) { return peso.format(Number(v || 0)); }
  function num(v) { var n = parseFloat(v); return isNaN(n) ? 0 : n; }
  function $(id) { return document.getElementById(id); }
  function readJson(id, fallback) {
    var el = $(id);
    try { return el ? JSON.parse(el.textContent || '') : fallback; } catch (e) { return fallback; }
  }
  function page() { var m = document.querySelector('main.content-area'); return m ? m.getAttribute('data-hr-page') : null; }
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }
  function open(id) { if (window.CBM && window.CBM.openDialog) window.CBM.openDialog(id); else { var d = $(id); if (d && !d.open) d.showModal(); } }

  // ------------------------------------------------------------------ shared: select that navigates
  document.addEventListener('change', function (e) {
    var sel = e.target.closest && e.target.closest('select[data-hr-nav]');
    if (!sel) return;
    if (tsDirtyCount() > 0 && !window.confirm('You have time sheet changes that are not saved. Leave without saving?')) return;
    tsReset();
    if (window.CBM && window.CBM.navigate) window.CBM.navigate(sel.value); else window.location.href = sel.value;
  });

  // ================================================================== Employees
  var EMP = {};

  function empFilter() {
    var table = $('empTable'); if (!table) return;
    var q = ($('empSearch').value || '').trim().toLowerCase();
    var g = $('empGroupFilter').value, st = $('empStateFilter').value, shown = 0;
    table.querySelectorAll('tbody tr').forEach(function (tr) {
      var ok = (!q || tr.getAttribute('data-search').indexOf(q) >= 0) && (!g || tr.getAttribute('data-group') === g);
      if (ok && st === 'active') ok = tr.getAttribute('data-active') === 'true';
      if (ok && st === 'inactive') ok = tr.getAttribute('data-active') !== 'true';
      if (ok && st === 'norate') ok = tr.getAttribute('data-norate') === 'true' && tr.getAttribute('data-active') === 'true';
      tr.hidden = !ok; if (ok) shown++;
    });
    $('empCount').textContent = shown;
    $('empNoMatch').hidden = shown > 0;
  }

  function setRateType(type) {
    var monthly = type === 'MONTHLY';
    $('empRateMonthly').checked = monthly; $('empRateDaily').checked = !monthly;
    $('monthlyWrap').hidden = !monthly;
    $('rateTypeHint').textContent = monthly
      ? 'Half the monthly rate each cut-off, less absences × daily rate.'
      : 'Paid for the days worked in each cut-off.';
    $('dailyHint').textContent = monthly
      ? 'Used for absences, OT and lates. Leave blank to use monthly × 12 ÷ 313.'
      : 'Basic + allowance per day. Rate/hr = ÷ 8.';
  }

  function rateChanged() {
    var f = $('employeeForm'); if (!f) return;
    var orig = f._orig || {};
    var changed = !!$('empId').value && (
      num($('empDaily').value) !== num(orig.dailyRate) ||
      ($('empRateMonthly').checked && num($('empMonthly').value) !== num(orig.monthlyRate)));
    $('rateChangeWrap').hidden = !changed; $('rateNoteWrap').hidden = !changed;
  }

  function openEmployee(id) {
    var d = id ? EMP[String(id)] : null;
    var f = $('employeeForm'); f.reset(); f._orig = d || {};
    $('empId').value = d ? d.id : '';
    $('employeeDialogTitle').textContent = d ? d.fullName : 'Add employee';
    $('employeeDialogSub').textContent = d ? (d.linked ? 'On the organizational chart' : 'Added in HR') : 'Someone who is not on the organizational chart.';
    var linked = !!(d && d.linked);
    $('empLinkedNote').hidden = !linked;
    f.querySelectorAll('[data-chart-field]').forEach(function (i) { i.readOnly = linked; i.classList.toggle('is-readonly', linked); });
    $('empName').value = d ? d.fullName || '' : '';
    $('empNo').value = d ? d.employeeNo || '' : '';
    $('empHired').value = d ? d.dateHired || '' : '';
    $('empPosition').value = d ? d.position || '' : '';
    $('empDept').value = d ? d.department || '' : '';
    $('empGroup').value = d ? d.payGroup || '' : '';
    $('empBio').value = d ? d.biometricId || '' : '';
    $('empStatusCode').value = d ? d.statusCode || '' : '';
    $('empEmployment').value = d ? d.employmentStatus || '' : '';
    setRateType(d ? d.rateType : 'DAILY');
    $('empDaily').value = d ? d.dailyRate || '' : '';
    $('empMonthly').value = d ? d.monthlyRate || '' : '';
    $('empDaily').placeholder = d && !d.dailyRate && d.computedDaily && d.computedDaily !== '0' ? 'auto: ' + d.computedDaily : '';
    $('empSss').value = d ? d.sss || '' : '';
    $('empPh').value = d ? d.philhealth || '' : '';
    $('empPi').value = d ? d.pagibig || '' : '';
    $('empCutoff').value = d ? d.contributionCutoff || 'SECOND' : 'SECOND';
    $('empRemarks').value = d ? d.remarks || '' : '';
    $('empActive').checked = d ? !!d.active : true;
    $('rateChangeWrap').hidden = true; $('rateNoteWrap').hidden = true;

    var del = $('empDeleteBtn');
    del.hidden = !d || linked;
    if (d) $('employeeDeleteForm').action = $('employeeDeleteForm').action.replace(/\/\d+\/delete$/, '/' + d.id + '/delete');

    var hist = $('rateHistory'); hist.textContent = '';
    var items = d && d.history ? d.history : [];
    $('rateHistoryWrap').hidden = items.length === 0;
    items.forEach(function (h) {
      var li = el('li');
      li.appendChild(el('span', 'rh-date', h.date));
      var txt = (h.newDaily != null || h.oldDaily != null) ? ('₱' + (h.oldDaily || '—') + ' → ₱' + (h.newDaily || '—') + ' /day') : '';
      if (h.newMonthly != null || h.oldMonthly != null) txt += (txt ? ' · ' : '') + '₱' + (h.oldMonthly || '—') + ' → ₱' + (h.newMonthly || '—') + ' /mo';
      li.appendChild(el('span', 'rh-rate', txt));
      li.appendChild(el('span', 'rh-note', (h.note || '') + (h.by ? ' · ' + h.by : '')));
      hist.appendChild(li);
    });
    open('employeeDialog');
  }

  document.addEventListener('click', function (e) {
    var t = e.target; if (!t.closest) return;
    if (t.closest('[data-hr-new-employee]')) { openEmployee(null); return; }
    var ed = t.closest('[data-hr-edit]');
    var row = !ed && page() === 'employees' ? t.closest('#empTable tr[data-emp-id]') : null;
    if (ed || row) { e.preventDefault(); e.stopPropagation(); openEmployee((ed || row).getAttribute(ed ? 'data-hr-edit' : 'data-emp-id')); }
  }, true);

  document.addEventListener('input', function (e) {
    var id = e.target.id;
    if (id === 'empSearch') empFilter();
    if (id === 'empDaily' || id === 'empMonthly') rateChanged();
  });
  document.addEventListener('change', function (e) {
    var id = e.target.id;
    if (id === 'empGroupFilter' || id === 'empStateFilter') empFilter();
    if (id === 'empRateDaily' || id === 'empRateMonthly') { setRateType(e.target.value); rateChanged(); }
  });

  // ================================================================== Payroll periods
  document.addEventListener('click', function (e) {
    var b = e.target.closest && e.target.closest('[data-hr-edit-period]');
    var custom = e.target.closest && e.target.closest('[data-open-dialog="customPeriodDialog"]');
    var form = $('periodForm');
    if (!form) return;
    if (b) {
      form.action = form.getAttribute('data-create-action') + '/' + b.getAttribute('data-id') + '/update';
      $('perStart').value = b.getAttribute('data-start'); $('perEnd').value = b.getAttribute('data-end');
      $('perNotes').value = b.getAttribute('data-notes') || '';
      $('customPeriodTitle').textContent = 'Change dates'; $('perEditHint').hidden = false;
      open('customPeriodDialog');
    } else if (custom) {
      form.action = form.getAttribute('data-create-action');
      $('customPeriodTitle').textContent = 'Payroll period'; $('perEditHint').hidden = true;
    }
  });

  // ================================================================== Loans
  function loanPreview() {
    var p = $('loanPreview'); if (!p) return;
    var total = num($('loanTotal').value), per = num($('loanPer').value);
    p.textContent = per > 0 && total > 0 ? 'About ' + Math.ceil(total / per) + ' cut-off(s) to finish.'
      : (per > 0 ? 'No total: deducted every cut-off until you stop it.' : '');
  }
  document.addEventListener('click', function (e) {
    if (!e.target.closest) return;
    var add = e.target.closest('[data-hr-new-loan]'), ed = e.target.closest('[data-hr-edit-loan]');
    if (!add && !ed) return;
    var f = $('loanForm'); f.reset();
    $('loanId').value = ed ? ed.getAttribute('data-id') : '';
    $('loanDialogTitle').textContent = ed ? 'Edit schedule' : 'Add schedule';
    if (ed) {
      $('loanEmployee').value = ed.getAttribute('data-employee');
      $('loanKind').value = ed.getAttribute('data-kind');
      $('loanDesc').value = ed.getAttribute('data-description') || '';
      $('loanTotal').value = ed.getAttribute('data-total') || '';
      $('loanPer').value = ed.getAttribute('data-per') || '';
      $('loanStart').value = ed.getAttribute('data-start') || '';
    }
    loanPreview();
    open('loanDialog');
  });
  document.addEventListener('input', function (e) { if (e.target.id === 'loanTotal' || e.target.id === 'loanPer') loanPreview(); });

  // ================================================================== Time sheet
  var TS = null; // { panel, days: [{working,sunday}], hpd, grace, dirty: Set<tr> }

  function tsDirtyCount() { return TS && TS.dirty ? TS.dirty.size : 0; }
  function tsReset() { if (TS) TS.dirty = new Set(); }

  function tsInit() {
    var panel = $('tsPanel');
    if (!panel) { TS = null; return; }
    var days = [];
    panel.querySelectorAll('thead th.ts-day').forEach(function (th) {
      days.push({ date: th.getAttribute('data-date'), working: th.getAttribute('data-working') === 'true', sunday: th.getAttribute('data-sunday') === 'true' });
    });
    TS = { panel: panel, days: days, hpd: num(panel.getAttribute('data-hpd')) || 8, grace: num(panel.getAttribute('data-grace')),
           locked: panel.getAttribute('data-locked') === 'true', dirty: new Set() };
    panel.querySelectorAll('tr.ts-row').forEach(function (tr) {
      tr.querySelectorAll('td input').forEach(function (i) { i.setAttribute('autocomplete', 'off'); if (!i.classList.contains('rem')) i.setAttribute('inputmode', 'decimal'); });
      tsRecalc(tr);
    });
    tsUpdateSave();
  }

  function tsInputs(tr) { return tr.querySelectorAll('td input'); }

  function tsRecalc(tr) {
    var inp = tsInputs(tr), d = TS.days, hpd = TS.hpd;
    var reg = 0, ot = 0, sun = 0, late = 0, abs = 0;
    for (var k = 0; k < d.length; k++) {
      var r = num(inp[k * 3].value), o = num(inp[k * 3 + 1].value), l = num(inp[k * 3 + 2].value);
      if (d[k].sunday) sun += r; else { reg += r; ot += o; }
      late += Math.max(0, l - TS.grace);
      if (d[k].working && r < hpd) abs += hpd - r;
    }
    function f(v) { return v ? (Math.round(v * 100) / 100).toString() : ''; }
    tr.querySelector('.t-days').textContent = f(reg / hpd);
    tr.querySelector('.t-ot').textContent = f(ot);
    tr.querySelector('.t-sun').textContent = f(sun / hpd);
    tr.querySelector('.t-late').textContent = f(late);
    var a = tr.querySelector('.t-abs'); a.textContent = f(abs / hpd); a.classList.toggle('has', abs > 0);
  }

  function tsValid(input) {
    if (input.classList.contains('rem')) return true;
    var v = input.value.trim();
    return v === '' || /^\d+(\.\d+)?$/.test(v);
  }

  function tsMark(input) {
    var tr = input.closest('tr.ts-row'); if (!tr || !TS) return;
    input.classList.add('dirty');
    input.classList.toggle('bad', !tsValid(input));
    TS.dirty.add(tr);
    tsRecalc(tr);
    tsUpdateSave();
  }

  function tsUpdateSave() {
    var b = $('tsSave'); if (!b || !TS) return;
    var n = TS.dirty.size;
    b.disabled = n === 0;
    $('tsDirtyCount').textContent = n ? '(' + n + ' row' + (n > 1 ? 's' : '') + ')' : '';
  }

  function tsVisibleRows() { return Array.prototype.filter.call(TS.panel.querySelectorAll('tr.ts-row'), function (r) { return !r.hidden; }); }

  function tsFill(rows) {
    rows.forEach(function (tr) {
      var inp = tsInputs(tr), changed = false;
      TS.days.forEach(function (d, k) {
        var i = inp[k * 3];
        if (d.working && i.value.trim() === '') { i.value = String(TS.hpd); i.classList.add('dirty'); changed = true; }
      });
      if (changed) { TS.dirty.add(tr); tsRecalc(tr); }
    });
    tsUpdateSave();
  }

  function tsSave() {
    if (!TS || TS.dirty.size === 0) return;
    var bad = TS.panel.querySelector('input.bad');
    if (bad) { bad.focus(); tsFlash('Fix the highlighted cell first: numbers only.', true); return; }
    var cells = [], rows = [];
    TS.dirty.forEach(function (tr) {
      var e = Number(tr.getAttribute('data-e')), inp = tsInputs(tr);
      TS.days.forEach(function (d, k) {
        var r = inp[k * 3].value.trim(), o = inp[k * 3 + 1].value.trim(), l = inp[k * 3 + 2].value.trim();
        cells.push({ e: e, d: d.date, reg: r === '' ? null : Number(r), ot: o === '' ? null : Number(o), late: l === '' ? null : Math.round(Number(l)) });
      });
      var nd = tr.querySelector('input.nd').value.trim();
      rows.push({ e: e, nd: nd === '' ? null : Number(nd), remarks: tr.querySelector('input.rem').value });
    });
    var btn = $('tsSave'); btn.disabled = true;
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    var csrf = window.CBM && window.CBM.csrf && window.CBM.csrf();
    if (csrf) headers[csrf.header] = csrf.token;
    fetch(TS.panel.getAttribute('data-save-url'), { method: 'POST', headers: headers, credentials: 'same-origin', body: JSON.stringify({ cells: cells, rows: rows }) })
      .then(function (r) { return r.json().catch(function () { return { ok: false, message: 'Save failed (' + r.status + ').' }; }); })
      .then(function (res) {
        if (!res.ok) { tsFlash(res.message || 'Could not save.', true); tsUpdateSave(); return; }
        TS.panel.querySelectorAll('input.dirty').forEach(function (i) { i.classList.remove('dirty'); });
        var n = TS.dirty.size; TS.dirty = new Set(); tsUpdateSave();
        document.dispatchEvent(new CustomEvent('app:data-changed'));
        tsFlash('Saved ' + n + ' row' + (n > 1 ? 's' : '') + '.', false);
      })
      .catch(function () { tsFlash('Could not reach the server. Your changes are still here; try again.', true); tsUpdateSave(); });
  }

  function tsFlash(msg, error) {
    var f = $('tsFlash');
    if (!f) { f = el('div', 'ts-flash'); f.id = 'tsFlash'; document.body.appendChild(f); }
    f.textContent = msg; f.classList.toggle('err', !!error); f.classList.add('show');
    clearTimeout(f._t); f._t = setTimeout(function () { f.classList.remove('show'); }, error ? 5000 : 2200);
  }

  function tsMove(input, dRow, dCol) {
    var tr = input.closest('tr.ts-row'); if (!tr) return;
    var inp = Array.prototype.slice.call(tsInputs(tr)), idx = inp.indexOf(input);
    if (dCol) { var n = inp[idx + dCol]; if (n) { n.focus(); n.select(); } return; }
    var rows = tsVisibleRows(), ri = rows.indexOf(tr), next = rows[ri + dRow];
    if (next) { var t = tsInputs(next)[idx]; if (t) { t.focus(); t.select(); } }
  }

  document.addEventListener('input', function (e) {
    if (e.target.closest && e.target.closest('#tsGrid') && e.target.tagName === 'INPUT') tsMark(e.target);
    if (e.target.id === 'tsSearch') {
      var q = e.target.value.trim().toLowerCase();
      TS && TS.panel.querySelectorAll('tbody tr').forEach(function (tr) {
        if (tr.classList.contains('ts-row')) tr.hidden = !!q && tr.getAttribute('data-search').indexOf(q) < 0;
      });
      TS && TS.panel.querySelectorAll('tr.ts-group').forEach(function (g) {
        var n = g.nextElementSibling, any = false;
        while (n && !n.classList.contains('ts-group')) { if (!n.hidden) any = true; n = n.nextElementSibling; }
        g.hidden = !any;
      });
    }
  });

  document.addEventListener('keydown', function (e) {
    var i = e.target;
    if ((e.ctrlKey || e.metaKey) && e.key === 's' && TS && $('tsSave')) { e.preventDefault(); tsSave(); return; }
    if (!(i.closest && i.closest('#tsGrid')) || i.tagName !== 'INPUT') return;
    if (e.key === 'ArrowDown' || e.key === 'Enter') { e.preventDefault(); tsMove(i, 1, 0); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); tsMove(i, -1, 0); }
    else if (e.key === 'ArrowRight' && i.selectionStart === i.value.length && !i.classList.contains('rem')) { e.preventDefault(); tsMove(i, 0, 1); }
    else if (e.key === 'ArrowLeft' && i.selectionStart === 0 && i.selectionEnd === 0) { e.preventDefault(); tsMove(i, 0, -1); }
  });

  document.addEventListener('focusin', function (e) {
    if (e.target.closest && e.target.closest('#tsGrid') && e.target.tagName === 'INPUT' && !e.target.classList.contains('rem')) {
      try { e.target.select(); } catch (ignore) {}
    }
  });

  // Paste a block copied from Excel (tab-separated) starting at the focused cell.
  document.addEventListener('paste', function (e) {
    var start = e.target;
    if (!(start.closest && start.closest('#tsGrid')) || start.tagName !== 'INPUT' || !TS || TS.locked) return;
    var text = (e.clipboardData || window.clipboardData).getData('text');
    if (!text || (text.indexOf('\t') < 0 && text.indexOf('\n') < 0)) return;
    e.preventDefault();
    var lines = text.replace(/\r/g, '').split('\n');
    if (lines.length && lines[lines.length - 1] === '') lines.pop();
    var rows = tsVisibleRows(), tr0 = start.closest('tr.ts-row'), r0 = rows.indexOf(tr0);
    var c0 = Array.prototype.indexOf.call(tsInputs(tr0), start), count = 0;
    lines.forEach(function (line, r) {
      var tr = rows[r0 + r]; if (!tr) return;
      var inp = tsInputs(tr);
      line.split('\t').forEach(function (val, c) {
        var target = inp[c0 + c]; if (!target) return;
        val = val.trim();
        if (!target.classList.contains('rem')) val = val.replace(/,/g, '');
        if (!target.classList.contains('rem') && val !== '' && !/^\d+(\.\d+)?$/.test(val)) val = '';
        target.value = val; target.classList.add('dirty'); target.classList.remove('bad'); count++;
      });
      TS.dirty.add(tr); tsRecalc(tr);
    });
    tsUpdateSave();
    tsFlash('Pasted ' + count + ' cells into ' + Math.min(lines.length, rows.length - r0) + ' row(s). Review, then Save.', false);
  });

  document.addEventListener('click', function (e) {
    if (!e.target.closest || !TS) return;
    if (e.target.closest('#tsSave')) { tsSave(); return; }
    if (e.target.closest('#tsFillAll')) { tsFill(tsVisibleRows()); tsFlash('Filled empty working days. Review, then Save.', false); return; }
    var f = e.target.closest('.ts-fill');
    if (f) { tsFill([f.closest('tr.ts-row')]); return; }
    // Leaving the page with unsaved changes
    var a = e.target.closest('a[href]');
    if (a && tsDirtyCount() > 0 && !a.target && !a.hasAttribute('download')) {
      if (!window.confirm('You have time sheet changes that are not saved. Leave without saving?')) { e.preventDefault(); e.stopImmediatePropagation(); return; }
      tsReset();
    }
  }, true);

  window.addEventListener('beforeunload', function (e) {
    if (tsDirtyCount() > 0) { e.preventDefault(); e.returnValue = ''; }
  });

  // ================================================================== Pay sheet
  var PS = null;

  function psFilter() {
    var q = ($('psSearch') && $('psSearch').value || '').trim().toLowerCase();
    var hide = $('psHideZero') && $('psHideZero').checked;
    document.querySelectorAll('#psTable tr.ps-row').forEach(function (tr) {
      tr.hidden = (q && tr.getAttribute('data-search').indexOf(q) < 0) || (hide && tr.getAttribute('data-zero') === 'true');
    });
  }

  function slipRow(table, label, qty, amount, desc, removeId) {
    var tr = el('tr');
    var td = el('td'); td.appendChild(el('span', null, label));
    if (desc) td.appendChild(el('span', 'd', desc));
    tr.appendChild(td);
    tr.appendChild(el('td', 'q', qty || ''));
    var a = el('td', 'a', money(amount));
    tr.appendChild(a);
    var x = el('td', 'x');
    if (removeId) {
      var dlg = $('payDialog');
      var f = el('form'); f.method = 'post'; f.action = dlg.getAttribute('data-delete-base') + removeId + '/delete';
      f.setAttribute('data-confirm', 'Remove this line?'); f.setAttribute('data-confirm-tone', 'danger'); f.setAttribute('data-confirm-ok', 'Remove');
      var meta = document.querySelector('meta[name="_csrf"]');
      if (meta) { var h = el('input'); h.type = 'hidden'; h.name = '_csrf'; h.value = meta.content; f.appendChild(h); }
      var b = el('button', 'btn btn-sm btn-ghost btn-icon'); b.type = 'submit'; b.setAttribute('aria-label', 'Remove');
      b.appendChild(el('i', 'bi bi-x-lg')); f.appendChild(b); x.appendChild(f);
    }
    tr.appendChild(x);
    table.appendChild(tr);
  }

  function d2(v) { var n = num(v); return (Math.round(n * 100) / 100).toString(); }

  function openPay(id) {
    var l = PS && PS.data[String(id)]; if (!l) return;
    var dlg = $('payDialog'), locked = dlg.getAttribute('data-locked') === 'true';
    $('payDialogTitle').textContent = l.name;
    $('payDialogSub').textContent = [l.position, l.payGroup, '₱' + money(l.dailyRate) + '/day · ₱' + money(l.hourlyRate) + '/hr',
      l.rateType === 'MONTHLY' ? 'monthly ₱' + money(l.monthlyRate) : null].filter(Boolean).join(' · ');
    var w = $('payWarnings'); w.textContent = '';
    (l.warnings || []).forEach(function (m) { var a = el('div', 'alert alert-warning py-2 mb-2'); a.textContent = m; w.appendChild(a); });
    if (l.remarks) { var r = el('div', 'alert alert-info py-2 mb-2'); r.textContent = 'Time sheet note: ' + l.remarks; w.appendChild(r); }

    var e = $('payEarnings'); e.textContent = '';
    slipRow(e, l.rateType === 'MONTHLY' ? 'Salary (half month)' : 'Salary + allowance', l.rateType === 'MONTHLY' ? '' : d2(l.regDays) + ' d', l.basicPay);
    if (num(l.otPay)) slipRow(e, 'Overtime', d2(l.otHours) + ' h', l.otPay);
    if (num(l.sunPay)) slipRow(e, 'Sunday', d2(l.sunDays) + ' d', l.sunPay);
    if (num(l.sunOtPay)) slipRow(e, 'Sunday OT', d2(l.sunOtHours) + ' h', l.sunOtPay);
    if (num(l.specialHolPay)) slipRow(e, 'Holiday 30%', d2(l.specialHolDays) + ' d', l.specialHolPay);
    if (num(l.regularHolPay)) slipRow(e, 'Holiday 2X', d2(l.regularHolDays) + ' d', l.regularHolPay);
    if (num(l.ndPay)) slipRow(e, 'Night differential', d2(l.ndHours) + ' h', l.ndPay);
    (l.earnings || []).forEach(function (i) { slipRow(e, i.label, '', i.amount, i.description, !locked && i.adjustmentId && !i.auto ? i.adjustmentId : null); });
    var te = el('tr', 'tot'); te.appendChild(el('td', null, 'Total pay')); te.appendChild(el('td')); te.appendChild(el('td', 'a', money(l.grossPay))); te.appendChild(el('td')); e.appendChild(te);

    var d = $('payDeductions'); d.textContent = '';
    if (num(l.lateAmount)) slipRow(d, 'Lates', l.lateMinutes + ' min', l.lateAmount);
    if (num(l.absentAmount)) slipRow(d, 'Absent', d2(l.absentDays) + ' d', l.absentAmount);
    else if (num(l.absentDays) && l.rateType !== 'MONTHLY') slipRow(d, 'Absent (not paid)', d2(l.absentDays) + ' d', 0);
    (l.deductions || []).forEach(function (i) {
      var desc = i.description || '';
      if (i.auto && !i.adjustmentId) desc = (desc ? desc + ' · ' : '') + 'automatic';
      slipRow(d, i.label, '', i.amount, desc, !locked && i.adjustmentId && !i.auto ? i.adjustmentId : null);
    });
    var td = el('tr', 'tot'); td.appendChild(el('td', null, 'Total deductions')); td.appendChild(el('td')); td.appendChild(el('td', 'a', money(l.totalDeductions))); td.appendChild(el('td')); d.appendChild(td);

    $('payNet').textContent = '₱' + money(l.netPay);
    $('payNet').classList.toggle('neg', num(l.netPay) < 0);
    if ($('adjEmployee')) { $('adjEmployee').value = l.employeeId; $('adjAmount').value = ''; $('adjDesc').value = ''; }
    $('paySlipLink').href = dlg.getAttribute('data-slip-base') + '?employee=' + l.employeeId;
    open('payDialog');
  }

  function psInit() {
    var dlg = $('payDialog');
    if (!dlg) { PS = null; return; }
    PS = { data: readJson('psData', {}) };
    var openId = dlg.getAttribute('data-open');
    if (openId) {
      openPay(openId);
      try { var u = new URL(window.location.href); u.searchParams.delete('open'); history.replaceState(history.state, '', u.toString()); } catch (ignore) {}
    }
  }

  document.addEventListener('click', function (e) {
    var tr = e.target.closest && e.target.closest('#psTable tr.ps-row');
    if (tr) { e.preventDefault(); openPay(tr.getAttribute('data-ps')); }
  });
  document.addEventListener('input', function (e) { if (e.target.id === 'psSearch') psFilter(); });
  document.addEventListener('change', function (e) { if (e.target.id === 'psHideZero') psFilter(); });

  // ================================================================== init
  function init() {
    if (page() === 'employees') { EMP = readJson('hrEmployeesData', {}); empFilter(); }
    tsInit();
    psInit();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
  document.addEventListener('app:navigated', init);
})();

/* Biometric devices and time logs */
(function () {
  'use strict';
  function $(id) { return document.getElementById(id); }
  function open(id) { if (window.CBM && window.CBM.openDialog) window.CBM.openDialog(id); else { var d = $(id); if (d && !d.open) d.showModal(); } }
  document.addEventListener('click', function (e) {
    if (!e.target.closest) return;
    var nd = e.target.closest('[data-hr-new-device]'), ed = e.target.closest('[data-hr-edit-device]');
    if (nd || ed) {
      $('deviceForm').reset();
      $('devId').value = ed ? ed.getAttribute('data-id') : '';
      $('devSn').value = ed ? ed.getAttribute('data-sn') : '';
      $('devSn').readOnly = !!ed;
      $('devName').value = ed ? (ed.getAttribute('data-name') || '') : '';
      $('devLoc').value = ed ? (ed.getAttribute('data-location') || '') : '';
      $('deviceDialogTitle').textContent = ed ? (ed.textContent.trim() === 'Approve' ? 'Approve device' : 'Edit device') : 'Add device';
      open('deviceDialog');
      return;
    }
    var ap = e.target.closest('[data-hr-add-punch]');
    if (ap) {
      var f = $('punchDialog').querySelector('form'); f.reset();
      if (ap.getAttribute('data-employee')) $('pEmp').value = ap.getAttribute('data-employee');
      var d = ap.getAttribute('data-date');
      if (!d) { var now = new Date(); d = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0'); }
      $('pDate').value = d;
      open('punchDialog');
    }
  });
})();
