<script setup lang="ts">
defineProps<{ id: string; message?: string }>()
</script>
<template>
  <p v-if="message" :id="id" class="auth-field-error" role="alert">{{ message }}</p>
</template>
