import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,it,expect,vi} from 'vitest'
// @ts-expect-error App SFC types are checked by vue-tsc.
import LearningWordManager from '@/components/vocabulary/LearningWordManager.vue'
import {learningWords,setLearningActive,setLearningProficiency} from '@/api/vocabulary-learning'
vi.mock('@/api/vocabulary-learning',()=>({learningWords:vi.fn(),setLearningActive:vi.fn(),setLearningProficiency:vi.fn()}))
const item={id:'31',entryId:'12',lemma:'agenda',meaning:'议程',active:true,stage:1,dueAt:null,proficient:false}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(learningWords).mockResolvedValue({items:[item],page:1,pageSize:20,total:1})})
it('keeps pause and resume available for learning words without an active collection',async()=>{
 const w=mount(LearningWordManager);await flushPromises()
 vi.mocked(learningWords).mockResolvedValue({items:[{...item,active:false}],page:1,pageSize:20,total:1})
 await w.get('button').trigger('click');await flushPromises()
 expect(setLearningActive).toHaveBeenCalledWith('31',false)
 expect(w.text()).toContain('恢复学习');expect(w.emitted('changed')).toHaveLength(1);w.unmount()
})
it('restores proficient words through the proficiency API, preserving their schedule',async()=>{
 vi.mocked(learningWords).mockResolvedValue({items:[{...item,proficient:true}],page:1,pageSize:20,total:1})
 const w=mount(LearningWordManager);await flushPromises();await w.get('button').trigger('click');await flushPromises()
 expect(setLearningProficiency).toHaveBeenCalledWith('31',false);expect(setLearningActive).not.toHaveBeenCalled();w.unmount()
})
