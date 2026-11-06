export interface SelectionContext { articleId: string; versionId: string; sentenceKey: string; startOffset: number; endOffset: number; surfaceForm: string }
const token = /[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*/g
export function normalizeWord(value: string): string | null {
  if (value.length > 64 || !/^[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*$/.test(value)) return null
  return value.toLowerCase().replaceAll('’', "'").replace(/[‐‑]/g, '-')
}
function sentenceElement(node: Node): HTMLElement | null { return (node.nodeType === Node.ELEMENT_NODE ? node as Element : node.parentElement)?.closest<HTMLElement>('[data-sentence]') ?? null }
export function rangeFromOffsets(sentence: HTMLElement, start: number, end = start): Range | null {
  const text = sentence.textContent ?? ''
  const boundary = (offset: number) => Number.isInteger(offset) && offset >= 0 && offset <= text.length && !(offset > 0 && offset < text.length && /[\uD800-\uDBFF]/.test(text[offset - 1]!) && /[\uDC00-\uDFFF]/.test(text[offset]!))
  if (!boundary(start) || !boundary(end) || end < start) return null
  const nodes: Text[] = [], walker = document.createTreeWalker(sentence, NodeFilter.SHOW_TEXT)
  let node: Node | null; while ((node = walker.nextNode())) nodes.push(node as Text)
  if (!nodes.length) return null
  function point(offset: number): [Text, number] {
    let remaining = offset
    for (const node of nodes) { if (remaining <= node.length) return [node, remaining]; remaining -= node.length }
    return [nodes.at(-1)!, nodes.at(-1)!.length]
  }
  const range = document.createRange(); range.setStart(...point(start)); range.setEnd(...point(end)); return range
}
export function selectionFromRange(range: Range, articleId: string, versionId: string): SelectionContext | null {
  if (range.collapsed) return null
  const sentence = sentenceElement(range.startContainer)
  if (!sentence || sentence !== sentenceElement(range.endContainer)) return null
  const prefix = range.cloneRange(); prefix.selectNodeContents(sentence); prefix.setEnd(range.startContainer, range.startOffset)
  const startOffset = prefix.toString().length, surfaceForm = range.toString(), endOffset = startOffset + surfaceForm.length
  if (!normalizeWord(surfaceForm)) return null
  const whole = sentence.textContent ?? ''
  const isWholeToken = Array.from(whole.matchAll(token)).some(match => match.index === startOffset && match.index + match[0].length === endOffset)
  if (!isWholeToken || whole.slice(startOffset, endOffset) !== surfaceForm) return null
  return { articleId, versionId, sentenceKey: sentence.dataset.sentence!, startOffset, endOffset, surfaceForm }
}
export function wordAtPoint(x: number, y: number, articleId: string, versionId: string): SelectionContext | null {
  const doc = document as Document & { caretPositionFromPoint?: (x: number, y: number) => { offsetNode: Node; offset: number } | null; caretRangeFromPoint?: (x: number, y: number) => Range | null }
  const caret = doc.caretPositionFromPoint?.(x, y)
  const fallback = !caret ? doc.caretRangeFromPoint?.(x, y) : null
  const node = caret?.offsetNode ?? fallback?.startContainer
  const offset = caret?.offset ?? fallback?.startOffset
  if (!node || offset === undefined) return null
  const sentence = sentenceElement(node)
  if (!sentence) return null
  const prefix = document.createRange(); prefix.selectNodeContents(sentence); prefix.setEnd(node, offset)
  const position = prefix.toString().length, whole = sentence.textContent ?? ''
  const match = Array.from(whole.matchAll(token)).find(m => m.index <= position && position < m.index + m[0].length)
  if (!match || !normalizeWord(match[0])) return null
  return { articleId, versionId, sentenceKey: sentence.dataset.sentence!, startOffset: match.index, endOffset: match.index + match[0].length, surfaceForm: match[0] }
}
