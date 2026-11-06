import {expect,it} from 'vitest'
import {validateSpellingPrefix} from '../../src/vocabulary/spelling'
it('checks every prefix, case and edits; complete words alone pass',()=>{
 expect(validateSpellingPrefix('','apple')).toBe('EMPTY')
 for(const value of ['a','ap','APP','appl'])expect(validateSpellingPrefix(value,'apple')).toBe('PREFIX')
 for(const value of ['x','ax','apx','applex',' apple','apple '])expect(validateSpellingPrefix(value,'apple')).toBe('INVALID')
 expect(validateSpellingPrefix('APPLE','apple')).toBe('COMPLETE')
})
