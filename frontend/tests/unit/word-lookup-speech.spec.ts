import {afterEach,beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises,type VueWrapper} from '@vue/test-utils'
// @ts-expect-error Production SFC types are checked separately by vue-tsc.
import WordLookupPanel from '../../src/components/reading/WordLookupPanel.vue'
let wrapper:VueWrapper|undefined
class Utterance {text:string;lang='';onend:(()=>void)|null=null;onstart:(()=>void)|null=null;onerror:((e:{error:string})=>void)|null=null;constructor(text:string){this.text=text}}
let spoken:Utterance[],cancel:ReturnType<typeof vi.fn>
const entry={entryId:'1',lemma:'cabin',phonetic:'kæbin',senses:[{partOfSpeech:'n.',definitionZh:'小屋',definitionEn:'a small house'}],sourceNote:'fixture',collection:null}
beforeEach(()=>{spoken=[];cancel=vi.fn();vi.stubGlobal('SpeechSynthesisUtterance',Utterance);vi.stubGlobal('speechSynthesis',{speak:vi.fn((u:Utterance)=>spoken.push(u)),cancel});vi.stubGlobal('requestAnimationFrame',()=>1);vi.stubGlobal('cancelAnimationFrame',()=>{});vi.stubGlobal('ResizeObserver',class{observe(){}disconnect(){}});wrapper=mount(WordLookupPanel,{props:{word:'cabin',result:{found:true,candidates:[entry]},busy:false,error:''},global:{stubs:{Teleport:true}}})})
afterEach(()=>{wrapper?.unmount();wrapper=undefined;vi.unstubAllGlobals()})
const click=(accent='英')=>wrapper!.get(`button[aria-label="${accent}式合成朗读"]`).trigger('click')
it('ignores synchronous cancellation and late errors from the replaced pronunciation',async()=>{await click();const first=spoken[0]!;cancel.mockImplementation(()=>first.onerror?.({error:'interrupted'}));await click('美');first.onerror?.({error:'synthesis-failed'});await flushPromises();expect(wrapper!.text()).not.toContain('朗读暂不可用');expect(spoken[1]!.lang).toBe('en-US');first.onend?.();await click();expect(cancel).toHaveBeenCalledTimes(2)})
it.each(['canceled','interrupted'])('does not call an intentional %s event unavailable',async error=>{await click();spoken[0]!.onerror?.({error});await flushPromises();expect(wrapper!.text()).not.toContain('朗读暂不可用')})
it('keeps genuine current failures visible and clears them on a successful retry',async()=>{await click();spoken[0]!.onerror?.({error:'audio-hardware'});await flushPromises();expect(wrapper!.text()).toContain('朗读暂不可用');await click('美');spoken[1]!.onstart?.();await flushPromises();expect(wrapper!.text()).not.toContain('朗读暂不可用')})
it('invalidates callbacks when the dictionary entry changes',async()=>{await click();const first=spoken[0]!;await wrapper!.setProps({word:'room',result:{found:true,candidates:[{...entry,entryId:'2',lemma:'room'}]}});first.onerror?.({error:'synthesis-failed'});await flushPromises();expect(wrapper!.text()).not.toContain('朗读暂不可用')})
