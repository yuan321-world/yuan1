import {beforeEach,expect,it,vi} from 'vitest'
import {createPinia,setActivePinia} from 'pinia'
import {useAdminUsersStore} from '../../src/stores/admin-users'
import * as api from '../../src/api/admin-users'
vi.mock('../../src/api/admin-users',()=>({listUsers:vi.fn(),userDetail:vi.fn(),userAudit:vi.fn(),changeStatus:vi.fn(),commandStatus:vi.fn()}))
beforeEach(()=>{setActivePinia(createPinia());vi.resetAllMocks()})
it('rejects query responses after changing account and clears private records',async()=>{
 let finish!:(v:unknown)=>void;vi.mocked(api.listUsers).mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve}) as never)
 const s=useAdminUsersStore();s.bindIdentity('a');s.keyword='private';const p=s.load();s.bindIdentity('b');finish({items:[{id:'private'}],total:1,page:1,pageSize:20});await p
 expect(s.rows).toEqual([]);expect(s.keyword).toBe('');expect(s.selected).toBeNull()
})
it('newer filter wins even when previous response arrives later',async()=>{
 let finish!:(v:unknown)=>void;vi.mocked(api.listUsers).mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve}) as never).mockResolvedValueOnce({items:[],total:0,page:1,pageSize:20})
 const s=useAdminUsersStore();s.bindIdentity('a');const p=s.load();s.keyword='second';await s.load();finish({items:[{id:'old'}],total:1,page:1,pageSize:20});await p;expect(s.rows).toEqual([]);expect(s.keyword).toBe('second')
})

it('keeps the same unknown command after a network failure and confirms before replay',async()=>{
 const s=useAdminUsersStore();s.bindIdentity('a');const target={id:'1',enabled:true,version:0,protectedAccount:false} as api.AdminUser
 vi.mocked(api.changeStatus).mockRejectedValueOnce(new Error('response lost'))
 expect(await s.submitStatus(target,'管理原因')).toBe(false)
 const first=vi.mocked(api.changeStatus).mock.calls[0]![1];expect(s.pending['1']?.requestId).toBe(first.requestId)
 vi.mocked(api.commandStatus).mockResolvedValueOnce({completed:true,targetId:'1',requestId:first.requestId,enabled:false,version:1})
 expect(await s.submitStatus(target,'管理原因')).toBe(true);expect(api.changeStatus).toHaveBeenCalledTimes(1);expect(s.pending['1']).toBeUndefined()
})
