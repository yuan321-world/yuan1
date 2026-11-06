import type {Metadata,PracticeQuestionInput} from '@/api/practice'
import {parseMatchBody} from '@/practice/match-material'
import type {PublicationCheck} from '@/api/admin-practice'
export const questionTypes:Record<string,string>={MAIN_IDEA:'主旨大意',DETAIL:'事实细节',INFERENCE:'推理判断',VOCABULARY:'词义猜测',ATTITUDE:'作者态度'}
export const blankQuestion=():PracticeQuestionInput=>({key:'q_'+crypto.randomUUID().replaceAll('-',''),type:'DETAIL',stem:'',options:['','','',''],correctOption:null,explanation:'',sortOrder:0})
export const blankMetadata=():Metadata=>({title:'',titleZh:'',sourceKind:'ORIGINAL',topic:'education',year:null,month:null,paperNo:null,sourceName:'',sourceUrl:null,usageNote:''})
export function questionProblems(q:PracticeQuestionInput,reviewed:boolean){const a:string[]=[];if(!q.stem.trim())a.push('缺题干');if(q.type!=='CLOZE'&&q.type!=='MATCH'){if(q.options.some(o=>!o.trim())||q.options.length!==4)a.push('缺选项');if(new Set(q.options.map(o=>o.trim().toLowerCase())).size!==4)a.push('重复选项')}else if(q.type==='CLOZE'&&!/^(2[6-9]|3[0-5])$/.test(q.stem))a.push('缺题干');if(!q.correctOption)a.push('未设置答案');if(!q.explanation.trim())a.push('缺解析');if(!reviewed)a.push('未校对');return a}
export function draftChecks(metadata:Metadata,questions:PracticeQuestionInput[],reviewed:string[],sourceReviewed:boolean,cloze?:{readingType:string;wordBank:string[];body:string}):PublicationCheck[]{const checks:PublicationCheck[]=[];const add=(path:string,message:string,questionKey:string|null=null)=>checks.push({path,message,questionKey});
 for(const k of ['title','titleZh','usageNote'] as const)if(!metadata[k]?.trim())add('metadata.'+k,({title:'填写英文标题',titleZh:'填写中文标题',usageNote:'填写来源与使用说明'})[k]);
 if(metadata.sourceKind==='PAST'){if(!metadata.sourceName.trim())add('metadata.sourceName','真题须填写来源名称');for(const k of ['year','month','paperNo'] as const)if(!metadata[k])add('metadata.'+k,'补充真实考试'+({year:'年份',month:'月份',paperNo:'套次'})[k])}
 for(const q of questions)for(const problem of questionProblems(q,reviewed.includes(q.key))){const field=problem==='缺题干'?'stem':problem.includes('选项')?'options':problem==='未设置答案'?'correctOption':problem==='缺解析'?'explanation':'review';add(`questions[${q.key}].${field}`,problem,q.key)}
 if(cloze?.readingType==='CLOZE'){
 const words=cloze.wordBank.map(w=>w.trim().toLowerCase());if(words.length!==15||words.some(w=>!w)||new Set(words).size!==15)add('wordBank','填写15个非空且不重复的共享词语');
 const markers=[...cloze.body.matchAll(/\{\{([^{}]*)\}\}/g)].map(m=>m[1]);if(markers.length!==10||Array.from({length:10},(_,i)=>String(i+26)).some(n=>markers.filter(m=>m===n).length!==1))add('body','原文须且仅须包含各一次的 {{26}} 至 {{35}} 空位');
 if(questions.length!==10||questions.some((q,i)=>q.type!=='CLOZE'||q.stem!==String(26+i)))add('questions','选词填空固定包含26至35共10空');
 const used=new Set<string>();for(const q of questions){if(q.correctOption&&used.has(q.correctOption))add(`questions[${q.key}].correctOption`,'每个答案词只能使用一次',q.key);if(q.correctOption&&!/^[A-O]$/.test(q.correctOption))add(`questions[${q.key}].correctOption`,'答案必须为A至O',q.key);if(q.correctOption)used.add(q.correctOption)}
 }
 if(cloze?.readingType==='MATCH'){
 const material=parseMatchBody(cloze.body);if(material.problem)add('body',material.problem);
 if(questions.length!==10||questions.some(q=>q.type!=='MATCH'))add('questions','长篇匹配固定包含36至45共10题');
 if(cloze.wordBank.length)add('wordBank','长篇匹配不使用共享词库');
 for(const q of questions)if(q.correctOption&&!material.paragraphs.some(p=>p.letter===q.correctOption))add(`questions[${q.key}].correctOption`,'答案须引用原文中存在的段落字母',q.key)
 }
 if(!sourceReviewed)add('sourceReview','尚未确认文章与来源校对');return checks
}

export const blankClozeQuestions=():PracticeQuestionInput[]=>Array.from({length:10},(_,i)=>({key:'gap'+(26+i),type:'CLOZE',stem:String(26+i),options:[],correctOption:null,explanation:'',sortOrder:i}))

export const blankMatchQuestions=():PracticeQuestionInput[]=>Array.from({length:10},(_,i)=>({key:"match"+(36+i),type:"MATCH",stem:"",options:[],correctOption:null,explanation:"",sortOrder:i}))
