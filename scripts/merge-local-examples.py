"""Audit all installed reference books and publish validated local drafts."""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import tempfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("fixed_examples", Path(__file__).with_name("prepare-dictionary-examples.py"))
fixed_examples = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixed_examples)


def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkpoint", type=Path)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    directory = ROOT / "content/dictionary/fixed-examples"
    source = directory / "source.tsv"
    bundle = read("content/vocabulary/ecdict-exams-local/bundle.json")
    books = bundle["books"]
    legacy = read("content/vocabulary/ecdict-cet4-local/entries.json")["entries"]
    books.append({"code":"CET4_REFERENCE","words":[entry["lemma"] for entry in legacy]})
    targets = set().union(*(book["words"] for book in books))
    corpus = read("frontend/src/vocabulary/corpus-examples.json")
    forms = {entry["lemma"]: {entry["lemma"], *entry["forms"]}
             for entry in legacy + bundle["dataset"]["entries"]}
    for word in targets & corpus.keys():
        english, chinese, en_id, en_author, zh_id, zh_author = corpus[word]
        if (not forms[word].intersection(fixed_examples.TOKEN.findall(english.lower()))
                or not re.search(r"[\u3400-\u9fff]", chinese)
                or not (en_id.isdecimal() and zh_id.isdecimal() and en_author.strip() and zh_author.strip())):
            raise ValueError(f"Invalid attributed example: {word}")
    existing = read("content/dictionary/fixed-examples/entries.json")["entries"]
    rows = {english: (chinese, set(words.split())) for english, chinese, words in
            (line.split("\t") for line in source.read_text(encoding="utf-8").splitlines())}
    drafts = {}
    for line in args.checkpoint.read_text(encoding="utf-8").splitlines():
        word, english, chinese = json.loads(line)
        if word in targets and word not in existing and word not in corpus:
            drafts[word] = (english, chinese)
    for word, (english, chinese) in sorted(drafts.items()):
        if english not in rows:
            rows[english] = (chinese, set())
        rows[english][1].add(word)
    body = "".join(f"{english}\t{chinese}\t{' '.join(sorted(words))}\n"
                   for english, (chinese, words) in rows.items())
    with tempfile.TemporaryDirectory(dir=args.checkpoint.parent) as directory_name:
        candidate = Path(directory_name) / "source.tsv"
        candidate.write_bytes(body.encode("utf-8"))
        payload, _ = fixed_examples.build(candidate)
    covered = set(payload["entries"]) | corpus.keys()
    missing = targets - covered
    for book in books:
        words = set(book["words"])
        print(f"{book['code']}: {len(words & covered)}/{len(words)}")
    print(f"Total: {len(targets & covered)}/{len(targets)}; missing {len(missing)}")
    if args.report:
        args.report.write_text("".join(word + "\n" for word in sorted(missing)), encoding="utf-8")
    if args.apply:
        if missing:
            raise SystemExit("Publication requires full wordbook coverage")
        source.write_bytes(body.encode("utf-8"))
        (directory / "entries.json").write_bytes(
            (json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8"))


if __name__ == "__main__":
    main()
