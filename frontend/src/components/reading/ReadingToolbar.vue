<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { createReaderSettings, readerFonts, type ReaderSettings } from '@/reading/settings'
const baseFontSize = createReaderSettings().fontSize
const settings = defineModel<ReaderSettings>('settings', { required: true })
defineProps<{ keywordCount: number; keywordSource?:string }>()
const open = ref(false), root = ref<HTMLElement>(), trigger = ref<HTMLButtonElement>()
function patch(value: Partial<ReaderSettings>) { settings.value = { ...settings.value, ...value } }
function outside(e: Event) { if (root.value && !root.value.contains(e.target as Node)) open.value = false }
function key(e: KeyboardEvent) { if (e.key === 'Escape' && open.value) { e.preventDefault(); open.value = false; trigger.value?.focus({ preventScroll: true }) } }
onMounted(() => { document.addEventListener('pointerdown', outside); document.addEventListener('keydown', key) })
onBeforeUnmount(() => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', key) })
</script>
<template><div class="reader-toolbar" role="group" aria-label="阅读辅助工具栏">
  <div class="reader-modes"><button type="button" :aria-pressed="!settings.bilingual" @click="patch({ bilingual: false })">纯英文</button><button type="button" :aria-pressed="settings.bilingual" @click="patch({ bilingual: true })">英中对照</button></div>
  <div class="reader-tool-actions"><button type="button" :aria-pressed="settings.keywords" :title="`高亮 ${keywordCount} 个重点词，来源：${keywordSource||'文章精选词'}`" @click="patch({ keywords: !settings.keywords })">重点词</button>
  <div ref="root" class="reader-settings-wrap"><button ref="trigger" type="button" class="reader-aa" aria-label="阅读设置" :aria-expanded="open" aria-controls="reader-settings" @click="open = !open">Aa</button>
  <section v-if="open" id="reader-settings" class="reader-settings" aria-label="阅读设置面板"><header><h2>阅读设置</h2><button type="button" aria-label="关闭阅读设置" @click="open = false; trigger?.focus({ preventScroll: true })">×</button></header>
  <span>字号</span><div class="reader-stepper"><button type="button" aria-label="缩小字号" :disabled="settings.fontSize <= 16" @click="patch({ fontSize: settings.fontSize - 1 })">A−</button><output class="reader-size-value">{{ Math.round(settings.fontSize / baseFontSize * 100) }}%</output><button type="button" aria-label="放大字号" :disabled="settings.fontSize >= 26" @click="patch({ fontSize: settings.fontSize + 1 })">A＋</button></div>
  <span>行间距</span><div class="reader-stepper"><button type="button" aria-label="缩小行距" :disabled="settings.lineHeight <= 1.4" @click="patch({ lineHeight: Math.round((settings.lineHeight - .05) * 100) / 100 })">−</button><output>{{ settings.lineHeight }}</output><button type="button" aria-label="增大行距" :disabled="settings.lineHeight >= 2.2" @click="patch({ lineHeight: Math.round((settings.lineHeight + .05) * 100) / 100 })">＋</button></div>
  <label>字体<select aria-label="字体" :value="settings.font" @change="patch({ font: ($event.target as HTMLSelectElement).value as ReaderSettings['font'] })"><option v-for="font in readerFonts" :key="font.value" :value="font.value">{{ font.label }}</option></select></label>
  <p class="reader-font-note">使用设备已安装的字体，缺少时自动采用相近字体。</p>
  <span>对齐</span><div class="reader-alignments"><button v-for="item in [{ value: 'left' as const, label: '左对齐', icon: '≡' }, { value: 'center' as const, label: '居中对齐', icon: '≡' }, { value: 'right' as const, label: '右对齐', icon: '≡' }, { value: 'justify' as const, label: '两端对齐', icon: '☰' }]" :key="item.value" type="button" :aria-label="item.label" :aria-pressed="settings.align === item.value" :style="{ textAlign: item.value }" @click="patch({ align: item.value })">{{ item.icon }}</button></div>
  <button type="button" class="button-primary reader-reset" @click="patch({ ...createReaderSettings(), bilingual: settings.bilingual, keywords: settings.keywords })">恢复默认</button>
  </section></div></div>
</div></template>
