<script setup lang="ts">
import type { ContentDocument } from '@/api/admin-articles'
defineProps<{ content: ContentDocument; translations?: Record<string, string>; bilingual?: boolean }>()
</script>
<template><div class="article-body" lang="en"><template v-for="paragraph in content.paragraphs" :key="paragraph.key"><p :data-paragraph="paragraph.key"><template v-for="(sentence, index) in paragraph.sentences" :key="sentence.key"><span :id="sentence.key" :data-sentence="sentence.key">{{ sentence.text }}</span>{{ index + 1 < paragraph.sentences.length ? ' ' : '' }}</template></p><p v-if="bilingual && translations?.[paragraph.key]" class="paragraph-translation" lang="zh-CN">{{ translations[paragraph.key] }}</p></template></div></template>
