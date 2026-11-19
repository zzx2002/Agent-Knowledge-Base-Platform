<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import {
  FolderOpened,
  Document,
  Coin,
  Tickets,
  Search,
  ChatDotRound,
} from '@element-plus/icons-vue'
import {
  getDashboardStats,
  type DashboardData,
  type StatOverview,
} from '@/api/stats'

const data = ref<DashboardData | null>(null)
const loading = ref(false)
const trendRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | undefined
let pieChart: echarts.ECharts | undefined

const statCards = ref<Array<{ label: string; value: string; icon: unknown; tone: string }>>([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await getDashboardStats()
    data.value = ((res as any).data ?? res) as DashboardData
    buildStatCards(data.value.overview)
    await nextTick()
    initCharts()
  } finally {
    loading.value = false
  }
  window.addEventListener('resize', handleResize)
})

function buildStatCards(o: StatOverview) {
  statCards.value = [
    { label: '知识库数量', value: String(o.kbCount), icon: FolderOpened, tone: 'purple' },
    { label: '文档数量', value: String(o.documentCount), icon: Document, tone: 'blue' },
    { label: '文件存储容量', value: formatSize(o.storageBytes), icon: Coin, tone: 'orange' },
    { label: '解析任务数量', value: String(o.taskCount), icon: Tickets, tone: 'green' },
  ]
}

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index++
  }
  return `${value.toFixed(index > 0 ? 1 : 0)} ${units[index]}`
}

function initCharts() {
  if (trendRef.value) {
    trendChart = echarts.init(trendRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['检索调用', '问答调用'], bottom: 0 },
      grid: { left: 12, right: 12, top: 20, bottom: 44, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: data.value?.trend.map((item) => item.day) ?? [],
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '检索调用',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          lineStyle: { width: 3, color: '#6c5ce7' },
          itemStyle: { color: '#6c5ce7' },
          areaStyle: { color: 'rgba(108,92,231,0.12)' },
          data: data.value?.trend.map((item) => item.search) ?? [],
        },
        {
          name: '问答调用',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          lineStyle: { width: 3, color: '#ff9f43' },
          itemStyle: { color: '#ff9f43' },
          areaStyle: { color: 'rgba(255,159,67,0.10)' },
          data: data.value?.trend.map((item) => item.chat) ?? [],
        },
      ],
    })
  }
  if (pieRef.value) {
    pieChart = echarts.init(pieRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['42%', '68%'],
          center: ['50%', '46%'],
          itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 6 },
          data: data.value?.documentTypes.length
            ? data.value.documentTypes
            : [{ name: '暂无文档', value: 1 }],
        },
      ],
    })
  }
}

function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
})

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    COMPLETED: '已完成',
    PROCESSING: '解析中',
    PENDING: '等待中',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return status ? map[status] || status : '未知'
}

function statusType(status?: string) {
  const map: Record<string, string> = {
    COMPLETED: 'success',
    PROCESSING: 'primary',
    PENDING: 'warning',
    FAILED: 'danger',
    CANCELLED: 'info',
  }
  return status ? map[status] || 'info' : 'info'
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header welcome">
      <div>
        <h2 class="page-title">数据面板</h2>
        <p class="page-desc">平台运行概览，数据实时来自知识库、文档、任务与访问审计</p>
      </div>
      <span class="live-badge">实时统计</span>
    </div>

    <el-row :gutter="16">
      <el-col v-for="card in statCards" :key="card.label" :xs="24" :sm="12" :md="6">
        <div class="stat-card" :class="card.tone">
          <div class="stat-icon"><el-icon><component :is="card.icon" /></el-icon></div>
          <div class="stat-copy">
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="mini-stats">
      <div class="mini-item"><el-icon><Tickets /></el-icon> 解析成功率 <strong>{{ data?.overview.successRate ?? 0 }}%</strong></div>
      <div class="mini-item"><el-icon><Search /></el-icon> 近 30 日检索 <strong>{{ data?.overview.searchCalls ?? 0 }}</strong> 次</div>
      <div class="mini-item"><el-icon><ChatDotRound /></el-icon> 近 30 日问答 <strong>{{ data?.overview.chatCalls ?? 0 }}</strong> 次</div>
    </div>

    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <div class="card-head">
            <div><strong>调用趋势</strong><span>近 7 日检索与问答调用量</span></div>
          </div>
          <div ref="trendRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <div class="card-head">
            <div><strong>文档类型分布</strong><span>按文件类型统计</span></div>
          </div>
          <div ref="pieRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="list-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <div class="card-head"><div><strong>最近解析任务</strong><span>最新 5 条任务</span></div></div>
          <div v-for="task in data?.recentTasks ?? []" :key="task.id" class="list-item">
            <div class="item-main">
              <strong>{{ task.fileName }}</strong>
              <span>{{ task.createdAt }}</span>
            </div>
            <el-tag :type="statusType(task.status) as any">{{ statusLabel(task.status) }}</el-tag>
          </div>
          <el-empty v-if="!(data?.recentTasks ?? []).length" description="暂无解析任务" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <div class="card-head"><div><strong>最近知识库</strong><span>最近更新的知识库</span></div></div>
          <div v-for="kb in data?.recentKnowledgeBases ?? []" :key="kb.id" class="list-item">
            <div class="item-main">
              <strong>{{ kb.name }}</strong>
              <span>{{ kb.embeddingModel || '未配置模型' }} · {{ kb.updatedAt }}</span>
            </div>
            <el-tag type="primary">{{ kb.status === 'ACTIVE' ? '运行中' : kb.status }}</el-tag>
          </div>
          <el-empty v-if="!(data?.recentKnowledgeBases ?? []).length" description="暂无知识库" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.live-badge {
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
}
.stat-card {
  background: #fff;
  border: 1px solid var(--kb-border);
  border-radius: 18px;
  padding: 20px;
  margin-bottom: 16px;
  display: flex;
  gap: 14px;
  align-items: center;
  box-shadow: var(--kb-shadow);
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
}
.stat-card.purple .stat-icon { background: linear-gradient(135deg, #6c5ce7, #8b7cf1); }
.stat-card.blue .stat-icon { background: linear-gradient(135deg, #4facfe, #00a8ff); }
.stat-card.orange .stat-icon { background: linear-gradient(135deg, #ffb86b, #ff8e72); }
.stat-card.green .stat-icon { background: linear-gradient(135deg, #43c6ac, #28a745); }
.stat-copy { display: flex; flex-direction: column; gap: 4px; }
.stat-copy span { font-size: 13px; color: var(--kb-muted); }
.stat-copy strong { font-size: 25px; color: #1c2030; }
.mini-stats {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin: 6px 0 18px;
}
.mini-item {
  background: #fff;
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  padding: 12px 16px;
  font-size: 13px;
  color: var(--kb-muted);
  display: flex;
  align-items: center;
  gap: 8px;
}
.mini-item strong { color: var(--kb-primary); font-size: 16px; }
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}
.card-head div { display: flex; flex-direction: column; gap: 4px; }
.card-head strong { font-size: 16px; }
.card-head span { font-size: 12px; color: var(--kb-muted); }
.chart { height: 300px; }
.chart-row, .list-row { margin-top: 4px; }
.list-row { margin-top: 16px; }
.list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--kb-border);
}
.list-item:last-child { border-bottom: none; }
.item-main { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.item-main strong { font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.item-main span { font-size: 12px; color: var(--kb-muted); }
</style>
