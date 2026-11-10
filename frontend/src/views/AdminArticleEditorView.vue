<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import { categories, getArticle, createArticle, saveDraft, publishArticle, unpublishArticle, bodyText, type Category, type DraftInput, type AdminArticle } from '@/api/admin-articles'
import { useFormFeedback, type PageFeedback } from '@/feedback/useFormFeedback'
import { getGeneration, onRemoteIdentityChange } from '@/auth/token-session'
import { errorMessage } from '@/api/identity'
import { notifySuccess } from '@/feedback/notify'
import FieldError from '@/components/FieldError.vue'
import FormFeedback from '@/components/FormFeedback.vue'
const route = useRoute(), router = useRouter()
const record = ref<AdminArticle>(), groups = ref<Category[]>([]), busy = ref(false), loading = ref(true), hasLoaded = ref(false), contextInvalidated = ref(false), pageError = ref<PageFeedback | null>(null)
const defaults = (): DraftInput => ({ categoryId: '1', title: '', titleZh: '', summary: '', difficulty: 'B1', body: '', author: 'Lingua Loop编辑组', sourceType: 'ORIGINAL', sourceUrl: '', usageNote: '原创英语学习练习，供站内阅读与词汇学习使用。' })
const input = reactive<DraftInput>(defaults())
const snapshot = ref(''), dirty = computed(() => hasLoaded.value && !loading.value && snapshot.value !== JSON.stringify(input))
const fields = ['title', 'titleZh', 'categoryId', 'difficulty', 'summary', 'body', 'author', 'sourceType', 'sourceUrl', 'usageNote'] as const
const feedback = useFormFeedback({ fields, onPageError: value => { pageError.value = value } })
const { form, panel, fieldErrors, formError } = feedback
let operation = 0, disposed = false, ownsDialog = false
const routeId = () => String(route.params.id ?? '')
const valid = (ticket: number, identity: number, id: string) => !disposed && !contextInvalidated.value && operation === ticket && getGeneration() === identity && routeId() === id
function closeOwnedDialog() { if (ownsDialog) { ownsDialog = false; ElMessageBox.close() } }
const unsubscribe = onRemoteIdentityChange(() => { contextInvalidated.value = true; operation++; closeOwnedDialog() })
watch(() => route.params.id, () => { void load() })
async function load() {
  if (disposed || contextInvalidated.value) return
  const ticket = ++operation, identity = getGeneration(), id = routeId()
  loading.value = true; hasLoaded.value = false; busy.value = false; pageError.value = null; feedback.reset(); record.value = undefined
  Object.assign(input, defaults())
  try {
    const options = await categories(); if (!valid(ticket, identity, id)) return; groups.value = options
    if (id) {
      const result = await getArticle(id); if (!valid(ticket, identity, id)) return
      record.value = result; const draft = result.draft
      Object.assign(input, { categoryId: draft.category.id, title: draft.title, titleZh: draft.titleZh, summary: draft.summary, difficulty: draft.difficulty, body: bodyText(draft), author: draft.author, sourceType: draft.sourceType, sourceUrl: draft.sourceUrl ?? '', usageNote: draft.usageNote })
    }
    snapshot.value = JSON.stringify(input); hasLoaded.value = true
  } catch (error) {
    if (!valid(ticket, identity, id)) return
    await feedback.fail(error)
    if (!pageError.value) pageError.value = { kind: 'permission', message: errorMessage(error) }
  } finally { if (valid(ticket, identity, id)) loading.value = false }
}
async function reconnect() {
  if (!hasLoaded.value) { await load(); return }
  if (busy.value || contextInvalidated.value) return
  const ticket = operation, identity = getGeneration(), id = routeId(); busy.value = true
  try { await categories(); if (valid(ticket, identity, id)) pageError.value = null }
  catch (error) { if (valid(ticket, identity, id)) await feedback.fail(error) }
  finally { if (valid(ticket, identity, id)) busy.value = false }
}
async function save() {
  if (busy.value || loading.value || !hasLoaded.value || contextInvalidated.value) return
  if (!feedback.validate()) { await feedback.focusError(); return }
  const ticket = operation, identity = getGeneration(), id = routeId(), sent = { ...input }, existing = record.value
  busy.value = true; pageError.value = null
  try {
    const result = existing ? await saveDraft(existing.id, sent, existing.lockVersion) : await createArticle(sent)
    if (!valid(ticket, identity, id)) return
    record.value = result; snapshot.value = JSON.stringify(sent); notifySuccess('草稿已保存')
    await router.replace(`/admin/articles/${result.id}/edit`)
  } catch (error) { if (valid(ticket, identity, id)) await feedback.fail(error) }
  finally { if (valid(ticket, identity, id)) busy.value = false }
}
async function confirmLeave() {
  if (contextInvalidated.value || !dirty.value) return true
  if (ownsDialog) return false
  const ticket = operation, identity = getGeneration(), id = routeId(); ownsDialog = true
  try { await ElMessageBox.confirm('未保存的修改将丢失，仍要离开吗？', '离开编辑', { confirmButtonText: '离开', cancelButtonText: '继续编辑', type: 'warning', autofocus: false }); return valid(ticket, identity, id) }
  catch { return false }
  finally { ownsDialog = false }
}
onBeforeRouteLeave(confirmLeave)
onBeforeRouteUpdate((to, from) => to.params.id === from.params.id ? true : confirmLeave())
onBeforeUnmount(() => { disposed = true; operation++; unsubscribe(); closeOwnedDialog() })
onMounted(load)
async function changePublication(unpublish: boolean) {
  if (!record.value || busy.value || dirty.value || contextInvalidated.value) return
  const ticket = operation, identity = getGeneration(), id = routeId(), target = { id: record.value.id, lockVersion: record.value.lockVersion }
  busy.value = true; ownsDialog = true
  try {
    try { await ElMessageBox.confirm(unpublish ? '下架后不再接受新阅读，已有阅读记录会保留。' : '发布后，学习者可以阅读这份草稿版本。请确认正文和来源已校对。', unpublish ? '下架文章' : '发布文章', { confirmButtonText: unpublish ? '确认下架' : '确认发布', cancelButtonText: '取消', autofocus: false }) } catch { return }
    ownsDialog = false
    if (!valid(ticket, identity, id) || record.value?.id !== target.id || record.value.lockVersion !== target.lockVersion) return
    const result = await (unpublish ? unpublishArticle : publishArticle)(target.id, target.lockVersion)
    if (!valid(ticket, identity, id)) return
    record.value = result; notifySuccess(unpublish ? '文章已下架' : '文章已发布')
  } catch (error) { if (valid(ticket, identity, id)) await feedback.fail(error) }
  finally { ownsDialog = false; if (valid(ticket, identity, id)) busy.value = false }
}</script>
<template>
  <section class="reading-page">
    <header class="reading-page-header"><div><RouterLink class="reading-back" to="/admin/articles">← 文章管理</RouterLink><h1>{{ record ? '编辑草稿' : '创建文章' }}</h1><p>保存生成独立版本，不会自动发布。</p><RouterLink v-if="record" class="text-button" :to="{path: `/admin/articles/${record.id}/quiz`,query:{version:record.publishedVersionId||record.draftVersionId}}">管理阅读题组</RouterLink></div><RouterLink v-if="record && !dirty" class="button-secondary" :to="`/admin/articles/${record.id}/preview?version=${record.draftVersionId}`">预览草稿</RouterLink></header>
    <p v-if="loading" role="status">正在加载编辑器…</p>
    <div v-if="!loading && pageError" class="reading-notice" role="alert"><p>{{ pageError.message }}</p><button class="button-secondary" @click="reconnect">重新连接</button></div>
    <form v-if="!loading && hasLoaded" ref="form" class="article-editor" :aria-busy="busy" @invalid.capture="feedback.invalid" @submit.prevent="save">
      <fieldset :disabled="busy || contextInvalidated"><div class="article-editor-grid">
        <div v-for="field in (['title', 'titleZh'] as const)" :key="field" class="reading-field"><label :for="`article-${field}`">{{ field === 'title' ? '英文标题' : '中文标题' }}</label><input :id="`article-${field}`" v-model="input[field]" :name="field" maxlength="160" required :aria-invalid="fieldErrors[field] ? 'true' : undefined" :aria-describedby="feedback.describedBy(field)" @input="feedback.changed(field)"><FieldError :id="`article-${field}-error`" :message="fieldErrors[field]" /></div>
        <div class="reading-field"><label for="article-categoryId">分类</label><select id="article-categoryId" v-model="input.categoryId" name="categoryId" required :aria-invalid="fieldErrors.categoryId ? 'true' : undefined" :aria-describedby="feedback.describedBy('categoryId')" @change="feedback.changed('categoryId')"><option v-for="group in groups" :key="group.id" :value="group.id">{{ group.name }}</option></select><FieldError id="article-categoryId-error" :message="fieldErrors.categoryId" /></div>
        <div class="reading-field"><label for="article-difficulty">编辑难度</label><select id="article-difficulty" v-model="input.difficulty" name="difficulty" required><option>B1</option><option>B2</option></select><FieldError id="article-difficulty-error" :message="fieldErrors.difficulty" /></div>
      </div>
      <div class="reading-field"><label for="article-summary">简介</label><textarea id="article-summary" v-model="input.summary" name="summary" rows="3" maxlength="500" required :aria-invalid="fieldErrors.summary ? 'true' : undefined" :aria-describedby="feedback.describedBy('summary')" @input="feedback.changed('summary')" /><FieldError id="article-summary-error" :message="fieldErrors.summary" /></div>
      <div class="reading-field"><label for="article-body">正文</label><textarea id="article-body" v-model="input.body" class="article-body-input" name="body" rows="15" maxlength="20000" required :aria-invalid="fieldErrors.body ? 'true' : undefined" :aria-describedby="feedback.describedBy('body', 'article-body-hint')" @input="feedback.changed('body')" /><p id="article-body-hint" class="field-hint">空行分段，最多20,000字符；保存后请在预览中校对分句。HTML会作为纯文本显示。</p><FieldError id="article-body-error" :message="fieldErrors.body" /></div>
      <div class="article-editor-grid"><div class="reading-field"><label for="article-author">作者或编辑署名</label><input id="article-author" v-model="input.author" name="author" maxlength="160" required :aria-invalid="fieldErrors.author ? 'true' : undefined" :aria-describedby="feedback.describedBy('author')" @input="feedback.changed('author')"><FieldError id="article-author-error" :message="fieldErrors.author" /></div>
      <div class="reading-field"><label for="article-sourceType">来源类型</label><select id="article-sourceType" v-model="input.sourceType" name="sourceType"><option value="ORIGINAL">Lingua Loop原创练习</option><option value="THIRD_PARTY">第三方内容</option></select><FieldError id="article-sourceType-error" :message="fieldErrors.sourceType" /></div></div>
      <div class="reading-field"><label for="article-sourceUrl">来源链接{{ input.sourceType === 'ORIGINAL' ? '（选填）' : '' }}</label><input id="article-sourceUrl" v-model="input.sourceUrl" name="sourceUrl" type="url" maxlength="2048" :required="input.sourceType === 'THIRD_PARTY'" :aria-invalid="fieldErrors.sourceUrl ? 'true' : undefined" :aria-describedby="feedback.describedBy('sourceUrl')" @input="feedback.changed('sourceUrl')"><FieldError id="article-sourceUrl-error" :message="fieldErrors.sourceUrl" /></div>
      <div class="reading-field"><label for="article-usageNote">使用说明</label><textarea id="article-usageNote" v-model="input.usageNote" name="usageNote" rows="2" maxlength="1000" required :aria-invalid="fieldErrors.usageNote ? 'true' : undefined" :aria-describedby="feedback.describedBy('usageNote')" @input="feedback.changed('usageNote')" /><FieldError id="article-usageNote-error" :message="fieldErrors.usageNote" /></div></fieldset>
      <FormFeedback ref="panel" :message="formError" /><div class="reading-actions"><button class="button-primary" :disabled="busy">{{ busy ? '正在保存…' : '保存草稿' }}</button><button v-if="record && record.status !== 'PUBLISHED'" type="button" class="button-secondary" :disabled="busy || dirty" @click="changePublication(false)">发布草稿</button><button v-if="record?.status === 'PUBLISHED' && record.draftVersionId !== record.publishedVersionId" type="button" class="button-secondary" :disabled="busy || dirty" @click="changePublication(false)">发布新草稿</button><button v-if="record?.status === 'PUBLISHED'" type="button" class="button-secondary" :disabled="busy || dirty" @click="changePublication(true)">下架文章</button></div><span v-if="record" class="reading-meta">草稿版本 {{ record.draft.versionNo }} · {{ dirty ? '有未保存修改' : '已保存' }} · {{ record.status === 'PUBLISHED' ? '已发布' : record.status === 'UNLISTED' ? '已下架' : '未发布' }}</span>
    </form>
  </section>
</template>
