import { expect,it } from 'vitest'
import { toWallPoint, fitWall } from '../../src/wall/layout'
it('converts a double click after panning and zooming to its canvas coordinate',()=>{expect(toWallPoint(400,300,{x:100,y:50,scale:.5})).toEqual({x:600,y:500})})
it('fits all notes including positions beyond the original canvas',()=>{const p=fitWall([{x:-300,y:200,width:280,height:420},{x:1800,y:2000,width:280,height:420}],1000,700);expect(p.scale).toBeGreaterThan(0);expect(-300*p.scale+p.x).toBeGreaterThanOrEqual(20);expect(2420*p.scale+p.y).toBeLessThanOrEqual(600)})
