<script setup lang="ts">
defineProps<{ name: 'vocabulary' | 'reading' | 'practice' | 'mistakes' | 'notes' | 'wall' }>()
const paths = {
  vocabulary: 'M5 19 11 5h2l6 14M7.5 14h9M4 5h3M5.5 3.5v3',
  reading: 'M12 6C8.5 4 5.5 4 3 5v14c2.5-1 5.5-1 9 1 3.5-2 6.5-2 9-1V5c-2.5-1-5.5-1-9 1Zm0 0v14',
  practice: 'M14 4H5v17h14V11M10 11l3 3 7-8M9 18h6',
  mistakes: 'M9 9l6 6m0-6-6 6M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  notes: 'M7 3h12v18H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm1 0v18m3-12h5m-5 4h5',
  wall: 'M5 5h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-7l-6 3v-3H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Zm2 5h10m-10 4h6',
}
</script>
<template><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path :d="paths[name]" /></svg></template>
