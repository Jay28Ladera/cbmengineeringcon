/*
 * Quotation editor: priced lines with type, inventory material, our cost and markup.
 * Shows line amounts, totals and the internal costing (cost, profit, margin) live as you type.
 * The server recalculates everything when the quotation is saved.
 */
(function () {
  'use strict';

  var peso = new Intl.NumberFormat('en-PH', { style: 'currency', currency: 'PHP', minimumFractionDigits: 2 });
  function num(input) { var v = input ? parseFloat(String(input.value).replace(/,/g, '')) : NaN; return isFinite(v) ? v : 0; }
  function has(input) { return !!input && String(input.value).trim() !== '' && isFinite(parseFloat(input.value)); }
  function r2(v) { return Math.round(v * 100) / 100; }
  function set(id, value) { var n = document.getElementById(id); if (n) n.textContent = value; }
  function q(row, sel) { return row.querySelector(sel); }

  /* ---------- price hint under a material line ---------- */
  function hintText(o) {
    if (!o || !o.value) return '';
    var d = o.dataset, parts = [];
    if (d.latest) parts.push('Latest ' + peso.format(+d.latest) + (d.from ? ' from ' + d.from : '') + (d.on ? ' (' + d.on + ')' : ''));
    if (d.low && d.low !== d.latest) parts.push('lowest ' + peso.format(+d.low) + (d.lowfrom ? ' (' + d.lowfrom + ')' : ''));
    if (+d.onhand > 0) parts.push('in stock ' + d.onhand + ' ' + (d.unit || '') + ' @ ' + peso.format(+d.avg));
    else parts.push('none in stock');
    if (!d.latest && !(+d.avg > 0)) parts.unshift('No price on file yet');
    return parts.join(' · ');
  }

  function syncKind(row) {
    var kind = q(row, '.kind').value;
    row.dataset.kind = kind;
    if (kind === 'LABOR') {
      var qty = q(row, '[name=quantity]');
      if (!has(qty)) { qty.value = '1'; q(row, '.unit').value = 'lot'; }
    } else { ['.crew', '.days', '.rate'].forEach(function (s) { var i = q(row, s); if (i) i.value = ''; }); }
    var mat = q(row, '.mat');
    if (kind !== 'MATERIAL' && mat) mat.value = '';
    updateHint(row);
  }
  function updateHint(row) {
    var mat = q(row, '.mat'), h = q(row, '.qline-hint');
    if (h) h.textContent = row.dataset.kind === 'MATERIAL' && mat ? hintText(mat.options[mat.selectedIndex]) : '';
  }

  /* ---------- labor: workers × days × rate per day ---------- */
  function crewCost(row) {
    if (row.dataset.kind !== 'LABOR') return null;
    var c = q(row, '.crew'), d = q(row, '.days'), r = q(row, '.rate');
    if (!has(c) || !has(d) || !has(r)) return null;
    return r2(num(c) * num(d) * num(r));
  }
  /** Keeps the per-unit cost in step with the crew cost, so markup and price work the same way. */
  function syncCrew(row) {
    var cc = crewCost(row);
    if (cc === null) return;
    var qty = num(q(row, '[name=quantity]')) || 1;
    q(row, '.cost').value = (Math.round(cc / qty * 100) / 100).toFixed(2);
  }

  /* ---------- cost / markup / price ---------- */
  function defaultMarkup() { var m = document.getElementById('markupPct'); return has(m) ? num(m) : null; }
  function priceFromMarkup(row) {
    var cost = q(row, '.cost'), mk = q(row, '.mk'), price = q(row, '.price');
    if (has(cost) && has(mk)) price.value = r2(num(cost) * (1 + num(mk) / 100)).toFixed(2);
  }
  function markupFromPrice(row) {
    var cost = q(row, '.cost'), mk = q(row, '.mk'), price = q(row, '.price');
    if (has(cost) && num(cost) > 0 && has(price)) mk.value = (Math.round((num(price) / num(cost) - 1) * 1000) / 10).toString();
    else if (!has(cost)) mk.value = '';
  }

  function pickMaterial(row) {
    var mat = q(row, '.mat'), o = mat.options[mat.selectedIndex];
    if (o && o.value) {
      var d = o.dataset, desc = q(row, '.description');
      if (!desc.value.trim() || desc.dataset.auto === '1') { desc.value = d.name; desc.dataset.auto = '1'; }
      q(row, '.unit').value = d.unit || q(row, '.unit').value;
      if (d.cost) {
        q(row, '.cost').value = (+d.cost).toFixed(2);
        var mk = q(row, '.mk');
        if (!has(mk)) { var dm = defaultMarkup(); if (dm !== null) mk.value = dm; }
        if (has(mk)) priceFromMarkup(row); else markupFromPrice(row);
      }
    }
    updateHint(row);
  }

  /* ---------- totals ---------- */
  function recalc() {
    var list = document.getElementById('lineItems');
    if (!list) return;
    var subtotal = 0, cost = { MATERIAL: 0, LABOR: 0, SUBCON: 0, OTHER: 0 }, uncosted = 0, lines = 0;
    list.querySelectorAll('.qline').forEach(function (row) {
      var qty = num(q(row, '[name=quantity]')), amount = r2(qty * num(q(row, '.price')));
      subtotal += amount;
      q(row, '.line-total').textContent = peso.format(amount);
      var c = q(row, '.cost'), calc = q(row, '.qline-calc'), cc = crewCost(row);
      if (!q(row, '.description').value.trim()) { calc.textContent = ''; return; }
      lines++;
      if (cc !== null || has(c)) {
        var lc = cc !== null ? cc : r2(qty * num(c)), k = row.dataset.kind;
        cost[k === 'EQUIPMENT' ? 'OTHER' : (cost[k] === undefined ? 'OTHER' : k)] += lc;
        var p = amount - lc;
        calc.textContent = (cc !== null ? '= ' + (num(q(row, '.crew')) * num(q(row, '.days'))) + ' man-days · cost ' : 'Line cost ') + peso.format(lc) + ' · profit ' + peso.format(p);
        calc.classList.toggle('neg', p < 0);
      } else { uncosted++; calc.textContent = row.dataset.kind === 'LABOR' ? 'Enter workers, days and rate' : 'No cost yet'; calc.classList.remove('neg'); }
    });
    var discount = num(document.getElementById('discountAmount'));
    var rate = num(document.getElementById('taxRate'));
    var taxable = Math.max(subtotal - discount, 0);
    var tax = Math.round(taxable * rate) / 100;
    set('liveSubtotal', peso.format(subtotal));
    set('liveDiscount', '− ' + peso.format(discount));
    set('liveTaxRate', rate.toString());
    set('liveTax', peso.format(tax));
    set('liveTotal', peso.format(taxable + tax));
    var warn = document.getElementById('discountWarning');
    if (warn) warn.hidden = discount <= subtotal;

    var total = cost.MATERIAL + cost.LABOR + cost.SUBCON + cost.OTHER, profit = taxable - total;
    set('cMaterial', peso.format(cost.MATERIAL)); set('cLabor', peso.format(cost.LABOR));
    set('cSubcon', peso.format(cost.SUBCON)); set('cOther', peso.format(cost.OTHER));
    set('cTotal', peso.format(total)); set('cSell', peso.format(taxable)); set('cProfit', peso.format(profit));
    set('cMargin', taxable > 0 ? (Math.round(profit / taxable * 1000) / 10) + '%' : '—');
    var pe = document.getElementById('cProfit'); if (pe) pe.classList.toggle('text-danger', profit < 0);
    var uw = document.getElementById('uncostedWarn');
    if (uw) { uw.hidden = uncosted === 0; set('uncostedText', uncosted + ' of ' + lines + ' line(s) have no cost yet, so the profit shown is too high.'); }
  }

  function init() {
    document.querySelectorAll('#lineItems .qline').forEach(updateHint);
    recalc();
  }

  document.addEventListener('click', function (event) {
    var t = event.target;
    if (!t || !t.closest) return;
    if (t.closest('#addLine')) {
      var list = document.getElementById('lineItems'), tpl = document.getElementById('qlineTemplate');
      if (!list || !tpl) return;
      var row = tpl.content.firstElementChild.cloneNode(true);
      list.appendChild(row);
      var prev = list.querySelectorAll('.qline');
      if (prev.length > 1) { q(row, '.kind').value = q(prev[prev.length - 2], '.kind').value; syncKind(row); }
      var dm = defaultMarkup(); if (dm !== null) q(row, '.mk').value = dm;
      (row.dataset.kind === 'MATERIAL' ? q(row, '.mat') : q(row, '.description')).focus();
      recalc();
      return;
    }
    if (t.closest('#applyMarkup')) {
      var m = defaultMarkup();
      if (m === null) { var mi = document.getElementById('markupPct'); if (mi) mi.focus(); return; }
      document.querySelectorAll('#lineItems .qline').forEach(function (row) {
        if (has(q(row, '.cost'))) { q(row, '.mk').value = m; priceFromMarkup(row); }
      });
      recalc();
      return;
    }
    var remove = t.closest('.remove-line');
    if (remove) {
      var container = remove.closest('#lineItems');
      var rows = container ? container.querySelectorAll('.qline') : [];
      var row = remove.closest('.qline');
      if (rows.length > 1) row.remove();
      else row.querySelectorAll('input').forEach(function (i) { if (i.name !== 'unit') i.value = ''; });
      recalc();
    }
  });

  document.addEventListener('change', function (e) {
    var row = e.target.closest && e.target.closest('#lineItems .qline');
    if (!row) return;
    if (e.target.classList.contains('kind')) syncKind(row);
    if (e.target.classList.contains('mat')) pickMaterial(row);
    if (e.target.classList.contains('hr-rate') && e.target.value) {
      q(row, '.rate').value = (+e.target.value).toFixed(2);
      e.target.value = '';
      q(row, '.rate').dispatchEvent(new Event('input', { bubbles: true }));
    }
    recalc();
  });

  document.addEventListener('input', function (event) {
    var t = event.target;
    if (!t || !t.closest || !t.closest('#quoteItemsForm')) return;
    var row = t.closest('.qline');
    if (row) {
      if (t.classList.contains('description')) delete t.dataset.auto;
      if (t.classList.contains('crew') || t.classList.contains('days') || t.classList.contains('rate') || t.name === 'quantity') {
        if (crewCost(row) !== null) {
          syncCrew(row);
          if (!has(q(row, '.mk'))) { var dm0 = defaultMarkup(); if (dm0 !== null) q(row, '.mk').value = dm0; }
          if (has(q(row, '.mk'))) priceFromMarkup(row); else markupFromPrice(row);
        }
      }
      else if (t.classList.contains('mk')) priceFromMarkup(row);
      else if (t.classList.contains('cost')) { if (has(q(row, '.mk'))) priceFromMarkup(row); else { var dm = defaultMarkup(); if (dm !== null) { q(row, '.mk').value = dm; priceFromMarkup(row); } } }
      else if (t.classList.contains('price')) markupFromPrice(row);
    }
    recalc();
  });

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
  document.addEventListener('app:navigated', init);
})();
