import {businessGet,businessWrite} from './business'
import type {PageResult} from './admin-articles'
import type {Metadata} from './practice'
import type {AdminSet} from './admin-practice'
export interface SubjectivePrompt {prompt:string;requirements:string;minWords:number|null;maxWords:number|null;minutes:number|null}
export interface SubjectiveContent extends SubjectivePrompt {reference:string;notes:string}
export interface SubjectiveAttempt {id:string;setId:string;setVersionId:string;parentAttemptId?:string;title:string;readingType:'WRITING'|'TRANSLATION';prompt:SubjectivePrompt;answerText:string;wordCount:number;status:'IN_PROGRESS'|'COMPLETED';version:number;startedAt:string;updatedAt:string;completedAt:string|null;reference?:string;notes?:string}
export interface SubjectiveHistory {id:string;setId:string;title:string;readingType:string;status:string;wordCount:number;startedAt:string;completedAt:string|null}
export interface SubjectiveInput {readingType:string;metadata:Metadata;subjective:SubjectiveContent;expectedLockVersion:number}
export const isSubjective=(type:string)=>type==='WRITING'||type==='TRANSLATION'
export const englishWords=(text:string)=>(text.match(/[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*/g)||[]).length
export const createSubjective=(input:SubjectiveInput)=>businessWrite<AdminSet>('/api/v1/admin/practice/subjective-sets',input)
export const saveSubjective=(id:string,input:SubjectiveInput)=>businessWrite<AdminSet>(`/api/v1/admin/practice/sets/${encodeURIComponent(id)}/subjective`,input,'PUT')
export const startSubjective=(id:string,setVersionId:string,requestId:string)=>businessWrite<SubjectiveAttempt>(`/api/v1/practice/sets/${encodeURIComponent(id)}/subjective-attempts`,{setVersionId,requestId})
export const getSubjectiveAttempt=(id:string)=>businessGet<SubjectiveAttempt>(`/api/v1/practice/subjective-attempts/${encodeURIComponent(id)}`)
export const saveSubjectiveDraft=(id:string,input:{expectedVersion:number;answerText:string})=>businessWrite<SubjectiveAttempt>(`/api/v1/practice/subjective-attempts/${encodeURIComponent(id)}/draft`,input,'PUT')
export const submitSubjective=(id:string,input:{expectedVersion:number;requestId:string;confirmWordRange:boolean})=>businessWrite<SubjectiveAttempt>(`/api/v1/practice/subjective-attempts/${encodeURIComponent(id)}/submit`,input)
export const reviseSubjective=(id:string,requestId:string)=>businessWrite<SubjectiveAttempt>(`/api/v1/practice/subjective-attempts/${encodeURIComponent(id)}/revise`,{requestId})
export const subjectiveHistory=(query:URLSearchParams,signal?:AbortSignal)=>businessGet<PageResult<SubjectiveHistory>>(`/api/v1/practice/subjective-history?${query}`,signal)
