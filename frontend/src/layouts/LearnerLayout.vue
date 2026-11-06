<script setup lang="ts">
import AppNavigation from '@/components/AppNavigation.vue'
import WritingLoginContinuation from '@/components/reading/WritingLoginContinuation.vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
const route = useRoute()
</script>
<template>
  <div>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <AppNavigation />
  <main id="main-content" :class="['page-content', { 'home-content': route.path === '/', 'wall-content': route.path === '/wall', 'reading-content': route.path.startsWith('/reading/'), 'practice-study-content': route.path.startsWith('/practice') }]" tabindex="-1">
    <RouterView :key="auth.user?.id ?? 'anonymous'" />
    <WritingLoginContinuation />
    <footer class="site-footer"><span>Lingua Loop · 一点积累，每天循环。</span></footer>
  </main>
  </div>
</template>
