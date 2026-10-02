<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getOverview, getTrend, getTypeStats, getWorkerStats } from '../api/phase5'
import { getMapBuildings, getMapOrders, getMapWorkers } from '../api/dispatch'
import { getOrders } from '../api/repair'
import { useAuthStore } from '../store/auth'
import type { Overview, TrendPoint, TypeCount, WorkerCount } from '../types/phase5'
import type { CampusBuilding, MapOrder, MapWorker } from '../types/dispatch'
import type { RepairOrder } from '../types/repair'
import { formatTime } from '../utils/format'
import { createMapMarkers } from '../utils/map'
import DashboardCard from '../components/DashboardCard.vue'
import ChartPanel from '../components/ChartPanel.vue'
import ExportReportButton from '../components/ExportReportButton.vue'
import OrderStatusTag from '../components/OrderStatusTag.vue'
import DashboardHero from '../components/dashboard/DashboardHero.vue'
import TrendChart from '../components/dashboard/TrendChart.vue'
import FaultChart from '../components/dashboard/FaultChart.vue'
import ActiveRepairCard from '../components/dashboard/ActiveRepairCard.vue'
import WorkerRanking from '../components/dashboard/WorkerRanking.vue'
import DashboardMap from '../components/dashboard/DashboardMap.vue'

const auth = useAuthStore()
const overview = ref<Overview | null>(null), trend = ref<TrendPoint[]>([]), types = ref<TypeCount[]>([]), workers = ref<WorkerCount[]>([])
const buildings = ref<CampusBuilding[]>([]), orders = ref<MapOrder[]>([]), people = ref<MapWorker[]>([])
const activeRepairs = ref<RepairOrder[]>([]), latestOrders = ref<RepairOrder[]>([]), activeTotal = ref(0)
const loading = ref(true), listsLoading = ref(true), error = ref(''), activeError = ref(''), latestError = ref(''), updatedAt = ref<Date | null>(null)
const markers = computed(() => createMapMarkers(buildings.value, orders.value, people.value))
let revision = 0
async function loadOrderLists(current: number) {
  const [active, latest] = await Promise.allSettled([
    getOrders('admin', { page: 1, size: 4, phase: 'PROCESSING' }),
    getOrders('admin', { page: 1, size: 5 }),
  ])
  if (current !== revision) return
  if (active.status === 'fulfilled') { activeRepairs.value = active.value.records; activeTotal.value = active.value.total; activeError.value = '' }
  else { activeRepairs.value = []; activeTotal.value = 0; activeError.value = active.reason instanceof Error ? active.reason.message : '维修任务加载失败' }
  if (latest.status === 'fulfilled') { latestOrders.value = latest.value.records; latestError.value = '' }
  else { latestOrders.value = []; latestError.value = latest.reason instanceof Error ? latest.reason.message : '最新工单加载失败' }
  listsLoading.value = false
}
async function load() {
  if (loading.value && revision > 0) return
  const current = ++revision
  loading.value = true; listsLoading.value = true; error.value = ''
  const lists = loadOrderLists(current)
  try {
    const [summary, points, kinds, staff, locations, tasks, positions] = await Promise.all([
      getOverview(), getTrend(), getTypeStats(), getWorkerStats(), getMapBuildings(), getMapOrders(), getMapWorkers(),
    ])
    if (current !== revision) return
    overview.value = summary; trend.value = points; types.value = kinds; workers.value = staff
    buildings.value = locations; orders.value = tasks.records; people.value = positions
    updatedAt.value = new Date()
  } catch (e) {
    if (current === revision) {
      overview.value = null; trend.value = []; types.value = []; workers.value = []; buildings.value = []; orders.value = []; people.value = []
      updatedAt.value = null; error.value = e instanceof Error ? e.message : '数据加载失败'
    }
  } finally {
    await lists
    if (current === revision) loading.value = false
  }
}
onMounted(load)
onBeforeUnmount(() => { revision++ })
</script>

