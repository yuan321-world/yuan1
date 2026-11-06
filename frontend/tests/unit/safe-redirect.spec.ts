import { expect, it } from 'vitest'
import { safeRedirect } from '../../src/router/safe-redirect'
it('accepts only known local reading and admin paths with bounded source positions', () => {
  expect(safeRedirect('/reading/12?version=8&sentence=p0001-s0001&start=0&end=5', false)).toContain('/reading/12')
  for (const path of ['//evil.invalid', 'https://evil.invalid', '/reading/1?unknown=x', '/reading/1?start=9000', '/reading/1/edit', '/admin/articles']) expect(safeRedirect(path, false)).toBe('/')
  expect(safeRedirect('/admin/articles/12/edit', true)).toBe('/admin/articles/12/edit')
  expect(safeRedirect('/admin/articles/12/quiz?version=8',true)).toBe('/admin/articles/12/quiz?version=8')
  expect(safeRedirect('/admin/articles/12/quiz?userId=4',true)).toBe('/admin')
  expect(safeRedirect('/writing',false)).toBe('/writing')
  expect(safeRedirect('/writing?userId=2',false)).toBe('/')
  expect(safeRedirect('/admin/articles/12/writing?version=8',true)).toBe('/admin/articles/12/writing?version=8')
})
