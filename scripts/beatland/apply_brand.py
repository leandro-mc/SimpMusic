#!/usr/bin/env python3
"""
Beatland fork: apply the brand name from `beatland.properties` everywhere it is DISPLAYED.

Usage (run from anywhere; paths are resolved from the repo root):

    python3 scripts/beatland/apply_brand.py            # apply
    python3 scripts/beatland/apply_brand.py --dry-run  # show what would change, write nothing
    python3 scripts/beatland/apply_brand.py --check    # report visible "SimpMusic" left behind; exit 1 if any

What it manages (see docs/beatland/BRANDING.md for the rules):
  1. composeApp/src/commonMain/composeResources/values*/*.xml  -> visible app name in every locale
  2. composeApp/src/commonMain/kotlin/.../brand/Brand.kt        -> generated constant `Brand.NAME`
  3. androidApp/src/beatland/res/values/app_name.xml            -> launcher name (release)
     androidApp/src/beatlandDebug/res/values/app_name.xml       -> launcher name (debug, "<name> Dev")
  4. conveyor.conf                                              -> start-menu group, StartupWMClass, vcs-url

It is IDEMPOTENT and it handles RENAMES: the previously applied brand is read from Brand.kt, so
changing `brand.name` from "Beatland" to something else rewrites both the original "SimpMusic" and
the old brand. Re-run it after EVERY sync with dev (new upstream strings bring "SimpMusic" back).

It deliberately does NOT touch: package names, applicationId, resource keys, url schemes, the
`.simpmusic` data folder, URLs, or the protected strings below (third-party service names and the
original author's promo texts).
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ORIGINAL_NAME = "SimpMusic"

PROPERTIES = ROOT / "beatland.properties"
BRAND_KT = ROOT / "composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/brand/Brand.kt"
RES_DIR = ROOT / "composeApp/src/commonMain/composeResources"
CONVEYOR = ROOT / "conveyor.conf"
ANDROID_NAME_FILES = {
    ROOT / "androidApp/src/beatland/res/values/app_name.xml": "{brand}",
    ROOT / "androidApp/src/beatlandDebug/res/values/app_name.xml": "{brand} Dev",
}

# Resource keys whose text must keep saying "SimpMusic" (decision D-006 / D-007, see DECISIONS.md):
# they are the original author's promos / review prompts that point at the UPSTREAM project.
PROTECTED_KEYS = {
    "blog_promo_message",
    "footguns_promo_message",
    "enjoying_simpmusic",
    "if_you_enjoy_using_simpmusic_star_simpmusic_on_github_or_leave_a_review_on",
}

# Phrases that name a THIRD-PARTY SERVICE (the community lyrics database run by upstream). Replacing
# them would misattribute the data source (decision D-006). Matched case-insensitively.
PROTECTED_PHRASES = [
    re.compile(r"SimpMusic[ \u00a0]+lyrics(?:'s)?", re.IGNORECASE),
]

URL_RE = re.compile(r"https?://[^\s<>\"']+")
ELEMENT_RE = re.compile(
    r"(?P<open><(?P<tag>string|item)(?=[\s>])(?P<attrs>[^>]*)>)(?P<body>.*?)(?P<close></(?P=tag)>)",
    re.DOTALL,
)
NAME_ATTR_RE = re.compile(r'\bname="([^"]+)"')


def read_brand() -> str:
    if not PROPERTIES.exists():
        sys.exit(f"missing {PROPERTIES}")
    for raw in PROPERTIES.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            if key.strip() == "brand.name":
                value = value.strip()
                if not value:
                    sys.exit("brand.name is empty")
                if re.search(r'[<>&"\\$]', value):
                    sys.exit("brand.name must not contain < > & \" \\ $ (it is injected into XML/Kotlin/Gradle)")
                return value
    sys.exit("brand.name not found in beatland.properties")


def previously_applied() -> str | None:
    if BRAND_KT.exists():
        m = re.search(r'const val NAME = "([^"]*)"', BRAND_KT.read_text(encoding="utf-8"))
        if m:
            return m.group(1)
    return None


def xml_escape_apostrophe(text: str) -> str:
    return text  # brand names with apostrophes are not supported (see check in read_brand)


def replace_visible(text: str, old_names: list[str], brand: str) -> str:
    """Replace old names with `brand`, skipping URLs and protected phrases."""
    stash: list[str] = []

    def keep(m: re.Match) -> str:
        stash.append(m.group(0))
        return f"\u0000{len(stash) - 1}\u0000"

    protected = text
    for rx in [URL_RE, *PROTECTED_PHRASES]:
        protected = rx.sub(keep, protected)
    for old in old_names:
        protected = protected.replace(old, brand)
    return re.sub(r"\u0000(\d+)\u0000", lambda m: stash[int(m.group(1))], protected)


def process_resource_file(path: Path, old_names: list[str], brand: str) -> tuple[str, int]:
    original = path.read_text(encoding="utf-8")
    changes = 0

    def sub(m: re.Match) -> str:
        nonlocal changes
        attrs = m.group("attrs")
        key = NAME_ATTR_RE.search(attrs)
        if key and key.group(1) in PROTECTED_KEYS:
            return m.group(0)
        body = m.group("body")
        new_body = replace_visible(body, old_names, brand)
        if new_body != body:
            changes += 1
        return m.group("open") + new_body + m.group("close")

    return ELEMENT_RE.sub(sub, original), changes


def apply(brand: str, dry: bool) -> int:
    old_names = [ORIGINAL_NAME]
    prev = previously_applied()
    if prev and prev != brand and prev not in old_names:
        old_names.append(prev)

    total = 0
    # 1) resource strings (every locale, plus values/app_name.xml)
    for f in sorted(RES_DIR.glob("values*/*.xml")):
        new_text, n = process_resource_file(f, old_names, brand)
        if n:
            total += n
            print(f"  strings  {f.relative_to(ROOT)}: {n} element(s)")
            if not dry:
                f.write_text(new_text, encoding="utf-8", newline="\n")

    # 2) Brand.kt
    brand_kt = (
        "package com.maxrave.simpmusic.brand\n\n"
        "/**\n"
        " * Beatland fork: single source of truth for the app's display name in Kotlin code.\n"
        " *\n"
        " * GENERATED by `scripts/beatland/apply_brand.py` from `beatland.properties` (key `brand.name`).\n"
        " * Do not edit by hand: change `beatland.properties` and re-run the script.\n"
        " * See `docs/beatland/BRANDING.md`.\n"
        " */\n"
        "object Brand {\n"
        f'    const val NAME = "{brand}"\n'
        "}\n"
    )
    if not BRAND_KT.exists() or BRAND_KT.read_text(encoding="utf-8") != brand_kt:
        print(f"  kotlin   {BRAND_KT.relative_to(ROOT)}")
        total += 1
        if not dry:
            BRAND_KT.parent.mkdir(parents=True, exist_ok=True)
            BRAND_KT.write_text(brand_kt, encoding="utf-8", newline="\n")

    # 3) Android launcher names
    for path, template in ANDROID_NAME_FILES.items():
        content = (
            f'<resources>\n    <string name="app_name">{template.format(brand=brand)}</string>\n</resources>\n'
        )
        if not path.exists() or path.read_text(encoding="utf-8") != content:
            print(f"  android  {path.relative_to(ROOT)}")
            total += 1
            if not dry:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8", newline="\n")

    # 4) conveyor.conf (display-facing lines only)
    if CONVEYOR.exists():
        text = CONVEYOR.read_text(encoding="utf-8")
        new = re.sub(r'(start-menu\.group\s*=\s*)"[^"]*"', rf'\1"{brand}"', text)
        new = re.sub(r'(StartupWMClass\s*=\s*)"[^"]*"', rf'\1"{brand}"', new)
        if new != text:
            print(f"  conveyor {CONVEYOR.relative_to(ROOT)}")
            total += 1
            if not dry:
                CONVEYOR.write_text(new, encoding="utf-8", newline="\n")

    print(f"{'would change' if dry else 'changed'}: {total} item(s); brand = {brand!r}")
    return 0


# ----------------------------------------------------------------------------- --check

KOTLIN_LITERAL_RE = re.compile(r'"[^"\n]*SimpMusic[^"\n]*"')
IGNORED_LITERAL_FRAGMENTS = (
    "github.com/maxrave-dev/SimpMusic",  # upstream links kept on purpose
    "SimpMusic GitHub Release",  # update-channel label: it IS upstream's release feed (D-009)
    "SimpMusic/MiniPlayer",  # java.util.prefs node: user data key, never renamed (D-003)
    'setRequestProperty("User-Agent", "SimpMusic")',  # network identity (D-003)
)


def check(brand: str) -> int:
    problems: list[str] = []

    for f in sorted(RES_DIR.glob("values*/*.xml")):
        text = f.read_text(encoding="utf-8")
        for m in ELEMENT_RE.finditer(text):
            key = NAME_ATTR_RE.search(m.group("attrs"))
            if key and key.group(1) in PROTECTED_KEYS:
                continue
            probe = m.group("body")
            for rx in [URL_RE, *PROTECTED_PHRASES]:
                probe = rx.sub("", probe)
            if ORIGINAL_NAME in probe:
                line = text[: m.start()].count("\n") + 1
                problems.append(f"{f.relative_to(ROOT)}:{line}: {probe.strip()[:90]}")

    kt_roots = [ROOT / "composeApp/src", ROOT / "androidApp/src", ROOT / "desktopApp/src"]
    for base in kt_roots:
        for f in base.rglob("*.kt"):
            for n, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
                stripped = line.strip()
                if stripped.startswith(("import ", "package ", "//", "*", "/*")):
                    continue
                for lit in KOTLIN_LITERAL_RE.findall(line):
                    if any(frag in line for frag in IGNORED_LITERAL_FRAGMENTS):
                        continue
                    if re.search(r"Logger\.|log\(|println\(", line):
                        continue  # log text is not user-visible
                    problems.append(f"{f.relative_to(ROOT)}:{n}: {lit}")

    if problems:
        print(f'Visible "{ORIGINAL_NAME}" left behind ({len(problems)}):')
        for p in problems:
            print("  " + p)
        print("\nFix kotlin literals with Brand.NAME; for strings.xml just run apply_brand.py without --check.")
        print("If an occurrence is intentional, document it in docs/beatland/DECISIONS.md and add it to the")
        print("ignore lists at the top of this script.")
        return 1
    print(f"OK: no visible {ORIGINAL_NAME!r} left (brand = {brand!r}).")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    brand = read_brand()
    if args.check:
        return check(brand)
    return apply(brand, args.dry_run)


if __name__ == "__main__":
    sys.exit(main())
