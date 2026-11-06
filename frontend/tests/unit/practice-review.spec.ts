import {beforeEach,it,expect,vi} from 'vitest'
import {createPinia,setActivePinia} from 'pinia'
import {useAuthStore} from '@/stores/auth'
import {usePracticeReview} from '@/stores/practice-review'
import * as api from '@/api/practice-review'
import {advanceGeneration} from '@/auth/token-session'
vi.mock('@/api/practice-review',()=>({history:vi.fn(),create:vi.fn()}))
const source={sourceType:'SUBJECTIVE' as const,attemptId:'1',slot:'WRITING' as const}
const make=()=>({id:'9',...source,status:'QUEUED' as const,submittedText:'Frozen text',createdAt:'2026-10-26T00:00:00Z',completedAt:null,result:null,errorCode:null,model:'deepseek-flash',promptVersion:'subjective-review-v1',rubricVersion:'learning-15-v1'})
beforeEach(()=>{setActivePinia(createPinia());vi.clearAllMocks();useAuthStore().user={id:'one',username:'one',displayName:'One',roles:['USER']}})
it('retries an uncertain request with its same UUID and isolates source slots',async()=>{const s=usePracticeReview();vi.mocked(api.create).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(make());await s.request(source);await s.request(source);expect(vi.mocked(api.create).mock.calls[0]![1]).toBe(vi.mocked(api.create).mock.calls[1]![1]);expect(s.state(source).records).toHaveLength(1);expect(s.state({...source,slot:'TRANSLATION'}).records).toHaveLength(0)})
it('ignores private history arriving after account change',async()=>{const s=usePracticeReview();let resolve!:(v:api.Review[])=>void;vi.mocked(api.history).mockImplementation(()=>new Promise(r=>resolve=r));const pending=s.load(source);useAuthStore().user={id:'two',username:'two',displayName:'Two',roles:['USER']};resolve([make()]);await pending;expect(s.state(source).records).toEqual([])})
it('settles loading flags when credentials expire without an account ID change',async()=>{const s=usePracticeReview();vi.mocked(api.history).mockImplementation(async()=>{advanceGeneration();throw new TypeError('credentials expired')});await s.load(source);expect(s.state(source).loading).toBe(false);vi.mocked(api.create).mockImplementation(async()=>{advanceGeneration();throw new TypeError('credentials expired')});await s.request(source);expect(s.state(source).requesting).toBe(false);expect(s.state(source).records).toEqual([])})
