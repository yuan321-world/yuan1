<script setup lang="ts">
defineProps<{disabled?:boolean}>()
defineEmits<{click:[event:MouseEvent]}>()
</script>
<template>
 <button class="dictionary-search-button" type="button" aria-label="查单词" title="查单词" :disabled="disabled" @click="$emit('click',$event)"><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m15.5 15.5 4.5 4.5"/></svg></button>
</template>
<style scoped>
button.dictionary-search-button{display:inline-flex;align-items:center;justify-content:center;width:46px;height:46px;min-width:46px;padding:0;border:1px solid var(--line,#dce1d3);border-radius:15px;background:var(--surface,#faf9f2);color:var(--accent,#587344);cursor:pointer;transition:background-color 160ms ease}
.dictionary-search-button svg{width:21px;height:21px;transition:transform 160ms ease}
.dictionary-search-button:hover:not(:disabled){background:var(--accent-soft,#edf1e6)}.dictionary-search-button:hover:not(:disabled) svg{transform:scale(1.08)}.dictionary-search-button:focus-visible{outline:2px solid var(--accent,#587344);outline-offset:3px}.dictionary-search-button:disabled{opacity:.5;cursor:default}
@media(prefers-reduced-motion:reduce){.dictionary-search-button,.dictionary-search-button svg{transition:none}}
</style>
