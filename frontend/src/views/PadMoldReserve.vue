<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">换模垫板预留</h2>
      <el-button type="primary" @click="openRegisterDialog()">
        <el-icon><Stamp /></el-icon>登记预留
      </el-button>
    </div>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="4" v-for="card in statCards" :key="card.status">
        <div class="stat-card" :class="card.cls" @click="quickFilter(card.status)">
          <div class="stat-icon"><el-icon :size="26"><component :is="card.icon" /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-value">{{ stats[card.key] || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card card-month" @click="quickFilterMonth">
          <div class="stat-icon"><el-icon :size="26"><Calendar /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">本月预留</div>
            <div class="stat-value">{{ stats.monthReserveCount || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" @submit.prevent="handleSearch">
        <el-form-item label="预留状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="待生效" value="PENDING" />
            <el-option label="生效中" value="ACTIVE" />
            <el-option label="已到期" value="EXPIRED" />
            <el-option label="已释放" value="RELEASED" />
          </el-select>
        </el-form-item>
        <el-form-item label="模具">
          <el-input
            v-model="searchForm.moldCode"
            placeholder="模具编码/名称"
            clearable
            style="width: 160px"
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
        <el-form-item label="预留日期">
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
              预留板清单（{{ (row.items || []).length }} 块）
              <span class="pad-list-tip">层位为登记时快照，括号内为垫板当前状态</span>
            </div>
            <el-table :data="row.items || []" border size="small">
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column prop="padCode" label="垫板编号" width="150" />
              <el-table-column prop="moldType" label="适配模具" min-width="140" show-overflow-tooltip>
                <template #default="{ row: item }">{{ item.moldType || '-' }}</template>
              </el-table-column>
              <el-table-column label="预留时层位" width="160">
                <template #default="{ row: item }">
                  <el-tag size="small" type="info" effect="plain">{{ item.layerCode || '未在架' }}</el-tag>
                  <div style="font-size: 12px; color: #909399; margin-top: 2px">{{ item.layerName || '' }}</div>
                </template>
              </el-table-column>
              <el-table-column label="当前层位/状态" width="240">
                <template #default="{ row: item }">
                  <template v-if="item.currentLayerCode">
                    <el-tag size="small" type="success" effect="plain">{{ item.currentLayerCode }}</el-tag>
                    <el-tag
                      v-if="isReserveOpen(row) && item.currentLayerCode !== item.layerCode"
                      size="small"
                      type="danger"
                      style="margin-left: 6px"
                    >层位已变动</el-tag>
                  </template>
                  <el-tag v-else size="small" type="warning">已离架</el-tag>
                  <el-tag
                    v-if="item.maintenanceStatus && item.maintenanceStatus !== 'AVAILABLE'"
                    size="small"
                    :type="item.maintenanceStatus === 'SCRAPPED' ? 'danger' : 'info'"
                    style="margin-left: 6px"
                  >{{ getMaintenanceLabel(item.maintenanceStatus) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="模具" width="200">
        <template #default="{ row }">
          <el-tag type="primary" effect="plain">{{ row.moldCode }}</el-tag>
          <div style="font-size: 12px; color: #909399; margin-top: 2px">{{ row.moldName || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="productionLine" label="上线产线/工位" width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.productionLine || '-' }}</template>
      </el-table-column>
      <el-table-column label="预留板" width="90" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="toggleExpand(row)">
            {{ row.padCount ?? (row.items || []).length }} 块
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="预留时段" width="200">
        <template #default="{ row }">
          <div style="font-size: 12px; line-height: 1.6">
            <div>{{ formatTime(row.startTime) }}</div>
            <div>至 {{ formatTime(row.endTime) }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="经办人" width="90" />
      <el-table-column label="状态" width="96" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.status)" :effect="row.status === 'ACTIVE' ? 'dark' : 'light'">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="释放时间" width="160">
        <template #default="{ row }">{{ row.releaseTime ? formatTime(row.releaseTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="释放结论" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.releaseConclusion || '-' }}</template>
      </el-table-column>
      <el-table-column label="释放经办人" width="100">
        <template #default="{ row }">{{ row.releaseOperator || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING' || row.status === 'ACTIVE'"
            link
            type="primary"
            size="small"
            @click="openReleaseDialog(row)"
          >
            手工释放
          </el-button>
          <el-button v-else link type="info" size="small" disabled>
            {{ row.status === 'EXPIRED' ? '已到期' : '已释放' }}
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

    <!-- 登记预留弹窗：模具、预留板清单、生效时段与经办人 -->
    <el-dialog v-model="registerVisible" title="换模垫板预留登记" width="720px" destroy-on-close>
      <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="模具编码" prop="moldCode">
              <el-input v-model="registerForm.moldCode" placeholder="即将上线的模具编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模具名称">
              <el-input v-model="registerForm.moldName" placeholder="模具名称（可选）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="上线产线/工位">
          <el-input v-model="registerForm.productionLine" placeholder="如：冲压二线-1号工位（可选）" />
        </el-form-item>
        <el-form-item label="预留垫板" prop="padIds">
          <el-select
            v-model="registerForm.padIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择在架垫板（待检/停用/领用离架/已预留的垫板不可选）"
            style="width: 100%"
            :loading="padLoading"
          >
            <el-option
              v-for="pad in padOptions"
              :key="pad.id"
              :label="padOptionLabel(pad)"
              :value="pad.id"
              :disabled="!isPadReservable(pad)"
            />
          </el-select>
          <div class="form-tip">
            已选 {{ registerForm.padIds.length }} 块；预留生效后这些垫板禁止其他产线领用，也不能解绑/换层
          </div>
        </el-form-item>
        <el-form-item label="预留时段" prop="period">
          <el-date-picker
            v-model="registerForm.period"
            type="datetimerange"
            range-separator="至"
            start-placeholder="预留开始（默认当前）"
            end-placeholder="预留结束（必填，到期自动释放）"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="经办人" prop="operator">
              <el-input v-model="registerForm.operator" placeholder="请输入经办人姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input
            v-model="registerForm.remark"
            type="textarea"
            :rows="2"
            placeholder="预留备注（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRegister">
          确认预留
        </el-button>
      </template>
    </el-dialog>

    <!-- 手工释放弹窗：必须填写释放结论 -->
    <el-dialog v-model="releaseVisible" title="手工释放换模预留" width="540px" destroy-on-close>
      <el-form ref="releaseFormRef" :model="releaseForm" :rules="releaseRules" label-width="100px">
        <el-form-item label="模具">
          <el-tag type="primary" effect="plain">{{ currentRecord?.moldCode }}</el-tag>
          <span class="form-tip">{{ currentRecord?.moldName || '' }}</span>
        </el-form-item>
        <el-form-item label="预留时段">
          <span>{{ formatTime(currentRecord?.startTime) }} 至 {{ formatTime(currentRecord?.endTime) }}</span>
        </el-form-item>
        <el-form-item label="预留板数">
          <span>{{ currentRecord?.padCount ?? (currentRecord?.items || []).length }} 块</span>
          <span class="form-tip">释放后垫板立即恢复可领、可解绑换层</span>
        </el-form-item>
        <el-form-item label="释放结论" prop="releaseConclusion">
          <el-input
            v-model="releaseForm.releaseConclusion"
            type="textarea"
            :rows="3"
            placeholder="必填：如换模完成已领用/换模计划取消，提前释放预留"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="释放时间">
              <el-date-picker
                v-model="releaseForm.releaseTime"
                type="datetime"
                placeholder="默认当前时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="释放经办人">
              <el-input v-model="releaseForm.releaseOperator" placeholder="缺省取登记经办人" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="releaseVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRelease">
          确认释放
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
  getPadReservePage,
  getPadReserveStatistics,
  registerPadReserve,
  releasePadReserve
} from '@/api/reserve'
import { getPadPage } from '@/api/pad'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const dateRange = ref([])
const expandedRowKeys = ref([])
const stats = reactive({
  activeCount: 0,
  pendingCount: 0,
  expiredCount: 0,
  releasedCount: 0,
  monthReserveCount: 0
})

const statCards = [
  { status: 'ACTIVE', key: 'activeCount', label: '生效中', icon: 'CircleCheck', cls: 'card-active' },
  { status: 'PENDING', key: 'pendingCount', label: '待生效', icon: 'Clock', cls: 'card-pending' },
  { status: 'EXPIRED', key: 'expiredCount', label: '已到期', icon: 'Timer', cls: 'card-expired' },
  { status: 'RELEASED', key: 'releasedCount', label: '已释放', icon: 'CircleClose', cls: 'card-released' }
]

const searchForm = reactive({ status: '', moldCode: '', padCode: '', operator: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

const STATUS_LABELS = { PENDING: '待生效', ACTIVE: '生效中', EXPIRED: '已到期', RELEASED: '已释放' }
const getStatusLabel = (status) => STATUS_LABELS[status] || status
const getStatusTagType = (status) => {
  const map = { PENDING: 'info', ACTIVE: 'warning', EXPIRED: 'success', RELEASED: 'danger' }
  return map[status] || 'info'
}
const isReserveOpen = (row) => row.status === 'PENDING' || row.status === 'ACTIVE'

const MAINTENANCE_LABELS = { AVAILABLE: '可用', PENDING: '待检', DISABLED: '停用', SCRAPPED: '已报废' }
const getMaintenanceLabel = (status) => MAINTENANCE_LABELS[status] || status

const buildQuery = () => {
  const query = { ...searchForm, ...pagination }
  ;['moldCode', 'padCode', 'operator'].forEach((key) => {
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
    const data = await getPadReservePage(buildQuery())
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
    Object.assign(stats, await getPadReserveStatistics())
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { status: '', moldCode: '', padCode: '', operator: '' })
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

// ---------------- 登记预留 ----------------
const registerVisible = ref(false)
const registerFormRef = ref(null)
const padLoading = ref(false)
const padOptions = ref([])
const registerForm = reactive({
  moldCode: '',
  moldName: '',
  productionLine: '',
  padIds: [],
  period: [],
  operator: '',
  remark: ''
})
const registerRules = {
  moldCode: [{ required: true, message: '请填写模具编码', trigger: 'blur' }],
  padIds: [{ required: true, type: 'array', min: 1, message: '请至少选择一块预留垫板', trigger: 'change' }],
  period: [{ required: true, type: 'array', len: 2, message: '请选择预留时段（结束时间必填）', trigger: 'change' }],
  operator: [{ required: true, message: '请输入经办人', trigger: 'blur' }]
}

const isPadAvailable = (pad) => !pad.maintenanceStatus || pad.maintenanceStatus === 'AVAILABLE'
const isPadOfficial = (pad) => !pad.stockStatus || pad.stockStatus === 'OFFICIAL'
const isPadReservable = (pad) =>
  !!(pad.shelfLayerCode && pad.borrowStatus !== 'BORROWED' && isPadAvailable(pad) && !pad.reserveId
    && isPadOfficial(pad))

const padOptionLabel = (pad) => {
  if (pad.stockStatus === 'QUARANTINE') return `${pad.padCode}（到货待检层，不可预留）`
  if (pad.stockStatus === 'REJECTED') return `${pad.padCode}（已判退离库，不可预留）`
  if (!isPadAvailable(pad)) {
    return `${pad.padCode}（${getMaintenanceLabel(pad.maintenanceStatus)}，不可预留）`
  }
  if (!pad.shelfLayerCode) return `${pad.padCode}（未在架，不可预留）`
  if (pad.borrowStatus === 'BORROWED') return `${pad.padCode}（领用离架中，不可预留）`
  if (pad.reserveId) return `${pad.padCode}（已预留给模具 ${pad.reserveMoldCode || ''}，不可重复预留）`
  return `${pad.padCode}（${pad.moldType || '无模具'} / 层位：${pad.shelfLayerCode}）`
}

const loadPads = async () => {
  padLoading.value = true
  try {
    const data = await getPadPage({ pageNum: 1, pageSize: 10000 })
    padOptions.value = data.records || []
  } catch (e) {
    console.error(e)
  } finally {
    padLoading.value = false
  }
}

const openRegisterDialog = async (presetPadId) => {
  Object.assign(registerForm, {
    moldCode: '',
    moldName: '',
    productionLine: '',
    padIds: presetPadId ? [Number(presetPadId)] : [],
    period: [],
    operator: '',
    remark: ''
  })
  registerVisible.value = true
  await loadPads()
  // 档案页快捷入口带入的垫板若已不可预留（并发/状态变化），清空并提示，交由后端二次校验
  if (presetPadId) {
    const target = padOptions.value.find((p) => p.id === Number(presetPadId))
    if (target && !isPadReservable(target)) {
      ElMessage.warning(`垫板【${target.padCode}】当前不可预留（须在架、可用、未被领用、未被预留）`)
      registerForm.padIds = []
    }
  }
}

const submitRegister = async () => {
  await registerFormRef.value?.validate()
  submitting.value = true
  try {
    await registerPadReserve({
      moldCode: registerForm.moldCode.trim(),
      moldName: registerForm.moldName || null,
      productionLine: registerForm.productionLine || null,
      padIds: registerForm.padIds,
      startTime: registerForm.period?.[0] || null,
      endTime: registerForm.period?.[1] || null,
      operator: registerForm.operator,
      remark: registerForm.remark || null
    })
    ElMessage.success('预留登记成功，预留期内这些垫板禁止领用、解绑与换层')
    registerVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

// ---------------- 手工释放 ----------------
const releaseVisible = ref(false)
const releaseFormRef = ref(null)
const currentRecord = ref(null)
const releaseForm = reactive({
  id: null,
  releaseConclusion: '',
  releaseOperator: '',
  releaseTime: ''
})
const releaseRules = {
  releaseConclusion: [{ required: true, message: '手工释放必须填写结论', trigger: 'blur' }]
}

const openReleaseDialog = (row) => {
  currentRecord.value = row
  Object.assign(releaseForm, {
    id: row.id,
    releaseConclusion: '',
    releaseOperator: '',
    releaseTime: ''
  })
  releaseVisible.value = true
}

const submitRelease = async () => {
  await releaseFormRef.value?.validate()
  submitting.value = true
  try {
    await releasePadReserve({
      id: releaseForm.id,
      releaseConclusion: releaseForm.releaseConclusion,
      releaseOperator: releaseForm.releaseOperator || null,
      releaseTime: releaseForm.releaseTime || null
    })
    ElMessage.success('预留已释放，垫板恢复可领')
    releaseVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // 从垫板档案页“预留”快捷入口跳转：直接打开登记弹窗并预选垫板
  if (route.query.status) {
    searchForm.status = String(route.query.status)
  }
  if (route.query.moldCode && route.query.status) {
    searchForm.moldCode = String(route.query.moldCode)
  }
  loadData()
  loadStats()
  if (route.query.padId && !route.query.status) {
    openRegisterDialog(String(route.query.padId))
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

  &.card-active {
    background: linear-gradient(135deg, #f6a23c 0%, #e6890e 100%);
  }
  &.card-pending {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }
  &.card-expired {
    background: linear-gradient(135deg, #11998e 0%, #38b87a 100%);
  }
  &.card-released {
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
</style>
