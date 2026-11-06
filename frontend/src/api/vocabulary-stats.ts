import {businessGet} from './business'
export interface VocabularyStats {today:string;weekStart:string;days:{date:string;newLearned:number;reviewed:number}[];totalLearned:number;totalReviewPasses:number;due:number;paused:number;completedRounds:number;nextDueAt:string|null;independentReviewPasses?:number;streakDays?:number;longestStreak?:number;difficult?:number;recentDays?:{date:string;newLearned:number;reviewed:number;independentReviewed?:number}[]}
export const vocabularyStats=(signal?:AbortSignal)=>businessGet<VocabularyStats>('/api/v1/vocabulary/learning/stats',signal)
