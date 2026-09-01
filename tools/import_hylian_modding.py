#!/usr/bin/env python3
"""Import the public Hylian Modding indexes into Main Store.

Only public catalog metadata is read. Patch files are never downloaded or
committed by this tool: Hylian Modding does not publish patch checksums in its
``mod.json`` documents, so direct downloads are intentionally represented with
an empty checksum. The Android patch pipeline still validates BPS/Xdelta source
data when the user explicitly chooses to install a patch.

Run from the repository root:

    python3 tools/import_hylian_modding.py

Use ``--dry-run`` to inspect the number of records without modifying a file.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import copy
import json
import re
import sys
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen


BASE_URL = "https://hylianmodding.com/"
USER_AGENT = "Zelda64PlayerCatalogImporter/1.0 (+https://github.com/zonaro/zelda64player)"
DIRECT_PATCH_EXTENSIONS = (".bps", ".ips", ".xdelta", ".zip")
CATALOG_VERSION = 3

# Known developer sites for curated Main Store entries that have no Hylian Modding counterpart
# or whose patch is mirrored on zonaro/zelda64player. This ensures catalog.json always has
# useful developerLinks even for hand-curated records.
CURATED_DEVELOPER_LINKS: dict[str, list[dict[str, str]]] = {
    "ocarina_of_time_dx": [{"label": "GitHub", "url": "https://github.com/N64DX/oot-dx"}],
    "ultimate_trial": [{"label": "GitHub", "url": "https://github.com/RichieUltimate/ultimate-trial"}],
    "majoras_mask_redux": [{"label": "Romhacking.net", "url": "https://www.romhacking.net/hacks/5122/"}],
    "sealed_palace": [{"label": "Romhacking.net", "url": "https://www.romhacking.net/hacks/7663/"}],
    "dawn_and_dusk": [{"label": "GitHub", "url": "https://github.com/LuigiBlood/zelda64-dawn-dusk"}],
    "the_missing_link": [{"label": "GitHub", "url": "https://github.com/zeldaret/oot"}],
}


@dataclass(frozen=True)
class Source:
    id: str
    index_url: str


SOURCES = (
    Source("mods", f"{BASE_URL}mods/index.json"),
    Source("2025-crossover", f"{BASE_URL}competitions/2025-crossover/index.json"),
    Source("2024-horror", f"{BASE_URL}competitions/2024-horror/index.json"),
    Source("2023-escape-room", f"{BASE_URL}competitions/2023-escape-room/index.json"),
    Source("hm-jam-1", f"{BASE_URL}competitions/hm-jam-1/index.json"),
)


class ImportError(RuntimeError):
    """A remote catalog could not be read or did not have the expected shape."""


def fetch_json(url: str, timeout: int) -> dict[str, Any]:
    request = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    try:
        with urlopen(request, timeout=timeout) as response:
            payload = response.read().decode("utf-8")
    except (HTTPError, URLError, TimeoutError) as error:
        raise ImportError(f"could not fetch {url}: {error}") from error
    try:
        value = json.loads(payload)
    except json.JSONDecodeError as error:
        raise ImportError(f"invalid JSON at {url}: {error}") from error
    if not isinstance(value, dict):
        raise ImportError(f"expected an object at {url}")
    return value


def to_strings(value: Any) -> list[str]:
    if isinstance(value, str):
        return [value] if value.strip() else []
    if isinstance(value, list):
        return [item.strip() for item in value if isinstance(item, str) and item.strip()]
    return []


def absolute_url(value: str) -> str:
    return urljoin(BASE_URL, value.strip())


def filename_from_url(url: str) -> str:
    filename = Path(urlparse(url).path).name
    return filename or "patch"


def direct_patch(url: str) -> bool:
    return urlparse(url).path.lower().endswith(DIRECT_PATCH_EXTENSIONS)


def is_youtube_url(url: str) -> bool:
    host = urlparse(url).netloc.lower()
    return "youtube.com" in host or "youtu.be" in host or "youtube-nocookie.com" in host


def youtube_id_from_url(url: str) -> str | None:
    m = re.search(r'(?:v=|youtu\.be/|embed/|shorts/)([A-Za-z0-9_-]{11})', url)
    if m:
        return m.group(1)
    m = re.search(r'youtube\.com/watch\?.*v=([A-Za-z0-9_-]{11})', url)
    if m:
        return m.group(1)
    return None


def video_urls(document: dict[str, Any]) -> list[str]:
    """Collect optional present/future video fields without assuming one schema."""
    result: list[str] = []
    for key in ("video", "videos", "youtube", "trailer"):
        for value in to_strings(document.get(key)):
            url = absolute_url(value) if value.startswith("/") else value
            if url and url not in result:
                result.append(url)
    return result


def extract_markdown_links(text: str) -> list[dict[str, str]]:
    pattern = re.compile(r'\[([^\]]+)\]\((https?://[^\)]+)\)')
    result: list[dict[str, str]] = []
    for label, url in pattern.findall(text):
        label = label.strip()
        url = url.strip()
        if url and label:
            result.append({"label": label, "url": url})
    return result


def label_for_url(url: str) -> str:
    host = urlparse(url).netloc.lower().replace("www.", "")
    if "github.com" in host:
        return "GitHub"
    if "discord.com" in host or "discord.gg" in host:
        return "Discord"
    if "itch.io" in host:
        return "itch.io"
    if "romhacking.net" in host:
        return "Romhacking.net"
    if "youtube.com" in host or "youtu.be" in host:
        return "YouTube"
    if "twitter.com" in host or "x.com" in host:
        return "Twitter"
    if "twitch.tv" in host:
        return "Twitch"
    if "gamebanana.com" in host:
        return "GameBanana"
    if "nexusmods.com" in host:
        return "Nexus Mods"
    if host:
        # Use first part of host as label, capitalized
        base = host.split(".")[0]
        # Handle co.uk etc - just take first
        return base.capitalize()
    return "Site"


def developer_links_for(document: dict[str, Any], download_url: str | None) -> list[dict[str, str]]:
    links: list[dict[str, str]] = []
    seen: set[str] = set()

    def add(label: str, url: str) -> None:
        url = url.strip()
        if not url or url in seen:
            return
        host = urlparse(url).netloc.lower()
        # Skip Hylian Modding itself - not a developer site
        if "hylianmodding.com" in host:
            return
        # Skip YouTube - those are videos, not developer sites
        if is_youtube_url(url):
            return
        # Skip empty or fragment-only
        if url.startswith("#"):
            return
        seen.add(url)
        clean_label = label.strip() if label and label.strip() else label_for_url(url)
        # Truncate very long labels
        if len(clean_label) > 40:
            clean_label = clean_label[:37] + "..."
        links.append({"label": clean_label, "url": url})

    # From download_link if it's a developer site (github, external page, not direct hylian patch)
    if download_url:
        host = urlparse(download_url).netloc.lower()
        is_github = "github.com" in host
        is_direct = direct_patch(download_url)
        is_hylian = "hylianmodding.com" in host
        # If it's github, always add (even if direct patch? github direct patches are still developer)
        # If it's external (not direct) and not hylian, add
        # If it's direct but not hylian (e.g., direct zip on github), the downloadTarget will be direct but we still want the repo as link
        # So for github direct, we add the repo page, not the direct file? But download_url is the direct file url for github direct? Actually for github direct, download_url is the file url, not repo. We should add the repo as well.
        # For now, add download_url if it's github or external non-hylian
        if is_github:
            # For github direct file urls, extract repo base
            # e.g., https://github.com/krm01/oot-indigo-mod/releases/download/... -> https://github.com/krm01/oot-indigo-mod
            m = re.match(r'(https?://github\.com/[^/]+/[^/]+)', download_url)
            if m:
                add(label_for_url(m.group(1)), m.group(1))
            else:
                add(label_for_url(download_url), download_url)
        elif not is_hylian and not is_direct:
            add(label_for_url(download_url), download_url)
        elif not is_hylian and is_direct:
            # Direct patch on non-hylian host (e.g., direct zip on some site) - still add host as developer link
            # But avoid adding direct file url as developer link if it's a file; instead add host
            # For now, add the url itself with file label
            add(label_for_url(download_url), download_url)

    # From description markdown links
    desc = document.get("description", "")
    if isinstance(desc, str) and desc.strip():
        for item in extract_markdown_links(desc):
            add(item["label"], item["url"])

    return links


def split_screenshots_and_videos(raw_screenshots: list[str], document: dict[str, Any]) -> tuple[list[str], list[str]]:
    screenshots: list[str] = []
    videos: list[str] = []
    # Explicit video fields first
    for v in video_urls(document):
        if v not in videos:
            videos.append(v)
    # Then check screenshots for youtube
    for url in raw_screenshots:
        if not url or not url.strip():
            continue
        url = url.strip()
        # Make absolute if needed
        if url.startswith("/"):
            url = absolute_url(url)
        if is_youtube_url(url):
            if url not in videos:
                videos.append(url)
        else:
            # Ensure absolute for hylian paths
            if url.startswith("/") or url.startswith("competitions/") or url.startswith("mods/"):
                url = absolute_url(url)
            if url not in screenshots:
                screenshots.append(url)
    return screenshots, videos


def base_rom_for(supported_games: list[str]) -> dict[str, Any]:
    game = supported_games[0].lower() if supported_games else ""
    if game in {"oot", "ocarina of time", "ocarina_of_time"}:
        return {
            "name": "Ocarina of Time",
            "gameCode": "CZLE",
            "versionByte": -1,
            "checksums": {"crc32": ""},
        }
    if game in {"mm", "majora's mask", "majoras mask", "majora_mask"}:
        return {
            "name": "Majora's Mask",
            "gameCode": "NSME",
            "versionByte": -1,
            "checksums": {"crc32": ""},
        }
    return {
        "name": "Unknown Zelda 64 base ROM",
        "gameCode": "UNKN",
        "versionByte": -1,
        "checksums": {"crc32": ""},
    }


def download_target(download_url: str) -> dict[str, Any]:
    lower_path = urlparse(download_url).path.lower()
    if direct_patch(download_url):
        return {
            "type": "direct",
            "patch": {
                "url": download_url,
                "filename": filename_from_url(download_url),
                "size": 0,
                "checksums": {"crc32": ""},
            },
        }
    if "github.com" in urlparse(download_url).netloc.lower():
        repository_url = download_url if "/releases" in lower_path else f"{download_url.rstrip('/')}/releases"
        return {"type": "github", "repoUrl": repository_url}
    return {"type": "external", "url": download_url}


def hylian_entry(source: Source, slug: str, document: dict[str, Any]) -> dict[str, Any]:
    raw_id = document.get("id")
    name = document.get("name")
    if not isinstance(raw_id, str) or not raw_id.strip() or not isinstance(name, str) or not name.strip():
        raise ImportError(f"{source.id}/{slug} has no usable id or name")

    authors = to_strings(document.get("authors"))
    games = to_strings(document.get("supported_games"))
    source_url = urljoin(source.index_url, f"{slug}/mod.json")
    thumbnails = to_strings(document.get("thumbnail_image"))
    raw_screenshots = to_strings(document.get("screenshots"))
    # Split screenshots and videos (youtube in screenshots -> videos)
    screenshot_urls, video_list = split_screenshots_and_videos(raw_screenshots, document)
    # Ensure absolute for thumbnails
    thumb_url = absolute_url(thumbnails[0]) if thumbnails else None
    if thumb_url and thumb_url.startswith("/"):
        thumb_url = absolute_url(thumb_url)

    download_link = document.get("download_link")
    download_url: str | None = None
    if isinstance(download_link, str) and download_link.strip():
        download_url = absolute_url(download_link)
        target = download_target(download_url)
        patch = copy.deepcopy(target["patch"]) if target["type"] == "direct" else None
    else:
        target = {"type": "external", "url": source_url}
        patch = None

    developer_links = developer_links_for(document, download_url)

    category = document.get("category")
    tags = [category.strip()] if isinstance(category, str) and category.strip() else []
    completion = document.get("completion_status")
    if isinstance(completion, str) and completion.strip() and completion.strip() not in tags:
        tags.append(completion.strip())

    changelog = document.get("changelog")
    if not isinstance(changelog, list):
        changelog = []

    entry: dict[str, Any] = {
        "id": raw_id.strip(),
        "name": name.strip(),
        "description": document.get("description", "") if isinstance(document.get("description", ""), str) else "",
        "author": ", ".join(authors),
        "version": str(document.get("last_updated") or "1.0"),
        "baseRom": base_rom_for(games),
        "patch": patch,
        "coverImageUrl": thumb_url,
        "tags": tags,
        "compatibleCores": ["mupen64plus_next_gles3", "parallel_n64"],
        "storeId": "picks",
        "sourceCatalogId": "picks",
        "screenshots": screenshot_urls,
        "videos": video_list,
        "developerLinks": developer_links,
        "completionStatus": completion.strip() if isinstance(completion, str) and completion.strip() else None,
        "supportedGames": ", ".join(games) or None,
        "compatibility": document.get("compatibility") if isinstance(document.get("compatibility"), str) else None,
        "lastUpdated": document.get("last_updated") if isinstance(document.get("last_updated"), str) else None,
        "changelog": [item for item in changelog if isinstance(item, dict)],
        "sourceMetadata": {
            "timestamp": document.get("timestamp"),
            "isUpdate": document.get("is_update"),
        },
        "downloadTarget": target,
        "importSource": {
            "provider": "Hylian Modding",
            "catalogId": source.id,
            "modUrl": source_url,
            "managed": True,
        },
    }
    return entry


def slugs_for(source: Source, timeout: int) -> list[str]:
    index = fetch_json(source.index_url, timeout)
    slugs = to_strings(index.get("mods"))
    if not slugs:
        raise ImportError(f"{source.index_url} has no mods")
    return slugs


def fetch_source(source: Source, timeout: int, workers: int) -> list[dict[str, Any]]:
    slugs = slugs_for(source, timeout)

    def fetch_mod(slug: str) -> dict[str, Any]:
        mod_url = urljoin(source.index_url, f"{slug}/mod.json")
        return hylian_entry(source, slug, fetch_json(mod_url, timeout))

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        futures = [executor.submit(fetch_mod, slug) for slug in slugs]
        return [future.result() for future in futures]


def is_legacy_hylian_entry(entry: dict[str, Any]) -> bool:
    source = entry.get("importSource")
    if (
        isinstance(source, dict)
        and source.get("provider") == "Hylian Modding"
        and source.get("managed", True) is not False
    ):
        return True
    patch = entry.get("patch")
    if not isinstance(patch, dict):
        return False
    url = patch.get("url")
    return isinstance(url, str) and urlparse(url).netloc.lower() == "hylianmodding.com"


def preserve_verified_patch(existing: dict[str, Any], replacement: dict[str, Any]) -> None:
    """Keep manual size/checksums when the source still points at the same file."""
    previous_patch = existing.get("patch")
    imported_patch = replacement.get("patch")
    if not isinstance(previous_patch, dict) or not isinstance(imported_patch, dict):
        return
    if previous_patch.get("url") != imported_patch.get("url"):
        return
    for key in ("size", "checksums"):
        if key in previous_patch:
            imported_patch[key] = copy.deepcopy(previous_patch[key])
    target = replacement.get("downloadTarget")
    if isinstance(target, dict) and target.get("type") == "direct":
        target["patch"] = copy.deepcopy(imported_patch)


def enrich_curated_entry(existing: dict[str, Any], imported: dict[str, Any]) -> dict[str, Any]:
    """Add upstream details without replacing a curated patch or base-ROM contract."""
    enriched = copy.deepcopy(existing)
    for field in (
        "author",
        "description",
        "coverImageUrl",
        "tags",
        "screenshots",
        "videos",
        "developerLinks",
        "completionStatus",
        "supportedGames",
        "compatibility",
        "lastUpdated",
        "changelog",
        "sourceMetadata",
    ):
        enriched[field] = copy.deepcopy(imported[field])
    origin = copy.deepcopy(imported["importSource"])
    origin["managed"] = False
    enriched["importSource"] = origin
    return enriched


def infer_developer_links(entry: dict[str, Any]) -> list[dict[str, str]]:
    """Infer developer links for curated entries that lack them."""
    existing = entry.get("developerLinks")
    if isinstance(existing, list) and len(existing) > 0:
        return existing
    # Check curated mapping first
    hack_id = entry.get("id", "")
    if hack_id in CURATED_DEVELOPER_LINKS:
        return copy.deepcopy(CURATED_DEVELOPER_LINKS[hack_id])
    links: list[dict[str, str]] = []
    seen: set[str] = set()
    def add(url: str, label: str | None = None) -> None:
        if not url or url in seen:
            return
        host = urlparse(url).netloc.lower()
        if "hylianmodding.com" in host:
            return
        if "zonaro/zelda64player" in url:
            return
        if is_youtube_url(url):
            return
        seen.add(url)
        links.append({"label": label or label_for_url(url), "url": url})
    # From downloadTarget
    dt = entry.get("downloadTarget")
    if isinstance(dt, dict):
        if dt.get("type") == "github" and isinstance(dt.get("repoUrl"), str):
            add(dt["repoUrl"])
        elif dt.get("type") == "external" and isinstance(dt.get("url"), str):
            add(dt["url"])
        elif dt.get("type") == "direct" and isinstance(dt.get("patch"), dict):
            patch_url = dt["patch"].get("url", "")
            if isinstance(patch_url, str) and "github.com" in patch_url:
                m = re.match(r'(https?://github\.com/[^/]+/[^/]+)', patch_url)
                if m:
                    add(m.group(1))
    # From patch url
    patch = entry.get("patch")
    if isinstance(patch, dict) and isinstance(patch.get("url"), str):
        url = patch["url"]
        if "github.com" in url and "zonaro/zelda64player" not in url:
            m = re.match(r'(https?://github\.com/[^/]+/[^/]+)', url)
            if m:
                add(m.group(1))
    # From description markdown
    desc = entry.get("description", "")
    if isinstance(desc, str):
        for item in extract_markdown_links(desc):
            host = urlparse(item["url"]).netloc.lower()
            if "hylianmodding.com" not in host and not is_youtube_url(item["url"]):
                add(item["url"], item["label"])
    return links


def merge_catalog(catalog: dict[str, Any], imported: list[dict[str, Any]]) -> tuple[dict[str, Any], int, int]:
    hacks = catalog.get("hacks")
    if not isinstance(hacks, list):
        raise ImportError("catalog has no hacks array")

    incoming = {entry["id"]: entry for entry in imported}
    retained: list[dict[str, Any]] = []
    updated = 0
    for item in hacks:
        if not isinstance(item, dict):
            retained.append(item)
            continue
        item_id = item.get("id")
        replacement = incoming.pop(item_id, None) if isinstance(item_id, str) else None
        if replacement is None:
            # No incoming update - ensure developerLinks and videos are backfilled
            # Fix youtube in screenshots -> videos for legacy entries
            if isinstance(item.get("screenshots"), list):
                raw = [str(x) for x in item["screenshots"] if isinstance(x, str)]
                # Also check if videos already has youtube, keep it
                existing_videos = item.get("videos") if isinstance(item.get("videos"), list) else []
                # Re-split
                new_screenshots: list[str] = []
                new_videos: list[str] = list(existing_videos)
                for url in raw:
                    if is_youtube_url(url):
                        if url not in new_videos:
                            new_videos.append(url)
                    else:
                        if url not in new_screenshots:
                            new_screenshots.append(url)
                item["screenshots"] = new_screenshots
                item["videos"] = new_videos
            # Backfill developerLinks
            if not isinstance(item.get("developerLinks"), list) or len(item.get("developerLinks", [])) == 0:
                inferred = infer_developer_links(item)
                if inferred:
                    item["developerLinks"] = inferred
                elif "developerLinks" not in item:
                    item["developerLinks"] = []
            retained.append(item)
            continue
        if is_legacy_hylian_entry(item):
            preserve_verified_patch(item, replacement)
            retained.append(replacement)
            updated += 1
        else:
            # A curated Main Store patch/base-ROM contract wins, while the
            # source contributes its richer description, galleries and history.
            retained.append(enrich_curated_entry(item, replacement))
            updated += 1

    additions = sorted(incoming.values(), key=lambda entry: (entry["name"].casefold(), entry["id"]))
    retained.extend(additions)
    # Ensure all retained entries have developerLinks and proper videos
    for entry in retained:
        if not isinstance(entry.get("developerLinks"), list):
            entry["developerLinks"] = infer_developer_links(entry)
        elif len(entry.get("developerLinks", [])) == 0:
            inferred = infer_developer_links(entry)
            if inferred:
                entry["developerLinks"] = inferred
        # Ensure videos/screenshots split for any remaining legacy
        if isinstance(entry.get("screenshots"), list):
            raw = [str(x) for x in entry["screenshots"] if isinstance(x, str)]
            vids = entry.get("videos") if isinstance(entry.get("videos"), list) else []
            new_screenshots: list[str] = []
            new_videos: list[str] = list(vids)
            changed = False
            for url in raw:
                if is_youtube_url(url):
                    if url not in new_videos:
                        new_videos.append(url)
                    changed = True
                else:
                    new_screenshots.append(url)
            if changed:
                entry["screenshots"] = new_screenshots
                entry["videos"] = new_videos

    result = copy.deepcopy(catalog)
    result["catalogVersion"] = max(int(result.get("catalogVersion", 1)), CATALOG_VERSION)
    result["storeName"] = "Main Store"
    result["lastUpdated"] = datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    result["hacks"] = retained
    return result, updated, len(additions)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=Path("catalog/catalog.json"), help="catalog to update")
    parser.add_argument("--dry-run", action="store_true", help="fetch and validate without writing")
    parser.add_argument("--timeout", type=int, default=30, help="per-request timeout in seconds")
    parser.add_argument("--workers", type=int, default=6, help="parallel mod requests per source")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.timeout <= 0 or args.workers <= 0:
        raise SystemExit("--timeout and --workers must be positive")
    try:
        catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
        if not isinstance(catalog, dict):
            raise ImportError(f"{args.catalog} is not a JSON object")

        imported: list[dict[str, Any]] = []
        for source in SOURCES:
            entries = fetch_source(source, args.timeout, args.workers)
            imported.extend(entries)
            print(f"{source.id}: {len(entries)} mods", file=sys.stderr)

        unique: dict[str, dict[str, Any]] = {}
        for entry in imported:
            if entry["id"] in unique:
                raise ImportError(f"duplicate Hylian Modding id: {entry['id']}")
            unique[entry["id"]] = entry
        merged, updated, added = merge_catalog(catalog, list(unique.values()))
    except (OSError, ImportError, json.JSONDecodeError) as error:
        print(f"Import failed: {error}", file=sys.stderr)
        return 1

    print(
        f"Hylian Modding: {len(unique)} mods; {updated} updated; {added} added; "
        f"catalog total: {len(merged['hacks'])}",
        file=sys.stderr,
    )
    if not args.dry_run:
        args.catalog.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
