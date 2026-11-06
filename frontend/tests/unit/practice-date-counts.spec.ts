import {describe,it,expect,vi} from 'vitest'
import {catalogDateCounts} from '@/practice/catalog-date-counts'

describe('catalog year counts match a year click that clears the month',()=>{
 it('keeps the selected results and months but counts years without the outgoing month',async()=>{
  const fetch=vi.fn(async(q:URLSearchParams)=>q.has('month')?{items:['march'],total:1,years:[{value:'2023',count:1}],months:[{value:'3',count:1}]}:{items:['other'],total:70,years:[{value:'2023',count:9},{value:'2024',count:6}],months:[]})
  const q=new URLSearchParams('exam=CET4&year=2023&month=3&page=2&readingType=MATCH')
  const result=await catalogDateCounts(q,fetch,'years')
  expect(result).toMatchObject({items:['march'],total:1,years:[{value:'2023',count:9},{value:'2024',count:6}],months:[{value:'3',count:1}]})
  expect(q.get('month')).toBe('3')
  const countsQuery=fetch.mock.calls.find(([query])=>!query.has('month'))![0]
  expect(countsQuery.get('exam')).toBe('CET4')
  expect(countsQuery.get('year')).toBe('2023')
  expect(countsQuery.get('readingType')).toBe('MATCH')
  expect(countsQuery.get('pageSize')).toBe('1')
 })
 it('reuses the existing facet response when no month is selected',async()=>{
  const fetch=vi.fn(async()=>({year:[{value:'2024',count:6}],month:[]}))
  await expect(catalogDateCounts(new URLSearchParams('exam=CET4'),fetch,'year')).resolves.toEqual({year:[{value:'2024',count:6}],month:[]})
  expect(fetch).toHaveBeenCalledTimes(1)
 })
})
