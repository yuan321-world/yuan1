import {onMounted,onBeforeUnmount} from 'vue'

/** Read-only refresh when returning to a visible page or when the Shanghai date changes. */
export function useReadingRefresh(refresh:()=>unknown,enabled:()=>boolean){
 const day=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date())
 let lastDay=day(),timer:ReturnType<typeof setInterval>|undefined,queued:ReturnType<typeof setTimeout>|undefined
 function request(){
  if(document.visibilityState!=='visible'||!enabled()||queued!==undefined)return
  queued=setTimeout(()=>{queued=undefined;if(document.visibilityState==='visible'&&enabled()){lastDay=day();void refresh()}},100)
 }
 function checkDay(){if(day()!==lastDay)request()}
 onMounted(()=>{timer=setInterval(checkDay,30000);window.addEventListener('focus',request);document.addEventListener('visibilitychange',request)})
 onBeforeUnmount(()=>{clearInterval(timer);clearTimeout(queued);window.removeEventListener('focus',request);document.removeEventListener('visibilitychange',request)})
}
