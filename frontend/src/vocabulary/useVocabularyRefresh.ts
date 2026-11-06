import {onMounted,onBeforeUnmount} from 'vue'
/** Idle dashboard reads only; never restore a session or interrupt an uncertain write. */
export function useVocabularyRefresh(refresh:()=>Promise<boolean>,enabled:()=>boolean,nextDue:()=>string|null|undefined){
 const day=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date())
 let lastDay=day(),lastDue='',disposed=false,inFlight=false,timer:ReturnType<typeof setInterval>|undefined,queued:ReturnType<typeof setTimeout>|undefined
 const dueKey=()=>{const value=nextDue();return value&&Number.isFinite(Date.parse(value))&&Date.parse(value)<=Date.now()?day()+'\n'+value:''}
 function request(){
  if(disposed||document.visibilityState!=='visible'||!enabled()||inFlight||queued!==undefined)return
  queued=setTimeout(async()=>{queued=undefined;if(disposed||document.visibilityState!=='visible'||!enabled())return;inFlight=true;const date=day(),due=dueKey();try{if(await refresh()){lastDay=date;if(due)lastDue=due}}finally{inFlight=false}},100)
 }
 function check(){if(day()!==lastDay||dueKey()&&dueKey()!==lastDue)request()}
 onMounted(()=>{timer=setInterval(check,30000);window.addEventListener('focus',request);document.addEventListener('visibilitychange',request)})
 onBeforeUnmount(()=>{disposed=true;clearInterval(timer);clearTimeout(queued);window.removeEventListener('focus',request);document.removeEventListener('visibilitychange',request)})
}
