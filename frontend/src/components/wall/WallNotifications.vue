<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import * as api from '@/api/wall'
import {errorMessage} from '@/api/identity'
import {useAuthStore} from '@/stores/auth'
import {useAuthDialogStore} from '@/stores/auth-dialog'
import type {WallNotice,WallMessage} from '@/wall/types'
const emit=defineEmits<{openMessage:[message:WallMessage]}>(),auth=useAuthStore(),login=useAuthDialogStore(),dialog=ref<HTMLDialogElement>()
const notices=ref<WallNotice[]>([]),unread=ref(0),boundary=ref('0'),cursor=ref<string|null>(null),more=ref(false),busy=ref(false),error=ref('')
let generation=0,loadingSequence=0
const actionText:Record<string,string>={EDIT:'已由管理员修改',HIDE:'已隐藏',DELETE:'已删除',RESTORE:'已恢复展示'}
async function load(append=false){if(!auth.user)return;const g=generation,s=++loadingSequence;busy.value=true;error.value='';try{const page=await api.listNotices(append?cursor.value??undefined:undefined);if(g!==generation||s!==loadingSequence)return;notices.value=append?[...notices.value,...page.items.filter(n=>!notices.value.some(x=>x.id===n.id))]:page.items;unread.value=page.unreadCount;if(!append)boundary.value=page.readBoundary;cursor.value=page.nextCursor;more.value=page.hasMore}catch(e){if(g===generation&&s===loadingSequence)error.value=errorMessage(e)}finally{if(g===generation&&s===loadingSequence)busy.value=false}}
watch(()=>auth.user?.id,()=>{generation++;loadingSequence++;notices.value=[];unread.value=0;boundary.value='0';cursor.value=null;more.value=false;error.value='';busy.value=false;dialog.value?.close();if(auth.user)void load()},{immediate:true})
onBeforeUnmount(()=>{generation++;loadingSequence++})
function open(){if(!auth.user){login.request('/wall');return}dialog.value?.showModal();void load()}
async function read(id?:string){const g=generation;busy.value=true;error.value='';try{if(id)await api.readNotice(id);else await api.readAllNotices(boundary.value);if(g===generation)await load()}catch(e){if(g===generation)error.value=errorMessage(e)}finally{if(g===generation)busy.value=false}}
async function view(n:WallNotice){const g=generation;busy.value=true;error.value='';try{const message=await api.message(n.messageId);if(g!==generation)return;dialog.value?.close();emit('openMessage',message)}catch(e){if(g===generation)error.value=errorMessage(e)}finally{if(g===generation)busy.value=false}}
</script>
<template>
 <button type="button" class="wall-bell" :aria-label="unread?'我的消息，'+unread+'条未读':'我的消息'" title="我的消息" @click="open"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M9 21h6"/></svg><span v-if="unread" class="wall-unread">{{unread>99?'99+':unread}}</span></button>
 <dialog ref="dialog" class="wall-dialog wall-notifications" aria-labelledby="wall-notifications-title"><header class="wall-dialog-heading"><div><h2 id="wall-notifications-title">我的消息</h2><p class="muted">仅本人可见的内容处理通知</p></div><button aria-label="关闭我的消息" @click="dialog?.close()">×</button></header><div class="wall-notice-summary"><span>{{notices.length}} 条消息 · {{unread}} 条未读</span><button :disabled="busy||!unread" @click="read()">全部标为已读</button></div><p v-if="error" role="alert" class="wall-error">{{error}} <button :disabled="busy" @click="load()">重试</button></p><p v-if="busy" class="muted" role="status">正在同步消息…</p><p v-else-if="!notices.length" class="wall-empty">暂时没有消息。</p>
 <article v-for="n in notices" :key="n.id" class="wall-notice-card" :class="{unread:!n.readAt}"><h3>你的{{n.targetType==='COMMENT'?'评论':'留言'}}{{actionText[n.action]??'有了处理结果'}}</h3><p class="muted">{{new Date(n.createdAt).toLocaleString('zh-CN')}} · {{n.readAt?'已读':'未读'}}</p><blockquote>{{n.excerpt}}</blockquote><p>处理原因：{{n.authorReason}}</p><div class="wall-notice-actions"><button v-if="!n.readAt" :disabled="busy" @click="read(n.id)">标为已读</button><button v-if="n.canOpen" :disabled="busy" @click="view(n)">查看当前内容</button><span v-else class="muted">当前内容不再公开展示</span></div></article><button v-if="more" :disabled="busy" @click="load(true)">加载更多消息</button></dialog>
</template>
