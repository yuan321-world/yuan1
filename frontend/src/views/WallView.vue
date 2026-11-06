<script setup lang="ts">
import { ref,watch,nextTick } from 'vue'
import { useWallStore } from '@/stores/wall'
import { useAuthStore } from '@/stores/auth'
import { useAuthDialogStore } from '@/stores/auth-dialog'
import { categories } from '@/wall/types'
import type { WallMessage } from '@/wall/types'
import WallCanvas from '@/components/wall/WallCanvas.vue'
import WallToolbar from '@/components/wall/WallToolbar.vue'
import WallDetail from '@/components/wall/WallDetail.vue'
import WallNotifications from '@/components/wall/WallNotifications.vue'
import WallComposer from '@/components/wall/WallComposer.vue'
import '@/styles/wall.css'
const store=useWallStore(),auth=useAuthStore(),login=useAuthDialogStore(),composing=ref(false),selected=ref<WallMessage|null>(null),detailOpen=ref(false)
const canvas=ref<InstanceType<typeof WallCanvas>>(),point=ref<{x:number;y:number}>(),notice=ref('')
watch(()=>auth.user?.id,key=>{store.bindIdentity(key??'guest');composing.value=false;selected.value=null;detailOpen.value=false;void store.load(true)},{immediate:true})
function compose(anchor?:{x:number;y:number}){if(!auth.user){login.request('/wall');return}point.value=anchor;composing.value=true}
function locate(){const own=store.rows.find(n=>n.owned);if(own)canvas.value?.focus(own);else notice.value='当前视图没有你的便签，可点“我的”查看。'}
async function posted(){composing.value=false;await nextTick();const note=store.rows.find(n=>n.id===store.lastCreatedId);if(note)canvas.value?.ensureVisible(note)}
function info(){notice.value='分享学习心得、打卡和鼓励。长文可展开，手机默认列表；积分仍为演示入口，尚未接入，不会写入真实积分。'}
function like(note:WallMessage){if(!auth.user){login.request('/wall');return}void store.toggleLike(note)}
function open(note:WallMessage){selected.value=note;detailOpen.value=true}
function showCanvas(){const reload=store.mine;store.view='canvas';store.mine=false;if(reload)void store.load(true)}
function mine(){if(!auth.user){login.request('/wall');return}store.mine=!store.mine;void store.load(true)}
</script>
<template>
 <section class="wall-page"><WallCanvas ref="canvas" :notes="store.rows" @open="open" @like="like" @compose="compose"><template #controls><header class="wall-page-heading"><h1>留言墙</h1><span>{{store.rows.length}} 条{{store.hasMore?' +':''}}</span></header>
  <div class="wall-controls"><button type="button" :class="{active:!store.category}" @click="store.category='';store.load(true)">全部</button><button v-for="(label,key) in categories" :key="key" type="button" :class="{active:store.category===key}" @click="store.category=key;store.mine=false;store.load(true)">{{label}}</button><button type="button" :aria-pressed="store.mine" @click="mine">我的留言</button><form class="wall-search" @submit.prevent="store.load(true)"><input v-model="store.keyword" aria-label="搜索留言或昵称" placeholder="搜索留言或昵称"><button>搜索</button></form></div>
  <div class="wall-view-switch"><button :aria-pressed="store.view==='canvas'&&!store.mine" @click="showCanvas">便签画布</button><button :aria-pressed="store.view==='list'||store.mine" @click="store.view='list'">列表</button></div></template><template #status><div class="wall-stage-status"><p v-if="store.error&&!composing" class="wall-error" role="alert">{{store.error}} <button @click="store.load(true)">重试</button></p><p v-if="store.loading" role="status" class="muted">正在加载留言…</p><p v-else-if="!store.rows.length" class="wall-empty">还没有找到留言，来写下今天的第一点进步吧。</p><p v-if="notice" role="status" class="wall-notice">{{notice}} <button aria-label="关闭提示" @click="notice=''">×</button></p></div></template><template #pagination><button v-if="store.hasMore" :disabled="store.loading" @click="store.load()">加载更多</button></template><template #dock><WallToolbar @compose="compose()" @mine="mine" @locate="locate" @info="info"><WallNotifications @open-message="open" /></WallToolbar></template></WallCanvas><div class="wall-demo-footer"><button @click="notice='积分规则尚未接入，此处仅保留演示说明，不会写入真实积分。'">积分演示</button></div>
  <WallComposer :open="composing" :point="point" @close="composing=false" @posted="posted" />
  <WallDetail :open="detailOpen" :note="selected" @close="detailOpen=false" @refresh="store.load(true)" />
 </section>
</template>
