import {mount,flushPromises} from '@vue/test-utils'
import {afterEach,beforeEach,expect,it,vi} from 'vitest'
import {listeningClipCue} from '@/practice/listening-clips'
// @ts-expect-error Vue shim is checked by vue-tsc.
import ClipAudio from '@/components/practice/PracticeListeningClipAudio.vue'
const hash='3138dd4a84c4d9cf6de184efd41f7a5bd4a91d1aec518ccaf5944bdecda8e649'
beforeEach(()=>{vi.spyOn(HTMLMediaElement.prototype,'pause').mockImplementation(()=>{});vi.spyOn(HTMLMediaElement.prototype,'play').mockResolvedValue()})
afterEach(()=>vi.restoreAllMocks())
it('binds cues to the exact recording and question group, never a guessed suite or asset ID',()=>{
 const group={key:'news1',questionKeys:['q1','q2']}
 expect(listeningClipCue(hash,group)?.start).toBeGreaterThan(40)
 expect(listeningClipCue('another-recording',group)).toBeNull()
 expect(listeningClipCue(hash,{...group,questionKeys:['q2','q3']})).toBeNull()
 expect(listeningClipCue(hash,{...group,key:'news2'})).toBeNull()
})
it('uses the independently aligned March recording rather than the June offset',()=>{
 const group={key:'news1',questionKeys:['q1','q2']}
 const march=listeningClipCue('1541d26c964f0711a4e4d5de10682b65321f4181a4ee911ff5505d3ae81f08c1',group)
 expect(march?.start).toBeGreaterThan(101)
 expect(march?.start).toBeLessThan(103)
 expect(listeningClipCue(hash,group)?.start).toBeLessThan(41)
})
it('keeps segment seeking local and pauses at its boundary without entering the next clip',async()=>{
 const w=mount(ClipAudio,{props:{src:'/api/v1/practice/audio/8?ticket=fixture',start:40,end:140,rate:1,title:'短篇新闻1'}})
 const a=w.get('audio').element as HTMLAudioElement
 Object.defineProperty(a,'duration',{value:1500,configurable:true})
 Object.defineProperty(a,'readyState',{value:1,configurable:true})
 await w.get('audio').trigger('loadedmetadata');expect(a.currentTime).toBe(40)
 await w.get('input').setValue('20');expect(a.currentTime).toBe(60)
 a.currentTime=140.1;await w.get('audio').trigger('timeupdate');expect(a.currentTime).toBe(140)
 expect(w.get('input').element.value).toBe('100')
 await w.get('button').trigger('click');expect(a.currentTime).toBe(40)
 await w.get('audio').trigger('play');expect(w.emitted('active')).toHaveLength(1)
 await w.setProps({rate:1.5});expect(a.playbackRate).toBe(1.5)
 await w.setProps({start:160,end:278,title:'短篇新闻2'});expect(a.currentTime).toBe(160)
 w.unmount()
})
it('rejects a cue outside the actual audio and handles playback rejection visibly',async()=>{
 const w=mount(ClipAudio,{props:{src:'/api/v1/practice/audio/8?ticket=fixture',start:40,end:140,rate:1,title:'新闻'}})
 const a=w.get('audio').element as HTMLAudioElement
 Object.defineProperty(a,'duration',{value:100,configurable:true})
 await w.get('audio').trigger('loadedmetadata');expect(w.get('button').attributes('disabled')).toBeDefined()
 Object.defineProperty(a,'duration',{value:1500,configurable:true})
 Object.defineProperty(a,'readyState',{value:1,configurable:true})
 await w.get('audio').trigger('loadedmetadata')
 vi.mocked(a.play).mockRejectedValueOnce(new Error('unavailable'));await w.get('button').trigger('click');await flushPromises()
 expect(w.get('[role="alert"]').text()).toContain('片段暂时无法播放')
 w.unmount()
})

it('keeps July 2020 clip boundaries inside its 24-minute recording and rejects mismatched groups',()=>{
 const hash='117cae76d7a7d8d72e48df57500afd7d9f4fb3c298949dc4f0e27bbaae428fcf'
 const groups=[['news1',1,2],['news2',3,4],['news3',5,7],['conversation1',8,11],['conversation2',12,15],['passage1',16,18],['passage2',19,21],['passage3',22,25]] as const
 let previous=0
 for(const [key,lo,hi] of groups){
  const group={key,questionKeys:Array.from({length:hi-lo+1},(_,i)=>'q'+(lo+i))}
  const cue=listeningClipCue(hash,group)
  expect(cue).not.toBeNull()
  expect(cue!.start).toBeGreaterThan(previous)
  expect(cue!.end).toBeGreaterThan(cue!.start)
  expect(cue!.end).toBeLessThan(1440)
  expect(listeningClipCue(hash,{...group,questionKeys:[...group.questionKeys].reverse()})).toBeNull()
  previous=cue!.end
 }
})
