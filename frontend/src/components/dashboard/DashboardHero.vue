<script setup lang="ts">
import { computed } from 'vue'
import CampusHero from '../CampusHero.vue'
import type { Overview } from '../../types/phase5'

const props = defineProps<{ overview: Overview | null; name: string; updatedAt: Date | null; loading: boolean; error: boolean }>()
const date = computed(() => new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', month: 'long', day: 'numeric', weekday: 'long',
}).format(props.updatedAt ?? new Date()))
const status = computed(() => props.error ? '数据暂不可用，请重新加载'
  : !props.overview ? '正在了解校园维修进展'
  : props.overview.overdueCount > 0 ? `${props.overview.overdueCount} 项超时任务需要关注`
  : props.overview.waitingAuditCount > 0 ? `${props.overview.waitingAuditCount} 项报修等待审核`
  : '当前没有超时工单')
</script>

<template>
  <header class="operations-hero">
    <CampusHero label="校园维修运营中心欢迎区域" asset="service">
      <p class="hero-kicker">智慧校园 · 维修服务</p>
      <h1>校园维修运营中心</h1>
      <p class="hero-welcome">{{ name }}，欢迎回来。优先审核新报修，安排维修人员，跟进异常任务。</p>
      <div class="hero-context">
        <span class="hero-status"><i aria-hidden="true" />{{ status }}</span>
        <time>{{ date }}</time>
      </div>
    </CampusHero>
    <div class="hero-toolbar">
      <div class="update-caption" role="status">
        <span v-if="loading">正在更新数据…</span>
        <span v-else-if="updatedAt">更新于 {{ updatedAt.toLocaleTimeString('zh-CN', { timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit' }) }} · 手动刷新快照</span>
        <span v-else>统计时间以北京时间为准</span>
      </div>
      <div class="hero-actions"><slot /></div>
    </div>
  </header>
</template>

<style scoped>
.operations-hero { min-width: 0; }
.hero-kicker { color: var(--hero-muted); font-size: var(--text-caption); font-weight: 500; letter-spacing: .08em; margin-bottom: var(--space-3); }
h1 { font-size: var(--text-page); font-weight: 600; line-height: 1.25; letter-spacing: -.035em; text-wrap: balance; }
.hero-welcome { color: var(--hero-muted); font-size: var(--text-body); line-height: 1.8; margin-top: var(--space-3); }
.hero-context { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3) var(--space-5); color: var(--hero-muted); font-size: var(--text-caption); margin-top: var(--space-4); }
.hero-status { display: flex; align-items: center; gap: var(--space-2); }
.hero-status i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
.hero-toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: var(--space-4); margin-top: var(--space-4); }
.update-caption { color: var(--muted); font-size: var(--text-caption); line-height: 1.6; }
.hero-actions { display: flex; align-items: flex-start; justify-content: flex-end; flex-wrap: wrap; gap: var(--space-2); }
@media (max-width: 740px) { .hero-toolbar { align-items: flex-start; flex-direction: column; } .hero-actions { justify-content: flex-start; width: 100%; } .hero-context { gap: var(--space-3); } }
</style>