<template>
  <section class="business-page dashboard-page operations-dashboard" :aria-busy="loading">
    <DashboardHero :overview="overview" :name="auth.user?.realName || '管理员'" :updated-at="updatedAt" :loading="loading" :error="!!error">
      <RouterLink class="primary-button" to="/admin/dispatch">分配维修人员 →</RouterLink>
      <ExportReportButton />
      <button class="secondary-button" :disabled="loading" @click="load">{{ loading ? '更新中…' : '刷新数据' }}</button>
    </DashboardHero>
    <div v-if="error" class="dashboard-load-error" role="alert"><div><strong>暂时无法汇总数据</strong><p>{{ error }}</p></div><button class="secondary-button" :disabled="loading" @click="load">重新汇总数据</button></div>
    <div v-if="loading && !overview" class="dashboard-skeleton" role="status"><span class="sr-only">正在汇总真实工单数据…</span><i v-for="item in 4" :key="item" aria-hidden="true" /></div>

    <template v-if="overview">
      <div class="dashboard-cards operations-metrics">
        <DashboardCard label="今日报修" :value="overview.todayCount" hint="北京时间今日新提交工单" :index="0" />
        <DashboardCard label="在途工单" :value="overview.activeCount" hint="已分配、维修中、待验收与待返工" :index="1" />
        <DashboardCard class="metric-overdue" :class="{ 'has-overdue': overview.overdueCount > 0 }" label="超时任务" :value="overview.overdueCount" hint="接单、待开工或维修超过时限" :index="2" />
        <DashboardCard label="完成率" :value="overview.completionRate" suffix="%" hint="学生已确认完成 / 全部工单" :index="3" />
      </div>

      <nav class="operations-attention" aria-label="需要跟进的工单">
        <span>优先跟进</span>
        <RouterLink to="/admin/dispatch">待派单工单<span aria-hidden="true">↗</span></RouterLink>
        <RouterLink to="/admin/orders?phase=WAIT_AUDIT">待审核报修 <strong>{{ overview.waitingAuditCount }}</strong><span aria-hidden="true">↗</span></RouterLink>
        <RouterLink to="/admin/orders?overdue=true" :class="{ 'attention-danger': overview.overdueCount > 0 }">超时任务 <strong>{{ overview.overdueCount }}</strong><span aria-hidden="true">↗</span></RouterLink>
        <RouterLink to="/admin/orders?phase=REWORK_PENDING">待安排返工 <strong>{{ overview.reworkPendingCount }}</strong><span aria-hidden="true">↗</span></RouterLink>
        <p>平均维修时间 <strong>{{ overview.averageRepairHours === null ? '暂无数据' : `${overview.averageRepairHours} 小时` }}</strong><small>每轮开工至提交结果</small></p>
      </nav>

      <div class="operations-analysis">
        <ChartPanel title="维修趋势" description="最近14天 · 报修提交与维修结果提交"><TrendChart :points="trend" /></ChartPanel>
        <ChartPanel title="故障类型分析" description="全量工单 · 按故障类型占比"><FaultChart :types="types" /></ChartPanel>
      </div>

      <section class="operations-active" aria-labelledby="active-repairs-title">
        <header class="operations-section-heading"><div><p class="eyebrow">正在发生</p><h2 id="active-repairs-title">当前维修任务 <span v-if="!listsLoading && !activeError">{{ activeTotal }}</span></h2><p>已开始维修的工单 · 刷新时快照，按提交时间倒序展示最多4项</p></div><RouterLink class="text-button" to="/admin/orders?phase=PROCESSING">全部维修任务 ↗</RouterLink></header>
        <p v-if="listsLoading" class="dashboard-section-message" role="status">正在更新维修任务…</p>
        <p v-else-if="activeError" class="dashboard-section-message" role="alert">{{ activeError }} · 可使用顶部刷新重试</p>
        <div v-else-if="activeRepairs.length" class="active-repairs-grid"><ActiveRepairCard v-for="order in activeRepairs" :key="order.id" :order="order" /></div>
        <div v-else class="dashboard-section-message"><strong>当前暂无正在维修的工单</strong><p>维修人员开始处理后，任务会显示在这里。</p></div>
      </section>

      <div class="operations-bottom">
        <ChartPanel title="最新工单" description="最近提交的5项 · 查看下一步处理"><template #action><RouterLink class="text-button" to="/admin/orders">全部工单 ↗</RouterLink></template>
          <p v-if="listsLoading" class="dashboard-section-message" role="status">正在更新最新工单…</p>
          <p v-else-if="latestError" class="dashboard-section-message" role="alert">{{ latestError }} · 请刷新重试</p>
          <ul v-else-if="latestOrders.length" class="latest-orders"><li v-for="order in latestOrders" :key="order.id"><RouterLink :to="`/admin/orders/${order.id}`">
            <div class="latest-order-meta"><span>#{{ order.id }} · {{ order.typeName }}</span><OrderStatusTag :order="order" /></div><h3>{{ order.title }}</h3><p>{{ order.buildingName }} · {{ order.roomNo }}</p><time :datetime="order.createTime">{{ formatTime(order.createTime) }}</time>
          </RouterLink></li></ul>
          <div v-else class="dashboard-section-message"><strong>还没有报修工单</strong><p>学生提交后，最新工单将显示在这里。</p></div>
        </ChartPanel>
        <ChartPanel title="维修人员效率" description="全量完成单数排序 · 评分来自学生评价"><WorkerRanking :workers="workers" /></ChartPanel>
        <DashboardMap :markers="markers" :order-count="orders.length" />
      </div>
    </template>
  </section>
