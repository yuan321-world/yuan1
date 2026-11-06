<script setup lang="ts">
import {ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {useAuthStore} from '@/stores/auth'
import {useAuthDialogStore} from '@/stores/auth-dialog'
import {useWritingIntent,type WritingIntent} from '@/stores/writing-intent'
import WritingMaterialDetail from './WritingMaterialDetail.vue'
const auth=useAuthStore(),login=useAuthDialogStore(),store=useWritingIntent(),route=useRoute(),resume=ref<WritingIntent>()
watch(()=>route.fullPath,value=>{if(store.intent&&store.intent.route!==value)store.clear();if(resume.value&&resume.value.route!==value)resume.value=undefined})
watch(()=>[auth.user?.id,login.open,store.intent?.userId],()=>{if(auth.isAdmin){store.clear();resume.value=undefined;return}if(auth.user&&!login.open&&store.intent){resume.value=store.take(route.fullPath)??undefined}},{flush:'post'})
</script>
<template><WritingMaterialDetail v-if="resume" :material-id="resume.materialId" :resume="resume" @close="resume=undefined" @changed="store.changed()" /></template>
