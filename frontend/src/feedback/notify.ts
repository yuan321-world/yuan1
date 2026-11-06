import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'

export function notifySuccess(message: string) {
  ElMessage({ message, type: 'success', duration: 4000, showClose: true, grouping: true, customClass: 'loop-toast' })
  // Success-only messages are polite statuses; Element Plus defaults to assertive alerts.
  document.querySelectorAll('.loop-toast').forEach(element => {
    element.setAttribute('role', 'status'); element.setAttribute('aria-live', 'polite')
  })
}
