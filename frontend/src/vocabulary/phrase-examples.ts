import editorial from './phrase-examples.json'
import fixed from '../../../content/dictionary/fixed-examples/entries.json'
import corpus from './corpus-examples.json'
import type {DictionaryExample} from '@/api/dictionary'

export type PhraseExample = DictionaryExample & {
  attribution?: {englishId:string;englishAuthor:string;chineseId:string;chineseAuthor:string}
}
const cache=new Map<string,readonly PhraseExample[]>()
const normalize=(text:string)=>text.toLowerCase().replaceAll('’',"'").replace(/\s+/g,' ').trim()
const curated=editorial as Record<string,string[][]>
const fixedExamples=Object.values(fixed.entries).flat() as DictionaryExample[]
const corpusPairs=Object.values(corpus) as string[][]

/** Only literal, whole-token occurrences; never fabricates a sentence for an unseen phrase. */
export function phraseExamples(text:string):readonly PhraseExample[]{
  const key=normalize(text)
  if(key.length>80||!/^[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){1,9}$/.test(key))return []
  const cached=cache.get(key);if(cached)return cached
  const escaped=key.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')
  const matches=new RegExp(`(^|[^a-z'-])${escaped}(?=$|[^a-z'-])`)
  const result:PhraseExample[]=[],seen=new Set<string>()
  const add=(example:PhraseExample)=>{
    const sentence=normalize(example.text)
    if(result.length<2&&!seen.has(sentence)&&matches.test(sentence)){
      seen.add(sentence);result.push(example)
    }
  }
  for(const pair of curated[key]??[])add({text:pair[0]!,translationZh:pair[1]!})
  if(result.length<2)for(const pairs of Object.values(curated))for(const pair of pairs){add({text:pair[0]!,translationZh:pair[1]!});if(result.length===2)break}
  if(result.length<2)for(const example of fixedExamples){add(example);if(result.length===2)break}
  if(result.length<2)for(const pair of corpusPairs){
    add({text:pair[0]!,translationZh:pair[1]!,attribution:{englishId:pair[2]!,englishAuthor:pair[3]!,chineseId:pair[4]!,chineseAuthor:pair[5]!}})
    if(result.length===2)break
  }
  if(cache.size>=100)cache.delete(cache.keys().next().value!)
  cache.set(key,result)
  return result
}
