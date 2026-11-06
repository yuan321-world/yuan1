<script setup lang="ts">
import {computed} from 'vue'
import {wallTextParts} from '@/wall/format'
const props=defineProps<{text:string;announcement?:boolean}>(),parts=computed(()=>wallTextParts(props.text,!!props.announcement))
</script>
<template><template v-for="(part,index) in parts" :key="index"><strong v-if="part.bold">{{part.text}}</strong><template v-else>{{part.text}}</template></template></template>
