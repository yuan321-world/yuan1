import {beforeEach,expect,it,vi} from 'vitest'
import {createPinia,setActivePinia} from 'pinia'
import {useAuthStore} from '../../src/stores/auth'
import {useVocabularyLearning} from '../../src/stores/vocabulary-learning'
import * as api from '../../src/api/vocabulary-learning'
vi.mock('../../src/api/vocabulary-learning',()=>({learningOverview:vi.fn(),learningSession:vi.fn(),learningCommand:vi.fn(),learningSavedSession:vi.fn()}))
beforeEach(()=>{setActivePinia(createPinia());vi.resetAllMocks();useAuthStore().user={id:'one',username:'one',displayName:'One',roles:['USER']}})
it('preserves a failed command id and only shows definitions returned by the server',async()=>{
 const store=useVocabularyLearning();store.session={id:'1',version:0,kind:'NEW',mode:'SPELLING',status:'ACTIVE',phase:'QUESTION',current:{id:'2'} as api.Current,queue:[],passed:0,total:1,results:[]}
 vi.mocked(api.learningCommand).mockRejectedValueOnce(new TypeError('network'))
 await store.answer({action:'SPELLING',spelling:'apple'});expect(store.session.phase).toBe('QUESTION');expect(store.pending).toBeTruthy();const id=store.pending!.body.requestId
 vi.mocked(api.learningCommand).mockResolvedValueOnce({...store.session,phase:'DEFINITION',version:1})
 await store.retry();expect(vi.mocked(api.learningCommand).mock.calls[1]![1].requestId).toBe(id);expect(store.session.phase).toBe('DEFINITION')
})
it('identity changes clear the queue and ignore late responses',async()=>{
 const store=useVocabularyLearning();let resolve!:(v:api.Overview)=>void;vi.mocked(api.learningOverview).mockImplementationOnce(()=>new Promise(r=>{resolve=r}));vi.mocked(api.learningSession).mockResolvedValue(null)
 const loading=store.load();useAuthStore().user={id:'two',username:'two',displayName:'Two',roles:['USER']};resolve({due:9} as api.Overview);await loading;expect(store.overview).toBeNull();expect(store.session).toBeNull()
})
it('leaving and returning after an uncertain write retains an accessible retry',async()=>{
 const store=useVocabularyLearning();store.session={id:'1',version:0,kind:'NEW',mode:'SPELLING',status:'ACTIVE',phase:'QUESTION',current:{id:'2'} as api.Current,queue:[],passed:0,total:1,results:[]}
 vi.mocked(api.learningCommand).mockRejectedValueOnce(new TypeError('network'));await store.answer({action:'SPELLING',spelling:'apple'});store.dispose()
 vi.mocked(api.learningOverview).mockResolvedValue({due:0} as api.Overview);vi.mocked(api.learningSession).mockResolvedValue(store.session);await store.load()
 expect(store.pending).toBeTruthy();expect(store.error).toBeTruthy()
})

it('refreshes only overview without restoring or replacing a learning session',async()=>{const s=useVocabularyLearning();s.session={id:'paused',status:'PAUSED'} as api.Session;s.inStudy=false;const original=s.session;vi.mocked(api.learningOverview).mockResolvedValue({due:4} as api.Overview);await s.refreshOverview();expect(s.overview?.due).toBe(4);expect(s.session).toBe(original);expect(s.inStudy).toBe(false);expect(api.learningSession).not.toHaveBeenCalled()})
it('does not refresh during study or an unknown write and ignores late reads after a command starts',async()=>{const s=useVocabularyLearning();s.inStudy=true;await s.refreshOverview();expect(api.learningOverview).not.toHaveBeenCalled();s.inStudy=false;s.pending={path:'/sessions',body:{requestId:'unchanged'}};await s.refreshOverview();expect(api.learningOverview).not.toHaveBeenCalled();s.pending=null;let release!:(o:api.Overview)=>void;vi.mocked(api.learningOverview).mockImplementationOnce(()=>new Promise(r=>release=r));const reading=s.refreshOverview();s.session={id:'active',status:'ACTIVE'} as api.Session;s.inStudy=true;release({due:99} as api.Overview);await reading;expect(s.overview).toBeNull();expect(s.session.id).toBe('active')})

it('ignores idle overview responses from a previous account and keeps refresh failures separate',async()=>{const s=useVocabularyLearning();let release!:(o:api.Overview)=>void;vi.mocked(api.learningOverview).mockImplementationOnce(()=>new Promise(r=>release=r));const read=s.refreshOverview();useAuthStore().user={id:'two',username:'two',displayName:'Two',roles:['USER']};release({due:9} as api.Overview);await read;expect(s.overview).toBeNull();vi.mocked(api.learningOverview).mockRejectedValueOnce(new Error('offline'));s.error='existing command error';await s.refreshOverview();expect(s.refreshError).toBeTruthy();expect(s.error).toBe('existing command error')})

it('v2 command confirmation never migrates a version cache over the next attempt draft',async()=>{const store=useVocabularyLearning();store.session={id:'1',version:0,kind:'REVIEW',mode:'SPELLING',status:'ACTIVE',phase:'QUESTION',current:{id:'2',attemptId:0},queue:[],passed:0,total:1,results:[],flowVersion:'v2'} as unknown as api.Session;sessionStorage.setItem('lingua-vocabulary-input:one:1:2:0',JSON.stringify({value:'ap'}));vi.mocked(api.learningCommand).mockResolvedValue({...store.session,version:1,current:{...store.session.current!,attemptId:1}});await store.answer({action:'ERROR'});expect(sessionStorage.getItem('lingua-vocabulary-input:one:1:2:1')).toBeNull();sessionStorage.clear()})

it('resumes exact saved session by id instead of reallocating the current source',async()=>{
 const store=useVocabularyLearning();const saved={id:'saved-book-a',version:4,kind:'NEW',mode:'SPELLING',status:'PAUSED',phase:'QUESTION',current:{id:'item-a',attemptId:0},queue:[],passed:0,total:1,results:[],flowVersion:'v2'} as unknown as api.Session;
 vi.mocked(api.learningSavedSession).mockResolvedValue(saved);vi.mocked(api.learningCommand).mockResolvedValue({...saved,status:'ACTIVE',version:5});vi.mocked(api.learningOverview).mockResolvedValue({} as api.Overview);
 await store.resume('saved-book-a');expect(api.learningSavedSession).toHaveBeenCalledWith('saved-book-a',expect.any(AbortSignal));expect(api.learningCommand).toHaveBeenCalledWith('/sessions/saved-book-a/state',expect.objectContaining({status:'ACTIVE',expectedVersion:4,itemId:'item-a'}),expect.any(AbortSignal));expect(store.session?.id).toBe('saved-book-a');expect(store.inStudy).toBe(true)
})
