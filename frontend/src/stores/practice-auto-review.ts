import {ref,watch} from 'vue'
import {defineStore} from 'pinia'
import {useAuthStore} from './auth'
import {usePracticeReview} from './practice-review'
import {getGeneration,onRemoteIdentityChange} from '@/auth/token-session'
import type {ReviewSource} from '@/api/practice-review'
interface Job extends ReviewSource {requestId:string;status:'PENDING'|'STOPPED';message?:string;retry?:boolean}
const key=(s:ReviewSource)=>`${s.sourceType}:${s.attemptId}:${s.slot}`
export const useAutomaticPracticeReview=defineStore('practice-auto-review',()=>{
 const auth=useAuthStore(),reviews=usePracticeReview(),jobs=ref<Job[]>([])
 let owner='',epoch=0,busy=false,timer:ReturnType<typeof setTimeout>|undefined
 const storageKey=()=>`lingua-practice-auto-review:${owner}`
 function persist(){if(!owner)return;try{localStorage.setItem(storageKey(),JSON.stringify(jobs.value))}catch{/* The in-memory queue still works when browser storage is unavailable. */}}
 function stop(){epoch++;clearTimeout(timer);timer=undefined;busy=false;jobs.value=[];owner=''}
 function schedule(delay=3000){clearTimeout(timer);timer=undefined;if(owner&&jobs.value.some(j=>j.status==='PENDING'))timer=setTimeout(()=>void tick(),delay)}
 function restore(){const id=auth.user?.id||'';if(id===owner)return;if(owner)stop();owner=id;if(!owner)return
  try{const saved:unknown=JSON.parse(localStorage.getItem(storageKey())||'[]');if(Array.isArray(saved))jobs.value=saved.filter((j):j is Job=>j&&['SUBJECTIVE','PAPER'].includes(j.sourceType)&&/^\d+$/.test(j.attemptId)&&['WRITING','TRANSLATION'].includes(j.slot)&&['PENDING','STOPPED'].includes(j.status)&&typeof j.requestId==='string')}catch{jobs.value=[]}
 }
 function resume(){restore();schedule(0)}
 function job(source:ReviewSource){return jobs.value.find(j=>key(j)===key(source))}
 function enqueue(sources:ReviewSource[]){restore();if(!owner)return;for(const source of sources)if(!job(source))jobs.value.push({...source,requestId:crypto.randomUUID(),status:'PENDING'});persist();schedule(0)}
 function retry(source:ReviewSource){restore();const item=job(source);if(item){item.status='PENDING';item.retry=true;item.message='';const state=reviews.state(source);if(state.records.length)item.requestId=crypto.randomUUID();state.requestId=item.requestId}else{jobs.value.push({...source,requestId:crypto.randomUUID(),status:'PENDING',retry:true})}persist();schedule(0)}
 async function tick(){timer=undefined;if(busy||!owner)return;if(document.visibilityState==='hidden'){schedule();return}const item=jobs.value.find(j=>j.status==='PENDING');if(!item)return
  const e=epoch,u=owner,g=getGeneration(),valid=()=>e===epoch&&u===owner&&u===auth.user?.id&&g===getGeneration()
  busy=true
  const stopped=(message:string)=>{item.status='STOPPED';item.message=message}
  try{
   const source:ReviewSource={sourceType:item.sourceType,attemptId:item.attemptId,slot:item.slot}
   const loaded=await reviews.load(source);if(!valid())return
   const state=reviews.state(item)
   if(!loaded){if(state.error)stopped(state.error);return}
   if(state.records.some(r=>r.status==='SUCCEEDED')){jobs.value=jobs.value.filter(j=>key(j)!==key(item));return}
   if(state.records.some(r=>r.status==='QUEUED'||r.status==='RUNNING'))return
   if(state.records.length&&!item.retry){stopped('本次评分未完成，请手动重试。');return}
   await reviews.request(source,item.requestId);if(!valid())return
   if(state.errorCode==='PRACTICE_REVIEW_ACTIVE'){item.message='等待上一项批改完成';return}
   item.retry=false
   if(state.error){stopped(state.error);return}
   if(state.records.some(r=>r.status==='SUCCEEDED'))jobs.value=jobs.value.filter(j=>key(j)!==key(item))
   else if(state.records.some(r=>r.status==='FAILED'))stopped('本次评分未完成，请手动重试。')
  }finally{if(e===epoch&&u===owner&&u===auth.user?.id){busy=false;persist();schedule()}}
 }
 watch(()=>auth.user?.id,()=>{stop();resume()},{flush:'sync'})
 onRemoteIdentityChange(stop)
 return {jobs,job,enqueue,retry,resume,stop}
})
