import LearningStatsView from '../views/LearningStatsView.vue'
import VocabularyView from '../views/VocabularyView.vue'
import AdminArticleQuizView from '../views/AdminArticleQuizView.vue'
import { createRouter, createWebHistory } from 'vue-router'
import LearnerLayout from '@/layouts/LearnerLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import HomeView from '@/views/HomeView.vue'
import PendingFeatureView from '@/views/PendingFeatureView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import AccountView from '@/views/AccountView.vue'
import AdminArticlesView from '@/views/AdminArticlesView.vue'
import AdminArticleEditorView from '@/views/AdminArticleEditorView.vue'
import AdminArticlePreviewView from '@/views/AdminArticlePreviewView.vue'
import ReadingListView from '@/views/ReadingListView.vue'
import ReadingDetailView from '@/views/ReadingDetailView.vue'
import AdminDashboardView from '@/views/AdminDashboardView.vue'
import { useAuthStore } from '@/stores/auth'
import { useAuthDialogStore } from '@/stores/auth-dialog'
import { safeRedirect } from '@/router/safe-redirect'
import { adminIdentity } from '@/api/identity'
import { onRemoteIdentityChange } from '@/auth/token-session'
import { installNavigationRecovery } from './navigation-recovery'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/', component: LearnerLayout, children: [
        { path: '', component: HomeView, meta: { title: '首页', public: true } },
        { path: 'login', component: HomeView, meta: { title: '登录', public: true } },
        { path: 'register', component: HomeView, meta: { title: '注册', public: true } },
        { path: 'forgot-password', component: HomeView, meta: { title: '重置密码', public: true } },
        { path: 'account', component: AccountView, meta: { title: '账户设置' } },
        { path: 'vocabulary', component: VocabularyView, meta: { title: '背单词' } },
        { path: 'reading', component: ReadingListView, meta: { title: '外刊精读', public: true } },
        { path: 'notebook', redirect: to => ({path:'/vocabulary',query:{...to.query,tab:'words'},hash:to.hash}) },
        { path: 'wall', component: () => import('@/views/WallView.vue'), meta: { title: '留言墙', public: true } },
        { path: 'writing', redirect: '/reading' },
        { path: 'reading/:articleId', component: ReadingDetailView, meta: { title: '阅读文章', public: true } },
        { path: 'practice', component: () => import('@/views/PracticeEntryView.vue'), meta: { title: '专项练习', public: true } },
        { path: 'practice/cet', component: () => import('@/views/PracticeLibraryView.vue'), meta: { title: '大学英语（四六级）', public: true } },
        { path: 'practice/postgraduate', component: () => import('@/views/PracticeLibraryView.vue'), meta: { title: '考研英语', public: true } },
        { path: 'practice/cet4', component: () => import('@/views/PracticeLibraryView.vue'), meta: { title: '四级阅读题库', public: true } },
        { path: 'practice/paper-attempts/:id', component: () => import('@/views/PracticePaperAttemptView.vue'), meta: { title: '整卷练习' } },
        { path: 'practice/subjective-attempts/:id', component: () => import('@/views/PracticeSubjectiveAttemptView.vue'), meta: { title: '写作与翻译练习' } },
        { path: 'practice/attempts/:id', component: () => import('@/views/PracticeAttemptView.vue'), meta: { title: '专项练习作答' } },
        { path: 'stats', component: LearningStatsView, meta: { title: '学习统计' } },
        { path: ':pathMatch(.*)*', component: NotFoundView, meta: { title: '页面不存在', public: true } },
      ],
    },
    { path: '/admin', component: AdminLayout, meta: { admin: true }, children: [
      { path: '', component: AdminDashboardView, meta: { title: '数据管理' } },
      { path: 'articles', component: AdminArticlesView, meta: { title: '文章管理' } },
      { path: 'articles/new', component: AdminArticleEditorView, meta: { title: '创建文章' } },
      { path: 'articles/:id/edit', component: AdminArticleEditorView, meta: { title: '编辑草稿' } },
      { path: 'articles/:id/preview', component: AdminArticlePreviewView, meta: { title: '文章预览' } },
      { path: 'articles/:id/writing', redirect: to => `/admin/articles/${to.params.id}/edit` },
      { path: 'articles/:id/quiz', component: AdminArticleQuizView, meta: { title: '阅读题组管理' } },
      { path: 'practice', component: () => import('@/views/AdminPracticeSetsView.vue'), meta: { title: '专项题组' } },
      { path: 'practice/import', component: () => import('@/views/AdminPracticeImportView.vue'), meta: { title: '专项题组导入' } },
      { path: 'practice/subjective/:id', component: () => import('@/views/AdminPracticeSubjectiveView.vue'), meta: { title: '写作翻译题目编辑' } },
      { path: 'practice/papers', component: () => import('@/views/AdminPracticePapersView.vue'), meta: { title: '整卷管理' } },
      { path: 'practice/papers/:id', component: () => import('@/views/AdminPracticePaperView.vue'), meta: { title: '整卷编辑' } },
      { path: 'practice/:id', component: () => import('@/views/AdminPracticeEditorView.vue'), meta: { title: '专项题组编辑' } },
      { path: 'account', component: AccountView, meta: { title: '管理员账户设置' } },
      { path: 'users', component: () => import('@/views/AdminUsersView.vue'), meta: { title: '用户管理' } },
      { path: 'wall', component: () => import('@/views/AdminWallView.vue'), meta: { title: '留言管理' } },
    ] },
  ],
  scrollBehavior: (to, from, savedPosition) => {
    if (savedPosition) return savedPosition
    if (to.path === from.path && (to.path === '/admin/practice' || /^\/practice(?:\/(?:cet4|cet|postgraduate))?$/.test(to.path))) return false
    return { top: 0 }
  },
})
router.beforeEach(async (to, from) => {
  const auth = useAuthStore()
  try { await auth.restore(!to.meta.public) }
  catch { if (!to.meta.public) return { path: '/login', query: { redirect: to.fullPath } } }
  const entry = ({ '/login': 'login', '/register': 'register', '/forgot-password': 'reset' } as const)[to.path as '/login' | '/register' | '/forgot-password']
  if (entry) {
    if (auth.user && !auth.connectionError && entry !== 'reset') return auth.isAdmin ? '/admin' : '/'
    const dialog = useAuthDialogStore()
    dialog.switchMode(entry, to.query.changed ? '密码已更新，请重新登录' : to.query.expired ? '登录已失效，请重新登录' : '')
    dialog.request(typeof to.query.redirect === 'string' ? to.query.redirect : '')
    return { path: auth.isAdmin && !auth.connectionError ? '/admin' : '/', replace: true }
  }
  if (!auth.user && !to.meta.public) {
    useAuthDialogStore().request(to.fullPath)
    return from.matched.length && from.meta.public && !['/login', '/register', '/forgot-password'].includes(from.path) ? false : '/'
  }
  if (auth.isAdmin && !auth.connectionError && ['/', '/vocabulary', '/reading', '/notebook', '/practice', '/stats', '/writing'].includes(to.path)) return '/admin'
  if (auth.isAdmin && to.path.startsWith('/reading/')) return '/admin'
  if (auth.isAdmin && to.path.startsWith('/practice')) return '/admin/practice'
  if (auth.isAdmin && to.path === '/account') return '/admin/account'
  if (to.meta.admin) {
    if (!auth.isAdmin) return '/account'
    try { await adminIdentity() }
    catch { auth.connectionError = true; return { path: '/login', query: { redirect: to.path } } }
  }
  return true
})
router.afterEach((to) => { document.title = `${String(to.meta.title ?? 'Lingua Loop')} · Lingua Loop` })
onRemoteIdentityChange(() => queueMicrotask(() => {
  const auth = useAuthStore()
  void auth.restore(true).then(async () => {
    const dialog = useAuthDialogStore()
    if (auth.user && dialog.open) {
      const destination = safeRedirect(dialog.target || router.currentRoute.value.fullPath, auth.isAdmin)
      dialog.complete()
      await router.replace(destination)
      return
    }
    if (!auth.user) {
      if (!router.currentRoute.value.meta.public) { useAuthDialogStore().request(router.currentRoute.value.fullPath); await router.replace('/') }
    }
    else if (auth.isAdmin) await router.replace('/admin')
    else if (router.currentRoute.value.meta.admin || ['/login', '/register'].includes(router.currentRoute.value.path)) await router.replace('/')
  }).catch(() => { void router.replace('/login') })
}))
installNavigationRecovery(router)
export default router
