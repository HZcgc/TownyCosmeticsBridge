document.addEventListener('DOMContentLoaded', () => {
  const toggle = document.querySelector('[data-menu-toggle]');
  const sidebar = document.querySelector('[data-sidebar]');
  if (toggle && sidebar) {
    toggle.addEventListener('click', () => sidebar.classList.toggle('open'));
  }

  document.querySelectorAll('[data-confirm]').forEach((button) => {
    button.addEventListener('click', (event) => {
      const message = button.getAttribute('data-confirm') || 'Continue?';
      if (!window.confirm(message)) event.preventDefault();
    });
  });

  document.querySelectorAll('[data-copy]').forEach((button) => {
    button.addEventListener('click', async () => {
      const selector = button.getAttribute('data-copy');
      const target = selector ? document.querySelector(selector) : null;
      if (!target) return;
      await navigator.clipboard.writeText('value' in target ? target.value : target.textContent.trim());
      const old = button.textContent;
      button.textContent = 'Copied';
      window.setTimeout(() => { button.textContent = old; }, 1400);
    });
  });

  document.querySelectorAll('[data-player-picker]').forEach((picker) => {
    setupPlayerPicker(picker);
  });

  document.querySelectorAll('[data-player-avatar]').forEach((image) => {
    setupAvatarFallback(image);
  });

  setupAttachmentPickers();
  setupSessionTimeout();
});

function setupAvatarFallback(image, player = null) {
  if (!image || image.dataset.avatarFallbackReady === '1') return;
  image.dataset.avatarFallbackReady = '1';

  const uuid = String((player && player.uuid) || image.dataset.avatarUuid || '').replace(/[^0-9a-f]/gi, '');
  const name = String((player && player.username) || image.dataset.avatarName || '').trim();
  const size = Math.max(24, Math.min(128, Number(image.dataset.avatarSize || image.width || 48) || 48));
  image.dataset.avatarName = name;
  image.dataset.avatarUuid = uuid;
  image.dataset.avatarSize = String(size);
  image.classList.add('skin-avatar');

  const candidates = [];
  if (uuid) {
    candidates.push('https://mc-heads.net/avatar/' + encodeURIComponent(uuid) + '/' + size);
    candidates.push('https://crafatar.com/avatars/' + encodeURIComponent(uuid) + '?size=' + size + '&overlay&default=MHF_Steve');
  }
  if (name) {
    candidates.push('https://mc-heads.net/avatar/' + encodeURIComponent(name) + '/' + size);
    candidates.push('https://minotar.net/helm/' + encodeURIComponent(name) + '/' + size + '.png');
  }
  candidates.push('https://mc-heads.net/avatar/MHF_Steve/' + size);

  const current = image.currentSrc || image.src;
  const remaining = [...new Set(candidates.filter((source) => source && source !== current))];
  image.addEventListener('error', () => {
    const next = remaining.shift();
    if (next) image.src = next;
  });
}

function setupAttachmentPickers() {
  const inputs = [...document.querySelectorAll('input[type="file"][name="attachments[]"]')];
  if (!inputs.length) return;

  const controllers = inputs.map((input) => enhanceAttachmentInput(input)).filter(Boolean);
  let active = controllers[0] || null;
  controllers.forEach((controller) => {
    controller.root.addEventListener('pointerdown', () => { active = controller; });
    controller.root.addEventListener('focusin', () => { active = controller; });
  });

  controllers.forEach((controller) => bindChunkedUpload(controller));

  document.addEventListener('paste', (event) => {
    const files = clipboardFiles(event.clipboardData);
    if (!files.length || !active) return;
    event.preventDefault();
    active.addFiles(files, true);
  });
}

