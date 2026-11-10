import {beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {createPinia,setActivePinia} from 'pinia'
import {useAuthStore} from '../../src/stores/auth'
// @ts-expect-error SFC imports are transformed by Vitest.
import WordbookWords from '../../src/components/vocabulary/WordbookWords.vue'
import * as api from '../../src/api/vocabulary-learning'
vi.mock('../../src/api/vocabulary-learning',()=>({wordbookWords:vi.fn(),setBookWordProficiency:vi.fn()}))
const result={title:'测试词书',counts:{total:1,unlearned:1,reviewing:0,proficient:0},items:[{entryId:'1',lemma:'apple',meaning:'n. 苹果',state:'NEW' as const,dueAt:null,reviewActive:false,collectionId:null,collected:false}],total:1,page:1,pageSize:30}
beforeEach(()=>{setActivePinia(createPinia());vi.resetAllMocks();useAuthStore().user={id:'one',username:'one',displayName:'One',roles:['USER']};vi.mocked(api.wordbookWords).mockResolvedValue(result);HTMLDialogElement.prototype.showModal=function(){this.open=true};HTMLDialogElement.prototype.close=function(){this.open=false;this.dispatchEvent(new Event('close'))}})
it('browses without switching source and uses only three learning states',async()=>{
 const view=mount(WordbookWords,{props:{sourceTitle:'当前词书'},attachTo:document.body});await view.vm.open('9');await flushPromises();expect(api.wordbookWords).toHaveBeenCalledWith('9',expect.objectContaining({status:'ALL'}),expect.any(AbortSignal));expect(document.body.textContent).toContain('apple');expect(document.body.textContent).toContain('苹果');expect(document.body.textContent).not.toMatch(/难词|已暂停/);expect(document.body.textContent).toContain('仅查看');view.unmount()
})
it('account change discards an old list response and closes the viewer',async()=>{
 let resolve!:(value:api.WordbookWords)=>void;vi.mocked(api.wordbookWords).mockImplementationOnce(()=>new Promise(r=>{resolve=r}));const view=mount(WordbookWords,{props:{sourceTitle:'当前词书'},attachTo:document.body});await view.vm.open('9');useAuthStore().user=null;resolve(result);await flushPromises();expect(document.querySelector('.wbw-list')?.textContent).not.toContain('apple');expect(document.querySelector('dialog.wbw-dialog[open]')).toBeNull();view.unmount()
})
