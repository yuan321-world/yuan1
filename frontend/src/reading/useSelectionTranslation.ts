import {ref} from 'vue'
import * as api from '@/api/selection-translation'
import {ApiError} from '@/api/client'
import {errorMessage} from '@/api/identity'
export function useSelectionTranslation(){
 const task=ref<api.SelectionTranslationTask|null>(null),busy=ref(false),error=ref(''),waiting=ref(false)
 const requests=new Map<string,api.SelectionTranslationInput>();let identity='',generation=0,controller:AbortController|undefined
 function cancel(){generation++;controller?.abort();busy.value=false;waiting.value=false}
 function clear(){cancel();task.value=null;error.value=''}
 function bindIdentity(key:string){if(identity===key)return;identity=key;requests.clear();clear()}
 function pause(ms:number,signal:AbortSignal){return new Promise<void>(resolve=>{const done=()=>{clearTimeout(timer);signal.removeEventListener('abort',done);resolve()},timer=setTimeout(done,ms);signal.addEventListener('abort',done,{once:true});if(signal.aborted)done()})}
 async function start(articleId:string,versionId:string,text:string,retry=false,mode:api.SelectionMode='TRANSLATION',context:api.PracticeSelectionContext={}){cancel();const current=generation;controller=new AbortController();const signal=controller.signal;busy.value=true;error.value='';task.value=null;const key=mode+'\n'+versionId+'\n'+text+'\n'+JSON.stringify(context);if(retry)requests.delete(key);let input=requests.get(key);const previous=!!input;if(!input){input={requestId:crypto.randomUUID(),articleId,versionId,text,mode,...context};requests.set(key,input)}
  try{let result:api.SelectionTranslationTask|undefined;if(previous){try{result=await api.translationRequest(input.requestId,signal)}catch(e){if(!(e instanceof ApiError&&e.status===404))throw e}}if(current!==generation)return;if(!result)result=await api.createTranslation(input,signal);if(current!==generation)return;task.value=result;const deadline=Date.now()+60000;while((result.status==='QUEUED'||result.status==='RUNNING')&&Date.now()<deadline){await pause(1000,signal);if(current!==generation)return;result=await api.translationTask(result.id,signal);if(current!==generation)return;task.value=result}if(current===generation)waiting.value=result.status==='QUEUED'||result.status==='RUNNING'
  }catch(e){if(current===generation&&!(e instanceof DOMException&&e.name==='AbortError')){error.value=errorMessage(e);if(e instanceof ApiError&&[400,401,403,409,429].includes(e.status))requests.delete(key)}}finally{if(current===generation)busy.value=false}
 }
 return {task,busy,error,waiting,cancel,clear,bindIdentity,start}
}
