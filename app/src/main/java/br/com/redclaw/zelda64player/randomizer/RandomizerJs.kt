package br.com.redclaw.zelda64player.randomizer

/**
 * JavaScript snippets injected into the ootrandomizer.com generator / seed pages.
 *
 * Two responsibilities:
 *  - [INJECT_ROM_AUTOFILL]: programmatically click the ROM `<input type=file>`
 *    on the seed page so [android.webkit.WebChromeClient.onShowFileChooser]
 *    fires and the app can supply the user's vanilla ROM URI without opening a
 *    system file chooser.
 *  - [hookDownload]: monkeypatch the site's blob-download mechanisms so the
 *    patched ROM bytes are captured and POSTed to the local [LocalRomServer]
 *    (falling back to the `@JavascriptInterface` [WebViewJsBridge] when the
 *    localhost POST is unreachable).
 *
 * The DOM of ootrandomizer.com may change; both snippets use defensive
 * fallbacks (first file input, multiple hook points) and are documented as
 * best-effort. The app signals capture success/failure back to the user.
 */
object RandomizerJs {

    /**
     * Clicks the ROM file input. Uses a MutationObserver so it works even if the
     * input is added to the DOM after page load. Prefers an input whose
     * label/id/name/accept mentions "rom"; falls back to the first file input.
     */
    const val INJECT_ROM_AUTOFILL: String = """
        (function() {
            function findRomInput() {
                var inputs = document.querySelectorAll('input[type=file]');
                for (var i = 0; i < inputs.length; i++) {
                    var el = inputs[i];
                    var label = '';
                    if (el.id) {
                        var l = document.querySelector('label[for="' + el.id + '"]');
                        if (l) label += ' ' + l.textContent;
                    }
                    if (el.getAttribute('accept')) label += ' ' + el.getAttribute('accept');
                    if (el.name) label += ' ' + el.name;
                    if (el.className) label += ' ' + el.className;
                    if (label.toLowerCase().indexOf('rom') !== -1) return el;
                }
                return inputs.length ? inputs[0] : null;
            }
            function tryClick() {
                var input = findRomInput();
                if (input) { input.click(); return true; }
                return false;
            }
            if (!tryClick()) {
                var observer = new MutationObserver(function() {
                    if (tryClick()) observer.disconnect();
                });
                if (document.body) observer.observe(document.body, { childList: true, subtree: true });
            }
        })();
    """

    /**
     * Install download hooks. [port] is the localhost [LocalRomServer] port.
     * On a blob download the snippet POSTs the blob to
     * `http://127.0.0.1:<port>/patch`; if that fails it streams the blob to the
     * `@JavascriptInterface` bridge `window.AndroidRandomizer` as base64 chunks.
     */
    fun hookDownload(port: Int): String = """
        (function() {
            var PORT = $port;
            var BRIDGE = window.AndroidRandomizer;
            function fileNameFrom(anchor) {
                var name = (anchor && anchor.getAttribute && anchor.getAttribute('download')) || '';
                return name || null;
            }
            function sendBlob(blob, fileName) {
                var url = 'http://127.0.0.1:' + PORT + '/patch';
                fetch(url, { method: 'POST', body: blob, headers: { 'X-Patch-Filename': fileName || '' } })
                    .then(function(r) { if (!r.ok) throw new Error('server ' + r.status); })
                    .catch(function() {
                        if (!BRIDGE) return;
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            var b64 = reader.result.split(',')[1];
                            var size = blob.size;
                            var chunk = 512 * 1024;
                            var offset = 0;
                            function next() {
                                if (offset >= size) { BRIDGE.endCapture(fileName); return; }
                                var end = Math.min(offset + chunk, size);
                                blob.slice(offset, end).arrayBuffer().then(function(ab) {
                                    var arr = new Uint8Array(ab);
                                    var bin = '';
                                    for (var i = 0; i < arr.length; i++) bin += String.fromCharCode(arr[i]);
                                    BRIDGE.appendChunk(btoa(bin));
                                    offset = end;
                                    next();
                                });
                            }
                            next();
                        };
                        reader.readAsDataURL(blob);
                    });
            }
            function captureAnchor(anchor) {
                var href = anchor.href;
                if (!href || href.indexOf('blob:') !== 0) return;
                var fileName = fileNameFrom(anchor);
                fetch(href).then(function(r) { return r.blob(); }).then(function(blob) {
                    sendBlob(blob, fileName);
                }).catch(function() {});
            }
            var origClick = HTMLAnchorElement.prototype.click;
            HTMLAnchorElement.prototype.click = function() {
                if (this.getAttribute && this.getAttribute('download') && this.href && this.href.indexOf('blob:') === 0) {
                    captureAnchor(this);
                    return;
                }
                return origClick.apply(this, arguments);
            };
            var origCreate = URL.createObjectURL;
            URL.createObjectURL = function(blob) {
                var url = origCreate.apply(this, arguments);
                setTimeout(function() {
                    var anchors = document.querySelectorAll('a[download]');
                    for (var i = 0; i < anchors.length; i++) {
                        if (anchors[i].href === url) { captureAnchor(anchors[i]); break; }
                    }
                }, 0);
                return url;
            };
            var origOpen = window.open;
            window.open = function(url) {
                if (url && url.indexOf('blob:') === 0) {
                    fetch(url).then(function(r) { return r.blob(); }).then(function(blob) {
                        sendBlob(blob, null);
                    }).catch(function() {});
                    return null;
                }
                return origOpen.apply(this, arguments);
            };
        })();
    """
}
