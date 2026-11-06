<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  id: string
  name?: string
  modelValue: string
  autocomplete: string
  placeholder?: string
  disabled?: boolean
  required?: boolean
  maxlength?: number
  describedBy?: string
  invalid?: boolean
}>(), { disabled: false, required: false, invalid: false })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const visible = ref(false)
watch(() => props.modelValue, value => { if (!value) visible.value = false })
</script>

<template>
  <div class="password-control">
    <input :id="props.id" :name="props.name" :value="props.modelValue" :type="visible ? 'text' : 'password'"
      :autocomplete="props.autocomplete" :placeholder="props.placeholder" :disabled="props.disabled"
      :required="props.required" :maxlength="props.maxlength" :aria-describedby="props.describedBy"
      :aria-invalid="props.invalid ? 'true' : undefined" @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)">
    <button type="button" class="password-toggle" :disabled="props.disabled" :aria-pressed="visible"
      :aria-label="visible ? '隐藏密码' : '显示密码'" :aria-controls="props.id" @click="visible = !visible">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
        <template v-if="visible">
          <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z" />
          <circle cx="12" cy="12" r="3" />
        </template>
        <template v-else>
          <path d="m3 3 18 18M10.6 5.1 12 5c6.4 0 10 7 10 7a18 18 0 0 1-3 3.8M6.1 6.1A19 19 0 0 0 2 12s3.6 7 10 7a11 11 0 0 0 5.9-1.9M9.9 9.9a3 3 0 0 0 4.2 4.2" />
        </template>
      </svg>
    </button>
  </div>
</template>
