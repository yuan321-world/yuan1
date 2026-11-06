import type {ComputedRef,InjectionKey} from 'vue'
import type {PracticeSelectionContext} from '@/api/selection-translation'
export const practiceSelectionContextKey:InjectionKey<ComputedRef<PracticeSelectionContext&{enabled:boolean}>>=Symbol('practice-selection-context')
