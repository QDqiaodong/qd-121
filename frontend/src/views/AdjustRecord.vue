<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">层位调整记录台账</h2>
      <div>
        <el-button type="success" @click="handleExport">
          <el-icon><Download /></el-icon>导出筛选结果
        </el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" @submit.prevent="handleSearch">
        <el-form-item label="垫板编号">
          <el-input
            v-model="searchForm.padCode"
            placeholder="请输入垫板编号"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="调整类型">
          <el-select
            v-model="searchForm.adjustType"
            placeholder="全部类型"
            clearable
            style="width: 140px"
          >
            <el-option label="初始绑定" value="BIND" />
            <el-option label="变更绑定" value="REBIND" />
            <el-option label="解除绑定" value="UNBIND" />
            <el-option label="领用离架" value="CHECKOUT" />
            <el-option label="归还上架" value="RETURN" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshRight /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="padCode" label="垫板编号" width="140" />
      <el-table-column label="调整类型" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="getAdjustTagType(row.adjustType)" effect="light">
            <el-icon style="margin-right: 2px">
              <component :is="getAdjustIcon(row.adjustType)" />
            </el-icon>
            {{ getAdjustTypeLabel(row.adjustType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="层位变更" width="280">
        <template #default="{ row }">
          <div class="layer-change">
            <span class="old-layer" :class="{ empty: !row.oldLayerCode }">
              {{ row.oldLayerCode || '未绑定' }}
            </span>
            <el-icon :size="18" color="#909399"><Right /></el-icon>
            <span class="new-layer" :class="{ empty: !row.newLayerCode }">
              {{ row.newLayerCode || '已解绑' }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="120">
        <template #default="{ row }">{{ row.operator || '-' }}</template>
      </el-table-column>
      <el-table-column prop="adjustReason" label="调整原因" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.adjustReason || '-' }}</template>
      </el-table-column>
      <el-table-column prop="adjustTime" label="调整时间" width="180" sortable>
        <template #default="{ row }">{{ formatTime(row.adjustTime) }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      v-model:current-page="pagination.pageNum"
      v-model:page-size="pagination.pageSize"
      :page-sizes="[20, 50, 100, 200]"
      :total="pagination.total"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadData"
      @current-change="loadData"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  CircleCheck,
  RefreshRight,
  Warning,
  Close,
  Right,
  Promotion,
  Van
} from '@element-plus/icons-vue'
import { exportRecords, getRecordPage } from '@/api/record'
import { downloadBlob, getFileNameFromDisposition } from '@/utils/download'
import dayjs from 'dayjs'

const loading = ref(false)
const tableData = ref([])
const dateRange = ref([])

const searchForm = reactive({
  padCode: '',
  adjustType: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

const getAdjustTypeLabel = (type) => {
  const map = {
    BIND: '初始绑定',
    REBIND: '变更绑定',
    UNBIND: '解除绑定',
    CHECKOUT: '领用离架',
    RETURN: '归还上架'
  }
  return map[type] || type
}

const getAdjustTagType = (type) => {
  const map = {
    BIND: 'success',
    REBIND: 'warning',
    UNBIND: 'danger',
    CHECKOUT: 'warning',
    RETURN: 'success'
  }
  return map[type] || 'info'
}

const getAdjustIcon = (type) => {
  const map = {
    BIND: CircleCheck,
    REBIND: RefreshRight,
    UNBIND: Close,
    CHECKOUT: Promotion,
    RETURN: Van
  }
  return map[type] || Warning
}

const buildQuery = () => {
  const query = { ...searchForm, ...pagination }
  if (dateRange.value && dateRange.value.length === 2) {
    query.startTime = dateRange.value[0]
    query.endTime = dateRange.value[1]
  }
  return query
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getRecordPage(buildQuery())
    tableData.value = data.records || []
    pagination.total = data.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { padCode: '', adjustType: '' })
  dateRange.value = []
  handleSearch()
}

const handleExport = async () => {
  try {
    const query = buildQuery()
    query.pageNum = 1
    query.pageSize = 10000
    const res = await exportRecords(query)
    const filename = getFileNameFromDisposition(res.headers['content-disposition'])
    downloadBlob(res.data, filename || '层位调整记录台账.xlsx')
  } catch (e) {
    console.error(e)
  }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.layer-change {
  display: flex;
  align-items: center;
  gap: 8px;

  .old-layer,
  .new-layer {
    flex-shrink: 0;
    padding: 3px 10px;
    border-radius: 4px;
    font-size: 13px;
    font-weight: 500;
  }

  .old-layer {
    background: #fdf6ec;
    color: #e6a23c;
    border: 1px solid #faecd8;

    &.empty {
      background: #f4f4f5;
      color: #909399;
      border-color: #e9e9eb;
    }
  }

  .new-layer {
    background: #f0f9eb;
    color: #67c23a;
    border: 1px solid #e1f3d8;

    &.empty {
      background: #fef0f0;
      color: #f56c6c;
      border-color: #fde2e2;
    }
  }
}
</style>
