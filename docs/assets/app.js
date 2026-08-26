/* Zelda 64 Player — Landing page logic
   - i18n (pt-BR default, en, es)
   - Hack catalog fetch (live -> local fallback) + render
   - Original Switch HOME aesthetic, no external dependencies
*/

const I18N = {
  pt: {
    "meta.title": "Zelda 64 Player — Emulador de hacks N64 para Android",
    "meta.desc": "Front-end de emulador LibretroDroid para hacks de Zelda 64 (Ocarina of Time, Majora's Mask) with live BPS/IPS patching, OoT and RetroAchievements.",
    "nav.features": "Recursos",
    "nav.catalog": "Catálogo",
    "nav.legal": "Legal",
    "lang.label": "Idioma",
    "hero.title.pre": "Zelda 64",
    "hero.title.accent": "Player",
    "hero.subtitle": "O emulador de hacks de Nintendo 64 para Android. Live BPS/IPS patching, OoT and RetroAchievements — com a estética da HOME do Nintendo Switch.",
    "hero.badge1": "Android",
    "hero.badge2": "GPL-3.0",
    "hero.badge3": "Sem ROMs embutidas",
    "features.title": "Recursos",
    "features.sub": "Tudo o que o app oferece",
    "legal.title": "Aviso legal",
    "legal.text": "O Zelda 64 Player NUNCA distribui, baixa ou embute ROMs (Ocarina of Time, Majora's Mask). O app é licenciado sob GPL-3.0 e apenas aplica patches BPS a ROMs que VOCÊ fornece legalmente. Você deve ser dono de uma cópia legítima de Ocarina of Time ou Majora's Mask para usar este aplicativo.",
    "catalog.title": "Catálogo de Hacks",
    "catalog.sub": "Hacks da loja (mesmo JSON usado pelo app)",
    "catalog.loading": "Carregando catálogo...",
    "catalog.error": "Não foi possível carregar o catálogo.",
    "catalog.retry": "Tentar novamente",
    "catalog.count": "Hacks disponíveis",
    "card.author": "Autor",
    "card.version": "Versão",
    "card.baseRom": "ROM base",
    "card.cores": "Cores compatíveis",
    "card.coverFallback": "Sem capa",
    "footer.text": "Zelda 64 Player é um software livre GPL-3.0. Não afiliado à Nintendo.",
    "footer.source": "Código-fonte",
    "footer.llms": "LLMS.txt",
    "footer.sitemap": "Sitemap",
    "footer.robots": "Robots",
    "footer.catalogSchema": "Catálogo Schema",
    "footer.catalogExample": "Exemplo JSON",
    "footer.catalogCurrent": "Catálogo Atual",
    "f.store.title": "Loja de Hacks com catálogo JSON",
    "f.store.desc": "Navegue e instale hacks a partir de um catálogo JSON hospedado no GitHub, com atualização em segundo plano e cache no dispositivo.",
    "f.patch.title": "Patch BPS/IPS ao vivo com validação CRC32 tripla",
    "f.patch.desc": "Aplica patches BPS/IPS em memória/cache na hora de jogar, validando CRC32 da ROM base, do patch e da ROM final.",
    "f.byteorder.title": "Normalização de byte-order N64",
    "f.byteorder.desc": "Converte automaticamente ROMs .z64/.v64/.n64 para o formato big-endian .z64 antes da emulação.",
    "f.byteorder.desc": "Converte automaticamente ROMs .z64/.v64/.n64 para o formato big-endian .z64 antes da emulação.",
"f.checksum.title": "Correspondência de ROM base por checksum",
    "f.checksum.desc": "Identifica a ROM correta do usuário via CRC32/MD5/SHA1 de a cabecera, evitando parchear a ROM equivocada.",
    "f.ocarina.title": "Auto-Ocarina",
    "f.ocarina.desc": "HUD in-game que toca automaticamente as músicas de ocarina (OoT: 12, MM: 11) a partir do menu de pausa.",
    "f.ra.title": "Integração RetroAchievements",
    "f.ra.desc": "Conquistas, classificações e hardcore mode via rcheevos, com hash calculado da ROM final patchada.",
    "f.switchui.title": "Home e configurações estilo Nintendo Switch",
    "f.switchui.desc": "Home com carrossel de capas 13:9, título do jogo em foco, dock colorido e rodapé de comandos; configurações com categorias laterais em paisagem e gaveta no celular — sem IP da Nintendo.",
    "f.profile.title": "Perfil RetroAchievements na Home",
    "f.profile.desc": "Acesse o perfil RetroAchievements pelo avatar na Home, com dados armazenados em cache e atualização ao abrir a tela.",
    "f.tester.title": "Teste de controle Nintendo 64",
    "f.tester.desc": "Confira em tempo real os botões e analógicos do controle físico, inclusive o mapeamento usado pelos jogos.",
    "f.vanilla.title": "Jogos Vanilla na Biblioteca",
    "f.vanilla.desc": "Jogue suas ROMs base importadas de OoT/MM diretamente da tela principal da Biblioteca.",
    "f.gamepad.title": "Layout de gamepad personalizado",
    "f.gamepad.desc": "Controles RadialGamePad sob medida: C-buttons, Auto-Z, ButtonStick e FloatingJoystick para OoT/MM.",
    "f.backup.title": "Backup e restauração de saves",
    "f.backup.desc": "Exporte e restaure seus saves em arquivo ZIP, armazenados localmente no dispositivo.",
    "f.refresh.title": "Atualização de catálogo em segundo plano",
    "f.refresh.desc": "WorkManager atualiza o catálogo a cada 12 horas, mantendo a loja sempre atualizada sem consumo manual.",
    "f.i18n.title": "Internacionalização pt-BR / en / es",
    "f.i18n.desc": "Toda a interface do app é traduzida para português, inglês e espanhol, com detecção de idioma do sistema.",
    "f.capture.title": "Captura de tela, gravação e galeria",
    "f.capture.desc": "Faça screenshots e grave o gameplay em MP4 com ou sem os controles na tela, e veja tudo numa galeria estilo Switch.",
    "fPoints": {
      "store": ["Catálogo JSON hospedado no GitHub", "Atualização em segundo plano automática", "Cache local dos hacks no dispositivo"],
      "patch": ["Suporte a patches BPS e IPS", "Aplicação em memória/cache na hora de jogar", "Validação CRC32 tripla (base, patch, final)"],
      "byteorder": ["Conversão automática de byte-order", "Suporta .z64, .v64 e .n64", "Normaliza para big-endian .z64"],
      "checksum": ["Identifica a ROM base por checksum", "Usa CRC32, MD5 e SHA1 da cabecera", "Evita parchear a ROM errada"],
      "ocarina": ["HUD in-game de ocarina", "Catálogo embutido: OoT (12) e MM (11)", "Toca a partir do menu de pausa"],
      "ra": ["Conquistas e classificações (rcheevos)", "Modo hardcore via rcheevos", "Hash calculado da ROM final patchada"],
      "profile": ["Perfil RA acessível pela Home", "Dados em cache no dispositivo", "Atualiza ao abrir a tela"],
      "switchui": ["Home estilo Nintendo Switch", "Carrossel de capas e dock colorido", "Configurações com categorias laterais"],
      "vanilla": ["ROMs base próprias na Biblioteca", "OoT e MM importados", "Jogáveis direto da tela principal"],
      "gamepad": ["Layout RadialGamePad sob medida", "C-buttons, Auto-Z e ButtonStick", "FloatingJoystick para OoT/MM"],
      "tester": ["Teste do controle N64 em tempo real", "Botões e analógicos físicos", "Mostra o mapeamento dos jogos"],
      "backup": ["Exporta e restaura saves", "Arquivo ZIP local", "Armazenado no dispositivo"],
      "refresh": ["Atualização via WorkManager", "A cada 12 horas", "Sem consumo manual"],
      "i18n": ["Interface em pt-BR, en e es", "Detecção do idioma do sistema", "Toda a interface traduzida"],
      "capture": ["Screenshot via PixelCopy com ou sem overlay", "Gravação em MP4 via MediaProjection", "Galeria estilo Switch para ver, compartilhar e apagar"]
    },
    "download.title": "Baixar",
    "download.sub": "Instale o APK de release assinado",
    "download.lead": "Baixe o Zelda 64 Player para Android. O link sempre aponta para a versão mais recente no GitHub.",
    "download.versionLabel": "Versão mais recente:",
    "download.button": "Baixar APK",
    "download.note": "Você precisa fornecer suas próprias ROMs base (Ocarina of Time / Majora's Mask) de forma legal. O app nunca distribui ROMs."
  },
  en: {
    "meta.title": "Zelda 64 Player — N64 Hack Emulator for Android",
    "meta.desc": "LibretroDroid emulator front-end for Zelda 64 hacks (Ocarina of Time, Majora's Mask) with live BPS/IPS patching, OoT  RetroAchievements.",
    "nav.features": "Features",
    "nav.catalog": "Catalog",
    "nav.legal": "Legal",
    "lang.label": "Language",
    "hero.title.pre": "Zelda 64",
    "hero.title.accent": "Player",
    "hero.subtitle": "The Nintendo 64 hack emulator for Android. Live BPS/IPS patching, OoT  RetroAchievements — with the Nintendo Switch HOME aesthetic.",
    "hero.badge1": "Android",
    "hero.badge2": "GPL-3.0",
    "hero.badge3": "No bundled ROMs",
    "features.title": "Features",
    "features.sub": "Everything the app offers",
    "legal.title": "Legal notice",
    "legal.text": "Zelda 64 Player NEVER distributes, downloads or bundles ROMs (Ocarina of Time, Majora's Mask). The app is licensed under GPL-3.0 and only applies BPS patches to ROMs YOU legally provide. You must own a legitimate copy of Ocarina of Time or Majora's Mask to use this app.",
    "catalog.title": "Hack Catalog",
    "catalog.sub": "Store hacks (same JSON the app uses)",
    "catalog.loading": "Loading catalog...",
    "catalog.error": "Could not load the catalog.",
    "catalog.retry": "Try again",
    "catalog.count": "Hacks available",
    "card.author": "Author",
    "card.version": "Version",
    "card.baseRom": "Base ROM",
    "card.cores": "Compatible cores",
    "card.coverFallback": "No cover",
    "footer.text": "Zelda 64 Player is free software under GPL-3.0. Not affiliated with Nintendo.",
    "footer.source": "Source code",
    "footer.llms": "LLMS.txt",
    "footer.sitemap": "Sitemap",
    "footer.robots": "Robots",
    "footer.catalogSchema": "Catalog Schema",
    "footer.catalogExample": "JSON Example",
    "footer.catalogCurrent": "Current Catalog",
    "f.store.title": "Hack Store with JSON catalog",
    "f.store.desc": "Browse and install hacks from a GitHub-hosted JSON catalog, with background refresh and on-device cache.",
    "f.patch.title": "Live BPS/IPS patching with triple CRC32 validation",
    "f.patch.desc": "Applies BPS/IPS patches in memory/cache at launch, validating CRC32 of base ROM, patch and final ROM.",
    "f.byteorder.title": "N64 byte-order normalization",
    "f.byteorder.desc": "Automatically converts .z64/.v64/.n64 ROMs to big-endian .z64 before emulation.",
    "f.checksum.title": "Checksum-based base ROM matching",
    "f.checksum.desc": "Identifies the user's correct ROM via header CRC32/MD5/SHA1, preventing patches on the wrong ROM.",
    "f.ocarina.title": "Auto-Ocarina",
    "f.ocarina.desc": "In-game HUD that auto-plays ocarina songs (OoT: 12, MM: 11) from the pause menu.",
    "f.ra.title": "RetroAchievements integration",
    "f.ra.desc": "Achievements, leaderboards and hardcore mode via rcheevos, with hash computed from the final patched ROM.",
    "f.switchui.title": "Nintendo Switch-style Home and settings",
    "f.switchui.desc": "Home with a 13:9 cover carousel, focused-game title, colored dock and control hints; settings with a landscape sidebar and a phone drawer — no Nintendo IP.",
    "f.profile.title": "RetroAchievements profile on Home",
    "f.profile.desc": "Open the RetroAchievements profile from the Home avatar, with cached information refreshed when the screen opens.",
    "f.tester.title": "Nintendo 64 controller test",
    "f.tester.desc": "Check physical-controller buttons and analog sticks live, including the mapping used by games.",
    "f.vanilla.title": "Vanilla Games in Library",
    "f.vanilla.desc": "Play your imported base OoT/MM ROMs directly from the main Library screen.",
    "f.gamepad.title": "Custom gamepad layout",
    "f.gamepad.desc": "Tailored RadialGamePad controls: C-buttons, Auto-Z, ButtonStick and FloatingJoystick for OoT/MM.",
    "f.backup.title": "Save backup and restore",
    "f.backup.desc": "Export and restore your saves as a local ZIP file on the device.",
    "f.refresh.title": "Background catalog refresh",
    "f.refresh.desc": "WorkManager refreshes the catalog every 12 hours, keeping the store current without manual effort.",
    "f.i18n.title": "i18n pt-BR / en / es",
    "f.i18n.desc": "The entire app UI is translated to Portuguese, English and Spanish, with system language detection.",
    "f.capture.title": "Screen capture, recording and gallery",
    "f.capture.desc": "Take screenshots and record gameplay to MP4 with or without on-screen controls, and browse everything in a Switch-style gallery.",
    "fPoints": {
      "store": ["GitHub-hosted JSON catalog", "Automatic background refresh", "On-device hack cache"],
      "patch": ["BPS and IPS patch support", "Applied in memory/cache at launch", "Triple CRC32 validation (base, patch, final)"],
      "byteorder": ["Automatic byte-order conversion", "Supports .z64, .v64 and .n64", "Normalizes to big-endian .z64"],
      "checksum": ["Matches the correct base ROM by checksum", "Uses header CRC32, MD5 and SHA1", "Prevents patching the wrong ROM"],
      "ocarina": ["In-game ocarina HUD", "Built-in songs: OoT (12) and MM (11)", "Plays from the pause menu"],
      "ra": ["Achievements and leaderboards (rcheevos)", "Hardcore mode via rcheevos", "Hash from the final patched ROM"],
      "profile": ["RA profile accessible from Home", "Cached data on device", "Refreshes when the screen opens"],
      "switchui": ["Nintendo Switch-style Home", "Cover carousel and colored dock", "Settings with sidebar categories"],
      "vanilla": ["Your own base ROMs in the Library", "Imported OoT and MM", "Playable right from the main screen"],
      "gamepad": ["Tailored RadialGamePad layout", "C-buttons, Auto-Z and ButtonStick", "FloatingJoystick for OoT/MM"],
      "tester": ["Live N64 controller test", "Physical buttons and sticks", "Shows in-game button mapping"],
      "backup": ["Export and restore saves", "Local ZIP file", "Stored on device"],
      "refresh": ["Refresh via WorkManager", "Every 12 hours", "No manual effort"],
      "i18n": ["UI in pt-BR, en and es", "System language detection", "Entire interface translated"],
      "capture": ["PixelCopy screenshots with/without overlay", "MP4 recording via MediaProjection", "Switch-style gallery to view, share and delete"]
    },
    "download.title": "Download",
    "download.sub": "Get the signed release APK",
    "download.lead": "Download Zelda 64 Player for Android. The link always points to the latest release on GitHub.",
    "download.versionLabel": "Latest version:",
    "download.button": "Download APK",
    "download.note": "You must provide your own base ROMs (Ocarina of Time / Majora's Mask) legally. The app never distributes ROMs."
  },
  es: {
    "meta.title": "Zelda 64 Player — Emulador de hacks N64 para Android",
    "meta.desc": "Front-end de emulador LibretroDroid para hacks de Zelda 64 (Ocarina of Time, Majora's Mask) con parcheo BPS/IPS en vivo, y OoT y RetroAchievements.",
    "nav.features": "Funciones",
    "nav.catalog": "Catálogo",
    "nav.legal": "Legal",
    "lang.label": "Idioma",
    "hero.title.pre": "Zelda 64",
    "hero.title.accent": "Player",
    "hero.subtitle": "El emulador de hacks de Nintendo 64 para Android. Parcheo BPS/IPS en vivo, y OoT y RetroAchievements — con la estética HOME de Nintendo Switch.",
    "hero.badge1": "Android",
    "hero.badge2": "GPL-3.0",
    "hero.badge3": "Sin ROMs incluidas",
    "features.title": "Funciones",
    "features.sub": "Todo lo que ofrece la app",
    "legal.title": "Aviso legal",
    "legal.text": "Zelda 64 Player NUNCA distribuye, descarga ni incluye ROMs (Ocarina of Time, Majora's Mask). La app es licencia GPL-3.0 y solo aplica parches BPS a las ROMs que TÚ aportas legalmente. Debes ser dueño de una copia legítima de Ocarina of Time o Majora's Mask para usar esta aplicación.",
    "catalog.title": "Catálogo de Hacks",
    "catalog.sub": "Hacks de la tienda (mismo JSON que usa la app)",
    "catalog.loading": "Cargando catálogo...",
    "catalog.error": "No se pudo cargar el catálogo.",
    "catalog.retry": "Reintentar",
    "catalog.count": "Hacks disponibles",
    "card.author": "Autor",
    "card.version": "Versión",
    "card.baseRom": "ROM base",
    "card.cores": "Cores compatibles",
    "card.coverFallback": "Sin portada",
    "footer.text": "Zelda 64 Player es software libre GPL-3.0. No afiliado a Nintendo.",
    "footer.source": "Código fuente",
    "footer.llms": "LLMS.txt",
    "footer.sitemap": "Sitemap",
    "footer.robots": "Robots",
    "footer.catalogSchema": "Esquema Catálogo",
    "footer.catalogExample": "Ejemplo JSON",
    "footer.catalogCurrent": "Catálogo Actual",
    "f.store.title": "Tienda de Hacks con catálogo JSON",
    "f.store.desc": "Explora e instala hacks desde un catálogo JSON en GitHub, con actualización en segundo plano y caché en el dispositivo.",
    "f.patch.title": "Parcheo BPS/IPS en vivo con validación CRC32 triple",
    "f.patch.desc": "Aplica parches BPS/IPS en memoria/caché al iniciar, validando CRC32 de la ROM base, del parche y de la ROM final.",
    "f.byteorder.title": "Normalización de byte-order N64",
    "f.byteorder.desc": "Convierte automáticamente ROMs .z64/.v64/.n64 al formato big-endian .z64 antes de emular.",
"f.checksum.title": "Coincidencia de ROM base por checksum",
    "f.checksum.desc": "Identifica la ROM correcta del usuario vía CRC32/MD5/SHA1 de la cabecera, evitando parchear la ROM equivocada.",
    "f.ocarina.title": "Auto-Ocarina",
    "f.ocarina.desc": "HUD en juego que reproduce automáticamente las canciones de ocarina (OoT: 12, MM: 11) desde el menú de pausa.",
    "f.ra.title": "Integración RetroAchievements",
    "f.ra.desc": "Logros, clasificaciones y modo hardcore vía rcheevos, con hash calculado de la ROM final parcheada.",
    "f.switchui.title": "Inicio y configuración estilo Nintendo Switch",
    "f.switchui.desc": "Inicio con carrusel de portadas 13:9, título del juego enfocado, dock con color y ayudas de control; configuración con barra lateral en horizontal y cajón en el móvil — sin IP de Nintendo.",
    "f.profile.title": "Perfil RetroAchievements en Inicio",
    "f.profile.desc": "Abre el perfil de RetroAchievements desde el avatar de Inicio, con datos en caché que se actualizan al abrir la pantalla.",
    "f.tester.title": "Prueba de control Nintendo 64",
    "f.tester.desc": "Comprueba en vivo los botones y analógicos del control físico, incluido el mapeo usado por los juegos.",
    "f.vanilla.title": "Juegos Vanilla en la Biblioteca",
    "f.vanilla.desc": "Juega tus ROMs base importadas de OoT/MM directamente desde la pantalla principal de la Biblioteca.",
    "f.gamepad.title": "Layout de gamepad personalizado",
    "f.gamepad.desc": "Controles RadialGamePad a medida: C-buttons, Auto-Z, ButtonStick y FloatingJoystick para OoT/MM.",
    "f.backup.title": "Copia de seguridad y restauración de saves",
    "f.backup.desc": "Exporta y restaura tus partidas en un archivo ZIP local en el dispositivo.",
    "f.refresh.title": "Actualización de catálogo en segundo plano",
    "f.refresh.desc": "WorkManager actualiza el catálogo cada 12 horas, manteniendo la tienda al día sin esfuerzo manual.",
    "f.i18n.title": "Internacionalización pt-BR / en / es",
    "f.i18n.desc": "Toda la interfaz de la app está traducida a portugués, inglés y español, con detección del idioma del sistema.",
    "f.capture.title": "Captura de pantalla, grabación y galería",
    "f.capture.desc": "Haz capturas y graba gameplay en MP4 con o sin los controles en pantalla, y revisa todo en una galería estilo Switch.",
    "fPoints": {
      "store": ["Catálogo JSON alojado en GitHub", "Actualización en segundo plano automática", "Caché de hacks en el dispositivo"],
      "patch": ["Soporte de parches BPS e IPS", "Aplicados en memoria/caché al iniciar", "Validación CRC32 triple (base, parche, final)"],
      "byteorder": ["Conversión automática de byte-order", "Soporta .z64, .v64 y .n64", "Normaliza a big-endian .z64"],
      "checksum": ["Identifica la ROM base por checksum", "Usa CRC32, MD5 y SHA1 de la cabecera", "Evita parchear la ROM equivocada"],
      "ocarina": ["HUD de ocarina en juego", "Canciones integradas: OoT (12) y MM (11)", "Suena desde el menú de pausa"],
      "ra": ["Logros y clasificaciones (rcheevos)", "Modo hardcore vía rcheevos", "Hash calculado de la ROM final parcheada"],
      "profile": ["Perfil RA accesible desde Inicio", "Datos en caché en el dispositivo", "Se actualiza al abrir la pantalla"],
      "switchui": ["Inicio estilo Nintendo Switch", "Carrusel de portadas y dock con color", "Ajustes con categorías laterales"],
      "vanilla": ["Tus ROMs base en la Biblioteca", "OoT y MM importados", "Jugables directo desde la pantalla principal"],
      "gamepad": ["Layout RadialGamePad a medida", "C-buttons, Auto-Z y ButtonStick", "FloatingJoystick para OoT/MM"],
      "tester": ["Prueba del mando N64 en vivo", "Botones y analógicos físicos", "Muestra el mapeo de los juegos"],
      "backup": ["Exporta y restaura partidas", "Archivo ZIP local", "Almacenado en el dispositivo"],
      "refresh": ["Actualización vía WorkManager", "Cada 12 horas", "Sin esfuerzo manual"],
      "i18n": ["Interfaz en pt-BR, en y es", "Detección del idioma del sistema", "Toda la interfaz traducida"],
      "capture": ["Capturas PixelCopy con/sin overlay", "Grabación MP4 vía MediaProjection", "Galería estilo Switch para ver, compartir y borrar"]
    },
    "download.title": "Descargar",
    "download.sub": "Obtén el APK de release firmado",
    "download.lead": "Descarga Zelda 64 Player para Android. El enlace siempre apunta a la versión más reciente en GitHub.",
    "download.versionLabel": "Última versión:",
    "download.button": "Descargar APK",
    "download.note": "Debes aportar tus propias ROMs base (Ocarina of Time / Majora's Mask) legalmente. La app nunca distribuye ROMs."
  }
};

