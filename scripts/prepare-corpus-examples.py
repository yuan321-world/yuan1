"""Match attributed ManyThings/Tatoeba pairs to local dictionary headwords."""
import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ATTRIBUTION = re.compile(r"CC-BY 2\.0 \(France\) Attribution: tatoeba\.org #(\d+) \(([^()]+)\) & #(\d+) \(([^()]+)\)")
TOKEN = re.compile(r"[a-z]+(?:['’-][a-z]+)*")
SKIP = re.compile(r"\b(?:fuck\w*|shit\w*|rape\w*|porn\w*|suicid\w*|nigger\w*|bitch\w*|genitals?|nude|sex\w*)\b", re.I)


def read(relative):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def build(source):
    forms = {}
    for entries in (
        read("content/dictionary/entries.v1.json")["entries"],
        read("content/dictionary/ecdict-local/entries.json")["entries"],
        read("content/vocabulary/ecdict-exams-local/bundle.json")["dataset"]["entries"],
    ):
        for entry in entries:
            forms.setdefault(entry["lemma"], set()).update([entry["lemma"], *entry["forms"]])
    by_form = {}
    for lemma, variants in forms.items():
        for form in variants:
            by_form.setdefault(form, set()).add(lemma)
    chosen = {}
    with zipfile.ZipFile(source) as archive, archive.open("cmn.txt") as lines:
        for raw in lines:
            fields = raw.decode("utf-8").rstrip("\r\n").split("\t")
            if len(fields) != 3:
                continue
            english, chinese, attribution = fields
            match = ATTRIBUTION.fullmatch(attribution)
            tokens = TOKEN.findall(english.lower())
            if not match or not 4 <= len(tokens) <= 18 or len(english) > 130 or len(chinese) > 100:
                continue
            if not re.search(r"[\u3400-\u9fff]", chinese) or SKIP.search(english):
                continue
            en_id, en_author, zh_id, zh_author = match.groups()
            if not en_author.strip() or not zh_author.strip():
                continue
            for token in set(tokens):
                for lemma in by_form.get(token, ()):
                    # Prefer exact headwords, then short sentences with enough context.
                    score = (token != lemma, abs(len(tokens) - 8), len(english), int(en_id), int(zh_id))
                    if lemma not in chosen or score < chosen[lemma][0]:
                        chosen[lemma] = (score, [english, chinese, en_id, en_author, zh_id, zh_author])
    return {lemma: item[1] for lemma, item in sorted(chosen.items())}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("zip", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    payload = build(args.zip)
    raw = (json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
    output = ROOT / "frontend/src/vocabulary/corpus-examples.json"
    if args.check:
        if output.read_bytes() != raw:
            raise ValueError("Corpus examples differ from source")
    else:
        output.write_bytes(raw)
    print(f"source SHA256 {hashlib.sha256(args.zip.read_bytes()).hexdigest()}; {len(payload)} matched lemmas")
