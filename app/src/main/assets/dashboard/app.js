/* Zelda 64 Player — Dashboard App — Nintendo Switch HOME edition
   - Single unified Collection/Play grid (Switch cards, 13:9, focus border)
   - Each card has a "Jogar" button that opens the player overlay
   - Covers served via /covers/{hackId} (phone-proxied + cached)
   - Switch dock + footer hints + dialog + toast
*/

(function () {
    'use strict';

    // ---- i18n ----
    let currentLang = navigator.language.startsWith('pt') ? 'pt-BR'
        : navigator.language.startsWith('es') ? 'es'
            : 'en';
    let translations = {};

    async function loadTranslations() {
        try {
            const resp = await fetch(`/i18n/${currentLang}.json`);
            if (resp.ok) translations = await resp.json();
        } catch (e) {
            console.warn('Failed to load translations, using HTML defaults');
        }
        applyTranslations();
    }

    function applyTranslations() {
        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key = el.getAttribute('data-i18n');
            if (translations[key]) el.textContent = translations[key];
        });
    }

    function t(key) {
        return translations[key] || key;
    }

    // ---- Toast (Switch pill) ----
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `switch-toast ${type}`;
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(-50%) translateY(8px)';
            toast.style.transition = 'opacity 0.2s, transform 0.2s';
            setTimeout(() => toast.remove(), 220);
        }, 3000);
    }

    // ---- API helpers ----
    async function api(path, options = {}) {
        const resp = await fetch(`/api${path}`, {
            headers: { 'Content-Type': 'application/json', ...options.headers },
            ...options
        });
        if (!resp.ok) {
            const err = await resp.json().catch(() => ({ error: resp.statusText }));
            throw new Error(err.error || 'API error');
        }
        return resp.json();
    }

    // ---- Navigation (Collection + Settings only) ----
    function setupNavigation() {
        document.querySelectorAll('.switch-nav-link').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const page = link.getAttribute('data-page');
                document.querySelectorAll('.switch-nav-link').forEach(l => l.classList.remove('active'));
                document.querySelectorAll('.switch-page').forEach(p => p.classList.remove('active'));
                link.classList.add('active');
                document.getElementById(`page-${page}`).classList.add('active');
                if (page === 'collection') loadCollection();
                if (page === 'streaming') loadStreaming();
                if (page === 'settings') loadSettings();
            });
        });

        // Dock actions
        document.querySelectorAll('.switch-dock-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const action = btn.getAttribute('data-action');
                if (action === 'collection') {
                    document.querySelector('.switch-nav-link[data-page="collection"]').click();
                } else if (action === 'settings') {
                    document.querySelector('.switch-nav-link[data-page="settings"]').click();
                } else if (action === 'refresh') {
                    loadCollection();
                    showToast(t('loading'), 'info');
                } else if (action === 'backup-export') {
                    document.getElementById('btnBackupExport').click();
                } else if (action === 'backup-import') {
                    document.getElementById('backupImportInput').click();
                }
            });
        });
    }

    async function loadStreaming() {
        const grid = document.getElementById('streamingGrid');
        if (!grid) return;
        grid.innerHTML = `<div class="switch-loading">${t('loading')}</div>`;
        try {
            const data = await api('/collection');
            const playable = data.games.filter(game => game.canPlay !== false && game.hasRom);
            grid.innerHTML = playable.length ? playable.map(game => `
                <article class="switch-card" tabindex="0">
                    <div class="switch-card-cover"><img src="${escapeHtml(game.coverUrl || `/covers/${game.hackId}`)}" alt="${escapeHtml(game.name)}"></div>
                    <div class="switch-card-info"><div class="switch-card-name">${escapeHtml(game.name)}</div>
                    <button class="switch-btn switch-btn-play" data-stream-play="${escapeHtml(game.hackId)}">▶ ${t('play')}</button></div>
                </article>`).join('') : `<div class="switch-empty"><p>${t('collection_empty')}</p></div>`;
            grid.querySelectorAll('[data-stream-play]').forEach(button => button.addEventListener('click', () => {
                const id = button.getAttribute('data-stream-play');
                const game = playable.find(item => item.hackId === id);
                if (game) openPlayer(id, game.name);
            }));
        } catch (e) { grid.innerHTML = `<div class="switch-loading">${t('error_loading')}</div>`; }
    }

    // ---- Collection (unified grid with Play button) ----
    async function loadCollection() {
        const grid = document.getElementById('gameGrid');
        const countEl = document.getElementById('gameCount');
        grid.innerHTML = `<div class="switch-loading">${t('loading')}</div>`;
        if (countEl) countEl.textContent = '';
        try {
            const data = await api('/collection');
            if (countEl) countEl.textContent = `${data.total} ${t('collection_count_suffix') || 'jogos'}`;
            if (data.games.length === 0) {
                grid.innerHTML = `<div class="switch-empty"><p>${t('collection_empty')}</p></div>`;
                return;
            }
            grid.innerHTML = data.games.map(game => {
                const canPlay = game.canPlay !== false && game.hasRom;
                const badge = game.isVanilla
                    ? `<span class="switch-card-badge switch-card-badge-vanilla">Vanilla</span>`
                    : `<span class="switch-card-badge switch-card-badge-hack">Hack</span>`;
                const coverUrl = game.coverUrl || `/covers/${game.hackId}`;
                return `
                <article class="switch-card" data-hack-id="${game.hackId}" tabindex="0" aria-label="${escapeHtml(game.name)}">
                    <div class="switch-card-cover">
                        <img src="${escapeHtml(coverUrl)}" alt="${escapeHtml(game.name)}"
                             loading="lazy"
                             onerror="this.style.display='none'; this.nextElementSibling.style.display='grid';">
                        <div class="switch-card-cover-fallback" style="display:none;">◧</div>
                        <div class="switch-card-dim" aria-hidden="true"></div>
                        ${badge}
                    </div>
                    <div class="switch-card-info">
                        <div class="switch-card-name" title="${escapeHtml(game.name)}">${escapeHtml(game.name)}</div>
                        <div class="switch-card-meta">
                            ${game.hasRom ? `<span class="switch-badge switch-badge-rom">ROM</span>` : ''}
                            ${game.hasSram ? `<span class="switch-badge switch-badge-sram">SRAM</span>` : ''}
                            ${game.hasState ? `<span class="switch-badge switch-badge-state">State</span>` : ''}
                        </div>
                        <div class="switch-card-actions">
                            <button class="switch-btn switch-btn-play" data-play="${escapeHtml(game.hackId)}" ${canPlay ? '' : 'disabled'} title="${canPlay ? t('play') : t('play_unavailable')}">
                                <span>▶</span> <span data-i18n="play">Jogar</span>
                            </button>
                            <button class="switch-btn switch-btn-secondary" data-options="${escapeHtml(game.hackId)}" title="${t('options')}">
                                <span>⋯</span>
                            </button>
                        </div>
                    </div>
                </article>
            `;
            }).join('');

            // Focus ring on keyboard nav
            grid.querySelectorAll('.switch-card').forEach(card => {
                card.addEventListener('focus', () => card.classList.add('is-focused'));
                card.addEventListener('blur', () => card.classList.remove('is-focused'));
                card.addEventListener('mouseenter', () => card.classList.add('is-focused'));
                card.addEventListener('mouseleave', () => {
                    if (document.activeElement !== card) card.classList.remove('is-focused');
                });
            });

            // Play buttons
            grid.querySelectorAll('[data-play]').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    const hackId = btn.getAttribute('data-play');
                    const game = data.games.find(g => g.hackId === hackId);
                    if (game) openPlayer(hackId, game.name);
                });
            });

            // Options buttons
            grid.querySelectorAll('[data-options]').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    const hackId = btn.getAttribute('data-options');
                    const game = data.games.find(g => g.hackId === hackId);
                    if (game) showCardDialog(game);
                });
            });

            // Card click = options (not play — play is explicit)
            grid.querySelectorAll('.switch-card').forEach(card => {
                card.addEventListener('click', (e) => {
                    if (e.target.closest('[data-play]') || e.target.closest('[data-options]')) return;
                    const hackId = card.getAttribute('data-hack-id');
                    const game = data.games.find(g => g.hackId === hackId);
                    if (game) showCardDialog(game);
                });
                card.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') {
                        const hackId = card.getAttribute('data-hack-id');
                        const game = data.games.find(g => g.hackId === hackId);
                        if (game && game.canPlay !== false && game.hasRom) openPlayer(hackId, game.name);
                    }
                });
            });

            applyTranslations();
        } catch (e) {
            grid.innerHTML = `<div class="switch-loading">${t('error_loading')}</div>`;
            showToast(e.message, 'error');
        }
    }

    function escapeHtml(s) {
        const d = document.createElement('div');
        d.textContent = s;
        return d.innerHTML;
    }

    // ---- Card Dialog (SwitchDialog) ----
    function showCardDialog(game) {
        const scrim = document.getElementById('cardDialogScrim');
        const dialog = document.getElementById('cardDialog');
        const title = document.getElementById('cardDialogTitle');
        const body = document.getElementById('cardDialogBody');
        title.textContent = game.name;

        const canPlay = game.canPlay !== false && game.hasRom;
        const rows = [];

        if (canPlay) {
            rows.push({ label: t('play'), hint: t('play_hint') || 'Abrir no navegador', action: () => { closeCardDialog(); openPlayer(game.hackId, game.name); } });
        }
        rows.push({ label: t('download_sram'), hint: game.hasSram ? '' : t('no_save') || 'Sem save', action: () => downloadFile(game.hackId, 'sram'), disabled: !game.hasSram });
        rows.push({ label: t('download_state'), hint: game.hasState ? '' : t('no_save') || 'Sem save', action: () => downloadFile(game.hackId, 'state'), disabled: !game.hasState });
        rows.push({ label: t('download_rom'), hint: '', action: () => downloadFile(game.hackId, 'rom'), disabled: !game.hasRom });
        rows.push({ label: t('upload_sram'), hint: '', action: () => uploadFile(game.hackId, 'sram') });
        rows.push({ label: t('upload_state'), hint: '', action: () => uploadFile(game.hackId, 'state') });
        rows.push({ label: t('upload_cover') || 'Enviar capa', hint: '', action: () => uploadCover(game.hackId) });

        body.innerHTML = rows.map((r, i) => `
            <button class="switch-dialog-row" data-idx="${i}" ${r.disabled ? 'disabled style="opacity:0.5;cursor:not-allowed;"' : ''}>
                <span>${escapeHtml(r.label)}</span>
                ${r.hint ? `<small>${escapeHtml(r.hint)}</small>` : ''}
            </button>
        `).join('');

        body.querySelectorAll('.switch-dialog-row').forEach(btn => {
            if (btn.disabled) return;
            btn.addEventListener('click', () => {
                const idx = parseInt(btn.getAttribute('data-idx'), 10);
                rows[idx].action();
                if (rows[idx].label !== t('upload_sram') && rows[idx].label !== t('upload_state') && rows[idx].label !== (t('upload_cover') || 'Enviar capa')) {
                    closeCardDialog();
                }
            });
        });

        scrim.hidden = false;
        dialog.hidden = false;
        scrim.addEventListener('click', closeCardDialog, { once: true });
        document.getElementById('cardDialogClose').addEventListener('click', closeCardDialog, { once: true });
        document.addEventListener('keydown', function esc(e) {
            if (e.key === 'Escape') { closeCardDialog(); document.removeEventListener('keydown', esc); }
        });
    }

    function closeCardDialog() {
        const scrim = document.getElementById('cardDialogScrim');
        const dialog = document.getElementById('cardDialog');
        if (scrim) scrim.hidden = true;
        if (dialog) dialog.hidden = true;
    }

    function downloadFile(hackId, type) {
        window.location.href = `/api/collection/${hackId}/${type}`;
    }

    function uploadFile(hackId, type) {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = type === 'sram' ? '.bin,.srm,.sav' : type === 'state' ? '.state,.state.*' : '.*';
        input.onchange = async () => {
            const file = input.files[0];
            if (!file) return;
            const formData = new FormData();
            formData.append('file', file);
            try {
                await fetch(`/api/collection/${hackId}/${type}`, { method: 'POST', body: formData });
                showToast(t('upload_success'), 'success');
                loadCollection();
                closeCardDialog();
            } catch (e) {
                showToast(t('upload_error'), 'error');
            }
        };
        input.click();
    }

    function uploadCover(hackId) {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = async () => {
            const file = input.files[0];
            if (!file) return;
            const formData = new FormData();
            formData.append('file', file);
            try {
                const resp = await fetch(`/api/collection/${hackId}/cover`, { method: 'POST', body: formData });
                const data = await resp.json();
                if (resp.ok && data.success) {
                    showToast(t('upload_success'), 'success');
                    loadCollection();
                    closeCardDialog();
                } else {
                    showToast(data.error || t('upload_error'), 'error');
                }
            } catch (e) {
                showToast(t('upload_error'), 'error');
            }
        };
        input.click();
    }

    // ---- Backup ----
    function setupBackup() {
        const exportBtn = document.getElementById('btnBackupExport');
        if (exportBtn) {
            exportBtn.addEventListener('click', () => {
                window.location.href = '/api/backup/export';
            });
        }
        const importInput = document.getElementById('backupImportInput');
        if (importInput) {
            importInput.addEventListener('change', async (e) => {
                const file = e.target.files[0];
                if (!file) return;
                const formData = new FormData();
                formData.append('file', file);
                try {
                    const result = await fetch('/api/backup/import', { method: 'POST', body: formData });
                    const data = await result.json();
                    if (data.success) {
                        showToast(t('backup_restore_success'), 'success');
                        loadCollection();
                    } else {
                        showToast(t('backup_restore_error'), 'error');
                    }
                } catch (err) {
                    showToast(t('backup_restore_error'), 'error');
                }
                e.target.value = '';
            });
        }
    }

    // ---- Player Overlay ----
    function openPlayer(hackId, name) {
        const overlay = document.getElementById('playerOverlay');
        const title = document.getElementById('playerTitle');
        const placeholder = document.getElementById('emulatorPlaceholder');
        const video = document.getElementById('emulatorVideo');
        if (!overlay || !title) return;
        title.textContent = name;
        overlay.hidden = false;
        if (placeholder) placeholder.hidden = false;
        if (video) video.style.display = 'none';
        document.body.style.overflow = 'hidden';
        // Try to start streaming if WebRTC is available; otherwise show placeholder
        startEmulator(hackId);
    }

    function closePlayer() {
        const overlay = document.getElementById('playerOverlay');
        const video = document.getElementById('emulatorVideo');
        if (overlay) overlay.hidden = true;
        if (video) {
            video.pause();
            video.srcObject = null;
            video.style.display = 'none';
        }
        const placeholder = document.getElementById('emulatorPlaceholder');
        if (placeholder) placeholder.hidden = false;
        document.body.style.overflow = '';
        stopEmulator();
    }

    function setupPlayer() {
        const scrim = document.getElementById('playerScrim');
        const exitBtn = document.getElementById('btnExitGame');
        const fsBtn = document.getElementById('btnFullscreen');
        if (scrim) scrim.addEventListener('click', closePlayer);
        if (exitBtn) exitBtn.addEventListener('click', closePlayer);
        if (fsBtn) fsBtn.addEventListener('click', () => {
            const container = document.getElementById('emulatorContainer');
            if (container && container.requestFullscreen) container.requestFullscreen();
            else {
                const video = document.getElementById('emulatorVideo');
                if (video && video.requestFullscreen) video.requestFullscreen();
            }
        });
        document.addEventListener('keydown', (e) => {
            const overlay = document.getElementById('playerOverlay');
            if (overlay && !overlay.hidden && e.key === 'Escape') closePlayer();
        });
    }

    let webrtcPeer = null;
    let activeEmulatorHackId = null;
    let sramSyncTimer = null;
    let emulatorScript = null;

    async function startEmulator(hackId) {
        const placeholder = document.getElementById('emulatorPlaceholder');
        const video = document.getElementById('emulatorVideo');
        showToast(t('player_starting') + ' ' + hackId, 'info');
        if (video) video.style.display = 'none';
        if (placeholder) placeholder.hidden = false;

        const game = document.getElementById('emulatorContainer');
        if (!game) return;
        // EmulatorJS names its persistent SRAM from EJS_gameName. A stable id per installed ROM
        // prevents vanilla and patched hacks from ever sharing a browser save.
        const safeId = `zelda64_${encodeURIComponent(hackId)}`;
        activeEmulatorHackId = hackId;
        game.querySelectorAll('.ejs_canvas_parent, .ejs_game, canvas').forEach(node => node.remove());

        // These are the official EmulatorJS boot variables. The ROM endpoint streams the actual
        // patched file selected by GameRomResolver; it is not a download/redirect endpoint.
        window.EJS_player = '#emulatorContainer';
        window.EJS_core = 'n64';
        window.EJS_gameName = safeId;
        window.EJS_gameID = safeId;
        window.EJS_gameUrl = `/api/collection/${encodeURIComponent(hackId)}/play/rom`;
        window.EJS_pathtodata = '/emulatorjs/data/';
        window.EJS_startOnLoaded = true;
        window.EJS_disableDatabases = false;
        window.EJS_externalFiles = {
            [`/home/web_user/retroarch/userdata/saves/${safeId}.srm`]:
                `/api/collection/${encodeURIComponent(hackId)}/play/sram-file`
        };
        window.EJS_onGameStart = () => {
            if (placeholder) placeholder.hidden = true;
            startSramPersistence(hackId, safeId);
        };
        window.EJS_onSaveSave = () => persistSram(hackId, safeId);
        window.EJS_ready = () => { if (placeholder) placeholder.hidden = true; };

        // The loader must be recreated for every game because it reads its configuration once.
        if (emulatorScript) emulatorScript.remove();
        emulatorScript = document.createElement('script');
        emulatorScript.src = '/emulatorjs/data/loader.js';
        emulatorScript.async = true;
        emulatorScript.onerror = () => {
            showToast(t('player_unavailable'), 'error');
            if (placeholder) placeholder.hidden = false;
        };
        document.head.appendChild(emulatorScript);
    }

    function startSramPersistence(hackId, gameName) {
        if (sramSyncTimer) clearInterval(sramSyncTimer);
        sramSyncTimer = setInterval(() => persistSram(hackId, gameName), 15000);
    }

    async function persistSram(hackId, gameName) {
        if (activeEmulatorHackId !== hackId || !window.EJS_emulator?.gameManager?.FS) return;
        try {
            const fs = window.EJS_emulator.gameManager.FS;
            const bytes = fs.readFile(`/home/web_user/retroarch/userdata/saves/${gameName}.srm`);
            if (!bytes || bytes.length === 0) return;
            let binary = '';
            for (let i = 0; i < bytes.length; i += 0x8000) {
                binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
            }
            await api(`/collection/${encodeURIComponent(hackId)}/play/sram`, {
                method: 'POST', body: JSON.stringify({ data: btoa(binary) })
            });
        } catch (_) {
            // A core may not have created SRAM yet. The next save/interval retries safely.
        }
    }

    function stopEmulator() {
        const hackId = activeEmulatorHackId;
        const gameName = hackId ? `zelda64_${encodeURIComponent(hackId)}` : null;
        if (hackId && gameName) persistSram(hackId, gameName);
        activeEmulatorHackId = null;
        if (sramSyncTimer) { clearInterval(sramSyncTimer); sramSyncTimer = null; }
        if (webrtcPeer) {
            try { webrtcPeer.close(); } catch (_) {}
            webrtcPeer = null;
        }
        if (emulatorScript) { emulatorScript.remove(); emulatorScript = null; }
        delete window.EJS_emulator;
    }

    // ---- Settings Page ----
    async function loadSettings() {
        try {
            const data = await api('/settings');
            const portEl = document.getElementById('settingPort');
            const passEl = document.getElementById('settingPassword');
            const displayEl = document.getElementById('settingDisplayOutput');
            const displayIdEl = document.getElementById('settingDisplayId');
            const displayTouchEl = document.getElementById('settingDisplayTouchControls');
            const addrEl = document.getElementById('serverAddress');
            const clientsEl = document.getElementById('clientCount');
            if (portEl) portEl.value = data.port;
            if (passEl) passEl.value = '';
            if (displayEl) displayEl.value = /^\d+$/.test(data.displayOutput) ? 'specific' : data.displayOutput;
            if (displayIdEl) displayIdEl.value = data.displayId;
            if (displayTouchEl) displayTouchEl.value = data.displayTouchControls;
            if (addrEl) addrEl.textContent = data.address;
            if (clientsEl) clientsEl.textContent = data.connectedClients;
            renderAppSettings(data.preferences || []);
        } catch (e) {
            showToast(e.message, 'error');
        }
    }

    function setupSettings() {
        const btn = document.getElementById('btnSaveSettings');
        if (!btn) return;
        btn.addEventListener('click', async () => {
            const port = parseInt(document.getElementById('settingPort').value, 10);
            const password = document.getElementById('settingPassword').value;
            const selectedDisplayOutput = document.getElementById('settingDisplayOutput').value;
            const displayId = parseInt(document.getElementById('settingDisplayId').value, 10);
            const displayOutput = selectedDisplayOutput === 'specific' ? String(displayId) : selectedDisplayOutput;
            const displayTouchControls = document.getElementById('settingDisplayTouchControls').value;
            const preferences = {};
            document.querySelectorAll('[data-app-setting]').forEach(input => {
                preferences[input.dataset.appSetting] = input.type === 'checkbox' ? String(input.checked) : input.value;
            });
            try {
                const result = await api('/settings', {
                    method: 'PUT',
                    body: JSON.stringify({ port, password: password || undefined, displayOutput, displayId, displayTouchControls, preferences })
                });
                showToast(result.message, 'success');
                if (result.restartRequired) showToast(t('settings_restart_required'), 'info');
            } catch (e) {
                showToast(e.message, 'error');
            }
        });
    }

    function renderAppSettings(preferences) {
        const root = document.getElementById('appSettings');
        if (!root) return;
        root.innerHTML = preferences.map(pref => {
            const label = t(`settings_${pref.key}`) === `settings_${pref.key}`
                ? pref.key.replaceAll('_', ' ').replace(/\b\w/g, c => c.toUpperCase())
                : t(`settings_${pref.key}`);
            let control;
            if (pref.type === 'boolean') {
                control = `<input type="checkbox" data-app-setting="${escapeHtml(pref.key)}" ${pref.value === 'true' ? 'checked' : ''}>`;
            } else {
                control = `<select class="switch-input" data-app-setting="${escapeHtml(pref.key)}">${pref.options.map(value => `<option value="${escapeHtml(value)}" ${value === pref.value ? 'selected' : ''}>${escapeHtml(value)}</option>`).join('')}</select>`;
            }
            return `<div class="switch-setting-row"><label class="switch-setting-label">${escapeHtml(label)}</label>${control}</div>`;
        }).join('');
    }

    // ---- Server status polling ----
    async function pollStatus() {
        const dot = document.getElementById('statusDot');
        const text = document.getElementById('statusText');
        try {
            await api('/health');
            if (dot) { dot.classList.remove('offline'); dot.style.background = ''; }
            if (text) text.textContent = t('status_connected');
        } catch (e) {
            if (dot) dot.classList.add('offline');
            if (text) text.textContent = t('status_disconnected');
        }
    }

    // ---- Init ----
    async function init() {
        await loadTranslations();
        setupNavigation();
        setupBackup();
        setupPlayer();
        setupSettings();
        loadCollection();
        pollStatus();
        setInterval(pollStatus, 10000);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
