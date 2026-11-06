<script setup lang="ts">
import type { Session } from '@/api/vocabulary-learning'

defineProps<{ session: Session }>()
defineEmits<{ home: [] }>()

function meaning(item: Session['results'][number]) {
  const senses = item.definition?.senses.filter(sense => sense.definitionZh?.trim()) ?? []
  return senses.slice(0, 2)
    .map(sense => [sense.partOfSpeech, sense.definitionZh].filter(Boolean).join(' '))
    .join('；')
}

function nextReview(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', {
    timeZone: 'Asia/Shanghai', month: 'numeric', day: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }) : '—'
}
</script>

<template>
  <section class="vl-card vl-finished">
    <span class="vl-check">✓</span>
    <h1>{{ session.results.some(item => !item.outcome) ? '这一轮，已结束。' : '这一轮，全部通过了。' }}</h1>
    <p class="vl-muted">{{ session.kind === 'REVIEW' ? '到期复习' : '新词学习' }} · {{ session.passed }} 词，每个词只计一次。</p>
    <ul class="vl-result-list">
      <li v-for="(item, index) in session.results" :key="index">
        <div class="vl-result-word">
          <strong lang="en">{{ item.lemma }}</strong>
          <p class="vl-result-meaning">{{ meaning(item) || '本轮记录暂无中文释义' }}</p>
        </div>
        <div class="vl-result-schedule">
          <template v-if="!item.outcome"><span>手动熟练 · 已跳过</span><small>不计学习通过</small></template>
          <template v-else>
            <span>下次复习 {{ nextReview(item.outcome.dueAt) }}</span>
            <small v-if="item.failures">本轮出错 · 次日再练</small>
          </template>
        </div>
      </li>
    </ul>
    <p class="vl-muted">本轮完成记录已保存。词汇练习不计入阅读打卡。</p>
    <button class="vl-primary" @click="$emit('home')">返回背单词</button>
  </section>
</template>

<style scoped>
.vl-finished > .vl-check {
  display: none;
}
.vl-finished > h1 {
  margin: 0 0 6px;
  font-size: clamp(22px, 3vw, 28px);
}
.vl-finished > .vl-muted {
  margin: 6px 0;
  font-size: 13px;
}
.vl-finished .vl-result-list {
  margin: 14px 0;
}
.vl-result-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px 24px;
  padding: 10px 0;
}
.vl-result-word {
  min-width: 0;
}
.vl-result-word strong {
  font-size: 22px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}
.vl-result-meaning {
  margin: 5px 0 0;
  color: var(--vl-soft);
  font-size: 14px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}
.vl-result-schedule {
  display: grid;
  gap: 4px;
  text-align: right;
  font-size: 12px;
  color: var(--vl-soft);
}
.vl-result-schedule small {
  color: var(--vl-green);
}
@media (max-width: 520px) {
  .vl-result-list li {
    grid-template-columns: minmax(0, 1fr);
    gap: 7px;
  }
  .vl-result-schedule {
    text-align: left;
  }
}
</style>