function enhanceAttachmentInput(input) {
  if (input.dataset.clipboardReady === '1') return null;
  input.dataset.clipboardReady = '1';
  input.classList.add('attachment-native-input');

  const root = document.createElement('div');
  root.className = 'attachment-picker';

  const dropzone = document.createElement('div');
  dropzone.className = 'attachment-dropzone';
  dropzone.tabIndex = 0;
  dropzone.setAttribute('role', 'button');
  dropzone.setAttribute('aria-label', 'Paste, drop or choose evidence files');

  const icon = document.createElement('span');
  icon.className = 'paste-icon';
  icon.textContent = 'Ctrl+V';
  const copy = document.createElement('span');
  const title = document.createElement('strong');
  title.textContent = 'Paste a screenshot';
  const hint = document.createElement('small');
  hint.textContent = 'Press Ctrl+V, drag files here, or click to browse';
  copy.append(title, hint);
  dropzone.append(icon, copy);

  const queue = document.createElement('div');
  queue.className = 'attachment-queue';
  queue.hidden = true;

  input.parentNode.insertBefore(root, input);
  root.append(dropzone, input, queue);

  let previewUrls = [];
  const rebuildInput = (files) => {
    if (typeof DataTransfer === 'undefined') {
      showAttachmentMessage(queue, 'This browser cannot add clipboard files. Use Chrome, Edge or Firefox.', true);
      return false;
    }
    const transfer = new DataTransfer();
    files.forEach((file) => transfer.items.add(file));
    input.files = transfer.files;
    renderQueue();
    return true;
  };

  const renderQueue = () => {
    previewUrls.forEach((url) => URL.revokeObjectURL(url));
    previewUrls = [];
    queue.replaceChildren();
    const files = [...input.files];
    queue.hidden = files.length === 0;
    if (!files.length) {
      dropzone.classList.remove('has-files');
      return;
    }
    dropzone.classList.add('has-files');

    const summary = document.createElement('div');
    summary.className = 'attachment-summary';
    const total = files.reduce((bytes, file) => bytes + file.size, 0);
    summary.textContent = files.length + ' file' + (files.length === 1 ? '' : 's') + ' ready · ' + formatFileSize(total);
    queue.append(summary);

    const list = document.createElement('div');
    list.className = 'attachment-queue-list';
    files.forEach((file, index) => {
      const item = document.createElement('div');
      item.className = 'queued-file';

      if (file.type.startsWith('image/')) {
        const preview = document.createElement('img');
        const url = URL.createObjectURL(file);
        previewUrls.push(url);
        preview.src = url;
        preview.alt = '';
        item.append(preview);
      } else {
        const type = document.createElement('span');
        type.className = 'queued-file-type';
        type.textContent = (file.name.split('.').pop() || 'FILE').slice(0, 5).toUpperCase();
        item.append(type);
      }

      const meta = document.createElement('span');
      const name = document.createElement('strong');
      name.textContent = file.name;
      const size = document.createElement('small');
      size.textContent = formatFileSize(file.size);
      meta.append(name, size);

      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'remove-queued-file';
      remove.setAttribute('aria-label', 'Remove ' + file.name);
      remove.textContent = '×';
      remove.addEventListener('click', () => {
        rebuildInput(files.filter((_, fileIndex) => fileIndex !== index));
      });
      item.append(meta, remove);
      list.append(item);
    });
    queue.append(list);
  };

  const addFiles = (incoming, fromClipboard = false) => {
    const existing = [...input.files];
    const prepared = incoming.map((file, index) => fromClipboard ? nameClipboardFile(file, index) : file);
    const combined = [...existing];
    prepared.forEach((file) => {
      const duplicate = combined.some((current) => current.name === file.name
        && current.size === file.size && current.lastModified === file.lastModified);
      if (!duplicate) combined.push(file);
    });
    rebuildInput(combined);
    if (fromClipboard && prepared.length) {
      dropzone.classList.add('paste-success');
      window.setTimeout(() => dropzone.classList.remove('paste-success'), 900);
    }
  };

  dropzone.addEventListener('click', () => input.click());
  dropzone.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      input.click();
    }
  });
  ['dragenter', 'dragover'].forEach((name) => {
    dropzone.addEventListener(name, (event) => {
      event.preventDefault();
      dropzone.classList.add('dragging');
    });
  });
  ['dragleave', 'drop'].forEach((name) => {
    dropzone.addEventListener(name, (event) => {
      event.preventDefault();
      dropzone.classList.remove('dragging');
    });
  });
  dropzone.addEventListener('drop', (event) => {
    addFiles([...event.dataTransfer.files]);
  });
  input.addEventListener('change', renderQueue);

  return {
    root,
    input,
    queue,
    addFiles,
    setFiles: (files) => rebuildInput(files),
    message: (text, error = false) => showAttachmentMessage(queue, text, error)
  };
}

