(() => {
  const sidebar = document.querySelector('[data-sidebar]');
  const toggle = document.querySelector('[data-menu-toggle]');
  const close = document.querySelector('[data-nav-close]');
  if (!sidebar || !toggle || !close) return;
  function sync() {
    const opened = sidebar.classList.contains('open');
    toggle.setAttribute('aria-expanded', String(opened));
    close.hidden = !opened;
    const mobile = matchMedia('(max-width:780px)').matches;
    document.body.classList.toggle('navigation-open', opened && mobile);
    const main = document.querySelector('main'); if (main) main.inert = opened && mobile;
  }
  function dismiss() { sidebar.classList.remove('open'); sync(); toggle.focus(); }
  close.addEventListener('click', dismiss);
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && sidebar.classList.contains('open')) dismiss(); });
  new MutationObserver(sync).observe(sidebar, { attributes:true, attributeFilter:['class'] });
  sync();
})();
