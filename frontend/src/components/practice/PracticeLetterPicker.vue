<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId } from 'vue'

const props = defineProps<{
  title: string
  options: { letter: string; label: string; used?: boolean }[]
  selected: string | null
}>()
const emit = defineEmits<{ choose: [letter: string]; clear: [] }>()
const panel = ref<HTMLElement>()
const titleId = useId()
let anchor: HTMLButtonElement | undefined

function close(restoreFocus = true) {
  if (!anchor) return
  const trigger = anchor
  anchor = undefined
  panel.value?.hidePopover()
  if (restoreFocus) trigger.focus({ preventScroll: true })
}

function position() {
  const element = panel.value
  if (!anchor || !element) return
  const trigger = anchor.getBoundingClientRect()
  // The root box excludes the space reserved by scrollbar-gutter.
  const width = document.documentElement.getBoundingClientRect().width
  const height = window.innerHeight
  if (!anchor.isConnected || trigger.bottom <= 0 || trigger.top >= height) {
    close(false)
    return
  }
  const margin = 8
  const gap = 6
  const { offsetWidth, offsetHeight } = element
  const below = trigger.bottom + gap
  const above = trigger.top - gap - offsetHeight
  const top = below + offsetHeight <= height - margin ? below : above
  element.style.left = `${Math.max(margin, Math.min(trigger.left, width - offsetWidth - margin))}px`
  element.style.top = `${Math.max(margin, Math.min(top, height - offsetHeight - margin))}px`
}

function open(trigger: HTMLButtonElement) {
  const element = panel.value
  if (!element || typeof element.showPopover !== 'function') return
  if (anchor === trigger) {
    close()
    return
  }
  if (!anchor) element.showPopover()
  anchor = trigger
  position()
  const option = element.querySelector<HTMLButtonElement>('[aria-pressed="true"]')
    ?? element.querySelector<HTMLButtonElement>('.letter-picker-option')
  option?.focus({ preventScroll: true })
}

function dismissOutside(event: PointerEvent) {
  if (!anchor || !(event.target instanceof Node)) return
  if (!panel.value?.contains(event.target) && !anchor.contains(event.target)) close(false)
}

function choose(letter: string) {
  close()
  emit('choose', letter)
}

function clear() {
  close()
  emit('clear')
}

onMounted(() => {
  document.addEventListener('pointerdown', dismissOutside, true)
  window.addEventListener('scroll', position, true)
  window.addEventListener('resize', position)
})
onBeforeUnmount(() => {
  close(false)
  document.removeEventListener('pointerdown', dismissOutside, true)
  window.removeEventListener('scroll', position, true)
  window.removeEventListener('resize', position)
})
defineExpose({ open, close })
</script>

<template>
  <section ref="panel" class="practice-letter-picker" popover="manual" role="dialog"
    :aria-labelledby="titleId" @keydown.esc.stop.prevent="close()">
    <h3 :id="titleId">{{ title }}</h3>
    <div class="letter-picker-grid">
      <button v-for="option in options" :key="option.letter" type="button" class="letter-picker-option"
        :class="{ 'is-used': option.used }" :title="option.label" :aria-label="option.label"
        :aria-pressed="selected === option.letter" @click="choose(option.letter)">{{ option.letter }}</button>
    </div>
    <div class="letter-picker-actions">
      <button type="button" :disabled="!props.selected" @click="clear">清空</button>
      <button type="button" @click="close()">关闭</button>
    </div>
  </section>
</template>

<style scoped>
.practice-letter-picker {
  position: fixed;
  inset: auto;
  margin: 0;
  width: min(356px, calc(100% - 16px));
  max-height: calc(100dvh - 16px);
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--line-strong);
  border-radius: 14px;
  background: var(--surface);
  color: var(--ink);
  box-shadow: 0 8px 30px #27322725;
}
.practice-letter-picker h3 { margin: 0 0 9px; font-size: 14px; }
.letter-picker-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 6px; }
.practice-letter-picker button {
  min-height: 40px;
  padding: 4px 8px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
}
.practice-letter-picker .letter-picker-option { font-weight: 650; }
.practice-letter-picker .is-used { color: var(--muted); background: var(--surface-3); }
.practice-letter-picker button:hover:enabled,
.practice-letter-picker button[aria-pressed="true"] {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent-dark);
}
.letter-picker-actions { display: flex; gap: 6px; margin-top: 9px; }
.letter-picker-actions button { min-height: 32px; font-size: 12px; }
</style>
