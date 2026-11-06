<script setup lang="ts">
import { ref } from 'vue'
defineProps<{ message?: string }>()
const element = ref<HTMLElement>()
defineExpose({ focus: (options?: FocusOptions) => element.value?.focus(options), scrollIntoView: (options?: ScrollIntoViewOptions) => element.value?.scrollIntoView(options) })
</script>
<template><p v-if="message" ref="element" class="auth-message auth-error form-feedback" role="alert" tabindex="-1">{{ message }}</p></template>
