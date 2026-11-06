import {expect,it,vi,beforeEach} from 'vitest'
import {createPinia,setActivePinia} from 'pinia'
import {useAuthStore} from '../../src/stores/auth'
import {useVocabularyLearning} from '../../src/stores/vocabulary-learning'
import * as api from '../../src/api/vocabulary-learning'
import {retainedSpellingPrefix} from '../../src/vocabulary/spelling'
vi.mock('../../src/api/vocabulary-learning',()=>({learningOverview:vi.fn(),learningSession:vi.fn(),learningCommand:vi.fn()}))
beforeEach(()=>{setActivePinia(createPinia());vi.resetAllMocks();useAuthStore().user={id:'one',username:'one',displayName:'One',roles:['USER']}})
it('keeps the correct prefix on an incorrect letter and normalizes case',()=>{expect(retainedSpellingPrefix('apX','apple')).toBe('ap');expect(retainedSpellingPrefix('APp','apple')).toBe('APp')})
it('starts the explicit v2 protocol and binds feedback to the attempt',async()=>{const s=useVocabularyLearning();vi.mocked(api.learningCommand).mockResolvedValue({id:'s',flowVersion:'v2',version:2,current:{id:'i',attemptId:4},status:'ACTIVE'} as api.Session);await s.start('NEW','SPELLING');expect(vi.mocked(api.learningCommand).mock.calls[0]![1]).toMatchObject({flowVersion:'v2'});await s.answer({action:'HINT'});expect(vi.mocked(api.learningCommand).mock.calls[1]![1]).toMatchObject({attemptId:4,itemId:'i',expectedVersion:2})})
