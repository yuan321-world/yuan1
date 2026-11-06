<script setup lang="ts">
import type {WritingContent} from '@/api/writing'
defineProps<{content:WritingContent}>()
</script>
<template><p class="writing-expression" lang="en">{{ content.expression }}</p><p class="writing-meaning">{{ content.meaningZh }}</p><section><h3>重点词组</h3><ul><li v-for="phrase in content.keyPhrases" :key="phrase" lang="en">{{ phrase }}</li></ul></section><section><h3>句型结构</h3><p>{{ content.structure }}</p></section><section><h3>完整例句</h3><p class="writing-example" lang="en">{{ content.exampleEn }}</p><p>{{ content.exampleZh }}</p></section><section><h3>适用写作场景</h3><p>{{ content.scenarios.join('、') }}</p></section></template>
