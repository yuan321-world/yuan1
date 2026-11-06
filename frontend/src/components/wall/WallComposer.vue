<script setup lang="ts">
import { ref,watch } from 'vue'
import { useWallStore } from '@/stores/wall'
import { categories,noteColors } from '@/wall/types'
const props=defineProps<{open:boolean;point?:{x:number;y:number};announcement?:boolean}>(),emit=defineEmits<{close:[];posted:[]}>(),store=useWallStore()
const dialog=ref<HTMLDialogElement>(),postText=ref<HTMLTextAreaElement>(),category=ref('STUDY'),color=ref('yellow'),title=ref('')
watch(()=>props.open,open=>{if(open)dialog.value?.showModal();else dialog.value?.close()})
async function submit(){if(await store.publish({kind:props.announcement?'ANNOUNCEMENT':'MESSAGE',category:props.announcement?'ANNOUNCEMENT':category.value,color:color.value,title:props.announcement?title.value:undefined,preferredPosition:props.point})){title.value='';emit('posted')}}
function bold(){const t=postText.value;if(!t)return;const a=t.selectionStart,b=t.selectionEnd,value=store.draft.slice(a,b)||'加粗文字';store.draft=store.draft.slice(0,a)+'**'+value+'**'+store.draft.slice(b);t.focus()}
</script>
<template>
 <dialog ref="dialog" class="wall-dialog" aria-labelledby="wall-compose-title" @cancel.prevent="!store.sending&&emit('close')">
  <form @submit.prevent="submit"><header class="wall-dialog-heading"><div><h2 id="wall-compose-title">{{announcement?'发布公告便签':'贴一张便签'}}</h2><p class="muted">{{announcement?'以管理员身份发布，公告标识和加粗标题与普通留言区分。':'发布后立即展示，分享真实的学习体验。'}}</p></div><button type="button" aria-label="关闭写留言" :disabled="store.sending" @click="emit('close')">×</button></header>
  <template v-if="announcement"><label for="wall-announcement-title">公告标题</label><input id="wall-announcement-title" v-model="title" :disabled="!!store.pending" required><p class="muted">最多80字</p></template><template v-else><label for="wall-post-category">这是一条</label><select id="wall-post-category" v-model="category" :disabled="!!store.pending"><option v-for="(label,key) in categories" v-show="key!=='ANNOUNCEMENT'" :key="key" :value="key">{{label}}</option></select></template>
  <label for="wall-post-text">留言内容</label><div v-if="announcement" class="wall-format-tools"><button type="button" :disabled="!!store.pending" @click="bold"><strong>B</strong> 加粗选中文字</button><span class="muted">支持 **加粗文字**</span></div><textarea id="wall-post-text" ref="postText" v-model="store.draft" required :disabled="!!store.pending" placeholder="今天学会了什么？想对努力中的同学说些什么？" /><p class="wall-word-count">{{Array.from(store.draft).length}} / 1000 字</p>
  <div class="wall-swatches"><button v-for="(value,key) in noteColors" :key="key" type="button" :style="{background:value}" :aria-label="'便签颜色 '+key" :aria-pressed="color===key" :disabled="!!store.pending" @click="color=key" /></div>
  <p v-if="store.error" class="wall-error" role="alert">{{store.error}}</p><p v-if="store.pending&&!store.sending" class="muted">上次发布结果尚未确认，请先确认结果，避免重复发布。</p>
  <div class="wall-form-actions"><button type="button" :disabled="store.sending" @click="emit('close')">取消</button><button type="submit" class="button-primary" :disabled="store.sending||!store.draft.trim()||Array.from(store.draft).length>1000">{{store.sending?'正在发布…':store.pending?'确认上次发布结果':'发布留言'}}</button></div></form>
 </dialog>
</template>
