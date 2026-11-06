<script setup lang="ts">
import type {PublicationCheck} from '@/api/admin-practice'
import type {PracticeQuestionInput} from '@/api/practice'
defineProps<{checks:PublicationCheck[];questions:PracticeQuestionInput[]}>();const emit=defineEmits<{jump:[check:PublicationCheck]}>()
</script>
<template><section class="practice-panel practice-checks" id="practice-checks"><h2 tabindex="-1">发布检查</h2><p>{{checks.length?'还有 '+checks.length+' 项待处理':'内容检查完成，可确认发布'}}</p><button v-for="(item,n) in checks" :key="item.path+n" class="practice-check-item" @click="emit('jump',item)">{{item.questionKey?'第 '+(questions.findIndex(q=>q.key===item.questionKey)+(questions[0]?.type==='MATCH'?36:1))+' 题 · ':''}}{{item.message}} →</button><p class="practice-hint">检查题干/空位、选项/词库或分段原文、有效答案、解析、真题来源与时间，以及每题和文章来源校对。服务端发布前会再次检查。</p></section></template>
