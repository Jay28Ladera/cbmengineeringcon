/* Accounting pages: bill lines, voucher bill picking, journal entry balancing. Delegated so partial navigation works. */
(function () {
  function num(v) { var n = parseFloat(String(v == null ? '' : v).replace(/,/g, '')); return isNaN(n) ? 0 : n; }
  function peso(n) { return n.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }

  // ---------------------------------------------------------------- bill form
  function billCalc(form) {
    var gross = 0;
    form.querySelectorAll('tr[data-bill-line]').forEach(function (tr) {
      var q = num(tr.querySelector('[name=l_qty]').value), p = num(tr.querySelector('[name=l_price]').value);
      var a = Math.round(q * p * 100) / 100; gross += a;
      var out = tr.querySelector('[data-amt]'); if (out) out.textContent = peso(a);
    });
    var mode = (form.querySelector('[name=vatMode]') || {}).value, rate = num((form.querySelector('[name=vatRate]') || {}).value) || 12;
    var vat = 0, total = gross;
    if (mode === 'INCLUSIVE') vat = Math.round(gross * rate / (100 + rate) * 100) / 100;
    else if (mode === 'EXCLUSIVE') { vat = Math.round(gross * rate) / 100; total = gross + vat; }
    var ewt = num((form.querySelector('[name=ewtRate]') || {}).value);
    var set = function (k, v) { var el = form.querySelector('[data-total=' + k + ']'); if (el) el.textContent = peso(v); };
    set('net', total - vat); set('vat', vat); set('gross', total); set('ewt', Math.round((total - vat) * ewt) / 100);
    set('pay', total - Math.round((total - vat) * ewt) / 100);
  }

  // ---------------------------------------------------------------- voucher form
  function dvCalc(form) {
    var g = 0, w = 0;
    form.querySelectorAll('tr[data-dv-bill]').forEach(function (tr) {
      var tick = tr.querySelector('[data-tick]'), on = tick && tick.checked;
      tr.classList.toggle('row-selected', !!on);
      var pay = tr.querySelector('[data-pay]'), rate = tr.querySelector('[data-ewt]');
      pay.disabled = !on; rate.disabled = !on;
      var hid = tr.querySelector('[data-tick-hidden]'); if (hid) hid.value = on ? 'true' : 'false';
      if (!on) { tr.querySelector('[data-row-ewt]').textContent = '—'; tr.querySelector('[data-row-net]').textContent = '—'; return; }
      var gross = num(pay.value), share = num(tr.getAttribute('data-net-share')) || 1;
      var e = Math.round(gross * share * num(rate.value)) / 100;
      g += gross; w += e;
      tr.querySelector('[data-row-ewt]').textContent = peso(e);
      tr.querySelector('[data-row-net]').textContent = peso(gross - e);
    });
    var set = function (k, v) { var el = form.querySelector('[data-dv=' + k + ']'); if (el) el.textContent = peso(v); };
    set('gross', g); set('ewt', w); set('net', g - w);
    var m = form.querySelector('[name=method]:checked');
    form.querySelectorAll('[data-cheque-only]').forEach(function (x) { x.hidden = !m || m.value !== 'CHEQUE'; });
  }

  // ---------------------------------------------------------------- manual journal
  function jeCalc(form) {
    var d = 0, c = 0;
    form.querySelectorAll('tr[data-je-line]').forEach(function (tr) { d += num(tr.querySelector('[name=j_debit]').value); c += num(tr.querySelector('[name=j_credit]').value); });
    d = Math.round(d * 100) / 100; c = Math.round(c * 100) / 100;
    form.querySelector('[data-je=debit]').textContent = peso(d);
    form.querySelector('[data-je=credit]').textContent = peso(c);
    var diff = form.querySelector('[data-je=diff]');
    var ok = d > 0 && d === c;
    diff.textContent = ok ? 'Balanced' : 'Out of balance by ' + peso(Math.abs(d - c));
    diff.className = ok ? 'text-success fw-semibold' : 'text-danger fw-semibold';
    var btn = form.querySelector('[data-je-submit]'); if (btn) btn.disabled = !ok;
  }

  function calc(el) {
    var f = el.closest && el.closest('form'); if (!f) return;
    if (f.hasAttribute('data-bill-form')) billCalc(f);
    if (f.hasAttribute('data-dv-form')) dvCalc(f);
    if (f.hasAttribute('data-je-form')) jeCalc(f);
  }
  document.addEventListener('input', function (e) { calc(e.target); });
  document.addEventListener('change', function (e) { calc(e.target); });

  document.addEventListener('click', function (e) {
    var add = e.target.closest && e.target.closest('[data-add-row]');
    if (add) {
      var tb = document.getElementById(add.getAttribute('data-add-row')); if (!tb) return;
      var rows = tb.querySelectorAll('tr'); var last = rows[rows.length - 1]; if (!last) return;
      var tr = last.cloneNode(true);
      tr.querySelectorAll('input').forEach(function (i) { if (i.type !== 'hidden' || i.name === 'l_poline') i.value = i.name === 'l_qty' ? '1' : ''; });
      tr.querySelectorAll('select[name=j_account], select[name=j_job]').forEach(function (s) { s.value = ''; });
      tr.querySelectorAll('[data-amt]').forEach(function (x) { x.textContent = '0.00'; });
      tb.appendChild(tr); calc(tb); e.preventDefault(); return;
    }
    var rm = e.target.closest && e.target.closest('[data-remove-row]');
    if (rm) {
      var row = rm.closest('tr'), body = row.parentNode;
      if (body.querySelectorAll('tr').length > 1) { row.remove(); calc(body); }
      e.preventDefault(); return;
    }
    var all = e.target.closest && e.target.closest('[data-pay-all]');
    if (all) {
      var tr2 = all.closest('tr'); tr2.querySelector('[data-tick]').checked = true;
      tr2.querySelector('[data-pay]').value = all.getAttribute('data-pay-all'); calc(tr2); e.preventDefault();
    }
  });

  function init() {
    document.querySelectorAll('form[data-bill-form], form[data-dv-form], form[data-je-form]').forEach(function (f) {
      if (f.hasAttribute('data-bill-form')) billCalc(f);
      if (f.hasAttribute('data-dv-form')) dvCalc(f);
      if (f.hasAttribute('data-je-form')) jeCalc(f);
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
  document.addEventListener('app:navigated', init);
})();

/* Client progress billing: live figures as the % to date are typed. */
(function () {
  function num(v) { var n = parseFloat(String(v == null ? '' : v).replace(/,/g, '')); return isNaN(n) ? 0 : n; }
  function r2(n) { return Math.round(n * 100) / 100; }
  function peso(n) { return n.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
  function calc(f) {
    var toDate = 0, prev = 0;
    f.querySelectorAll('tr[data-sb-line]').forEach(function (tr) {
      var v = num(tr.getAttribute('data-value')), pp = num(tr.getAttribute('data-prev-pct')), pa = num(tr.getAttribute('data-prev-amt'));
      var p = Math.min(100, Math.max(pp, num(tr.querySelector('input').value)));
      var amt = p >= 100 ? v : Math.max(pa, r2(v * p / 100));
      tr.querySelector('[data-this-pct]').textContent = (p - pp).toFixed(2) + '%';
      tr.querySelector('[data-this-amt]').textContent = peso(amt - pa);
      tr.querySelector('[data-to-date]').textContent = peso(amt);
      toDate += amt; prev += pa;
    });
    var carried = num(f.getAttribute('data-carried'));
    toDate += carried; prev += carried;
    var gross = r2(toDate - prev), dp = num(f.getAttribute('data-dp')), ret = num(f.getAttribute('data-ret'));
    var unrec = Math.max(0, num(f.getAttribute('data-unrecouped')));
    var last = toDate >= num(f.getAttribute('data-contract')) - 0.005;
    var rec = last ? unrec : Math.min(unrec, r2(gross * dp / 100));
    var rt = r2(gross * ret / 100), due = r2(gross - rec - rt);
    var vat = f.getAttribute('data-vat') === 'true' ? r2((gross - rec) * 12 / 112) : 0;
    var set = function (k, v) { var el = f.querySelector('[data-sb=' + k + ']'); if (el) el.textContent = peso(v); };
    set('todate', toDate); set('prev', prev); set('gross', gross); set('rec', -rec); set('ret', -rt); set('due', due); set('vat', vat);
  }
  function form(el) { return el && el.closest ? el.closest('form[data-sb-form]') : null; }
  document.addEventListener('input', function (e) { var f = form(e.target); if (f) calc(f); });
  document.addEventListener('click', function (e) {
    var a = e.target.closest && e.target.closest('[data-sb-full]'); if (!a) return;
    e.preventDefault(); var tr = a.closest('tr'); tr.querySelector('input').value = '100'; calc(form(tr));
  });
  function init() { document.querySelectorAll('form[data-sb-form]').forEach(calc); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
  document.addEventListener('app:navigated', init);
})();

/* Collection form: tax withheld defaults to the client's CWT rate on the amount net of VAT. */
(function () {
  function num(v) { var n = parseFloat(String(v == null ? '' : v).replace(/,/g, '')); return isNaN(n) ? 0 : n; }
  function r2(n) { return Math.round(n * 100) / 100; }
  function peso(n) { return n.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
  function calc(f, changed) {
    var rate = num(f.getAttribute('data-cwt')), factor = num(f.getAttribute('data-factor')) || 1, A = 0, W = 0;
    f.querySelectorAll('tr[data-cr-row]').forEach(function (tr) {
      var on = tr.querySelector('[data-cr-tick]').checked, amt = tr.querySelector('[data-cr-amt]'), cwt = tr.querySelector('[data-cr-cwt]');
      amt.disabled = !on; cwt.disabled = !on; tr.classList.toggle('row-selected', on);
      if (!on) { tr.querySelector('[data-cr-cash]').textContent = '—'; return; }
      if (changed !== cwt && (cwt.value === '' || cwt.dataset.auto === '1')) { cwt.value = r2(num(amt.value) * factor * rate / 100).toFixed(2); cwt.dataset.auto = '1'; }
      if (changed === cwt) cwt.dataset.auto = '0';
      var a = num(amt.value), w = num(cwt.value); A += a; W += w;
      tr.querySelector('[data-cr-cash]').textContent = peso(a - w);
    });
    var set = function (k, v) { var el = f.querySelector('[data-cr=' + k + ']'); if (el) el.textContent = peso(v); };
    set('applied', A); set('cwt', W); set('cash', A - W);
    var m = f.querySelector('[name=method]');
    f.querySelectorAll('[data-cr-cheque]').forEach(function (x) { x.hidden = m && m.value !== 'CHEQUE'; });
  }
  function form(el) { return el && el.closest ? el.closest('form[data-cr-form]') : null; }
  document.addEventListener('input', function (e) { var f = form(e.target); if (f) calc(f, e.target); });
  document.addEventListener('change', function (e) { var f = form(e.target); if (f) calc(f, e.target); });
  function init() { document.querySelectorAll('form[data-cr-form]').forEach(function (f) { calc(f, null); }); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
  document.addEventListener('app:navigated', init);
})();

/* Cash advance liquidation: receipts + cash returned; the rest goes to payroll. */
(function () {
  function num(v) { var n = parseFloat(String(v == null ? '' : v).replace(/,/g, '')); return isNaN(n) ? 0 : n; }
  function peso(n) { return n.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
  function calc(f) {
    var s = 0; f.querySelectorAll('[data-ca-amt]').forEach(function (i) { s += num(i.value); });
    var r = num((f.querySelector('[data-ca-ret]') || {}).value), a = num(f.getAttribute('data-amount'));
    f.querySelector('[data-ca=spent]').textContent = peso(s); f.querySelector('[data-ca=ret]').textContent = peso(r);
    var left = Math.round((a - s - r) * 100) / 100, el = f.querySelector('[data-ca=left]');
    el.textContent = left < 0 ? 'Over by ' + peso(-left) : peso(left); el.className = left < 0 ? 'text-danger' : '';
  }
  document.addEventListener('input', function (e) { var f = e.target.closest && e.target.closest('form[data-ca-form]'); if (f) calc(f); });
})();

/* Bank reconciliation: cleared balance and difference update as items are ticked. */
(function () {
  function num(v) { var n = parseFloat(String(v == null ? '' : v).replace(/,/g, '')); return isNaN(n) ? 0 : n; }
  function peso(n) { return n.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
  function calc(f) {
    var c = num(f.getAttribute('data-opening'));
    f.querySelectorAll('tr[data-recon-row] input[type=checkbox]').forEach(function (b) {
      if (b.checked) c += num(b.getAttribute('data-amount'));
      b.closest('tr').classList.toggle('row-selected', b.checked);
    });
    c = Math.round(c * 100) / 100;
    var st = f.querySelector('[data-recon-stmt]'), d = st ? Math.round((num(st.value) - c) * 100) / 100 : null;
    f.querySelector('[data-recon=cleared]').textContent = peso(c);
    if (d !== null) {
      var el = f.querySelector('[data-recon=diff]'); el.textContent = peso(d); el.className = 'kpi-value ' + (d === 0 ? 'text-success' : 'text-danger'); el.style.fontSize = '1.6rem';
      var fin = f.querySelector('[data-recon-finish]'); if (fin) fin.disabled = d !== 0;
    }
  }
  function form(el) { return el && el.closest ? el.closest('form[data-recon-form]') : null; }
  document.addEventListener('change', function (e) {
    var f = form(e.target); if (!f) return;
    if (e.target.matches('[data-recon-all]')) f.querySelectorAll('tr[data-recon-row]:not([hidden]) input[type=checkbox]').forEach(function (b) { if (!b.disabled) b.checked = e.target.checked; });
    calc(f);
  });
  document.addEventListener('input', function (e) { var f = form(e.target); if (f) calc(f); });
  function init() { document.querySelectorAll('form[data-recon-form]').forEach(calc); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
  document.addEventListener('app:navigated', init);
})();

/* Fixed assets: life by category, fields for how the asset is in the books, and the monthly amount. */
(function () {
  var HELP = {
    PAID: 'Posts Dr asset / Cr the bank now.',
    BOOKED: 'No entry: the cost is already in the asset account (e.g. a supplier bill coded to 1500–1550). Enter depreciation already booked, if any.',
    OPENING: 'Posts Dr asset / Cr opening balance equity, and the depreciation to date to accumulated depreciation.'
  };
  function num(v) { var n = parseFloat(v); return isNaN(n) ? 0 : n; }
  function sync(f) {
    var how = (f.querySelector('[data-fa-how]:checked') || {}).value || 'PAID';
    f.querySelectorAll('[data-fa-show]').forEach(function (el) { el.hidden = el.getAttribute('data-fa-show').split(' ').indexOf(how) < 0; });
    var cost = num(f.cost.value), salvage = num(f.salvage.value), months = Math.round(num(f.lifeYears.value) * 12);
    var monthly = months > 0 && cost > salvage ? (cost - salvage) / months : 0;
    var help = f.querySelector('[data-fa-help]');
    if (help) help.textContent = HELP[how] + (monthly > 0 ? ' Depreciation: ₱' + monthly.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' a month for ' + months + ' months.' : '');
  }
  document.addEventListener('change', function (e) {
    var f = e.target.closest && e.target.closest('form[data-fa-form]'); if (!f) return;
    if (e.target.matches('[data-fa-category]')) { var o = e.target.selectedOptions[0]; if (o && o.getAttribute('data-life')) f.lifeYears.value = o.getAttribute('data-life'); }
    sync(f);
  });
  document.addEventListener('input', function (e) { var f = e.target.closest && e.target.closest('form[data-fa-form]'); if (f) sync(f); });
  function init() { document.querySelectorAll('form[data-fa-form]').forEach(sync); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
  document.addEventListener('app:navigated', init);
})();