const FEATURE_KEYS = [
  "store", "patch", "byteorder", "checksum",
  "ocarina", "ra", "profile", "switchui", "vanilla", "gamepad", "tester",
  "backup", "refresh", "i18n", "capture"
];

const FEATURE_ICONS = {
  store: "a", patch: "b", byteorder: "c", checksum: "d", ocarina: "e", ra: "f", profile: "g", switchui: "h", vanilla: "i", gamepad: "j", tester: "k",
  backup: "l", refresh: "m", i18n: "n", capture: "o"
};

const LIVE_CATALOG_URL = "https://raw.githubusercontent.com/zonaro/zelda64player/main/catalog/catalog.json";
const FALLBACK_CATALOG_URL = "./catalog.json";

let currentLang = "pt";

function detectLang() {
  const stored = localStorage.getItem("z64p_lang");
  if (stored && I18N[stored]) return stored;
  const nav = (navigator.language || "pt-BR").slice(0, 2).toLowerCase();
  if (I18N[nav]) return nav;
  return "pt";
}

function t(key) {
  return (I18N[currentLang] && I18N[currentLang][key]) || (I18N.en[key]) || key;
}

function setLang(lang) {
  if (!I18N[lang]) return;
  currentLang = lang;
  localStorage.setItem("z64p_lang", lang);
  document.documentElement.lang = lang;
  renderStatic();
  renderFeatures();
  updateLangButtons();
  // Re-render catalog labels if already loaded
  if (window.__catalogData) renderCatalog(window.__catalogData);
  refreshGlyphs();
}

