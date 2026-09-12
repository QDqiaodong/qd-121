<template>
  <div class="dashboard">
    <el-row :gutter="20" class="stat-row">
      <el-col :span="6">
        <div class="stat-card card-total">
          <div class="stat-icon"><el-icon :size="36"><Goods /></el-icon></div>
          <div class="stat-content">
            <div class="stat-label">垫板总数</div>
            <div class="stat-value">{{ statistics.totalCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-bound">
          <div class="stat-icon"><el-icon :size="36"><CircleCheck /></el-icon></div>
          <div class="stat-content">
            <div class="stat-label">已绑定层位</div>
            <div class="stat-value">{{ statistics.boundCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-borrowed" @click="goBorrow">
          <div class="stat-icon"><el-icon :size="36"><Van /></el-icon></div>
          <div class="stat-content">
            <div class="stat-label">领用中（离架）</div>
            <div class="stat-value">{{ statistics.borrowedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-unbound" @click="goBorrowOverdue">
          <div class="stat-icon"><el-icon :size="36"><Warning /></el-icon></div>
          <div class="stat-content">
            <div class="stat-label">逾期未还</div>
            <div class="stat-value">{{ statistics.overdueCount || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-row">
      <el-col :span="16">
        <div class="page-container">
          <div class="page-header">
            <h3 class="page-title">货架分层分布</h3>
            <el-button type="primary" size="small" @click="refreshData">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
          <div v-loading="shelfLoading" class="shelf-overview">
            <el-row :gutter="16">
              <el-col :span="12" v-for="shelf in groupedShelves" :key="shelf.shelfCode">
                <div class="shelf-group-card">
                  <div class="shelf-header">
                    <el-icon :size="20" color="#2563eb"><Box /></el-icon>
                    <span class="shelf-title">{{ shelf.shelfCode }} 货架</span>
                    <el-tag size="small" type="info">共 {{ shelf.totalCount }} 块垫板</el-tag>
                  </div>
                  <div class="layer-list">
                    <div
                      v-for="layer in shelf.layers"
                      :key="layer.layerCode"
                      class="layer-item"
                    >
                      <div class="layer-info">
                        <span class="layer-code">{{ layer.layerCode }}</span>
                        <span class="layer-name">{{ layer.layerName }}</span>
                      </div>
                      <div class="layer-count">
                        <el-progress
                          type="dashboard"
                          :percentage="getUsagePercent(layer)"
                          :width="50"
                          :stroke-width="8"
                          :show-text="false"
                          :status="getUsageStatus(layer)"
                        />
                        <span class="count-num" :class="{ 'count-full': isLayerFull(layer) }">
                          {{ layer.padCount }}/{{ layer.capacity ?? 0 }}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </el-col>
            </el-row>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="page-container">
          <div class="page-header">
            <h3 class="page-title">最近调整记录</h3>
          </div>
          <el-table :data="recentRecords" v-loading="recordLoading" size="small">
            <el-table-column prop="padCode" label="垫板编号" width="110" />
            <el-table-column label="调整类型" width="90">
              <template #default="{ row }">
                <el-tag :type="getAdjustTagType(row.adjustType)" size="small">
                  {{ getAdjustTypeLabel(row.adjustType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="newLayerCode" label="新层位" width="100" show-overflow-tooltip />
            <el-table-column prop="adjustTime" label="时间" width="150">
              <template #default="{ row }">
                {{ formatTime(row.adjustTime) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStatistics } from '@/api/pad'
import { getShelfLayerGroup } from '@/api/shelf'
import { getRecordPage } from '@/api/record'
import dayjs from 'dayjs'

const router = useRouter()
const goBorrow = () => router.push('/borrow')
const goBorrowOverdue = () => router.push({ path: '/borrow', query: { overdue: 1 } })

const statistics = reactive({
  totalCount: 0,
  boundCount: 0,
  unboundCount: 0,
  borrowedCount: 0,
  returnedCount: 0,
  overdueCount: 0
})
const groupedShelves = ref([])
const recentRecords = ref([])
const shelfLoading = ref(false)
const recordLoading = ref(false)

const formatTime = (time) => {
  return time ? dayjs(time).format('MM-DD HH:mm') : '-'
}

const getAdjustTypeLabel = (type) => {
  const map = {
    BIND: '初始绑定',
    REBIND: '变更绑定',
    UNBIND: '解除绑定',
    CHECKOUT: '领用离架',
    RETURN: '归还上架',
    SCRAP: '报废出库'
  }
  return map[type] || type
}

const getAdjustTagType = (type) => {
  const map = {
    BIND: 'success',
    REBIND: 'warning',
    UNBIND: 'danger',
    CHECKOUT: 'warning',
    RETURN: 'success',
    SCRAP: 'danger'
  }
  return map[type] || 'info'
}

const groupByShelf = (layers) => {
  const map = {}
  layers.forEach((layer) => {
    if (!map[layer.shelfCode]) {
      map[layer.shelfCode] = { shelfCode: layer.shelfCode, layers: [], totalCount: 0 }
    }
    map[layer.shelfCode].layers.push(layer)
    map[layer.shelfCode].totalCount += layer.padCount || 0
  })
  return Object.values(map)
}

// 层位占用按容量配额计算百分比，已满层位标红提示
const getUsagePercent = (layer) => {
  const capacity = layer.capacity || 0
  if (capacity <= 0) return (layer.padCount || 0) > 0 ? 100 : 0
  return Math.min(Math.round(((layer.padCount || 0) / capacity) * 100), 100)
}

const getUsageStatus = (layer) => {
  const percent = getUsagePercent(layer)
  if (percent >= 100) return 'exception'
  if (percent >= 80) return 'warning'
  return 'success'
}

const isLayerFull = (layer) => getUsagePercent(layer) >= 100

const loadStatistics = async () => {
  try {
    const data = await getStatistics()
    Object.assign(statistics, data)
  } catch (e) {
    console.error(e)
  }
}

const loadShelves = async () => {
  shelfLoading.value = true
  try {
    const data = await getShelfLayerGroup()
    groupedShelves.value = groupByShelf(data)
  } catch (e) {
    console.error(e)
  } finally {
    shelfLoading.value = false
  }
}

const loadRecentRecords = async () => {
  recordLoading.value = true
  try {
    const data = await getRecordPage({ pageNum: 1, pageSize: 10 })
    recentRecords.value = data.records || []
  } catch (e) {
    console.error(e)
  } finally {
    recordLoading.value = false
  }
}

const refreshData = () => {
  loadStatistics()
  loadShelves()
  loadRecentRecords()
}

onMounted(refreshData)
</script>

<style lang="scss" scoped>
.dashboard {
  .stat-row {
    margin-bottom: 20px;
  }

  .stat-card {
    display: flex;
    align-items: center;
    padding: 24px;
    border-radius: 12px;
    color: #fff;
    gap: 16px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
    transition: transform 0.2s;

    &:hover {
      transform: translateY(-2px);
    }

    &.card-total {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    }
    &.card-bound {
      background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
    }
    &.card-borrowed {
      background: linear-gradient(135deg, #f6a84c 0%, #f5851f 100%);
      cursor: pointer;
    }
    &.card-unbound {
      background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
      cursor: pointer;
    }

    .stat-icon {
      opacity: 0.9;
    }

    .stat-content {
      .stat-label {
        font-size: 14px;
        opacity: 0.9;
        margin-bottom: 4px;
      }
      .stat-value {
        font-size: 34px;
        font-weight: 700;
        line-height: 1;
      }
    }
  }

  .content-row {
    min-height: 400px;
  }

  .shelf-overview {
    .shelf-group-card {
      border: 1px solid #ebeef5;
      border-radius: 8px;
      margin-bottom: 16px;
      overflow: hidden;

      .shelf-header {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 12px 16px;
        background: #f5f7fa;
        border-bottom: 1px solid #ebeef5;

        .shelf-title {
          font-weight: 600;
          font-size: 15px;
          flex: 1;
        }
      }

      .layer-list {
        padding: 8px 0;

        .layer-item {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 10px 16px;

          &:hover {
            background: #f5f7fa;
          }

          .layer-info {
            display: flex;
            flex-direction: column;
            gap: 2px;

            .layer-code {
              font-weight: 600;
              font-size: 13px;
              color: #1e3a8a;
            }
            .layer-name {
              font-size: 12px;
              color: #909399;
            }
          }

          .layer-count {
            display: flex;
            align-items: center;
            gap: 8px;

            .count-num {
              font-size: 13px;
              font-weight: 600;
              color: #606266;

              &.count-full {
                color: #f56c6c;
              }
            }
          }
        }
      }
    }
  }
}
</style>
