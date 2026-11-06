<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount,onMounted} from 'vue'
import {wordbookCatalog,selectLearningSource,type LearningSource,type Wordbook} from '@/api/vocabulary-learning'
import {errorMessage} from '@/api/identity'
import {getGeneration} from '@/auth/token-session'
import {useAuthStore} from '@/stores/auth'
const props=defineProps<{source?:LearningSource;disabled:boolean}>(),emit=defineEmits<{changed:[];busy:[value:boolean];catalog:[value:Wordbook[]]}>(),auth=useAuthStore(),dialog=ref<HTMLDialogElement>(),opener=ref<HTMLButtonElement>(),books=ref<Wordbook[]>([]),busy=ref(false),saving=ref(false),error=ref('')
const query = ref('')
const group = ref<'all' | 'book' | 'personal'>('all')
const groups = [{id:'all',label:'全部'},{id:'book',label:'参考词书'},{id:'personal',label:'个人来源'}] as const
const search = computed(() => query.value.trim().toLowerCase())
const showPersonal = computed(() => group.value !== 'book' && '我的阅读生词'.includes(search.value))
const visibleBooks = computed(() => group.value === 'personal' ? [] : books.value.filter(book => book.title.toLowerCase().includes(search.value)))
watch(saving,value=>emit('busy',value))
let sequence=0,controller:AbortController|undefined,disposed=false
async function load(){const current=++sequence,g=getGeneration();controller?.abort();controller=new AbortController();busy.value=true;error.value='';try{const result=await wordbookCatalog(controller.signal);if(!disposed&&current===sequence&&g===getGeneration()){books.value=result;emit('catalog',result)}}catch(e){if(!disposed&&current===sequence&&g===getGeneration()&&!(e instanceof DOMException&&e.name==='AbortError'))error.value=errorMessage(e)}finally{if(current===sequence)busy.value=false}}
function open(){if(props.disabled)return;query.value='';group.value='all';dialog.value?.showModal();void load()}
function close(){sequence++;controller?.abort();busy.value=false;opener.value?.focus({preventScroll:true})}
async function select(kind:'PERSONAL'|'BOOK',id?:string){if(busy.value||saving.value||props.disabled)return;const current=sequence,g=getGeneration();saving.value=true;error.value='';controller=new AbortController();try{await selectLearningSource(kind,id,controller.signal);if(!disposed&&current===sequence&&g===getGeneration()){emit('changed');dialog.value?.close()}}catch(e){if(!disposed&&current===sequence&&g===getGeneration()){error.value=errorMessage(e);emit('changed')}}finally{if(!disposed)saving.value=false}}
watch(()=>auth.user?.id,()=>{sequence++;controller?.abort();dialog.value?.close();books.value=[];error.value='';saving.value=false},{flush:'sync'})
onMounted(()=>{if(props.source?.kind==='BOOK')void load()})
watch(()=>props.source?.bookId,id=>{if(id)void load()})
onBeforeUnmount(()=>{disposed=true;sequence++;controller?.abort()})
</script>
<template>
  <button ref="opener" :disabled="disabled" @click="open">切换来源</button>
  <Teleport to="body">
    <dialog ref="dialog" class="wordbook-dialog" aria-labelledby="wb-title" @close="close" @cancel="saving && $event.preventDefault()">
      <header class="wb-heading">
        <div><h2 id="wb-title">你的学习书架</h2><p>选择学习来源，原来的学习记录和保存轮次都会保留。</p></div>
        <button aria-label="关闭词书选择" :disabled="saving" @click="dialog?.close()">×</button>
      </header>
      <label class="wb-search">搜索词书<input v-model="query" type="search" placeholder="输入词书名称" :disabled="saving"></label>
      <div class="wb-filters" role="group" aria-label="来源筛选">
        <button v-for="item in groups" :key="item.id" :aria-pressed="group === item.id" :disabled="saving" @click="group = item.id">{{ item.label }}</button>
      </div>
      <div v-if="error" class="wb-error" role="alert"><p>{{ error }}</p><button :disabled="busy || saving" @click="load">重试词书列表</button></div>
      <p v-if="busy" class="wb-muted" role="status">正在加载词书…</p>
      <p v-else-if="saving" class="wb-muted" role="status">正在切换来源…</p>
      <p v-else-if="!showPersonal && !visibleBooks.length && !error" class="wb-muted">没有找到匹配的词书。</p>
      <div class="wb-grid">
        <article v-for="book in visibleBooks" :key="book.id" class="wb-card" :class="{ 'is-current': source?.bookId === book.id }">
          <div class="wb-tags"><span class="wb-tag">参考词书</span><span v-if="source?.bookId === book.id" class="wb-tag">正在使用</span></div>
          <h3>{{ book.title }}</h3>
          <p class="wb-muted">{{ book.status === 'PUBLISHED' ? 'ECDICT · 本地参考' : '已下架 · 可恢复保存轮次' }}</p>
          <p class="wb-count">本书 <strong>{{ book.availableCount.toLocaleString() }}</strong> 词</p>
          <p class="wb-muted">已学习覆盖 {{ book.learned.toLocaleString() }} 词</p>
          <progress v-if="book.availableCount > 0" :value="book.learned" :max="book.availableCount" aria-label="本书词汇覆盖进度"/>
          <button :disabled="busy || saving || source?.bookId === book.id" @click="select('BOOK',book.id)">{{ source?.bookId === book.id ? '当前词书' : book.status === 'PUBLISHED' ? '选择这本词书' : '切回已保存词书' }}</button>
        </article>
        <article v-if="showPersonal" class="wb-card" :class="{ 'is-current': source?.kind === 'PERSONAL' }">
          <div class="wb-tags"><span class="wb-tag">个人来源</span><span v-if="source?.kind === 'PERSONAL'" class="wb-tag">正在使用</span></div>
          <h3>我的阅读生词</h3>
          <p class="wb-muted">从已收藏并明确加入学习的词开始。</p>
          <button :disabled="busy || saving || source?.kind === 'PERSONAL'" @click="select('PERSONAL')">{{ source?.kind === 'PERSONAL' ? '当前来源' : '使用个人生词' }}</button>
        </article>
      </div>
    </dialog>
  </Teleport>
