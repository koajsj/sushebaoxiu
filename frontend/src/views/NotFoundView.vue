<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <main class="not-found"><p class="not-found-code">404</p><h1>页面不存在</h1>
    <p>请检查地址，或返回校园服务入口。</p><RouterLink to="/login" class="text-link">返回登录页面 →</RouterLink>
  </main>
</template>
