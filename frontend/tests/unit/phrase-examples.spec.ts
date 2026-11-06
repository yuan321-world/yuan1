import {describe,it,expect} from 'vitest'
import {phraseExamples} from '../../src/vocabulary/phrase-examples'
import editorial from '../../src/vocabulary/phrase-examples.json'

describe('local phrase examples',()=>{
  it('provides two fixed bilingual examples for each edited phrase',()=>{
    for(const [phrase,pairs] of Object.entries(editorial)){
      const result=phraseExamples(phrase)
      expect(result).toHaveLength(2)
      expect(result.map(e=>e.text)).toEqual(pairs.map(p=>p[0]))
      result.forEach(e=>expect(e.translationZh).toMatch(/\p{Script=Han}/u))
    }
  })
  it('normalizes case and whitespace and reuses exact phrase occurrences',()=>{
    expect(phraseExamples('  PLAY   a ROLE ')).toEqual(phraseExamples('play a role'))
    expect(phraseExamples('play a role in')).toHaveLength(2)
    expect(phraseExamples('a pair of')[0]?.translationZh).toContain('一双')
  })
  it('does not match fragments or generate sentences for unknown expressions',()=>{
    for(const phrase of ['play a rol','pair of comfort','zzqxj abcdef','play a role.','a'.repeat(81)])expect(phraseExamples(phrase)).toEqual([])
  })
  it('keeps attribution for reused public corpus examples',()=>{
    const result=phraseExamples('comfort zone')
    expect(result.length).toBeGreaterThan(0)
    expect(result[0]?.attribution).toMatchObject({englishId:'4297220',englishAuthor:'Hybrid',chineseId:'8932001',chineseAuthor:'iiujik'})
  })
})
