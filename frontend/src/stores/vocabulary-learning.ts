import {ref,watch} from 'vue'
import {defineStore} from 'pinia'
import {useAuthStore} from './auth'
import {getGeneration,onRemoteIdentityChange} from '../auth/token-session'
import {ApiError} from '../api/client'
import {errorMessage} from '../api/identity'
import * as api from '../api/vocabulary-learning'
export const useVocabularyLearning=defineStore('vocabulary-learning',()=>{
 const auth=useAuthStore(),overview=ref<api.Overview|null>(null),session=ref<api.Session|null>(null),busy=ref(false),error=ref(''),inStudy=ref(false)
 const refreshError=ref('')
 const pending=ref<{path:string;body:api.Command}|null>(null)
 let refreshSequence=0,refreshController:AbortController|undefined
 function cancelRefresh(){refreshSequence++;refreshController?.abort();refreshError.value=''}
 let epoch=0,controller:AbortController|undefined
 function clear(){cancelRefresh();epoch++;controller?.abort();overview.value=null;session.value=null;pending.value=null;error.value='';busy.value=false;inStudy.value=false;try{for(const key of Object.keys(sessionStorage))if(key.startsWith('lingua-vocabulary-input:'))sessionStorage.removeItem(key)}catch{/* storage is optional */}}
 watch(()=>auth.user?.id,clear,{flush:'sync'});onRemoteIdentityChange(clear)
 function context(){const e=epoch,g=getGeneration(),id=auth.user?.id;return()=>epoch===e&&getGeneration()===g&&auth.user?.id===id}
 async function load(){if(!auth.user)return;cancelRefresh();epoch++;controller?.abort();controller=new AbortController();const current=context();busy.value=true;error.value=pending.value?'有一项操作尚未确认，请重试刚才的操作。':''
  try{const [o,s]=await Promise.all([api.learningOverview(controller.signal),api.learningSession(controller.signal)]);if(current()){overview.value=o;session.value=s;inStudy.value=!!s&&s.status==='ACTIVE'}}catch(e){if(current()&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(current())busy.value=false}
 }
 async function refreshOverview(){
  if(!auth.user||busy.value||pending.value||inStudy.value)return false
  refreshController?.abort();refreshController=new AbortController();const sequence=++refreshSequence,current=context(),savedSession=session.value
  const valid=()=>sequence===refreshSequence&&current()&&!busy.value&&!pending.value&&!inStudy.value&&session.value===savedSession
  refreshError.value=''
  try{const result=await api.learningOverview(refreshController.signal);if(!valid())return false;overview.value=result;return true}
  catch(e){if(valid()&&!(e instanceof DOMException&&e.name==='AbortError'))refreshError.value=errorMessage(e);return false}
 }
 async function send(path:string,body:api.Command){if(busy.value||(pending.value&&pending.value.body.requestId!==body.requestId))return;cancelRefresh();pending.value={path,body};const current=context();busy.value=true;error.value='';controller=new AbortController()
  try{const s=await api.learningCommand(path,body,controller.signal);if(current()){
   if(s.flowVersion!=='v2'&&s.mode==='SPELLING'&&s.phase==='QUESTION'&&s.current){
    try{const prefix=`lingua-vocabulary-input:${auth.user?.id}:${s.id}:${s.current.id}:`,keys=Object.keys(sessionStorage).filter(k=>k.startsWith(prefix)&&Number(k.slice(prefix.length))<=s.version).sort((a,b)=>Number(b.slice(prefix.length))-Number(a.slice(prefix.length)));const saved=keys[0]&&sessionStorage.getItem(keys[0]);if(saved)sessionStorage.setItem(prefix+s.version,saved)}catch{/* optional cache */}
   }
   session.value=s;pending.value=null;inStudy.value=s.status!=='PAUSED';const o=await api.learningOverview(controller.signal);if(current())overview.value=o}}
  catch(e){if(current()){error.value=errorMessage(e);if(e instanceof ApiError&&e.status>=400&&e.status<500){pending.value=null;if(e.status===409){try{const latest=await api.learningSession(controller.signal);if(current()){session.value=latest;inStudy.value=!!latest&&latest.status==='ACTIVE'}}catch{/* retain the visible retry error */}}}}}
  finally{if(current())busy.value=false}
 }
 function reviewBlocksNew(){return overview.value?.savedSessions?.some(round=>round.kind==='REVIEW') ?? false}
 async function resume(id:string,completedOnly=false){if(busy.value||pending.value)return;
  if(!completedOnly&&reviewBlocksNew()&&overview.value?.savedSessions?.some(round=>round.id===id&&round.kind==='NEW'))return;cancelRefresh();const current=context();controller=new AbortController();busy.value=true;error.value='';try{const saved=await api.learningSavedSession(id,controller.signal);if(!current()||(completedOnly?saved.status!=='COMPLETED':saved.kind==='NEW'&&reviewBlocksNew()))return;session.value=saved;if(saved.status==='COMPLETED'){inStudy.value=true;return}busy.value=false;await update('state',{status:'ACTIVE'})}catch(e){if(current()&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(current())busy.value=false}}
 function start(kind:'NEW'|'REVIEW',mode:'RECOGNITION'|'SPELLING'){if(kind==='NEW'&&reviewBlocksNew())return Promise.resolve();return send('/sessions',{requestId:crypto.randomUUID(),kind,mode,flowVersion:'v2'})}
 function update(operation:'feedback'|'next'|'state',fields:Partial<api.Command>={}){const s=session.value;if(!s)return Promise.resolve();return send(`/sessions/${s.id}/${operation}`,{requestId:crypto.randomUUID(),expectedVersion:s.version,itemId:s.current?.id,...(s.flowVersion==='v2'?{attemptId:s.current?.attemptId}:{}),...fields})}
 function answer(fields:Partial<api.Command>){return update('feedback',fields)}
 function retry(){const p=pending.value;return p?send(p.path,p.body):load()}
 function dispose(){cancelRefresh();epoch++;controller?.abort();busy.value=false}
 return {overview,session,busy,error,inStudy,pending,refreshError,refreshOverview,load,start,resume,answer,retry,update,clear,dispose}
})

