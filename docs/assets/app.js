/* Zelda 64 Player — landing page logic
   User-facing copy in pt-BR, English and Spanish.
*/

const I18N = {
  pt: {
    "meta.title": "Zelda 64 Player — Suas aventuras Zelda 64 no Android",
    "meta.desc": "Descubra e jogue aventuras de Zelda 64 no Android. Biblioteca pessoal, Auto-Ocarina, RetroAchievements, controles flexíveis e capturas. Gratuito, GPL-3.0 e sem ROMs incluídas.",
    "nav.features": "O app", "nav.catalog": "Aventuras", "nav.download": "Baixar",
    "hero.title.pre": "Zelda 64", "hero.title.accent": "Player",
    "hero.subtitle": "Suas aventuras de Zelda 64, prontas para jogar no Android. Encontre novos mundos, mantenha sua biblioteca por perto e volte para a jornada quando quiser.",
    "hero.cta": "Baixar para Android", "hero.secondaryCta": "Conheça o app",
    "hero.badge1": "Gratuito para Android", "hero.badge2": "Português, English e Español", "hero.badge3": "Sem ROMs incluídas",
    "features.title": "Feito para a sua próxima aventura", "features.sub": "Menos configuração, mais tempo explorando.",
    "start.title": "Comece em poucos passos", "start.sub": "Você leva suas próprias ROMs; o resto acontece dentro do app.",
    "start.step1": "Baixe o Zelda 64 Player no seu Android.",
    "start.step2": "Importe uma cópia sua, obtida legalmente, de Ocarina of Time ou Majora's Mask.",
    "start.step3": "Escolha uma aventura no catálogo e comece a jogar.",
    "catalog.title": "Encontre sua próxima aventura", "catalog.sub": "Uma prévia das aventuras que você pode descobrir no catálogo do app.",
    "catalog.loading": "Buscando aventuras...", "catalog.error": "Não foi possível mostrar as aventuras agora.", "catalog.retry": "Tentar novamente", "catalog.count": "aventuras para explorar",
    "card.author": "Por", "card.baseRom": "Jogo necessário", "card.coverFallback": "Aventura Zelda 64",
    "download.title": "Pronto para começar?", "download.sub": "O Zelda 64 Player é gratuito para Android.",
    "download.lead": "Baixe a versão mais recente e prepare sua biblioteca de aventuras Zelda 64.", "download.versionLabel": "Versão mais recente:", "download.button": "Baixar APK",
    "download.note": "O app não inclui nem distribui ROMs. Para jogar, importe uma cópia sua obtida legalmente de Ocarina of Time ou Majora's Mask.",
    "legal.title": "Jogue respeitando os criadores", "legal.text": "Zelda 64 Player não distribui, baixa nem inclui ROMs. O aplicativo usa apenas as ROMs que você importa legalmente e é software livre sob a licença GPL-3.0. Não é afiliado à Nintendo.",
    "footer.text": "Zelda 64 Player é software livre sob GPL-3.0. Não afiliado à Nintendo.", "footer.source": "Ver código-fonte", "footer.license": "Licença GPL-3.0", "footer.releases": "Todas as versões",
    "f.discover.title": "Descubra novas jornadas", "f.discover.desc": "Explore um catálogo de aventuras feitas pela comunidade, com capas, autores e detalhes para você escolher a próxima experiência.",
    "f.discover.points": ["Navegue por aventuras em um só lugar", "Veja quem criou cada projeto", "Volte para suas favoritas quando quiser"],
    "f.library.title": "Sua biblioteca, do seu jeito", "f.library.desc": "Mantenha suas próprias versões de Ocarina of Time e Majora's Mask na biblioteca, ao lado das aventuras que você preparar.",
    "f.library.points": ["Jogue seus jogos base diretamente", "Tudo organizado na tela principal", "Sua ROM original permanece intacta"],
    "f.ocarina.title": "Auto-Ocarina", "f.ocarina.desc": "Escolha uma música no menu de pausa e deixe o app tocar as notas para você. Ótimo para seguir explorando sem interromper o ritmo.",
    "f.ocarina.points": ["Canções de Ocarina of Time e Majora's Mask", "Mostra o progresso na tela", "Você pode cancelar a qualquer momento"],
    "f.achievements.title": "Conquistas que acompanham sua jornada", "f.achievements.desc": "Entre com sua conta RetroAchievements para desbloquear conquistas e acompanhar rankings enquanto joga.",
    "f.achievements.points": ["Conquistas aparecem durante a partida", "Acompanhe seu progresso", "Rankings ficam no menu do jogo, sem atrapalhar a ação"],
    "f.controls.title": "Jogue do jeito mais confortável", "f.controls.desc": "Use os controles de toque pensados para Zelda 64 ou conecte um controle físico e confira tudo antes de jogar.",
    "f.controls.points": ["Controles de toque ajustados para OoT e MM", "Compatível com controle físico", "Tela de teste para botões e analógicos"],
    "f.memories.title": "Guarde os melhores momentos", "f.memories.desc": "Capture imagens, grave sua jogada e mantenha seus saves seguros no próprio aparelho.",
    "f.memories.points": ["Capturas com ou sem controles na tela", "Grave vídeos da sua partida", "Galeria local e backup dos saves"]
  },
  en: {
    "meta.title": "Zelda 64 Player — Your Zelda 64 adventures on Android",
    "meta.desc": "Discover and play Zelda 64 adventures on Android. Personal library, Auto-Ocarina, RetroAchievements, flexible controls and captures. Free, GPL-3.0 and with no bundled ROMs.",
    "nav.features": "The app", "nav.catalog": "Adventures", "nav.download": "Download",
    "hero.title.pre": "Zelda 64", "hero.title.accent": "Player",
    "hero.subtitle": "Your Zelda 64 adventures, ready to play on Android. Find new worlds, keep your library close, and return to the journey whenever you like.",
    "hero.cta": "Get it for Android", "hero.secondaryCta": "Explore the app",
    "hero.badge1": "Free for Android", "hero.badge2": "Português, English and Español", "hero.badge3": "No bundled ROMs",
    "features.title": "Made for your next adventure", "features.sub": "Less setup, more time exploring.",
    "start.title": "Start in a few steps", "start.sub": "Bring your own ROMs; the rest happens in the app.",
    "start.step1": "Download Zelda 64 Player on your Android device.",
    "start.step2": "Import a legally obtained copy of Ocarina of Time or Majora's Mask that you own.",
    "start.step3": "Pick an adventure from the catalog and start playing.",
    "catalog.title": "Find your next adventure", "catalog.sub": "A preview of the adventures you can discover in the app catalog.",
    "catalog.loading": "Finding adventures...", "catalog.error": "We could not show the adventures right now.", "catalog.retry": "Try again", "catalog.count": "adventures to explore",
    "card.author": "By", "card.baseRom": "Game needed", "card.coverFallback": "Zelda 64 adventure",
    "download.title": "Ready to begin?", "download.sub": "Zelda 64 Player is free for Android.",
    "download.lead": "Download the latest version and start building your Zelda 64 adventure library.", "download.versionLabel": "Latest version:", "download.button": "Download APK",
    "download.note": "The app does not include or distribute ROMs. To play, import a legally obtained copy of Ocarina of Time or Majora's Mask that you own.",
    "legal.title": "Play with respect for creators", "legal.text": "Zelda 64 Player does not distribute, download or include ROMs. The app only uses ROMs you legally import and is free software under the GPL-3.0 license. It is not affiliated with Nintendo.",
    "footer.text": "Zelda 64 Player is free software under GPL-3.0. Not affiliated with Nintendo.", "footer.source": "View source code", "footer.license": "GPL-3.0 license", "footer.releases": "All releases",
    "f.discover.title": "Discover new journeys", "f.discover.desc": "Browse a catalog of community-made adventures, with cover art, authors and details to help you choose your next experience.",
    "f.discover.points": ["Browse adventures in one place", "See who made each project", "Return to your favorites anytime"],
    "f.library.title": "Your library, your way", "f.library.desc": "Keep your own copies of Ocarina of Time and Majora's Mask in your library, alongside the adventures you prepare.",
    "f.library.points": ["Play your base games directly", "Everything organized on the home screen", "Your original ROM stays untouched"],
    "f.ocarina.title": "Auto-Ocarina", "f.ocarina.desc": "Choose a song from the pause menu and let the app play the notes for you. A great way to keep exploring without breaking your rhythm.",
    "f.ocarina.points": ["Songs for Ocarina of Time and Majora's Mask", "On-screen progress as it plays", "Cancel anytime"],
    "f.achievements.title": "Achievements for your journey", "f.achievements.desc": "Sign in with your RetroAchievements account to unlock achievements and follow leaderboards as you play.",
    "f.achievements.points": ["Achievements appear during play", "Track your progress", "Leaderboards stay in the game menu, out of the action"],
    "f.controls.title": "Play in the most comfortable way", "f.controls.desc": "Use touch controls made for Zelda 64 or connect a physical controller and check everything before you play.",
    "f.controls.points": ["Touch controls tuned for OoT and MM", "Physical-controller support", "Test screen for buttons and sticks"],
    "f.memories.title": "Keep the best moments", "f.memories.desc": "Capture images, record your play and keep saves safe on your own device.",
    "f.memories.points": ["Captures with or without on-screen controls", "Record videos of your session", "Local gallery and save backups"]
  },
  es: {
    "meta.title": "Zelda 64 Player — Tus aventuras de Zelda 64 en Android",
    "meta.desc": "Descubre y juega aventuras de Zelda 64 en Android. Biblioteca personal, Auto-Ocarina, RetroAchievements, controles flexibles y capturas. Gratis, GPL-3.0 y sin ROMs incluidas.",
    "nav.features": "La app", "nav.catalog": "Aventuras", "nav.download": "Descargar",
    "hero.title.pre": "Zelda 64", "hero.title.accent": "Player",
    "hero.subtitle": "Tus aventuras de Zelda 64, listas para jugar en Android. Encuentra nuevos mundos, mantén tu biblioteca cerca y vuelve al viaje cuando quieras.",
    "hero.cta": "Descargar para Android", "hero.secondaryCta": "Conoce la app",
    "hero.badge1": "Gratis para Android", "hero.badge2": "Português, English y Español", "hero.badge3": "Sin ROMs incluidas",
    "features.title": "Hecha para tu próxima aventura", "features.sub": "Menos configuración, más tiempo explorando.",
    "start.title": "Empieza en pocos pasos", "start.sub": "Trae tus propias ROMs; el resto ocurre dentro de la app.",
    "start.step1": "Descarga Zelda 64 Player en tu Android.",
    "start.step2": "Importa una copia tuya obtenida legalmente de Ocarina of Time o Majora's Mask.",
    "start.step3": "Elige una aventura del catálogo y empieza a jugar.",
    "catalog.title": "Encuentra tu próxima aventura", "catalog.sub": "Una vista previa de las aventuras que puedes descubrir en el catálogo de la app.",
    "catalog.loading": "Buscando aventuras...", "catalog.error": "No podemos mostrar las aventuras ahora.", "catalog.retry": "Intentar de nuevo", "catalog.count": "aventuras para explorar",
    "card.author": "Por", "card.baseRom": "Juego necesario", "card.coverFallback": "Aventura Zelda 64",
    "download.title": "¿Listo para empezar?", "download.sub": "Zelda 64 Player es gratis para Android.",
    "download.lead": "Descarga la versión más reciente y prepara tu biblioteca de aventuras Zelda 64.", "download.versionLabel": "Última versión:", "download.button": "Descargar APK",
    "download.note": "La app no incluye ni distribuye ROMs. Para jugar, importa una copia tuya obtenida legalmente de Ocarina of Time o Majora's Mask.",
    "legal.title": "Juega respetando a los creadores", "legal.text": "Zelda 64 Player no distribuye, descarga ni incluye ROMs. La app solo usa las ROMs que importas legalmente y es software libre bajo la licencia GPL-3.0. No está afiliada a Nintendo.",
    "footer.text": "Zelda 64 Player es software libre bajo GPL-3.0. No está afiliado a Nintendo.", "footer.source": "Ver código fuente", "footer.license": "Licencia GPL-3.0", "footer.releases": "Todas las versiones",
    "f.discover.title": "Descubre nuevos viajes", "f.discover.desc": "Explora un catálogo de aventuras creadas por la comunidad, con portadas, autores y detalles para elegir tu próxima experiencia.",
    "f.discover.points": ["Explora aventuras en un solo lugar", "Conoce quién creó cada proyecto", "Vuelve a tus favoritas cuando quieras"],
    "f.library.title": "Tu biblioteca, a tu manera", "f.library.desc": "Guarda tus propias copias de Ocarina of Time y Majora's Mask en la biblioteca, junto a las aventuras que prepares.",
    "f.library.points": ["Juega tus juegos base directamente", "Todo organizado en la pantalla principal", "Tu ROM original permanece intacta"],
    "f.ocarina.title": "Auto-Ocarina", "f.ocarina.desc": "Elige una canción en el menú de pausa y deja que la app toque las notas por ti. Ideal para seguir explorando sin perder el ritmo.",
    "f.ocarina.points": ["Canciones de Ocarina of Time y Majora's Mask", "Muestra el progreso en pantalla", "Puedes cancelarlo en cualquier momento"],
    "f.achievements.title": "Logros para tu aventura", "f.achievements.desc": "Inicia sesión con tu cuenta de RetroAchievements para desbloquear logros y seguir clasificaciones mientras juegas.",
    "f.achievements.points": ["Los logros aparecen durante la partida", "Sigue tu progreso", "Las clasificaciones permanecen en el menú del juego"],
    "f.controls.title": "Juega de la forma más cómoda", "f.controls.desc": "Usa controles táctiles pensados para Zelda 64 o conecta un control físico y comprueba todo antes de jugar.",
    "f.controls.points": ["Controles táctiles ajustados para OoT y MM", "Compatible con controles físicos", "Pantalla de prueba para botones y analógicos"],
    "f.memories.title": "Guarda los mejores momentos", "f.memories.desc": "Captura imágenes, graba tus partidas y mantén las partidas guardadas a salvo en tu dispositivo.",
    "f.memories.points": ["Capturas con o sin controles en pantalla", "Graba vídeos de tu partida", "Galería local y copias de seguridad"]
  }
};

