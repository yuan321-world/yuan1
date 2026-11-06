import {businessGet,businessWrite} from './business'
import type {ArticleVersion,PageResult} from './admin-articles'
import type {Metadata,PracticeQuestionInput,ListeningData,AudioTicket} from './practice'
export interface PublicationCheck {path:string;questionKey:string|null;message:string}
export interface DraftInput {articleId:string;articleVersionId:string;examCode:string;readingType:string;metadata:Metadata;questions:PracticeQuestionInput[];expectedLockVersion:number;wordBank?:string[];listening?:ListeningData}
export interface AdminSet extends DraftInput {id:string;reviewedQuestionKeys:string[];sourceReviewed:boolean;status:'DRAFT'|'PUBLISHED'|'UNLISTED';revision:number;lockVersion:number;article:ArticleVersion;checks:PublicationCheck[];materialOnly?:boolean;subjective?:import('./practice-subjective').SubjectiveContent}
export interface PublishedVersion {revision:number;publishedAt:string;metadata:Metadata;article:ArticleVersion;questions:PracticeQuestionInput[];readingType?:string;wordBank?:string[];listening?:ListeningData;subjective?:import('./practice-subjective').SubjectiveContent}
export const adminPracticeSets=(query:URLSearchParams,signal?:AbortSignal)=>businessGet<PageResult<AdminSet>>(`/api/v1/admin/practice/sets?${query}`,signal)
export const adminPracticeSet=(id:string,signal?:AbortSignal)=>businessGet<AdminSet>(`/api/v1/admin/practice/sets/${encodeURIComponent(id)}`,signal)
export const createPracticeSet=(input:DraftInput)=>businessWrite<AdminSet>('/api/v1/admin/practice/sets',input)
export const savePracticeSet=(id:string,input:DraftInput)=>businessWrite<AdminSet>(`/api/v1/admin/practice/sets/${encodeURIComponent(id)}`,input,'PUT')
export const reviewPracticeSet=(id:string,expectedLockVersion:number,kind:'QUESTION'|'QUESTIONS'|'SOURCE'|'AUDIO'|'CONTENT',reviewed:boolean,questionKey?:string)=>businessWrite<AdminSet>(`/api/v1/admin/practice/sets/${encodeURIComponent(id)}/review`,{expectedLockVersion,kind,reviewed,...(questionKey?{questionKey}:{})})
export const practiceSetStatus=(id:string,expectedLockVersion:number,publish:boolean)=>businessWrite<AdminSet>(`/api/v1/admin/practice/sets/${encodeURIComponent(id)}/${publish?'publish':'unpublish'}`,{expectedLockVersion})
export const adminPracticeVersions=(id:string,page=1)=>businessGet<PageResult<PublishedVersion>>(`/api/v1/admin/practice/sets/${encodeURIComponent(id)}/versions?page=${page}`)
export const adminPracticeVersion=(id:string,revision:number)=>businessGet<PublishedVersion>(`/api/v1/admin/practice/sets/${encodeURIComponent(id)}/versions/${revision}`)
export const validatePracticeImport=(input:unknown)=>businessWrite<{valid:boolean;fieldErrors:Record<string,string>;itemCount:number}>('/api/v1/admin/practice/import/validate',input)
export const importPracticeDraft=(input:unknown)=>businessWrite<AdminSet>('/api/v1/admin/practice/import',input)

export interface MaterialInput {readingType?:string;requestId:string;body:string;metadata:Metadata;questions:PracticeQuestionInput[];expectedLockVersion:number;wordBank?:string[];listening?:ListeningData}
export const createPracticeMaterial=(input:MaterialInput)=>businessWrite<AdminSet>('/api/v1/admin/practice/material-sets',input)
export const savePracticeMaterial=(id:string,input:MaterialInput)=>businessWrite<AdminSet>(`/api/v1/admin/practice/sets/${encodeURIComponent(id)}/material`,input,'PUT')

export const adminAudioTicket=(id:string)=>businessWrite<AudioTicket>(`/api/v1/admin/practice/audio-assets/${encodeURIComponent(id)}/playback-ticket`)
export async function uploadPracticeAudio(file:File){
 const {authenticatedJson,csrfOptions}=await import('../auth/auth-transport');const {getGeneration}=await import('../auth/token-session');const generation=getGeneration();const options=await csrfOptions();const headers=new Headers(options.headers);headers.delete('Content-Type');const data=new FormData();data.append('file',file);const result=await authenticatedJson<{id:string;originalName:string;byteSize:number}>('/api/v1/admin/practice/audio-assets',{...options,headers,body:data},generation);if(generation!==getGeneration())throw new Error('账号已变化，请重新加载');return result
}
