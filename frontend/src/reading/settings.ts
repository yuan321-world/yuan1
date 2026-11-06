export interface ReaderSettings { fontSize: number; lineHeight: number; font: 'serif' | 'sans'; align: 'left' | 'center' | 'right' | 'justify'; bilingual: boolean; keywords: boolean }
export const createReaderSettings = (): ReaderSettings => ({ fontSize: 18, lineHeight: 1.6, font: 'serif', align: 'left', bilingual: false, keywords: false })
