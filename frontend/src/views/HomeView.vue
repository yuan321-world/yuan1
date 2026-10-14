<script setup lang="ts">
import SystemStatusBadge from '@/components/SystemStatusBadge.vue'
import DailyQuote from '@/components/DailyQuote.vue'
const modules = [
  { glyph: 'Aa', name: '背单词', state: '学习与复习尚未开放' },
  { glyph: '×', name: '错词本', state: '还没有学习记录' },
  { glyph: '▣', name: '笔记本', state: '生词收藏尚未开放' },
  { glyph: '▤', name: '外刊精读', state: '文章阅读尚未开放' },
  { glyph: '◎', name: '分题型专练', state: '阅读训练尚未开放' },
  { glyph: '✎', name: '留言墙', state: '暂未开放' },
]
</script>
<template>
<section class="approved-home">
  <div class="home-brand"><h1>Lingua Loop</h1><p>智能英语学习平台<br><span>背单词、外刊精读、四六级刷题，三个模块共用一个生词队列</span></p></div>
  <DailyQuote />
  <div class="home-actions"><button class="button-primary" disabled>开始今天的复习</button><RouterLink class="button-secondary" to="/reading">读今天的文章</RouterLink></div>
  <p class="home-status">学习记录将在账号与学习功能开放后显示</p>
  <div class="home-modules"><div v-for="module in modules" :key="module.name" class="module-tile"><span class="module-glyph" aria-hidden="true">{{ module.glyph }}</span><h2>{{ module.name }}</h2><p>{{ module.state }}</p></div></div>
  <div class="home-records">
    <section class="today-plan"><h2>今日计划</h2><p>学习与复习开放后，在这里查看每天的任务。</p><span class="record-empty">暂无学习任务</span></section>
    <section class="week-checkin"><h2>本周打卡</h2><p>完成学习后，记录每一天的积累。</p><div class="week-days" aria-label="暂无打卡记录"><span v-for="day in ['一','二','三','四','五','六','日']" :key="day">{{ day }}<b>·</b></span></div></section>
  </div>
  <aside class="connection-panel home-connection"><h2>开发连接状态</h2><SystemStatusBadge /></aside>
</section>
</template>
