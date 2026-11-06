<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef } from 'vue'
import { validateImport, confirmImport, type ImportValidation, type ImportResult } from '@/api/admin-articles'
import { errorMessage } from '@/api/identity'
import { notifySuccess } from '@/feedback/notify'
const emit = defineEmits<{ imported: [] }>()
const payload = shallowRef<unknown>(), validation = ref<ImportValidation>(), result = ref<ImportResult>(), busy = ref(false), error = ref(''), fileName = ref('')
let clientId = '', generation = 0, controller: AbortController | undefined, disposed = false
async function choose(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]; const current = ++generation
  controller?.abort(); validation.value = undefined; result.value = undefined; payload.value = undefined; error.value = ''; clientId = ''; fileName.value = file?.name ?? ''
  if (!file) return
  if (file.size > 1048576) { error.value = '文件须不超过1MiB，请拆分后导入'; return }
  busy.value = true
  try { const text = await file.text(); if (disposed || current !== generation) return; payload.value = JSON.parse(text.replace(/^\uFEFF/, '')); clientId = crypto.randomUUID() }
  catch { if (!disposed && current === generation) error.value = '无法解析JSON文件，请检查格式后重新选择' }
  finally { if (!disposed && current === generation) busy.value = false }
}
async function validate() {
  if (busy.value || !clientId) return
  const current = generation; controller = new AbortController(); busy.value = true; error.value = ''; validation.value = undefined
  try { const response = await validateImport(payload.value, controller.signal); if (!disposed && current === generation) validation.value = response }
  catch (e) { if (!disposed && current === generation) error.value = errorMessage(e) }
  finally { if (!disposed && current === generation) busy.value = false }
}
async function confirm() {
  if (busy.value || !validation.value?.valid || result.value) return
  const current = generation; controller = new AbortController(); busy.value = true; error.value = ''
  try { const response = await confirmImport(clientId, payload.value, controller.signal); if (disposed || current !== generation) return; result.value = response; notifySuccess(`已导入${response.articleIds.length}篇草稿`); emit('imported') }
  catch (e) { if (!disposed && current === generation) error.value = errorMessage(e) }
  finally { if (!disposed && current === generation) busy.value = false }
}
onBeforeUnmount(() => { disposed = true; generation++; controller?.abort() })
</script>
<template><details class="article-import"><summary>批量导入草稿</summary><div class="article-import-content"><p class="reading-meta">选择schemaVersion=1的JSON文件，每批1—20篇，最多1MiB。校验后确认导入，文章仍为草稿。</p><label for="article-import-file">文章JSON文件</label><input id="article-import-file" type="file" accept=".json,application/json" :disabled="busy" :aria-invalid="!!error" aria-describedby="article-import-error" @change="choose"><p v-if="error" id="article-import-error" class="field-error" role="alert">{{ error }}</p><p v-if="busy" role="status">正在处理文件…</p><div class="reading-actions"><button class="button-secondary" :disabled="busy || !payload || !!result" @click="validate">校验文件</button><button v-if="validation?.valid && !result" class="button-primary" :disabled="busy" @click="confirm">确认导入 {{ validation.itemCount }} 篇草稿</button></div><div v-if="validation" aria-live="polite"><p v-if="validation.valid">{{ fileName }}：{{ validation.itemCount }}篇文章校验通过，请确认导入。</p><ul v-else class="import-errors"><li v-for="(message,field) in validation.fieldErrors" :key="field"><code>{{ field }}</code>：{{ message }}</li></ul></div><div v-if="result" role="status"><p>已创建草稿，请逐篇预览、校对后发布。</p><ul><li v-for="id in result.articleIds" :key="id"><RouterLink :to="`/admin/articles/${id}/edit`">编辑草稿 #{{ id }}</RouterLink></li></ul></div></div></details></template>
