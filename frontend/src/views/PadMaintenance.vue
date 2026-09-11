<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">垫板保养台账</h2>
      <el-button type="primary" @click="openRegister">
        <el-icon><Tools /></el-icon>登记保养
      </el-button>
    </div>

    <el-row :gutter="16" class="stat-row">
      <el-col :span="8">
        <div class="stat-card card-available" @click="quickFilter('AVAILABLE')">
          <div class="stat-icon"><el-icon :size="30"><CircleCheck /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">可用</div>
            <div class="stat-value">{{ stats.availableCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card card-pending" @click="quickFilter('PENDING')">
          <div class="stat-icon"><el-icon :size="30"><Clock /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">待检</div>
            <div class="stat-value">{{ stats.pendingCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card card-disabled" @click="quickFilter('DISABLED')">
          <div class="stat-icon"><el-icon :size="30"><CircleClose /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">停用</div>
            <div class="stat-value">{{ stats.disabledCount || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" @submit.prevent="handleSearch">
        <el-form-item label="当前状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="可用" value="AVAILABLE" />
            <el-option label="待检" value="PENDING" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="垫板编号">
          <el-input
            v-model="searchForm.padCode"
            placeholder="请输入垫板编号"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="保养日期">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 340px"
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
      <el-table-column prop="padCode" label="垫板编号" width="130" />
      <el-table-column prop="moldType" label="适配模具" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.moldType || '-' }}</template>
      </el-table-column>
      <el-table-column label="保养类型" width="110">
        <template #default="{ row }">
          <el-tag type="info" effect="plain">{{ row.maintenanceType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="保养结果" width="120">
        <template #default="{ row }">
          <el-tag :type="getResultTagType(row.maintenanceResult)" size="small">
            {{ getResultLabel(row.maintenanceResult) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态变更" width="190">
        <template #default="{ row }">
          <div class="status-change">
            <el-tag size="small" :type="getStatusTagType(row.statusBefore)" effect="plain">
              {{ getStatusLabel(row.statusBefore) }}
            </el-tag>
            <el-icon class="arrow"><Right /></el-icon>
            <el-tag size="small" :type="getStatusTagType(row.statusAfter)">
              {{ getStatusLabel(row.statusAfter) }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="当前状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.currentStatus)">
            {{ getStatusLabel(row.currentStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="handler" label="处理人" width="100" />
      <el-table-column prop="maintenanceTime" label="保养时间" width="160">
        <template #default="{ row }">{{ formatTime(row.maintenanceTime) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      v-model:current-page="pagination.pageNum"
      v-model:page-size="pagination.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadData"
      @current-change="loadData"
    />

    <!-- 保养登记弹窗 -->
    <el-dialog v-model="registerVisible" title="垫板保养登记" width="600px" destroy-on-close>
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-width="100px"
      >
        <el-form-item label="选择垫板" prop="padId">
          <el-select
            v-model="registerForm.padId"
            filterable
            placeholder="请选择需要保养的垫板"
            style="width: 100%"
            :loading="padLoading"
            @change="handlePadChange"
          >
            <el-option
              v-for="pad in allPads"
              :key="pad.id"
              :label="padOptionLabel(pad)"
              :value="pad.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedPad" label="当前状态">
          <el-tag :type="getStatusTagType(selectedPad.maintenanceStatus)">
            {{ getStatusLabel(selectedPad.maintenanceStatus) }}
          </el-tag>
          <span v-if="selectedPad.borrowStatus === 'BORROWED'" class="form-tip warning">
            该垫板领用离架中，仍可登记保养并更新状态
          </span>
          <span v-else-if="willAutoOffShelf" class="form-tip warning">
            当前在架 {{ selectedPad.shelfLayerCode }}，登记为{{ getStatusLabel(registerForm.statusAfter) }}后将自动离架
          </span>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="保养类型" prop="maintenanceType">
              <el-select
                v-model="registerForm.maintenanceType"
                placeholder="请选择保养类型"
                style="width: 100%"
                allow-create
                filterable
              >
                <el-option label="日常保养" value="日常保养" />
                <el-option label="定期保养" value="定期保养" />
                <el-option label="故障维修" value="故障维修" />
                <el-option label="送检" value="送检" />
                <el-option label="清洁润滑" value="清洁润滑" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="处理人" prop="handler">
              <el-input v-model="registerForm.handler" placeholder="请输入处理人姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="保养时间" prop="maintenanceTime">
              <el-date-picker
                v-model="registerForm.maintenanceTime"
                type="datetime"
                placeholder="默认当前时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保养结果" prop="maintenanceResult">
              <el-select v-model="registerForm.maintenanceResult" placeholder="请选择结果" style="width: 100%">
                <el-option label="正常" value="NORMAL" />
                <el-option label="已修复" value="REPAIRED" />
                <el-option label="异常待处理" value="ABNORMAL" />
                <el-option label="报废建议" value="SCRAPPED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态标记" prop="statusAfter">
          <el-radio-group v-model="registerForm.statusAfter">
            <el-radio value="AVAILABLE">可用</el-radio>
            <el-radio value="PENDING">待检</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
          <div class="form-tip">待检/停用垫板将被禁止领用，归还时也无法选择为目标；在架垫板登记后自动离架，不再占用层位配额</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="registerForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入保养情况备注（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRegister">
          确认登记
        </el-button>
      </template>
    </el-dialog>

    <!-- 保养详情弹窗：最近一次保养 + 状态变更记录 -->
    <el-dialog v-model="detailVisible" title="垫板保养详情" width="820px" destroy-on-close>
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail?.pad" :column="3" border size="small" class="detail-desc">
          <el-descriptions-item label="垫板编号">{{ detail.pad.padCode }}</el-descriptions-item>
          <el-descriptions-item label="适配模具">{{ detail.pad.moldType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusTagType(detail.pad.maintenanceStatus)">
              {{ getStatusLabel(detail.pad.maintenanceStatus) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-section-title">最近一次保养</div>
        <el-table v-if="detail?.latestRecord" :data="[detail.latestRecord]" border size="small">
          <el-table-column prop="maintenanceType" label="类型" width="90" />
          <el-table-column label="结果" width="100">
            <template #default="{ row }">
              <el-tag :type="getResultTagType(row.maintenanceResult)" size="small">
                {{ getResultLabel(row.maintenanceResult) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="handler" label="处理人" width="90" />
          <el-table-column prop="maintenanceTime" label="时间" width="160">
            <template #default="{ row }">{{ formatTime(row.maintenanceTime) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无保养记录" :image-size="60" />

        <div class="detail-section-title">状态变更记录</div>
        <el-timeline v-if="detail && detail.statusChangeRecords.length">
          <el-timeline-item
            v-for="item in detail.statusChangeRecords"
            :key="item.id"
            :type="getTimelineType(item.statusAfter)"
            :timestamp="formatTime(item.maintenanceTime) + ' · ' + item.handler"
          >
            <div class="timeline-body">
              <el-tag size="small" :type="getStatusTagType(item.statusBefore)" effect="plain">
                {{ getStatusLabel(item.statusBefore) }}
              </el-tag>
              <el-icon class="arrow"><Right /></el-icon>
              <el-tag size="small" :type="getStatusTagType(item.statusAfter)">
                {{ getStatusLabel(item.statusAfter) }}
              </el-tag>
              <span class="timeline-type">{{ item.maintenanceType }}</span>
              <span v-if="item.remark" class="timeline-remark">（{{ item.remark }}）</span>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无状态变更记录" :image-size="60" />

        <div class="detail-section-title">全部保养记录</div>
        <el-table v-if="detail && detail.maintenanceRecords.length" :data="detail.maintenanceRecords" border size="small" max-height="260">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="maintenanceType" label="保养类型" width="90" />
          <el-table-column label="结果" width="100">
            <template #default="{ row }">
              <el-tag :type="getResultTagType(row.maintenanceResult)" size="small">
                {{ getResultLabel(row.maintenanceResult) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="handler" label="处理人" width="90" />
          <el-table-column prop="maintenanceTime" label="保养时间" width="160">
            <template #default="{ row }">{{ formatTime(row.maintenanceTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="150">
            <template #default="{ row }">
              {{ getStatusLabel(row.statusBefore) }} → {{ getStatusLabel(row.statusAfter) }}
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="registerFromDetail">再登记一次</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import {
  getMaintenancePage,
  getMaintenanceStatistics,
  getMaintenanceDetail,
  registerMaintenance
} from '@/api/maintenance'
import { getPadPage } from '@/api/pad'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const dateRange = ref([])
const route = useRoute()
const stats = reactive({ availableCount: 0, pendingCount: 0, disabledCount: 0 })

const searchForm = reactive({ status: '', padCode: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

const STATUS_LABELS = { AVAILABLE: '可用', PENDING: '待检', DISABLED: '停用' }
const RESULT_LABELS = { NORMAL: '正常', REPAIRED: '已修复', ABNORMAL: '异常待处理', SCRAPPED: '报废建议' }
const getStatusLabel = (status) => STATUS_LABELS[status] || '可用'
const getResultLabel = (result) => RESULT_LABELS[result] || result
const getStatusTagType = (status) => {
  if (status === 'AVAILABLE') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'DISABLED') return 'danger'
  return 'info'
}
const getResultTagType = (result) => {
  if (result === 'NORMAL' || result === 'REPAIRED') return 'success'
  if (result === 'ABNORMAL') return 'warning'
  if (result === 'SCRAPPED') return 'danger'
  return 'info'
}
const getTimelineType = (status) => {
  if (status === 'AVAILABLE') return 'success'
  if (status === 'PENDING') return 'warning'
  return 'danger'
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
    const data = await getMaintenancePage(buildQuery())
    tableData.value = data.records || []
    pagination.total = data.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    Object.assign(stats, await getMaintenanceStatistics())
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { status: '', padCode: '' })
  dateRange.value = []
  handleSearch()
}

const quickFilter = (status) => {
  searchForm.status = status
  handleSearch()
}

// ---------------- 登记保养 ----------------
const registerVisible = ref(false)
const registerFormRef = ref(null)
const padLoading = ref(false)
const allPads = ref([])
const registerForm = reactive({
  padId: null,
  maintenanceType: '',
  handler: '',
  maintenanceTime: '',
  maintenanceResult: 'NORMAL',
  statusAfter: 'AVAILABLE',
  remark: ''
})
const registerRules = {
  padId: [{ required: true, message: '请选择垫板', trigger: 'change' }],
  maintenanceType: [{ required: true, message: '请选择保养类型', trigger: 'change' }],
  handler: [{ required: true, message: '请输入处理人', trigger: 'blur' }],
  maintenanceResult: [{ required: true, message: '请选择保养结果', trigger: 'change' }],
  statusAfter: [{ required: true, message: '请标记保养后状态', trigger: 'change' }]
}

const selectedPad = computed(() => allPads.value.find((p) => p.id === registerForm.padId))

// 在架垫板登记为待检/停用时后端会自动离架，提前在表单中提示
const willAutoOffShelf = computed(() => {
  const pad = selectedPad.value
  if (!pad || !pad.shelfLayerCode) return false
  return registerForm.statusAfter === 'PENDING' || registerForm.statusAfter === 'DISABLED'
})

const padOptionLabel = (pad) => {
  const status = getStatusLabel(pad.maintenanceStatus)
  return `${pad.padCode}（${pad.moldType || '无模具'} / ${status}）`
}

const loadAllPads = async () => {
  padLoading.value = true
  try {
    const data = await getPadPage({ pageNum: 1, pageSize: 10000 })
    allPads.value = data.records || []
  } catch (e) {
    console.error(e)
  } finally {
    padLoading.value = false
  }
}

const resetRegisterForm = (padId = null) => {
  Object.assign(registerForm, {
    padId,
    maintenanceType: '',
    handler: '',
    maintenanceTime: '',
    maintenanceResult: 'NORMAL',
    statusAfter: 'AVAILABLE',
    remark: ''
  })
}

const openRegister = async () => {
  resetRegisterForm()
  registerVisible.value = true
  await loadAllPads()
}

// 登记时选择垫板：待检/停用默认恢复为可用，可用默认保持，送检类默认待检
const handlePadChange = (padId) => {
  const pad = allPads.value.find((p) => p.id === padId)
  if (!pad) return
  registerForm.statusAfter = pad.maintenanceStatus === 'DISABLED' || pad.maintenanceStatus === 'PENDING'
    ? 'AVAILABLE'
    : (pad.maintenanceStatus || 'AVAILABLE')
}

const submitRegister = async () => {
  await registerFormRef.value?.validate()
  submitting.value = true
  const autoOffShelf = willAutoOffShelf.value
  try {
    await registerMaintenance({
      padId: registerForm.padId,
      maintenanceType: registerForm.maintenanceType,
      handler: registerForm.handler,
      maintenanceTime: registerForm.maintenanceTime || null,
      maintenanceResult: registerForm.maintenanceResult,
      statusAfter: registerForm.statusAfter,
      remark: registerForm.remark || null
    })
    ElMessage.success(autoOffShelf ? '保养登记成功，垫板已自动离架' : '保养登记成功，垫板状态已更新')
    registerVisible.value = false
    loadData()
    loadStats()
    if (detailVisible.value) loadDetail(currentDetailPadId.value)
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

// ---------------- 详情 ----------------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const currentDetailPadId = ref(null)

const openDetail = async (row) => {
  detailVisible.value = true
  await loadDetail(row.padId)
}

const loadDetail = async (padId) => {
  detailLoading.value = true
  currentDetailPadId.value = padId
  try {
    detail.value = await getMaintenanceDetail(padId)
  } catch (e) {
    console.error(e)
  } finally {
    detailLoading.value = false
  }
}

const registerFromDetail = async () => {
  const padId = currentDetailPadId.value
  detailVisible.value = false
  resetRegisterForm(padId)
  registerVisible.value = true
  await loadAllPads()
  handlePadChange(padId)
}

onMounted(async () => {
  loadData()
  loadStats()
  // 从垫板档案“保养”入口跳转：按编号预筛选并直接打开登记弹窗
  if (route.query.padCode) {
    searchForm.padCode = String(route.query.padCode)
    handleSearch()
  }
  if (route.query.padId) {
    registerVisible.value = true
    await loadAllPads()
    resetRegisterForm(Number(route.query.padId))
    handlePadChange(Number(route.query.padId))
  }
})
</script>

<style lang="scss" scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 10px;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-2px);
  }

  &.card-available {
    background: linear-gradient(135deg, #11998e 0%, #38b87a 100%);
  }
  &.card-pending {
    background: linear-gradient(135deg, #f6a84c 0%, #f5851f 100%);
  }
  &.card-disabled {
    background: linear-gradient(135deg, #6b7280 0%, #4b5563 100%);
  }

  .stat-label {
    font-size: 13px;
    opacity: 0.92;
  }
  .stat-value {
    font-size: 26px;
    font-weight: 700;
    line-height: 1.2;
  }
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.status-change {
  display: flex;
  align-items: center;
  gap: 6px;

  .arrow {
    color: #909399;
  }
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;

  &.warning {
    color: #e6a23c;
  }
}

.detail-desc {
  margin-bottom: 12px;
}

.detail-section-title {
  font-weight: 600;
  font-size: 14px;
  color: #1e3a8a;
  margin: 16px 0 8px;
  padding-left: 8px;
  border-left: 3px solid #2563eb;
}

.timeline-body {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;

  .arrow {
    color: #909399;
  }

  .timeline-type {
    margin-left: 6px;
    font-weight: 600;
    color: #303133;
  }

  .timeline-remark {
    color: #909399;
    font-size: 12px;
  }
}
</style>
