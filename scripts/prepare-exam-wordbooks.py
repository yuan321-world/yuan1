"""Generate eight local reference wordbooks from the pinned ECDICT CSV."""
import argparse
import csv
import hashlib
import importlib.util
import json
from pathlib import Path

spec = importlib.util.spec_from_file_location("ecdict", Path(__file__).with_name("prepare-ecdict.py"))
ecdict = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ecdict)

EXAMS = {"ky": "考研英语", "cet6": "大学英语六级", "cet4": "大学英语四级", "gk": "高考英语"}
VERSION = "ecdict-exam-bc015ed-v1"


def generate(source):
    ranks = {tag: {} for tag in EXAMS}
    with source.open(encoding="utf-8-sig", newline="") as stream:
        for row in csv.DictReader(stream):
            lemma = row["word"].strip().lower()
            rank = int(row["bnc"]) if row["bnc"].isdigit() and int(row["bnc"]) > 0 else None
            for tag in set(row["tag"].split()) & EXAMS.keys():
                prior = ranks[tag].get(lemma)
                if lemma not in ranks[tag] or rank is not None and (prior is None or rank < prior):
                    ranks[tag][lemma] = rank
    entries, books = {}, []
    for tag, title in EXAMS.items():
        data, _ = ecdict.build(source, tag=tag, limit=None)
        supported = {entry["lemma"]: entry for entry in data["entries"]}
        entries.update(supported)
        order = sorted(supported, key=lambda word: hashlib.sha256(f"{VERSION}:{tag}:{word}".encode()).hexdigest())
        core = sorted((word for word in supported if ranks[tag][word] is not None),
                      key=lambda word: (ranks[tag][word], word))[:1000]
        if len(core) != 1000:
            raise ValueError(f"Insufficient ranked words for {tag}")
        excluded = [{"word": word, "reason": "当前词条格式或释义不满足学习要求"}
                    for word in sorted(ranks[tag].keys() - supported.keys())]
        for variant, suffix, words, omitted in [
            ("SHUFFLE", "乱序版", order, excluded), ("CORE", "核心词汇版", core, [])
        ]:
            rule = "考试标签下的可学词，采用固定乱序。" if variant == "SHUFFLE" else "考试标签内按 BNC 通用语料词频选取前 1,000 词。"
            books.append({"code": f"{tag.upper()}_{variant}", "title": f"{title}·{suffix}",
                          "notice": rule + " ECDICT 本地备考参考，非官方考试大纲。",
                          "excluded": omitted, "words": words})
    return {"dataset": {"schemaVersion": 1, "dataVersion": VERSION,
                        "sourceNote": "ECDICT 本地考试标签参考 https://github.com/skywind3000/ECDICT",
                        "entries": [entries[word] for word in sorted(entries)]}, "books": books}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    payload = generate(args.csv)
    encoded = (json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
    if args.check:
        if args.output.read_bytes() != encoded:
            raise ValueError("Wordbook bundle is not reproducible")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(encoded)
    print(hashlib.sha256(encoded).hexdigest())
    for book in payload["books"]:
        print(book["code"], len(book["words"]), "excluded", len(book["excluded"]))
