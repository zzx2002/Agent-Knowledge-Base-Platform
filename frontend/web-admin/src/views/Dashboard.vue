<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import * as echarts from 'echarts'
import {
  FolderOpened,
  Document,
  Coin,
  Tickets,
  TrendCharts,
  PieChart,
  List,
  ArrowRight,
  MagicStick,
} from '@element-plus/icons-vue'

// 平台概览指标（对应文档 9.1 系统首页指标，示例统计）
const stats = ref([
  { label: '知识库数量', value: '12', unit: '个', trend: '+2 本周', icon: FolderOpened, tone: 'purple' },
  { label: '文档数量', value: '1,284', unit: '份', trend: '+8.4%', icon: Document, tone: 'blue' },
  { label: '文件存储容量', value: '36.5 GB', unit: '', trend: '运行稳定', icon: Coin, tone: 'orange' },
  { label: '解析任务数量', value: '328', unit: '个', trend: '+5.2%', icon: Tickets, tone: 'green' },
])

// 其余概览指标（保留原有占位数据）
const moreStats = ref([
  { label: '解析成功率', value: '98.6%' },
  { label: '检索调用量', value: '8,431' },
  { label: '问答调用量', value: '5,209' },
])

// 最近任务（示例统计）
const recentTasks = ref([
  { id: 'task_9f3a', name: '产品需求说明书 v2.docx', status: 'COMPLETED', time: '10 分钟前' },
  { id: 'task_8k21', name: '2026 Q3 研发规划.pdf', status: 'PROCESSING', time: '32 分钟前' },
  { id: 'task_7d10', name: '接口文档汇总.xlsx', status: 'FAILED', time: '1 小时前' },
  { id: 'task_6c04', name: '运维手册合辑.pptx', status: 'PENDING', time: '2 小时前' },
])

const taskStatusLabel: Record<string, string> = {
  COMPLETED: '已完成',
  PROCESSING: '解析中',
  FAILED: '失败',
  PENDING: '等待中',
}

function taskStatusClass(status: string) {
  const map: Record<string, string> = {
    COMPLETED: 'done',
    PROCESSING: 'processing',
    FAILED: 'failed',
    PENDING: 'pending',
  }
  return map[status] || 'pending'
}

// 最近知识库（示例统计）
const recentKbs = ref([
  { name: '产品文档库', docs: 486, updated: '今天 09:42', tone: 'purple' },
  { name: '研发知识库', docs: 352, updated: '昨天 18:20', tone: 'blue' },
  { name: '运维手册库', docs: 221, updated: '11-16 14:05', tone: 'orange' },
  { name: '制度规范库', docs: 225, updated: '11-15 10:30', tone: 'green' },
])

// 图表配色：紫色 / 橙色
const chartPalette = ['#6c5ce7', '#ff9f43', '#a29bfe', '#ffb86b', '#8b7cf1']

const chartRef = ref<HTMLDivElement>()
const trendChartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined
let trendChart: echarts.ECharts | undefined

onMounted(() => {
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}：{c} 份（{d}%）' },
      legend: {
        bottom: 0,
        icon: 'circle',
        itemWidth: 9,
        itemHeight: 9,
        textStyle: { color: '#8b93a7', fontSize: 12 },
      },
      color: chartPalette,
      series: [
        {
          type: 'pie',
          radius: ['50%', '72%'],
          center: ['50%', '43%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 8 },
          label: { show: false, position: 'center' },
          labelLine: { show: false },
          emphasis: { scale: true, scaleSize: 6 },
          data: [
            { value: 420, name: 'DOCX' },
            { value: 310, name: 'PDF' },
            { value: 220, name: 'XLSX' },
            { value: 180, name: 'PPTX' },
            { value: 154, name: '其他' },
          ],
        },
      ],
      graphic: [
        {
          type: 'text',
          left: 'center',
          top: '35%',
          style: { text: '1,284', textAlign: 'center', fill: '#1f2434', fontSize: 24, fontWeight: 700 },
        },
        {
          type: 'text',
          left: 'center',
          top: '47%',
          style: { text: '文档总量', textAlign: 'center', fill: '#8b93a7', fontSize: 12 },
        },
      ],
    })
  }
  if (trendChartRef.value) {
    trendChart = echarts.init(trendChartRef.value)
    trendChart.setOption({
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#fff',
        borderColor: '#eceef4',
        textStyle: { color: '#1f2434' },
      },
      legend: {
        data: ['检索', '问答'],
        bottom: 0,
        icon: 'circle',
        itemWidth: 9,
        itemHeight: 9,
        textStyle: { color: '#8b93a7', fontSize: 12 },
      },
      grid: { left: 8, right: 16, top: 24, bottom: 42, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
        axisLine: { lineStyle: { color: '#eceef4' } },
        axisTick: { show: false },
        axisLabel: { color: '#8b93a7', fontSize: 12 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f0f1f6', type: 'dashed' } },
        axisLabel: { color: '#8b93a7', fontSize: 12 },
      },
      series: [
        {
          name: '检索',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          showSymbol: false,
          symbolSize: 7,
          lineStyle: { width: 3, color: '#6c5ce7' },
          itemStyle: { color: '#6c5ce7' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(108, 92, 231, 0.28)' },
              { offset: 1, color: 'rgba(108, 92, 231, 0)' },
            ]),
          },
          data: [820, 932, 901, 1034, 1290, 860, 760],
        },
        {
          name: '问答',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          showSymbol: false,
          symbolSize: 7,
          lineStyle: { width: 3, color: '#ff9f43' },
          itemStyle: { color: '#ff9f43' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(255, 159, 67, 0.22)' },
              { offset: 1, color: 'rgba(255, 159, 67, 0)' },
            ]),
          },
          data: [520, 632, 701, 834, 990, 560, 460],
        },
      ],
    })
  }
  window.addEventListener('resize', handleResize)
})

