/*
 * User management: fills the edit dialog (account + access) from the page's JSON, keeps the
 * module checkboxes in step with the "Follow department" / "Choose modules" choice, opens a
 * user directly from /users?edit=ID (used by the org chart's "Manage access" button) and keeps
 * the active tab in the URL (#staff, #pending).
 */
(function () {
  'use strict';

  var MODULE_LABELS = null;

  function data() {
    var el = document.getElementById('usersData');
    try { return el ? JSON.parse(el.textContent || '{}') : {}; } catch (e) { return {}; }
  }

  function labels() {
    if (MODULE_LABELS) return MODULE_LABELS;
    MODULE_LABELS = {};
    document.querySelectorAll('#editModuleGrid input[name=modules]').forEach(function (cb) {
      var strong = cb.closest('label').querySelector('strong');
      MODULE_LABELS[cb.value] = strong ? strong.textContent : cb.value;
    });
    return MODULE_LABELS;
  }

  var current = null;

  function openEditor(id) {
    var d = data()[String(id)];
    if (!d) return;
    current = d;
    MODULE_LABELS = null;
    document.getElementById('editUserId').value = d.id;
    document.getElementById('editName').value = d.name || '';
    document.getElementById('editEmail').value = d.email || '';
    document.getElementById('editUsername').value = d.username || '';
    document.getElementById('editUserTitle').textContent = 'Edit ' + (d.name || 'user');
    document.getElementById('editUserSub').textContent = d.linked
      ? (d.position || 'On the org chart') + (d.department ? ' · ' + d.department : ' · no department on their box')
      : 'Not on the org chart';

    var admin = document.getElementById('editAdmin');
    admin.checked = !!d.admin;
    admin.disabled = !!(d.self && d.admin);           // can't remove your own admin rights
    document.getElementById('editAdminSelfNote').hidden = !(d.self && d.admin);
    // A disabled checkbox isn't submitted, so keep the value with a hidden field.
    var keep = document.getElementById('editAdminKeep');
    if (admin.disabled && !keep) {
      keep = document.createElement('input'); keep.type = 'hidden'; keep.name = 'admin'; keep.value = 'true'; keep.id = 'editAdminKeep';
      admin.parentNode.appendChild(keep);
    } else if (!admin.disabled && keep) keep.remove();

    var follow = document.getElementById('editFollow'), custom = document.getElementById('editCustom');
    var deptMods = d.departmentModules || [];
    var names = deptMods.map(function (k) { return labels()[k] || k; });
    document.getElementById('editFollowLabel').textContent = d.department ? 'Follow department: ' + d.department : 'Follow department';
    document.getElementById('editFollowHint').textContent = d.department
      ? (names.length ? names.join(', ') : 'This department has no modules yet (Department Access).')
      : (d.linked ? 'Their box has no department, so they only get the dashboard and org chart.'
                  : 'Not on the org chart: they keep access to all modules until linked to a box.');
    (d.followDepartment ? follow : custom).checked = true;
    setModules(d.followDepartment ? deptMods : (d.modules || []));
    sync();
    window.CBM.openDialog('editUserDialog');
  }

  function setModules(list) {
    document.querySelectorAll('#editModuleGrid input[name=modules]').forEach(function (cb) {
      cb.checked = list.indexOf(cb.value) !== -1;
    });
  }

  // Admin → modules don't apply. Follow department → show the department's modules, read-only.
  function sync() {
    var admin = document.getElementById('editAdmin').checked;
    var follow = document.getElementById('editFollow').checked;
    var box = document.getElementById('editAccessModules');
    // Keep the radios enabled so the choice is saved for if they stop being admin later.
    box.classList.toggle('is-disabled', admin);
    document.querySelectorAll('#editModuleGrid input[name=modules]').forEach(function (cb) {
      cb.disabled = admin || follow;
    });
    document.getElementById('editModuleGrid').classList.toggle('is-readonly', follow && !admin);
  }

  document.addEventListener('click', function (event) {
    var button = event.target.closest && event.target.closest('.js-edit-user');
    if (!button) return;
    openEditor(button.getAttribute('data-id'));
  });

  document.addEventListener('change', function (event) {
    var t = event.target;
    if (!t || !t.closest || !t.closest('#editUserForm')) return;
    if (t.id === 'editAdmin') sync();
    if (t.id === 'editFollow' && current) { setModules(current.departmentModules || []); sync(); }
    if (t.id === 'editCustom' && current) {
      // Start from what they have now (their department's modules, or their saved custom set).
      setModules(current.followDepartment ? (current.departmentModules || []) : (current.modules || []));
      sync();
    }
  });

  // Before submitting: disabled checkboxes aren't sent, which is what we want for "follow department".
  document.addEventListener('submit', function (event) {
    // Compare the element itself: the form has a field named "id", which hides form.id.
    if (event.target !== document.getElementById('editUserForm')) return;
    var admin = document.getElementById('editAdmin');
    if (admin.checked && current && !current.admin && !event.target.dataset.confirmedAdmin) {
      event.preventDefault();
      event.stopImmediatePropagation();
      window.CBM.confirm({ title: 'Make administrator', message: 'Give ' + (current.name || 'this person') +
          ' full admin access? They will be able to manage users, access and the org chart.', ok: 'Make admin', tone: 'dark' })
        .then(function (yes) {
          if (!yes) return;
          event.target.dataset.confirmedAdmin = '1';
          event.target.requestSubmit();
        });
    }
  }, true);

  document.addEventListener('shown.bs.tab', function (event) {
    if (!event.target.closest('#userTabs')) return;
    var target = event.target.getAttribute('data-bs-target');
    history.replaceState(history.state, '', window.location.pathname + (target === '#approved' ? '' : target));
  });

  function onLoad() {
    if (!document.getElementById('userTabs') || !window.bootstrap) return;
    var hash = window.location.hash;
    var tab = hash === '#pending' ? 'pending-tab' : hash === '#staff' ? 'staff-tab' : null;
    if (tab) bootstrap.Tab.getOrCreateInstance(document.getElementById(tab)).show();
    var params = new URLSearchParams(window.location.search);
    var edit = params.get('edit');
    if (edit) {
      params.delete('edit');
      history.replaceState(history.state, '', window.location.pathname + (params.toString() ? '?' + params : '') + window.location.hash);
      openEditor(edit);
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { setTimeout(onLoad, 0); });
  else setTimeout(onLoad, 0);
  document.addEventListener('app:navigated', onLoad);
})();