</template>

<style scoped>
.operations-dashboard { display: grid; gap: var(--space-5); min-width: 0; max-width: var(--page-max); }
.operations-dashboard > :deep(.operations-hero) { animation: dashboard-enter var(--motion-enter) var(--ease-out) both; }
.operations-metrics.dashboard-cards { margin: 0; padding: var(--space-2); gap: var(--space-2); border-radius: var(--radius-lg); box-shadow: var(--surface-shadow); }
.operations-metrics :deep(.dashboard-card) { min-height: 176px; padding: var(--space-5); animation: none; transition: background var(--motion-fast); }
.operations-metrics :deep(.dashboard-card:hover) { background: var(--surface-subtle); }
.operations-metrics :deep(.dashboard-card > span) { color: var(--ink-secondary); font-weight: 500; font-size: var(--text-support); }
.operations-metrics :deep(.dashboard-card strong) { font-size: var(--text-number); margin: var(--space-5) 0 var(--space-4); }
.operations-metrics :deep(.dashboard-card.is-empty strong) { font-size: 24px; }
.operations-metrics :deep(.dashboard-card p) { line-height: 1.7; }
.operations-metrics :deep(.metric-overdue.has-overdue) { background: var(--danger-soft); }.operations-metrics :deep(.metric-overdue.has-overdue strong) { color: var(--danger); }
.operations-metrics :deep(.metric-overdue.has-overdue:hover) { background: var(--danger-soft); }
.operations-attention { display: flex; align-items: center; gap: var(--space-2) var(--space-4); flex-wrap: wrap; padding: 0 var(--space-2); font-size: var(--text-caption); color: var(--muted); }
.operations-attention > span { font-weight: 500; }.operations-attention a { display: inline-flex; align-items: center; gap: var(--space-2); color: var(--ink-secondary); padding: var(--space-2) var(--space-3); border-radius: var(--radius-sm); background: var(--surface); transition: background var(--motion-fast), color var(--motion-fast); }
.operations-attention a:hover { background: var(--accent-soft); color: var(--accent); }.operations-attention a.attention-danger { color: var(--danger); background: var(--danger-soft); }.operations-attention a > span { font-size: 11px; }.operations-attention strong { font-weight: 600; font-variant-numeric: tabular-nums; }
.operations-attention > p { margin-left: auto; line-height: 1.7; }.operations-attention p strong { color: var(--ink-secondary); margin-left: var(--space-2); }.operations-attention small { display: block; text-align: right; font-size: 11px; }
.operations-analysis { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); gap: var(--space-5); align-items: stretch; }
.operations-dashboard :deep(.chart-panel) { min-width: 0; padding: var(--space-5); margin: 0; box-shadow: var(--surface-shadow); }.operations-dashboard :deep(.chart-panel > header) { gap: var(--space-3); margin-bottom: var(--space-5); }.operations-dashboard :deep(.chart-panel h2) { font-size: var(--text-section); font-weight: 600; }.operations-dashboard :deep(.chart-panel header .text-button) { flex-shrink: 0; font-size: var(--text-caption); }
.operations-section-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--space-4); margin-bottom: var(--space-5); }.operations-section-heading .eyebrow { font-size: 11px; color: var(--muted); margin-bottom: var(--space-2); }.operations-section-heading h2 { font-size: var(--text-section); font-weight: 600; letter-spacing: -.025em; }.operations-section-heading h2 > span { display: inline-block; color: var(--muted); font-size: var(--text-support); font-weight: 400; margin-left: var(--space-2); }.operations-section-heading h2 + p { color: var(--muted); font-size: var(--text-caption); line-height: 1.7; margin-top: var(--space-2); }.operations-section-heading a { flex-shrink: 0; font-size: var(--text-caption); }
.operations-active { padding-top: var(--space-2); }.active-repairs-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-4); }
.operations-bottom { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.25fr); gap: var(--space-5); align-items: start; margin-top: var(--space-2); }
.latest-orders { list-style: none; padding: 0; margin: 0; max-height: 360px; overflow-y: auto; scrollbar-width: thin; }.latest-orders li { border-bottom: 1px solid var(--surface-line); }.latest-orders li:last-child { border: 0; }.latest-orders a { display: block; padding: var(--space-4) var(--space-2); border-radius: var(--radius-sm); transition: background var(--motion-fast); color: var(--ink); }
.latest-orders a:hover { background: var(--surface-subtle); }
.latest-order-meta { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--space-2); color: var(--muted); font-size: 11px; }.latest-orders h3 { font-size: var(--text-support); font-weight: 500; line-height: 1.7; margin-top: var(--space-2); overflow-wrap: anywhere; }.latest-orders p { color: var(--ink-secondary); font-size: var(--text-caption); line-height: 1.65; margin-top: var(--space-1); overflow-wrap: anywhere; }.latest-orders time { display: block; color: var(--muted); font-size: 11px; margin-top: var(--space-2); }
.dashboard-section-message { text-align: center; border-radius: var(--radius); padding: var(--space-7) var(--space-5); background: var(--surface); color: var(--muted); font-size: var(--text-support); line-height: 1.8; }.dashboard-section-message strong { display: block; color: var(--ink-secondary); font-size: var(--text-body); font-weight: 500; }.dashboard-section-message p { margin-top: var(--space-2); font-size: var(--text-caption); }
.dashboard-load-error { display: flex; align-items: center; justify-content: space-between; gap: var(--space-4); padding: var(--space-5); border-radius: var(--radius); background: var(--danger-soft); color: var(--danger); font-size: var(--text-support); }.dashboard-load-error strong { font-weight: 500; }.dashboard-load-error p { margin-top: var(--space-2); overflow-wrap: anywhere; }
.dashboard-skeleton { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-4); }.dashboard-skeleton i { display: block; height: 176px; background: var(--surface); border: 1px solid var(--surface-line); border-radius: var(--radius); }
/* Animate only newly mounted sections; refreshing existing data does not replay entries. */
.operations-metrics { animation: dashboard-enter var(--motion-enter) 30ms var(--ease-out) both; }.operations-analysis { animation: dashboard-enter var(--motion-enter) 60ms var(--ease-out) both; }.operations-active, .operations-bottom { animation: dashboard-enter var(--motion-enter) 90ms var(--ease-out) both; }
@keyframes dashboard-enter { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 1280px) { .active-repairs-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.operations-bottom { grid-template-columns: repeat(2, minmax(0, 1fr)); }.operations-bottom > :deep(.dashboard-campus-map) { grid-column: 1 / -1; } }
@media (max-width: 1000px) { .operations-attention > p { width: 100%; margin-left: 0; }.operations-attention small { display: inline; margin-left: var(--space-3); }.operations-analysis { grid-template-columns: 1fr; } }
@media (max-width: 740px) { .operations-dashboard { gap: var(--space-5); }.operations-metrics.dashboard-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }.operations-metrics :deep(.dashboard-card) { padding: var(--space-4); min-height: 160px; }.operations-metrics :deep(.dashboard-card strong) { font-size: 34px; }.operations-metrics :deep(.dashboard-card.is-empty strong) { font-size: 21px; }.operations-section-heading { align-items: flex-start; flex-direction: column; gap: var(--space-3); }.operations-bottom { grid-template-columns: 1fr; }.dashboard-skeleton { grid-template-columns: repeat(2, minmax(0, 1fr)); }.dashboard-load-error { align-items: flex-start; flex-direction: column; } }
@media (max-width: 480px) { .active-repairs-grid { grid-template-columns: 1fr; }.operations-attention { padding: 0; gap: var(--space-2); }.operations-attention > span { width: 100%; }.operations-attention a { padding: var(--space-2); }.operations-attention small { display: block; margin-left: 0; text-align: left; } }
@media (prefers-reduced-motion: reduce) { .operations-dashboard > :deep(.operations-hero), .operations-metrics, .operations-analysis, .operations-active, .operations-bottom { animation: none; }.operations-metrics :deep(.dashboard-card), .operations-attention a, .latest-orders a { transition: none; } }
</style>
