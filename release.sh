#!/usr/bin/env bash
# Zelda 64 Player — Automated Release Script
# Generates version at build time, commits, pushes, and creates GitHub release via GH CLI
# Version format: {yy}.{dayOfYear}.{hhmm} (e.g., 26.238.1430)
#
# By default this script ONLY builds the release APK (no GitHub interaction).
# Pass --github to also tag, push and publish the GitHub release.

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() { echo -e "${BLUE}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# Check if gh CLI is installed and authenticated
check_gh_cli() {
    if ! command -v gh &> /dev/null; then
        log_error "GitHub CLI (gh) not found. Install it: https://cli.github.com/"
        exit 1
    fi
    if ! gh auth status &> /dev/null; then
        log_error "GitHub CLI not authenticated. Run: gh auth login"
        exit 1
    fi
    log_success "GitHub CLI is ready"
}

# Check if we're in a git repo with clean working tree (except for version changes)
check_git_status() {
    if ! git rev-parse --git-dir &> /dev/null; then
        log_error "Not a git repository"
        exit 1
    fi
    log_success "Git repository detected"
}

# Build the app to generate version
build_app() {
    log_info "Building app to generate version..."
    ./gradlew :app:assembleRelease --no-daemon -q
    log_success "Build completed"
}

# Extract version from generated BuildConfig or APK
get_generated_version() {
    # The version is generated at build time in BuildConfig
    # We can extract it from the generated BuildConfig class
    local build_config_path="app/build/generated/source/buildConfig/release/br/com/redclaw/zelda64player/BuildConfig.java"
    
    if [[ -f "$build_config_path" ]]; then
        local version_name=$(grep -E 'VERSION_NAME\s*=' "$build_config_path" | sed -E 's/.*"([^"]+)".*/\1/')
        local version_code=$(grep -E 'VERSION_CODE\s*=' "$build_config_path" | sed -E 's/.*= (\d+);.*/\1/')
        echo "$version_name|$version_code"
    else
        # Fallback: compute from current time (should match build time)
        local now=$(date -u +"%y.%j.%H%M")
        local code=$(date -u +"%y%j%H%M")
        echo "$now|$code"
    fi
}

# Create git tag
create_git_tag() {
    local version_name=$1
    local tag="v$version_name"
    
    log_info "Creating git tag: $tag"
    git tag -a "$tag" -m "Release $tag"
    log_success "Tag created: $tag"
}

# Commit and push changes
commit_and_push() {
    local version_name=$1
    local version_code=$2
    
    log_info "Committing version changes..."
    git add app/build.gradle.kts
    git commit -m "chore: release v$version_name (versionCode: $version_code)" || {
        log_warn "No changes to commit (version may be same)"
    }
    
    log_info "Pushing to origin..."
    git push origin main
    git push origin --tags
    log_success "Pushed to origin"
}

# Delete existing release if it exists
delete_existing_release() {
    local version_name=$1
    local tag="v$version_name"
    
    log_info "Checking for existing release with tag: $tag"
    if gh release view "$tag" &> /dev/null; then
        log_warn "Release $tag exists. Deleting..."
        gh release delete "$tag" --yes
        log_success "Deleted existing release: $tag"
    else
        log_info "No existing release found for $tag"
    fi
}

# Create new GitHub release
create_github_release() {
    local version_name=$1
    local version_code=$2
    local tag="v$version_name"
    
    log_info "Creating GitHub release: $tag"
    
    # Find the built APK
    local apk_path=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
    if [[ -z "$apk_path" ]]; then
        log_error "No release APK found in app/build/outputs/apk/release/"
        exit 1
    fi
    
    # Generate release notes
    local release_notes="## Zelda 64 Player v$version_name

**Build:** $version_name (versionCode: $version_code)
**Date:** $(date -u +"%Y-%m-%d %H:%M UTC")

### Changes
- Automated release via build-time version generation
- Version format: \`{yy}.{dayOfYear}.{hhmm}\`

### Installation
Download the APK below and install on your Android device (API 24+).

> **Note:** This app does not include any ROMs. You must legally own and import your own Ocarina of Time and Majora's Mask ROMs."
    
    gh release create "$tag" "$apk_path" \
        --title "Zelda 64 Player $tag" \
        --notes "$release_notes" \
        --latest
    
    log_success "Release created: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases/tag/$tag"
}

# Main flow
# DO_GITHUB=1 (set via --github) publishes the GitHub release; otherwise we
# only build the APK locally.
main() {
    echo "=========================================="
    echo "  Zelda 64 Player — Release Automation"
    echo "=========================================="
    echo

    build_app

    local version_info=$(get_generated_version)
    local version_name=$(echo "$version_info" | cut -d'|' -f1)
    local version_code=$(echo "$version_info" | cut -d'|' -f2)

    log_info "Generated version: $version_name (code: $version_code)"

    if [[ "${DO_GITHUB:-0}" -eq 1 ]]; then
        check_gh_cli
        check_git_status
        create_git_tag "$version_name"
        commit_and_push "$version_name" "$version_code"
        delete_existing_release "$version_name"
        create_github_release "$version_name" "$version_code"

        echo
        log_success "🎉 Release v$version_name completed successfully!"
        echo "   Version: $version_name"
        echo "   Version Code: $version_code"
        echo "   Tag: v$version_name"
    else
        log_success "Build complete: v$version_name (APK in app/build/outputs/apk/release/)"
        log_info "GitHub release skipped. Re-run with --github to tag, push and publish."
    fi
}

# Parse flags: only --github triggers the GitHub release steps.
DO_GITHUB=0
for arg in "$@"; do
    case "$arg" in
        --github) DO_GITHUB=1 ;;
        *) log_warn "Unknown argument ignored: $arg" ;;
    esac
done

main