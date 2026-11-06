import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,expect,it,vi} from 'vitest'
// @ts-expect-error SFC types are checked by vue-tsc; node test config has no Vue shim.
import PracticeListeningPlayer from '@/components/practice/PracticeListeningPlayer.vue'
import type {Attempt} from '@/api/practice'
vi.mock('@/api/practice',()=>({practiceAudioTicket:vi.fn().mockRejectedValue(new Error('No media in this navigation test'))}))
beforeEach(()=>{vi.spyOn(HTMLMediaElement.prototype,'pause').mockImplementation(()=>{})})
it('groups whole-listening navigation by material type while preserving question numbers and navigation',async()=>{
 const questions=Array.from({length:25},(_,i)=>({id:String(i+1),key:'q'+(i+1),type:'LISTENING',stem:'Question '+(i+1),options:['A','B','C','D']}))
 const ranges=[['news1','短篇新闻1',1,2],['news2','短篇新闻2',3,4],['news3','短篇新闻3',5,7],['conversation1','长对话1',8,11],['conversation2','长对话2',12,15],['passage1','听力篇章1',16,18],['passage2','听力篇章2',19,21],['passage3','听力篇章3',22,25]] as const
 const attempt={id:'1',status:'IN_PROGRESS',questions,answers:[],listening:{audioAssetId:'1',groups:ranges.map(([key,title,lo,hi])=>({key,title,questionKeys:questions.slice(lo-1,hi).map(q=>q.key)}))}} as unknown as Attempt
 const wrapper=mount(PracticeListeningPlayer,{props:{attempt,choices:{'8':'B'},flags:{'16':true},currentId:'8',playback:{positionSeconds:0,rate:1},disabled:false}})
 await flushPromises()
 const sections=wrapper.findAll('aside .listening-number-section')
 expect(sections.map(s=>s.find('h3').text())).toEqual(['短篇新闻','长对话','听力短文'])
 expect(sections.map(s=>s.findAll('button').map(b=>b.attributes('aria-label')))).toEqual([Array.from({length:7},(_,i)=>'第'+(i+1)+'题'),Array.from({length:8},(_,i)=>'第'+(i+8)+'题'),Array.from({length:10},(_,i)=>'第'+(i+16)+'题')])
 expect(sections[1]!.get('[aria-label="第8题"]').attributes('aria-pressed')).toBe('true')
 expect(sections[1]!.get('[aria-label="第8题"]').text()).toContain('已答')
 expect(sections[2]!.get('[aria-label="第16题"]').text()).toContain('待检查')
 await sections[2]!.get('[aria-label="第16题"]').trigger('click')
 expect(wrapper.emitted('navigate')?.at(-1)).toEqual(['16'])
 wrapper.unmount()
})

it('shows every question in the active clip and hides the overview until completed',async()=>{
 const questions=Array.from({length:9},(_,i)=>({id:String(i+1),key:'q'+(i+1),type:'LISTENING',stem:'Question '+(i+1),options:['One','Two','Three','Four']}))
 const attempt={id:'1',status:'IN_PROGRESS',questions,answers:[],listening:{audioAssetId:'1',groups:[{key:'news1',title:'短篇新闻1',questionKeys:['q1','q2']},{key:'news2',title:'短篇新闻2',questionKeys:['q3','q4','q5']},{key:'conversation1',title:'长对话1',questionKeys:['q6','q7','q8','q9']}]}} as unknown as Attempt
 const wrapper=mount(PracticeListeningPlayer,{props:{attempt,choices:{'1':'A'},flags:{},currentId:'1',playback:{positionSeconds:0,rate:1},disabled:false}})
 await flushPromises()
 expect(wrapper.find('.listening-overview').exists()).toBe(false)
 expect(wrapper.findAll('.listening-question').map(q=>q.attributes('data-question-id'))).toEqual(['1','2'])
 await wrapper.get('[data-question-id="2"] input[aria-label="B. Two"]').trigger('change')
 expect(wrapper.emitted('change')?.at(-1)).toEqual([[{questionId:'2',choice:'B'}]])
 await wrapper.setProps({currentId:'3',choices:{'1':'A','2':'B'}})
 expect(wrapper.findAll('.listening-question').map(q=>q.attributes('data-question-id'))).toEqual(['3','4','5'])
 await wrapper.get('[aria-label="标记第4题待检查"]').trigger('click')
 expect(wrapper.emitted('change')?.at(-1)).toEqual([[{questionId:'4',choice:null,flagged:true}]])
 await wrapper.setProps({currentId:'6'})
 expect(wrapper.findAll('.listening-question')).toHaveLength(4)
 await wrapper.setProps({attempt:{...attempt,status:'COMPLETED'}})
 expect(wrapper.find('.listening-overview').exists()).toBe(true)
 expect(wrapper.get('[data-question-id="6"] fieldset').attributes('disabled')).toBeDefined()
 wrapper.unmount()
})
