import { createRouter, createWebHistory } from 'vue-router'
import LearnerLayout from '@/layouts/LearnerLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import PendingFeatureView from '@/views/PendingFeatureView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import AccountView from '@/views/AccountView.vue'
import { useAuthStore } from '@/stores/auth'
import { adminIdentity } from '@/api/identity'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/', component: LearnerLayout, children: [
        { path: '', component: HomeView, meta: { title: '首页' } },
        { path: 'login', component: LoginView, meta: { title: '登录', public: true } },
        { path: 'register', component: LoginView, meta: { title: '注册', public: true } },
        { path: 'account', component: AccountView, meta: { title: '账户设置' } },
        { path: 'vocabulary', component: PendingFeatureView, props: { title: '背单词', stage: 3, description: '词书、生词学习与间隔复习将在阶段 3 开放。阅读中的收藏词会成为学习与复习的起点。', glyph: 'Aa' }, meta: { title: '背单词' } },
        { path: 'reading', component: PendingFeatureView, props: { title: '外刊精读', stage: 2, description: '文章阅读、查词与生词收藏将在阶段 2 开放。当前尚未接入文章和词典内容。', glyph: 'Read' }, meta: { title: '外刊精读' } },
        { path: 'practice', component: PendingFeatureView, props: { title: '专项练习', stage: 4, description: '四级阅读题组、答题与解析将在阶段 4 开放。当前尚未接入题库与成绩记录。', glyph: 'Q' }, meta: { title: '专项练习' } },
        { path: 'stats', component: PendingFeatureView, props: { title: '学习统计', stage: 3, description: '学习统计将在阶段 3 开放，届时由真实学习记录生成。当前没有可展示的学习数据。', glyph: '∑' }, meta: { title: '学习统计' } },
        { path: ':pathMatch(.*)*', component: NotFoundView, meta: { title: '页面不存在', public: true } },
      ],
    },
    { path: '/admin', component: AdminLayout, meta: { title: '管理端', admin: true } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
router.beforeEach(async to => {
  const auth = useAuthStore()
  try { await auth.restore(!to.meta.public) }
  catch { return to.path === '/login' ? true : { path: '/login', query: { redirect: to.path } } }
  if (!auth.user && !to.meta.public) return { path: '/login', query: { redirect: to.path } }
  if (auth.user && !auth.connectionError && (to.path === '/login' || to.path === '/register')) return '/'
  if (to.meta.admin) {
    if (!auth.isAdmin) return '/account'
    try { await adminIdentity() } catch { return '/account' }
  }
  return true
})
router.afterEach((to) => { document.title = `${String(to.meta.title ?? 'Lingua Loop')} · Lingua Loop` })
export default router
