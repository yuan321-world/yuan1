<script setup lang="ts">
import type {PaperAttempt} from '@/api/practice-paper'
import {slotLabels} from '@/api/practice-paper'
defineProps<{attempt:PaperAttempt}>();defineEmits<{review:[];restart:[];mistakes:[]}>()
</script>
<template><section class="paper-panel"><h1>本次整卷已完成</h1><p class="paper-muted">参考内容已解锁，可以逐部分回看。</p><div v-for="c in attempt.components" :key="c.slot" class="paper-result-row"><strong>{{slotLabels[c.slot]}}</strong><span v-if="c.objective">正确 {{c.objective.correctCount}} / {{c.objective.total}} · 未答 {{c.objective.answers.filter(a=>!a.submittedChoice).length}} 题</span><span v-else>{{c.wordCount}}词 · {{c.text?'未批改':'未作答'}}</span></div><p class="paper-muted">客观题按当前冻结版本的参考判分；写作与翻译未批改。不折算四级标准分或整卷总分。</p><div class="paper-actions"><button class="paper-primary" @click="$emit('review')">查看作答与参考</button><button v-if="attempt.components.some(c=>c.objective&&(c.objective.correctCount||0)<c.objective.total)" @click="$emit('mistakes')">复习本卷错题</button><button @click="$emit('restart')">重新练习（保留本次记录）</button></div></section></template>
