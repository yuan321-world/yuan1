<script setup lang="ts">
const features = [
  { label: '智能背词', path: 'M4 15 8.5 4h3L16 15M6 11h8' },
  { label: '外刊阅读', path: 'M10 5C7 3 4 3 2.5 4v12c2-1 4.5-1 7.5 1 3-2 5.5-2 7.5-1V4C16 3 13 3 10 5Zm0 0v12' },
  { label: '真题练习', path: 'M12 4H4v13h12V9M9 10l2 2 6-7M7 14h5' },
]
</script>

<template>
  <section class="auth-brand-panel" aria-labelledby="auth-brand-title">
    <h2 id="auth-brand-title" class="auth-brand-title" lang="en">Build your English,<span> step by step.</span></h2>
    <p class="auth-brand-tagline">让每一次积累，都成为下一次进步的起点。</p>
    <p class="auth-brand-description">从单词积累到外刊阅读，从真题练习到智能复习，<span class="auth-brand-name">在 Lingua Loop</span> 开启属于你的英语学习旅程。</p>
    <ul class="auth-features" aria-label="学习方向">
      <li v-for="feature in features" :key="feature.label" class="auth-feature">
        <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.35" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="feature.path" /></svg>
        {{ feature.label }}
      </li>
    </ul>
    <figure class="auth-word-card" aria-label="英语单词展示">
      <div class="auth-word-heading" lang="en"><span class="auth-word">Progress</span><span class="auth-word-ipa">/ˈprɑːɡres/</span></div>
      <p class="auth-word-meaning">n. 进步；进展</p>
      <blockquote lang="en">Every small step matters.</blockquote>
    </figure>
  </section>
</template>
