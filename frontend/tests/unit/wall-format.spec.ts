import { expect,it } from 'vitest'
import { wallTextParts } from '../../src/wall/format'
it('only announcements parse emphasis and keep HTML as literal text',()=>{expect(wallTextParts('**<img src=x>**',false)).toEqual([{text:'**<img src=x>**',bold:false}]);expect(wallTextParts('请**友好交流**。<script>x</script>',true)).toEqual([{text:'请',bold:false},{text:'友好交流',bold:true},{text:'。<script>x</script>',bold:false}])})
