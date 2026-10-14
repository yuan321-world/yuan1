<template>
<section class="login-page" aria-labelledby="login-title"><div class="login-card">
  <div class="login-logo" aria-hidden="true">L</div><p class="login-brand">Lingua Loop</p>
  <h1 id="login-title">登录与账号</h1><p class="login-subtitle">让每一次积累，都成为下一次进步的起点。</p>
  <form @submit.prevent>
    <label for="login-username">用户名</label><input id="login-username" autocomplete="username" placeholder="你的用户名" disabled>
    <label for="login-password">密码</label><input id="login-password" type="password" autocomplete="current-password" placeholder="你的密码" disabled>
    <button class="button-primary" type="submit" disabled>登录</button>
  </form><p class="login-notice">真实账号登录将在阶段 1 开放。</p>
</div></section>
</template>
