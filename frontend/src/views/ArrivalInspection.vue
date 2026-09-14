<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">到货待检管理</h2>
      <el-button type="primary" @click="openRegisterDialog()">
        <el-icon><Plus /></el-icon>登记到货
      </el-button>
    </div>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="4">
        <div class="stat-card card-pending" @click="quickFilter('PENDING')">
          <div class="stat-icon"><el-icon :size="26"><Clock /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">待检中批次</div>
            <div class="stat-value">{{ stats.pendingCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card card-quarantine" @click="quickFilter('PENDING')">
          <div class="stat-icon"><el-icon :size="26"><Goods /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">待检占用(块)</div>
            <div class="stat-value">{{ stats.pendingPadCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card card-passed" @click="quickFilter('PASSED')">
          <div class="stat-icon"><el-icon :size="26"><CircleCheck /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">质检通过</div>
            <div class="stat-value">{{ stats.passedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card card-rejected" @click="quickFilter('REJECTED')">
          <div class="stat-icon"><el-icon :size="26"><CircleClose /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">判退离库</div>
            <div class="stat-value">{{ stats.rejectedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card card-month" @click="quickFilterMonth">
          <div class="stat-icon"><el-icon :size="26"><Calendar /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">本月到货</div>
            <div class="stat-value">{{ stats.monthArrivalCount || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" @submit.prevent="handleSearch">
        <el-form-item label="批次状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="待检中" value="PENDING" />
            <el-option label="质检通过" value="PASSED" />
            <el-option label="判退离库" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次号">
          <el-input
            v-model="searchForm.batchNo"
            placeholder="请输入到货批次号"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="垫板编号">
          <el-input
            v-model="searchForm.padCode"
            placeholder="请输入垫板编号"
            clearable
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="经办人">
          <el-input
            v-model="searchForm.operator"
            placeholder="请输入经办人"
            clearable
            style="width: 130px"
          />
        </el-form-item>
        <el-form-item label="到货日期">
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

    <el-table
      :data="tableData"
      v-loading="loading"
      border
      stripe
      row-key="id"
      :expand-row-keys="expandedRowKeys"
      @expand-change="handleExpandChange"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="pad-list-panel">
            <div class="pad-list-title">
              批内垫板清单（{{ (row.items || []).length }} 块）
              <span class="pad-list-tip">目标层位为质检通过时登记，括号内为垫板当前状态</span>
            </div>
            <el-table :data="row.items || []" border size="small">
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column prop="padCode" label="垫板编号" width="150" />
              <el-table-column prop="moldType" label="适配模具" min-width="140" show-overflow-tooltip>
                <template #default="{ row: item }">{{ item.moldType || '-' }}</template>
              </el-table-column>
              <el-table-column label="转正式层位" width="130">
                <template #default="{ row: item }">
                  <el-tag v-if="item.targetLayerCode" size="small" type="success" effect="plain">
                    {{ item.targetLayerCode }}
                  </el-tag>
                  <span v-else style="color: #909399">-</span>
                </template>
              </el-table-column>
              <el-table-column label="当前状态" width="220">
                <template #default="{ row: item }">
                  <el-tag size="small" :type="getStockTagType(item.currentStockStatus)">
                    {{ getStockLabel(item.currentStockStatus) }}
                  </el-tag>
                  <el-tag
                    v-if="item.currentLayerCode"
                    size="small"
                    type="info"
                    effect="plain"
                    style="margin-left: 6px"
                  >在架 {{ item.currentLayerCode }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="batchNo" label="到货批次号" width="190">
        <template #default="{ row }">
          <el-tag type="primary" effect="plain">{{ row.batchNo }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="supplier" label="供应商/来源" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.supplier || '-' }}</template>
      </el-table-column>
      <el-table-column label="到货时间" width="165">
        <template #default="{ row }">{{ formatTime(row.arrivalTime) }}</template>
      </el-table-column>
      <el-table-column prop="operator" label="经办人" width="90" />
      <el-table-column label="批内垫板" width="90" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="toggleExpand(row)">
            {{ row.padCount ?? (row.items || []).length }} 块
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.status)" :effect="row.status === 'PENDING' ? 'dark' : 'light'">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质检时间" width="165">
        <template #default="{ row }">{{ row.inspectTime ? formatTime(row.inspectTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="质检人" width="90">
        <template #default="{ row }">{{ row.inspector || '-' }}</template>
      </el-table-column>
      <el-table-column label="质检结论" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.inspectConclusion || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="success" size="small" @click="openPassDialog(row)">
              质检通过
            </el-button>
            <el-button link type="danger" size="small" @click="openRejectDialog(row)">
              判退离库
            </el-button>
          </template>
          <el-button v-else link type="info" size="small" disabled>
            {{ row.status === 'PASSED' ? '已转正式层' : '已判退离库' }}
          </el-button>
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

    <!-- 登记到货弹窗：到货批次、到货时间、经办人与批内垫板清单 -->
    <el-dialog v-model="registerVisible" title="到货登记（新到垫板先落待检层）" width="860px" destroy-on-close>
      <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="到货时间" prop="arrivalTime">
              <el-date-picker
                v-model="registerForm.arrivalTime"
                type="datetime"
                placeholder="默认当前时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经办人" prop="operator">
              <el-input v-model="registerForm.operator" placeholder="请输入经办人姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input v-model="registerForm.supplier" placeholder="供应商/来源（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="registerForm.remark" placeholder="批次备注（可选）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="到货垫板">
          <div class="item-editor">
            <div class="item-editor-head">
              <span>逐块登记编号与规格，至少一块；待检层垫板不可领用/换绑，不计可用库存</span>
              <el-button type="primary" size="small" plain @click="addItemRow">
                <el-icon><Plus /></el-icon>添加垫板
              </el-button>
            </div>
            <el-table :data="registerForm.items" border size="small">
              <el-table-column type="index" label="序号" width="56" align="center" />
              <el-table-column label="垫板编号" width="170">
                <template #default="{ row }">
                  <el-input v-model="row.padCode" placeholder="必填，不可重复" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="适配模具" min-width="140">
                <template #default="{ row }">
                  <el-input v-model="row.moldType" placeholder="可选" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="长(mm)" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.length" :min="0" :precision="2" size="small"
                    controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="宽(mm)" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.width" :min="0" :precision="2" size="small"
                    controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="厚(mm)" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.thickness" :min="0" :precision="2" size="small"
                    controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ $index }">
                  <el-button
                    link
                    type="danger"
                    size="small"
                    :disabled="registerForm.items.length <= 1"
                    @click="removeItemRow($index)"
                  >删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRegister">
          确认到货（落待检层）
        </el-button>
      </template>
    </el-dialog>

    <!-- 质检通过弹窗：整批转正式层，逐块指定目标层位 -->
    <el-dialog v-model="passVisible" title="质检通过 - 整批转正式层" width="760px" destroy-on-close>
      <el-form ref="passFormRef" :model="passForm" :rules="passRules" label-width="100px">
        <el-form-item label="到货批次">
          <el-tag type="primary" effect="plain">{{ currentBatch?.batchNo }}</el-tag>
          <span class="form-tip">共 {{ (currentBatch?.items || []).length }} 块，逐块指定正式层位后整批上架</span>
        </el-form-item>
        <el-form-item label="统一层位">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-select
              v-model="quickLayerCode"
              placeholder="选择层位后一键应用到全部垫板"
              clearable
              style="flex: 1"
            >
              <el-option
                v-for="layer in layerOptions"
                :key="layer.layerCode"
                :label="layerOptionLabel(layer)"
                :value="layer.layerCode"
                :disabled="isLayerFull(layer) || isLayerBlocked(layer)"
              />
            </el-select>
            <el-button size="default" :disabled="!quickLayerCode" @click="applyQuickLayer">
              应用到全部
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="逐块层位">
          <el-table :data="passTargets" border size="small" max-height="300">
            <el-table-column type="index" label="序号" width="56" align="center" />
            <el-table-column prop="padCode" label="垫板编号" width="150" />
            <el-table-column label="转正式层位" min-width="240">
              <template #default="{ row }">
                <el-select
                  v-model="row.layerCode"
                  placeholder="请选择正式层位"
                  size="small"
                  style="width: 100%"
                >
                  <el-option
                    v-for="layer in layerOptions"
                    :key="layer.layerCode"
                    :label="layerOptionLabel(layer)"
                    :value="layer.layerCode"
                    :disabled="isLayerFull(layer) || isLayerBlocked(layer)"
                  />
                </el-select>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <el-form-item label="质检结论" prop="inspectConclusion">
          <el-input
            v-model="passForm.inspectConclusion"
            type="textarea"
            :rows="2"
            placeholder="必填：如外观尺寸抽检合格，同意入库"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="质检时间">
              <el-date-picker
                v-model="passForm.inspectTime"
                type="datetime"
                placeholder="默认当前时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质检人">
              <el-input v-model="passForm.inspector" placeholder="缺省取登记经办人" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="passVisible = false">取消</el-button>
        <el-button type="success" :loading="submitting" @click="submitPass">
          确认通过（整批转正式层）
        </el-button>
      </template>
    </el-dialog>

    <!-- 判退离库弹窗：整批离库，判退结论必填 -->
    <el-dialog v-model="rejectVisible" title="判退离库" width="540px" destroy-on-close>
      <el-alert
        type="error"
        :closable="false"
        show-icon
        title="判退后整批离库，批内垫板档案冻结，不可再领用/上架/保养，且不可撤销"
        style="margin-bottom: 16px"
      />
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="100px">
        <el-form-item label="到货批次">
          <el-tag type="primary" effect="plain">{{ currentBatch?.batchNo }}</el-tag>
          <span class="form-tip">共 {{ (currentBatch?.items || []).length }} 块</span>
        </el-form-item>
        <el-form-item label="判退结论" prop="inspectConclusion">
          <el-input
            v-model="rejectForm.inspectConclusion"
            type="textarea"
            :rows="3"
            placeholder="必填：如尺寸超差/外观不良，整批退回供应商"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="质检时间">
              <el-date-picker
                v-model="rejectForm.inspectTime"
                type="datetime"
                placeholder="默认当前时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质检人">
              <el-input v-model="rejectForm.inspector" placeholder="缺省取登记经办人" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitReject">
          确认判退离库
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import {
  getArrivalPage,
  getArrivalStatistics,
  registerArrival,
  passArrival,
  rejectArrival
} from '@/api/arrival'
import { getShelfLayerList } from '@/api/shelf'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const dateRange = ref([])
const expandedRowKeys = ref([])
const stats = reactive({
  pendingCount: 0,
  pendingPadCount: 0,
  passedCount: 0,
  rejectedCount: 0,
  monthArrivalCount: 0
})

const searchForm = reactive({ status: '', batchNo: '', padCode: '', operator: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

const STATUS_LABELS = { PENDING: '待检中', PASSED: '质检通过', REJECTED: '判退离库' }
const getStatusLabel = (status) => STATUS_LABELS[status] || status
const getStatusTagType = (status) => {
  const map = { PENDING: 'warning', PASSED: 'success', REJECTED: 'danger' }
  return map[status] || 'info'
}

const STOCK_LABELS = { QUARANTINE: '到货待检', OFFICIAL: '正式在库', REJECTED: '判退离库' }
const getStockLabel = (status) => STOCK_LABELS[status] || status || '-'
const getStockTagType = (status) => {
  const map = { QUARANTINE: 'warning', OFFICIAL: 'success', REJECTED: 'danger' }
  return map[status] || 'info'
}

const buildQuery = () => {
  const query = { ...searchForm, ...pagination }
  ;['batchNo', 'padCode', 'operator'].forEach((key) => {
    if (typeof query[key] === 'string') {
      query[key] = query[key].trim()
      if (!query[key]) delete query[key]
    }
  })
  if (!query.status) delete query.status
  if (dateRange.value && dateRange.value.length === 2) {
    query.startTime = dateRange.value[0]
    query.endTime = dateRange.value[1]
  }
  return query
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getArrivalPage(buildQuery())
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
    Object.assign(stats, await getArrivalStatistics())
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { status: '', batchNo: '', padCode: '', operator: '' })
  dateRange.value = []
  handleSearch()
}

const quickFilter = (status) => {
  searchForm.status = status
  handleSearch()
}

const quickFilterMonth = () => {
  dateRange.value = [
    dayjs().startOf('month').format('YYYY-MM-DD HH:mm:ss'),
    dayjs().endOf('month').format('YYYY-MM-DD HH:mm:ss')
  ]
  searchForm.status = ''
  handleSearch()
}

const toggleExpand = (row) => {
  const idx = expandedRowKeys.value.indexOf(row.id)
  if (idx >= 0) {
    expandedRowKeys.value.splice(idx, 1)
  } else {
    expandedRowKeys.value.push(row.id)
  }
}

const handleExpandChange = (row, expanded) => {
  const opened = Array.isArray(expanded) ? expanded.map((r) => r.id) : (expanded ? [row.id] : [])
  expandedRowKeys.value = opened
}

// ---------------- 层位选项（质检通过转正式层） ----------------
const layerOptions = ref([])
const effCap = (layer) => layer.effectiveCapacity ?? layer.capacity ?? 0
const isLayerFull = (layer) => (layer.padCount || 0) >= effCap(layer)
const isLayerBlocked = (layer) => !!layer.activeBlock
const layerOptionLabel = (layer) => {
  const used = layer.padCount || 0
  const capacity = effCap(layer)
  const expandTag = layer.activeExpand ? '扩容中 ' : ''
  const base = `${layer.layerCode} - ${layer.layerName}`
  if (isLayerBlocked(layer)) return `${base}（封锁中，不可上架）`
  return isLayerFull(layer)
    ? `${base}（${expandTag}已满 ${used}/${capacity}）`
    : `${base}（${expandTag}占用 ${used}/${capacity}）`
}

const loadLayers = async () => {
  try {
    layerOptions.value = await getShelfLayerList()
  } catch (e) {
    console.error(e)
  }
}

// ---------------- 登记到货 ----------------
const registerVisible = ref(false)
const registerFormRef = ref(null)
const blankItem = () => ({ padCode: '', moldType: '', length: null, width: null, thickness: null })
const registerForm = reactive({
  arrivalTime: '',
  operator: '',
  supplier: '',
  remark: '',
  items: [blankItem()]
})
const registerRules = {
  operator: [{ required: true, message: '请输入经办人', trigger: 'blur' }]
}

const addItemRow = () => registerForm.items.push(blankItem())
const removeItemRow = (index) => {
  if (registerForm.items.length > 1) registerForm.items.splice(index, 1)
}

const openRegisterDialog = () => {
  Object.assign(registerForm, {
    arrivalTime: '',
    operator: '',
    supplier: '',
    remark: '',
    items: [blankItem()]
  })
  registerVisible.value = true
}

const submitRegister = async () => {
  await registerFormRef.value?.validate()
  const items = registerForm.items
    .map((row) => ({
      padCode: (row.padCode || '').trim(),
      moldType: (row.moldType || '').trim() || null,
      length: row.length ?? null,
      width: row.width ?? null,
      thickness: row.thickness ?? null
    }))
  if (items.length === 0 || items.some((i) => !i.padCode)) {
    ElMessage.warning('请补全每行垫板编号（至少一块）')
    return
  }
  const codes = items.map((i) => i.padCode)
  if (new Set(codes).size !== codes.length) {
    ElMessage.warning('批次内垫板编号重复，请检查')
    return
  }
  submitting.value = true
  try {
    await registerArrival({
      arrivalTime: registerForm.arrivalTime || null,
      operator: registerForm.operator,
      supplier: registerForm.supplier || null,
      remark: registerForm.remark || null,
      items
    })
    ElMessage.success('到货登记成功，批内垫板已落待检层（不可领用/换绑，不计可用库存）')
    registerVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

// ---------------- 质检通过 ----------------
const passVisible = ref(false)
const passFormRef = ref(null)
const currentBatch = ref(null)
const passTargets = ref([])
const quickLayerCode = ref('')
const passForm = reactive({ inspectConclusion: '', inspectTime: '', inspector: '' })
const passRules = {
  inspectConclusion: [{ required: true, message: '质检通过必须填写质检结论', trigger: 'blur' }]
}

const applyQuickLayer = () => {
  passTargets.value.forEach((t) => {
    t.layerCode = quickLayerCode.value
  })
}

const openPassDialog = async (row) => {
  currentBatch.value = row
  passTargets.value = (row.items || []).map((item) => ({
    padId: item.padId,
    padCode: item.padCode,
    layerCode: ''
  }))
  quickLayerCode.value = ''
  Object.assign(passForm, { inspectConclusion: '', inspectTime: '', inspector: '' })
  passVisible.value = true
  await loadLayers()
}

const submitPass = async () => {
  await passFormRef.value?.validate()
  const missing = passTargets.value.filter((t) => !t.layerCode)
  if (missing.length > 0) {
    ElMessage.warning(`还有 ${missing.length} 块垫板未指定正式层位，整批通过须逐块上架`)
    return
  }
  // 前端预检：同层分配块数不得超过剩余容量（后端事务内二次校验）
  const countByLayer = {}
  passTargets.value.forEach((t) => {
    countByLayer[t.layerCode] = (countByLayer[t.layerCode] || 0) + 1
  })
  for (const [code, count] of Object.entries(countByLayer)) {
    const layer = layerOptions.value.find((l) => l.layerCode === code)
    if (layer) {
      const remaining = effCap(layer) - (layer.padCount || 0)
      if (count > remaining) {
        ElMessage.warning(`层位【${code}】剩余容量 ${remaining} 块，不足以容纳分配的 ${count} 块，请调整`)
        return
      }
    }
  }
  submitting.value = true
  try {
    await passArrival({
      id: currentBatch.value.id,
      inspectConclusion: passForm.inspectConclusion,
      inspectTime: passForm.inspectTime || null,
      inspector: passForm.inspector || null,
      targets: passTargets.value.map((t) => ({ padId: t.padId, layerCode: t.layerCode }))
    })
    ElMessage.success('质检通过，整批已转正式层并恢复可领用')
    passVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

// ---------------- 判退离库 ----------------
const rejectVisible = ref(false)
const rejectFormRef = ref(null)
const rejectForm = reactive({ inspectConclusion: '', inspectTime: '', inspector: '' })
const rejectRules = {
  inspectConclusion: [{ required: true, message: '判退离库必须填写判退结论/原因', trigger: 'blur' }]
}

const openRejectDialog = (row) => {
  currentBatch.value = row
  Object.assign(rejectForm, { inspectConclusion: '', inspectTime: '', inspector: '' })
  rejectVisible.value = true
}

const submitReject = async () => {
  await rejectFormRef.value?.validate()
  submitting.value = true
  try {
    await rejectArrival({
      id: currentBatch.value.id,
      inspectConclusion: rejectForm.inspectConclusion,
      inspectTime: rejectForm.inspectTime || null,
      inspector: rejectForm.inspector || null
    })
    ElMessage.success('已判退离库，批内垫板档案冻结')
    rejectVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // 从数据概览/档案页快捷入口跳转：按状态或垫板编号筛选
  if (route.query.status) {
    searchForm.status = String(route.query.status)
  }
  if (route.query.padCode) {
    searchForm.padCode = String(route.query.padCode)
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
  gap: 10px;
  padding: 16px 14px;
  border-radius: 10px;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-2px);
  }

  &.card-pending {
    background: linear-gradient(135deg, #f6a23c 0%, #e6890e 100%);
  }
  &.card-quarantine {
    background: linear-gradient(135deg, #b88230 0%, #8c5e10 100%);
  }
  &.card-passed {
    background: linear-gradient(135deg, #11998e 0%, #38b87a 100%);
  }
  &.card-rejected {
    background: linear-gradient(135deg, #909399 0%, #606266 100%);
  }
  &.card-month {
    background: linear-gradient(135deg, #409eff 0%, #2563eb 100%);
  }

  .stat-label {
    font-size: 13px;
    opacity: 0.92;
  }
  .stat-value {
    font-size: 24px;
    font-weight: 700;
    line-height: 1.2;
  }
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.pad-list-panel {
  padding: 8px 16px 12px;
  background: #fafafa;
}

.pad-list-title {
  font-weight: 600;
  margin-bottom: 8px;

  .pad-list-tip {
    margin-left: 10px;
    font-size: 12px;
    font-weight: 400;
    color: #909399;
  }
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.item-editor {
  width: 100%;

  .item-editor-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: 12px;
    color: #909399;
  }
}
</style>
