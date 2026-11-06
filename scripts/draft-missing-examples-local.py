"""Draft bilingual examples with a local-only llama.cpp server and a resumable checkpoint."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import re
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
TOKEN = re.compile(r"[a-z]+(?:['’‐‑-][a-z]+)*")


def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkpoint", type=Path)
    parser.add_argument("--port", type=int, default=8421)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--workers", type=int, default=3)
    args = parser.parse_args()
    bundle = read("content/vocabulary/ecdict-exams-local/bundle.json")
    legacy = read("content/vocabulary/ecdict-cet4-local/entries.json")["entries"]
    entries = {e["lemma"]: e for e in legacy + bundle["dataset"]["entries"]}
    targets = set().union(*(book["words"] for book in bundle["books"]), (e["lemma"] for e in legacy))
    fixed = read("content/dictionary/fixed-examples/entries.json")["entries"]
    corpus = read("frontend/src/vocabulary/corpus-examples.json")
    checkpoint = args.checkpoint
    accepted = {}
    if checkpoint.exists():
        for line in checkpoint.read_text(encoding="utf-8").splitlines():
            word, english, chinese = json.loads(line)
            accepted[word] = (english, chinese)
    remaining = sorted(targets - fixed.keys() - corpus.keys() - accepted.keys())
    if args.limit is not None:
        remaining = remaining[:args.limit]
    checkpoint.parent.mkdir(parents=True, exist_ok=True)

    def request(words):
        prompt = "\n".join(word + "\t" + "; ".join(
            sense["definitionZh"] for sense in entries[word]["senses"][:2])[:120] for word in words)
        data = {
            "model": "local", "temperature": 0.25, "max_tokens": max(256, len(words) * 100),
            "messages": [
                {"role":"system","content": "Write short original examples for an English learner's dictionary. For every supplied headword, write one natural complete English sentence using the headword or a normal inflection in one supplied meaning, and one faithful simplified Chinese translation. Avoid quoting the dictionary or defining the word. Use safe, neutral situations. Output exactly one line per requested word: headword | English sentence. | 中文译文。 No numbering or code blocks."},
                {"role":"user","content":prompt},
            ],
        }
        req = Request(f"http://127.0.0.1:{args.port}/v1/chat/completions",
                      data=json.dumps(data).encode(), headers={"Content-Type":"application/json"})
        with urlopen(req, timeout=240) as response:
            result = json.load(response)["choices"][0]["message"]["content"]
        found = {}
        for line in result.splitlines():
            parts = [part.strip() for part in line.split("|")]
            if len(parts) != 3:
                continue
            word, english, chinese = parts
            if word not in words or word in found or not english.endswith((".", "?", "!")):
                continue
            tokens = TOKEN.findall(english.lower())
            forms = {*entries[word]["forms"], word}
            if not (4 <= len(tokens) <= 24 and len(english) <= 180 and len(chinese) <= 120
                    and set(tokens) & forms and re.search(r"[\u3400-\u9fff]", chinese)
                    and not re.search(r"[\t\u3400-\u9fff]", english) and "\t" not in chinese):
                continue
            found[word] = (english, chinese)
        return found

    with checkpoint.open("a", encoding="utf-8") as output, ThreadPoolExecutor(args.workers) as executor:
        for batch_size in (12, 5, 1):
            batches = [remaining[i:i + batch_size] for i in range(0, len(remaining), batch_size)]
            remaining = []
            futures = {executor.submit(request, words):words for words in batches}
            for index, future in enumerate(as_completed(futures), start=1):
                words = futures[future]
                try:
                    found = future.result()
                except Exception as error:
                    print(f"Batch failed: {type(error).__name__}", flush=True)
                    found = {}
                remaining.extend(word for word in words if word not in found)
                for word, (english, chinese) in found.items():
                    output.write(json.dumps([word, english, chinese], ensure_ascii=False) + "\n")
                    accepted[word] = (english, chinese)
                output.flush()
                if index == 1 or index % 10 == 0 or index == len(futures):
                    print(f"Batch {index}/{len(futures)}, checkpoint {len(accepted)} words", flush=True)
    print(f"Unresolved in this run: {len(remaining)}", flush=True)
    if remaining:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
