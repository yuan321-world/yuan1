import {mount} from '@vue/test-utils'
import {describe,it,expect} from 'vitest'
// @ts-expect-error SFC types are checked by vue-tsc.
import PronunciationControls from '../../src/components/common/PronunciationControls.vue'

describe('shared pronunciation controls',()=>{
  it('renders UK and US with the supplied general phonetic and speaker icons',()=>{
    const w=mount(PronunciationControls,{props:{phonetic:'bəˈnɑːnə'}})
    expect(w.findAll('b').map(b=>b.text())).toEqual(['UK','US'])
    expect(w.findAll('span').map(b=>b.text())).toEqual(['/bəˈnɑːnə/','/bəˈnɑːnə/'])
    expect(w.findAll('svg')).toHaveLength(2)
    expect(w.find('button').attributes('title')).toContain('通用标注')
  })
  it('does not duplicate phonetic slashes or invent missing phonetics',async()=>{
    const w=mount(PronunciationControls,{props:{phonetic:' /ˈæpəl/ '}})
    expect(w.find('span').text()).toBe('/ˈæpəl/')
    await w.setProps({phonetic:null})
    expect(w.findAll('span')).toHaveLength(0)
    expect(w.findAll('button').map(b=>b.text())).toEqual(['UK','US'])
  })
  it('keeps individual locale actions and caller accessible labels',async()=>{
    const w=mount(PronunciationControls,{props:{ukLabel:'英式朗读',usLabel:'美式朗读'}})
    const buttons=w.findAll('button')
    expect(buttons.map(b=>b.attributes('aria-label'))).toEqual(['英式朗读','美式朗读'])
    await buttons[0]!.trigger('click');await buttons[1]!.trigger('click')
    expect(w.emitted('speak')).toEqual([['en-GB'],['en-US']])
  })
})
