import {beforeEach,it,expect,vi} from 'vitest'
import {createPinia,setActivePinia} from 'pinia'
import {useAuthStore} from '../../src/stores/auth'
import * as api from '../../src/api/practice'
import {usePracticeAttempt} from '../../src/stores/practice-attempt'
import {advanceGeneration} from '../../src/auth/token-session'
import {ApiError} from '../../src/api/client'
vi.mock('../../src/api/practice',()=>({getPracticeAttempt:vi.fn(),savePracticeDraft:vi.fn(),submitPractice:vi.fn()}))
const make=()=>({id:'1',setId:'2',setVersionId:'3',quizVersionId:'4',kind:'FULL',status:'IN_PROGRESS',version:0,currentQuestionId:'q1',questions:[{id:'q1'},{id:'q2'}],answers:[{questionId:'q1',draftChoice:null},{questionId:'q2',draftChoice:null}]} as api.Attempt)
beforeEach(()=>{setActivePinia(createPinia());vi.resetAllMocks();useAuthStore().user={id:'one',username:'one',displayName:'One',roles:['USER']}})
it('flush keeps edits typed during an inflight save and serializes subsequent writes',async()=>{
 const store=usePracticeAttempt();vi.mocked(api.getPracticeAttempt).mockResolvedValue(make());await store.load('1');let resolve!:(a:api.Attempt)=>void
 vi.mocked(api.savePracticeDraft).mockImplementationOnce(()=>new Promise(r=>resolve=r)).mockResolvedValueOnce({...make(),version:2})
 store.choose('q1','A');const saving=store.flush();store.choose('q1','B');resolve({...make(),version:1});expect(await saving).toBe(true);expect(store.choices.q1).toBe('B');expect(api.savePracticeDraft).toHaveBeenCalledTimes(2);expect(vi.mocked(api.savePracticeDraft).mock.calls[1]![1].answers).toEqual([{questionId:'q1',choice:'B'}]);store.dispose()
})
it('failed exit retains a cleared choice and retry uses the latest selection',async()=>{
 const store=usePracticeAttempt();vi.mocked(api.getPracticeAttempt).mockResolvedValue(make());await store.load('1');store.choose('q1',null);store.choose('q2','B');vi.mocked(api.savePracticeDraft).mockRejectedValueOnce(new TypeError('offline'));expect(await store.flush()).toBe(false);expect(store.dirty).toBe(true);expect(store.choices.q1).toBeNull();vi.mocked(api.savePracticeDraft).mockResolvedValue({...make(),version:1});expect(await store.flush()).toBe(true);expect(store.dirty).toBe(false);store.dispose()
})
it('account changes discard private state and ignore a late response',async()=>{
 const store=usePracticeAttempt();let resolve!:(a:api.Attempt)=>void;vi.mocked(api.getPracticeAttempt).mockImplementation(()=>new Promise(r=>resolve=r));const loading=store.load('1');useAuthStore().user={id:'two',username:'two',displayName:'Two',roles:['USER']};resolve(make());await loading;expect(store.attempt).toBeNull();expect(store.choices).toEqual({});store.dispose()
})
it('an uncertain submit is retried with exactly the same request id and version',async()=>{
 const store=usePracticeAttempt();vi.mocked(api.getPracticeAttempt).mockResolvedValue(make());await store.load('1');vi.mocked(api.submitPractice).mockRejectedValueOnce(new TypeError('timeout')).mockResolvedValueOnce({...make(),status:'COMPLETED',version:1,correctCount:0});expect(await store.submit(true)).toBe(false);expect(store.pendingSubmit).toBeTruthy();const first=vi.mocked(api.submitPractice).mock.calls[0]![1];expect(await store.submit(true)).toBe(true);expect(vi.mocked(api.submitPractice).mock.calls[1]![1]).toEqual(first);store.dispose()
})
it('expired credentials release saving while retaining same-owner pending choices',async()=>{
 const store=usePracticeAttempt();vi.mocked(api.getPracticeAttempt).mockResolvedValue(make());await store.load('1');store.choose('q1','B');vi.mocked(api.savePracticeDraft).mockImplementationOnce(async()=>{advanceGeneration();throw new ApiError(401,{code:'UNAUTHENTICATED',message:'登录已过期'})});expect(await store.flush()).toBe(false);expect(store.saving).toBe(false);expect(store.dirty).toBe(true);expect(store.choices.q1).toBe('B');expect(store.error).toContain('登录');store.dispose()
})

it('saves a word move in one batch and retains the whole batch after failure',async()=>{
 const store=usePracticeAttempt();vi.mocked(api.getPracticeAttempt).mockResolvedValue(make());await store.load('1');
 store.choose('q1','O');vi.mocked(api.savePracticeDraft).mockResolvedValue({...make(),version:1});await store.flush();
 store.chooseBatch([{questionId:'q1',choice:null},{questionId:'q2',choice:'O'}]);
 vi.mocked(api.savePracticeDraft).mockRejectedValueOnce(new TypeError('offline'));
 expect(await store.flush()).toBe(false);expect(store.choices).toEqual({q1:null,q2:'O'});
 expect(vi.mocked(api.savePracticeDraft).mock.calls.at(-1)![1].answers).toEqual([{questionId:'q1',choice:null},{questionId:'q2',choice:'O'}]);
 vi.mocked(api.savePracticeDraft).mockResolvedValue({...make(),version:2});expect(await store.flush()).toBe(true);store.dispose()
})
it('rejects an invalid batch without applying its valid prefix',async()=>{
 const store=usePracticeAttempt();vi.mocked(api.getPracticeAttempt).mockResolvedValue(make());await store.load('1');
 store.chooseBatch([{questionId:'q1',choice:'A'},{questionId:'foreign',choice:'O'}]);expect(store.dirty).toBe(false);expect(store.choices.q1).toBeNull();store.dispose()
})

