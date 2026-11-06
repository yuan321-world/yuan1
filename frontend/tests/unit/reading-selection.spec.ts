import { expect, it } from 'vitest'
import { normalizeWord, selectionFromRange, rangeFromOffsets } from '../../src/reading/selection'
import { readFileSync } from 'node:fs'
it('agrees with the shared server token cases', () => {
  const data = JSON.parse(readFileSync('../content/english-token-cases.v1.json', 'utf8')) as { cases: Array<{ surface: string; normalized: string | null }> }
  for (const sample of data.cases) expect(normalizeWord(sample.surface)).toBe(sample.normalized)
})
it('accepts only whole English tokens and uses sentence-local UTF-16 offsets', () => {
  expect(normalizeWord('learner’s')).toBe("learner's")
  expect(normalizeWord('well-being')).toBe('well-being')
  expect(normalizeWord("can't")).toBe("can't")
  expect(normalizeWord('two words')).toBeNull()
  expect(normalizeWord('study.')).toBeNull()
  expect(normalizeWord('a'.repeat(64))).toBe('a'.repeat(64))
  expect(normalizeWord('a'.repeat(65))).toBeNull()
  const root = document.createElement('div'); root.innerHTML = '<span data-sentence="p0001-s0001">😀 <b>studies</b> help.</span><span data-sentence="p0001-s0002">Next.</span>'
  const text = root.querySelector('b')!.firstChild!
  const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 7)
  expect(selectionFromRange(range, '10', '11')).toMatchObject({ sentenceKey: 'p0001-s0001', startOffset: 3, endOffset: 10, surfaceForm: 'studies' })
  range.setEnd(text, 3); expect(selectionFromRange(range, '10', '11')).toBeNull()
  range.setEnd(root.lastElementChild!.firstChild!, 2); expect(selectionFromRange(range, '10', '11')).toBeNull()
})
it('restores a UTF-16 range across nested sentence text without splitting emoji', () => {
  const sentence = document.createElement('span'); sentence.innerHTML = '😀 <b>studies</b> help.'
  expect(rangeFromOffsets(sentence, 3, 10)?.toString()).toBe('studies')
  expect(rangeFromOffsets(sentence, 1, 3)).toBeNull()
  expect(rangeFromOffsets(sentence, 0, 999)).toBeNull()
})
