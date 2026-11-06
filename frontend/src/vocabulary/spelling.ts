export function validateSpellingPrefix(input: string, target: string): 'EMPTY' | 'PREFIX' | 'INVALID' | 'COMPLETE' {
  if (!input) return 'EMPTY'
  const value = input.toLowerCase(), expected = target.toLowerCase()
  if (!expected.startsWith(value)) return 'INVALID'
  return value === expected ? 'COMPLETE' : 'PREFIX'
}

export function retainedSpellingPrefix(input: string, target: string): string {
 let length=0
 while(length<input.length && input[length]!.toLowerCase()===target[length]?.toLowerCase())length++
 return input.slice(0,length)
}
