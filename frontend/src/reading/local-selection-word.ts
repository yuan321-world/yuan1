import {lookupWord,type LookupResult} from '@/api/dictionary'
/** Preserve complete entries, rather than reducing them to translation strings. */
export async function localSelectionWord(text:string,signal:AbortSignal):Promise<LookupResult|null>{
 if(!/^[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*$/.test(text)||text.length>60)return null
 const result=await lookupWord(text,signal)
 return result.found&&result.candidates.some(candidate=>candidate.senses.some(sense=>sense.definitionZh?.trim()))?result:null
}