function handleResize() {
  chart?.resize()
  trendChart?.resize()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  trendChart?.dispose()
})
</script>

<template>
  <div class="page-container dashboard">
    <div class="welcome">
      <div class="welcome-copy">
        <div class="welcome-eyebrow"><el-icon><MagicStick /></el-icon> 数据面板</div>
        <h2 class="page-title">欢迎回来，管理员</h2>
        <p class="page-desc">这是平台今天的运行概览与关键指标，数据为示例统计，仅用于展示。</p>
      </div>
      <div class="welcome-badge"><span class="badge-dot"></span> 系统运行正常</div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="item in stats" :key="item.label" :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="stat-card" :class="`tone-${item.tone}`">
          <div class="stat-head">
            <div class="stat-icon"><el-icon><component :is="item.icon" /></el-icon></div>
            <span class="stat-trend">{{ item.trend }}</span>
          </div>
          <div class="stat-value">{{ item.value }}<small v-if="item.unit">{{ item.unit }}</small></div>
          <div class="stat-label">{{ item.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 其余概览指标 -->
    <el-card shadow="never" class="more-card">
      <div class="more-stats">
        <div v-for="(item, idx) in moreStats" :key="item.label" class="more-stat" :class="{ divided: idx > 0 }">
          <span class="more-label">{{ item.label }}</span>
          <span class="more-value">{{ item.value }}</span>
        </div>
      </div>
    </el-card>

    <!-- 趋势 + 分布 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="section-head">
              <div class="section-title"><el-icon><TrendCharts /></el-icon> 调用趋势</div>
              <span class="section-tag">近 7 日 · 平台概览</span>
            </div>
          </template>
          <div ref="trendChartRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="section-head">
              <div class="section-title"><el-icon><PieChart /></el-icon> 文档类型分布</div>
              <span class="section-tag">示例统计</span>
            </div>
          </template>
          <div ref="chartRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近任务 / 最近知识库 -->
    <el-row :gutter="16" class="list-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="section-head">
              <div class="section-title"><el-icon><List /></el-icon> 最近任务</div>
              <span class="section-tag">示例统计</span>
            </div>
          </template>
          <div class="recent-list">
            <div v-for="task in recentTasks" :key="task.id" class="recent-item">
              <div class="recent-file-icon"><el-icon><Document /></el-icon></div>
              <div class="recent-main">
                <div class="recent-name">{{ task.name }}</div>
                <div class="recent-sub">{{ task.id }} · {{ task.time }}</div>
              </div>
              <span class="status-chip" :class="taskStatusClass(task.status)">{{ taskStatusLabel[task.status] || task.status }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="section-head">
              <div class="section-title"><el-icon><FolderOpened /></el-icon> 最近知识库</div>
              <span class="section-tag">示例统计</span>
            </div>
          </template>
          <div class="recent-list">
            <div v-for="kb in recentKbs" :key="kb.name" class="recent-item">
              <div class="kb-icon" :class="`tone-${kb.tone}`"><el-icon><FolderOpened /></el-icon></div>
              <div class="recent-main">
                <div class="recent-name">{{ kb.name }}</div>
                <div class="recent-sub">{{ kb.docs }} 份文档 · 更新于 {{ kb.updated }}</div>
              </div>
              <el-icon class="row-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  padding-bottom: 40px;
}

/* 欢迎语 */
.welcome {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}
.welcome-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  margin-bottom: 10px;
  border-radius: 999px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-size: 12px;
  font-weight: 600;
}
.welcome-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--kb-border);
  box-shadow: var(--kb-shadow);
  color: #5c6377;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}
