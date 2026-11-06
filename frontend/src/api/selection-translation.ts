import {businessGet,businessWrite} from './business'
export type SelectionMode='TRANSLATION'|'GRAMMAR'
export interface GrammarItem{quote:string;role?:string;explanation:string}
export interface GrammarAnalysis{scopeNote:string;mainClause:GrammarItem[];clauses:GrammarItem[];clauseNote:string;grammarPoints:GrammarItem[]}
export interface SelectionTranslationTask{sourceCharacters?:number;mode?:SelectionMode;analysis?:GrammarAnalysis|null;id:string;text:string;status:'QUEUED'|'RUNNING'|'SUCCEEDED'|'FAILED';translation:string|null;model:string;errorCode:string|null;budgetStatus:'RESERVED'|'SETTLED'|'UNKNOWN'|'RELEASED';estimatedCostMicrocny:number}
export interface PracticeSelectionContext{practiceAttemptId?:string;paperAttemptId?:string;slot?:string}
export interface SelectionTranslationInput extends PracticeSelectionContext{requestId:string;articleId:string;versionId:string;text:string;mode?:SelectionMode}
export const createTranslation=(input:SelectionTranslationInput,signal?:AbortSignal)=>businessWrite<SelectionTranslationTask>('/api/v1/reading/selection-translations',input,'POST',signal)
export const translationTask=(id:string,signal?:AbortSignal)=>businessGet<SelectionTranslationTask>(`/api/v1/reading/selection-translations/${encodeURIComponent(id)}`,signal)
export const translationRequest=(id:string,signal?:AbortSignal)=>businessGet<SelectionTranslationTask>(`/api/v1/reading/selection-translations/requests/${encodeURIComponent(id)}`,signal)

export const selectionReference=(input:Omit<SelectionTranslationInput,'requestId'>,signal?:AbortSignal)=>businessWrite<{text:string|null;source:string|null}>('/api/v1/reading/selection-translations/reference',input,'POST',signal)
