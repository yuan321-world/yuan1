import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as api from '@/api/wall'
import { ApiError } from '@/api/client'
import { errorMessage } from '@/api/identity'
import type { MessageInput, WallMessage } from '@/wall/types'
export const useWallStore = defineStore('wall', () => {
 const rows=ref<WallMessage[]>([]), loading=ref(false), sending=ref(false), error=ref(''), draft=ref(''), category=ref(''),keyword=ref(''),mine=ref(false),cursor=ref<string|null>(null),hasMore=ref(false),pending=ref<MessageInput|null>(null)
 const likePending=ref<Record<string,boolean>>({})
 const lastCreatedId=ref<string|null>(null),replyDrafts=ref<Record<string,{text:string;pending:{requestId:string;text:string}|null}>>({})
 let identity='',epoch=0,sequence=0,controller:AbortController|undefined
 const view=ref<'canvas'|'list'>(typeof innerWidth!=='undefined'&&innerWidth<=600?'list':'canvas'),pan=ref({x:20,y:100,scale:.8}),panReady=ref(false)
 function bindIdentity(key:string){if(identity===key)return;identity=key;epoch++;sequence++;controller?.abort();rows.value=[];likePending.value={};draft.value='';pending.value=null;lastCreatedId.value=null;replyDrafts.value={};keyword.value='';category.value='';pan.value={x:20,y:100,scale:.8};panReady.value=false;error.value='';loading.value=false;sending.value=false;mine.value=false;cursor.value=null;hasMore.value=false}
 async function load(reset=false){controller?.abort();controller=new AbortController();const current=++sequence,account=epoch;loading.value=true;error.value='';if(reset){cursor.value=null;rows.value=[]}
  try{let page;if(mine.value){const result=await api.listMine(cursor.value??undefined,controller.signal);page={...result,items:result.items.map(r=>({...r.message,status:r.status,authorReason:r.authorReason}))}}else page=await api.listMessages({category:category.value,keyword:keyword.value,cursor:cursor.value??undefined},controller.signal)
   if(current!==sequence||account!==epoch)return;const existing=reset?[]:rows.value;rows.value=[...existing,...page.items.filter(p=>!existing.some(x=>x.id===p.id))];cursor.value=page.nextCursor;hasMore.value=page.hasMore
  }catch(e){if(current===sequence&&account===epoch&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(current===sequence&&account===epoch)loading.value=false}
 }
 async function toggleLike(note:WallMessage){if(likePending.value[note.id]||note.status==='HIDDEN')return;const account=epoch;likePending.value[note.id]=true;error.value='';try{const result=await api.setLike(note.id,!note.liked);if(account!==epoch)return;rows.value=rows.value.map(row=>row.id===result.id?{...row,likes:result.likes,liked:result.liked}:row)}catch(e){if(account===epoch)error.value=errorMessage(e)}finally{if(account===epoch)delete likePending.value[note.id]}}
 async function publish(input:Omit<MessageInput,'requestId'|'text'>){if(sending.value)return false;const account=epoch;sending.value=true;error.value='';const previous=pending.value;if(!pending.value)pending.value={...input,text:draft.value,requestId:crypto.randomUUID()}
  try{if(previous){try{const status=await api.requestStatus(previous.requestId);if(status.completed){if(account!==epoch)return false;lastCreatedId.value=status.targetId;pending.value=null;draft.value='';mine.value=false;category.value='';keyword.value='';await load(true);return true}}catch(e){if(!(e instanceof ApiError&&e.status===404))throw e}}
   const created=await (pending.value!.kind==='ANNOUNCEMENT'?api.createAnnouncement(pending.value!):api.createMessage(pending.value!));if(account!==epoch)return false;lastCreatedId.value=created.id;pending.value=null;draft.value='';mine.value=false;category.value='';keyword.value='';await load(true);return true
  }catch(e){if(account===epoch){error.value=errorMessage(e);if(e instanceof ApiError&&[400,401,403,409,429].includes(e.status))pending.value=null}return false}finally{if(account===epoch)sending.value=false}
 }
 return {likePending,toggleLike,rows,loading,sending,error,draft,category,keyword,mine,cursor,hasMore,pending,view,pan,panReady,lastCreatedId,replyDrafts,bindIdentity,load,publish}
})
