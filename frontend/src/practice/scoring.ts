import type {PaperAttempt} from '@/api/practice-paper'
// Practice weights only: these are not official normalized examination scores.
export const paperWeights:Record<string,number>={WRITING:15,LISTENING:35,CLOZE:5,MATCH:10,DETAILED_1:10,DETAILED_2:10,TRANSLATION:15}
const round=(n:number)=>Math.round((n+Number.EPSILON)*100)/100
export function objectiveScore(a:{total:number;correctCount?:number}){return a.total>0&&Number.isFinite(a.correctCount)?round(Math.max(0,Math.min(a.total,a.correctCount!))/a.total*100):null}
export function paperScore(a:PaperAttempt,subjective:Partial<Record<string,number>>){
 const parts:Record<string,number|null>={}
 for(const c of a.components){const weight=paperWeights[c.slot];if(weight===undefined)continue
  const percentage=c.objective?objectiveScore(c.objective):null
  const raw=subjective[c.slot]
  parts[c.slot]=c.objective?(percentage===null?null:round(percentage/100*weight)):!c.text?.trim()?0:typeof raw==='number'&&Number.isFinite(raw)&&raw>=0&&raw<=15?round(raw/15*weight):null
 }
 const objectiveSlots=['LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2']
 const objective=objectiveSlots.every(s=>parts[s]!=null)?round(objectiveSlots.reduce((n,s)=>n+parts[s]!,0)):null
 const total=Object.keys(paperWeights).every(s=>parts[s]!=null)?round(Object.keys(paperWeights).reduce((n,s)=>n+parts[s]!,0)):null
 return {parts,objective,total}
}
