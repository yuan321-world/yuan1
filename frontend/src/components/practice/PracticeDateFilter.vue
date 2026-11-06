<script setup lang="ts">
const props=defineProps<{
  field:'month'|'year'
  label:string
  value:string
  values:string[]
  facets?:{value:string;count:number}[]
  unit:string
  disabled?:boolean
}>()
const emit=defineEmits<{choose:[value:string]}>()
function count(value:string){
  if(!props.facets)return undefined
  return value?props.facets.find(item=>item.value===value)?.count||0:props.facets.reduce((total,item)=>total+item.count,0)
}
function description(value:string){const total=count(value);return total===undefined?'正在加载数量':`${total}${props.unit}可用`}
</script>

<template>
  <details class="practice-filter-group practice-date-filter">
    <summary>{{label}} <span>{{value?value+(field==='month'?'月':'年'):'全部'}}</span></summary>
    <div class="practice-filter-chips">
      <button :disabled="disabled" :aria-label="label+'：全部'" :aria-description="description('')" :aria-pressed="!value" @click="!disabled&&emit('choose','')">
        <span>全部</span><span class="practice-filter-count" aria-hidden="true">{{count('')??'—'}}</span>
      </button>
      <button v-for="option in values" :key="option" :disabled="disabled" :aria-label="label+'：'+option+(field==='month'?'月':'年')" :aria-description="description(option)" :aria-pressed="value===option" @click="!disabled&&emit('choose',option)">
        <span>{{option}}{{field==='month'?'月':''}}</span><span class="practice-filter-count" aria-hidden="true">{{count(option)??'—'}}</span>
      </button>
    </div>
  </details>
</template>
