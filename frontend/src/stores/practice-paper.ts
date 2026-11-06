import {ref,computed,watch} from 'vue'
import {defineStore} from 'pinia'
import * as api from '@/api/practice-paper'
import {useAuthStore} from './auth'
import {ApiError} from '@/api/client'
import {errorMessage} from '@/api/identity'
import {getGeneration,onRemoteIdentityChange} from '@/auth/token-session'
const clone=<T>(x:T):T=>JSON.parse(JSON.stringify(x)) as T
export const usePracticePaperStore=defineStore('practice-paper',()=>{
 const auth=useAuthStore(),attempt=ref<api.PaperAttempt|null>(null),responses=ref<api.PaperResponse[]>([]),currentSlot=ref('WRITING'),currentQuestionId=ref<string|null>(null),loading=ref(false),saving=ref(false),submitting=ref(false),error=ref(''),conflict=ref(false),recoverySnapshot=ref(''),pendingSubmit=ref<api.PaperSubmitInput|null>(null)
 let epoch=0,timer:ReturnType<typeof setTimeout>|undefined,inflight:Promise<boolean>|null=null,saved=''
 const snapshot=()=>({currentSlot:currentSlot.value,currentQuestionId:currentQuestionId.value,responses:clone(responses.value)})
 const dirty=computed(()=>attempt.value?.status==='IN_PROGRESS'&&JSON.stringify(snapshot())!==saved)
 const canEdit=computed(()=>attempt.value?.status==='IN_PROGRESS'&&!loading.value&&!submitting.value&&!pendingSubmit.value&&!conflict.value)
 const status=computed(()=>error.value?'保存尚未确认':submitting.value?'正在交卷…':saving.value?'正在保存…':dirty.value?'有待保存内容':attempt.value?.status==='COMPLETED'?'已交卷':'草稿已保存')
 function dispose(){clearTimeout(timer);timer=undefined}
 function clear(){epoch++;dispose();attempt.value=null;responses.value=[];recoverySnapshot.value='';pendingSubmit.value=null;error.value='';conflict.value=false;loading.value=false;saving.value=false;submitting.value=false;inflight=null;saved=''}
 watch(()=>auth.user?.id,clear,{flush:'sync'});onRemoteIdentityChange(clear)
 const scope=()=>{const n=epoch,u=auth.user?.id,g=getGeneration();return()=>n===epoch&&u===auth.user?.id&&g===getGeneration()}
 function apply(a:api.PaperAttempt){attempt.value=a;responses.value=clone(a.responses);currentSlot.value=a.currentSlot;currentQuestionId.value=a.currentQuestionId;saved=JSON.stringify(snapshot())}
 async function load(id:string){clear();loading.value=true;const valid=scope();try{const a=await api.getPaperAttempt(id);if(valid())apply(a)}catch(e){if(valid())error.value=errorMessage(e)}finally{if(valid())loading.value=false}}
 function schedule(){dispose();timer=setTimeout(()=>{void flush()},900)}
 function setText(slot:string,text:string){if(!canEdit.value)return;const r=responses.value.find(x=>x.slot===slot);if(r){r.text=text;schedule()}}
 function setChoice(slot:string,id:string,choice:string|null){if(!canEdit.value)return;const r=responses.value.find(x=>x.slot===slot);if(r){if(choice)r.choices[id]=choice;else delete r.choices[id];schedule()}}
 function navigate(slot:string,id:string|null=null){currentSlot.value=slot;currentQuestionId.value=id;if(canEdit.value)schedule()}
 async function flush():Promise<boolean>{dispose();if(inflight)return inflight;if(!attempt.value||attempt.value.status==='COMPLETED'||!dirty.value)return true;if(conflict.value||pendingSubmit.value)return false;const valid=scope();saving.value=true;error.value='';const work=(async()=>{try{while(valid()&&dirty.value){const sent=snapshot(),fingerprint=JSON.stringify(sent);const a=await api.savePaperDraft(attempt.value!.id,{expectedLockVersion:attempt.value!.lockVersion,...sent});if(!valid())return false;attempt.value=a;saved=fingerprint}return valid()}catch(e){if(valid()){error.value=errorMessage(e);if(e instanceof ApiError&&e.status===409)conflict.value=true}return false}finally{if(valid()){saving.value=false;inflight=null}}})();inflight=work;return work}
 async function reloadKeepingLocal(){if(!attempt.value||saving.value||submitting.value)return;const valid=scope(),local=snapshot();loading.value=true;try{const a=await api.getPaperAttempt(attempt.value.id);if(!valid())return;if(JSON.stringify(local.responses)!==JSON.stringify(a.responses))recoverySnapshot.value=JSON.stringify(local.responses,null,2);apply(a);if(a.status==='COMPLETED')pendingSubmit.value=null;conflict.value=false;error.value=''}catch(e){if(valid())error.value=errorMessage(e)}finally{if(valid())loading.value=false}}
 async function submit(){if(!attempt.value||submitting.value||conflict.value)return false;if(!pendingSubmit.value&&!await flush())return false;if(!pendingSubmit.value)pendingSubmit.value={expectedLockVersion:attempt.value.lockVersion,...snapshot(),requestId:crypto.randomUUID(),confirmUnanswered:true};const valid=scope();submitting.value=true;error.value='';try{const a=await api.submitPaper(attempt.value.id,clone(pendingSubmit.value));if(!valid())return false;apply(a);pendingSubmit.value=null;return true}catch(e){if(valid()){error.value=errorMessage(e);if(e instanceof ApiError&&e.status===409){conflict.value=true;pendingSubmit.value=null}else if(e instanceof ApiError&&e.status===400)pendingSubmit.value=null}return false}finally{if(valid())submitting.value=false}}
 return{attempt,responses,currentSlot,currentQuestionId,loading,saving,submitting,error,conflict,recoverySnapshot,pendingSubmit,dirty,canEdit,status,load,setText,setChoice,navigate,flush,submit,reloadKeepingLocal,clear,dispose}
})