function bindChunkedUpload(controller) {
  const form = controller.input.closest('form');
  if (!form || form.dataset.chunkUploadReady === '1') return;
  form.dataset.chunkUploadReady = '1';

  form.addEventListener('submit', async (event) => {
    const files = [...controller.input.files];
    if (!files.length || form.dataset.chunkUploading === '1') return;
    event.preventDefault();
    form.dataset.chunkUploading = '1';

    const submit = event.submitter || form.querySelector('button[type="submit"]');
    const oldLabel = submit ? submit.textContent : '';
    if (submit) {
      submit.disabled = true;
      submit.textContent = 'Preparing upload…';
    }

    try {
      const formAction = form.getAttribute('action') || window.location.href;
      let ownerType = form.querySelector('[name="owner_type"]')?.value || '';
      let ownerId = Number(form.querySelector('[name="owner_id"]')?.value || 0);
      let redirectUrl = form.dataset.chunkRedirect || formAction;

      if (!ownerType || !ownerId) {
        const metadata = new FormData(form);
        metadata.delete(controller.input.name);
        const response = await fetch(formAction, {
          method: 'POST',
          body: metadata,
          credentials: 'same-origin',
          headers: { 'X-Requested-With': 'XMLHttpRequest', Accept: 'application/json' }
        });
        const result = await readJsonResponse(response);
        if (!response.ok || !result.ok) throw new Error(result.error || 'The case or document could not be saved.');
        ownerType = result.owner_type;
        ownerId = Number(result.owner_id);
        redirectUrl = result.redirect || redirectUrl;
        form.dataset.chunkRedirect = redirectUrl;
        for (const [name, value] of [['owner_type', ownerType], ['owner_id', String(ownerId)]]) {
          let hidden = form.querySelector(`input[name="${name}"]`);
          if (!hidden) {
            hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = name;
            form.append(hidden);
          }
          hidden.value = value;
        }
      }

      let completed = 0;
      const totalBytes = files.reduce((sum, file) => sum + file.size, 0);
      for (let fileIndex = 0; fileIndex < files.length; fileIndex += 1) {
        const file = files[fileIndex];
        await uploadFileInChunks(file, ownerType, ownerId, (fileBytes) => {
          const done = completed + fileBytes;
          const percentage = totalBytes > 0 ? Math.min(100, Math.round((done / totalBytes) * 100)) : 100;
          controller.message(`Uploading ${file.name} · ${percentage}%`);
          if (submit) submit.textContent = `Uploading… ${percentage}%`;
        });
        completed += file.size;
        controller.setFiles(files.slice(fileIndex + 1));
      }
      controller.message(`${files.length} file${files.length === 1 ? '' : 's'} uploaded successfully.`);
      if (submit) submit.textContent = 'Uploaded';
      const redirectTarget = new URL(String(redirectUrl), window.location.href);
      if (redirectTarget.origin !== window.location.origin) throw new Error('The upload redirect was rejected.');
      window.location.assign(redirectTarget.href);
    } catch (error) {
      controller.message(error instanceof Error ? error.message : 'The upload failed.', true);
      form.dataset.chunkUploading = '0';
      if (submit) {
        submit.disabled = false;
        submit.textContent = oldLabel;
      }
    }
  });
}

async function uploadFileInChunks(file, ownerType, ownerId, progress) {
  const transportSize = 524288;
  const chunkCount = Math.max(1, Math.ceil(file.size / transportSize));
  const init = new FormData();
  init.set('action', 'init');
  init.set('csrf_token', csrfToken());
  init.set('owner_type', ownerType);
  init.set('owner_id', String(ownerId));
  init.set('name', file.name || 'attachment');
  init.set('size', String(file.size));
  init.set('chunks', String(chunkCount));
  const initResponse = await fetch('upload.php', { method: 'POST', body: init, credentials: 'same-origin' });
  const initialized = await readJsonResponse(initResponse);
  if (!initResponse.ok || !initialized.ok) throw new Error(initialized.error || 'The upload could not be initialized.');

  const uploadId = initialized.upload_id;
  const chunkSize = Number(initialized.chunk_size) || transportSize;
  try {
    for (let index = 0; index < chunkCount; index += 1) {
      const start = index * chunkSize;
      const end = Math.min(file.size, start + chunkSize);
      const part = new FormData();
      part.set('action', 'chunk');
      part.set('csrf_token', csrfToken());
      part.set('upload_id', uploadId);
      part.set('index', String(index));
      part.set('chunk', file.slice(start, end), 'chunk.bin');
      await fetchChunkWithRetry(part);
      progress(end);
    }
    const complete = new FormData();
    complete.set('action', 'complete');
    complete.set('csrf_token', csrfToken());
    complete.set('upload_id', uploadId);
    const completeResponse = await fetch('upload.php', { method: 'POST', body: complete, credentials: 'same-origin' });
    const completed = await readJsonResponse(completeResponse);
    if (!completeResponse.ok || !completed.ok) throw new Error(completed.error || 'The uploaded file could not be finalized.');
  } catch (error) {
    const abort = new FormData();
    abort.set('action', 'abort');
    abort.set('csrf_token', csrfToken());
    abort.set('upload_id', uploadId);
    fetch('upload.php', { method: 'POST', body: abort, credentials: 'same-origin' }).catch(() => {});
    throw error;
  }
}

