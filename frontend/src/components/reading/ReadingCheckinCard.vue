<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import {RouterLink} from 'vue-router'
import {articleCheckin,type CheckinStatus} from '@/api/checkins'
import {errorMessage} from '@/api/identity'
import {useAuthStore} from '@/stores/auth'
import {useReadingRefresh} from '@/reading/useReadingRefresh'
const props=defineProps<{article:string;version:string;revision:number;readStatus?:string}>(),auth=useAuthStore()
const state=ref<CheckinStatus>(),error=ref(''),loading=ref(false)
let sequence=0,controller:AbortController|undefined
async function load(){const current=++sequence;controller?.abort();controller=new AbortController();error.value='';if(!auth.user){state.value=undefined;loading.value=false;return}loading.value=true;try{const data=await articleCheckin(props.article,props.version,controller.signal);if(current===sequence)state.value=data}catch(e){if(current===sequence&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(current===sequence)loading.value=false}}
watch(()=>[props.article,props.version,props.revision,props.readStatus,auth.user?.id],()=>{state.value=undefined;void load()},{immediate:true})
useReadingRefresh(load,()=>!!auth.user)
onBeforeUnmount(()=>{sequence++;controller?.abort()})
</script>
<template><section v-if="auth.user" class="reading-checkin-card" :aria-busy="loading" aria-label="阅读打卡条件"><div class="checkin-card-heading"><h2>阅读打卡</h2><RouterLink to="/reading?records=1">阅读记录 →</RouterLink></div><p v-if="loading&&!state" role="status">正在核对学习记录…</p><div v-else-if="error" role="alert"><p>{{ error }}</p><button class="button-secondary" @click="load">重试打卡状态</button></div><template v-else-if="state"><p class="checkin-conditions"><span :class="{fulfilled:state.readCompleted}">{{ state.readCompleted?'阅读已完成':'阅读待完成' }}</span><span :class="{fulfilled:state.practiceCompleted}">{{ state.practiceCompleted?'练习已完成':state.hasQuiz?'练习待完成':'暂无配套练习' }}</span></p><p role="status"><strong>{{ state.articleQualified?'本篇已达成':state.readCompleted&&state.practiceCompleted?'两项已完成，历史记录不补打卡':'完成阅读与全部配套练习后自动打卡' }}</strong><span v-if="state.articleQualified"> · {{ state.qualifiedDate }}</span></p><p v-if="state.todayChecked" class="checkin-today">今日已打卡<span v-if="state.articleQualified">，本篇学习记录已保存。</span></p><p v-else class="reader-rail-note">今日尚未打卡。同篇只计一次，重做不增加打卡天数。</p><p v-if="!state.hasQuiz&&!state.practiceCompleted" class="reader-rail-note">本篇可正常标记读完；配套练习发布后，完成练习才能达成打卡。</p></template></section></template>
