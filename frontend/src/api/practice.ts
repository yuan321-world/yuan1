import {businessGet,businessWrite,publicReadingGet} from './business'
import type {ArticleVersion,PageResult} from './admin-articles'
import type {PublicQuestion,QuizQuestionInput} from './quiz'
export type PracticeQuestionInput=Omit<QuizQuestionInput,'correctOption'|'type'>&{type:QuizQuestionInput['type']|'CLOZE'|'MATCH'|'LISTENING';correctOption:string|null}
export type PracticePublicQuestion=Omit<PublicQuestion,'type'>&{type:PublicQuestion['type']|'CLOZE'|'MATCH'|'LISTENING'}
export interface ListeningData {audioAssetId:string|null;groups:{key:string;title:string;questionKeys:string[]}[];audioReviewed?:boolean}
export interface PlaybackState {positionSeconds:number;rate:number}
export interface AudioTicket {url:string;expiresAt:string;sha256?:string}
export interface Metadata {title:string;titleZh:string;sourceKind:'ORIGINAL'|'PAST';topic:string;year:number|null;month:number|null;paperNo:number|null;sourceName:string;sourceUrl:string|null;usageNote:string}
export interface SetSummary {id:string;setVersionId:string|null;metadata:Metadata;examCode:string;readingType:string;questionCount:number;publishedAt:string|null;personalState?:string;currentAttemptId?:string}
export interface SetDetail extends SetSummary {article:ArticleVersion;wordBank?:string[];listening?:ListeningData;playback?:PlaybackState}
export interface SavedAnswer {questionId:string;flagged?:boolean;draftChoice?:string|null;submittedChoice?:string|null;correct?:boolean;correctOption?:string;explanation?:string}
export interface Attempt {reviewSource?:{attemptId:string;slot:string;originalNumbers:Record<string,number>};id:string;setId:string;setVersionId:string;quizVersionId:string;kind:'FULL'|'MISTAKES';sourceAttemptId:string|null;status:'IN_PROGRESS'|'COMPLETED';version:number;currentQuestionId:string;total:number;article:ArticleVersion;questions:PracticePublicQuestion[];answers:SavedAnswer[];correctCount?:number;startedAt:string;completedAt:string|null;title:string;readingType?:string;wordBank?:string[];listening?:ListeningData;playback?:PlaybackState}
export interface Mistake {questionId:string;setId:string;setVersionId:string;sourceAttemptId:string;latestAttemptId:string;status:'PENDING'|'RESOLVED';wrongCount:number;stem:string;correctOption:string;explanation:string;title:string;readingType?:string}
export interface Catalog {exams:{code:string;label:string;available:boolean;types:{code:string;label:string;available:boolean}[]}[]}
export interface Facets {source:{value:string;count:number}[];readingType:{value:string;count:number}[];topic:{value:string;count:number}[];state:{value:string;count:number}[];year:{value:string;count:number}[];month:{value:string;count:number}[];paperNo:{value:string;count:number}[]}
export const practiceCatalog=(signal?:AbortSignal)=>publicReadingGet<Catalog>('/api/v1/practice/catalog',signal)
export const practiceSets=(query:URLSearchParams,signal?:AbortSignal)=>publicReadingGet<PageResult<SetSummary>>(`/api/v1/practice/sets?${query}`,signal)
export const practiceFacets=(query:URLSearchParams,signal?:AbortSignal)=>publicReadingGet<Facets>(`/api/v1/practice/facets?${query}`,signal)
export const practiceSet=(id:string,signal?:AbortSignal)=>publicReadingGet<SetDetail>(`/api/v1/practice/sets/${encodeURIComponent(id)}`,signal)
export const startPractice=(id:string,setVersionId:string,requestId:string)=>businessWrite<Attempt>(`/api/v1/practice/sets/${encodeURIComponent(id)}/attempts`,{setVersionId,requestId})
export const getPracticeAttempt=(id:string,signal?:AbortSignal)=>businessGet<Attempt>(`/api/v1/practice/attempts/${encodeURIComponent(id)}`,signal)
export const savePracticeDraft=(id:string,body:{expectedVersion:number;answers:{questionId:string;choice:string|null;flagged?:boolean}[];currentQuestionId:string;playback?:PlaybackState})=>businessWrite<Attempt>(`/api/v1/practice/attempts/${encodeURIComponent(id)}/draft`,body,'PUT')
export const submitPractice=(id:string,body:{expectedVersion:number;requestId:string;confirmUnanswered:boolean})=>businessWrite<Attempt>(`/api/v1/practice/attempts/${encodeURIComponent(id)}/submit`,body)
export const practiceHistory=(query:URLSearchParams,signal?:AbortSignal)=>businessGet<PageResult<Attempt>>(`/api/v1/practice/history?${query}`,signal)
export const practiceMistakes=(query:URLSearchParams,signal?:AbortSignal)=>businessGet<PageResult<Mistake>>(`/api/v1/practice/mistakes?${query}`,signal)
export const retryPracticeMistakes=(id:string,requestId:string)=>businessWrite<Attempt>(`/api/v1/practice/attempts/${encodeURIComponent(id)}/retry-mistakes`,{requestId})

export const practiceAudioTicket=(id:string)=>businessWrite<AudioTicket>(`/api/v1/practice/attempts/${encodeURIComponent(id)}/audio-ticket`)
