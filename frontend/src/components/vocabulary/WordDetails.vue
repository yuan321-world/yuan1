<script setup lang="ts">
import {computed,shallowRef,watch} from 'vue'
import PhraseSaveButton from './PhraseSaveButton.vue'
import type {DictionaryCandidate} from '@/api/dictionary'
import type {WordSource} from '@/api/vocabulary'
import type {SupplementalExamples} from '@/vocabulary/supplemental-examples'
const props = defineProps<{entry:DictionaryCandidate;compact?:boolean;allSenses?:boolean;exampleSource?:WordSource|null}>()
const mainSenses = computed(() => props.compact && !props.allSenses ? props.entry.senses.slice(0, 2) : props.entry.senses)
const supplement = shallowRef<SupplementalExamples>()
const loadingExamples = shallowRef(false)
watch(() => [props.entry.lemma, props.entry.examples] as const, async ([lemma, existing], _, cleanup) => {
  let current = true
  cleanup(() => { current = false })
  supplement.value = undefined
  loadingExamples.value = !existing?.length
  if (!loadingExamples.value) return
  try {
    const {supplementalExamples} = await import('@/vocabulary/supplemental-examples')
    if (current) supplement.value = supplementalExamples(lemma)
  } catch {
    // Optional example material can be unavailable without blocking study.
  } finally {
    if (current) loadingExamples.value = false
  }
}, {immediate:true})
const examples = computed(() => props.entry.examples?.length ? props.entry.examples : supplement.value?.examples)
const mainExamples = computed(() => props.compact ? examples.value?.slice(0, 1) : examples.value)
const findNeighbors = shallowRef<(word: string) => string[]>()
watch(() => props.compact, async (compact) => {
  if (!compact || findNeighbors.value) return
  try {
    findNeighbors.value = (await import('@/vocabulary/spelling-neighbors')).spellingNeighbors
  } catch {
    // The word remains usable if optional spelling suggestions cannot load.
  }
}, {immediate: true})
const neighbors = computed(() => findNeighbors.value?.(props.entry.lemma) ?? [])
const synonyms = computed(() => [...new Set(props.entry.synonyms?.flatMap(group => group.words) ?? [])]
  .filter(word => word.toLowerCase() !== props.entry.lemma.toLowerCase()).slice(0, 8))
</script>
<template>
  <section class="vl-dictionary" aria-label="完整词条">
    <template v-if="!compact"><h2 lang="en">{{ entry.lemma }}</h2><p class="vl-phonetic">{{ entry.phonetic }}</p></template>
    <div :class="{ 'vl-dictionary-summary': compact }">
      <div class="vl-senses">
        <p v-for="(sense,i) in mainSenses" :key="i" class="vl-definition-meaning"><span class="vl-part-of-speech">{{ sense.partOfSpeech }}</span> {{ sense.definitionZh }}</p>
      </div>
      <div class="vl-fixed-examples">
        <h3>{{ compact ? '例句' : '固定双语例句' }}</h3>
        <blockquote v-for="(example,i) in mainExamples" :key="i" class="vl-example"><p lang="en">{{ example.text }}</p><span>{{ example.translationZh }}</span></blockquote>
        <small v-if="supplement?.attribution" class="vl-example-credit">
          Tatoeba ·
          <a :href="'https://tatoeba.org/en/sentences/show/' + supplement.attribution.englishId" target="_blank" rel="noopener">{{ supplement.attribution.englishAuthor }}</a>
          /
          <a :href="'https://tatoeba.org/en/sentences/show/' + supplement.attribution.chineseId" target="_blank" rel="noopener">{{ supplement.attribution.chineseAuthor }}</a>
          · <a href="https://creativecommons.org/licenses/by/2.0/fr/" target="_blank" rel="noopener">CC BY 2.0 FR</a>
        </small>
        <p v-if="loadingExamples && !exampleSource" class="vl-muted">正在加载例句…</p>
        <blockquote v-if="compact && !mainExamples?.length && exampleSource" class="vl-example">
          <p lang="en">{{ exampleSource.sentence }}</p>
          <RouterLink :to="{path: `/reading/${exampleSource.articleId}`,query:{version:exampleSource.articleVersionId,sentence:exampleSource.sentenceKey}}" target="_blank">阅读原句 · {{ exampleSource.title }} ↗</RouterLink>
        </blockquote>
        <p v-if="!loadingExamples && !mainExamples?.length && (!compact || !exampleSource)" class="vl-muted">暂无已收录例句。</p>
      </div>
    </div>
    <slot/>
    <details v-if="compact && (examples?.length ?? 0) > 1" class="vl-dictionary-more">
      <summary>更多例句</summary>
      <blockquote v-for="(example,i) in examples?.slice(1)" :key="i" class="vl-example"><p lang="en">{{ example.text }}</p><span>{{ example.translationZh }}</span></blockquote>
    </details>
    <component :is="compact ? 'details' : 'div'" v-if="entry.phrases?.length" :class="{ 'vl-dictionary-more': compact }">
      <summary v-if="compact">常用短语</summary><h3 v-else>常用短语</h3>
      <ul class="vl-phrase-list">
        <li v-for="phrase in entry.phrases" :key="phrase.text">
          <div><strong lang="en">{{ phrase.text }}</strong><p>{{ phrase.translationZh }}</p></div>
          <PhraseSaveButton :text="phrase.text" :meaning="phrase.translationZh" :note="'词典搭配：'+entry.lemma"/>
        </li>
      </ul>
    </component>
    <div v-if="compact && (neighbors.length || synonyms.length)" class="vl-related-words">
      <section v-if="neighbors.length" aria-label="形近词"><h3>形近词</h3><ul><li v-for="word in neighbors" :key="word" lang="en">{{ word }}</li></ul></section>
      <section v-if="synonyms.length" aria-label="近义词"><h3>近义词</h3><ul><li v-for="word in synonyms" :key="word" lang="en">{{ word }}</li></ul></section>
    </div>
    <div v-if="!compact && entry.synonyms?.length">
      <h3>分义近义词</h3>
      <p v-for="(group,i) in entry.synonyms" :key="i">{{ group.partOfSpeech }} {{ group.meaningEn }} · <span lang="en">{{ group.words.join(' / ') }}</span></p>
    </div>
  </section>
</template>
