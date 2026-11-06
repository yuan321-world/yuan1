import { businessGet, businessWrite, publicReadingGet } from './business'
export interface WritingContent { expression:string;meaningZh:string;keyPhrases:string[];structure:string;exampleEn:string;exampleZh:string;scenarios:string[];sortOrder:number }
export interface PublishedWriting { id:string;articleId:string;articleVersionId:string;content:WritingContent;revision:number }
export interface AdminWriting { id:string;articleId:string;articleVersionId:string;draft:WritingContent;published:WritingContent|null;status:'DRAFT'|'PUBLISHED'|'UNLISTED';revision:number;lockVersion:number }
export interface SavedImitation { text:string;materialRevision:number;snapshot:WritingContent;lockVersion:number }
export interface PersonalWriting { materialId:string;articleId:string;articleVersionId:string;expression:string;available:boolean;favorite:boolean;material:PublishedWriting|null;imitation:SavedImitation|null }
export const writingPersonal=(id:string,signal?:AbortSignal)=>businessGet<PersonalWriting>(`/api/v1/writing-materials/${encodeURIComponent(id)}/personal`,signal)
export const writingLibrary=(view:string,page:number,signal?:AbortSignal)=>businessGet<import('./admin-articles').PageResult<PersonalWriting>>(`/api/v1/account/writing-materials?view=${encodeURIComponent(view)}&page=${page}&pageSize=12`,signal)
export const setWritingFavorite=(id:string,revision:number,signal?:AbortSignal,isCurrent?:()=>boolean)=>businessWrite<PersonalWriting>(`/api/v1/writing-materials/${encodeURIComponent(id)}/favorite`,{materialRevision:revision},'PUT',signal,isCurrent)
export const cancelWritingFavorite=(id:string,signal?:AbortSignal,isCurrent?:()=>boolean)=>businessWrite<void>(`/api/v1/writing-materials/${encodeURIComponent(id)}/favorite`,undefined,'DELETE',signal,isCurrent)
export const saveWritingImitation=(id:string,text:string,materialRevision:number,expectedLockVersion:number,signal?:AbortSignal,isCurrent?:()=>boolean)=>businessWrite<PersonalWriting>(`/api/v1/writing-materials/${encodeURIComponent(id)}/imitation`,{text,materialRevision,expectedLockVersion},'PUT',signal,isCurrent)
export const writingFavoriteStates=(article:string,version:string,signal?:AbortSignal)=>businessGet<Record<string,boolean>>(`/api/v1/reading/articles/${encodeURIComponent(article)}/versions/${encodeURIComponent(version)}/writing-materials/states`,signal)
const base=(article:string,version:string)=>`articles/${encodeURIComponent(article)}/versions/${encodeURIComponent(version)}/writing-materials`
export const publishedWriting=(article:string,version:string,signal?:AbortSignal)=>publicReadingGet<PublishedWriting[]>(`/api/v1/reading/${base(article,version)}`,signal)
export const adminWriting=(article:string,version:string,signal?:AbortSignal)=>businessGet<AdminWriting[]>(`/api/v1/admin/${base(article,version)}`,signal)
export const createWriting=(article:string,version:string,input:WritingContent)=>businessWrite<AdminWriting>(`/api/v1/admin/${base(article,version)}`,input)
export const saveWriting=(id:string,input:WritingContent,expectedLockVersion:number)=>businessWrite<AdminWriting>(`/api/v1/admin/writing-materials/${encodeURIComponent(id)}`,{...input,expectedLockVersion},'PUT')
export const writingStatus=(id:string,status:'publish'|'unpublish'|'archive',expectedLockVersion:number)=>businessWrite<AdminWriting|undefined>(`/api/v1/admin/writing-materials/${encodeURIComponent(id)}${status==='archive'?'':'/'+status}`,{expectedLockVersion},status==='archive'?'DELETE':'POST')
