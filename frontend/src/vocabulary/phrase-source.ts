import type {PhraseSourceInput} from '@/api/vocabulary-phrases'
export function phraseSourceFromRange(range:Range|null,article:{id:string;articleId:string;content?:{paragraphs:{sentences:{key:string;text:string}[]}[]}},context?:{practiceAttemptId?:string;paperAttemptId?:string;slot?:string}):PhraseSourceInput|undefined {
  if(!range)return
  const start=range.startContainer.nodeType===Node.ELEMENT_NODE?range.startContainer as Element:range.startContainer.parentElement
  const end=range.endContainer.nodeType===Node.ELEMENT_NODE?range.endContainer as Element:range.endContainer.parentElement
  const sentence=start?.closest<HTMLElement>('[data-sentence]')
  if(!sentence&&context){
    const block=start?.closest('[data-practice-source]'),raw=range.toString().trim()
    if(!block||block!==end?.closest('[data-practice-source]')||!raw)return
    const matches=(article.content?.paragraphs.flatMap(p=>p.sentences)||[]).filter(s=>s.text.indexOf(raw)>=0&&s.text.indexOf(raw)===s.text.lastIndexOf(raw))
    if(matches.length!==1)return
    const match=matches[0]!,startOffset=match.text.indexOf(raw)
    return {articleId:article.articleId,articleVersionId:article.id,sentenceKey:match.key,startOffset,endOffset:startOffset+raw.length,...context}
  }
  if(!sentence||sentence!==end?.closest('[data-sentence]')||!sentence.dataset.sentence)return
  const prefix=range.cloneRange();prefix.selectNodeContents(sentence);prefix.setEnd(range.startContainer,range.startOffset)
  const raw=range.toString(),text=raw.trim(),startOffset=prefix.toString().length+(raw.length-raw.trimStart().length)
  if(!text)return
  return {articleId:article.articleId,articleVersionId:article.id,sentenceKey:sentence.dataset.sentence,startOffset,endOffset:startOffset+text.length,...context}
}
