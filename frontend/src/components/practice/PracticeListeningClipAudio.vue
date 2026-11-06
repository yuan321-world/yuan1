<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue'
const props=defineProps<{src:string;start:number;end:number;rate:number;title:string}>()
const emit=defineEmits<{active:[]}>()
const audio=ref<HTMLAudioElement>(),position=ref(0),playing=ref(false),ready=ref(false),error=ref('')
const duration=computed(()=>props.end-props.start)
let timer:ReturnType<typeof setTimeout>|undefined
function clearTimer(){if(timer!==undefined)clearTimeout(timer);timer=undefined}
function pause(){audio.value?.pause();playing.value=false;clearTimer()}
function arm(){clearTimer();if(!playing.value||!audio.value)return;timer=setTimeout(tick,Math.max(20,(props.end-audio.value.currentTime)/audio.value.playbackRate*1000))}
function tick(){const a=audio.value;if(!a)return;if(a.currentTime>=props.end){pause();a.currentTime=props.end;position.value=duration.value}else{position.value=Math.max(0,a.currentTime-props.start);arm()}}
function metadata(){const a=audio.value;if(!a)return;ready.value=Number.isFinite(a.duration)&&props.start>=0&&props.end>props.start&&props.end<=a.duration;if(!ready.value){error.value='片段时间与音频不匹配，请使用上方完整音频。';pause();return}a.currentTime=props.start;a.playbackRate=props.rate;position.value=0;error.value=''}
async function toggle(){const a=audio.value;if(!a||!ready.value)return;if(playing.value){pause();return}if(a.currentTime<props.start||a.currentTime>=props.end)a.currentTime=props.start;try{await a.play()}catch{pause();error.value='片段暂时无法播放，可重试或使用上方完整音频。'}}
function started(){const a=audio.value;if(!a)return;if(a.currentTime<props.start||a.currentTime>=props.end)a.currentTime=props.start;playing.value=true;error.value='';emit('active');arm()}
function seek(event:Event){const a=audio.value;if(!a||!ready.value)return;a.currentTime=props.start+Math.max(0,Math.min(duration.value,Number((event.target as HTMLInputElement).value)));tick()}
function failed(){pause();error.value='片段暂时无法播放，可重试或使用上方完整音频。'}
watch(()=>props.rate,value=>{if(audio.value)audio.value.playbackRate=value;arm()})
watch(()=>[props.src,props.start,props.end],()=>{pause();position.value=0;error.value='';if(audio.value&&audio.value.readyState>=1)metadata();else ready.value=false})
const format=(v:number)=>`${Math.floor(v/60)}:${String(Math.floor(v%60)).padStart(2,'0')}`
defineExpose({pause})
onBeforeUnmount(pause)
</script>
<template>
 <div class="listening-clip-audio" role="group" :aria-label="title+'片段播放器'">
  <audio ref="audio" :src="src" preload="metadata" @loadedmetadata="metadata" @play="started" @pause="playing=false;clearTimer()" @timeupdate="tick" @seeked="tick" @waiting="clearTimer" @playing="arm" @ended="pause" @error="failed"/>
  <button type="button" :disabled="!ready" :aria-label="(playing?'暂停':'播放')+title+'片段'" @click="toggle">{{playing?'暂停片段':'播放片段'}}</button>
  <span class="listening-clip-time">{{format(position)}} / {{format(duration)}}</span>
  <input class="listening-clip-range" type="range" :style="{'--clip-progress':Math.max(0,Math.min(100,position/duration*100))+'%'}" min="0" :max="duration" step="0.1" :value="position" :disabled="!ready" aria-label="当前片段播放进度" @input="seek">
  <small>仅当前片段</small>
  <p v-if="error" role="alert">{{error}}</p>
 </div>
</template>
<style scoped>
.listening-clip-audio{display:flex;align-items:center;gap:10px;flex-wrap:wrap;padding:12px;border:1px solid var(--practice-line, var(--line));border-radius:12px;background:var(--surface-2);margin:0 0 20px}.listening-clip-audio audio{display:none}.listening-clip-audio button{white-space:nowrap}.listening-clip-audio .listening-clip-range[type=range]{flex:1;min-width:80px;width:100%;min-height:24px;height:24px;margin:0;padding:0;border:0;border-radius:0;background:transparent;appearance:none;cursor:pointer}.listening-clip-range::-webkit-slider-runnable-track{height:4px;border-radius:4px;background:linear-gradient(to right,#191a1e var(--clip-progress,0%),#d8d6dd var(--clip-progress,0%))}.listening-clip-range::-webkit-slider-thumb{appearance:none;width:16px;height:16px;margin-top:-6px;border:0;border-radius:50%;background:var(--accent)}.listening-clip-range::-moz-range-track{height:4px;border-radius:4px;background:#d8d6dd}.listening-clip-range::-moz-range-progress{height:4px;background:var(--accent)}.listening-clip-range::-moz-range-thumb{width:16px;height:16px;border:0;border-radius:50%;background:var(--accent)}.listening-clip-range:disabled{opacity:.5;cursor:default}.listening-clip-time{font-variant-numeric:tabular-nums;font-size:12px;white-space:nowrap}.listening-clip-audio small{font-size:11px;color:var(--muted)}.listening-clip-audio p{flex-basis:100%;margin:0;font-size:12px;color:var(--accent-dark)}@media(max-width:600px){.listening-clip-audio .listening-clip-range[type=range]{flex-basis:100%;order:2}}
</style>
