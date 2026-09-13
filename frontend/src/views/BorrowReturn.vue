<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">垫板领用归还</h2>
      <div>
        <el-button type="success" @click="handleExport">
          <el-icon><Download /></el-icon>导出台账
        </el-button>
        <el-button type="primary" @click="openCheckout">
          <el-icon><Promotion /></el-icon>登记领用
        </el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stat-row">
      <el-col :span="8">
        <div class="stat-card card-borrowed" @click="quickFilter('BORROWED')">
          <div class="stat-icon"><el-icon :size="30"><Van /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">领用中（当前占用）</div>
            <div class="stat-value">{{ stats.borrowedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card card-overdue" @click="quickFilter('OVERDUE')">
          <div class="stat-icon"><el-icon :size="30"><AlarmClock /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">逾期未还</div>
            <div class="stat-value">{{ stats.overdueCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card card-returned" @click="quickFilter('RETURNED')">
          <div class="stat-icon"><el-icon :size="30"><CircleCheck /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">已归还</div>
            <div class="stat-value">{{ stats.returnedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" @submit.prevent="handleSearch">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="领用中" value="BORROWED" />
            <el-option label="已逾期" value="OVERDUE" />
            <el-option label="已归还" value="RETURNED" />
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
        <el-form-item label="领用人">
          <el-input
            v-model="searchForm.borrower"
            placeholder="请输入领用人"
            clearable
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="领用日期">
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
      <el-table-column label="保养状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getMaintenanceTagType(row.maintenanceStatus)" size="small" effect="light">
            {{ getMaintenanceLabel(row.maintenanceStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'RETURNED'" type="success" effect="light">已归还</el-tag>
          <el-tag v-else-if="row.overdue" type="danger" effect="dark">
            <el-icon style="margin-right: 2px"><AlarmClock /></el-icon>领用中·已逾期
          </el-tag>
          <el-tag v-else type="warning" effect="light">领用中</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="borrower" label="领用人" width="100" />
      <el-table-column prop="productionLine" label="产线/工位" width="140" show-overflow-tooltip />
      <el-table-column prop="purpose" label="用途" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.purpose || '-' }}</template>
      </el-table-column>
      <el-table-column label="层位变化" width="230">
        <template #default="{ row }">
          <div class="layer-change">
            <el-tag size="small" type="info" effect="plain">{{ row.originLayerCode || '未在架' }}</el-tag>
            <el-icon class="arrow"><Right /></el-icon>
            <el-tag v-if="row.returnLayerCode" size="small" type="success" effect="plain">
              {{ row.returnLayerCode }}
            </el-tag>
            <el-tag v-else size="small" type="danger" effect="plain">离架中</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="领用时间" width="160">
        <template #default="{ row }">{{ formatTime(row.checkoutTime) }}</template>
      </el-table-column>
      <el-table-column label="预计归还" width="160">
        <template #default="{ row }">
          <span :class="{ 'overdue-text': row.status === 'BORROWED' && row.overdue }">
            {{ row.expectedReturnTime ? formatTime(row.expectedReturnTime) : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="归还时间" width="160">
        <template #default="{ row }">
          {{ row.returnTime ? formatTime(row.returnTime) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'BORROWED' && isPadAvailable(row)"
            link
            type="primary"
            size="small"
            @click="openReturn(row)"
          >
            归还
          </el-button>
          <el-tooltip
            v-else-if="row.status === 'BORROWED' && row.maintenanceStatus === 'SCRAPPED'"
            content="垫板已报废出库，禁止归还回架"
            placement="top"
          >
            <el-button link type="primary" size="small" disabled>归还</el-button>
          </el-tooltip>
          <el-tooltip
            v-else-if="row.status === 'BORROWED'"
            content="待检/停用垫板不可作为归还目标，请先在保养台账恢复为可用"
            placement="top"
          >
            <el-button link type="primary" size="small" disabled>归还</el-button>
          </el-tooltip>
          <el-button v-else link type="info" size="small" disabled>已归还</el-button>
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

    <!-- 领用登记弹窗：从垫板档案中选择在架垫板 -->
    <el-dialog v-model="checkoutVisible" title="垫板领用登记" width="600px" destroy-on-close>
      <el-form
        ref="checkoutFormRef"
        :model="checkoutForm"
        :rules="checkoutRules"
        label-width="100px"
      >
        <el-form-item label="选择垫板" prop="padId">
          <el-select
            v-model="checkoutForm.padId"
            filterable
            placeholder="请选择垫板（仅在架垫板可领用）"
            style="width: 100%"
            :loading="padLoading"
          >
            <el-option
              v-for="pad in allPads"
              :key="pad.id"
              :label="padOptionLabel(pad)"
              :value="pad.id"
              :disabled="!isPadCheckoutable(pad)"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedPad" label="当前层位">
          <el-tag type="success">{{ selectedPad.shelfLayerCode }}</el-tag>
          <span class="form-tip">领用后该垫板将自动从该层位标记离架</span>
        </el-form-item>
        <el-form-item label="领用人" prop="borrower">
          <el-input v-model="checkoutForm.borrower" placeholder="请输入领用人姓名" />
        </el-form-item>
        <el-form-item label="产线/工位" prop="productionLine">
          <el-input v-model="checkoutForm.productionLine" placeholder="如：冲压一线-3号工位" />
        </el-form-item>
        <el-form-item label="用途" prop="purpose">
          <el-input
            v-model="checkoutForm.purpose"
            type="textarea"
            :rows="2"
            placeholder="请输入领用用途"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="领用时间" prop="checkoutTime">
              <el-date-picker
                v-model="checkoutForm.checkoutTime"
                type="datetime"
                placeholder="默认当前时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预计归还">
              <el-date-picker
                v-model="checkoutForm.expectedReturnTime"
                type="datetime"
                placeholder="用于逾期提醒（可选）"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="checkoutVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCheckout">
          确认领用离架
        </el-button>
      </template>
    </el-dialog>

    <!-- 归还登记弹窗：必须选择未被占用的可用层位 -->
    <el-dialog v-model="returnVisible" title="垫板归还登记" width="560px" destroy-on-close>
      <el-form
        ref="returnFormRef"
        :model="returnForm"
        :rules="returnRules"
        label-width="100px"
      >
        <el-form-item label="垫板编号">
          <el-input :model-value="currentRecord?.padCode || ''" disabled />
        </el-form-item>
        <el-form-item label="保养状态">
          <el-tag :type="getMaintenanceTagType(currentRecord?.maintenanceStatus)" size="small">
            {{ getMaintenanceLabel(currentRecord?.maintenanceStatus) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="领用人">
          <el-input :model-value="currentRecord?.borrower || ''" disabled />
        </el-form-item>
        <el-form-item label="产线/工位">
          <el-input :model-value="currentRecord?.productionLine || ''" disabled />
        </el-form-item>
        <el-form-item label="原在架层位">
          <el-tag type="info" effect="plain">{{ currentRecord?.originLayerCode || '-' }}</el-tag>
        </el-form-item>
        <el-form-item label="归还层位" prop="returnLayerCode">
          <el-alert
            v-if="unbalancedLayers.length > 0"
            class="unbalanced-tip"
            type="error"
            :closable="false"
            show-icon
            title="以下层位盘点差异未闭环，未闭环前禁止归还上架："
          >
            <div v-for="layer in unbalancedLayers" :key="layer.layerCode" class="unbalanced-line">
              【{{ layer.layerCode }}】{{ layer.activeUnbalanced.diffReason }}
              （单号 {{ layer.activeUnbalanced.sheetNo }}）
            </div>
          </el-alert>
          <el-select
            v-model="returnForm.returnLayerCode"
            placeholder="请选择可用层位（已占用/已满/封锁/未平账层位不可选）"
            style="width: 100%"
          >
            <el-option
              v-for="layer in returnLayerOptions"
              :key="layer.layerCode"
              :label="returnLayerLabel(layer)"
              :value="layer.layerCode"
              :disabled="layer.padCount > 0 || isLayerFull(layer) || isLayerBlocked(layer) || isLayerUnbalanced(layer)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="归还时间" prop="returnTime">
          <el-date-picker
            v-model="returnForm.returnTime"
            type="datetime"
            placeholder="默认当前时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="returnForm.remark"
            type="textarea"
            :rows="2"
            placeholder="归还备注（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReturn">
          确认归还上架
        </el-button>
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
  getBorrowPage,
  getBorrowStatistics,
  getAvailableReturnLayers,
  checkoutPad,
  returnPad,
  exportBorrowRecords
} from '@/api/borrow'
import { getPadPage } from '@/api/pad'
import { downloadBlob, getFileNameFromDisposition } from '@/utils/download'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const dateRange = ref([])
const stats = reactive({ borrowedCount: 0, returnedCount: 0, overdueCount: 0 })

const searchForm = reactive({ status: '', padCode: '', borrower: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

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
    const data = await getBorrowPage(buildQuery())
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
    Object.assign(stats, await getBorrowStatistics())
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { status: '', padCode: '', borrower: '' })
  dateRange.value = []
  handleSearch()
}

const quickFilter = (status) => {
  searchForm.status = status
  handleSearch()
}

const handleExport = async () => {
  try {
    const query = { ...searchForm }
    if (dateRange.value && dateRange.value.length === 2) {
      query.startTime = dateRange.value[0]
      query.endTime = dateRange.value[1]
    }
    const res = await exportBorrowRecords(query)
    const filename = getFileNameFromDisposition(res.headers['content-disposition'])
    downloadBlob(res.data, filename || '垫板领用归还台账.xlsx')
  } catch (e) {
    console.error(e)
  }
}

// ---------------- 领用 ----------------
const checkoutVisible = ref(false)
const checkoutFormRef = ref(null)
const padLoading = ref(false)
const allPads = ref([])

const MAINTENANCE_LABELS = { AVAILABLE: '可用', PENDING: '待检', DISABLED: '停用', SCRAPPED: '已报废' }
const getMaintenanceLabel = (status) => MAINTENANCE_LABELS[status] || '可用'
const getMaintenanceTagType = (status) => {
  if (status === 'AVAILABLE' || !status) return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'SCRAPPED') return 'danger'
  return 'info'
}
const isPadAvailable = (pad) => !pad.maintenanceStatus || pad.maintenanceStatus === 'AVAILABLE'

const isPadCheckoutable = (pad) =>
  !!(pad.shelfLayerCode && pad.borrowStatus !== 'BORROWED' && isPadAvailable(pad) && !pad.reserveId)
const padOptionLabel = (pad) => {
  if (pad.maintenanceStatus === 'SCRAPPED') {
    return `${pad.padCode}（已报废出库，不可领用）`
  }
  if (!isPadAvailable(pad)) {
    return `${pad.padCode}（${getMaintenanceLabel(pad.maintenanceStatus)}，不可领用）`
  }
  if (!pad.shelfLayerCode) return `${pad.padCode}（未绑定层位，不可领用）`
  if (pad.borrowStatus === 'BORROWED') return `${pad.padCode}（领用中，不可重复领用）`
  if (pad.reserveId) return `${pad.padCode}（已预留给模具 ${pad.reserveMoldCode || ''}，预留期内不可领用）`
  return `${pad.padCode}（${pad.moldType || '无模具'} / 层位：${pad.shelfLayerCode}）`
}
const selectedPad = computed(() =>
  allPads.value.find((p) => p.id === checkoutForm.padId)
)
const checkoutForm = reactive({
  padId: null,
  borrower: '',
  productionLine: '',
  purpose: '',
  checkoutTime: '',
  expectedReturnTime: ''
})
const checkoutRules = {
  padId: [{ required: true, message: '请选择要领用的垫板', trigger: 'change' }],
  borrower: [{ required: true, message: '请输入领用人', trigger: 'blur' }],
  productionLine: [{ required: true, message: '请输入产线/工位', trigger: 'blur' }]
}

const loadAvailablePads = async () => {
  padLoading.value = true
  try {
    // 拉取全部档案垫板：在架可领用，未绑定/领用中的禁用并标注原因
    const data = await getPadPage({ pageNum: 1, pageSize: 10000 })
    allPads.value = data.records || []
  } catch (e) {
    console.error(e)
  } finally {
    padLoading.value = false
  }
}

const openCheckout = async () => {
  Object.assign(checkoutForm, {
    padId: route.query.padId ? Number(route.query.padId) : null,
    borrower: '',
    productionLine: '',
    purpose: '',
    checkoutTime: '',
    expectedReturnTime: ''
  })
  checkoutVisible.value = true
  await loadAvailablePads()
  const target = allPads.value.find((p) => p.id === Number(route.query.padId))
  if (route.query.padId && target && !isPadCheckoutable(target)) {
    if (!isPadAvailable(target)) {
      ElMessage.warning(`该垫板当前为【${getMaintenanceLabel(target.maintenanceStatus)}】状态，不可领用`)
    } else if (target.borrowStatus === 'BORROWED') {
      ElMessage.warning('该垫板已领用且未归还，禁止重复领用')
    } else if (target.reserveId) {
      ElMessage.warning(`该垫板已预留给模具【${target.reserveMoldCode || ''}】，预留期内不可领用`)
    } else {
      ElMessage.warning('该垫板当前未在架，无法领用，请先绑定层位')
    }
    checkoutForm.padId = null
  }
}

const submitCheckout = async () => {
  await checkoutFormRef.value?.validate()
  if (checkoutForm.expectedReturnTime && checkoutForm.checkoutTime
      && dayjs(checkoutForm.expectedReturnTime).isBefore(dayjs(checkoutForm.checkoutTime))) {
    ElMessage.warning('预计归还时间不能早于领用时间')
    return
  }
  submitting.value = true
  try {
    await checkoutPad({
      padId: checkoutForm.padId,
      borrower: checkoutForm.borrower,
      productionLine: checkoutForm.productionLine,
      purpose: checkoutForm.purpose || null,
      checkoutTime: checkoutForm.checkoutTime || null,
      expectedReturnTime: checkoutForm.expectedReturnTime || null
    })
    ElMessage.success('领用成功，垫板已标记离架')
    checkoutVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

// ---------------- 归还 ----------------
const returnVisible = ref(false)
const returnFormRef = ref(null)
const currentRecord = ref(null)
const returnLayerOptions = ref([])
const returnForm = reactive({
  id: null,
  returnLayerCode: '',
  returnTime: '',
  remark: ''
})
const returnRules = {
  returnLayerCode: [{ required: true, message: '请选择归还层位', trigger: 'change' }]
}

// 归还目标：已占用、已达实际配额、封锁中或盘点未平账的层位不可选
// 实际配额：扩容期内取扩容后配额，到期/结束后回到原配额
const effCap = (layer) => layer.effectiveCapacity ?? layer.capacity ?? 0
const isLayerFull = (layer) => (layer.padCount || 0) >= effCap(layer)
const isLayerBlocked = (layer) => !!layer.activeBlock
const isLayerUnbalanced = (layer) => !!layer.activeUnbalanced
// 未平账层位清单：归还弹窗顶部展示未平账原因，与盘点页/层位页/概览同源
const unbalancedLayers = computed(() => returnLayerOptions.value.filter(isLayerUnbalanced))
const returnLayerLabel = (layer) => {
  const used = layer.padCount || 0
  const capacity = effCap(layer)
  const expandTag = layer.activeExpand ? '扩容中 ' : ''
  if (isLayerBlocked(layer)) return `${layer.layerCode} - ${layer.layerName}（封锁中，不可归还）`
  if (isLayerUnbalanced(layer)) {
    return `${layer.layerCode} - ${layer.layerName}（盘点未平账：${layer.activeUnbalanced.diffReason}，禁止归还）`
  }
  if (used > 0) return `${layer.layerCode} - ${layer.layerName}（${expandTag}已占用 ${used}/${capacity}）`
  if (isLayerFull(layer)) return `${layer.layerCode} - ${layer.layerName}（${expandTag}已满 ${used}/${capacity}）`
  return `${layer.layerCode} - ${layer.layerName}（${expandTag}空闲可用 ${used}/${capacity}）`
}

const openReturn = async (row) => {
  currentRecord.value = row
  Object.assign(returnForm, {
    id: row.id,
    returnLayerCode: '',
    returnTime: '',
    remark: ''
  })
  returnVisible.value = true
  try {
    returnLayerOptions.value = await getAvailableReturnLayers()
  } catch (e) {
    console.error(e)
  }
}

const submitReturn = async () => {
  await returnFormRef.value?.validate()
  const layer = returnLayerOptions.value.find((l) => l.layerCode === returnForm.returnLayerCode)
  if (layer && layer.padCount > 0) {
    ElMessage.warning('该层位已被占用，请选择其他可用层位')
    return
  }
  if (layer && isLayerFull(layer)) {
    ElMessage.warning('该层位已达容量配额，请选择其他可用层位')
    return
  }
  if (layer && isLayerBlocked(layer)) {
    ElMessage.warning('该层位处于封锁中，请选择其他可用层位')
    return
  }
  if (layer && isLayerUnbalanced(layer)) {
    ElMessage.warning(`该层位盘点差异未闭环（${layer.activeUnbalanced.diffReason}），未闭环前禁止归还上架`)
    return
  }
  submitting.value = true
  try {
    await returnPad({
      id: returnForm.id,
      returnLayerCode: returnForm.returnLayerCode,
      returnTime: returnForm.returnTime || null,
      remark: returnForm.remark || null
    })
    ElMessage.success('归还成功，垫板已恢复层位绑定')
    returnVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (route.query.overdue) {
    searchForm.status = 'OVERDUE'
  }
  loadData()
  loadStats()
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

  &.card-borrowed {
    background: linear-gradient(135deg, #f6a84c 0%, #f5851f 100%);
  }
  &.card-overdue {
    background: linear-gradient(135deg, #f5576c 0%, #d9363e 100%);
  }
  &.card-returned {
    background: linear-gradient(135deg, #11998e 0%, #38b87a 100%);
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

.layer-change {
  display: flex;
  align-items: center;
  gap: 6px;

  .arrow {
    color: #909399;
  }
}

.overdue-text {
  color: #f56c6c;
  font-weight: 600;
}

.unbalanced-tip {
  margin-bottom: 10px;

  .unbalanced-line {
    font-size: 12px;
    line-height: 1.6;
  }
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
