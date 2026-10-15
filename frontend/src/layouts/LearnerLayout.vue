<script setup lang="ts">
import AppNavigation from '@/components/AppNavigation.vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
const authPage = computed(() => route.path === '/login' || route.path === '/register')
</script>
<template>
  <div :class="{ 'auth-layout': authPage }">
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <header v-if="authPage" class="auth-header">
    <RouterLink class="brand" to="/" aria-label="Lingua Loop 首页"><span class="brand-mark" aria-hidden="true">L</span>Lingua Loop</RouterLink>
  </header>
  <AppNavigation v-else />
  <main id="main-content" :class="authPage ? 'auth-main' : 'page-content'" tabindex="-1">
    <RouterView />
    <footer v-if="!authPage" class="site-footer"><span>Lingua Loop · 一点积累，每天循环。</span><span>工程基础预览</span></footer>
  </main>
  </div>
</template>
