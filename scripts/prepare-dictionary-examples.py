"""Build reviewed, original fixed bilingual examples; no network, AI calls or database writes."""
import argparse,hashlib,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
TOKEN=re.compile(r"[a-z]+(?:['’‐‑-][a-z]+)*")
def build(source=None):
 forms={}
 for name in ['entries.v1.json','ecdict-local/entries.json']:
  for e in json.loads((ROOT/'content/dictionary'/name).read_text(encoding='utf-8'))['entries']:
   forms.setdefault(e['lemma'],set()).update(e['forms']+[e['lemma']])
 for e in json.loads((ROOT/'content/vocabulary/ecdict-exams-local/bundle.json').read_text(encoding='utf-8'))['dataset']['entries']:
  forms.setdefault(e['lemma'],set()).update(e['forms']+[e['lemma']])
 pairs=[]
 source=source or ROOT/'content/dictionary/fixed-examples/source.tsv'
 for line in source.read_text(encoding='utf-8-sig').splitlines():
  en,zh,targets=line.split('\t')
  if not en or len(en)>300 or not re.search('[\u3400-\u9fff]',zh) or len(zh)>300 or not en.endswith(('.', '?','!')):raise ValueError('Invalid example')
  tokens=set(TOKEN.findall(en.lower())); lemmas=targets.split()
  if len(lemmas)!=len(set(lemmas)):raise ValueError('Duplicate lemma target')
  for lemma in lemmas:
   if lemma not in forms or not tokens&forms[lemma]:raise ValueError('Target must be an existing lemma with an explicit known form: '+lemma)
  pairs.append((en,zh,set(lemmas)))
 if len({p[0] for p in pairs})!=len(pairs):raise ValueError('Duplicate example')
 entries={}
 for lemma,variants in sorted(forms.items()):
  matches=[p for p in pairs if lemma in p[2]]
  if matches:entries[lemma]=[{'text':en,'translationZh':zh} for en,zh,_ in matches[:2]]
 return {'schemaVersion':1,'dataVersion':'editorial-fixed-examples-20261104-v2','sourceNote':'Lingua Loop原创双语学习例句（AI辅助编写，待人工语言校对）；非外部权威词典例句。','sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'entries':entries},len(pairs)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args();data,count=build();output=ROOT/'content/dictionary/fixed-examples/entries.json';raw=(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n').encode()
 if args.check:
  if output.read_bytes()!=raw:raise ValueError('Examples differ from fixed source')
 else:output.write_bytes(raw)
 print(f"sentences={count}, covered_lemmas={len(data['entries'])}, example_links={sum(map(len,data['entries'].values()))}")
