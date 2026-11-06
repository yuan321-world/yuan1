import { publicReadingGet } from './business'
export interface DictionarySense { partOfSpeech: string; definitionZh: string; definitionEn: string }
export interface SynonymGroup { partOfSpeech: string; meaningEn: string; words: string[] }
export interface DictionaryPhrase { text: string; translationZh: string }
export interface DictionaryExample { text: string; translationZh: string }
export interface DictionaryCandidate { entryId: string; lemma: string; phonetic: string | null; senses: DictionarySense[]; sourceNote: string; collection: { wordId: string; active: boolean } | null; synonyms?: SynonymGroup[]; phrases?: DictionaryPhrase[]; lexicalSourceNote?: string; phoneticSourceNote?: string | null; examples?: DictionaryExample[]; exampleSourceNote?: string }
export interface PhraseLookupResult { found: boolean; phrase: DictionaryPhrase | null; sourceNote: string }
export const lookupPhrase = (text: string, signal?: AbortSignal) => publicReadingGet<PhraseLookupResult>(`/api/v1/dictionary/phrase?text=${encodeURIComponent(text)}`, signal)
export interface LookupResult { found: boolean; candidates: DictionaryCandidate[] }
export const lookupWord = (word: string, signal?: AbortSignal) => publicReadingGet<LookupResult>(`/api/v1/dictionary/lookup?word=${encodeURIComponent(word)}`, signal)

export interface DictionarySearchItem { entryId: string; lemma: string; partOfSpeech: string; definitionZh: string; kind?: 'WORD'|'FORM'|'PHRASE'; canonicalLemma?: string; relatedEntryIds?: string[] }
export const searchDictionary = (q: string, signal?: AbortSignal) => publicReadingGet<{items:DictionarySearchItem[]}>(`/api/v1/dictionary/search?q=${encodeURIComponent(q)}`, signal)