async function fetchChunkWithRetry(body) {
  let lastError = null;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const response = await fetch('upload.php', { method: 'POST', body, credentials: 'same-origin' });
      const result = await readJsonResponse(response);
      if (response.ok && result.ok) return;
      lastError = new Error(result.error || 'A file chunk was rejected.');
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError || new Error('A file chunk could not be uploaded.');
}

async function readJsonResponse(response) {
  try {
    return await response.json();
  } catch (_) {
    return { ok: false, error: `The webserver returned an invalid upload response (HTTP ${response.status}).` };
  }
}

function csrfToken() {
  return document.body.dataset.csrfToken
    || document.querySelector('input[name="csrf_token"]')?.value
    || '';
}

function setupSessionTimeout() {
  const seconds = Number(document.body.dataset.sessionTimeout || 0);
  const logoutForm = document.getElementById('session-logout-form');
  if (!seconds || !logoutForm) return;
  let lastActivity = Date.now();
  let logoutTimer = 0;

  const arm = () => {
    window.clearTimeout(logoutTimer);
    logoutTimer = window.setTimeout(() => logoutForm.requestSubmit(), Math.max(60, seconds) * 1000);
  };
  const active = () => {
    lastActivity = Date.now();
    arm();
  };
  ['pointerdown', 'keydown', 'scroll', 'touchstart'].forEach((name) => {
    window.addEventListener(name, active, { passive: true });
  });
  window.setInterval(() => {
    if (Date.now() - lastActivity > 300000) return;
    fetch('index.php?page=session-ping', { credentials: 'same-origin', headers: { Accept: 'application/json' } }).catch(() => {});
  }, 240000);
  arm();
}

function clipboardFiles(clipboard) {
  if (!clipboard) return [];
  const files = [...clipboard.files];
  if (files.length) return files;
  return [...clipboard.items]
    .filter((item) => item.kind === 'file')
    .map((item) => item.getAsFile())
    .filter(Boolean);
}

function nameClipboardFile(file, index) {
  const mimeExtensions = {
    'image/png': 'png', 'image/jpeg': 'jpg', 'image/gif': 'gif', 'image/webp': 'webp'
  };
  const extension = mimeExtensions[file.type] || (file.name.includes('.') ? file.name.split('.').pop() : 'bin');
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const suffix = index ? '-' + (index + 1) : '';
  try {
    return new File([file], 'Screenshot-' + stamp + suffix + '.' + extension, {
      type: file.type,
      lastModified: Date.now() + index
    });
  } catch (_) {
    return file;
  }
}

function formatFileSize(bytes) {
  if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + ' GB';
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB';
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return bytes + ' B';
}

function showAttachmentMessage(queue, message, error = false) {
  queue.hidden = false;
  queue.replaceChildren();
  const notice = document.createElement('div');
  notice.className = 'attachment-summary' + (error ? ' error' : '');
  notice.textContent = message;
  queue.append(notice);
}

