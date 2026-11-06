"""Build a deterministic local-development subset; never downloads data or connects to MySQL."""
import argparse
import csv
import hashlib
import json
import re
from pathlib import Path

REVISION = "bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b"
CSV_SHA256 = "1a6947e04785db63613a92e14903cdae7954f7e84860b10e68e5c7cbb3f9c3cf"
TOKEN = re.compile(r"[a-z]+(?:['-][a-z]+)*\Z")

def build(csv_path, tag=None, limit=10000, ranked_only=False):
    if hashlib.sha256(csv_path.read_bytes()).hexdigest() != CSV_SHA256:
        raise ValueError("ECDICT input hash differs from the reviewed pinned revision")
    records = {}
    paradigms = {}
    with csv_path.open(encoding="utf-8-sig", newline="") as stream:
        for row in csv.DictReader(stream):
            frequencies = [int(row[k]) for k in ("bnc", "frq") if row[k].isdigit() and int(row[k]) > 0]
            if ranked_only and not frequencies:
                continue
            if tag and tag not in row["tag"].split():
                continue
            lemma = row["word"].strip().lower()
            if not TOKEN.fullmatch(lemma) or len(lemma) > 64 or not re.search(r"[\u3400-\u9fff]", row["translation"]):
                continue
            senses = []
            english = row["definition"].replace("\\n", "\n").strip()
            # Do not invent an English gloss or align unrelated Chinese/English sense arrays.
            english = english if len(english) <= 500 else ""
            for line in row["translation"].replace("\\n", "\n").splitlines():
                match = re.match(r"^\s*((?:(?:n|v|vt|vi|adj|adv|a|ad|pron|prep|conj|interj|num|art|aux)\.?\s*[,/&]?\s*)+)\s+(.+)$", line)
                pos, meaning = (match[1].strip(), match[2].strip()) if match else ("释义", line.strip())
                if meaning and len(meaning) <= 300 and len(pos) <= 32:
                    senses.append({"partOfSpeech": pos, "definitionZh": meaning, "definitionEn": english})
            if not senses:
                continue
            forms = {lemma}
            paradigm = {}
            for exchange in row["exchange"].split("/"):
                key, _, values = exchange.partition(":")
                if key not in {"p", "d", "i", "3", "r", "t", "s"}:
                    continue
                for form in values.split(","):
                    form = form.strip().lower()
                    if TOKEN.fullmatch(form) and len(form) <= 64:
                        forms.add(form)
                        category = {"p": "past", "d": "past", "i": "ing", "3": "third", "s": "plural", "r": "comparison", "t": "comparison"}[key]
                        paradigm.setdefault(category, set()).add(form)
            rank = int(row["bnc"]) if row["bnc"].isdigit() and int(row["bnc"]) > 0 else 10**9
            if ranked_only:
                rank = min(frequencies)
            if lemma not in records or rank < records[lemma][0]:
                records[lemma] = (rank, {"lemma": lemma, "phonetic": row["phonetic"][:128] or None, "forms": sorted(forms)[:64], "senses": senses[:16]})
                paradigms[lemma] = {key: sorted(values) for key, values in sorted(paradigm.items())}
    selected = sorted(records.values(), key=lambda item: (item[0], item[1]["lemma"]))[:limit]
    data = {"schemaVersion": 1, "dataVersion": "ecdict-bc015ed-bnc10k-v1", "sourceNote": "ECDICT bc015ed（仓库声明MIT；上游混合来源），本地开发离线子集。https://github.com/skywind3000/ECDICT", "entries": sorted((item[1] for item in selected), key=lambda item: item["lemma"])}
    return data, {entry["lemma"]: paradigms[entry["lemma"]] for entry in data["entries"] if paradigms[entry["lemma"]]}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data, paradigms = build(args.csv)
    encoded = (json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
    paradigm_bytes = (json.dumps(paradigms, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
    paradigm_path = args.output.with_name("paradigms.json")
    if args.check:
        if args.output.read_bytes() != encoded:
            raise ValueError("Generated subset is not reproducible")
        if paradigm_path.read_bytes() != paradigm_bytes:
            raise ValueError("Generated paradigms are not reproducible")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(encoded)
        paradigm_path.write_bytes(paradigm_bytes)
    print(f"entries={len(data['entries'])}, forms={sum(len(e['forms']) for e in data['entries'])}, sha256={hashlib.sha256(encoded).hexdigest()}")
