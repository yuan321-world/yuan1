import {describe,it,expect} from 'vitest'
import {mount} from '@vue/test-utils'
// @ts-expect-error SFC types are checked by vue-tsc; node test config has no Vue shim.
import PracticeFilters from '@/components/practice/PracticeFilters.vue'
import {emptyFilters,parseFilters} from '@/practice/catalog-query'
describe('simple past-paper filters',()=>{
 it('shows counts for each date and totals across the whole facet, including zero-count years',async()=>{
  const facets={source:[],readingType:[],topic:[],state:[],paperNo:[],year:[{value:'2024',count:6},{value:'2023',count:9}],month:[{value:'12',count:3},{value:'6',count:3}]}
  const w=mount(PracticeFilters,{props:{modelValue:{...emptyFilters(),source:'PAST',year:'2024',month:'6'},catalogLayout:true,section:'listening',examCodes:['CET4','CET6'],facets}})
  const count=(label:string)=>w.get(`button[aria-label="${label}"] .practice-filter-count`).text()
  expect(count('年份：2024年')).toBe('6')
  expect(count('年份：全部')).toBe('15')
  expect(count('年份：2026年')).toBe('0')
  expect(count('月份：12月')).toBe('3')
  expect(count('月份：全部')).toBe('6')
  await w.setProps({facets:{...facets,month:[{value:'3',count:1}]}})
  expect(count('月份：3月')).toBe('1')
  expect(count('月份：全部')).toBe('1')
  expect(w.find('button[aria-label="月份：6月"]').exists()).toBe(false)
 })
 it('reserves the count position while facets load without showing a false zero',async()=>{
  const w=mount(PracticeFilters,{props:{modelValue:{...emptyFilters(),source:'PAST'},catalogLayout:true,section:'paper',examCodes:['CET4','CET6']}})
  expect(w.get('button[aria-label="年份：2024年"] .practice-filter-count').text()).toBe('—')
  await w.setProps({facets:{source:[],readingType:[],topic:[],state:[],paperNo:[],year:[{value:'2024',count:6}],month:[]}})
  expect(w.get('button[aria-label="年份：2024年"] .practice-filter-count').text()).toBe('6')
  expect(w.get('button[aria-label="年份：2024年"]').attributes('aria-description')).toContain('6套')
 })
 it('shows only exam, available dates, reading type and clear; applies a date immediately',async()=>{
  const w=mount(PracticeFilters,{props:{modelValue:{...emptyFilters(),source:'PAST'},catalogLayout:true,sourceLocked:true,section:'reading',examCodes:['CET4','CET6'],facets:{source:[],readingType:[],topic:[],state:[],year:[{value:'2024',count:3}],month:[{value:'6',count:3}],paperNo:[]}}})
  expect(w.find('[aria-label="搜索题组"]').exists()).toBe(false)
  expect(w.find('[aria-label="练习状态筛选"]').exists()).toBe(false)
  expect(w.find('[aria-label="来源筛选"]').exists()).toBe(false)
  expect(w.find('[aria-label="题型筛选"]').exists()).toBe(true)
  await w.get('button[aria-label="月份：6月"]').trigger('click')
  expect(w.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({month:'6',page:1})
  expect(w.emitted('apply')).toHaveLength(1)
  await w.setProps({section:'listening'});expect(w.find('[aria-label="题型筛选"]').exists()).toBe(false)
 })
 it('keeps the existing admin controls',()=>{const w=mount(PracticeFilters,{props:{modelValue:emptyFilters(true),admin:true}});expect(w.find('[aria-label="搜索题组"]').exists()).toBe(true);expect(w.find('[aria-label="发布状态筛选"]').exists()).toBe(true)})
})

it('CET months use the available year facets without inventing regular or exceptional sessions',async()=>{
 const props={modelValue:{...emptyFilters(),source:'PAST',year:'2023',month:'3'},catalogLayout:true,section:'listening',examCodes:['CET4','CET6'],facets:{source:[],readingType:[],topic:[],state:[],year:[],month:[{value:'3',count:1}],paperNo:[]}}
 const w=mount(PracticeFilters,{props})
 expect(w.find('button[aria-label="月份：3月"]').exists()).toBe(true)
 expect(w.find('button[aria-label="月份：6月"]').exists()).toBe(false)
 expect(w.find('button[aria-label="月份：12月"]').exists()).toBe(false)
 await w.get('button[aria-label="年份：2024年"]').trigger('click')
 expect(w.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({year:'2024',month:'',page:1})
 await w.setProps({facets:{...props.facets,month:[{value:'6',count:1}]}})
 expect(w.find('button[aria-label="月份：3月"]').exists()).toBe(false)
 expect(w.find('button[aria-label="月份：6月"]').exists()).toBe(true)
})

it('history never accepts a free-form year either',()=>{
 const w=mount(PracticeFilters,{props:{modelValue:{...emptyFilters(),source:'PAST'},catalogLayout:true,history:true,facets:{source:[],readingType:[],topic:[],state:[],year:[{value:'2024',count:3}],month:[],paperNo:[]}}})
 expect(w.find('input[aria-label="考试年份筛选"]').exists()).toBe(false)
 expect(w.find('select[aria-label="考试年份筛选"]').exists()).toBe(true)
 expect(w.get('select[aria-label="考试年份筛选"]').text()).toContain('2024年')
 expect(w.find('[aria-label="题组排序"]').exists()).toBe(false)
})

it('shows the requested fixed year grid from 2026 through 2016 without imported content',()=>{
 const w=mount(PracticeFilters,{props:{modelValue:{...emptyFilters(),source:'PAST'},catalogLayout:true,section:'listening',examCodes:['CET4','CET6']}})
 for(let year=2026;year>=2016;year--)expect(w.find(`button[aria-label="年份：${year}年"]`).exists()).toBe(true)
 expect(w.text()).not.toContain('暂无可选年份')
 expect(w.text()).not.toContain('规划中')
})

it('admin offers exam filters only for past papers and clears them when leaving past papers',async()=>{
 const w=mount(PracticeFilters,{props:{modelValue:{...emptyFilters(true),source:'',exam:'CET4',readingType:'WRITING'},admin:true}})
 expect(w.find('[aria-label="考试类别筛选"]').exists()).toBe(false)
 expect(w.find('input[aria-label="考试年份筛选"]').exists()).toBe(false)
 await w.setProps({modelValue:{...emptyFilters(true),source:'PAST',exam:'CET4',year:'2024',month:'6',paperNo:'1',readingType:'WRITING'}})
 expect(w.find('[aria-label="考试类别筛选"]').exists()).toBe(true)
 await w.get('[aria-label="来源筛选"]').setValue('ORIGINAL')
 expect(w.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({source:'ORIGINAL',exam:'',year:'',month:'',paperNo:'',readingType:'WRITING'})
})
it('admin legacy original and mixed-source links cannot carry exam conditions',()=>{
 for(const source of ['', 'ORIGINAL'])expect(parseFilters({exam:'CET4',source,year:'2024',month:'6',paperNo:'1',readingType:'TRANSLATION'},true)).toMatchObject({exam:'',year:'',month:'',paperNo:'',readingType:'TRANSLATION'})
 expect(parseFilters({exam:'CET4',source:'PAST',year:'2024'},true)).toMatchObject({exam:'CET4',year:'2024'})
})
