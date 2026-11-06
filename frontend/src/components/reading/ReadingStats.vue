<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import {RouterLink} from 'vue-router'
import {checkinWeek,type CheckinWeek} from '@/api/checkins'
import {errorMessage} from '@/api/identity'
import {useAuthStore} from '@/stores/auth'
import {useReadingRefresh} from '@/reading/useReadingRefresh'
defineProps<{title?:string}>()
const auth=useAuthStore(),data=ref<CheckinWeek>(),loading=ref(false),error=ref('')
const weekdays=['一','二','三','四','五','六','日']
let sequence=0,controller:AbortController|undefined
async function load(){const current=++sequence;controller?.abort();controller=new AbortController();loading.value=true;error.value='';try{const result=await checkinWeek(controller.signal);if(current===sequence)data.value=result}catch(e){if(current===sequence&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(current===sequence)loading.value=false}}
watch(()=>auth.user?.id,()=>{data.value=undefined;if(auth.user)void load()},{immediate:true})
useReadingRefresh(load,()=>!!auth.user)
onBeforeUnmount(()=>{sequence++;controller?.abort()})
</script>
<template><section class="reading-page learning-stats" :aria-busy="loading"><header class="reading-header"><h1>{{title||'阅读学习记录'}}</h1><p>把每一次认真阅读，留在自己的学习记录里。</p></header><p v-if="loading&&!data" class="reading-empty" role="status">正在加载学习记录…</p><div v-else-if="error" class="reading-notice" role="alert"><p>{{ error }}</p><button class="button-secondary" @click="load">重试统计</button></div><template v-else-if="data"><section class="stats-week"><div class="checkin-card-heading"><h2>本周阅读打卡</h2><strong>{{ data.todayChecked?'今日已打卡':'今日待完成' }}</strong></div><p class="reader-rail-note">{{ data.weekStart }} 起 · 上海时间 · 阅读完成并提交全部配套练习后自动记录</p><ol class="checkin-week"><li v-for="(day,index) in data.days" :key="day.date" class="checkin-day" :class="{'is-checked':day.checked}" :aria-current="day.date===data.today?'date':undefined"><span>周{{ weekdays[index] }}</span><strong>{{ Number(day.date.slice(-2)) }}</strong><small>{{ day.checked?'已打卡':day.date===data.today?'今天':'—' }}</small></li></ol></section><dl class="stats-metrics"><div><dt>累计打卡</dt><dd>{{ data.totalDays }} <small>天</small></dd></div><div><dt>已达成文章</dt><dd>{{ data.qualifiedArticles }} <small>篇</small></dd></div><div><dt>已读完版本</dt><dd>{{ data.completedReadings }} <small>篇</small></dd></div><div><dt>完成练习</dt><dd>{{ data.completedAttempts }} <small>轮</small></dd></div></dl><section class="stats-note"><h2>{{ data.totalDays?'继续下一篇积累':'从一篇阅读开始' }}</h2><p>标记读完，并完成同一文章版本的全部练习题，即可达成。答错也计入学习，每天最多计一天；同一文章仅贡献一次。</p><p>同日完成多篇会保留每篇记录；旧成绩不自动补打卡。词汇学习单独统计，不计入阅读打卡。</p><RouterLink class="button-primary" to="/reading">去阅读文章</RouterLink></section></template></section></template>
