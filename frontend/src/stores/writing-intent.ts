import {defineStore} from 'pinia'
import {ref,watch,onScopeDispose} from 'vue'
import {useAuthStore} from './auth'
import {onRemoteIdentityChange} from '@/auth/token-session'
export interface WritingIntent {materialId:string;route:string;action:'favorite'|'save';favorite:boolean;draft:string;revision?:number;userId:string|null;expires:number}
/** One guest operation survives the existing login remount; never persisted in browser storage. */
export const useWritingIntent=defineStore('writing-intent',()=>{
 const auth=useAuthStore(),intent=ref<WritingIntent|null>(null),changes=ref(0)
 function clear(){intent.value=null}
 function begin(value:Omit<WritingIntent,'userId'|'expires'>){clear();intent.value={...value,userId:null,expires:Date.now()+300000}}
 function take(route:string){const value=intent.value;if(!value)return null;clear();return value.route===route&&value.userId===auth.user?.id?value:null}
 watch(()=>auth.user?.id,(value,previous)=>{if(previous&&previous!==value)clear();else if(value&&intent.value)intent.value.userId=value},{flush:'sync'})
 const unsubscribe=onRemoteIdentityChange(clear);onScopeDispose(()=>{clear();unsubscribe()})
 return {intent,changes,begin,take,clear,changed:()=>{changes.value++}}
})