it('MATCH pending flags survive edits during save and reload, then clear on identity change',async()=>{
 const store=usePracticeAttempt();vi.mocked(api.getPracticeAttempt).mockResolvedValue({...make(),readingType:'MATCH',answers:make().answers.map(a=>({...a,flagged:false}))});await store.load('1');
 store.chooseBatch([{questionId:'q1',choice:'A',flagged:true}]);expect(store.flags.q1).toBe(true);
 vi.mocked(api.savePracticeDraft).mockRejectedValueOnce(new TypeError('offline'));expect(await store.flush()).toBe(false);
 store.choose('q1','B');expect(store.flags.q1).toBe(true);await store.load('1');expect(store.flags.q1).toBe(true);expect(store.choices.q1).toBe('B');
 vi.mocked(api.savePracticeDraft).mockResolvedValue({...make(),version:1});expect(await store.flush()).toBe(true);expect(vi.mocked(api.savePracticeDraft).mock.calls.at(-1)![1].answers).toEqual([{questionId:'q1',choice:'B',flagged:true}]);
 useAuthStore().user={id:'two',username:'two',displayName:'Two',roles:['USER']};expect(store.flags).toEqual({});store.dispose()
})
it('MATCH explicit false flags persist and an invalid batch does not partly change flags',async()=>{
 const store=usePracticeAttempt();vi.mocked(api.getPracticeAttempt).mockResolvedValue({...make(),readingType:'MATCH',answers:make().answers.map(a=>({...a,flagged:true}))});await store.load('1');
 store.chooseBatch([{questionId:'q1',choice:null,flagged:false},{questionId:'foreign',choice:'A',flagged:false}]);expect(store.flags.q1).toBe(true);expect(store.dirty).toBe(false);
 store.chooseBatch([{questionId:'q1',choice:null,flagged:false}]);expect(store.flags.q1).toBe(false);store.discardLocal();expect(store.flags.q1).toBe(true);store.dispose()
})

it('completed result navigation remains local and never leaves an unsaved position',async()=>{
 const store=usePracticeAttempt();vi.mocked(api.getPracticeAttempt).mockResolvedValue({...make(),status:'COMPLETED',readingType:'MATCH'});await store.load('1');
 expect(await store.navigate('q2')).toBe(true);expect(store.currentQuestionId).toBe('q2');expect(store.dirty).toBe(false);expect(api.savePracticeDraft).not.toHaveBeenCalled();store.dispose()
})

it('reconciles frozen flags when a conflict reload finds an already submitted attempt',async()=>{
 const store=usePracticeAttempt();vi.mocked(api.getPracticeAttempt).mockResolvedValue({...make(),readingType:'MATCH'});await store.load('1');
 store.chooseBatch([{questionId:'q1',choice:'A',flagged:true}]);vi.mocked(api.savePracticeDraft).mockRejectedValueOnce(new ApiError(409,{code:'PRACTICE_ATTEMPT_CHANGED',message:'其他页面已交卷'}));await store.flush();
 vi.mocked(api.getPracticeAttempt).mockResolvedValue({...make(),status:'COMPLETED',readingType:'MATCH',version:1,answers:[{questionId:'q1',draftChoice:'B',submittedChoice:'B',flagged:false},{questionId:'q2',draftChoice:null,flagged:false}]});await store.reloadKeepingChoices();
 expect(store.dirty).toBe(false);expect(store.flags.q1).toBe(false);expect(store.choices.q1).toBe('B');expect(store.conflict).toBe(false);store.dispose()
})

it('listening playback and choices share the same save queue and survive failure',async()=>{
 const store=usePracticeAttempt(),base={...make(),readingType:'LISTENING',playback:{positionSeconds:8,rate:1.25}};vi.mocked(api.getPracticeAttempt).mockResolvedValue(base);await store.load('1');expect(store.playback.positionSeconds).toBe(8);
 store.updatePlayback({positionSeconds:14,rate:1.5});store.choose('q1','B');vi.mocked(api.savePracticeDraft).mockRejectedValueOnce(new TypeError('offline'));expect(await store.flush()).toBe(false);expect(store.playback).toEqual({positionSeconds:14,rate:1.5});expect(store.dirty).toBe(true);
 vi.mocked(api.savePracticeDraft).mockResolvedValue({...base,version:1,playback:{positionSeconds:14,rate:1.5}});expect(await store.flush()).toBe(true);expect(vi.mocked(api.savePracticeDraft).mock.calls.at(-1)![1]).toMatchObject({playback:{positionSeconds:14,rate:1.5},answers:[{questionId:'q1',choice:'B'}]});expect(store.dirty).toBe(false);store.dispose()
})

it('continuous playback does not turn autosave into an endless request chain',async()=>{
 const store=usePracticeAttempt();const base={...make(),readingType:'LISTENING',playback:{positionSeconds:0,rate:1}};vi.mocked(api.getPracticeAttempt).mockResolvedValue(base);await store.load('1');let calls=0;
 vi.mocked(api.savePracticeDraft).mockImplementation(async()=>{calls++;if(calls<4)store.updatePlayback({positionSeconds:calls*5+1,rate:1});return {...base,version:calls}});
 store.updatePlayback({positionSeconds:5,rate:1});expect(await store.flush(false)).toBe(true);expect(calls).toBe(1);expect(store.dirty).toBe(true);store.dispose()
})
