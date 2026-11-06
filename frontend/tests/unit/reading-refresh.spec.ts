import {afterEach,expect,it,vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {defineComponent,h} from 'vue'
import {useReadingRefresh} from '../../src/reading/useReadingRefresh'
afterEach(()=>{vi.useRealTimers();vi.restoreAllMocks()})
it('suppresses hidden or disabled refreshes, coalesces visible events, and cancels queued work on unmount',()=>{
 vi.useFakeTimers();let visible='hidden',enabled=true;vi.spyOn(document,'visibilityState','get').mockImplementation(()=>visible as DocumentVisibilityState);const refresh=vi.fn();const wrapper=mount(defineComponent({setup(){useReadingRefresh(refresh,()=>enabled);return()=>h('div')}}))
 window.dispatchEvent(new Event('focus'));document.dispatchEvent(new Event('visibilitychange'));vi.advanceTimersByTime(200);expect(refresh).not.toHaveBeenCalled();visible='visible';document.dispatchEvent(new Event('visibilitychange'));window.dispatchEvent(new Event('focus'));vi.advanceTimersByTime(100);expect(refresh).toHaveBeenCalledTimes(1);enabled=false;window.dispatchEvent(new Event('focus'));vi.advanceTimersByTime(200);expect(refresh).toHaveBeenCalledTimes(1);enabled=true;window.dispatchEvent(new Event('focus'));wrapper.unmount();vi.advanceTimersByTime(60000);window.dispatchEvent(new Event('focus'));vi.advanceTimersByTime(200);expect(refresh).toHaveBeenCalledTimes(1);expect(vi.getTimerCount()).toBe(0)
})
