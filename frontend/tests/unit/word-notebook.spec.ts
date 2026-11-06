import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,describe,it,expect,vi} from 'vitest'
// @ts-expect-error Vue SFC types are checked by vue-tsc in the app project.
import WordNotebook from '@/components/vocabulary/WordNotebook.vue'
import * as api from '@/api/vocabulary'
import {joinLearning} from '@/api/vocabulary-learning'
vi.mock('@/stores/auth',()=>({useAuthStore:()=>({user:{id:'1'}})}))
vi.mock('@/api/vocabulary',()=>({searchVocabularyWords:vi.fn(),vocabularyDetail:vi.fn(),cancelWord:vi.fn()}))
vi.mock('@/api/vocabulary-learning',()=>({joinLearning:vi.fn()}))
const word={id:'7',entryId:'9',lemma:'study',phonetic:'stʌdi',senses:[{partOfSpeech:'v.',definitionZh:'学习',definitionEn:'learn'}],sourceNote:'original',active:true,createdAt:'2026-11-02',updatedAt:'2026-11-02',learning:null}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(api.searchVocabularyWords).mockResolvedValue({items:[word],page:1,pageSize:20,total:1})})
describe('word notebook remains authentic and separates collection from learning',()=>{
 it('searches server and joining never cancels the collection',async()=>{const w=mount(WordNotebook,{global:{stubs:{RouterLink:true}}});await flushPromises();expect(joinLearning).not.toHaveBeenCalled();await w.findAll('button').find(b=>b.text()==='加入学习')!.trigger('click');await flushPromises();expect(joinLearning).toHaveBeenCalledWith(['7'],expect.any(AbortSignal));expect(api.cancelWord).not.toHaveBeenCalled();expect(w.emitted('changed')).toHaveLength(1);await w.get('input[type=search]').setValue('学习');await w.get('form').trigger('submit');await flushPromises();expect(api.searchVocabularyWords).toHaveBeenLastCalledWith(1,'学习','ALL',expect.any(AbortSignal));w.unmount()})
 it('cancels collection without sending any learning removal',async()=>{const w=mount(WordNotebook,{global:{stubs:{RouterLink:true}}});await flushPromises();await w.findAll('button').find(b=>b.text()==='取消收藏')!.trigger('click');await flushPromises();expect(api.cancelWord).toHaveBeenCalledWith('7',expect.any(AbortSignal),expect.any(Function));expect(joinLearning).not.toHaveBeenCalled();expect(w.text()).toContain('学习的复习记录保留');w.unmount()})
})
