import {mount} from '@vue/test-utils'
import {describe,it,expect} from 'vitest'
import {createPinia,setActivePinia} from 'pinia'
// @ts-expect-error Vue SFC checked by app vue-tsc.
import LearningDashboard from '@/components/vocabulary/LearningDashboard.vue'
import type {Overview} from '@/api/vocabulary-learning'
describe('saved round cards distinguish current source and exact restore',()=>{
 it('blocks every new-word entry while another-scope review is saved',async()=>{
  setActivePinia(createPinia())
  const overview={unlearned:3,due:2,newToday:0,reviewToday:0,candidates:0,nextDueAt:null,sessionId:null,settings:{newGoal:20,reviewGoal:100,reviewScope:'CURRENT'},source:{kind:'BOOK',bookId:'B',title:'Book B',available:true,reason:''},savedSessions:[{id:'A1',kind:'NEW',mode:'SPELLING',status:'PAUSED',phase:'QUESTION',passed:0,total:2,sourceTitle:'Book A',sourceKey:'BOOK:A'},{id:'R1',kind:'REVIEW',mode:'SPELLING',status:'PAUSED',phase:'QUESTION',passed:0,total:1,sourceTitle:'全部来源',sourceKey:'GLOBAL'}]} as Overview
  const w=mount(LearningDashboard,{props:{overview,session:null,busy:false},global:{stubs:{WordbookSelector:true,LearningTrend:true,LearningSettings:true,RouterLink:true}}})
  const button = (label: string) => w.findAll('button').find(b => b.text() === label)!
  expect(button('开始学习').attributes('disabled')).toBeDefined()
  expect(w.text()).not.toContain('其他词书的进度')
  await button('继续复习').trigger('click')
  expect(w.emitted('resume')).toEqual([['R1']])
  await w.setProps({overview:{...overview,source:{...overview.source!,bookId:'A',title:'Book A'}}})
  expect(button('继续学习').attributes('disabled')).toBeDefined()
  await w.setProps({overview:{...overview,source:{...overview.source!,bookId:'A',title:'Book A'},savedSessions:overview.savedSessions!.filter(r=>r.kind==='NEW')}})
  await button('继续学习').trigger('click')
  expect(w.emitted('resume')).toEqual([['R1'],['A1']])
  w.unmount()
 })
})
