<script setup lang="ts">
import SystemStatusBadge from '@/components/SystemStatusBadge.vue'
</script>
<template>
<section class="home-intro">
  <p class="home-wordmark">Lingua Loop</p>
  <h1>把英语学习连成一个循环。</h1>
  <p class="intro-copy">从阅读中认识新词，在复习中记住它们，再把积累带回阅读与练习。</p>
  <span class="phase-chip"><span class="phase-dot" aria-hidden="true"></span>阶段 0 · 工程基础预览</span>
</section>
<section class="entry-panel" aria-labelledby="entries-title">
  <div class="panel-heading"><h2 id="entries-title">学习入口</h2><p>先搭好入口，再逐步连接真实学习内容</p></div>
  <div class="entry-grid">
    <RouterLink class="entry-item" to="/vocabulary"><span class="entry-glyph" aria-hidden="true">Aa</span><h3>背单词</h3><p>把阅读中的生词带进学习与复习。</p><span class="entry-meta">阶段 3 · 待开发</span></RouterLink>
    <RouterLink class="entry-item" to="/reading"><span class="entry-glyph" aria-hidden="true">Read</span><h3>外刊精读</h3><p>读文章、查词义，收藏值得记住的词。</p><span class="entry-meta">阶段 2 · 待开发</span></RouterLink>
    <RouterLink class="entry-item" to="/practice"><span class="entry-glyph" aria-hidden="true">Q</span><h3>专项练习</h3><p>从四级阅读开始，检验理解与积累。</p><span class="entry-meta">阶段 4 · 待开发</span></RouterLink>
  </div>
</section>
<div class="home-lower">
  <section class="next-panel"><h2>接下来，建立你的学习空间</h2><p>真实账号、阅读内容和复习记录将随各阶段逐步开放。</p><ul class="next-list"><li><RouterLink to="/login">登录与账号</RouterLink></li><li><RouterLink to="/stats">学习统计</RouterLink></li><li><RouterLink to="/admin">管理端预览</RouterLink></li></ul></section>
  <aside class="connection-panel"><h2>开发连接状态</h2><p>这里只展示基础服务连接情况。</p><SystemStatusBadge /></aside>
</div>
<p class="study-hint">当前页面为工程基础预览，学习内容、账号和学习记录尚未接入。</p>
</template>
