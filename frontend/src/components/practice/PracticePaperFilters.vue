<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {PaperCatalog} from '@/api/practice-paper'
import type {LocationQuery} from 'vue-router'
import {parseFilters,type Filters} from '@/practice/catalog-query'
import PracticeFilters from './PracticeFilters.vue'
const props=defineProps<{query:LocationQuery;catalog:PaperCatalog|null;datesPending?:boolean}>()
const emit=defineEmits<{apply:[filters:Filters]}>()
const filters=ref(parseFilters(props.query))
watch(()=>props.query,query=>{filters.value={...parseFilters(query),source:'PAST'}},{immediate:true})
const facets=computed(()=>props.catalog?({source:[],readingType:[],topic:[],state:[],paperNo:[],year:props.catalog.years,month:props.catalog.months}):undefined)
</script>
<template><PracticeFilters v-model="filters" catalog-layout source-locked section="paper" :exam-codes="['CET4','CET6']" :facets="facets" :dates-pending="datesPending" @apply="emit('apply',filters)"/></template>
