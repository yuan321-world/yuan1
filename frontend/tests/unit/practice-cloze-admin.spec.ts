import {expect,it} from 'vitest'
import {blankMetadata,draftChecks,questionProblems} from '../../src/practice/admin-draft'
import type {PracticeQuestionInput} from '../../src/api/practice'
const questions=():PracticeQuestionInput[]=>Array.from({length:10},(_,i)=>({key:'gap'+(26+i),type:'CLOZE',stem:String(26+i),options:[],correctOption:String.fromCharCode(65+i),explanation:'Explanation',sortOrder:i}))
const metadata=()=>({...blankMetadata(),title:'Test only',titleZh:'测试专用',usageNote:'Test fixture'})
const bank=()=>Array.from({length:15},(_,i)=>'word'+i)
const body=()=>questions().map(q=>'A test sentence {{'+q.stem+'}}.').join('\n')
it('cloze gaps use shared bank rather than four repeated options',()=>{
 expect(questionProblems(questions()[0]!,true)).toEqual([])
 expect(draftChecks(metadata(),questions(),questions().map(q=>q.key),true,{readingType:'CLOZE',wordBank:bank(),body:body()})).toEqual([])
})
it('finds missing or duplicate markers, words and answers before publication',()=>{
 const qs=questions();qs[1]!.correctOption='A';const words=bank();words[1]=words[0]!
 const errors=draftChecks(metadata(),qs,qs.map(q=>q.key),true,{readingType:'CLOZE',wordBank:words,body:body().replace('{{27}}','{{26}}')})
 expect(errors.some(c=>c.path==='wordBank')).toBe(true)
 expect(errors.some(c=>c.path==='body')).toBe(true)
 expect(errors.some(c=>c.questionKey==='gap27')).toBe(true)
})