.badge-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.15);
}

/* 统计卡片 */
.stat-row {
  margin-bottom: 2px;
}
.stat-card {
  position: relative;
  overflow: hidden;
  margin-bottom: 16px;
  min-height: 148px;
  transition: transform 0.2s ease;
}
.stat-card:hover {
  transform: translateY(-3px);
}
.stat-card::after {
  content: '';
  position: absolute;
  right: -28px;
  bottom: -40px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: var(--kb-primary-soft);
  opacity: 0.6;
}
.stat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  position: relative;
  z-index: 1;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  box-shadow: 0 8px 16px rgba(31, 36, 52, 0.12);
}
.stat-trend {
  padding: 3px 9px;
  border-radius: 999px;
  background: #f0fdf4;
  color: #16a34a;
  font-size: 11px;
  font-weight: 600;
}
.stat-value {
  position: relative;
  z-index: 1;
  margin-top: 14px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.5px;
  color: #171b2b;
}
.stat-value small {
  margin-left: 4px;
  font-size: 14px;
  font-weight: 500;
  color: #8b93a7;
}
.stat-label {
  position: relative;
  z-index: 1;
  margin-top: 6px;
  color: var(--kb-muted);
  font-size: 13px;
}
.tone-purple .stat-icon { background: linear-gradient(135deg, #6c5ce7, #8b7cf1); }
.tone-blue .stat-icon { background: linear-gradient(135deg, #4a90e2, #6cb8f0); }
.tone-orange .stat-icon { background: linear-gradient(135deg, #ff9f43, #ffbe76); }
.tone-green .stat-icon { background: linear-gradient(135deg, #26c6a2, #5cdbbd); }

/* 其余指标 */
.more-card {
  margin-bottom: 16px;
}
.more-stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0;
}
.more-stat {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 2px 26px;
}
.more-stat:first-child {
  padding-left: 4px;
}
.more-stat.divided {
  border-left: 1px solid var(--kb-border);
}
.more-label {
  color: var(--kb-muted);
  font-size: 13px;
}
.more-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--kb-primary);
}

/* 图表卡片 */
.chart-row {
  margin-top: 2px;
}
.chart-card {
  height: 100%;
}
.chart {
  height: 300px;
  width: 100%;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.section-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: #1f2434;
}
.section-title .el-icon {
  color: var(--kb-primary);
  font-size: 17px;
}
.section-tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

/* 最近列表卡片 */
.list-row {
  margin-top: 16px;
}
.list-card {
  height: 100%;
}
.recent-list {
  display: flex;
  flex-direction: column;
}
.recent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 4px;
  border-bottom: 1px solid var(--kb-border);
}
.recent-item:last-child {
  border-bottom: none;
}
.recent-file-icon,
.kb-icon {
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
}
.recent-file-icon {
  background: #f4f5fa;
  color: #8b93a7;
}
.kb-icon { color: #fff; }
.kb-icon.tone-purple { background: linear-gradient(135deg, #6c5ce7, #8b7cf1); }
.kb-icon.tone-blue { background: linear-gradient(135deg, #4a90e2, #6cb8f0); }
.kb-icon.tone-orange { background: linear-gradient(135deg, #ff9f43, #ffbe76); }
.kb-icon.tone-green { background: linear-gradient(135deg, #26c6a2, #5cdbbd); }
.recent-main {
  flex: 1;
  min-width: 0;
}
.recent-name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2434;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--kb-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-arrow {
  color: #c7cbd6;
  font-size: 15px;
}

/* 状态 chip */
.status-chip {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.status-chip.done { background: #e8f8f0; color: #16a34a; }
.status-chip.processing { background: var(--kb-primary-soft); color: var(--kb-primary); }
.status-chip.failed { background: #fef0f0; color: #e74c3c; }
.status-chip.pending { background: #f4f5f7; color: #7b8399; }

@media (max-width: 768px) {
  .welcome-badge { display: none; }
  .more-stat { padding: 6px 14px; }
  .more-stat.divided { border-left: none; }
  .chart { height: 260px; }
}
</style>
