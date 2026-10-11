(() => {
  const root = document.documentElement;
  const choice = document.querySelector('[data-theme-choice]');
  const large = document.querySelector('[data-large-text]');
  function save(key, value) { try { localStorage.setItem(key, value); } catch (_) {} }
  if (choice) {
    choice.value = root.dataset.theme || 'dark';
    choice.addEventListener('change', () => {
      root.dataset.theme = choice.value;
      save('tr.hq.theme', choice.value);
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', getComputedStyle(root).getPropertyValue('--bg').trim());
    });
  }
  if (large) {
    large.checked = root.dataset.largeText === '1';
    large.addEventListener('change', () => {
      root.dataset.largeText = large.checked ? '1' : '0';
      save('tr.hq.largeText', root.dataset.largeText);
    });
  }
  // Keep each table's actual column names beside its values when it becomes mobile cards.
  function labelTables() {
    document.querySelectorAll('.table-wrap table').forEach(table => {
      const headings = Array.from(table.querySelectorAll('thead tr:first-child th')).map(th => th.textContent.trim());
      table.querySelectorAll('tbody tr').forEach(row => {
        Array.from(row.children).forEach((cell, index) => {
          if (cell.tagName === 'TD' && cell.colSpan === 1) cell.dataset.label = headings[index] || '';
        });
      });
    });
  }
  labelTables();
  new MutationObserver(labelTables).observe(document.querySelector('main') || document.body, { childList: true, subtree: true });
})();
