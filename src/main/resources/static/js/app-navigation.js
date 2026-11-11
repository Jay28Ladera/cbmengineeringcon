/*
 * Fast page changes for the signed-in app.
 *
 * Clicking an internal link fetches only the page body (the server renders the page's
 * th:fragment="page" when it sees the X-App-Navigation header; see WebConfig) and swaps
 * <main class="content-area">. The sidebar, top bar, styles and scripts stay loaded.
 *
 * Speed-ups:
 *   - Links are prefetched when the pointer rests on them, so most clicks render instantly.
 *   - A thin progress bar shows while a page loads.
 * Safety:
 *   - Open dialogs, Bootstrap modals and tooltips are closed before the swap.
 *   - Anything unexpected (session expired, error, non-HTML) falls back to a normal page load.
 */
(function () {
  'use strict';

  var FILE_PATTERN = /\.(pdf|xlsx?|csv|zip|docx?|png|jpe?g|webp|gif|svg|mp4|mp3)$/i;
  var PREFETCH_MS = 15000;
  var prefetchCache = new Map();   // url -> { at, promise }
  var activeController = null;
  var requestNumber = 0;
  var progress = document.getElementById('navProgress');
  var progressTimer = null;

  // ---------- progress bar ----------
  function progressStart() {
    if (!progress) return;
    clearTimeout(progressTimer);
    progress.style.transition = 'none';
    progress.style.width = '0';
    progress.classList.add('active');
    void progress.offsetWidth;
    progress.style.transition = '';
    progress.style.width = '35%';
    progressTimer = setTimeout(function () { progress.style.width = '75%'; }, 350);
  }
  function progressDone() {
    if (!progress) return;
    clearTimeout(progressTimer);
    progress.style.width = '100%';
    progressTimer = setTimeout(function () { progress.classList.remove('active'); progress.style.width = '0'; }, 220);
  }

  // ---------- which links we handle ----------
  function eligible(link, event) {
    if (!link || !link.href) return null;
    if (event && (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey)) return null;
    if (link.hasAttribute('download') || link.target || link.hasAttribute('data-bs-toggle') || link.hasAttribute('data-full-load')) return null;
    if (!document.querySelector('main.content-area')) return null;
    var url;
    try { url = new URL(link.href, window.location.href); } catch (e) { return null; }
    if (url.origin !== window.location.origin || !/^https?:$/.test(url.protocol)) return null;
    if (FILE_PATTERN.test(url.pathname)) return null;
    if (url.pathname === '/' || url.pathname === '/logout' || url.pathname === '/login') return null;
    if (url.pathname === window.location.pathname && url.search === window.location.search && url.hash) return null;
    return url;
  }

  function request(url, signal) {
    return fetch(url.href, {
      credentials: 'same-origin',
      signal: signal,
      headers: { 'X-App-Navigation': 'page', 'Accept': 'text/html' }
    }).then(function (response) {
      var type = response.headers.get('Content-Type') || '';
      var landed = new URL(response.url);
      // Session expired (redirected to login) or not an HTML page: let the browser handle it.
      if (landed.pathname === '/login' || type.indexOf('text/html') === -1) throw new Error('full-load');
      return response.text().then(function (html) { return { html: html, finalUrl: landed, status: response.status }; });
    });
  }

  function prefetch(url) {
    var key = url.href;
    var hit = prefetchCache.get(key);
    if (hit && Date.now() - hit.at < PREFETCH_MS) return hit.promise;
    var promise = request(url).catch(function (e) { prefetchCache.delete(key); throw e; });
    prefetchCache.set(key, { at: Date.now(), promise: promise });
    return promise;
  }

  function takePrefetched(url) {
    var hit = prefetchCache.get(url.href);
    prefetchCache.delete(url.href);
    if (hit && Date.now() - hit.at < PREFETCH_MS) return hit.promise;
    return null;
  }

  // ---------- clean-up before swapping ----------
  function closeOverlays() {
    document.querySelectorAll('dialog[open]').forEach(function (d) { d.close(); });
    if (window.bootstrap) {
      document.querySelectorAll('.modal.show').forEach(function (m) {
        var inst = bootstrap.Modal.getInstance(m); if (inst) inst.hide();
      });
      document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function (el) {
        var tip = bootstrap.Tooltip.getInstance(el); if (tip) tip.dispose();
      });
      document.querySelectorAll('.dropdown-menu.show').forEach(function (menu) {
        var toggle = menu.parentElement && menu.parentElement.querySelector('[data-bs-toggle="dropdown"]');
        var dd = toggle && bootstrap.Dropdown.getInstance(toggle); if (dd) dd.hide();
      });
    }
    document.querySelectorAll('.modal-backdrop').forEach(function (b) { b.remove(); });
    document.body.classList.remove('modal-open');
    document.body.style.removeProperty('overflow');
    document.body.style.removeProperty('padding-right');
  }

  function syncNavigation() {
    var current = (window.location.pathname.replace(/\/$/, '') || '/').toLowerCase();
    var best = null, bestLength = -1;
    document.querySelectorAll('#sidebar a.sidebar-link[href]').forEach(function (link) {
      link.classList.remove('active');
      var path;
      try { path = (new URL(link.href).pathname.replace(/\/$/, '') || '/').toLowerCase(); } catch (e) { return; }
      if ((path === current || current.indexOf(path + '/') === 0) && path.length > bestLength) { best = link; bestLength = path.length; }
    });
    if (!best) return;
    best.classList.add('active');
    var submenu = best.closest('.sidebar-submenu');
    if (submenu && !submenu.classList.contains('show')) {
      submenu.classList.add('show');
      var toggle = document.querySelector('[data-bs-target="#' + submenu.id + '"]');
      if (toggle) toggle.setAttribute('aria-expanded', 'true');
    }
  }

  // ---------- navigation ----------
  function navigate(url, push) {
    var main = document.querySelector('main.content-area');
    if (!main) { window.location.assign(url.href); return; }

    if (activeController) activeController.abort();
    activeController = new AbortController();
    var mine = ++requestNumber;
    main.classList.add('page-loading');
    progressStart();

    (takePrefetched(url) || request(url, activeController.signal)).then(function (result) {
      if (mine !== requestNumber) return;
      var doc = new DOMParser().parseFromString(result.html, 'text/html');
      var next = doc.querySelector('main.content-area');
      var live = document.querySelector('main.content-area');
      if (!next || !live) throw new Error('full-load');

      closeOverlays();
      live.replaceWith(next);
      var titleEl = doc.querySelector('title');
      var pageTitle = next.getAttribute('data-title');
      document.title = titleEl ? titleEl.textContent : (pageTitle ? pageTitle + ' | CBM Engineering & Construction' : document.title);

      var finalUrl = result.finalUrl.href + (url.hash || '');
      if (push) window.history.pushState({ app: true }, '', finalUrl);
      else if (finalUrl !== window.location.href) window.history.replaceState({ app: true }, '', finalUrl);

      syncNavigation();
      var target = url.hash ? document.getElementById(url.hash.slice(1)) : null;
      if (target) target.scrollIntoView(); else window.scrollTo(0, 0);
      var focusTarget = next.querySelector('h1');
      if (focusTarget) { focusTarget.setAttribute('tabindex', '-1'); focusTarget.focus({ preventScroll: true }); }
      document.dispatchEvent(new CustomEvent('app:navigated', { detail: { url: finalUrl } }));
    }).catch(function (error) {
      if (error.name === 'AbortError' || mine !== requestNumber) return;
      window.location.assign(url.href);
    }).finally(function () {
      if (mine !== requestNumber) return;
      var m = document.querySelector('main.content-area');
      if (m) m.classList.remove('page-loading');
      activeController = null;
      progressDone();
    });
  }
  window.CBM = window.CBM || {};
  window.CBM.navigate = function (href) { navigate(new URL(href, window.location.href), true); };

  document.addEventListener('click', function (event) {
    var link = event.target.closest && event.target.closest('a[href]');
    var url = eligible(link, event);
    if (!url) return;
    event.preventDefault();
    navigate(url, true);
  });

  // Prefetch on hover / touch (after a short pause so a passing pointer doesn't fetch).
  var hoverTimer = null;
  document.addEventListener('mouseover', function (event) {
    var link = event.target.closest && event.target.closest('a[href]');
    var url = eligible(link);
    clearTimeout(hoverTimer);
    if (!url || url.href === window.location.href) return;
    hoverTimer = setTimeout(function () { prefetch(url).catch(function () {}); }, 70);
  });
  document.addEventListener('touchstart', function (event) {
    var link = event.target.closest && event.target.closest('a[href]');
    var url = eligible(link);
    if (url) prefetch(url).catch(function () {});
  }, { passive: true });

  // Any form submission changes data: drop prefetched pages so nothing stale is shown.
  document.addEventListener('submit', function () { prefetchCache.clear(); }, true);
  document.addEventListener('app:data-changed', function () { prefetchCache.clear(); });

  window.addEventListener('popstate', function () { navigate(new URL(window.location.href), false); });

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', syncNavigation);
  else syncNavigation();
})();
