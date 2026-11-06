<script setup lang="ts">
import {ref,watch,onMounted,onBeforeUnmount} from 'vue'
import {publishedWriting,writingFavoriteStates,type PublishedWriting,type PersonalWriting} from '@/api/writing'
import {errorMessage} from '@/api/identity'
import {useAuthStore} from '@/stores/auth'
import {onRemoteIdentityChange,getGeneration} from '@/auth/token-session'
import WritingMaterialDetail from './WritingMaterialDetail.vue'
import {useWritingIntent} from '@/stores/writing-intent'
const props=defineProps<{article:string;version:string}>(),auth=useAuthStore()
const items=ref<PublishedWriting[]>([]),busy=ref(false),error=ref(''),stateError=ref(''),states=ref<Record<string,boolean>>({}),desktop=ref(matchMedia('(min-width:1021px)').matches),expanded=ref(false),selected=ref<PublishedWriting>(),initialFavorite=ref<boolean>()
const media=matchMedia('(min-width:1021px)');let controller:AbortController|undefined,stateController:AbortController|undefined,generation=0,stateGeneration=0
function resize(){desktop.value=media.matches}
async function loadStates(){const seq=++stateGeneration,ticket=generation,identity=getGeneration();stateController?.abort();states.value={};stateError.value='';if(!auth.user)return;stateController=new AbortController();try{const data=await writingFavoriteStates(props.article,props.version,stateController.signal);if(seq===stateGeneration&&ticket===generation&&identity===getGeneration())states.value=data}catch(e){if(seq===stateGeneration&&ticket===generation&&identity===getGeneration()&&!(e instanceof DOMException&&e.name==='AbortError'))stateError.value=errorMessage(e)}}
async function load(){const ticket=++generation;controller?.abort();controller=new AbortController();items.value=[];selected.value=undefined;busy.value=true;error.value='';expanded.value=false;try{const data=await publishedWriting(props.article,props.version,controller.signal);if(ticket===generation){items.value=data;void loadStates()}}catch(e){if(ticket===generation&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(ticket===generation)busy.value=false}}
function open(item:PublishedWriting,favorite?:boolean){initialFavorite.value=favorite;selected.value=item}
function changed(value:PersonalWriting){states.value={...states.value,[value.materialId]:value.favorite}}
function clearIdentity(){generation++;stateGeneration++;controller?.abort();stateController?.abort();items.value=[];states.value={};selected.value=undefined;busy.value=false;error.value='账户状态已变化，请重新加载素材。'}
const unsubscribe=onRemoteIdentityChange(clearIdentity)
const intent=useWritingIntent();watch(()=>intent.changes,()=>{void loadStates()})
watch(()=>[props.article,props.version],load,{immediate:true,flush:'sync'})
watch(()=>auth.user?.id,(value,previous)=>{if(previous&&value!==previous)clearIdentity();else void loadStates()},{flush:'sync'})
onMounted(()=>media.addEventListener('change',resize));onBeforeUnmount(()=>{generation++;stateGeneration++;controller?.abort();stateController?.abort();unsubscribe();media.removeEventListener('change',resize)})
</script>
<template><Teleport to="#reader-writing-mobile" :disabled="desktop" defer><details class="reader-rail-card article-writing-panel" :open="desktop"><summary>写作积累 <small>{{ items.length }} 条</small></summary><p class="reader-rail-note">不只理解意思，也留下值得再用的表达。</p><p v-if="busy" role="status">正在加载素材…</p><div v-else-if="error" role="alert"><p>{{ error }}</p><button type="button" class="text-button" @click="load">重试写作素材</button></div><template v-else><p v-if="stateError" class="reader-rail-note" role="status">收藏状态暂未同步。<button type="button" class="text-button" @click="loadStates">重试收藏状态</button></p><p v-if="!items.length" class="reader-rail-note">本篇暂无已发布写作素材。</p><section v-for="item in (expanded?items:items.slice(0,3))" :key="item.id" class="article-writing-item"><div class="writing-card-heading"><h3 lang="en">{{ item.content.expression }}</h3><button type="button" class="writing-bookmark" :aria-label="states[item.id]?'取消收藏素材':'收藏素材'" :aria-pressed="!!states[item.id]" :disabled="!!stateError" @click="open(item,!states[item.id])"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h12v18l-6-4-6 4V3Z"/></svg></button></div><p>{{ item.content.meaningZh }}</p><button class="text-button" type="button" @click="open(item)">查看解析</button></section><button v-if="items.length>3" type="button" class="text-button" @click="expanded=!expanded">{{ expanded?'收起素材':`查看其余 ${items.length-3} 条` }}</button></template><RouterLink class="writing-library-link" to="/writing">我的写作素材 →</RouterLink></details></Teleport><WritingMaterialDetail v-if="selected" :key="selected.id" :material-id="selected.id" :material="selected" :initial-favorite="initialFavorite" @close="selected=undefined" @changed="changed" /></template>
