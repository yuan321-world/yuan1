<script setup lang="ts">
defineProps<{ title: string; stage: number; description: string; glyph: string }>()
</script>
<template>
  <section class="pending-page">
    <div class="pending-sheet">
      <span class="pending-symbol" aria-hidden="true">{{ glyph }}</span>
      <span class="phase-chip">阶段 {{ stage }}</span>
      <h1>{{ title }}</h1>
      <p class="pending-state">待开发</p>
      <p class="pending-description">{{ description }}</p>
      <RouterLink class="button-primary" to="/">返回首页</RouterLink>
    </div>
  </section>
</template>
