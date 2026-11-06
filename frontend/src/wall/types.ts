export interface WallAuthor { displayName: string; isAdmin: boolean }
export interface WallMessage {
 id: string; version: number; kind: 'MESSAGE'|'ANNOUNCEMENT'; category: string; color: string; text: string; title: string
 author: WallAuthor; createdAt: string; editedByAdmin: boolean; position: { x: number; y: number; width: number; height: number }
 likes: number; liked: boolean; commentCount: number; owned: boolean; status?: string; authorReason?: string
}
export interface WallPage<T> { items: T[]; nextCursor: string|null; hasMore: boolean }
export interface MessageInput { requestId: string; kind: 'MESSAGE'|'ANNOUNCEMENT'; category: string; color: string; text: string; title?: string; preferredPosition?: { x: number; y: number } }
export interface WallQuery { category?: string; keyword?: string; cursor?: string; pageSize?: number; mine?: boolean }
export interface WallCommand { requestId: string; expectedVersion: number }
export interface WallComment { id:string;messageId:string;version:number;text:string;author:WallAuthor;createdAt:string;owned:boolean }
export interface WallNotice {id:string;messageId:string;targetId:string;targetType:'MESSAGE'|'COMMENT';action:string;authorReason:string;excerpt:string;createdAt:string;readAt:string|null;canOpen:boolean}
export interface WallNoticePage extends WallPage<WallNotice>{unreadCount:number;readBoundary:string}
export interface WallReport {id:string;messageId:string;commentId:string|null;targetType:string;reason:string;detail:string;status:string;createdAt:string}
export interface WallAudit {id:string;commentId:string|null;action:string;beforeText:string;afterText:string;authorReason:string;internalNote:string;createdAt:string}
export interface AdminWallRow {message:WallMessage;status:string;pendingReports:number}
export interface AdminWallDetail {message:WallMessage;status:string;reports:WallReport[];history:WallAudit[]}
export interface AdminWallComment {comment:WallComment;status:string;reports:WallReport[]}
export interface ModerationInput {requestId:string;expectedVersion:number;action:string;text?:string;title?:string;authorReason:string;internalNote:string;reportIds:string[]}
export interface ModerationTarget {id:string;version:number;action:string;targetType:'MESSAGE'|'COMMENT';kind?:string;text:string;title?:string;reportIds:string[]}
export const categories: Record<string,string> = { STUDY:'学习心得',CHECKIN:'每日打卡',ENCOURAGE:'加油鼓励',ANNOUNCEMENT:'公告' }
export const noteColors: Record<string,string> = { yellow:'#fff0b7',pink:'#f8dce6',blue:'#dceafa',green:'#e1eddb',purple:'#e8dff7',orange:'#f9e2c9' }