function updateLangButtons() {
  document.querySelectorAll(".lang-btn").forEach(function (btn) {
    btn.classList.toggle("active", btn.dataset.lang === currentLang);
    btn.setAttribute("aria-pressed", btn.dataset.lang === currentLang ? "true" : "false");
  });
}

function renderStatic() {
  document.title = t("meta.title");
  const metaDesc = document.querySelector('meta[name="description"]');
  if (metaDesc) metaDesc.setAttribute("content", t("meta.desc"));

  document.querySelectorAll("[data-i18n]").forEach(function (el) {
    el.textContent = t(el.dataset.i18n);
  });
  document.querySelectorAll("[data-i18n-html]").forEach(function (el) {
    el.innerHTML = t(el.dataset.i18nHtml);
  });
}

function renderFeatures() {
  const grid = document.getElementById("feature-grid");
  if (!grid) return;
  grid.innerHTML = "";
  FEATURE_KEYS.forEach(function (key) {
    const section = document.createElement("section");
    section.className = "feature-section";

    const media = document.createElement("div");
    media.className = "feature-media";
    const icon = document.createElement("div");
    icon.className = "feature-icon";
    icon.textContent = FEATURE_ICONS[key] || "a";
    icon.setAttribute("aria-hidden", "true");
    media.appendChild(icon);

    const body = document.createElement("div");
    body.className = "feature-body";

    const h3 = document.createElement("h3");
    h3.textContent = t("f." + key + ".title");

    const p = document.createElement("p");
    p.textContent = t("f." + key + ".desc");

    const ul = document.createElement("ul");
    ul.className = "feature-points";
    const pointsSrc = (I18N[currentLang] && I18N[currentLang].fPoints && I18N[currentLang].fPoints[key]) ||
      (I18N.en.fPoints && I18N.en.fPoints[key]) || [];
    (Array.isArray(pointsSrc) ? pointsSrc : []).forEach(function (pt) {
      const li = document.createElement("li");
      li.textContent = pt;
      ul.appendChild(li);
    });

    body.appendChild(h3);
    body.appendChild(p);
    body.appendChild(ul);

    section.appendChild(media);
    section.appendChild(body);
    grid.appendChild(section);
  });
}

