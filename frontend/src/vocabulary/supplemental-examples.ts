import type {DictionaryExample} from '@/api/dictionary'
import fixed from '../../../content/dictionary/fixed-examples/entries.json'
import corpus from './corpus-examples.json'

type CorpusPair = [string, string, string, string, string, string]
export type SupplementalExamples = {
  examples: DictionaryExample[]
  attribution?: {englishId:string; englishAuthor:string; chineseId:string; chineseAuthor:string}
}

export function supplementalExamples(lemma: string): SupplementalExamples | undefined {
  const key = lemma.toLowerCase()
  const examples = (fixed.entries as Record<string, DictionaryExample[]>)[key]
  if (Array.isArray(examples) && examples.length) return {examples}
  const pair = (corpus as unknown as Record<string, CorpusPair>)[key]
  if (!Array.isArray(pair)) return undefined
  const [text, translationZh, englishId, englishAuthor, chineseId, chineseAuthor] = pair
  return {examples:[{text,translationZh}],attribution:{englishId,englishAuthor,chineseId,chineseAuthor}}
}
