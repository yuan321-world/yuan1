import {businessGet,businessWrite} from './business'
export interface AdminUser {id:string;username:string;displayName:string;roles:string[];enabled:boolean;version:number;createdAt:string;protectedAccount:boolean}
export interface AdminUserDetail extends AdminUser {maskedEmail:string;emailVerified:boolean}
export interface Page<T>{items:T[];page:number;pageSize:number;total:number}
export interface Audit {id:string;actor:{id:string;username:string};target:{id:string;username:string};beforeEnabled:boolean;afterEnabled:boolean;reason:string;createdAt:string}
export interface AuditPage {items:Audit[];nextCursor:string|null;hasMore:boolean}
export interface StatusCommand {requestId:string;expectedVersion:number;enabled:boolean;reason:string}
export interface Receipt {requestId:string;targetId:string;enabled:boolean;version:number;completed:boolean}
export const listUsers=(query:URLSearchParams,signal?:AbortSignal)=>businessGet<Page<AdminUser>>(`/api/v1/admin/users?${query}`,signal)
export const userDetail=(id:string,signal?:AbortSignal)=>businessGet<AdminUserDetail>(`/api/v1/admin/users/${encodeURIComponent(id)}`,signal)
export const changeStatus=(id:string,input:StatusCommand)=>businessWrite<Receipt>(`/api/v1/admin/users/${encodeURIComponent(id)}/status`,input,'PUT')
export const commandStatus=(id:string)=>businessGet<Receipt>(`/api/v1/admin/users/requests/${encodeURIComponent(id)}`)
export const userAudit=(id?:string,cursor?:string,signal?:AbortSignal)=>{const q=new URLSearchParams();if(id)q.set('userId',id);if(cursor)q.set('cursor',cursor);return businessGet<AuditPage>(`/api/v1/admin/users/audit?${q}`,signal)}
