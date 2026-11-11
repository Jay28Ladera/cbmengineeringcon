/* Sign-in pages: show / hide password and check that two password fields match. */
(function () {
  'use strict';
  document.addEventListener('click', function (event) {
    var btn = event.target.closest && event.target.closest('.password-toggle');
    if (!btn) return;
    var input = btn.parentElement.querySelector('input');
    var show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    btn.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
    var icon = btn.querySelector('.bi');
    icon.classList.toggle('bi-eye', !show);
    icon.classList.toggle('bi-eye-slash', show);
  });

  var confirm = document.querySelector('[data-match]');
  if (confirm) {
    var check = function () {
      var other = document.getElementById(confirm.getAttribute('data-match'));
      confirm.setCustomValidity(other && other.value !== confirm.value ? 'Passwords do not match.' : '');
    };
    confirm.addEventListener('input', check);
    var source = document.getElementById(confirm.getAttribute('data-match'));
    if (source) source.addEventListener('input', check);
  }

  document.addEventListener('submit', function (event) {
    var button = event.target.querySelector('button[type=submit]');
    if (button && !event.defaultPrevented) button.classList.add('is-busy');
  });
  window.addEventListener('pageshow', function () {
    document.querySelectorAll('.is-busy').forEach(function (b) { b.classList.remove('is-busy'); });
  });
})();