function coverPlaceholder(name) {
  const ph = document.createElement("div");
  ph.className = "placeholder";
  const ic = document.createElement("div");
  ic.className = "ph-icon";
  ic.textContent = "a";
  const nm = document.createElement("div");
  nm.className = "ph-name";
  nm.textContent = name || t("card.coverFallback");
  ph.appendChild(ic);
  ph.appendChild(nm);
  return ph;
}

function renderCatalog(data) {
  window.__catalogData = data;
  const state = document.getElementById("catalog-state");
  const grid = document.getElementById("catalog-grid");
  const countEl = document.getElementById("catalog-count");
  if (!grid) return;

  const hacks = (data && Array.isArray(data.hacks)) ? data.hacks : [];
  grid.innerHTML = "";
  if (state) state.style.display = "none";

  if (countEl) {
    countEl.textContent = hacks.length + " " + t("catalog.count");
  }

  hacks.forEach(function (hack) {
    const card = document.createElement("article");
    card.className = "hack-card";

    const cover = document.createElement("div");
    cover.className = "hack-cover";
    if (hack.coverImageUrl) {
      const img = document.createElement("img");
      img.loading = "lazy";
      img.alt = hack.name || "";
      img.src = hack.coverImageUrl;
      img.addEventListener("error", function () {
        cover.innerHTML = "";
        cover.appendChild(coverPlaceholder(hack.name));
      });
      cover.appendChild(img);
    } else {
      cover.appendChild(coverPlaceholder(hack.name));
    }

    const body = document.createElement("div");
    body.className = "hack-body";

    const title = document.createElement("div");
    title.className = "hack-title";
    title.textContent = hack.name || "";

    const meta = document.createElement("div");
    meta.className = "hack-meta";
    meta.innerHTML =
      "<div>" + t("card.author") + ": <b>" + escapeHtml(hack.author || "-") + "</b></div>" +
      "<div>" + t("card.version") + ": <b>" + escapeHtml(hack.version || "-") + "</b></div>" +
      "<div>" + t("card.baseRom") + ": <b>" + escapeHtml(hack.baseRom && hack.baseRom.name ? hack.baseRom.name : "-") + "</b></div>";

    const desc = document.createElement("div");
    desc.className = "hack-desc";
    desc.textContent = hack.description || "";

    body.appendChild(title);
    body.appendChild(meta);
    body.appendChild(desc);

    if (Array.isArray(hack.tags) && hack.tags.length) {
      const tags = document.createElement("div");
      tags.className = "hack-tags";
      hack.tags.forEach(function (tag) {
        const chip = document.createElement("span");
        chip.className = "chip";
        chip.textContent = tag;
        tags.appendChild(chip);
      });
      body.appendChild(tags);
    }

    if (Array.isArray(hack.compatibleCores) && hack.compatibleCores.length) {
      const cores = document.createElement("div");
      cores.className = "hack-cores";
      cores.innerHTML = t("card.cores") + ": " +
        hack.compatibleCores.map(function (c) {
          return '<span class="core">' + escapeHtml(c) + "</span>";
        }).join(", ");
      body.appendChild(cores);
    }

    card.appendChild(cover);
    card.appendChild(body);
    grid.appendChild(card);
  });
}

