#!/usr/bin/env bash
# Zelda 64 Player — Release & Deploy via ADB
#
# Runs the project's release.sh (which builds the signed/unsigned release APK
# and publishes the GitHub release) and then installs the resulting APK on a
# connected device/emulator via adb. Optionally launches the app afterwards.
#
# Usage:
#   ./tools/deploy_release.sh            # release + install + launch
#   LAUNCH=0 ./tools/deploy_release.sh   # release + install only
#   ADB=/path/to/adb ./tools/deploy_release.sh
#
# Notes:
#   - The build logic lives in release.sh at the repo root; this script only
#     adds the ADB install + launch step on top of it (no duplication). By
#     default release.sh ONLY builds the APK — no GitHub interaction happens
#     here, so gh/auth are not required for a local deploy.
#   - To also tag, push and publish the GitHub release, run release.sh --github
#     (or the "Release on GitHub" VS Code task) separately.
#   - A signed APK requires keystore.properties at the repo root (gitignored).
#     Without it, AGP emits an unsigned APK (app-release-unsigned.apk) which
#     adb install will reject on a production device.
#   - Set ANDROID_SERIAL to target a specific device when several are connected.

set -euo pipefail

# ---- Config ----------------------------------------------------------------
PACKAGE="br.com.redclaw.zelda64player"
ADB="${ADB:-adb}"
LAUNCH="${LAUNCH:-1}"            # set LAUNCH=0 to skip launching after install
APK_DIR="app/build/outputs/apk/release"

# Resolve repo root and the shared release script (no duplicated build logic).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RELEASE_SCRIPT="$REPO_ROOT/release.sh"

# ---- Colors (disabled when not a TTY) --------------------------------------
if [[ -t 1 ]]; then
    RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
else
    RED=''; GREEN=''; YELLOW=''; BLUE=''; NC=''
fi

log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ---- Steps -----------------------------------------------------------------
check_device() {
    if ! command -v "$ADB" >/dev/null 2>&1; then
        log_error "adb not found in PATH. Install Android platform-tools or set ADB=/path/to/adb."
        exit 1
    fi

    local devices
    devices=$("$ADB" devices 2>/dev/null | awk 'NR>1 && $1!="" {print $1}')
    local count
    count=$(printf '%s\n' "$devices" | grep -c .)

    if [[ "$count" -eq 0 ]]; then
        log_error "No ADB devices/emulators connected. Connect a device or start an emulator."
        exit 1
    fi

    if [[ "$count" -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
        local first
        first=$(printf '%s\n' "$devices" | head -n1)
        log_warn "Multiple devices found; using the first one: $first (set ANDROID_SERIAL to override)."
        export ANDROID_SERIAL="$first"
    else
        log_ok "Device ready: $("$ADB" get-serialno 2>/dev/null)"
    fi
}

# The actual build + GitHub release is handled by release.sh at the repo root.
run_release() {
    if [[ ! -f "$RELEASE_SCRIPT" ]]; then
        log_error "release.sh not found at $RELEASE_SCRIPT"
        exit 1
    fi
    log_info "Running release.sh (build + GitHub release)..."
    "$RELEASE_SCRIPT"
    log_ok "release.sh finished."
}

find_apk() {
    local apk
    apk=$(find "$APK_DIR" -name "*.apk" 2>/dev/null | head -n1)
    if [[ -z "$apk" ]]; then
        log_error "No APK found in $APK_DIR. Did the build succeed?"
        exit 1
    fi
    echo "$apk"
}

install_apk() {
    local apk="$1"
    if [[ "$apk" == *"-unsigned"* ]]; then
        log_warn "APK is UNSIGNED ($apk). adb install will fail on production devices."
        log_warn "Create keystore.properties at the repo root to produce a signed APK."
    fi
    log_info "Installing $apk ..."
    "$ADB" install -r "$apk"
    log_ok "Installed."
}

launch_app() {
    log_info "Launching $PACKAGE ..."
    "$ADB" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || \
        log_warn "Could not auto-launch $PACKAGE (monkey returned non-zero)."
    log_ok "Launch intent sent."
}

main() {
    echo "=================================================="
    echo "  Zelda 64 Player — Build & Deploy (ADB)"
    echo "=================================================="
    echo
    check_device
    run_release
    local apk
    apk=$(find_apk)
    install_apk "$apk"
    if [[ "${LAUNCH}" != "0" ]]; then
        launch_app
    fi
    echo
    log_ok "Deploy complete: $apk"
}

main "$@"
