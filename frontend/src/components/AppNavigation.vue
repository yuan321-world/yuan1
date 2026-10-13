<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { learnerNavigation } from '@/router/navigation'

const open = ref(false)
const toggle = ref<HTMLButtonElement>()
const route = useRoute()
watch(() => route.fullPath, () => { open.value = false })
function handleEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) {
    open.value = false
    toggle.value?.focus()
  }
}
onMounted(() => document.addEventListener('keydown', handleEscape))
onUnmounted(() => document.removeEventListener('keydown', handleEscape))
</script>

<template>
  <header class="topbar">
    <div class="nav-shell">
      <RouterLink class="brand" to="/" aria-label="Lingua Loop 首页"><span class="brand-mark" aria-hidden="true">L</span>Lingua Loop</RouterLink>
      <button ref="toggle" class="menu-toggle" type="button" :aria-expanded="open" aria-controls="learner-navigation" :aria-label="open ? '关闭导航' : '打开导航'" @click="open = !open">{{ open ? '关闭' : '菜单' }}</button>
      <nav id="learner-navigation" class="learner-nav" :class="{ 'is-open': open }" aria-label="学习导航">
        <RouterLink v-for="item in learnerNavigation" :key="item.to" :to="item.to" class="nav-link" :class="{ 'admin-link': item.to === '/admin' }" @click="open = false">{{ item.label }}</RouterLink>
      </nav>
    </div>
  </header>
</template>
