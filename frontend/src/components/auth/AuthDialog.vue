<script setup lang="ts">
import { computed, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElDialog } from 'element-plus'
import 'element-plus/es/components/dialog/style/css'
import AuthForm from './AuthForm.vue'
import PasswordResetForm from './PasswordResetForm.vue'
import { useAuthDialogStore, type AuthMode } from '@/stores/auth-dialog'
import { useAuthStore } from '@/stores/auth'
import { safeRedirect } from '@/router/safe-redirect'
import { useArticleFavorites } from '@/stores/article-favorites'
const dialog = useAuthDialogStore(), auth = useAuthStore(), router = useRouter(), route = useRoute()
const title = computed(() => dialog.mode === 'register' ? '创建账号' : dialog.mode === 'reset' ? '重置密码' : '欢迎回来')
function focusForm() { document.querySelector<HTMLInputElement>('.auth-dialog input:not(:disabled)')?.focus({ preventScroll: true }) }
function trapTab(event: KeyboardEvent) {
  if (event.key !== 'Tab' || event.altKey || event.ctrlKey || event.metaKey) return
  const panel = event.currentTarget as HTMLElement
  const elements = Array.from(panel.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), a[href], select:not(:disabled), textarea:not(:disabled), [tabindex="0"]'))
    .filter(element => element.tabIndex >= 0 && element.getClientRects().length > 0)
  const first = elements[0], last = elements.at(-1)
  // The first Tab after pointer focus can otherwise escape Element Plus's focus boundary.
  if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
  else if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
}
async function switchMode(mode: AuthMode, message = '') {
  if (dialog.busy && !message) return
  dialog.switchMode(mode, message); await nextTick(); focusForm()
}
async function success() {
  const intent = dialog.articleIntent
  const destination = dialog.target ? safeRedirect(dialog.target, auth.isAdmin) : auth.isAdmin ? '/admin' : route.fullPath
  dialog.complete()
  if (destination !== route.fullPath) await router.replace(destination)
  if (intent && !auth.isAdmin && intent.route === route.fullPath && destination === route.fullPath) await useArticleFavorites().set(intent.articleId, true)
}
watch(() => auth.user?.id, (value, previous) => {
  // Cross-tab identity changes must not retain another identity's credential drafts.
  if (previous && value !== previous) dialog.complete()
})
</script>
<template>
  <ElDialog :model-value="dialog.open" class="auth-dialog" :title="title" width="480px" align-center
    :show-close="false" :close-on-click-modal="false" :close-on-press-escape="!dialog.busy"
    :before-close="dialog.close" @update:model-value="value => { if (!value) dialog.close() }"
    @opened="focusForm" @closed="dialog.closed" @keydown.capture="trapTab">
    <template #header="{ titleId }">
      <span :id="titleId" class="auth-dialog-label">{{ title }}</span>
      <div class="auth-dialog-header"><span class="brand"><span class="brand-mark" aria-hidden="true">L</span>Lingua Loop</span>
        <button type="button" class="auth-dialog-close" aria-label="关闭认证窗口" :disabled="dialog.busy" @click="dialog.close"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="m6 6 12 12M6 18 18 6"/></svg></button>
      </div>
    </template>
    <div :key="dialog.session">
      <KeepAlive>
        <component v-if="dialog.open" :is="dialog.mode === 'reset' ? PasswordResetForm : AuthForm" :key="dialog.mode" embedded
          :mode="dialog.mode === 'reset' ? undefined : dialog.mode" :message="dialog.message"
          @switch="switchMode" @success="success" @busy="value => dialog.busy = value" />
      </KeepAlive>
    </div>
  </ElDialog>
</template>
