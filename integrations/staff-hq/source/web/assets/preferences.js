(() => {
  try {
    const theme = localStorage.getItem('tr.hq.theme');
    if (['dark', 'cherry', 'light'].includes(theme)) document.documentElement.dataset.theme = theme;
    if (localStorage.getItem('tr.hq.largeText') === '1') document.documentElement.dataset.largeText = '1';
  } catch (_) { /* Appearance still works when browser storage is disabled. */ }
})();
