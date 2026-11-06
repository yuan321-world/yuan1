import {beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {createPinia,setActivePinia} from 'pinia'
import {useAuthStore} from '../../src/stores/auth'
// @ts-expect-error SFC imports are transformed by Vitest; plain node-config tsc has no Vue loader.
import VocabularyStats from '../../src/components/vocabulary/VocabularyStats.vue'
import * as api from '../../src/api/vocabulary-stats'
vi.mock('../../src/api/vocabulary-stats',()=>({vocabularyStats:vi.fn()}))
const empty={today:'2026-10-19',weekStart:'2026-10-19',days:Array.from({length:7},(_,i)=>({date:`2026-10-${19+i}`,newLearned:0,reviewed:0})),totalLearned:0,totalReviewPasses:0,due:0,paused:0,completedRounds:0,nextDueAt:null}
beforeEach(()=>{setActivePinia(createPinia());vi.resetAllMocks();useAuthStore().user={id:'one',username:'one',displayName:'One',roles:['USER']}})
it('shows actual zero records and exposes a local error/retry',async()=>{
 vi.mocked(api.vocabularyStats).mockRejectedValueOnce(new TypeError('network'));const view=mount(VocabularyStats,{global:{stubs:{RouterLink:true}}});await flushPromises();expect(view.find('[role=alert]').exists()).toBe(true)
 vi.mocked(api.vocabularyStats).mockResolvedValueOnce(empty);await view.find('button').trigger('click');await flushPromises();expect(view.text()).toContain('还没有词汇学习记录');expect(view.findAll('.vs-day')).toHaveLength(7);view.unmount()
})
it('account changes drop late old statistics',async()=>{
 let resolve!:(v:api.VocabularyStats)=>void;vi.mocked(api.vocabularyStats).mockImplementationOnce(()=>new Promise(r=>{resolve=r})).mockResolvedValue(empty);const view=mount(VocabularyStats,{global:{stubs:{RouterLink:true}}});useAuthStore().user={id:'two',username:'two',displayName:'Two',roles:['USER']};await flushPromises();resolve({...empty,totalLearned:999});await flushPromises();expect(view.text()).not.toContain('999');view.unmount()
})
