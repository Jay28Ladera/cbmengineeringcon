/*
 * Clients (inquiries) page: new-inquiry dialog, inquiry details dialog, quotation draft dialog
 * and the search box. Delegated from `document`, so it works after sidebar navigation.
 */
(function () {
  'use strict';

  function byId(id) { return document.getElementById(id); }
  function text(id, value) { var n = byId(id); if (n) n.textContent = value || '—'; }

  function jsonPost(url, body) {
    var headers = { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' };
    return fetch(url, { method: 'POST', body: body, headers: headers, credentials: 'same-origin' }).then(readJson);
  }
  function jsonGet(url) {
    return fetch(url, { headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }, credentials: 'same-origin' }).then(readJson);
  }
  function readJson(response) {
    var type = response.headers.get('Content-Type') || '';
    if (type.indexOf('application/json') === -1) {
      // Usually an expired session that redirected to the login page.
      throw new Error(response.redirected ? 'Your session has expired. Reload the page and sign in again.' : 'Unexpected response from the server (' + response.status + ').');
    }
    return response.json().then(function (data) {
      if (!response.ok) throw new Error(data.error || data.message || 'Request failed (' + response.status + ').');
      return data;
    });
  }

  function el(tag, cls, content) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (content != null) node.textContent = content;
    return node;
  }

  function statusPill(status) {
    var s = (status || 'NEW');
    return el('span', 'status status-' + s.toLowerCase(), s.replace(/_/g, ' '));
  }

  // ---------- search ----------
  function filterRows() {
    var input = byId('inquirySearch');
    var rows = byId('inquiryRows');
    if (!input || !rows) return;
    var q = input.value.trim().toLowerCase();
    var shown = 0;
    rows.querySelectorAll('tr').forEach(function (row) {
      var match = q === '' || row.textContent.toLowerCase().indexOf(q) !== -1;
      row.hidden = !match;
      if (match) shown++;
    });
    var none = byId('inquiryNoMatch');
    if (none) none.hidden = shown !== 0 || q === '';
  }
  document.addEventListener('input', function (e) { if (e.target && e.target.id === 'inquirySearch') filterRows(); });

  // ---------- add a row after saving ----------
  function addRow(item) {
    var rows = byId('inquiryRows');
    if (!rows) return;
    var tr = el('tr', 'row-link');
    tr.setAttribute('data-inquiry-id', item.id);

    tr.appendChild(el('td', 'text-nowrap text-muted', item.createdAt));
    var contact = el('td'); contact.appendChild(el('div', 'cell-main', item.fullName)); contact.appendChild(el('div', 'cell-sub', item.email)); tr.appendChild(contact);
    var project = el('td'); project.appendChild(el('div', 'cell-main', item.projectName)); project.appendChild(el('div', 'cell-sub', item.projectLocation)); tr.appendChild(project);
    tr.appendChild(el('td', 'cell-wrap', item.servicesNeeded));
    var st = el('td'); st.appendChild(statusPill(item.status)); tr.appendChild(st);

    var action = el('td', 'actions');
    var button = el('button', 'btn btn-sm btn-dark js-create-quotation');
    button.type = 'button';
    button.setAttribute('data-inquiry-id', item.id);
    button.innerHTML = '<i class="bi bi-file-earmark-plus"></i>';
    button.appendChild(document.createTextNode('Create quotation'));
    action.appendChild(button);
    tr.appendChild(action);

    rows.prepend(tr);
    var wrap = byId('inquiryTableWrap'); if (wrap) wrap.hidden = false;
    var empty = byId('inquiryEmptyState'); if (empty) empty.hidden = true;
    var count = byId('inquiryCount'); if (count) count.textContent = String(rows.querySelectorAll('tr').length);
    filterRows();
  }

  function showPageAlert(message) {
    var box = byId('inquiryPageAlert');
    if (!box) return;
    box.textContent = message;
    box.hidden = false;
    clearTimeout(showPageAlert.t);
    showPageAlert.t = setTimeout(function () { box.hidden = true; }, 6000);
  }

  // ---------- save new inquiry ----------
  document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!form || form.id !== 'newInquiryForm') return;
    event.preventDefault();

    var errorBox = byId('inquiryFormError');
    var save = byId('saveInquiryButton');
    errorBox.hidden = true;

    if (!form.querySelector('input[name="services"]:checked')) {
      errorBox.textContent = 'Select at least one service needed.';
      errorBox.hidden = false;
      form.querySelector('input[name="services"]').focus();
      return;
    }
    if (!form.reportValidity()) return;

    save.classList.add('is-busy');
    jsonPost(form.action.replace(/\/$/, '') + '/ajax', new FormData(form)).then(function (data) {
      addRow(data);
      form.reset();
      byId('newInquiryDialog').close();
      showPageAlert('Inquiry saved for ' + data.fullName + '.');
      document.dispatchEvent(new CustomEvent('app:data-changed'));
    }).catch(function (err) {
      errorBox.textContent = err.message;
      errorBox.hidden = false;
      errorBox.scrollIntoView({ block: 'nearest' });
    }).finally(function () {
      save.classList.remove('is-busy');
    });
  });

  // ---------- inquiry details ----------
  function openDetails(id) {
    var dialog = byId('inquiryDetailDialog');
    if (!dialog) return;
    byId('inquiryDetailLoading').hidden = false;
    byId('inquiryDetailContent').hidden = true;
    byId('inquiryDetailError').hidden = true;
    dialog.setAttribute('data-inquiry-id', id);
    dialog.showModal();

    jsonGet('/Clients/' + encodeURIComponent(id) + '/details').then(function (d) {
      text('detName', d.fullName); text('detCompany', d.company); text('detEmail', d.email); text('detPhone', d.phone);
      text('detContactPref', d.preferredContactMethod); text('detProject', d.projectName); text('detLocation', d.projectLocation);
      text('detProperty', d.propertyType); text('detWork', d.workNeeded); text('detArea', d.floorAreaOrQuantity);
      text('detBudget', d.estimatedBudget); text('detStart', d.targetStartDate); text('detEnd', d.targetCompletionDate);
      text('detSiteVisit', d.siteVisit); text('detServices', d.servicesNeeded); text('detDetails', d.projectDetails);
      text('detReceived', d.createdAt);
      var link = byId('detPlans');
      if (d.plansUrl && /^https?:\/\//i.test(d.plansUrl)) { link.href = d.plansUrl; link.textContent = d.plansUrl; link.parentElement.hidden = false; }
      else link.parentElement.hidden = true;
      var status = byId('detStatus'); status.replaceChildren(statusPill(d.status));

      var open = byId('detOpenQuotation'), create = byId('detCreateQuotation');
      open.hidden = !d.quotationId; create.hidden = !!d.quotationId;
      if (d.quotationId) open.href = '/Quotations/' + d.quotationId;
      create.setAttribute('data-inquiry-id', d.id);

      byId('inquiryDetailLoading').hidden = true;
      byId('inquiryDetailContent').hidden = false;
    }).catch(function (err) {
      byId('inquiryDetailLoading').hidden = true;
      var box = byId('inquiryDetailError'); box.textContent = err.message; box.hidden = false;
    });
  }

  // ---------- quotation draft ----------
  function createQuotation(id, button) {
    var dialog = byId('quotationDialog');
    if (!dialog) return;
    var detail = byId('inquiryDetailDialog');
    if (detail && detail.open) detail.close();

    byId('quotationError').hidden = true;
    byId('quotationContent').hidden = true;
    byId('quotationLoading').hidden = false;
    byId('openQuotationPage').hidden = true;
    if (button) button.classList.add('is-busy');
    dialog.showModal();

    var body = new FormData();
    var csrf = window.CBM && window.CBM.csrf();
    if (csrf) body.append('_csrf', csrf.token);

    jsonPost('/Clients/' + encodeURIComponent(id) + '/quotation/modal', body).then(function (d) {
      text('quotationNumber', d.quotationNumber);
      text('quoteClient', d.clientName);
      text('quoteCompanyLine', d.company);
      text('quoteContactLine', [d.email, d.phone].filter(Boolean).join(' · '));
      text('quoteProject', d.projectName);
      text('quoteLocation', d.projectLocation);
      text('quoteServices', d.servicesNeeded);
      text('quoteDetails', d.projectDetails);
      text('quoteWork', d.workNeeded); text('quoteArea', d.area); text('quoteBudget', d.budget); text('quoteSiteVisit', d.siteVisit);
      var open = byId('openQuotationPage');
      open.href = '/Quotations/' + encodeURIComponent(d.id);
      open.hidden = false;
      byId('quotationContent').hidden = false;

      // Update the table row in place
      var row = document.querySelector('#inquiryRows tr[data-inquiry-id="' + id + '"]');
      if (row) {
        var st = row.querySelector('.status'); if (st) st.replaceWith(statusPill('QUOTATION_CREATED'));
        var cell = row.querySelector('td.actions');
        if (cell) {
          var a = el('a', 'btn btn-sm btn-outline');
          a.href = '/Quotations/' + d.id;
          a.innerHTML = '<i class="bi bi-file-earmark-text"></i>';
          a.appendChild(document.createTextNode(d.quotationNumber));
          cell.replaceChildren(a);
        }
      }
      document.dispatchEvent(new CustomEvent('app:data-changed'));
    }).catch(function (err) {
      var box = byId('quotationError'); box.textContent = err.message; box.hidden = false;
    }).finally(function () {
      byId('quotationLoading').hidden = true;
      if (button) button.classList.remove('is-busy');
    });
  }

  document.addEventListener('click', function (event) {
    var t = event.target;
    if (!t || !t.closest) return;
    var create = t.closest('.js-create-quotation');
    if (create) { event.preventDefault(); createQuotation(create.getAttribute('data-inquiry-id'), create); return; }
    var row = t.closest('#inquiryRows tr[data-inquiry-id]');
    if (row && !t.closest('a, button, form')) openDetails(row.getAttribute('data-inquiry-id'));
  });

  // The quotation dialog's "Open quotation" link closes the dialog before navigating.
  document.addEventListener('click', function (event) {
    var link = event.target.closest && event.target.closest('#openQuotationPage, #detOpenQuotation');
    if (!link) return;
    var d = link.closest('dialog'); if (d && d.open) d.close();
  }, true);

  // ---------- open "New inquiry" from ?openInquiry=1 (top bar button) ----------
  function maybeOpen() {
    var params = new URLSearchParams(window.location.search);
    if (params.get('openInquiry') !== '1') return;
    var dialog = byId('newInquiryDialog');
    if (!dialog) return;
    params.delete('openInquiry');
    var clean = window.location.pathname + (params.toString() ? '?' + params : '') + window.location.hash;
    window.history.replaceState(window.history.state, '', clean);
    if (!dialog.open) window.CBM.openDialog('newInquiryDialog');
  }

  function init() { filterRows(); maybeOpen(); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
  document.addEventListener('app:navigated', init);
})();
