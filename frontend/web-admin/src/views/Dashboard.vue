<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import * as echarts from 'echarts'

// 统计卡片占位数据（对应文档 9.1 系统首页指标）
const stats = ref([
  { label: '知识库数量', value: '12', icon: '库', color: '#409eff' },
  { label: '文档数量', value: '1,284', icon: '文', color: '#67c23a' },
  { label: '文件存储容量', value: '36.5 GB', icon: '存', color: '#e6a23c' },
  { label: '解析任务数量', value: '328', icon: '任', color: '#909399' },
  { label: '解析成功率', value: '98.6%', icon: '成', color: '#19be6b' },
  { label: '检索调用量', value: '8,431', icon: '检', color: '#9b59b6' },
  { label: '问答调用量', value: '5,209', icon: '答', color: '#f56c6c' },
])

const chartRef = ref<HTMLDivElement>()
const trendChartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined
let trendChart: echarts.ECharts | undefined

onMounted(() => {
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
    chart.setOption({
      title: { text: '文档类型分布（占位数据）', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['40%', '65%'],
          data: [
            { value: 420, name: 'DOCX' },
            { value: 310, name: 'PDF' },
            { value: 220, name: 'XLSX' },
            { value: 180, name: 'PPTX' },
            { value: 154, name: '其他' },
          ],
        },
      ],
    })
  }
  if (trendChartRef.value) {
    trendChart = echarts.init(trendChartRef.value)
    trendChart.setOption({
      title: { text: '近 7 日调用趋势（占位数据）', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      legend: { data: ['检索', '问答'], bottom: 0 },
      xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] },
      yAxis: { type: 'value' },
      series: [
        { name: '检索', type: 'line', smooth: true, data: [820, 932, 901, 1034, 1290, 860, 760] },
        { name: '问答', type: 'line', smooth: true, data: [520, 632, 701, 834, 990, 560, 460] },
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
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">系统首页</h2>
      <p class="page-desc">平台运行概览与关键指标（当前为占位数据）</p>
    </div>

    <el-row :gutter="16">
      <el-col v-for="item in stats" :key="item.label" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" :style="{ background: item.color }">{{ item.icon }}</div>
            <div class="stat-info">
              <div class="stat-label">{{ item.label }}</div>
              <div class="stat-value">{{ item.value }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <div ref="chartRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <div ref="trendChartRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat-card {
  margin-bottom: 16px;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 14px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  color: #fff;
}
.stat-label {
  color: var(--kb-muted);
  font-size: 13px;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 4px;
}
.chart-row {
  margin-top: 8px;
}
.chart {
  height: 320px;
  width: 100%;
}
</style>
