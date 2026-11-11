/*
 * Quotation editor: add / remove priced items and show line totals and the grand total
 * live as you type. The server recalculates everything when the quotation is saved.
 */
(function () {
  'use strict';

  var peso = new Intl.NumberFormat('en-PH', { style: 'currency', currency: 'PHP', minimumFractionDigits: 2 });

  function num(input) {
    var v = input ? parseFloat(input.value) : NaN;
    return isFinite(v) ? v : 0;
  }

  function recalc() {
    var list = document.getElementById('lineItems');
    if (!list) return;
    var subtotal = 0;
    list.querySelectorAll('.line-row').forEach(function (row) {
      var amount = num(row.querySelector('[name=quantity]')) * num(row.querySelector('[name=unitPrice]'));
      amount = Math.round(amount * 100) / 100;
      subtotal += amount;
      var cell = row.querySelector('.line-total');
      if (cell) cell.textContent = peso.format(amount);
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
  }

  function set(id, value) { var n = document.getElementById(id); if (n) n.textContent = value; }

  function newRow() {
    var row = document.createElement('div');
    row.className = 'line-row';
    row.innerHTML =
      '<input class="form-control description" name="description" placeholder="Description of work or material" maxlength="500" required>' +
      '<input class="form-control" type="number" name="quantity" min="0.001" step="0.001" placeholder="Qty" required>' +
      '<input class="form-control" name="unit" placeholder="Unit" maxlength="40" value="lot">' +
      '<input class="form-control" type="number" name="unitPrice" min="0" step="0.01" placeholder="0.00" required>' +
      '<div class="line-total">₱0.00</div>' +
      '<button class="btn btn-ghost btn-icon remove-line" type="button" aria-label="Remove item" title="Remove item"><i class="bi bi-x-lg"></i></button>';
    return row;
  }

  document.addEventListener('click', function (event) {
    var t = event.target;
    if (!t || !t.closest) return;
    if (t.closest('#addLine')) {
      var list = document.getElementById('lineItems');
      if (!list) return;
      var row = newRow();
      list.appendChild(row);
      row.querySelector('.description').focus();
      recalc();
      return;
    }
    var remove = t.closest('.remove-line');
    if (remove) {
      var container = remove.closest('#lineItems');
      var rows = container ? container.querySelectorAll('.line-row') : [];
      if (rows.length > 1) remove.closest('.line-row').remove();
      else remove.closest('.line-row').querySelectorAll('input').forEach(function (i) { if (i.name !== 'unit') i.value = ''; });
      recalc();
    }
  });

  document.addEventListener('input', function (event) {
    if (event.target && event.target.closest && event.target.closest('#quoteItemsForm')) recalc();
  });

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', recalc);
  else recalc();
  document.addEventListener('app:navigated', recalc);
})();
