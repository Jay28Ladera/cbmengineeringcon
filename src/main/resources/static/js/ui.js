/*
 * Shared behaviour for every signed-in page. Loaded once.
 *
 * All handlers are delegated from `document`, so they keep working after the sidebar
 * navigation swaps the page body (app-navigation.js), with no per-page setup.
 *
 *   - Sidebar collapse (desktop) and slide-in menu (mobile)
 *   - Breadcrumb in the top bar, read from <main data-section data-title>
 *   - <dialog> helpers: [data-open-dialog="id"], [data-close-dialog="id"], click outside to close
 *   - Confirmation for any form with data-confirm="Message"
 *       optional: data-confirm-title, data-confirm-ok, data-confirm-tone="danger|success|primary"
 *   - Busy state on submit buttons, which also stops double submissions
 *   - Clickable table rows: <tr class="row-link" data-href="/path">
 */
(function () {
  'use strict';

  var body = document.body;
  var COLLAPSE_KEY = 'cbm.sidebarCollapsed';
  var mobile = window.matchMedia('(max-width: 992px)');

  // ---------- Sidebar ----------
  function setCollapsed(collapsed) {
    body.classList.toggle('sidebar-collapsed', collapsed);
    try { localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0'); } catch (ignore) {}
    var btn = document.getElementById('sidebarToggle');
    if (btn) btn.setAttribute('aria-label', collapsed ? 'Expand menu' : 'Collapse menu');
  }

  function setMobileOpen(open) {
    body.classList.toggle('sidebar-open', open);
    var btn = document.getElementById('mobileMenuButton');
    if (btn) btn.setAttribute('aria-expanded', open ? 'true' : 'false');
  }

  function ensureOverlay() {
    if (document.querySelector('.sidebar-overlay')) return;
    var overlay = document.createElement('div');
    overlay.className = 'sidebar-overlay';
    overlay.addEventListener('click', function () { setMobileOpen(false); });
    body.appendChild(overlay);
  }

  try { if (localStorage.getItem(COLLAPSE_KEY) === '1' && !mobile.matches) body.classList.add('sidebar-collapsed'); } catch (ignore) {}
  ensureOverlay();

  document.addEventListener('click', function (event) {
    var t = event.target;
    if (!t || !t.closest) return;

    if (t.closest('#sidebarToggle')) { setCollapsed(!body.classList.contains('sidebar-collapsed')); return; }
    if (t.closest('#mobileMenuButton')) { setMobileOpen(!body.classList.contains('sidebar-open')); return; }

    // Collapsed sidebar: clicking a group icon expands the sidebar so the sub-links are reachable.
    var group = t.closest('.sidebar-group-toggle');
    if (group && body.classList.contains('sidebar-collapsed') && !mobile.matches) {
      setCollapsed(false);
      return;
    }

    // Close the mobile menu after choosing a page.
    var navLink = t.closest('#sidebar a.sidebar-link');
    if (navLink && mobile.matches) setTimeout(function () { setMobileOpen(false); }, 80);

    // Dialog open / close buttons
    var opener = t.closest('[data-open-dialog]');
    if (opener) { event.preventDefault(); openDialog(opener.getAttribute('data-open-dialog')); return; }
    var closer = t.closest('[data-close-dialog]');
    if (closer) { event.preventDefault(); var d = document.getElementById(closer.getAttribute('data-close-dialog')); if (d && d.open) d.close(); return; }

    // Click on the backdrop closes a dialog
    if (t.tagName === 'DIALOG' && t.open) {
      var r = t.getBoundingClientRect();
      var inside = event.clientX >= r.left && event.clientX <= r.right && event.clientY >= r.top && event.clientY <= r.bottom;
      if (!inside) t.close('cancel');
      return;
    }

    // Clickable rows (ignore clicks on buttons, links and forms inside the row)
    var row = t.closest('tr.row-link[data-href]');
    if (row && !t.closest('a, button, form, input, select, label')) {
      var link = document.createElement('a');
      link.href = row.getAttribute('data-href');
      link.style.display = 'none';
      body.appendChild(link);
      link.click();          // goes through app-navigation for a fast page change
      link.remove();
    }

    // Dismiss alerts
    var dismiss = t.closest('.alert [data-dismiss-alert]');
    if (dismiss) dismiss.closest('.alert').remove();
  });

  mobile.addEventListener('change', function () { setMobileOpen(false); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && body.classList.contains('sidebar-open')) setMobileOpen(false); });

  // ---------- Dialogs ----------
  function openDialog(id) {
    var dialog = document.getElementById(id);
    if (!dialog || dialog.open) return dialog;
    dialog.showModal();
    var focusable = dialog.querySelector('[autofocus], .dialog-body input:not([type=hidden]), .dialog-body select, .dialog-body textarea');
    if (focusable) setTimeout(function () { focusable.focus(); }, 30);
    return dialog;
  }
  window.CBM = window.CBM || {};
  window.CBM.openDialog = openDialog;

  // ---------- Confirmation ----------
  var confirmDialog = document.getElementById('confirmDialog');

  function askConfirm(options) {
    return new Promise(function (resolve) {
      if (!confirmDialog) { resolve(window.confirm(options.message)); return; }
      document.getElementById('confirmDialogTitle').textContent = options.title || 'Please confirm';
      document.getElementById('confirmDialogMessage').textContent = options.message || 'Are you sure?';
      var ok = document.getElementById('confirmDialogOk');
      ok.textContent = options.ok || 'Confirm';
      ok.className = 'btn ' + ({ danger: 'btn-primary', success: 'btn-success', dark: 'btn-dark' }[options.tone] || 'btn-primary');
      confirmDialog.classList.toggle('dialog-tone-danger', options.tone === 'danger');
      confirmDialog.returnValue = '';
      confirmDialog.addEventListener('close', function done() {
        confirmDialog.removeEventListener('close', done);
        resolve(confirmDialog.returnValue === 'ok');
      });
      confirmDialog.showModal();
      setTimeout(function () { ok.focus(); }, 30);
    });
  }
  window.CBM.confirm = askConfirm;

  document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!form || form.tagName !== 'FORM' || form.method === 'dialog') return;

    if (form.hasAttribute('data-confirm') && form.dataset.confirmed !== '1') {
      event.preventDefault();
      event.stopImmediatePropagation();
      var submitter = event.submitter;
      askConfirm({
        title: form.getAttribute('data-confirm-title'),
        message: form.getAttribute('data-confirm'),
        ok: form.getAttribute('data-confirm-ok'),
        tone: form.getAttribute('data-confirm-tone')
      }).then(function (yes) {
        if (!yes) return;
        form.dataset.confirmed = '1';
        if (form.requestSubmit) form.requestSubmit(submitter && submitter.form === form ? submitter : undefined);
        else form.submit();
      });
      return;
    }

    // Normal (full page) submission: show progress and block double clicks.
    if (!event.defaultPrevented && !form.hasAttribute('data-ajax')) {
      var button = event.submitter || form.querySelector('button[type=submit], button:not([type])');
      setTimeout(function () {
        if (event.defaultPrevented) return;
        if (button) button.classList.add('is-busy');
      }, 0);
    }
  }, true);

  // Clear busy buttons when returning with the browser back button (page restored from cache).
  window.addEventListener('pageshow', function () {
    document.querySelectorAll('.btn.is-busy').forEach(function (b) { b.classList.remove('is-busy'); });
    document.querySelectorAll('form[data-confirmed]').forEach(function (f) { delete f.dataset.confirmed; });
  });

  // ---------- Breadcrumb ----------
  function syncCrumbs() {
    var main = document.querySelector('main.content-area');
    var section = document.getElementById('crumbSection');
    var title = document.getElementById('crumbTitle');
    if (!main || !title) return;
    if (section) section.textContent = main.getAttribute('data-section') || '';
    title.textContent = main.getAttribute('data-title') || document.title.split('|')[0].trim();
  }
  window.CBM.syncCrumbs = syncCrumbs;

  // ---------- Small helpers used by page scripts ----------
  window.CBM.csrf = function () {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    return token && header ? { header: header.content, token: token.content } : null;
  };

  function onPage() {
    syncCrumbs();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', onPage);
  else onPage();
  document.addEventListener('app:navigated', onPage);
})();
