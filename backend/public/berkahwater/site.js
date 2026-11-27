(() => {
  const button = document.querySelector('.nav-toggle');
  const nav = document.querySelector('#nav-links');
  if (!button || !nav) return;
  const info = nav.querySelector('.nav-info');
  const close = () => {
    nav.classList.remove('open');
    button.setAttribute('aria-expanded', 'false');
    if (info) info.open = false;
  };
  button.addEventListener('click', () => {
    if (nav.classList.contains('open')) close();
    else { nav.classList.add('open'); button.setAttribute('aria-expanded', 'true'); }
  });
  nav.querySelectorAll('a').forEach(a => a.addEventListener('click', close));
  document.addEventListener('click', e => {
    if (!nav.contains(e.target) && !button.contains(e.target)) close();
    else if (info && !info.contains(e.target)) info.open = false;
  });
  document.addEventListener('keydown', e => {
    if (e.key !== 'Escape') return;
    if (info?.open) { info.open = false; info.querySelector('summary')?.focus(); }
    else if (nav.classList.contains('open')) { close(); button.focus(); }
  });
})();
