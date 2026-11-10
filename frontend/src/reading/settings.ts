export const readerFonts = [
  {value:'serif',label:'Georgia · 衬线',family:'Georgia, "Times New Roman", serif'},
  {value:'times',label:'Times New Roman · 衬线',family:'"Times New Roman", Times, serif'},
  {value:'palatino',label:'Palatino · 衬线',family:'"Palatino Linotype", "Book Antiqua", Palatino, serif'},
  {value:'cambria',label:'Cambria · 衬线',family:'Cambria, Georgia, serif'},
  {value:'arial',label:'Arial · 无衬线',family:'Arial, Helvetica, sans-serif'},
  {value:'verdana',label:'Verdana · 无衬线',family:'Verdana, Geneva, sans-serif'},
  {value:'sans',label:'系统字体 · 无衬线',family:'system-ui, -apple-system, "Segoe UI", sans-serif'},
] as const
export interface ReaderSettings { fontSize: number; lineHeight: number; font: typeof readerFonts[number]['value']; align: 'left' | 'center' | 'right' | 'justify'; bilingual: boolean; keywords: boolean }
export const readerFontFamily = (font:ReaderSettings['font']) => readerFonts.find(item=>item.value===font)?.family??readerFonts[0].family
export const createReaderSettings = (): ReaderSettings => ({ fontSize: 17, lineHeight: 1.5, font: 'serif', align: 'left', bilingual: false, keywords: false })
