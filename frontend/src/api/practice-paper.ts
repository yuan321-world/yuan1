import {businessGet,businessWrite,publicReadingGet} from './business'
import type {Attempt,AudioTicket} from './practice'
import type {SubjectivePrompt} from './practice-subjective'
import type {PageResult} from './admin-articles'
export const slots=['WRITING','LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2','TRANSLATION'] as const
export type Slot=typeof slots[number]
export const slotLabels:Record<string,string>={WRITING:'写作',LISTENING:'整套听力',CLOZE:'选词填空',MATCH:'长篇匹配',DETAILED_1:'仔细阅读 · 第一篇',DETAILED_2:'仔细阅读 · 第二篇',TRANSLATION:'翻译'}
export interface ComponentInput{slot:string;setVersionId:string}
export interface PaperInput{expectedLockVersion?:number;title:string;examCode:string;year:number;month:number;sourceNote:string;components:ComponentInput[]}
export interface PaperView extends PaperInput{id:string;paperVersionId:string|null;status:string;revision:number;lockVersion:number;sourceReviewed:boolean;componentDetails:{slot:string;setVersionId:string;title:string;type:string;questionCount:number}[];checks:string[];available:boolean;unavailableReason:string|null;currentAttemptId?:string|null}
export interface Candidate{setId:string;setVersionId:string;title:string;type:string;questionCount:number;revision:number;year:number;month:number;sourceNote:string}
export interface PaperCatalog{items:PaperView[];page:number;pageSize:number;total:number;years:{value:string;count:number}[];months:{value:string;count:number}[]}
export interface PaperResponse{slot:string;text:string;choices:Record<string,string>}
export interface ComponentAttempt{slot:string;type:string;title:string;objective?:Attempt;prompt?:SubjectivePrompt;text:string;wordCount:number;reference?:string;notes?:string}
export interface PaperAttempt{id:string;paperId:string;paperVersionId:string;title:string;sourceNote:string;status:'IN_PROGRESS'|'COMPLETED';lockVersion:number;currentSlot:string;currentQuestionId:string|null;responses:PaperResponse[];components:ComponentAttempt[];startedAt:string;completedAt:string|null}
export interface DraftInput{expectedLockVersion:number;currentSlot:string;currentQuestionId:string|null;responses:PaperResponse[]}
export interface PaperSubmitInput extends DraftInput{requestId:string;confirmUnanswered:boolean}
export interface PaperHistory{id:string;paperId:string;title:string;status:string;startedAt:string;completedAt:string|null}
export interface PaperMistake{questionId:string;setId:string;setVersionId:string;sourceAttemptId:string;slot:string;originalNumber:number;paperTitle:string;readingType:string;status:'PENDING'|'RESOLVED';wrongCount:number;stem:string;submittedChoice:string|null;correctOption:string;explanation:string}
const admin='/api/v1/admin/practice/papers',base='/api/v1/practice'
export const listPapers=(q:URLSearchParams,signal?:AbortSignal)=>publicReadingGet<PaperCatalog>(`${base}/papers?${q}`,signal)
export const getPaper=(id:string)=>publicReadingGet<PaperView>(`${base}/papers/${encodeURIComponent(id)}`)
export const startPaper=(id:string,paperVersionId:string,requestId:string)=>businessWrite<PaperAttempt>(`${base}/papers/${encodeURIComponent(id)}/attempts`,{paperVersionId,requestId})
export const getPaperAttempt=(id:string)=>businessGet<PaperAttempt>(`${base}/paper-attempts/${encodeURIComponent(id)}`)
export const savePaperDraft=(id:string,input:DraftInput)=>businessWrite<PaperAttempt>(`${base}/paper-attempts/${encodeURIComponent(id)}/draft`,input,'PUT')
export const submitPaper=(id:string,input:PaperSubmitInput)=>businessWrite<PaperAttempt>(`${base}/paper-attempts/${encodeURIComponent(id)}/submit`,input)
export const paperAudioTicket=(id:string)=>businessWrite<AudioTicket>(`${base}/paper-attempts/${encodeURIComponent(id)}/audio-ticket`)
export const listPaperHistory=(q:URLSearchParams,signal?:AbortSignal)=>businessGet<PageResult<PaperHistory>>(`${base}/paper-history?${q}`,signal)
export const paperMistakes=(q:URLSearchParams,signal?:AbortSignal)=>businessGet<PageResult<PaperMistake>>(`${base}/paper-mistakes?${q}`,signal)
export const retryPaperMistakes=(id:string,input:{requestId:string;slot:string})=>businessWrite<Attempt>(`${base}/paper-attempts/${encodeURIComponent(id)}/retry-mistakes`,input)
export const adminPapers=()=>businessGet<PaperView[]>(admin)
export const adminPaper=(id:string)=>businessGet<PaperView>(`${admin}/${encodeURIComponent(id)}`)
export const adminComponents=(q:URLSearchParams)=>businessGet<Candidate[]>(`${admin}/components?${q}`)
export const createPaper=(input:PaperInput)=>businessWrite<PaperView>(admin,input)
export const savePaper=(id:string,input:PaperInput)=>businessWrite<PaperView>(`${admin}/${encodeURIComponent(id)}`,input,'PUT')
export const paperAction=(id:string,action:'review'|'publish'|'unpublish',lock:number)=>businessWrite<PaperView>(`${admin}/${encodeURIComponent(id)}/${action}`,{expectedLockVersion:lock})
export const paperVersions=(id:string)=>businessGet<PaperView[]>(`${admin}/${encodeURIComponent(id)}/versions`)
