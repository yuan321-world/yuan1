<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getSystemStatus } from '../api/system'

type BadgeState = 'loading' | 'ready' | 'unavailable'

const state = ref<BadgeState>('loading')
let activeController: AbortController | null = null
let requestVersion = 0

async function checkStatus() {
  const thisRequest = ++requestVersion
  activeController?.abort()

  const controller = new AbortController()
  activeController = controller
  state.value = 'loading'

  try {
    const response = await getSystemStatus(controller.signal)
    if (thisRequest === requestVersion) {
      state.value = response.status === 'UP' ? 'ready' : 'unavailable'
    }
  } catch {
    if (thisRequest === requestVersion) state.value = 'unavailable'
  } finally {
    if (thisRequest === requestVersion) activeController = null
  }
}

onMounted(() => {
  void checkStatus()
})

onBeforeUnmount(() => {
  requestVersion += 1
  activeController?.abort()
  activeController = null
})
</script>

<template>
  <div class="system-status-badge" aria-live="polite" aria-atomic="true">
    <button
      class="system-status-badge__button"
      type="button"
      :aria-label="state === 'loading' ? '正在检查服务状态，点击重新检查' : state === 'ready' ? '连接正常，点击重新检查' : undefined"
      @click="checkStatus"
    >
      <template v-if="state === 'loading'">正在检查服务…</template>
      <template v-else-if="state === 'ready'">服务连接正常</template>
      <template v-else>服务暂不可用，点击重试</template>
    </button>
  </div>
</template>

<style scoped>
.system-status-badge__button {
  font: inherit;
  color: inherit;
  cursor: pointer;
  background: var(--surface-2);
  border: 1px solid var(--line-strong);
  border-radius: var(--r-pill);
  padding: 8px 14px;
}
</style>
