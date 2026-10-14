<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { quotes } from '@/data/dailyQuotes'
const dateKey = () => {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
  const fields = Object.fromEntries(parts.map(part => [part.type, part.value]))
  return fields.year + '-' + fields.month + '-' + fields.day
}
const today = ref(dateKey())
const quote = computed(() => quotes[Math.floor(Date.parse(today.value + 'T00:00:00Z') / 86400000) % quotes.length]!)
let timer: ReturnType<typeof setInterval>
function refreshDate() { today.value = dateKey() }
onMounted(() => { timer = setInterval(refreshDate, 60000); document.addEventListener('visibilitychange', refreshDate) })
onUnmounted(() => { clearInterval(timer); document.removeEventListener('visibilitychange', refreshDate) })
</script>
<template>
<blockquote class="daily-quote" :cite="quote.source.url" aria-label="英语名言">
  <div class="daily-quote-heading"><span>每日一句</span><time :datetime="today">{{ today.slice(5).replace('-', '月') }}日</time></div>
  <p class="daily-quote-english" lang="en">{{ quote.english }}</p>
  <p class="daily-quote-chinese">{{ quote.chinese }}</p>
  <footer>{{ quote.author }} <a :href="quote.source.url" target="_blank" rel="noopener noreferrer" :title="quote.source.speaker + '（作品人物台词）'">{{ quote.source.title }}</a></footer>
</blockquote>
</template>
