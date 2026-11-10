import {reactive,watch} from 'vue'
import {defineStore} from 'pinia'
import {useAuthStore} from './auth'
import {getGeneration,onRemoteIdentityChange} from '@/auth/token-session'
import {ApiError} from '@/api/client'
import {errorMessage} from '@/api/identity'
import * as api from '@/api/practice-review'
interface State {records:api.Review[];selected:string;loading:boolean;requesting:boolean;requestId:string;error:string;errorCode:string}
export const usePracticeReview=defineStore('practice-review',()=>{
 const auth=useAuthStore(),states=reactive<Record<string,State>>({});let epoch=0
 const key=(s:api.ReviewSource)=>`${s.sourceType}:${s.attemptId}:${s.slot}`
 const state=(s:api.ReviewSource)=>{const k=key(s);if(!states[k])states[k]={records:[],selected:'',loading:false,requesting:false,requestId:'',error:'',errorCode:''};return states[k]!}
 const sameOwner=()=>{const e=epoch,owner=auth.user?.id;return()=>e===epoch&&owner===auth.user?.id}
 const guard=()=>{const same=sameOwner(),g=getGeneration();return()=>same()&&g===getGeneration()}
 function clear(){epoch++;for(const k of Object.keys(states))delete states[k]}
 watch(()=>auth.user?.id,clear,{flush:'sync'});onRemoteIdentityChange(clear)
 async function load(source:api.ReviewSource){const s=state(source);if(s.loading||s.requesting)return false;const valid=guard(),same=sameOwner();s.loading=true;try{const records=await api.history(source);if(valid()){s.records=records;if(!records.some(r=>r.id===s.selected))s.selected=records[0]?.id||'';s.error='';s.errorCode='';return true}return false}catch(e){if(same()){s.error=errorMessage(e);s.errorCode=e instanceof ApiError?e.code||'HTTP_ERROR':'NETWORK_ERROR'}return false}finally{if(same())s.loading=false}}
 async function request(source:api.ReviewSource,requestId?:string){const s=state(source);if(s.requesting||s.records.some(r=>r.status==='QUEUED'||r.status==='RUNNING'||r.status==='SUCCEEDED'))return;s.requestId||=requestId||crypto.randomUUID();s.requesting=true;s.error='';s.errorCode='';const valid=guard(),same=sameOwner();try{const result=await api.create(source,s.requestId);if(valid()){s.records=[result,...s.records.filter(r=>r.id!==result.id)];s.selected=result.id;s.requestId=''}}catch(e){if(same()){s.error=errorMessage(e);s.errorCode=e instanceof ApiError?e.code||'HTTP_ERROR':'NETWORK_ERROR';if(e instanceof ApiError&&[400,401,403,404,409,429,503].includes(e.status))s.requestId=''}}finally{if(same())s.requesting=false}}
 return{state,load,request,clear}
})
