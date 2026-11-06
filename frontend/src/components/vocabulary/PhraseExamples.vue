<script setup lang="ts">
import {ref,watch} from 'vue'
import type {PhraseExample} from '@/vocabulary/phrase-examples'
const props=defineProps<{text:string}>()
const examples=ref<readonly PhraseExample[]>([]),loading=ref(false)
watch(()=>props.text,async(text,_,cleanup)=>{
  let current=true;cleanup(()=>{current=false})
  examples.value=[];loading.value=true
  try{
    const module=await import('@/vocabulary/phrase-examples')
    if(current)examples.value=module.phraseExamples(text)
  }catch{/* Optional local material must not block phrase lookup or collection. */}
  finally{if(current)loading.value=false}
},{immediate:true})
</script>

<template>
  <section class="ws-phrase-examples" aria-label="词组双语例句" :aria-busy="loading">
    <h3>双语例句</h3>
    <p v-if="loading" class="example-muted" role="status">正在加载例句…</p>
    <p v-else-if="!examples.length" class="example-muted">暂无已收录例句。</p>
    <article v-for="example in examples" :key="example.text" class="ws-phrase-example">
      <p lang="en">{{ example.text }}</p>
      <p lang="zh">{{ example.translationZh }}</p>
      <small v-if="example.attribution" class="example-credit">
        Tatoeba ·
        <a :href="'https://tatoeba.org/en/sentences/show/'+example.attribution.englishId" target="_blank" rel="noopener">{{ example.attribution.englishAuthor }}</a>
        / <a :href="'https://tatoeba.org/en/sentences/show/'+example.attribution.chineseId" target="_blank" rel="noopener">{{ example.attribution.chineseAuthor }}</a>
        · <a href="https://creativecommons.org/licenses/by/2.0/fr/" target="_blank" rel="noopener">CC BY 2.0 FR</a>
      </small>
    </article>
  </section>
</template>

<style scoped>
.ws-phrase-examples{margin-top:16px;padding-top:12px;border-top:1px solid var(--border,#dce1d3)}
h3{margin:0 0 12px;font-size:13px;font-weight:600;color:#788074}
.ws-phrase-example{margin:0 0 14px}
.ws-phrase-example:last-child{margin-bottom:0}
.ws-phrase-example p{margin:0;overflow-wrap:anywhere}
.ws-phrase-example [lang=en]{font-size:16px;line-height:1.55;color:var(--text,#293329)}
.ws-phrase-example [lang=zh]{font-size:13px;line-height:1.6;color:#788074;margin-top:4px}
.example-muted{font-size:13px;color:#788074;margin:0;line-height:1.6}
.example-credit{display:block;font-size:10px;line-height:1.5;color:#788074;margin-top:5px}
.example-credit a{color:inherit;text-underline-offset:2px}
</style>
