<script setup lang="ts">
import {computed} from 'vue'
const props=withDefaults(defineProps<{phonetic?:string|null;ukLabel?:string;usLabel?:string}>(),{ukLabel:'英音',usLabel:'美音'})
const emit=defineEmits<{speak:[locale:'en-GB'|'en-US']}>()
const phonetic=computed(()=>{
  const value=props.phonetic?.trim().replace(/^\/(.*)\/$/,'$1').trim()
  return value?`/${value}/`:''
})
const accents=computed(()=>[{code:'UK',lang:'en-GB' as const,label:props.ukLabel},{code:'US',lang:'en-US' as const,label:props.usLabel}])
</script>

<template>
  <div class="pronunciation-controls">
    <button v-for="accent in accents" :key="accent.code" class="pronunciation-control vl-pronunciation" type="button" :aria-label="accent.label" :title="accent.label+'（浏览器合成语音，实际口音取决于本机语音库）'+(phonetic?'；音标采用词典通用标注':'')" @click="emit('speak',accent.lang)">
      <b>{{ accent.code }}</b><span v-if="phonetic" lang="en">{{ phonetic }}</span>
      <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 10h4l4-4v12l-4-4H4zM16 9a4 4 0 0 1 0 6m2-9a8 8 0 0 1 0 12"/></svg>
    </button>
  </div>
</template>

<style scoped>
.pronunciation-controls{display:flex;flex-wrap:wrap;align-items:center;gap:4px 12px;min-width:0}
.pronunciation-controls>button.pronunciation-control{display:inline-flex;align-items:center;gap:5px;min-width:0;min-height:30px;padding:3px 5px;border:0;border-radius:6px;background:transparent;color:var(--ink-soft,#61685d);font:inherit;font-size:13px;line-height:1.4;cursor:pointer}
.pronunciation-control b{font-size:12px;font-weight:700;color:var(--muted,#788074)}
.pronunciation-control span{overflow-wrap:anywhere}
.pronunciation-control svg{width:15px;height:15px;flex-shrink:0;color:var(--muted,#788074)}
.pronunciation-controls>button.pronunciation-control:hover{background:var(--accent-soft,#edf1e6)}
.pronunciation-controls>button.pronunciation-control:focus-visible{outline:2px solid #7e9867;outline-offset:2px}
</style>
