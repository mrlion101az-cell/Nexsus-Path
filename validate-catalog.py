#!/usr/bin/env python3
"""Offline check of NexusPath's built-in catalog.yml (and, optionally, a custom-catalog.yml).

  python3 tools/validate-catalog.py [catalog.yml] [--plugins DIR]

Checks, so a typo can never silently hide or show the wrong thing on a server:
  * every system id is the real name of a plugin (looked up in DIR/*/src/main/resources/plugin.yml and in
    DIR/NexusTutorial's lessons.yml; default DIR: the folder above this project)
  * every real Nexus plugin there is has a system (otherwise it would show up as "unlisted")
  * every icon is a real item (set MCITEMS to a minecraft-data items.json to check; skipped otherwise)
  * every style names only systems that exist and are not locked, with no repeats
  * names, blurbs and titles fit the menus; no more styles than the menu has slots (14)
Needs: pip install pyyaml
"""
import glob, json, os, sys
import yaml

here = os.path.dirname(os.path.abspath(__file__))
args = [a for a in sys.argv[1:] if not a.startswith("--")]
plugins_dir = os.path.join(here, "..", "..")
if "--plugins" in sys.argv:
    plugins_dir = sys.argv[sys.argv.index("--plugins") + 1]
    args = [a for a in args if a != plugins_dir]
path = args[0] if args else os.path.join(here, "..", "src", "main", "resources", "catalog.yml")
cat = yaml.safe_load(open(path, encoding="utf-8")) or {}
systems = cat.get("systems") or {}
styles = cat.get("styles") or {}
errors, notes = [], []

INFRA = {"NexusPath", "NexusTutorial"}
SYSTEM_KEYS = {"name", "icon", "blurb", "locked", "world", "events", "default"}
STYLE_KEYS = {"title", "icon", "blurb", "systems", "add-systems", "remove-systems"}
MAX_STYLES = 14

# real plugin names
real = set()
for f in glob.glob(os.path.join(plugins_dir, "*", "src", "main", "resources", "plugin.yml")):
    try:
        real.add(yaml.safe_load(open(f, encoding="utf-8"))["name"])
    except Exception:
        pass
# NexusTutorial has a lesson for each plugin that was ever built; its names count as real too
lessons = os.path.join(plugins_dir, "NexusTutorial", "src", "main", "resources", "lessons.yml")
if os.path.exists(lessons):
    for lid, l in (yaml.safe_load(open(lessons, encoding="utf-8")).get("lessons") or {}).items():
        if isinstance(l, dict) and l.get("plugin"):
            real.add(l["plugin"])
if not real:
    notes.append("no plugin.yml or lessons found: skipped the check against real plugin names")

items = None
mc = os.environ.get("MCITEMS", "/tmp/mcdata/node_modules/minecraft-data/minecraft-data/data/pc/1.21.4/items.json")
if os.path.exists(mc):
    items = {i["name"].upper() for i in json.load(open(mc, encoding="utf-8"))}
else:
    notes.append("MCITEMS not set / not found: skipped the icon check")

ids = {k.lower(): k for k in systems}
if len(ids) != len(systems):
    errors.append("two system ids differ only in case")
for sid, s in systems.items():
    if not isinstance(s, dict):
        errors.append(f"system {sid}: not a section")
        continue
    for k in s:
        if k not in SYSTEM_KEYS:
            errors.append(f"system {sid}: unknown key '{k}'")
    for k in ("name", "icon", "blurb"):
        if not s.get(k):
            errors.append(f"system {sid}: missing {k}")
    if len(str(s.get("name", ""))) > 32:
        errors.append(f"system {sid}: name longer than 32 characters")
    if len(str(s.get("blurb", ""))) > 110:
        errors.append(f"system {sid}: blurb longer than 110 characters")
    for k in ("locked", "world", "events", "default"):
        if k in s and not isinstance(s[k], bool):
            errors.append(f"system {sid}: {k} must be true or false")
    if s.get("locked") and s.get("default"):
        errors.append(f"system {sid}: locked and default together is meaningless")
    if items is not None and str(s.get("icon", "")).upper() not in items:
        errors.append(f"system {sid}: icon {s.get('icon')} is not an item")
    if real and sid not in real:
        errors.append(f"system {sid}: no plugin with that name among the sibling projects")
    if sid in INFRA:
        errors.append(f"system {sid}: NexusPath's own infrastructure must not be in the catalog")

if real:
    for r in sorted(real):
        if r.startswith("Nexus") and r not in INFRA and r not in systems:
            errors.append(f"plugin {r} exists but has no system in the catalog (it would appear as unlisted)")

named = {}
if len(styles) > MAX_STYLES:
    errors.append(f"{len(styles)} styles but the menu has room for {MAX_STYLES}")
for tid, t in styles.items():
    if not isinstance(t, dict):
        errors.append(f"style {tid}: not a section")
        continue
    for k in t:
        if k not in STYLE_KEYS:
            errors.append(f"style {tid}: unknown key '{k}'")
    for k in ("title", "icon", "blurb"):
        if not t.get(k):
            errors.append(f"style {tid}: missing {k}")
    if len(str(t.get("title", ""))) > 24:
        errors.append(f"style {tid}: title longer than 24 characters")
    if len(str(t.get("blurb", ""))) > 110:
        errors.append(f"style {tid}: blurb longer than 110 characters")
    if items is not None and str(t.get("icon", "")).upper() not in items:
        errors.append(f"style {tid}: icon {t.get('icon')} is not an item")
    lst = t.get("systems") or []
    if len(lst) < 2:
        errors.append(f"style {tid}: fewer than two systems")
    seen = set()
    for sid in lst:
        low = sid.lower()
        if low in seen:
            errors.append(f"style {tid}: names {sid} twice")
        seen.add(low)
        if low not in ids:
            errors.append(f"style {tid}: names {sid}, which is not a system")
        elif systems[ids[low]].get("locked"):
            errors.append(f"style {tid}: names {sid}, which is locked (naming it does nothing)")
        named.setdefault(low, []).append(tid)

free = [sid for sid, s in systems.items() if not s.get("locked") and not s.get("default") and sid.lower() not in named]
if free:
    notes.append("in no style (reachable only by hand in the systems menu): " + ", ".join(free))
locked = [sid for sid, s in systems.items() if s.get("locked")]
notes.append(f"{len(systems)} systems ({len(locked)} locked), {len(styles)} styles")
for n in notes:
    print("note:", n)
if errors:
    for e in errors:
        print("ERROR:", e)
    sys.exit(1)
print("catalog OK")
