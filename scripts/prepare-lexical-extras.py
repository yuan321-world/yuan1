"""Reproducible offline WordNet synsets + ECDICT multiword expressions. No network or database writes."""
import argparse
import csv
import hashlib
import json
import re
import tarfile
from pathlib import Path

WN_SHA = '658b1ba191f5f98c2e9bae3e25c186013158f30ef779f191d2a44e5d25046dc8'
WN_SOURCE_SHA = '640db279c949a88f61f851dd54ebbb22d003f8b90b85267042ef85a3781d3a52'
EC_SHA = '1a6947e04785db63613a92e14903cdae7954f7e84860b10e68e5c7cbb3f9c3cf'
POS = {'n':'n.', 'v':'v.', 'a':'adj.', 's':'adj.', 'r':'adv.'}
PREFIXES = set('at in on from to by for with without out as of be get go have take make come give bring put look turn keep set hold'.split())

def checked(path, expected):
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise ValueError(f'Pinned input hash mismatch: {path.name}')

def build(wordnet, ecdict, words):
    checked(wordnet, WN_SHA)
    checked(ecdict, EC_SHA)
    synonyms = {}
    with tarfile.open(wordnet) as archive:
        # Read only named members: never unpack untrusted archive paths.
        for suffix in ['noun','verb','adj','adv']:
            synsets = {}
            for line in archive.extractfile('dict/data.'+suffix).read().decode('utf-8').splitlines():
                if not re.match(r'^\d{8} ',line):
                    continue
                header, _, gloss = line.partition('|')
                fields = header.split()
                count = int(fields[3],16)
                names = [re.sub(r'\((?:a|p|ip)\)$','',fields[4+2*i]).replace('_',' ') for i in range(count)]
                meaning = gloss.strip().split('; "')[0]
                if not meaning or len(meaning)>400:
                    continue
                synsets[fields[0]] = (POS[fields[2]],names,meaning)
            for line in archive.extractfile('dict/index.'+suffix).read().decode('utf-8').splitlines():
                if line.startswith(' ') or not line:
                    continue
                fields = line.split()
                lemma = fields[0].replace('_',' ').lower()
                if lemma not in words:
                    continue
                for offset in fields[6+int(fields[3]):]:
                    if offset not in synsets:
                        continue
                    pos,names,meaning = synsets[offset]
                    related = list(dict.fromkeys(name for name in names if name.lower()!=lemma and len(name)<=64))[:6]
                    if not related:
                        continue
                    groups = synonyms.setdefault(lemma,[])
                    if sum(group['partOfSpeech']==pos for group in groups)>=4:
                        continue
                    groups.append({'partOfSpeech':pos,'meaningEn':meaning,'words':related})
    phrases = {}
    with ecdict.open(encoding='utf-8-sig',newline='') as stream:
        for row in csv.DictReader(stream):
            text = row['word'].strip()
            if not re.fullmatch(r"[A-Za-z]+(?:['-][A-Za-z]+)*(?: [A-Za-z]+(?:['-][A-Za-z]+)*){1,5}",text) or len(text)>100:
                continue
            translation = row['translation'].replace('\\n','；').strip()
            if not re.search(r'[\u3400-\u9fff]',translation) or len(translation)>250:
                continue
            # A learner phrase list must not be filled by specialist dictionary subentries.
            if re.match(r'^\[[^\]]+\]',translation):
                continue
            tokens=text.lower().split()
            bnc = int(row['bnc']) if row['bnc'].isdigit() and int(row['bnc'])>0 else 10**9
            for lemma in set(tokens)&words:
                rank = (0 if tokens[0]==lemma and tokens[-1] in PREFIXES else 1 if tokens[0] in PREFIXES else 2,bnc,len(tokens),text.lower())
                phrases.setdefault(lemma,[]).append((rank,{'text':text,'translationZh':translation}))
    entries = {}
    for lemma in sorted(words):
        unique = {}
        for rank, phrase in sorted(phrases.get(lemma,[]),key=lambda item:item[0]):
            unique.setdefault(phrase['text'].lower(),phrase)
            if len(unique)>=5:
                break
        groups=synonyms.get(lemma,[])
        if groups or unique:
            entries[lemma]={'synonyms':groups,'phrases':list(unique.values())}
    return {'schemaVersion':1,'dataVersion':'wordnet30-ecdict-bc015ed-v1','synonymSource':'WordNet 3.0, Copyright 2006 Princeton University; original license retained.','phraseSource':'ECDICT bc015ed; MIT repository declaration, mixed upstream sources; local development.','entries':entries}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('wordnet',type=Path);parser.add_argument('wordnet_source',type=Path);parser.add_argument('ecdict',type=Path);parser.add_argument('output',type=Path)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args();checked(args.wordnet_source,WN_SOURCE_SHA)
    root=Path(__file__).resolve().parents[1]
    words=set()
    for name in ['content/dictionary/entries.v1.json','content/dictionary/ecdict-local/entries.json']:
        words.update(entry['lemma'] for entry in json.loads((root/name).read_text(encoding='utf-8'))['entries'])
    data=build(args.wordnet,args.ecdict,words)
    encoded=(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n').encode()
    with tarfile.open(args.wordnet_source) as archive:
        license_bytes=archive.extractfile('WordNet-3.0/LICENSE').read()
    license_path=args.output.with_name('LICENSE-WordNet.txt')
    if args.check:
        if args.output.read_bytes()!=encoded or license_path.read_bytes()!=license_bytes:
            raise ValueError('Generated lexical data/license differs')
    else:
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_bytes(encoded);license_path.write_bytes(license_bytes)
    print(f"entries={len(data['entries'])}, groups={sum(len(e['synonyms']) for e in data['entries'].values())}, phrases={sum(len(e['phrases']) for e in data['entries'].values())}, sha256={hashlib.sha256(encoded).hexdigest()}")
