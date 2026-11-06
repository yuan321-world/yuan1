export interface WallPan { x:number; y:number; scale:number }
export interface WallRectangle { x:number; y:number; width:number; height:number }
export function toWallPoint(x:number,y:number,pan:WallPan){return {x:(x-pan.x)/pan.scale,y:(y-pan.y)/pan.scale}}
export function fitWall(rects:WallRectangle[],width:number,height:number):WallPan{
 const minX=Math.min(0,...rects.map(r=>r.x)),minY=Math.min(0,...rects.map(r=>r.y)),maxX=Math.max(700,...rects.map(r=>r.x+r.width)),maxY=Math.max(500,...rects.map(r=>r.y+r.height))
 const scale=Math.max(.02,Math.min(1,(width-60)/(maxX-minX),(height-200)/(maxY-minY)))
 return {scale,x:(width-(maxX-minX)*scale)/2-minX*scale,y:90-minY*scale}
}
