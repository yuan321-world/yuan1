import {describe,it,expect} from 'vitest'
import {parseMatchBody} from '@/practice/match-material'
import {blankMatchQuestions,blankClozeQuestions,blankMetadata,draftChecks,questionProblems} from '@/practice/admin-draft'
describe('matching material and publication checks',()=>{
 it('does not admit matching questions into a cloze group',()=>{
  const questions=blankClozeQuestions().map(q=>({...q,type:'MATCH' as const,correctOption:'A',explanation:'Explanation'}))
  const checks=draftChecks({...blankMetadata(),title:'T',titleZh:'题',usageNote:'Test'},questions,questions.map(q=>q.key),true,{readingType:'CLOZE',wordBank:Array.from({length:15},(_,i)=>'word'+i),body:questions.map(q=>'Test {{'+q.stem+'}}').join(' ')})
  expect(checks).toContainEqual(expect.objectContaining({path:'questions'}))
 })
 it('requires continuous distinct paragraph labels and actual separators',()=>{
  expect(parseMatchBody('[A] One paragraph.\n\n[B] Another paragraph.').paragraphs.map(p=>p.letter)).toEqual(['A','B'])
  for(const body of ['[A]\nText missing space after label.','Unlabelled text.','[A] First.\n\n[C] Third.','[A] First.\n\n[A] Duplicate.','[A] First.\n[B] Missing separator.','[A]   '])expect(parseMatchBody(body).problem).toBeTruthy()
 })
 it('accepts repeated matching answers without four choice options',()=>{
  const questions=blankMatchQuestions().map(q=>({...q,stem:'A complete statement.',correctOption:'A',explanation:'Paragraph A supports this statement.'}))
  expect(questionProblems(questions[0]!,true)).toEqual([])
  const metadata={...blankMetadata(),title:'Matching practice',titleZh:'匹配练习',usageNote:'Original test fixture'}
  const context={readingType:'MATCH',wordBank:[],body:'[A] A paragraph.\n\n[B] Another paragraph.'}
  expect(draftChecks(metadata,questions,questions.map(q=>q.key),true,context)).toEqual([])
  questions[0]!.correctOption='C'
  expect(draftChecks(metadata,questions,questions.map(q=>q.key),true,context)).toContainEqual(expect.objectContaining({questionKey:questions[0]!.key,path:`questions[${questions[0]!.key}].correctOption`}))
  expect(draftChecks(metadata,questions,[],false,{...context,body:'[A] First.\n[B] Second.'})).toContainEqual(expect.objectContaining({path:'body'}))
 })
})
