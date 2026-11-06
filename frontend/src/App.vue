<script setup lang="ts">
import AuthDialog from '@/components/auth/AuthDialog.vue'
import { useAuthDialogStore } from '@/stores/auth-dialog'
import { navigationLoadFailure, retryFailedNavigation } from '@/router/navigation-recovery'
const dialog = useAuthDialogStore()
</script>
<template><div :inert="dialog.open || undefined" :aria-hidden="dialog.open || undefined"><div v-if="navigationLoadFailure" class="navigation-load-error" role="alert"><p>页面资源加载失败，可能刚刚更新或网络暂时不可用。</p><button type="button" @click="retryFailedNavigation">重新打开页面</button></div><RouterView /></div><AuthDialog /></template>
<style scoped>
.navigation-load-error{position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:100;width:min(540px,calc(100% - 32px));padding:16px 20px;border:1px solid var(--line);border-radius:14px;background:var(--surface-2);box-shadow:0 5px 22px #281b2515;color:var(--ink)}.navigation-load-error p{margin:0 0 10px;line-height:1.6}.navigation-load-error button{min-height:44px;padding:8px 16px;border:1px solid var(--line-strong);border-radius:9px;background:var(--surface);color:inherit;cursor:pointer}
</style>