function setupPlayerPicker(picker) {
  const mode = picker.dataset.mode === 'single' ? 'single' : 'multiple';
  const query = picker.querySelector('[data-player-query]');
  const results = picker.querySelector('[data-player-results]');
  const selectedContainer = picker.querySelector('[data-player-selected]');
  const jsonValue = picker.querySelector('[data-player-value]');
  const nameValue = picker.querySelector('[data-player-name]');
  const uuidValue = picker.querySelector('[data-player-uuid]');
  if (!query || !results || !selectedContainer) return;

  let selected = [];
  let timer = 0;
  let requestNumber = 0;

  if (mode === 'single' && picker.dataset.initial) {
    try {
      const initial = JSON.parse(picker.dataset.initial);
      if (initial && initial.username) selected = [initial];
    } catch (_) {}
  } else if (mode === 'multiple' && jsonValue && jsonValue.value) {
    try {
      const initial = JSON.parse(jsonValue.value);
      if (Array.isArray(initial)) selected = initial.filter((player) => player && player.username);
    } catch (_) {}
  }

  const playerKey = (player) => String(player.uuid || player.username || '').toLowerCase();

  const sync = () => {
    if (mode === 'multiple' && jsonValue) {
      jsonValue.value = JSON.stringify(selected.map(({ uuid, username, platform }) => ({ uuid, username, platform })));
    }
    if (mode === 'single') {
      const player = selected[0] || null;
      if (nameValue) nameValue.value = player ? player.username : '';
      if (uuidValue) uuidValue.value = player ? (player.uuid || '') : '';
    }
  };

  const renderSelected = () => {
    selectedContainer.replaceChildren();
    selected.forEach((player) => {
      const chip = document.createElement('span');
      chip.className = 'selected-player';

      const image = document.createElement('img');
      image.src = player.avatar || `avatar.php?name=${encodeURIComponent(player.username)}&uuid=${encodeURIComponent(player.uuid || '')}&size=32`;
      image.alt = '';
      image.loading = 'lazy';
      image.dataset.avatarName = player.username;
      image.dataset.avatarUuid = player.uuid || '';
      image.dataset.avatarSize = '32';
      setupAvatarFallback(image, player);

      const label = document.createElement('span');
      const strong = document.createElement('strong');
      strong.textContent = player.username;
      const small = document.createElement('small');
      small.textContent = player.platform || 'known player';
      label.append(strong, small);

      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'remove-player';
      remove.setAttribute('aria-label', `Remove ${player.username}`);
      remove.textContent = '×';
      remove.addEventListener('click', () => {
        selected = selected.filter((item) => playerKey(item) !== playerKey(player));
        renderSelected();
      });

      chip.append(image, label, remove);
      selectedContainer.append(chip);
    });
    sync();
  };

  const closeResults = () => {
    results.hidden = true;
    results.replaceChildren();
  };

  const choose = (player) => {
    if (mode === 'single') {
      selected = [player];
    } else if (selected.length < 20 && !selected.some((item) => playerKey(item) === playerKey(player))) {
      selected.push(player);
    }
    query.value = '';
    closeResults();
    renderSelected();
    query.focus();
  };

  const renderResults = (players) => {
    results.replaceChildren();
    if (!players.length) {
      const empty = document.createElement('span');
      empty.className = 'player-result-empty';
      empty.textContent = 'No matching player in the Staff HQ database.';
      results.append(empty);
    }
    players.forEach((player) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'player-result';

      const image = document.createElement('img');
      image.src = player.avatar;
      image.alt = '';
      image.loading = 'lazy';
      image.dataset.avatarName = player.username;
      image.dataset.avatarUuid = player.uuid || '';
      image.dataset.avatarSize = '38';
      setupAvatarFallback(image, player);

      const identity = document.createElement('span');
      const strong = document.createElement('strong');
      strong.textContent = player.username;
      const small = document.createElement('small');
      const state = player.online ? 'online' : (player.last_seen || 'offline');
      small.textContent = `${player.platform || 'java'} · ${state}${player.town ? ` · ${player.town}` : ''}`;
      identity.append(strong, small);

      const status = document.createElement('span');
      status.className = `result-status ${player.online ? 'online' : ''}`;
      status.textContent = player.online ? 'Online' : 'Select';

      button.append(image, identity, status);
      button.addEventListener('click', () => choose(player));
      results.append(button);
    });
    results.hidden = false;
  };

  const search = async () => {
    const value = query.value.trim();
    if (!value) {
      closeResults();
      return;
    }
    const currentRequest = ++requestNumber;
    results.hidden = false;
    results.replaceChildren();
    const loading = document.createElement('span');
    loading.className = 'player-result-empty';
    loading.textContent = 'Searching players…';
    results.append(loading);
    try {
      const response = await fetch(`index.php?page=player-lookup&q=${encodeURIComponent(value)}`, {
        headers: { Accept: 'application/json' },
        credentials: 'same-origin'
      });
      if (!response.ok) throw new Error('lookup failed');
      const data = await response.json();
      if (currentRequest === requestNumber) renderResults(Array.isArray(data.players) ? data.players : []);
    } catch (_) {
      if (currentRequest === requestNumber) {
        results.replaceChildren();
        const failed = document.createElement('span');
        failed.className = 'player-result-empty';
        failed.textContent = 'Player search is temporarily unavailable.';
        results.append(failed);
        results.hidden = false;
      }
    }
  };

  query.addEventListener('input', () => {
    requestNumber += 1;
    window.clearTimeout(timer);
    timer = window.setTimeout(search, 180);
  });
  query.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeResults();
  });
  document.addEventListener('click', (event) => {
    if (!picker.contains(event.target)) closeResults();
  });

  renderSelected();
}

