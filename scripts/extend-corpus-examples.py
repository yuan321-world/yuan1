"""Extend attributed examples with official Tatoeba detailed sentences and direct links."""
import argparse
import bz2
import importlib.util
import json
import re
import tarfile
from collections import defaultdict
from pathlib import Path

spec = importlib.util.spec_from_file_location("corpus", Path(__file__).with_name("prepare-corpus-examples.py"))
corpus = importlib.util.module_from_spec(spec)
spec.loader.exec_module(corpus)


def archive_lines(path):
    with tarfile.open(path, "r|bz2") as archive:
        member = archive.next()
        if not member or not member.isfile():
            raise ValueError(f"Expected a sentence data file: {path}")
        with archive.extractfile(member) as stream:
            for line in stream:
                yield line.decode("utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_zip", type=Path)
    parser.add_argument("mandarin", type=Path)
    parser.add_argument("links", type=Path)
    parser.add_argument("detailed", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    bundle = corpus.read("content/vocabulary/ecdict-exams-local/bundle.json")
    legacy = corpus.read("content/vocabulary/ecdict-cet4-local/entries.json")["entries"]
    entries = {entry["lemma"]:entry for entry in legacy + bundle["dataset"]["entries"]}
    examples = corpus.build(args.base_zip)
    missing = entries.keys() - examples.keys()
    by_form = defaultdict(set)
    for lemma in missing:
        for form in {lemma, *entries[lemma]["forms"]}:
            by_form[form].add(lemma)
    mandarin = {}
    with bz2.open(args.mandarin, "rt", encoding="utf-8") as lines:
        for line in lines:
            sentence_id, _, text = line.rstrip("\r\n").split("\t", 2)
            if 3 <= len(text) <= 100 and re.search(r"[\u3400-\u9fff]", text):
                mandarin[int(sentence_id)] = text
    print(f"Chinese sentences: {len(mandarin)}", flush=True)
    targets = defaultdict(set)
    for line in archive_lines(args.links):
        left, right = map(int, line.split("\t"))
        if right in mandarin and left not in mandarin:
            targets[left].add(right)
    print(f"Direct linked sentence IDs: {len(targets)}", flush=True)
    candidates = defaultdict(list)
    chinese_authors = {}
    for line in archive_lines(args.detailed):
        fields = line.rstrip("\r\n").split("\t")
        if len(fields) < 4:
            continue
        raw_id, language, text, author = fields[:4]
        if language not in {"eng", "cmn"} or not author.strip() or author == "\\N":
            continue
        sentence_id = int(raw_id)
        if language == "cmn":
            if sentence_id in mandarin and text == mandarin[sentence_id]:
                chinese_authors[sentence_id] = author
            continue
        if sentence_id not in targets or len(text) > 130 or corpus.SKIP.search(text):
            continue
        tokens = corpus.TOKEN.findall(text.lower())
        if not 4 <= len(tokens) <= 18 or not text.endswith((".", "?", "!")):
            continue
        matches = {lemma for token in tokens for lemma in by_form.get(token, ())}
        for lemma in matches:
            score = (lemma not in tokens, abs(len(tokens)-8), len(text), sentence_id)
            row = (score, sentence_id, text, author)
            kept = candidates[lemma]
            kept.append(row)
            kept.sort()
            if len(kept) > 12:
                kept.pop()
    for lemma, rows in candidates.items():
        for _, en_id, text, en_author in rows:
            linked = targets[en_id] & chinese_authors.keys()
            if linked:
                zh_id = min(linked)
                examples[lemma] = [text, mandarin[zh_id], str(en_id), en_author, str(zh_id), chinese_authors[zh_id]]
                break
    args.output.write_bytes((json.dumps(dict(sorted(examples.items())), ensure_ascii=False,
                                       separators=(",", ":")) + "\n").encode("utf-8"))
    print(f"Added {len(examples.keys() & missing)} attributed exam headwords", flush=True)


if __name__ == "__main__":
    main()
