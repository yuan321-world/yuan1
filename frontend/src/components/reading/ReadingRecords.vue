<script setup lang="ts">
import {ref} from 'vue'
import {useAuthStore} from '@/stores/auth'
import {useAuthDialogStore} from '@/stores/auth-dialog'
import ReadingStats from './ReadingStats.vue'
const auth=useAuthStore(),login=useAuthDialogStore(),dialog=ref<HTMLDialogElement>(),opened=ref(false)
let opener:HTMLElement|null=null
function show(event?:Event){opener=event?.currentTarget instanceof HTMLElement?event.currentTarget:document.activeElement as HTMLElement;opened.value=true;dialog.value?.showModal()}
function close(){opened.value=false;if(opener?.isConnected)opener.focus({preventScroll:true})}
defineExpose({show})
</script>
<template><dialog ref="dialog" class="reading-records-dialog" aria-label="阅读学习记录" @close="close"><button class="records-close" aria-label="关闭阅读学习记录" @click="dialog?.close()">×</button><template v-if="opened"><ReadingStats v-if="auth.user" @browse="dialog?.close()"/><div v-else class="records-login"><h2>阅读学习记录</h2><p>登录后查看自己的阅读与打卡记录。</p><button class="button-primary" @click="dialog?.close();login.request()">登录后查看</button></div></template></dialog></template>
<style scoped>.reading-records-dialog{box-sizing:border-box;width:min(850px,calc(100vw - 32px));max-height:85dvh;overflow:auto;border:1px solid var(--line);border-radius:24px;padding:34px 24px;background:var(--surface);color:var(--text-primary);position:fixed}.reading-records-dialog::backdrop{background:#25212a66;backdrop-filter:blur(3px)}.records-close{position:absolute;right:15px;top:12px;border:0;background:transparent;font-size:25px;color:#777;cursor:pointer}.reading-records-dialog :deep(.reading-page){padding:0;max-width:none}.reading-records-dialog :deep(.reading-header h1){font-size:25px}.records-login{padding:12px}@media(max-width:500px){.reading-records-dialog{padding:40px 16px 20px}.reading-records-dialog :deep(.checkin-week){grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.reading-records-dialog :deep(.checkin-day small){white-space:nowrap}}</style>