function setupInteractiveCharts(root = document) {
  root.querySelectorAll('[data-interactive-chart]').forEach(chart => {
    if (chart.dataset.chartReady) return;
    chart.dataset.chartReady = '1';
    const points = JSON.parse(chart.dataset.chartPoints || '[]');
    const svg = chart.querySelector('svg'), out = chart.querySelector('output');
    const cursor = chart.querySelector('[data-chart-cursor]'), marker = chart.querySelector('[data-chart-marker]');
    let index = 0;
    function show(i) {
      index = Math.max(0, Math.min(points.length - 1, i));
      const p = points[index]; if (!p) return;
      out.textContent = `${p[3]} UTC · ${Number(p[2]).toLocaleString('en-US', { maximumFractionDigits: 6 })}`;
      cursor.setAttribute('x1', p[0]); cursor.setAttribute('x2', p[0]); cursor.removeAttribute('hidden');
      marker.setAttribute('cx', p[0]); marker.setAttribute('cy', p[1]); marker.removeAttribute('hidden');
    }
    svg.addEventListener('pointermove', e => {
      const rect = svg.getBoundingClientRect(), x = (e.clientX - rect.left) / rect.width * 760;
      let nearest = 0; points.forEach((p, i) => { if (Math.abs(p[0] - x) < Math.abs(points[nearest][0] - x)) nearest = i; }); show(nearest);
    });
    svg.addEventListener('focus', () => show(index));
    svg.addEventListener('keydown', e => {
      if (!['ArrowLeft','ArrowRight','Home','End'].includes(e.key)) return;
      e.preventDefault(); show(e.key === 'Home' ? 0 : e.key === 'End' ? points.length - 1 : index + (e.key === 'ArrowRight' ? 1 : -1));
    });
  });
}
document.addEventListener('DOMContentLoaded', () => {
  setupInteractiveCharts();
  if (!document.querySelector('[data-plugin-live]')) return;
  window.setInterval(async () => {
    if (document.hidden || document.querySelector('input:focus,select:focus,textarea:focus')) return;
    try {
      const response = await fetch(window.location.href, {headers:{'X-TR-Background':'1'}, credentials:'same-origin',cache:'no-store'});
      if (!response.ok || response.redirected) return;
      const parsed = new DOMParser().parseFromString(await response.text(),'text/html');
      const next = parsed.querySelector('[data-plugin-live]'), current = document.querySelector('[data-plugin-live]');
      if (next && current) current.replaceWith(next);
    } catch (_) { /* Current data stays visible when a refresh fails. */ }
  },60000);
});