</template>
<style scoped>
.wordbook-dialog { width:min(800px,calc(100% - 32px));max-height:85dvh;overflow:auto;scrollbar-gutter:stable;border:1px solid var(--line);border-radius:20px;padding:22px;color:var(--ink);background:var(--bg) }
.wordbook-dialog::backdrop { background:#26322555 }
.wb-heading,.wb-tags { display:flex;justify-content:space-between;align-items:flex-start;gap:12px }
.wb-heading h2,.wb-card h3,.wb-card p { margin:0 }
.wb-heading p,.wb-muted,.wb-search { color:var(--muted);font-size:13px }
.wb-heading p { margin:4px 0 0 }
.wordbook-dialog button,.wb-search input { border:1px solid var(--line-strong);background:var(--surface);border-radius:10px;padding:7px 12px;font:inherit;color:inherit }
.wordbook-dialog button:disabled { opacity:.65;cursor:default }
.wb-heading>button { font-size:20px;line-height:1.2 }
.wb-search { display:grid;gap:4px;margin:14px 0 8px }
.wb-search input { width:100%;min-width:0 }
.wb-filters { display:flex;flex-wrap:wrap;gap:6px;margin-bottom:12px }
.wb-filters button { border-radius:99px;font-size:12px }
.wb-filters [aria-pressed=true] { background:var(--accent);border-color:var(--accent);color:white }
.wb-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px }
.wb-card { display:flex;flex-direction:column;gap:6px;min-width:0;min-height:288px;overflow-wrap:anywhere;background:var(--surface);border:1px solid var(--line);padding:14px;border-radius:16px }
.wb-card.is-current { border-color:var(--accent) }
.wb-card h3 { font-size:17px }
.wb-card>button { margin-top:auto;color:var(--accent-dark) }
.wb-card.is-current>button,.wb-tag { background:var(--accent-soft) }
.wb-tag { color:var(--accent-dark);font-size:11px;border-radius:99px;padding:2px 8px }
.wb-count strong { font-size:22px;font-variant-numeric:tabular-nums }
.wb-card progress { width:100%;height:6px;accent-color:var(--accent) }
.wb-error { color:var(--ink);background:var(--surface-3);padding:10px;border-radius:10px;margin-bottom:10px }
@media(max-width:540px){.wordbook-dialog{padding:15px}.wb-grid{grid-template-columns:1fr}}
</style>
