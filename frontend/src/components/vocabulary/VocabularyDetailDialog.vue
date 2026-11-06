<script setup lang="ts">
import {ref,onBeforeUnmount} from 'vue'
const props=defineProps<{title:string;busy?:boolean;closeLabel?:string}>()
const emit=defineEmits<{close:[]}>()
const element=ref<HTMLDialogElement>()
let opener:HTMLElement|null=null
function showModal(){
  if(element.value?.open)return
  opener=document.activeElement instanceof HTMLElement?document.activeElement:null
  element.value?.showModal()
}
function close(){element.value?.close?.()}
function closed(){emit('close');if(opener?.isConnected)opener.focus({preventScroll:true})}
function backdrop(event:MouseEvent){
  if(props.busy||event.target!==element.value)return
  const rect=element.value.getBoundingClientRect()
  if(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom)close()
}
onBeforeUnmount(close)
defineExpose({showModal,close,get open(){return element.value?.open??false}})
</script>

<template>
  <Teleport to="body">
    <dialog ref="element" class="vocabulary-detail-modal" :aria-label="title" @close="closed" @cancel="busy&&$event.preventDefault()" @click="backdrop">
      <header class="vd-toolbar"><div class="vd-heading"><slot name="heading"><h2>{{ title }}</h2></slot></div><button class="vd-close" :aria-label="closeLabel||'关闭'+title" :disabled="busy" @click="close">×</button></header>
      <div class="vd-scroll"><slot/></div>
      <footer v-if="$slots.footer" class="vd-footer"><slot name="footer"/></footer>
    </dialog>
  </Teleport>
</template>

<style scoped>
.vocabulary-detail-modal{position:fixed;inset:0;margin:auto;width:min(500px,calc(100vw - 28px));height:min(620px,calc(100dvh - 56px));max-width:calc(100vw - 28px);max-height:calc(100dvh - 56px);box-sizing:border-box;border:1px solid var(--border,#dce1d3);border-radius:22px;padding:0;overflow:hidden;background:var(--surface,#faf9f2);color:var(--text,#293329)}
.vocabulary-detail-modal[open]{display:flex;flex-direction:column}
.vocabulary-detail-modal::backdrop{background:#26302555;backdrop-filter:blur(3px)}
.vd-toolbar{display:flex;align-items:flex-start;gap:14px;padding:20px 20px 14px;border-bottom:1px solid var(--border,#dce1d3);flex-shrink:0}
.vd-heading{flex:1;min-width:0}.vd-heading :deep(h2){font:600 30px/1.25 Georgia,serif;margin:0;overflow-wrap:anywhere}
.vocabulary-detail-modal :deep(button){font:inherit;font-size:13px;cursor:pointer;border:1px solid var(--border,#dce1d3);border-radius:99px;padding:8px 12px;background:transparent;color:inherit}
.vocabulary-detail-modal :deep(button:disabled){opacity:.55;cursor:default}
.vocabulary-detail-modal :deep(button:focus-visible),.vocabulary-detail-modal :deep(a:focus-visible){outline:2px solid #7e9867;outline-offset:2px}
.vocabulary-detail-modal .vd-close{border:0;padding:0 4px;font-size:25px;line-height:1.1;flex-shrink:0}
.vd-scroll{flex:1;min-height:0;overflow:auto;overflow-wrap:anywhere;overscroll-behavior:contain;padding:16px 20px}
.vd-footer{flex-shrink:0;display:flex;align-items:center;justify-content:flex-end;gap:8px;flex-wrap:wrap;padding:12px 20px;border-top:1px solid var(--border,#dce1d3)}
.vd-footer :deep(.vd-primary){background:#4a653d;border-color:#4a653d;color:white}
.vd-scroll :deep(.vl-dictionary-summary){display:block}.vd-scroll :deep(.vl-senses){text-align:left}
.vd-scroll :deep(.vl-definition-meaning){font-size:16px;line-height:1.65;margin:4px 0 10px}
.vd-scroll :deep(.vl-fixed-examples){margin-top:16px;padding-top:12px;border-top:1px solid var(--border,#dce1d3)}
.vd-scroll :deep(.vl-fixed-examples h3){font-size:13px;color:#788074;margin:0 0 12px}
.vd-scroll :deep(.vl-example){margin:0 0 14px;padding:0;border:0;background:transparent}
.vd-scroll :deep(.vl-example p){font-size:16px;line-height:1.6;margin:0}
.vd-scroll :deep(.vl-example span){display:block;color:#788074;font-size:13px;line-height:1.6;margin-top:4px}
.vd-scroll :deep(.vl-example-credit){display:block;font-size:10px;color:#788074;line-height:1.6}
.vd-scroll :deep(.vl-example-credit a){color:inherit}
.vd-scroll :deep(.vl-dictionary-more){padding:10px 0;margin-top:10px;border-top:1px solid var(--border,#dce1d3)}
.vd-scroll :deep(summary){cursor:pointer;color:#587344;font-size:13px;line-height:1.6}
.vd-scroll :deep(.vl-related-words){font-size:13px}.vd-scroll :deep(.vl-related-words h3){font-size:13px;color:#587344}
.vd-scroll :deep(.vl-related-words ul){list-style:none;display:flex;flex-wrap:wrap;gap:6px;padding:0}
.vd-scroll :deep(.vl-related-words li){background:#edf1e6;border-radius:99px;padding:4px 8px}
.vd-scroll :deep(.vl-phrase-list){padding:0;list-style:none}.vd-scroll :deep(.vl-phrase-list li){display:flex;align-items:center;justify-content:space-between;gap:10px;padding:10px 0;border-bottom:1px solid var(--border,#dce1d3)}
.vd-scroll :deep(.vl-phrase-list p){font-size:13px;line-height:1.6;color:#788074;margin:4px 0 0}
@media(max-width:520px){.vocabulary-detail-modal{width:calc(100vw - 24px);max-width:calc(100vw - 24px);height:min(620px,calc(100dvh - 32px));max-height:calc(100dvh - 32px);border-radius:18px}.vd-toolbar{padding:16px}.vd-scroll{padding:14px 16px}.vd-footer{padding:12px 16px}.vd-heading :deep(h2){font-size:27px}}
</style>