const FEATURE_KEYS = ["discover", "library", "ocarina", "achievements", "controls", "memories"];
const FEATURE_ICONS = { discover: "a", library: "i", ocarina: "e", achievements: "f", controls: "j", memories: "o" };
const LIVE_CATALOG_URL = "https://raw.githubusercontent.com/zonaro/zelda64player/main/catalog/catalog.json";
const FALLBACK_CATALOG_URL = "./catalog.json";
let currentLang = "pt";

function detectLang() {
  const stored = localStorage.getItem("z64p_lang");
  if (stored && I18N[stored]) return stored;
  const nav = (navigator.language || "pt-BR").slice(0, 2).toLowerCase();
  return I18N[nav] ? nav : "pt";
}
function t(key) { return (I18N[currentLang] && I18N[currentLang][key]) || I18N.en[key] || key; }
function setLang(lang) {
  if (!I18N[lang]) return;
  currentLang = lang;
  localStorage.setItem("z64p_lang", lang);
  document.documentElement.lang = lang;
  renderStatic(); renderFeatures(); updateLangButtons();
  if (window.__catalogData) renderCatalog(window.__catalogData);
  refreshGlyphs();
}
function updateLangButtons() {
  document.querySelectorAll(".lang-btn").forEach(function (btn) {
    const active = btn.dataset.lang === currentLang;
    btn.classList.toggle("active", active);
    btn.setAttribute("aria-pressed", active ? "true" : "false");
  });
}
function renderStatic() {
  document.title = t("meta.title");
  const metaDesc = document.querySelector('meta[name="description"]');
  if (metaDesc) metaDesc.setAttribute("content", t("meta.desc"));
  document.querySelectorAll("[data-i18n]").forEach(function (el) { el.textContent = t(el.dataset.i18n); });
}
function renderFeatures() {
  const grid = document.getElementById("feature-grid");
  if (!grid) return;
  grid.innerHTML = "";
  FEATURE_KEYS.forEach(function (key) {
    const card = document.createElement("article"); card.className = "feature-card";
    const icon = document.createElement("div"); icon.className = "feature-icon"; icon.textContent = FEATURE_ICONS[key]; icon.setAttribute("aria-hidden", "true");
    const title = document.createElement("h3"); title.textContent = t("f." + key + ".title");
    const desc = document.createElement("p"); desc.textContent = t("f." + key + ".desc");
    const points = document.createElement("ul"); points.className = "feature-points";
    const entries = t("f." + key + ".points");
    (Array.isArray(entries) ? entries : []).forEach(function (entry) { const item = document.createElement("li"); item.textContent = entry; points.appendChild(item); });
    card.append(icon, title, desc, points); grid.appendChild(card);
  });
}
function coverPlaceholder(name) {
  const placeholder = document.createElement("div"); placeholder.className = "placeholder";
  const icon = document.createElement("div"); icon.className = "ph-icon"; icon.textContent = "a";
  const label = document.createElement("div"); label.className = "ph-name"; label.textContent = name || t("card.coverFallback");
  placeholder.append(icon, label); return placeholder;
}
function renderCatalog(data) {
  window.__catalogData = data;
  const state = document.getElementById("catalog-state"); const grid = document.getElementById("catalog-grid"); const countEl = document.getElementById("catalog-count");
  if (!grid) return;
  const hacks = data && Array.isArray(data.hacks) ? data.hacks : [];
  grid.innerHTML = ""; if (state) state.style.display = "none";
  if (countEl) countEl.textContent = hacks.length + " " + t("catalog.count");
  hacks.forEach(function (hack) {
    const card = document.createElement("article"); card.className = "hack-card";
    const cover = document.createElement("div"); cover.className = "hack-cover";
    if (hack.coverImageUrl) {
      const image = document.createElement("img"); image.loading = "lazy"; image.alt = hack.name || t("card.coverFallback"); image.src = hack.coverImageUrl;
      image.addEventListener("error", function () { cover.innerHTML = ""; cover.appendChild(coverPlaceholder(hack.name)); }); cover.appendChild(image);
    } else cover.appendChild(coverPlaceholder(hack.name));
    const body = document.createElement("div"); body.className = "hack-body";
    const title = document.createElement("h3"); title.className = "hack-title"; title.textContent = hack.name || "";
    const meta = document.createElement("div"); meta.className = "hack-meta";
    meta.innerHTML = "<div>" + t("card.author") + ": <b>" + escapeHtml(hack.author || "-") + "</b></div>" + "<div>" + t("card.baseRom") + ": <b>" + escapeHtml(hack.baseRom && hack.baseRom.name ? hack.baseRom.name : "-") + "</b></div>";
    const desc = document.createElement("p"); desc.className = "hack-desc"; desc.textContent = hack.description || "";
    body.append(title, meta, desc);
    if (Array.isArray(hack.tags) && hack.tags.length) {
      const tags = document.createElement("div"); tags.className = "hack-tags";
      hack.tags.slice(0, 3).forEach(function (tag) { const chip = document.createElement("span"); chip.className = "chip"; chip.textContent = tag; tags.appendChild(chip); }); body.appendChild(tags);
    }
    card.append(cover, body); grid.appendChild(card);
  });
}
function showCatalogLoading() {
  const state = document.getElementById("catalog-state"); const grid = document.getElementById("catalog-grid");
  if (grid) grid.innerHTML = "";
  if (state) { state.style.display = "block"; state.innerHTML = '<div class="spinner"></div><div>' + t("catalog.loading") + "</div>"; }
}
function showCatalogError() {
  const state = document.getElementById("catalog-state"); const grid = document.getElementById("catalog-grid");
  if (grid) grid.innerHTML = "";
  if (state) {
    state.style.display = "block"; state.innerHTML = "<div>" + t("catalog.error") + "</div><button class=\"btn-retry\" id=\"catalog-retry\">" + t("catalog.retry") + "</button>";
    const button = document.getElementById("catalog-retry"); if (button) button.addEventListener("click", loadCatalog);
  }
}
function escapeHtml(str) { return String(str == null ? "" : str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/\"/g, "&quot;").replace(/'/g, "&#39;"); }
async function fetchJson(url) { const response = await fetch(url, { cache: "no-store" }); if (!response.ok) throw new Error("HTTP " + response.status); return response.json(); }
async function loadCatalog() { showCatalogLoading(); try { renderCatalog(await fetchJson(LIVE_CATALOG_URL)); } catch (error) { try { renderCatalog(await fetchJson(FALLBACK_CATALOG_URL)); } catch (fallbackError) { showCatalogError(); } } }
function fetchLatestRelease() {
  const version = document.getElementById("download-version"); const date = document.getElementById("download-date"); if (!version) return;
  fetch("https://api.github.com/repos/zonaro/zelda64player/releases/latest", { cache: "no-store" }).then(function (response) { if (!response.ok) throw new Error("Latest release unavailable"); return response.json(); }).then(function (release) {
    if (!release || !release.tag_name) return; version.textContent = release.tag_name;
    if (date && release.published_at) { const published = new Date(release.published_at); if (!isNaN(published)) date.textContent = "(" + published.toISOString().slice(0, 10) + ")"; }
  }).catch(function () { /* Keep the bundled version when offline. */ });
}
function shouldSkipGlyphs(el) {
  if (!el || el.nodeType !== 1) return false; const tag = el.tagName.toLowerCase();
  if (tag === "img" || tag === "svg" || tag === "br" || tag === "script" || tag === "style") return true;
  if (el.hasAttribute("data-no-split") || el.classList.contains("glyph")) return true;
  const cls = typeof el.className === "string" ? el.className : ""; return /icon|symbol|ph-|hylian-symbol|feature-icon/i.test(cls);
}
function splitTextNode(node, counter) {
  const text = node.nodeValue; if (!text) return; const fragment = document.createDocumentFragment();
  for (let i = 0; i < text.length; i++) { const character = text.charAt(i); if (character === " " || character === " ") { fragment.appendChild(document.createTextNode(" ")); continue; } const glyph = document.createElement("span"); glyph.className = "glyph"; glyph.style.setProperty("--i", counter.n++); glyph.textContent = character; fragment.appendChild(glyph); }
  node.parentNode.replaceChild(fragment, node);
}
function splitElementGlyphs(el, counter) { Array.prototype.slice.call(el.childNodes).forEach(function (node) { if (node.nodeType === 3) splitTextNode(node, counter); else if (node.nodeType === 1 && !shouldSkipGlyphs(node) && node.id !== "catalog-grid") splitElementGlyphs(node, counter); }); }
function splitGlyphs(zone) { const counter = { n: 0 }; splitElementGlyphs(zone, counter); zone.style.setProperty("--step", Math.min(30, Math.floor(1200 / Math.max(1, counter.n))) + "ms"); if (zone.classList.contains("font-awakened")) zone.querySelectorAll(".glyph").forEach(function (glyph) { glyph.classList.add("is-static"); }); }
function refreshGlyphs() { document.querySelectorAll("[data-type-zone]").forEach(splitGlyphs); }
function setupTypographyAwakening() {
  const zones = Array.from(document.querySelectorAll("[data-type-zone]")); const awaken = function (zone) { zone.classList.add("font-awakened"); };
  if ("IntersectionObserver" in window) { const observer = new IntersectionObserver(function (entries) { entries.forEach(function (entry) { if (entry.isIntersecting) { awaken(entry.target); observer.unobserve(entry.target); } }); }, { threshold: 0.15, rootMargin: "100px 0px" }); zones.forEach(function (zone) { observer.observe(zone); }); }
  const checkVisibility = function () { const triggerPoint = window.innerHeight * 0.85; zones.forEach(function (zone) { const rect = zone.getBoundingClientRect(); if (rect.top < triggerPoint && rect.bottom > 0) awaken(zone); }); };
  checkVisibility(); window.addEventListener("scroll", checkVisibility, { passive: true }); window.addEventListener("resize", checkVisibility, { passive: true });
}
document.addEventListener("DOMContentLoaded", function () {
  currentLang = detectLang(); document.documentElement.lang = currentLang; renderStatic(); renderFeatures(); updateLangButtons();
  document.querySelectorAll(".lang-btn").forEach(function (button) { button.addEventListener("click", function () { setLang(button.dataset.lang); }); });
  loadCatalog(); fetchLatestRelease(); refreshGlyphs(); setupTypographyAwakening();
});
