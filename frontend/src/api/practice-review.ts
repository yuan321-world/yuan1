import {businessGet,businessWrite} from './business'
export interface ReviewSource {sourceType:'SUBJECTIVE'|'PAPER';attemptId:string;slot:'WRITING'|'TRANSLATION'}
export interface ReviewResult {score:number;summaryZh:string;summaryEn:string;dimensions:{name:string;level:'GOOD'|'FAIR'|'IMPROVE'}[];annotations:{kind:string;severity:'ERROR'|'SUGGESTION';quote:string;occurrence:number;replacement:string;reasonZh:string;reasonEn:string;sourceQuote?:string}[];omissions:{sourceQuote:string;reasonZh:string;reasonEn:string;suggestion:string}[];revisedText:string}
export interface Review extends ReviewSource {id:string;status:'QUEUED'|'RUNNING'|'SUCCEEDED'|'FAILED';errorCode:string|null;createdAt:string;completedAt:string|null;model:string;promptVersion:string;rubricVersion:string;submittedText:string;result:ReviewResult|null;budgetStatus?:'RESERVED'|'SETTLED'|'RELEASED'|'UNKNOWN';budgetAmountMicrocny?:number}
const base='/api/v1/practice/subjective-reviews'
export const history=(source:ReviewSource)=>businessGet<Review[]>(`${base}?${new URLSearchParams({...source})}`)
export const create=(source:ReviewSource,requestId:string)=>businessWrite<Review>(base,{...source,requestId,consentVersion:'2026-10-26'})
