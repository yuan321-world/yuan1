import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWallStore } from '../../src/stores/wall'
import * as api from '../../src/api/wall'
vi.mock('../../src/api/wall', () => ({ listMessages: vi.fn(), listMine: vi.fn(), createMessage: vi.fn(), message: vi.fn(), requestStatus: vi.fn(), setLike: vi.fn() }))
beforeEach(() => { setActivePinia(createPinia()); vi.resetAllMocks() })
it('discards an old account response and clears its draft on account switch', async () => {
  let finish!: (v: unknown) => void
  vi.mocked(api.listMessages).mockImplementationOnce(() => new Promise(r => { finish = r }) as never)
  const store = useWallStore(); store.bindIdentity('a'); store.draft = 'private draft'
  const pending = store.load(true); store.bindIdentity('b')
  finish({ items: [{ id: 'a-secret' }], nextCursor: null, hasMore: false }); await pending
  expect(store.rows).toEqual([]); expect(store.draft).toBe('')
})

it('card likes reject stale identity responses and prevent duplicate pending writes',async()=>{
 const store=useWallStore();store.bindIdentity('a')
 const note={id:'1',liked:false,likes:0} as import('../../src/wall/types').WallMessage
 store.rows=[note]
 let finish!:(value:typeof note)=>void
 vi.mocked(api.setLike).mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve}))
 const pending=store.toggleLike(note);await store.toggleLike(note)
 expect(api.setLike).toHaveBeenCalledTimes(1);expect(store.likePending['1']).toBe(true)
 store.bindIdentity('b');store.rows=[{...note,text:'B view'}]
 finish({...note,liked:true,likes:1});await pending
 expect(store.rows[0]?.liked).toBe(false);expect(store.likePending).toEqual({})
})
it('failed card likes keep the confirmed count and allow retry',async()=>{
 const store=useWallStore();store.bindIdentity('a')
 const note={id:'1',liked:false,likes:0} as import('../../src/wall/types').WallMessage
 store.rows=[note];vi.mocked(api.setLike).mockRejectedValueOnce(new Error('network error'))
 await store.toggleLike(note)
 expect(store.rows[0]?.likes).toBe(0);expect(store.rows[0]?.liked).toBe(false);expect(store.likePending).toEqual({});expect(store.error).not.toBe('')
 vi.mocked(api.setLike).mockResolvedValueOnce({...note,liked:true,likes:1})
 await store.toggleLike(note);expect(store.rows[0]?.liked).toBe(true);expect(store.error).toBe('')
})
