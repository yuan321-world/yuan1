import {it,expect} from 'vitest'
import {objectiveScore,paperScore} from '@/practice/scoring'
const component=(slot:string,correct=0,total=10)=>({slot,objective:{correctCount:correct,total},text:'',wordCount:0}) as any
it('scores objective exercises on a hundred-point scale including blank and empty results',()=>{expect(objectiveScore({correctCount:3,total:5})).toBe(60);expect(objectiveScore({correctCount:0,total:25})).toBe(0);expect(objectiveScore({total:0})).toBeNull()})
it('keeps paper totals pending until both subjective scores exist and counts blanks as zero',()=>{const a:any={components:[{slot:'WRITING',text:'Essay'},component('LISTENING',25,25),component('CLOZE',10),component('MATCH',10),component('DETAILED_1',5,5),component('DETAILED_2',5,5),{slot:'TRANSLATION',text:'Translation'}]};expect(paperScore(a,{})).toMatchObject({objective:70,total:null});expect(paperScore(a,{WRITING:12,TRANSLATION:10}).total).toBe(92);a.components[0].text='';a.components[6].text='';expect(paperScore(a,{}).total).toBe(70)})
it('does not invent scores for incomplete paper data',()=>{expect(paperScore({components:[component('MATCH',1)]} as any,{}).total).toBeNull()})
