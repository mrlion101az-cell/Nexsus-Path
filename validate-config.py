#!/usr/bin/env python3
"""Offline check of NexusPath's config.yml and plugin.yml against what the code actually uses.

  python3 tools/validate-config.py

Fails if config.yml lacks a key PathConfig reads (so a typo can never silently fall back), has a key
nothing reads, has a value that differs from the built-in default, or a message uses a placeholder that
message is never given; and if the code checks a permission or command that plugin.yml does not declare.
Needs: pip install pyyaml
"""
import os, re, sys
import yaml

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.join(here, "..")
res = os.path.join(root, "src", "main", "resources")
code = os.path.join(root, "src", "main", "java", "com", "nexuscraft", "nexuspath")
cfg_src = open(os.path.join(code, "PathConfig.java"), encoding="utf-8").read()
cfg = yaml.safe_load(open(os.path.join(res, "config.yml"), encoding="utf-8"))
plug = yaml.safe_load(open(os.path.join(res, "plugin.yml"), encoding="utf-8"))
errors = []

def flat(d, prefix=""):
    out = {}
    for k, v in d.items():
        p = prefix + str(k)
        if isinstance(v, dict):
            out.update(flat(v, p + "."))
        else:
            out[p] = v
    return out

have = flat(cfg)
reads = re.findall(r'c\.get(String|Int|Boolean)\("([a-z\-\.]+)",\s*("[^"]*"|\w+)', cfg_src)
read_keys = {k for _, k, _ in reads} | set(re.findall(r'c\.getStringList\("([a-z\-\.]+)"\)', cfg_src))
for k in sorted(read_keys):
    if k not in have:
        errors.append(f"code reads '{k}' but config.yml does not define it")
for k in sorted(have):
    if k not in read_keys:
        errors.append(f"config.yml defines '{k}' but the code never reads it")

decl = {}
for m in re.finditer(r'^\s{4}(?:String|int|boolean)\s+(\w+)\s*=\s*([^;]+);', cfg_src, re.M):
    decl[m.group(1)] = m.group(2).strip()
def lit(v):
    if v in ("true", "false"):
        return v == "true"
    if v.startswith('"') and v.endswith('"'):
        return v[1:-1]
    return int(v) if re.fullmatch(r'-?\d+', v) else None
for kind, key, field in reads:
    if key not in have or (field not in decl and not field.startswith('"')):
        continue
    default = lit(field) if field.startswith('"') else lit(decl[field])
    if default is None:
        continue
    if have[key] != default:
        errors.append(f"'{key}': config.yml says {have[key]!r} but the built-in default is {default!r}")

# clamps in code must keep the shipped values
for key, lo, hi in re.findall(r'clamp\(c\.getInt\("([a-z\-\.]+)",\s*\w+\),\s*(\d+),\s*(\d+)\)', cfg_src):
    v = have.get(key)
    if v is None or not (int(lo) <= v <= int(hi)):
        errors.append(f"'{key}': {v!r} is outside the range {lo}..{hi} the code allows")

# message placeholders
ALLOWED = {
    "messages.prefix": set(),
    "messages.invite": {"minutes"},
    "messages.saved": {"styles", "shown", "hidden", "minutes"},
    "messages.everything": {"minutes"},
    "messages.blocked": {"system", "id", "minutes"},
    "messages.try-start": {"system", "id", "minutes"},
    "messages.try-end": {"system", "id", "minutes"},
    "messages.try-no": {"why", "minutes"},
    "messages.reset": {"minutes"},
}
for key, ok in ALLOWED.items():
    if key not in have:
        errors.append(f"{key} missing from config.yml")
        continue
    for ph in re.findall(r"\{(\w+)\}", str(have[key])):
        if ph not in ok:
            errors.append(f"{key}: placeholder {{{ph}}} is never filled in for this message")
for k in have:
    if k.startswith("messages.") and k not in ALLOWED:
        errors.append(f"{k}: no placeholder rule for this message (add it to the validator)")

# permissions and commands
declared = set(plug.get("permissions", {}))
used = set()
for fn in os.listdir(code):
    src = open(os.path.join(code, fn), encoding="utf-8").read()
    used |= set(re.findall(r'"(nexuspath\.[a-z\.]+)"', src))
for p in sorted(used):
    if p.startswith("nexuspath.hidden."):
        continue  # the exported per-plugin nodes are made up at run time
    if p not in declared:
        errors.append(f"code uses permission {p} but plugin.yml does not declare it")
for cmd in (plug.get("commands") or {}).values():
    if cmd.get("permission"):
        used.add(cmd["permission"])
        if cmd["permission"] not in declared:
            errors.append(f"command permission {cmd['permission']} is not declared")
for p in sorted(declared):
    if p not in used:
        errors.append(f"plugin.yml declares {p} but the code never checks it")
if "path" not in plug.get("commands", {}):
    errors.append("plugin.yml has no 'path' command")
if plug.get("main") != "com.nexuscraft.nexuspath.PathPlugin":
    errors.append("plugin.yml main class is wrong")
if "NexusTutorial" not in (plug.get("softdepend") or []):
    errors.append("plugin.yml should softdepend on NexusTutorial")
# a gate that wraps handlers must run after every other plugin has loaded: make sure we load last-ish
if plug.get("load") not in (None, "POSTWORLD"):
    errors.append("plugin.yml load should stay POSTWORLD")

if errors:
    for e in errors:
        print("ERROR:", e)
    sys.exit(1)
print("config OK")
