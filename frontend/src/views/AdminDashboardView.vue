<script setup lang="ts">
import { ElEmpty } from 'element-plus'
import 'element-plus/es/components/empty/style/css'
</script>
<template>
  <section aria-labelledby="admin-data-title">
    <h1 id="admin-data-title">数据管理</h1>
    <p class="admin-description">集中维护文章、词书与题库数据。</p>
    <section class="admin-empty" aria-label="管理端空状态">
      <ElEmpty :image-size="105" description="暂无可管理的数据模块" />
      <p>文章、词书和题库的数据管理将随对应模块开放。</p>
    </section>
  </section>
</template>
