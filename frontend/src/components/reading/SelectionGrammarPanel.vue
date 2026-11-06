<script setup lang="ts">
import type {GrammarAnalysis} from '@/api/selection-translation'
defineProps<{analysis:GrammarAnalysis}>()
</script>
<template><div class="reader-grammar-analysis">
 <p class="reader-grammar-scope">{{analysis.scopeNote}}</p>
 <section aria-labelledby="grammar-main"><h3 id="grammar-main">句子主干<span aria-hidden="true">：</span></h3><p class="reader-grammar-main"><span v-for="(part,index) in analysis.mainClause" :key="index"><strong lang="en">{{part.quote}}</strong>（{{part.role}}；{{part.explanation}}）<span v-if="index<analysis.mainClause.length-1"> </span></span></p></section>
 <section aria-labelledby="grammar-clauses"><h3 id="grammar-clauses">从句划分<span aria-hidden="true">：</span></h3><ul><li>{{analysis.clauseNote}}</li><li v-for="(part,index) in analysis.clauses" :key="index"><strong lang="en">{{part.quote}}</strong>（{{part.role}}）：{{part.explanation}}</li></ul></section>
 <section aria-labelledby="grammar-points"><h3 id="grammar-points">语法要点<span aria-hidden="true">：</span></h3><ul><li v-for="(part,index) in analysis.grammarPoints" :key="index"><strong lang="en">{{part.quote}}</strong>：{{part.explanation}}</li></ul></section>
</div></template>
