<script setup lang="ts">
import { computed,nextTick,onMounted,onBeforeUnmount,ref,watch } from 'vue'
import { useWallStore } from '@/stores/wall'
import type { WallMessage } from '@/wall/types'
import { fitWall,toWallPoint } from '@/wall/layout'
import WallNote from './WallNote.vue'
const props=defineProps<{notes:WallMessage[]}>(),emit=defineEmits<{open:[note:WallMessage];like:[note:WallMessage];compose:[point:{x:number;y:number}]}>(),store=useWallStore(),stage=ref<HTMLElement>()
let initialFitNeeded=!store.panReady
let drag:{x:number;y:number;px:number;py:number}|null=null
const canvas=computed(()=>store.view==='canvas'&&!store.mine)
function fit(){if(stage.value)Object.assign(store.pan,fitWall(props.notes.map(n=>n.position),stage.value.clientWidth,stage.value.clientHeight))}
function focus(note:WallMessage){if(!canvas.value){stage.value?.querySelector<HTMLElement>(`[data-id="${note.id}"]`)?.scrollIntoView({block:'nearest'});return}if(!stage.value)return;store.pan.scale=Math.max(.85,store.pan.scale);store.pan.x=stage.value.clientWidth/2-(note.position.x+119)*store.pan.scale;store.pan.y=stage.value.clientHeight/2-(note.position.y+140)*store.pan.scale}
function zoom(delta:number,x=stage.value!.clientWidth/2,y=stage.value!.clientHeight/2){const p=store.pan,next=Math.max(.1,Math.min(1.6,p.scale+delta)),ratio=next/p.scale;p.x=x-(x-p.x)*ratio;p.y=y-(y-p.y)*ratio;p.scale=next}
function blank(e:Event){return !(e.target as HTMLElement).closest('button,input,select,textarea,form,.wall-note,.wall-canvas-controls,.wall-dock')}
function pointerDown(e:PointerEvent){if(!canvas.value||e.button!==0||!blank(e))return;drag={x:e.clientX,y:e.clientY,px:store.pan.x,py:store.pan.y};stage.value?.setPointerCapture(e.pointerId)}
function pointerMove(e:PointerEvent){if(drag){store.pan.x=drag.px+e.clientX-drag.x;store.pan.y=drag.py+e.clientY-drag.y}}
function doubleClick(e:MouseEvent){if(!canvas.value||!blank(e)||!stage.value)return;const r=stage.value.getBoundingClientRect();emit('compose',toWallPoint(e.clientX-r.left,e.clientY-r.top,store.pan))}
function wheel(e:WheelEvent){if(!canvas.value||!stage.value)return;e.preventDefault();const r=stage.value.getBoundingClientRect();zoom(e.deltaY>0?-.05:.05,e.clientX-r.left,e.clientY-r.top)}
function resize(){if(innerWidth<=600)store.view='list'}
watch(()=>props.notes.length,async length=>{if(length&&initialFitNeeded){await nextTick();fit();initialFitNeeded=false}})

onMounted(()=>{resize();addEventListener('resize',resize);if(!store.panReady){fit();store.panReady=true}})
onBeforeUnmount(()=>removeEventListener('resize',resize))
function ensureVisible(note:WallMessage){if(!stage.value||!canvas.value)return;const {x,y,scale}=store.pan,left=x+note.position.x*scale,top=y+note.position.y*scale;if(left<10||top<90||left+238*scale>stage.value.clientWidth-10||top+390*scale>stage.value.clientHeight-95)focus(note)}
defineExpose({fit,focus,ensureVisible})
</script>
<template>
 <section ref="stage" class="wall-stage" :class="{'is-list':!canvas}" aria-label="留言便签画布" @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="drag=null" @pointercancel="drag=null" @dblclick="doubleClick" @wheel="wheel">
  <div class="wall-canvas-controls"><slot name="controls" /></div>
  <div class="wall-world" :tabindex="canvas?undefined:0" :role="canvas?undefined:'region'" :aria-label="canvas?undefined:'留言列表'" :style="canvas?{transform:`translate(${store.pan.x}px,${store.pan.y}px) scale(${store.pan.scale})`}:{}"><WallNote v-for="note in notes" :key="note.id" :note="note" :liking="store.likePending[note.id]" :style="canvas?{left:note.position.x+'px',top:note.position.y+'px',transform:`rotate(${note.kind==='ANNOUNCEMENT'?0:Number(note.id)%2?-.7:1.1}deg)`}:{}" @open="emit('open',$event)" @like="emit('like',$event)" /><div v-if="!canvas" class="wall-list-pagination"><slot name="pagination" /></div></div>
  <slot name="status" /><div v-if="canvas" class="wall-stage-pagination"><slot name="pagination" /></div>
  <p class="wall-canvas-hint">{{canvas?'拖动空白移动画布 · 滚轮缩放 · 双击空白写留言':'滚动查看留言 · 点击展开阅读全文'}}</p>
  <slot name="dock" />
  <div class="wall-zoom"><button :disabled="!canvas" aria-label="缩小" @click="zoom(-.1)">−</button><span>{{canvas?Math.round(store.pan.scale*100)+'%':'列表'}}</span><button :disabled="!canvas" aria-label="放大" @click="zoom(.1)">＋</button><button :disabled="!canvas" aria-label="查看全部便签" @click="fit">▣</button></div>
 </section>
</template>
