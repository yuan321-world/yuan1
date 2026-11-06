import {ref} from 'vue'
import type {Router} from 'vue-router'

export const navigationLoadFailure=ref<{target:string}|null>(null)
const key='lingua-loop:module-navigation-reload'
function destination(target:string){const url=new URL(target,location.origin);if(url.origin!==location.origin)throw new Error('Navigation must stay on this site');return url.href}
export function retryFailedNavigation(){if(navigationLoadFailure.value)location.assign(destination(navigationLoadFailure.value.target))}
export function installNavigationRecovery(router:Router){
 router.onError((error,to)=>{
  const message=error instanceof Error?error.message:String(error)
  if(!/Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Unable to preload CSS|Loading chunk .+ failed/i.test(message))return
  navigationLoadFailure.value={target:to.fullPath}
  try{
   const previous=JSON.parse(sessionStorage.getItem(key)||'null') as {target?:string;time?:number}|null
   if(previous?.target===to.fullPath&&typeof previous.time==='number'&&Date.now()-previous.time<30_000)return
   sessionStorage.setItem(key,JSON.stringify({target:to.fullPath,time:Date.now()}))
   location.assign(destination(to.fullPath))
  }catch{/* Storage unavailable: keep the page and show a manual retry instead of looping. */}
 })
 router.afterEach((to,_from,failure)=>{
  if(failure)return
  if(navigationLoadFailure.value?.target===to.fullPath)navigationLoadFailure.value=null
  try{const previous=JSON.parse(sessionStorage.getItem(key)||'null') as {target?:string}|null;if(previous?.target===to.fullPath)sessionStorage.removeItem(key)}catch{/* Recovery still works without storage. */}
 })
}
