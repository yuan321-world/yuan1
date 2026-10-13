export interface NavigationItem { label: string; to: string }
export const learnerNavigation: NavigationItem[] = [
  { label: '首页', to: '/' },
  { label: '背单词', to: '/vocabulary' },
  { label: '外刊精读', to: '/reading' },
  { label: '专项练习', to: '/practice' },
  { label: '学习统计', to: '/stats' },
  { label: '登录', to: '/login' },
  { label: '管理端预览', to: '/admin' },
]
