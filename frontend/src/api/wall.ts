import { publicReadingGet, businessGet, businessWrite } from './business'
import type { WallMessage, WallPage, WallQuery, MessageInput, WallCommand, WallComment } from '@/wall/types'
import type { WallNoticePage,AdminWallRow,AdminWallDetail,AdminWallComment,ModerationInput } from '@/wall/types'
export function wallId(id: string) { if (!/^[1-9][0-9]{0,18}$/.test(id)) throw new TypeError('内容标识无效'); return id }
export function queryString(query: Record<string,unknown>) { const p = new URLSearchParams(); for (const [k,v] of Object.entries(query)) if(v!==undefined&&v!==null&&v!=='')p.set(k,String(v)); return p.size?'?'+p.toString():'' }
export const listMessages = (query: WallQuery = {}, signal?: AbortSignal) => publicReadingGet<WallPage<WallMessage>>('/api/v1/wall/messages'+queryString(query as Record<string,unknown>),signal)
export const listMine = (cursor?: string, signal?: AbortSignal) => businessGet<WallPage<{message:WallMessage;status:string;authorReason:string}>>('/api/v1/wall/mine'+queryString({cursor}),signal)
export const message = (id: string) => publicReadingGet<WallMessage>('/api/v1/wall/messages/'+wallId(id))
export const createMessage = (input: MessageInput) => businessWrite<WallMessage>('/api/v1/wall/messages',input)
export const deleteOwnMessage = (id: string, input: WallCommand) => businessWrite('/api/v1/wall/messages/'+wallId(id),input,'DELETE')
export const requestStatus = (id: string) => businessGet<{completed:boolean;operation:string;targetId:string}>('/api/v1/wall/requests/'+encodeURIComponent(id))
export const setLike = (id:string,liked:boolean) => businessWrite<WallMessage>('/api/v1/wall/messages/'+wallId(id)+'/like',{liked},'PUT')
export const listComments = (id:string,cursor?:string) => publicReadingGet<WallPage<WallComment>>('/api/v1/wall/messages/'+wallId(id)+'/comments'+queryString({cursor}))
export const createComment = (id:string,input:{requestId:string;text:string}) => businessWrite<WallComment>('/api/v1/wall/messages/'+wallId(id)+'/comments',input)
export const deleteComment = (id:string,input:WallCommand) => businessWrite('/api/v1/wall/comments/'+wallId(id),input,'DELETE')
export const reportContent = (input:{requestId:string;targetType:'MESSAGE'|'COMMENT';targetId:string;reason:string;detail:string}) => businessWrite('/api/v1/wall/reports',input)
export const listNotices = (cursor?:string) => businessGet<WallNoticePage>('/api/v1/wall/notifications'+queryString({cursor}))
export const readNotice = (id:string) => businessWrite('/api/v1/wall/notifications/'+wallId(id)+'/read',{},'PUT')
export const readAllNotices = (throughId:string) => businessWrite('/api/v1/wall/notifications/read-all',{throughId},'PUT')
export const adminMessages = (query:{status?:string;flagged?:boolean;cursor?:string}) => businessGet<WallPage<AdminWallRow>>('/api/v1/admin/wall/messages'+queryString(query))
export const adminMessage = (id:string) => businessGet<AdminWallDetail>('/api/v1/admin/wall/messages/'+wallId(id))
export const adminComments = (id:string,cursor?:string) => businessGet<WallPage<AdminWallComment>>('/api/v1/admin/wall/comments'+queryString({messageId:wallId(id),cursor}))
export const createAnnouncement = (input:MessageInput) => businessWrite<WallMessage>('/api/v1/admin/wall/messages',input)
export const moderateContent = (id:string,type:'MESSAGE'|'COMMENT',input:ModerationInput) => businessWrite('/api/v1/admin/wall/'+(type==='MESSAGE'?'messages/':'comments/')+wallId(id)+'/moderation',input)
