import words from './spelling-lexicon.json'

const lexicon = new Set(words)
const alphabet = 'abcdefghijklmnopqrstuvwxyz'

export function spellingNeighbors(lemma: string): string[] {
  const word = lemma.toLowerCase()
  if (!/^[a-z]{3,32}$/.test(word)) return []
  const matches = new Set<string>()
  function add(candidate: string) {
    if (candidate !== word && lexicon.has(candidate)) matches.add(candidate)
  }
  for (let i = 0; i <= word.length; i++) {
    const left = word.slice(0, i)
    const right = word.slice(i)
    for (const letter of alphabet) {
      add(left + letter + right)
      if (right) add(left + letter + right.slice(1))
    }
    if (right) add(left + right.slice(1))
    if (right.length >= 2) add(left + right[1] + right[0] + right.slice(2))
  }
  return [...matches].sort((a, b) =>
    Number(b.length === word.length) - Number(a.length === word.length) || a.localeCompare(b)
  ).slice(0, 5)
}
