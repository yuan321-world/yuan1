export const examLabels:Record<string,string>={CET4:'四级',CET6:'六级',POSTGRAD1:'考研英语一',POSTGRAD2:'考研英语二'}
export const examTypes:Record<string,{code:string;label:string;available:boolean}[]>={CET4:[{code:'DETAILED',label:'仔细阅读',available:true},{code:'CLOZE',label:'选词填空',available:true},{code:'MATCH',label:'长篇匹配',available:true}],CET6:[{code:'DETAILED',label:'仔细阅读',available:false},{code:'CLOZE',label:'选词填空',available:false},{code:'MATCH',label:'长篇匹配',available:false}],POSTGRAD1:[{code:'DETAILED',label:'阅读理解',available:false},{code:'NEW',label:'新题型',available:false}],POSTGRAD2:[{code:'DETAILED',label:'阅读理解',available:false},{code:'NEW',label:'新题型',available:false}]}
export interface Filters {exam:string;readingType:string;listeningType:string;source:string;status:string;state:string;topic:string;year:string;month:string;paperNo:string;q:string;sort:string;page:number;pageSize:number}
export const emptyFilters=(admin=false):Filters=>({exam:admin?'':'CET4',readingType:'',listeningType:'',source:'',status:'',state:'',topic:'',year:'',month:'',paperNo:'',q:'',sort:admin?'NEWEST':'LATEST',page:1,pageSize:20})
export function filterQuery(f:Filters){const q=new URLSearchParams();for(const [k,v] of Object.entries(f))if(v!==''&&v!==undefined)q.set(k,String(v));return q}
export function parseFilters(query:Record<string,unknown>,admin=false):Filters{const f=emptyFilters(admin);for(const k of Object.keys(f) as (keyof Filters)[])if(typeof query[k]==='string'){if(k==='page'||k==='pageSize')f[k]=Math.max(1,Math.min(k==='pageSize'?50:100000,Number(query[k])||1));else f[k]=String(query[k])}if(admin&&f.source!=='PAST'){f.exam='';f.year='';f.month='';f.paperNo=''}if(f.source==='ORIGINAL'){f.year='';f.month='';f.paperNo=''}return f}

// Planned listening sections follow the CET paper structure; no listening service is enabled.
export const listeningTypes:Record<string,{code:string;label:string;available:boolean}[]>={
 CET4:[{code:'NEWS',label:'短篇新闻',available:false},{code:'CONVERSATION',label:'长对话',available:false},{code:'PASSAGE',label:'听力篇章',available:false}],
 CET6:[{code:'CONVERSATION',label:'长对话',available:false},{code:'PASSAGE',label:'听力篇章',available:false},{code:'LECTURE',label:'讲话/报道/讲座',available:false}]
}
export function sectionFilters(filters:Filters,section:string):Filters{
 const next={...filters}
 if(section!=='reading'||!examTypes[next.exam]?.some(t=>t.code===next.readingType))next.readingType=''
 if(section!=='listening'||!listeningTypes[next.exam]?.some(t=>t.code===next.listeningType))next.listeningType=''
 return next
}
