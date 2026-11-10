<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { listArticles } from '@/api/admin-articles'
import { adminPracticeSets } from '@/api/admin-practice'
import { listUsers } from '@/api/admin-users'
import { adminPapers } from '@/api/practice-paper'

interface ManagementModule {
  path: string
  mark: string
  title: string
  description: string
  unit?: string
  count?: (signal: AbortSignal) => Promise<number>
}

const firstPage = new URLSearchParams({ page: '1', pageSize: '1' })
const modules: ManagementModule[] = [
  {
    path: '/admin/articles', mark: '文', title: '文章管理', unit: '篇',
    description: '编辑文章、校对阅读题目与管理发布',
    count: async signal => (await listArticles(firstPage, signal)).total,
  },
  {
    path: '/admin/practice', mark: '题', title: '专项题组', unit: '组',
    description: '整理真题、原创练习与配套材料',
    count: async signal => (await adminPracticeSets(firstPage, signal)).total,
  },
  {
    path: '/admin/practice/papers', mark: '卷', title: '整卷管理', unit: '套',
    description: '组卷、核对来源与管理发布版本',
    count: async signal => (await adminPapers(signal)).length,
  },
  {
    path: '/admin/wall', mark: '言', title: '留言管理',
    description: '处理举报、巡查讨论与发布公告',
  },
  {
    path: '/admin/users', mark: '人', title: '用户管理', unit: '个',
    description: '查看账号、管理启用状态与查看操作记录',
    count: async signal => (await listUsers(firstPage, signal)).total,
  },
]
const counts = ref<Partial<Record<string, number | 'unavailable'>>>({})
const busy = ref(true)
const formatter = new Intl.NumberFormat('zh-CN')
let controller: AbortController | undefined

async function refresh() {
  controller?.abort()
  controller = new AbortController()
  const { signal } = controller
  busy.value = true
  counts.value = {}
  await Promise.all(modules.map(async module => {
    if (!module.count) return
    try {
      const total = await module.count(signal)
      if (!signal.aborted) counts.value[module.path] = Number.isSafeInteger(total) && total >= 0 ? total : 'unavailable'
    } catch {
      if (!signal.aborted) counts.value[module.path] = 'unavailable'
    }
  }))
  if (!signal.aborted) busy.value = false
}

function countLabel(module: ManagementModule) {
  const value = counts.value[module.path]
  if (typeof value === 'number') return `${formatter.format(value)} ${module.unit}`
  if (value === 'unavailable') return '数量暂不可用'
  return '读取中…'
}

onMounted(refresh)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="admin-overview" aria-labelledby="admin-data-title">
    <header class="admin-overview-heading">
      <div>
        <h1 id="admin-data-title">数据管理</h1>
        <p class="admin-description">查看各模块的数据量，进入对应页面维护。</p>
      </div>
      <button class="button-secondary" :disabled="busy" @click="refresh">刷新数量</button>
    </header>

    <ul class="admin-module-list" aria-label="管理模块" :aria-busy="busy">
      <li v-for="module in modules" :key="module.path">
        <RouterLink
          class="admin-module-link"
          :to="module.path"
          :aria-label="`进入${module.title}`"
          :aria-describedby="module.count ? `module-description-${module.mark} module-count-${module.mark}` : `module-description-${module.mark}`"
        >
          <span class="admin-module-mark" aria-hidden="true">{{ module.mark }}</span>
          <span class="admin-module-copy">
            <strong>{{ module.title }}</strong>
            <span :id="`module-description-${module.mark}`">{{ module.description }}</span>
          </span>
          <span v-if="module.count" :id="`module-count-${module.mark}`" class="admin-module-count" :class="{ 'is-number': typeof counts[module.path] === 'number' }">
            {{ countLabel(module) }}
          </span>
          <span class="admin-module-arrow" aria-hidden="true">›</span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>
