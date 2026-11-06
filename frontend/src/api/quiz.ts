import {businessGet,businessWrite,publicReadingGet} from './business'
import {csrfOptions,authenticatedJson} from '@/auth/auth-transport'
import {getGeneration,getAccessToken} from '@/auth/token-session'
import {ApiError,requestJson} from './client'
export interface QuizQuestionInput {key:string;type:string;stem:string;options:string[];correctOption:string;explanation:string;sortOrder:number}
export interface PublicQuestion {id:string;key:string;type:string;stem:string;options:string[]}
export interface PublicQuiz {id:string;versionId:string;articleId:string;articleVersionId:string;questions:PublicQuestion[]}
export interface AdminQuiz {id:string;articleId:string;articleVersionId:string;draft:QuizQuestionInput[];published:PublicQuiz|null;status:'DRAFT'|'PUBLISHED'|'UNLISTED';revision:number;lockVersion:number}
export interface QuizFeedback {questionId:string;choice:string;correct:boolean;correctOption:string;explanation:string;completed:boolean;correctCount:number;total:number}
const path=(a:string,v:string)=>`articles/${encodeURIComponent(a)}/versions/${encodeURIComponent(v)}/quiz`
export const publishedQuiz=(a:string,v:string,signal?:AbortSignal)=>publicReadingGet<PublicQuiz|null>(`/api/v1/reading/${path(a,v)}`,signal)
export const adminQuiz=(a:string,v:string,signal?:AbortSignal)=>businessGet<AdminQuiz|null>(`/api/v1/admin/${path(a,v)}`,signal)
export const saveQuiz=(a:string,v:string,questions:QuizQuestionInput[],expectedLockVersion:number)=>businessWrite<AdminQuiz>(`/api/v1/admin/${path(a,v)}`,{questions,expectedLockVersion},'PUT')
export const quizStatus=(id:string,publish:boolean,expectedLockVersion:number)=>businessWrite<AdminQuiz>(`/api/v1/admin/reading/quizzes/${encodeURIComponent(id)}/${publish?'publish':'unpublish'}`,{expectedLockVersion})
async function guestWrite<T>(url:string,body:unknown,signal?:AbortSignal){const identity=getGeneration(),options=await csrfOptions(body);if(signal?.aborted)throw new DOMException('Aborted','AbortError');if(identity!==getGeneration())throw new ApiError(409,{code:'ACCOUNT_CHANGED',message:'账号已变化，请重新开始体验'});const data=getAccessToken()?await authenticatedJson<T>(url,{...options,signal},identity):await requestJson<T>(url,{...options,signal});if(identity!==getGeneration())throw new ApiError(409,{code:'ACCOUNT_CHANGED',message:'账号已变化，请重新开始体验'});return data}
export const startGuestQuiz=(a:string,v:string,quizVersionId:string,signal?:AbortSignal)=>guestWrite<{token:string;expiresIn:number}>(`/api/v1/reading/${path(a,v)}/guest-sessions`,{quizVersionId},signal)
export const submitGuestQuiz=(token:string,questionId:string,choice:string,signal?:AbortSignal)=>guestWrite<QuizFeedback>('/api/v1/reading/quiz-guest/submit',{token,questionId,choice},signal)
