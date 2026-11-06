"""Reproduce opt-in search extensions from the pinned offline ECDICT source. No network or API."""
import argparse, csv, hashlib, importlib.util, json, re
from pathlib import Path

spec = importlib.util.spec_from_file_location('ecdict', Path(__file__).with_name('prepare-ecdict.py'))
ecdict = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ecdict)

def build(source):
    data, _ = ecdict.build(source, limit=50000, ranked_only=True)
    words = {entry['lemma'] for entry in data['entries']}
    phrases = {}; ranks = {}
    pattern = re.compile(r"[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){1,4}\Z")
    with source.open(encoding='utf-8-sig', newline='') as stream:
        for row in csv.DictReader(stream):
            text = row['word'].strip().lower()
            frequencies = [int(row[k]) for k in ('bnc','frq') if row[k].isdigit() and int(row[k]) > 0]
            if text in words and frequencies:ranks[text] = min(frequencies)
            meaning = row['translation'].replace('\\n', '；').strip()
            if len(text) <= 80 and pattern.fullmatch(text) and all(w in words for w in text.split()) and re.search(r'[\u3400-\u9fff]', meaning) and len(meaning) <= 250:
                phrases.setdefault(text, {'text': text, 'translationZh': meaning})
    featured = ['play a role','play a part','play a trick','play around','play against','play along','play at','apple pie','apple juice']
    examples = {text:phrases[text] for text in featured if text in phrases}
    phrases = {**examples, **dict(sorted(phrases.items(), key=lambda p:(max(ranks.get(w,10**9) for w in p[0].split()),sum(ranks.get(w,10**9) for w in p[0].split()),p[0]))[:30000])}
    # Original concise glosses for common expressions absent from the pinned source.
    for text, meaning in {'play a role':'发挥作用；扮演角色','play a trick':'开玩笑；捉弄','play against':'与……比赛；与……对抗'}.items():
        phrases[text] = {'text': text, 'translationZh': meaning}
    phrases = {**{text:phrases[text] for text in featured if text in phrases}, **phrases}
    output = {}
    for i in range(0, len(data['entries']), 10000):
        shard = {**data, 'dataVersion':f'ecdict-search-50k-v1-{i//10000}', 'entries':data['entries'][i:i+10000]}
        output[f'words-{i//10000}.json'] = shard
    output['phrases.json'] = {'schemaVersion':1,'dataVersion':'ecdict-search-phrases-v1','sourceSha256':ecdict.CSV_SHA256,'entries':list(phrases.values())}
    return output

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path);parser.add_argument('--check',action='store_true')
    args=parser.parse_args();root=Path(__file__).resolve().parents[1]/'content/dictionary/search-expanded'
    root.mkdir(parents=True,exist_ok=True)
    for name, payload in build(args.source).items():
        encoded=(json.dumps(payload,ensure_ascii=False,separators=(',',':'))+'\n').encode()
        if args.check:
            assert (root/name).read_bytes()==encoded, f'Non-reproducible {name}'
        else:(root/name).write_bytes(encoded)
        print(f'{name}: {len(payload["entries"])} entries; sha256={hashlib.sha256(encoded).hexdigest()}')
