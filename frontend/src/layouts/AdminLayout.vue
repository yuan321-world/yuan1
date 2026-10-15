<script setup lang="ts">
import { ElEmpty } from 'element-plus'
import 'element-plus/es/components/empty/style/css'
</script>
<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink class="brand" to="/" aria-label="Lingua Loop 首页"><span class="brand-mark" aria-hidden="true">L</span>Lingua Loop</RouterLink>
      <p class="admin-caption">内容与学习资源管理</p>
      <div class="admin-current">管理员</div>
      <RouterLink class="admin-return" to="/">返回学习端</RouterLink>
    </aside>
    <main id="main-content" class="admin-content" tabindex="-1">
      <div class="admin-topline">Lingua Loop / 管理端</div>
      <h1>管理端</h1>
      <p class="admin-description">这里将用于维护文章、词书与题库。你的管理员身份已由服务端验证，内容管理随对应阶段开放。</p>
      <section class="admin-empty" aria-label="管理端空状态">
        <ElEmpty :image-size="105" description="管理功能尚未开放" />
        <p>文章、词书和题库将随对应学习阶段接入。</p>
      </section>
    </main>
  </div>
</template>
