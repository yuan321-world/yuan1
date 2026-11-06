export function wallTextParts(text:string,announcement:boolean):{text:string;bold:boolean}[]{
 if(!announcement)return [{text,bold:false}]
 const parts:{text:string;bold:boolean}[]=[];let last=0
 for(const match of text.matchAll(/\*\*([^*]+)\*\*/g)){const index=match.index!;if(index>last)parts.push({text:text.slice(last,index),bold:false});parts.push({text:match[1]!,bold:true});last=index+match[0].length}
 if(last<text.length)parts.push({text:text.slice(last),bold:false});return parts.length?parts:[{text:'',bold:false}]
}
