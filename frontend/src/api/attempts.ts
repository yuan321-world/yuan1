import {businessGet,businessWrite} from './business'
import type {PublicQuiz} from './quiz'
import type {PageResult} from './admin-articles'
export interface AttemptSummary {id:string;quizId:string;quizVersionId:string;articleId:string;articleVersionId:string;attemptNo:number;status:'IN_PROGRESS'|'COMPLETED';correctCount:number|null;total:number;startedAt:string;completedAt:string|null}
export interface SavedAnswer {questionId:string;draftChoice?:string;submittedChoice?:string;correct?:boolean;correctOption?:string;explanation?:string;submittedAt?:string}
export interface ReadingAttempt extends AttemptSummary {lockVersion:number;quiz:PublicQuiz;answers:SavedAnswer[]}
export interface PracticeState {published:PublicQuiz|null;current:ReadingAttempt|null;first:AttemptSummary|null;latest:AttemptSummary|null}
const articlePath=(a:string,v:string)=>`/api/v1/reading/articles/${encodeURIComponent(a)}/versions/${encodeURIComponent(v)}/practice`
export const practiceState=(a:string,v:string,signal?:AbortSignal)=>businessGet<PracticeState>(articlePath(a,v),signal)
export const practiceHistory=(a:string,v:string,page:number,signal?:AbortSignal)=>businessGet<PageResult<AttemptSummary>>(articlePath(a,v)+`/history?page=${page}&pageSize=6`,signal)
export const readingAttempt=(id:string,signal?:AbortSignal)=>businessGet<ReadingAttempt>(`/api/v1/reading/attempts/${encodeURIComponent(id)}`,signal)
export const startAttempt=(quizId:string,quizVersionId:string,clientRequestId:string,signal?:AbortSignal,isCurrent?:()=>boolean)=>businessWrite<ReadingAttempt>(`/api/v1/reading/quizzes/${encodeURIComponent(quizId)}/attempts`,{quizVersionId,clientRequestId},'POST',signal,isCurrent)
export const saveAttemptChoice=(id:string,questionId:string,choice:string|null,expectedLockVersion:number,signal?:AbortSignal,isCurrent?:()=>boolean)=>businessWrite<ReadingAttempt>(`/api/v1/reading/attempts/${encodeURIComponent(id)}/answers/${encodeURIComponent(questionId)}`,{choice,expectedLockVersion},'PUT',signal,isCurrent)
export const submitAttemptChoice=(id:string,questionId:string,choice:string,expectedLockVersion:number,signal?:AbortSignal,isCurrent?:()=>boolean)=>businessWrite<ReadingAttempt>(`/api/v1/reading/attempts/${encodeURIComponent(id)}/answers/${encodeURIComponent(questionId)}/submit`,{choice,expectedLockVersion},'POST',signal,isCurrent)
