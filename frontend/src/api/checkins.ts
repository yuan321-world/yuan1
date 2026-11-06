import {businessGet} from './business'
export interface CheckinStatus {readCompleted:boolean;practiceCompleted:boolean;hasQuiz:boolean;articleQualified:boolean;qualifiedDate:string|null;todayChecked:boolean}
export interface CheckinWeek {today:string;weekStart:string;days:{date:string;checked:boolean}[];todayChecked:boolean;totalDays:number;qualifiedArticles:number;completedReadings:number;completedAttempts:number}
export const articleCheckin=(article:string,version:string,signal?:AbortSignal)=>businessGet<CheckinStatus>(`/api/v1/reading/articles/${encodeURIComponent(article)}/versions/${encodeURIComponent(version)}/checkin`,signal)
export const checkinWeek=(signal?:AbortSignal)=>businessGet<CheckinWeek>('/api/v1/reading/checkins/week',signal)
