"""Reproducible ECDICT CET4-tag reference; filesystem only, no downloads or DB access."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path

spec = importlib.util.spec_from_file_location("ecdict", Path(__file__).with_name("prepare-ecdict.py"))
ecdict = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ecdict)

def encoded(value):
    return (json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n").encode()

def build(source):
    data, _ = ecdict.build(source, tag="cet4", limit=None)
    data["dataVersion"] = "ecdict-cet4-bc015ed-v1"
    data["sourceNote"] = "ECDICT 四级标签参考词表（bc015ed；仓库MIT声明及混合上游来源），仅本地开发，非官方大纲。https://github.com/skywind3000/ECDICT"
    if len(data["entries"]) != 3846:
        raise ValueError("Pinned CET4 supported count changed")
    entries = encoded(data)
    manifest = {"schemaVersion": 1, "code": "CET4_REFERENCE", "dataVersion": data["dataVersion"], "title": "大学英语四级·参考词表", "sourceUrl": "https://github.com/skywind3000/ECDICT/tree/" + ecdict.REVISION, "notice": "ECDICT四级标签参考；仅本地开发，非官方大纲。仓库声明MIT，混合上游来源未逐词审核。", "originalCount": 3849, "availableCount": 3846, "missingPhoneticCount": sum(not e["phonetic"] for e in data["entries"]), "excluded": [{"word": w, "reason": "缩略词暂不符合当前学习格式"} for w in ["B.C.", "i.e.", "P.M."]], "entriesSha256": hashlib.sha256(entries).hexdigest()}
    return encoded(manifest), entries

if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("csv", type=Path); p.add_argument("output", type=Path); p.add_argument("--check", action="store_true")
    args = p.parse_args(); manifest, entries = build(args.csv)
    for name, value in [("manifest.json", manifest), ("entries.json", entries)]:
        target = args.output / name
        if args.check:
            if target.read_bytes() != value: raise ValueError("Generated reference is not reproducible: " + name)
        else:
            target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(value)
    print("reference=3849 supported=3846 excluded=3 missingPhonetic=10 entriesSha256=" + hashlib.sha256(entries).hexdigest())
