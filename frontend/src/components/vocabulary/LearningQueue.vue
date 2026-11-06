<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Session } from '@/api/vocabulary-learning'

const props = defineProps<{ session: Session }>()
const queueList = ref<HTMLOListElement>()
watch(() => props.session.queue[0]?.id, () => {
  if (queueList.value) queueList.value.scrollTop = 0
})
</script>

<template>
  <aside class="vl-queue">
    <header>
      <h2>本轮待学顺序</h2>
      <small>{{ session.queue.length }} 项</small>
    </header>
    <ol ref="queueList" tabindex="0" aria-label="本轮待学顺序">
      <li v-for="(item, index) in session.queue" :key="item.id"
        :class="{ 'is-current': item.current }" :aria-current="item.current ? 'step' : undefined">
        <span>{{ session.kind === 'NEW' && session.mode === 'SPELLING' ? '单词 ' + (index + 1) : item.meaning || '单词 ' + (index + 1) }}</span>
        <small>{{ item.current ? '当前' : item.failures ? '再练' : '待学' }}</small>
      </li>
    </ol>
    <p class="vl-muted">
      {{ session.mode === 'SPELLING' ? '输错保留正确前缀；辅助完成后回队再练。' : '不认识或选错的词，看完释义后移到队尾。' }}
      可随时暂停，回来继续。
    </p>
  </aside>
</template>

<style scoped>
.vl-queue {
  min-width: 0;
  padding: 16px;
}
.vl-queue header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}
.vl-queue h2 {
  margin: 0;
  font-size: 16px;
}
.vl-queue ol {
  display: grid;
  gap: 4px;
  max-height: min(16rem, 45vh);
  margin: 10px 0 8px;
  overflow-y: auto;
  scrollbar-gutter: stable;
}
.vl-queue ol:focus-visible {
  outline: 2px solid var(--vl-green);
  outline-offset: 2px;
}
.vl-queue li {
  max-width: none;
  margin: 0;
  padding: 5px 8px;
  border-color: transparent;
  border-radius: 7px;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.vl-queue .vl-muted {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}
</style>
