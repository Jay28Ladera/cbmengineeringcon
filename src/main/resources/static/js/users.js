/*
 * User management: fill the edit dialog from the row's data attributes and keep the
 * active tab in the URL (#pending), so returning to the page reopens the same tab.
 * Approve / reject / delete use the shared confirmation (data-confirm on each form).
 */
(function () {
  'use strict';

  document.addEventListener('click', function (event) {
    var button = event.target.closest && event.target.closest('.js-edit-user');
    if (!button) return;
    var d = button.dataset;
    document.getElementById('editUserId').value = d.id || '';
    document.getElementById('editName').value = d.name || '';
    document.getElementById('editEmail').value = d.email || '';
    document.getElementById('editUsername').value = d.username || '';
    var role = document.getElementById('editRole');
    role.value = (d.role || 'user').toLowerCase();
    if (!role.value) role.value = 'user';
    window.CBM.openDialog('editUserDialog');
  });

  document.addEventListener('shown.bs.tab', function (event) {
    if (!event.target.closest('#userTabs')) return;
    var target = event.target.getAttribute('data-bs-target');
    history.replaceState(history.state, '', window.location.pathname + (target === '#pending' ? '#pending' : ''));
  });

  function showTabFromHash() {
    if (window.location.hash !== '#pending' || !window.bootstrap) return;
    var tab = document.getElementById('pending-tab');
    if (tab) bootstrap.Tab.getOrCreateInstance(tab).show();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { setTimeout(showTabFromHash, 0); });
  else setTimeout(showTabFromHash, 0);
  document.addEventListener('app:navigated', showTabFromHash);
})();
