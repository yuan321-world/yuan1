/** Labels are part of the frozen material, never inferred from an unlabelled passage. */
export function parseMatchBody(body:string):{paragraphs:{letter:string;text:string}[];problem?:string}{
 const blocks=body.replace(/\r\n?/g,'\n').trim().split(/\n[\t ]*\n+/),paragraphs:{letter:string;text:string}[]=[]
 if(!body.trim()||blocks.length>26)return {paragraphs,problem:'长篇匹配须有1至26段、按空行分隔的原文'}
 for(const [i,block] of blocks.entries()){
  const match=block.trim().match(/^\[([A-Z])\][\t ]+([\s\S]+)$/)
  if(!match||match[1]!==String.fromCharCode(65+i)||!match[2]?.trim()||/\n\s*\[[A-Z]\]/.test(match[2]))return {paragraphs:[],problem:'每段须按 [A]、[B] 顺序标记，正文非空，段落间保留空行'}
  paragraphs.push({letter:match[1],text:match[2].trim()})
 }
 return {paragraphs}
}
