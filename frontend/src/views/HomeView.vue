<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import DailyQuote from '@/components/DailyQuote.vue'
import FeatureIcon from '@/components/FeatureIcon.vue'
import { RouterLink } from 'vue-router'
import { readingRecommendation, type ArticleSummary } from '@/api/reading'
const auth = useAuthStore()
const recommended = ref<ArticleSummary | null>(null), recommendationError = ref('')
let recommendationController: AbortController | undefined, generation = 0
async function loadRecommendation() {
  if (!auth.user) { generation++; recommendationController?.abort(); recommended.value = null; recommendationError.value = ''; return }
  recommendationController?.abort(); recommendationController = new AbortController(); const current = ++generation
  try { const result = await readingRecommendation(recommendationController.signal); if (current === generation) { recommended.value = result.article; recommendationError.value = '' } }
  catch (e) { if (current === generation && !(e instanceof DOMException && e.name === 'AbortError')) recommendationError.value = '今日推荐暂不可用，可以先浏览文章列表。' }
}
const coreModules = [
  { icon: 'vocabulary' as const, name: '背单词', state: '积累新词，循环复习', availability: '进入学习与复习 →', to: '/vocabulary' },
  { icon: 'reading' as const, name: '外刊精读', state: '在语境中理解与积累', availability: '进入阅读 →', to: '/reading' },
  { icon: 'practice' as const, name: '专项练习', state: '按题型练习，整卷检验', availability: '进入专项练习 →', to: '/practice' },
]
const auxiliaryModules = [
  { icon: 'mistakes' as const, name: '错词本', state: '还没有学习记录', to: null },
  { icon: 'notes' as const, name: '笔记本', state: '收藏生词，回到原文', to: '/notebook' },
  { icon: 'wall' as const, name: '留言墙', state: '分享心得，互相鼓励', to: '/wall' },
]
function currentDay() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
}
const today = ref(currentDay())
function refreshDay() { const date = currentDay(); if (today.value !== date) { today.value = date; void loadRecommendation() } }
watch(() => auth.user?.id, () => { void loadRecommendation() })
let timer: ReturnType<typeof setInterval>
onMounted(() => { void loadRecommendation(); timer = setInterval(refreshDay, 60000); document.addEventListener('visibilitychange', refreshDay) })
onBeforeUnmount(() => { generation++; recommendationController?.abort(); clearInterval(timer); document.removeEventListener('visibilitychange', refreshDay) })
</script>
<template>
  <section class="approved-home">
    <div class="home-brand">
      <h1>Lingua Loop</h1>
      <p>让每一次积累，都成为进步的起点。</p>
      <span>单词积累 · 外刊阅读 · 专项练习</span>
    </div>
    <DailyQuote />
    <div class="home-actions"><RouterLink class="button-primary" to="/vocabulary">开始今天的复习</RouterLink><RouterLink class="button-secondary" :to="recommended ? `/reading/${recommended.id}` : '/reading'">读今天的文章</RouterLink></div>
    <p v-if="recommendationError" class="home-reading-notice" role="status">{{ recommendationError }}</p>
    <div class="home-modules">
      <div class="core-modules" role="group" aria-label="核心学习模块">
        <component :is="module.to ? RouterLink : 'div'" v-for="module in coreModules" :key="module.name" :to="module.to ?? undefined" class="module-tile core-module" :class="{ 'module-link': module.to }">
          <span class="module-glyph"><FeatureIcon :name="module.icon" /></span>
          <h2>{{ module.name }}</h2><p>{{ module.state }}</p><span class="module-availability">{{ module.availability }}</span>
        </component>
      </div>
      <div class="auxiliary-modules" role="group" aria-label="辅助学习模块">
        <component :is="module.to ? RouterLink : 'div'" v-for="module in auxiliaryModules" :key="module.name" :to="module.to ?? undefined" class="module-tile auxiliary-module" :class="{ 'module-link': module.to }">
          <span class="module-glyph"><FeatureIcon :name="module.icon" /></span><div><h2>{{ module.name }}</h2><p>{{ module.state }}</p></div>
        </component>
      </div>
    </div>

  </section>
</template>
