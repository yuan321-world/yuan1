<script setup lang="ts">
import AppNavigation from '@/components/AppNavigation.vue'
</script>
<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <AppNavigation />
  <main id="main-content" class="page-content" tabindex="-1">
    <RouterView />
    <footer class="site-footer"><span>Lingua Loop · 一点积累，每天循环。</span><span>工程基础预览</span></footer>
  </main>
</template>
