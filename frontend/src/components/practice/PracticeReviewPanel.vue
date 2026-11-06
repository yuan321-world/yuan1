<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount,onMounted} from 'vue'
import type {ReviewSource} from '@/api/practice-review'
import {usePracticeReview} from '@/stores/practice-review'
import '@/styles/practice-review.css'
const props=defineProps<{source:ReviewSource;compact?:boolean}>(),store=usePracticeReview()
const state=computed(()=>store.state(props.source)),selected=computed(()=>state.value.records.find(r=>r.id===state.value.selected)),busy=computed(()=>state.value.records.some(r=>r.status==='QUEUED'||r.status==='RUNNING'))
const successful=computed(()=>state.value.records.some(r=>r.status==='SUCCEEDED')),tab=ref('notes'),consent=ref(false),consentPanel=ref(false)
let timer:ReturnType<typeof setTimeout>|undefined,alive=true
const levels={GOOD:'较好',FAIR:'尚可',IMPROVE:'待提高'}
const kindLabels:Record<string,string>={grammar:'语法 · Grammar',lexical:'用词 · Word choice',meaning:'语义 · Meaning',style:'表达优化 · Style',structure:'结构 · Structure'}
const budgetMessage=computed(()=>{const r=selected.value;if(!r?.budgetStatus)return '';const amount=typeof r.budgetAmountMicrocny==='number'?`约 ¥${(r.budgetAmountMicrocny/1000000).toFixed(4)}`:'';return r.budgetStatus==='RELEASED'?'本次本地预留已释放，不占用今日额度。':r.budgetStatus==='SETTLED'?`本次按返回用量结算本地额度：${amount}，实际扣费以 DeepSeek 账单为准。`:r.budgetStatus==='UNKNOWN'?`本次费用待确认，暂保留额度${amount}；这不表示已实际扣除该金额。`:`本次额度暂时预留${amount}，收到可信用量后结算。`})
const providerErrors:Record<string,string>={PROVIDER_AUTH:'批改服务鉴权失败，请联系管理员检查配置。',PROVIDER_BALANCE:'DeepSeek 账户余额不足，请联系管理员检查充值情况。',PROVIDER_LIMIT:'批改服务请求过多，稍后可重新申请。',PROVIDER_REQUEST:'批改服务未接受本次请求，请联系管理员检查配置。'}
const failedMessage=computed(()=>providerErrors[selected.value?.errorCode||'']|| (selected.value?.errorCode==='OUTCOME_UNKNOWN'?'上次调用结果未确认。原文已保留，再次申请可能再次计费。':selected.value?.errorCode==='INVALID_OUTPUT'?'本次反馈格式或原文定位未通过校验，未展示评分。':'本次批改未完成，原文已保留。'))
async function refresh(){clearTimeout(timer);timer=undefined;if(!alive)return;if(document.visibilityState!=='hidden')await store.load({...props.source});if(alive&&busy.value)timer=setTimeout(()=>void refresh(),3000)}
function visibility(){if(document.visibilityState==='visible')void refresh();else clearTimeout(timer)}
watch(()=>`${props.source.sourceType}:${props.source.attemptId}:${props.source.slot}`,()=>{consent.value=false;consentPanel.value=false;tab.value='notes';void refresh()},{immediate:true})
watch(busy,v=>{if(v&&!timer)timer=setTimeout(()=>void refresh(),3000)})
onMounted(()=>document.addEventListener('visibilitychange',visibility));onBeforeUnmount(()=>{alive=false;clearTimeout(timer);document.removeEventListener('visibilitychange',visibility)})
async function request(){if(!consent.value){consentPanel.value=true;return}consentPanel.value=false;await store.request({...props.source});if(alive&&busy.value){clearTimeout(timer);timer=setTimeout(()=>void refresh(),3000)}}
</script>
<template><section class="practice-review-panel" aria-label="AI学习反馈" data-testid="ai-review-panel">
 <header :class="{'subjective-tools-row':compact}"><slot name="tools"/><h2 v-if="!compact">AI 学习反馈 <span class="review-ai-label">AI生成 · 学习参考</span></h2><button class="button-primary" :class="{'subjective-ai-button':compact}" :aria-label="state.requesting?'正在申请…':busy?'正在批改…':successful?'本次已批改':selected?.status==='FAILED'?'重新申请批改':'申请批改'" :disabled="state.loading||state.requesting||busy||successful" @click="request">{{state.requesting?'正在申请…':busy?'正在批改…':successful?'本次已批改':selected?.status==='FAILED'?'重新申请批改':compact?'AI 批改':'申请批改'}}</button><slot name="word-count"/></header>
 <slot name="reference"/>
 <h2 v-if="compact&&state.records.length" class="review-result-heading">AI 学习反馈 <span class="review-ai-label">AI生成 · 学习参考</span></h2>
 <div v-if="consentPanel" class="review-consent"><p>将题目和本次已提交作答发送至 DeepSeek 生成学习反馈。参考评分不计入原卷成绩。</p><label><input v-model="consent" type="checkbox">我同意发送本次题目和作答</label><button :disabled="!consent" @click="request">确认申请批改</button><button @click="consentPanel=false">取消</button></div>
 <p v-if="state.error" role="alert" class="review-error">{{state.error}} <button :disabled="state.loading" @click="refresh">读取服务器记录</button></p>
 <p v-if="state.loading&&!state.records.length" role="status">正在读取批改记录…</p>
 <p v-else-if="!state.records.length&&!compact" class="review-muted">主动申请后生成逐句建议；参考范文和译文仍可照常查看。</p>
 <div v-if="state.records.length" class="review-history"><label>批改记录 <select v-model="state.selected" @change="tab='notes'"><option v-for="(r,i) in state.records" :key="r.id" :value="r.id">{{new Date(r.createdAt).toLocaleString()}} · {{r.status==='SUCCEEDED'?'已完成':r.status==='FAILED'?'未完成':'处理中'}} · {{state.records.length-i}}</option></select></label></div>
 <p v-if="selected?.status==='QUEUED'||selected?.status==='RUNNING'" role="status">{{selected.status==='QUEUED'?'已排队，等待批改。':'正在生成反馈，可以离开页面，稍后回来查看。'}}</p>
 <p v-if="selected?.status==='FAILED'" role="alert" class="review-error">{{failedMessage}}</p>
 <p v-if="budgetMessage" class="review-muted review-budget" role="status">{{budgetMessage}}</p>
 <template v-if="selected?.result"><div class="review-summary"><div class="review-score"><span>学习参考分</span><strong>{{selected.result.score}}<small> / 15</small></strong><span>不换算考试分</span></div><div><p>{{selected.result.summaryZh}}</p><p class="review-muted">{{selected.result.summaryEn}}</p><div class="review-dimensions"><span v-for="(d,i) in selected.result.dimensions" :key="i">{{d.name}} · {{levels[d.level]}}</span></div></div></div>
 <div class="review-tabs" role="group" aria-label="反馈内容"><button :aria-pressed="tab==='notes'" @click="tab='notes'">逐句批注 · {{selected.result.annotations.length}}</button><button :aria-pressed="tab==='revised'" @click="tab='revised'">参考修改稿</button><button :aria-pressed="tab==='original'" @click="tab='original'">当次原文</button></div>
 <div v-if="tab==='notes'"><article v-for="(note,i) in selected.result.annotations" :key="i" class="review-annotation"><span class="review-kind">{{note.severity==='SUGGESTION'?'可选优化':'建议修正'}}</span><span class="review-muted">{{kindLabels[note.kind]||note.kind}} · 原文第{{note.occurrence}}处</span><p v-if="note.sourceQuote" class="review-muted">对应原文：{{note.sourceQuote}}</p><p class="review-english"><span :class="{'review-deleted':note.severity==='ERROR'}">{{note.quote}}</span><br><span class="review-added">{{note.replacement}}</span></p><p>{{note.reasonZh}}</p><p class="review-muted">{{note.reasonEn}}</p></article><p v-if="!selected.result.annotations.length" class="review-muted">本次未标出逐句修改项，可结合总评继续对照。</p><article v-for="(note,i) in selected.result.omissions" :key="'omission'+i" class="review-annotation"><h3>信息遗漏</h3><p>{{note.sourceQuote}}</p><p class="review-english">{{note.suggestion}}</p><p>{{note.reasonZh}}</p><p class="review-muted">{{note.reasonEn}}</p></article></div>
 <template v-else-if="tab==='revised'"><p class="review-muted">仅供比较与学习，不会覆盖你的原文。</p><p class="review-english review-fulltext">{{selected.result.revisedText}}</p></template><p v-else class="review-english review-fulltext">{{selected.submittedText}}</p>
 <p class="review-muted review-meta">AI生成的学习建议 · 反馈对应当次提交，后续修改不覆盖本记录。</p></template>
</section></template>
