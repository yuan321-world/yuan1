import {reactive,watch} from 'vue'
import {defineStore} from 'pinia'
import {useAuthStore} from './auth'
import {getGeneration,onRemoteIdentityChange} from '@/auth/token-session'
import {ApiError} from '@/api/client'
import {errorMessage} from '@/api/identity'
import * as api from '@/api/practice-review'
interface State {records:api.Review[];selected:string;loading:boolean;requesting:boolean;requestId:string;error:string}
export const usePracticeReview=defineStore('practice-review',()=>{
 const auth=useAuthStore(),states=reactive<Record<string,State>>({});let epoch=0
 const key=(s:api.ReviewSource)=>`${s.sourceType}:${s.attemptId}:${s.slot}`
 const state=(s:api.ReviewSource)=>{const k=key(s);if(!states[k])states[k]={records:[],selected:'',loading:false,requesting:false,requestId:'',error:''};return states[k]!}
 const sameOwner=()=>{const e=epoch,owner=auth.user?.id;return()=>e===epoch&&owner===auth.user?.id}
 const guard=()=>{const same=sameOwner(),g=getGeneration();return()=>same()&&g===getGeneration()}
 function clear(){epoch++;for(const k of Object.keys(states))delete states[k]}
 watch(()=>auth.user?.id,clear,{flush:'sync'});onRemoteIdentityChange(clear)
 async function load(source:api.ReviewSource){const s=state(source);if(s.loading||s.requesting)return;const valid=guard(),same=sameOwner();s.loading=true;try{const records=await api.history(source);if(valid()){s.records=records;if(!records.some(r=>r.id===s.selected))s.selected=records[0]?.id||'';s.error=''}}catch(e){if(same())s.error=errorMessage(e)}finally{if(same())s.loading=false}}
 async function request(source:api.ReviewSource){const s=state(source);if(s.requesting||s.records.some(r=>r.status==='QUEUED'||r.status==='RUNNING'||r.status==='SUCCEEDED'))return;s.requestId||=crypto.randomUUID();s.requesting=true;s.error='';const valid=guard(),same=sameOwner();try{const result=await api.create(source,s.requestId);if(valid()){s.records=[result,...s.records.filter(r=>r.id!==result.id)];s.selected=result.id;s.requestId=''}}catch(e){if(same()){s.error=errorMessage(e);if(e instanceof ApiError&&[400,401,403,404,409,429,503].includes(e.status))s.requestId=''}}finally{if(same())s.requesting=false}}
 return{state,load,request,clear}
})
