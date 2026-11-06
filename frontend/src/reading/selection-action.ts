export interface SelectionBounds {left:number;right:number;top:number;bottom:number}
export function placeSelectionAction(r:SelectionBounds,width:number,height:number,viewportWidth:number,viewportHeight:number):{left:number;top:number}|null{
 if(r.bottom<=0||r.top>=viewportHeight||r.right<=0||r.left>=viewportWidth)return null
 const margin=12,left=Math.max(margin,Math.min(viewportWidth-width-margin,(r.left+r.right-width)/2))
 const below=r.bottom+8,top=below+height<=viewportHeight-margin?below:Math.max(margin,r.top-height-8)
 return {left,top}
}

export function lastVisibleSelectionRect<T extends SelectionBounds>(rects:T[],width:number,height:number):T|undefined{return rects.filter(r=>r.bottom>0&&r.top<height&&r.right>0&&r.left<width).at(-1)}
export function articleSelectionText(range:Range,body:Element):string{
 const parts:string[]=[]
 for(const sentence of body.querySelectorAll('[data-sentence]')){
  if(!range.intersectsNode(sentence))continue
  const part=document.createRange();part.selectNodeContents(sentence)
  if(part.compareBoundaryPoints(Range.START_TO_START,range)<0)part.setStart(range.startContainer,range.startOffset)
  if(part.compareBoundaryPoints(Range.END_TO_END,range)>0)part.setEnd(range.endContainer,range.endOffset)
  const text=part.toString().trim();if(text)parts.push(text)
 }
 return parts.join(' ')
}

export function completeWordSelection(range:Range):boolean{
 const word=/[A-Za-z'’‐‑-]/
 const before=range.startContainer.nodeType===Node.TEXT_NODE?(range.startContainer.textContent??'')[range.startOffset-1]:undefined
 const after=range.endContainer.nodeType===Node.TEXT_NODE?(range.endContainer.textContent??'')[range.endOffset]:undefined
 return !(before&&word.test(before))&&!(after&&word.test(after))
}
export function practiceSelectionText(range:Range,root:Element):string{
 const parts:string[]=[];let paragraph:Element|null=null,sentence:string|null=null
 for(const el of root.querySelectorAll('[data-practice-source],[data-practice-gap]')){
  if(!range.intersectsNode(el))continue
  const next=el.closest('p'),key=el.getAttribute('data-practice-sentence');if(paragraph&&next!==paragraph||sentence&&key!==sentence)parts.push(' ');paragraph=next;sentence=key
  if(el.hasAttribute('data-practice-gap')){parts.push('{{'+el.getAttribute('data-practice-gap')+'}}');continue}
  const clipped=document.createRange();clipped.selectNodeContents(el)
  if(clipped.compareBoundaryPoints(Range.START_TO_START,range)<0)clipped.setStart(range.startContainer,range.startOffset)
  if(clipped.compareBoundaryPoints(Range.END_TO_END,range)>0)clipped.setEnd(range.endContainer,range.endOffset)
  parts.push(clipped.toString())
 }
 return parts.join('').replace(/\s+/g,' ').trim()
}
export function bindSettledSelection(capture:()=>void,hide:()=>void,pointerRelease:()=>void=capture):()=>void{
 let pressing=false,timer:ReturnType<typeof setTimeout>|undefined
 const down=(event:Event)=>{if((event.target as Element|null)?.closest?.('.selection-lookup-button'))return;pressing=true;clearTimeout(timer);hide()}
 const up=()=>{pressing=false;clearTimeout(timer);pointerRelease()}
 const keyup=(event:KeyboardEvent)=>{if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Home','End','Shift'].includes(event.key)){clearTimeout(timer);if(!pressing)capture()}}
 const changed=()=>{clearTimeout(timer);if(!pressing)timer=setTimeout(capture,180)}
 const cancel=()=>{pressing=false;clearTimeout(timer);hide()}
 document.addEventListener('pointerdown',down);document.addEventListener('pointerup',up);document.addEventListener('pointercancel',cancel);document.addEventListener('selectionchange',changed);document.addEventListener('keyup',keyup)
 return()=>{clearTimeout(timer);document.removeEventListener('pointerdown',down);document.removeEventListener('pointerup',up);document.removeEventListener('pointercancel',cancel);document.removeEventListener('selectionchange',changed);document.removeEventListener('keyup',keyup)}
}

export function sourceWordAtPoint(x:number,y:number,root:Element):Range|null{
 const doc=document as Document&{caretPositionFromPoint?:(x:number,y:number)=>{offsetNode:Node;offset:number}|null;caretRangeFromPoint?:(x:number,y:number)=>Range|null}
 const caret=doc.caretPositionFromPoint?.(x,y),fallback=caret?null:doc.caretRangeFromPoint?.(x,y)
 const node=caret?.offsetNode??fallback?.startContainer,offset=caret?.offset??fallback?.startOffset
 if(!node||offset===undefined||node.nodeType!==Node.TEXT_NODE||!root.contains(node)||!node.parentElement?.closest('[data-practice-source]'))return null
 const match=Array.from((node.textContent??'').matchAll(/[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*/g)).find(m=>m.index<=offset&&offset<m.index+m[0].length)
 if(!match)return null
 const range=document.createRange();range.setStart(node,match.index);range.setEnd(node,match.index+match[0].length);return range
}