function showCatalogLoading() {
  const state = document.getElementById("catalog-state");
  const grid = document.getElementById("catalog-grid");
  if (grid) grid.innerHTML = "";
  if (state) {
    state.style.display = "block";
    state.innerHTML = '<div class="spinner"></div><div>' + t("catalog.loading") + "</div>";
  }
}

function showCatalogError() {
  const state = document.getElementById("catalog-state");
  const grid = document.getElementById("catalog-grid");
  if (grid) grid.innerHTML = "";
  if (state) {
    state.style.display = "block";
    state.innerHTML =
      "<div>" + t("catalog.error") + "</div>" +
      '<button class="btn-retry" id="catalog-retry">' + t("catalog.retry") + "</button>";
    const btn = document.getElementById("catalog-retry");
    if (btn) btn.addEventListener("click", loadCatalog);
  }
}

function escapeHtml(str) {
  return String(str == null ? "" : str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

async function fetchJson(url) {
  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) throw new Error("HTTP " + res.status);
  return res.json();
}

async function loadCatalog() {
  showCatalogLoading();
  try {
    const data = await fetchJson(LIVE_CATALOG_URL);
    renderCatalog(data);
  } catch (e) {
    try {
      const data = await fetchJson(FALLBACK_CATALOG_URL);
      renderCatalog(data);
    } catch (e2) {
      showCatalogError();
    }
  }
}

function fetchLatestRelease() {
  const verEl = document.getElementById("download-version");
  const dateEl = document.getElementById("download-date");
  if (!verEl) return;
  fetch("https://api.github.com/repos/zonaro/zelda64player/releases/latest", { cache: "no-store" })
    .then(function (r) { if (!r.ok) throw new Error("bad"); return r.json(); })
    .then(function (data) {
      if (data && data.tag_name) {
        verEl.textContent = data.tag_name;
        if (dateEl && data.published_at) {
          const d = new Date(data.published_at);
          if (!isNaN(d)) dateEl.textContent = "(" + d.toISOString().slice(0, 10) + ")";
        }
      }
    })
    .catch(function () { /* keep default version text on failure */ });
  }

function shouldSkipGlyphs(el) {
  if (!el || el.nodeType !== 1) return false;
  const tag = el.tagName.toLowerCase();
  if (tag === "img" || tag === "svg" || tag === "br" || tag === "script" || tag === "style") return true;
  if (el.hasAttribute("data-no-split")) return true;
  const cls = (typeof el.className === "string") ? el.className : "";
  if (/icon|symbol|ph-|hylian-symbol|feature-icon/i.test(cls)) return true;
  return false;
}

function splitTextNode(node, counter) {
  const text = node.nodeValue;
  if (!text) return;
  const frag = document.createDocumentFragment();
  for (let i = 0; i < text.length; i++) {
    const ch = text.charAt(i);
    if (ch === " " || ch === " ") {
      frag.appendChild(document.createTextNode(" "));
      continue;
    }
    const span = document.createElement("span");
    span.className = "glyph";
    span.style.setProperty("--i", counter.n);
    counter.n++;
    span.textContent = ch;
    frag.appendChild(span);
  }
  node.parentNode.replaceChild(frag, node);
}

function splitElementGlyphs(el, counter) {
  const kids = Array.prototype.slice.call(el.childNodes);
  for (let i = 0; i < kids.length; i++) {
    const node = kids[i];
    if (node.nodeType === 3) {
      splitTextNode(node, counter);
    } else if (node.nodeType === 1) {
      if (shouldSkipGlyphs(node)) continue;
      if (node.id === "catalog-grid") continue;
      splitElementGlyphs(node, counter);
    }
  }
}

function splitGlyphs(zone) {
  const counter = { n: 0 };
  splitElementGlyphs(zone, counter);
  const count = counter.n;
  const step = Math.min(30, Math.floor(1200 / Math.max(1, count)));
  zone.style.setProperty("--step", step + "ms");
  if (zone.classList.contains("font-awakened")) {
    const glyphs = zone.querySelectorAll(".glyph");
    for (let i = 0; i < glyphs.length; i++) glyphs[i].classList.add("is-static");
  }
}

function refreshGlyphs() {
  const zones = document.querySelectorAll("[data-type-zone]");
  for (let i = 0; i < zones.length; i++) splitGlyphs(zones[i]);
}

function setupTypographyAwakening() {
  const zones = Array.from(document.querySelectorAll("[data-type-zone]"));
  if (!zones.length) return;

  const awaken = function (zone) {
    if (!zone.classList.contains("font-awakened")) {
      zone.classList.add("font-awakened");
    }
  };

  // IntersectionObserver with rootMargin to trigger earlier (more reliable on mobile)
  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          awaken(entry.target);
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.15, rootMargin: "100px 0px" });

    zones.forEach(function (zone) { observer.observe(zone); });
  }

  // Scroll-based backup checker (runs always, catches any missed zones on mobile)
  let scrollTicking = false;
  const checkVisibilityOnScroll = function () {
    if (scrollTicking) return;
    scrollTicking = true;
    requestAnimationFrame(function () {
      const vh = window.innerHeight;
      const triggerPoint = vh * 0.85; // same as fallback logic
      zones.forEach(function (zone) {
        if (!zone.classList.contains("font-awakened")) {
          const rect = zone.getBoundingClientRect();
          if (rect.top < triggerPoint && rect.bottom > 0) {
            awaken(zone);
          }
        }
      });
      scrollTicking = false;
    });
  };

  // Initial check (in case some zones are already in view)
  checkVisibilityOnScroll();

  // Listen for scroll with passive listener
  window.addEventListener("scroll", checkVisibilityOnScroll, { passive: true });

  // Also check on resize (mobile address bar hide/show changes viewport)
  window.addEventListener("resize", checkVisibilityOnScroll, { passive: true });
}

document.addEventListener("DOMContentLoaded", function () {
  currentLang = detectLang();
  document.documentElement.lang = currentLang;
  renderStatic();
  renderFeatures();
  updateLangButtons();
  document.querySelectorAll(".lang-btn").forEach(function (btn) {
    btn.addEventListener("click", function () { setLang(btn.dataset.lang); });
  });
  loadCatalog();
  fetchLatestRelease();
  refreshGlyphs();
  setupTypographyAwakening();
});
