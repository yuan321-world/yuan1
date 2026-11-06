import {describe,it,expect} from 'vitest'
import {phraseSourceFromRange} from '@/vocabulary/phrase-source'
describe('phrase source captures exact original offsets',()=>{
 it('retains trim offsets across highlight wrappers and rejects cross-sentence guessing',()=>{
  document.body.innerHTML='<span data-sentence="s1">I <mark>look</mark> forward to you.</span><span data-sentence="s2">Hello.</span>'
  const one=document.querySelector('[data-sentence="s1"]')!,mark=one.querySelector('mark')!,tail=mark.nextSibling!
  const r=document.createRange();r.setStart(mark.firstChild!,0);r.setEnd(tail,12)
  expect(phraseSourceFromRange(r,{articleId:'1',id:'2'})).toEqual({articleId:'1',articleVersionId:'2',sentenceKey:'s1',startOffset:2,endOffset:17})
  r.setEnd(document.querySelector('[data-sentence="s2"]')!.firstChild!,5)
  expect(phraseSourceFromRange(r,{articleId:'1',id:'2'})).toBeUndefined()
 })
 it('only links an unambiguous practice sentence and keeps private attempt context',()=>{
  document.body.innerHTML='<p data-practice-source>We make progress every day.</p>'
  const text=document.querySelector('p')!.firstChild!,r=document.createRange();r.setStart(text,3);r.setEnd(text,16)
  const sentence={key:'p1-s1',text:'We make progress every day.'},article={articleId:'4',id:'5',content:{paragraphs:[{sentences:[sentence]}]}}
  expect(phraseSourceFromRange(r,article,{practiceAttemptId:'9'})).toMatchObject({sentenceKey:'p1-s1',startOffset:3,endOffset:16,practiceAttemptId:'9'})
  article.content.paragraphs.push({sentences:[sentence]})
  expect(phraseSourceFromRange(r,article,{practiceAttemptId:'9'})).toBeUndefined()
 })
})