// Analytics uses one bounded data model for mouse, touch, keyboard, legend and zoom.
function setupAnalysisCharts(root = document) {
  const ns = 'http://www.w3.org/2000/svg';
  const number = (v, unit = '') => {
    if (v === null || v === undefined) return 'No measurement';
    const n = Number(v).toLocaleString('en-US', {maximumFractionDigits: unit === '$' ? 2 : 3});
    return unit === '$' ? `$${n}` : `${n}${unit ? ` ${unit}` : ''}`;
  };
  root.querySelectorAll('[data-analysis-chart]').forEach(chart => {
    if (chart.dataset.ready) return;
    chart.dataset.ready = '1';
    let data; try { data = JSON.parse(chart.dataset.chartData); } catch (_) { return; }
    const svg = chart.querySelector('svg'), output = chart.querySelector('output');
    const cursor = chart.querySelector('[data-analysis-cursor]'), markers = chart.querySelector('[data-analysis-markers]');
    const brush = chart.querySelector('[data-chart-brush]'), reset = chart.querySelector('[data-chart-reset]');
    const zoomLabel = chart.querySelector('[data-zoom-label]');
    const enabled = data.series.map(() => true); let left = 0, right = data.labels.length - 1, index = left, cumulative = false, drag = null, pointerId = null;
    let values = data.series.map(s => s.values.slice()), yMin = 0, yMax = 1, viewWidth = 1000, plotLeft = 70, plotRight = 970;
    const el = (name, attrs) => { const node = document.createElementNS(ns, name); Object.entries(attrs).forEach(([k,v]) => node.setAttribute(k,String(v))); return node; };
    const x = i => plotLeft + (right === left ? (plotRight-plotLeft)/2 : (i-left)/(right-left)*(plotRight-plotLeft));
    const y = v => 247 - (v-yMin)/(yMax-yMin)*215;
    const valueAt = i => Math.max(left, Math.min(right, i));
    const axisDate = label => {
      if(data.categories)return label;
      const date = new Date(label.replace(' ','T') + (label.length <= 10 ? 'T00:00:00Z' : 'Z'));
      if (isNaN(date.getTime())) return label;
      return new Intl.DateTimeFormat('en-US',{timeZone:'UTC',month:'short', ...(data.group === 'hour' ? {day:'numeric',hour:'2-digit',hourCycle:'h23'} : data.group === 'month' ? {year:'numeric'} : {day:'numeric',year:'2-digit'})}).format(date);
    };
    function rebuildValues() {
      values = data.series.map(series => {
        let sum = 0;
        return series.values.map(v => {
          if (v === null) return null;
          if (!cumulative) return Number(v);
          sum += Number(v); return sum;
        });
      });
    }
    function draw() {
      const actualWidth=svg.getBoundingClientRect().width;viewWidth=actualWidth<450?600:(actualWidth<760?700:1000);plotLeft=viewWidth===1000?70:55;plotRight=viewWidth-30;svg.setAttribute('viewBox',`0 0 ${viewWidth} 300`);
      const fontSize=viewWidth===600?18:(viewWidth===700?14:11);svg.querySelectorAll('.chart-axis-label').forEach(t=>t.style.fontSize=`${fontSize}px`);
      brush.setAttribute('x',plotLeft);
      let low = 0, high = 1;
      values.forEach((s,k) => {if (!enabled[k]) return; for (let i=left;i<=right;i++) if(s[i]!==null){low=Math.min(low,s[i]);high=Math.max(high,s[i]);}});
      if(data.percent){yMin=0;yMax=100;}else{const step=Math.pow(10,Math.floor(Math.log10(Math.max(Math.abs(low),Math.abs(high),1))));yMin=Math.floor(low/step*4)/4*step;yMax=Math.ceil(high/step*4)/4*step;}
      if(yMax<=yMin)yMax=yMin+1;
      svg.querySelectorAll('[data-y-tick]').forEach((g,i)=>{const v=yMax-i/4*(yMax-yMin);g.querySelector('line').setAttribute('x1',plotLeft);g.querySelector('line').setAttribute('x2',plotRight);g.querySelector('text').setAttribute('x',plotLeft-10);g.querySelector('text').textContent = Number(v.toFixed(2)).toLocaleString('en-US',{notation:Math.abs(v)>=10000?'compact':'standard',maximumFractionDigits:2});});
      const ticks=chart.querySelector('[data-x-ticks]');ticks.replaceChildren();
      const total=Math.min(viewWidth===600?3:(viewWidth===700?4:6),right-left+1);for(let t=0;t<total;t++){const i=left+Math.round(t/Math.max(1,total-1)*(right-left));const text=el('text',{x:x(i),y:279,'text-anchor':'middle',class:'chart-axis-label',style:`font-size:${fontSize}px`});text.textContent=axisDate(data.labels[i]);ticks.append(text);}
      data.series.forEach((s,k)=>{
        const group=chart.querySelector(`[data-series-line="${k}"]`), dots=group.querySelector('[data-series-dots]');group.style.display=enabled[k]?'':'none';dots.replaceChildren();let path='',start=true;
        for(let i=left;i<=right;i++){const n=values[k][i];if(n===null){start=true;continue;}path+=`${start?'M':'L'}${x(i).toFixed(2)},${y(n).toFixed(2)} `;start=false;
          const isolated=(i===left||values[k][i-1]===null)&&(i===right||values[k][i+1]===null);
          if(right-left<=60||isolated)dots.append(el('circle',{cx:x(i),cy:y(n),r:isolated?3.5:2.5,fill:s.color}));
        }group.querySelector('path').setAttribute('d',path);
      });
      reset.hidden=left===0&&right===data.labels.length-1;
      zoomLabel.textContent=reset.hidden?'Missing measurements appear as gaps.':`Zoomed: ${data.labels[left]} — ${data.labels[right]} UTC`;
      hideTooltip();
    }
    function hideTooltip(){output.hidden=true;cursor.setAttribute('hidden','');markers.replaceChildren();}
    function show(i, event = null) {
      index=valueAt(i);const label=data.labels[index];if(!label)return;
      const date=new Date(label.replace(' ','T')+(label.length<=10?'T00:00:00Z':'Z'));
      const end=new Date(date);if(data.group==='hour')end.setUTCHours(end.getUTCHours()+1);else if(data.group==='week')end.setUTCDate(end.getUTCDate()+7);else if(data.group==='month')end.setUTCMonth(end.getUTCMonth()+1);else end.setUTCDate(end.getUTCDate()+1);
      const partial=end.getTime()>Date.now();
      output.replaceChildren();const heading=document.createElement('strong');heading.textContent=`${label} UTC${!data.categories&&partial?' · in progress':''}`;output.append(heading);
      data.series.forEach((s,k)=>{if(!enabled[k])return;const row=document.createElement('span'),dot=document.createElement('i'),name=document.createElement('span'),value=document.createElement('b');dot.style.background=s.color;name.textContent=s.label;value.textContent=number(values[k][index],s.unit);row.append(dot,name,value);output.append(row);});
      output.hidden=false;output.classList.remove('align-right');
      const surface=chart.querySelector('.chart-surface').getBoundingClientRect(), box=svg.getBoundingClientRect();
      const anchorX=event?event.clientX-surface.left:box.left-surface.left+x(index)/viewWidth*box.width;
      const anchorY=event?event.clientY-surface.top:box.top-surface.top+box.height/2;
      const width=output.offsetWidth, height=output.offsetHeight, gap=14;
      let tipX=anchorX+gap,tipY=anchorY+gap;
      const maxX=Math.min(surface.width,innerWidth-surface.left)-width-4;
      const maxY=Math.min(surface.height,innerHeight-surface.top)-height-4;
      if(tipX>maxX)tipX=anchorX-width-gap;
      if(tipY>maxY)tipY=anchorY-height-gap;
      output.style.right='auto';
      output.style.left=`${Math.max(4,Math.min(maxX,tipX))}px`;
      output.style.top=`${Math.max(4,Math.min(maxY,tipY))}px`;
      cursor.setAttribute('x1',x(index));cursor.setAttribute('x2',x(index));cursor.removeAttribute('hidden');markers.replaceChildren();
      values.forEach((s,k)=>{if(enabled[k]&&s[index]!==null)markers.append(el('circle',{cx:x(index),cy:y(s[index]),r:5,fill:data.series[k].color,stroke:'var(--panel)','stroke-width':2}));});
    }
    const point = event => {const box=svg.getBoundingClientRect(),px=Math.max(plotLeft,Math.min(plotRight,(event.clientX-box.left)/box.width*viewWidth));return {px,i:valueAt(left+Math.round((px-plotLeft)/(plotRight-plotLeft)*(right-left)))};};
    svg.addEventListener('pointermove',event=>{const p=point(event);if(drag!==null&&pointerId===event.pointerId){const min=Math.min(drag.px,p.px);brush.setAttribute('x',min);brush.setAttribute('width',Math.abs(drag.px-p.px));brush.removeAttribute('hidden');hideTooltip();}else show(p.i,event);});
    svg.addEventListener('pointerdown',event=>{if(event.button!==0)return;const p=point(event);if(event.pointerType==='touch'){show(p.i,event);return;}drag=p;pointerId=event.pointerId;svg.setPointerCapture(event.pointerId);});
    svg.addEventListener('pointerup',event=>{if(drag===null||event.pointerId!==pointerId)return;const p=point(event),start=drag;drag=null;pointerId=null;brush.setAttribute('hidden','');if(Math.abs(start.px-p.px)>12&&Math.abs(start.i-p.i)>=2){left=Math.min(start.i,p.i);right=Math.max(start.i,p.i);index=left;draw();}else show(p.i,event);});
    svg.addEventListener('pointercancel',()=>{drag=null;pointerId=null;brush.setAttribute('hidden','');});
    svg.addEventListener('pointerleave',()=>{if(drag===null&&document.activeElement!==svg)hideTooltip();});
    svg.addEventListener('focus',()=>show(index));svg.addEventListener('blur',hideTooltip);
    svg.addEventListener('keydown',event=>{if(event.key==='Escape'){left=0;right=data.labels.length-1;draw();return;}if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();show(event.key==='Home'?left:event.key==='End'?right:index+(event.key==='ArrowRight'?1:-1));});
    chart.querySelectorAll('[data-series-toggle]').forEach(button=>button.addEventListener('click',()=>{const k=Number(button.dataset.seriesToggle);if(enabled[k]&&enabled.filter(Boolean).length===1)return;enabled[k]=!enabled[k];button.setAttribute('aria-pressed',String(enabled[k]));draw();}));
    reset.addEventListener('click',()=>{left=0;right=data.labels.length-1;draw();});
    const sumToggle=chart.querySelector('[data-chart-cumulative]');if(sumToggle)sumToggle.addEventListener('click',()=>{cumulative=!cumulative;sumToggle.setAttribute('aria-pressed',String(cumulative));rebuildValues();draw();});
    if(typeof ResizeObserver!=='undefined')new ResizeObserver(()=>draw()).observe(chart.querySelector('.chart-surface'));
    draw();
  });
}
document.addEventListener('DOMContentLoaded',()=>{
  setupAnalysisCharts();
  document.querySelectorAll('[data-range-date]').forEach(input=>input.addEventListener('change',()=>{const selector=input.closest('form').querySelector('[data-range-preset]');if(selector)selector.value='custom';}));
  document.querySelectorAll('[data-table-search]').forEach(input=>input.addEventListener('input',()=>{const table=document.getElementById(input.dataset.tableSearch),query=input.value.toLowerCase().trim();table?.querySelectorAll('tbody tr').forEach(row=>{row.hidden=!row.textContent.toLowerCase().includes(query);});}));
});
