<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">层位临时扩容</h2>
      <el-button type="primary" @click="openRegisterDialog()">
        <el-icon><TrendCharts /></el-icon>登记扩容
      </el-button>
    </div>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <div class="stat-card card-active" @click="quickFilter('ACTIVE')">
          <div class="stat-icon"><el-icon :size="28"><CircleCheck /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">生效中</div>
            <div class="stat-value">{{ stats.activeCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-pending" @click="quickFilter('PENDING')">
          <div class="stat-icon"><el-icon :size="28"><Clock /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">待生效</div>
            <div class="stat-value">{{ stats.pendingCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-expired" @click="quickFilter('EXPIRED')">
          <div class="stat-icon"><el-icon :size="28"><Timer /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">已到期</div>
            <div class="stat-value">{{ stats.expiredCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-ended" @click="quickFilter('ENDED')">
          <div class="stat-icon"><el-icon :size="28"><CircleClose /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">已结束</div>
            <div class="stat-value">{{ stats.endedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" @submit.prevent="handleSearch">
        <el-form-item label="生效状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="待生效" value="PENDING" />
            <el-option label="生效中" value="ACTIVE" />
            <el-option label="已到期" value="EXPIRED" />
            <el-option label="已结束" value="ENDED" />
          </el-select>
        </el-form-item>
        <el-form-item label="层位编码">
          <el-input
            v-model="searchForm.layerCode"
            placeholder="请输入层位编码"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="生效日期">
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
      <el-table-column label="层位" width="170">
        <template #default="{ row }">
          <el-tag type="info" effect="plain">{{ row.layerCode }}</el-tag>
          <div style="font-size: 12px; color: #909399; margin-top: 2px">{{ row.layerName || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="expandReason" label="扩容原因" min-width="170" show-overflow-tooltip />
      <el-table-column label="配额" width="120" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.originalCapacity }}</el-tag>
          <el-icon style="vertical-align: -2px; margin: 0 4px"><Right /></el-icon>
          <el-tag size="small" type="warning" effect="dark">{{ row.expandCapacity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生效时段" width="195">
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
      <el-table-column label="结束时间" width="160">
        <template #default="{ row }">{{ row.finishTime ? formatTime(row.finishTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="结束结论" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.finishConclusion || '-' }}</template>
      </el-table-column>
      <el-table-column label="结束经办人" width="100">
        <template #default="{ row }">{{ row.finishOperator || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING' || row.status === 'ACTIVE'"
            link
            type="primary"
            size="small"
            @click="openFinishDialog(row)"
          >
            提前结束
          </el-button>
          <el-button v-else link type="info" size="small" disabled>
            {{ row.status === 'EXPIRED' ? '已到期' : '已结束' }}
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

    <!-- 登记扩容弹窗：旺季到货等原因时登记新配额、生效时段与经办人 -->
    <el-dialog v-model="registerVisible" title="层位临时扩容登记" width="560px" destroy-on-close>
      <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-width="100px">
        <el-form-item label="选择层位" prop="layerCode">
          <el-select
            v-model="registerForm.layerCode"
            filterable
            placeholder="请选择要扩容的层位（扩容中层位不可重复登记）"
            style="width: 100%"
            :loading="layerLoading"
          >
            <el-option
              v-for="layer in layerOptions"
              :key="layer.layerCode"
              :label="expandLayerOptionLabel(layer)"
              :value="layer.layerCode"
              :disabled="!!layer.activeExpand"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedLayer" label="当前占用">
          <el-tag type="info">
            在架 {{ selectedLayer.padCount || 0 }} / 原配额 {{ selectedLayer.capacity ?? 0 }}
          </el-tag>
          <span class="form-tip">扩容期内绑定、换绑、归还上架与导入按新配额校验</span>
        </el-form-item>
        <el-form-item label="新配额" prop="expandCapacity">
          <el-input-number
            v-model="registerForm.expandCapacity"
            :min="minExpandCapacity"
            :max="9999"
            controls-position="right"
            style="width: 180px"
          />
          <span class="form-tip">须大于原配额{{ selectedLayer ? `（当前 ${selectedLayer.capacity ?? 0}）` : '' }}</span>
        </el-form-item>
        <el-form-item label="扩容原因" prop="expandReason">
          <el-input
            v-model="registerForm.expandReason"
            type="textarea"
            :rows="2"
            placeholder="如：旺季集中到货，层位配额临时加大"
          />
        </el-form-item>
        <el-form-item label="生效时段" prop="period">
          <el-date-picker
            v-model="registerForm.period"
            type="datetimerange"
            range-separator="至"
            start-placeholder="生效开始（默认当前）"
            end-placeholder="生效结束（必填）"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
          <div class="form-tip">到期自动回到原配额，未到期也可提前结束</div>
        </el-form-item>
        <el-form-item label="经办人" prop="operator">
          <el-input v-model="registerForm.operator" placeholder="请输入经办人姓名" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="registerForm.remark"
            type="textarea"
            :rows="2"
            placeholder="扩容备注（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRegister">
          确认扩容
        </el-button>
      </template>
    </el-dialog>

    <!-- 提前结束弹窗：必须填写结束结论 -->
    <el-dialog v-model="finishVisible" title="提前结束扩容" width="520px" destroy-on-close>
      <el-form ref="finishFormRef" :model="finishForm" :rules="finishRules" label-width="100px">
        <el-form-item label="层位">
          <el-tag type="info">{{ currentRecord?.layerCode }}</el-tag>
          <span class="form-tip">{{ currentRecord?.layerName || '' }}</span>
        </el-form-item>
        <el-form-item label="扩容原因">
          <span>{{ currentRecord?.expandReason }}</span>
        </el-form-item>
        <el-form-item label="配额">
          <el-tag size="small" effect="plain">{{ currentRecord?.originalCapacity }}</el-tag>
          <el-icon style="vertical-align: -2px; margin: 0 4px"><Right /></el-icon>
          <el-tag size="small" type="warning" effect="dark">{{ currentRecord?.expandCapacity }}</el-tag>
          <span class="form-tip">结束后立即回到原配额</span>
        </el-form-item>
        <el-form-item label="生效时段">
          <span>{{ formatTime(currentRecord?.startTime) }} 至 {{ formatTime(currentRecord?.endTime) }}</span>
        </el-form-item>
        <el-form-item label="结束结论" prop="finishConclusion">
          <el-input
            v-model="finishForm.finishConclusion"
            type="textarea"
            :rows="3"
            placeholder="必填：如旺季到货结束/备货已分流，提前恢复原配额"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="结束时间">
              <el-date-picker
                v-model="finishForm.finishTime"
                type="datetime"
                placeholder="默认当前时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束经办人">
              <el-input
                v-model="finishForm.finishOperator"
                placeholder="缺省取登记经办人"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="finishVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitFinish">
          确认结束
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import {
  getLayerExpandPage,
  getLayerExpandStatistics,
  registerLayerExpand,
  finishLayerExpand
} from '@/api/expand'
import { getShelfLayerList } from '@/api/shelf'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const dateRange = ref([])
const stats = reactive({ activeCount: 0, pendingCount: 0, expiredCount: 0, endedCount: 0 })

const searchForm = reactive({ status: '', layerCode: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

// 生效状态：待生效/生效中/已到期/已结束
const STATUS_LABELS = { PENDING: '待生效', ACTIVE: '生效中', EXPIRED: '已到期', ENDED: '已结束' }
const getStatusLabel = (status) => STATUS_LABELS[status] || status
const getStatusTagType = (status) => {
  const map = { PENDING: 'info', ACTIVE: 'warning', EXPIRED: 'success', ENDED: 'danger' }
  return map[status] || 'info'
}

const buildQuery = () => {
  const query = { ...searchForm, ...pagination }
  if (typeof query.layerCode === 'string') {
    query.layerCode = query.layerCode.trim()
    if (!query.layerCode) delete query.layerCode
  }
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
    const data = await getLayerExpandPage(buildQuery())
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
    Object.assign(stats, await getLayerExpandStatistics())
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { status: '', layerCode: '' })
  dateRange.value = []
  handleSearch()
}

const quickFilter = (status) => {
  searchForm.status = status
  handleSearch()
}

// ---------------- 登记扩容 ----------------
const registerVisible = ref(false)
const registerFormRef = ref(null)
const layerLoading = ref(false)
const layerOptions = ref([])
const registerForm = reactive({
  layerCode: '',
  expandCapacity: 1,
  expandReason: '',
  period: [],
  operator: '',
  remark: ''
})
const registerRules = {
  layerCode: [{ required: true, message: '请选择要扩容的层位', trigger: 'change' }],
  expandCapacity: [{ required: true, message: '请填写新配额', trigger: 'blur' }],
  expandReason: [{ required: true, message: '请填写扩容原因', trigger: 'blur' }],
  period: [{ required: true, type: 'array', len: 2, message: '请选择生效时段（结束时间必填）', trigger: 'change' }],
  operator: [{ required: true, message: '请输入经办人', trigger: 'blur' }]
}

const selectedLayer = computed(() =>
  layerOptions.value.find((l) => l.layerCode === registerForm.layerCode)
)

// 新配额下限：原配额 + 1（临时加大，必须大于原配额）
const minExpandCapacity = computed(() => (selectedLayer.value?.capacity ?? 0) + 1)

// 切换层位时把新配额重置为下限，避免沿用上一层的值
watch(() => registerForm.layerCode, (code) => {
  if (code) {
    registerForm.expandCapacity = minExpandCapacity.value
  }
})

// 已扩容层位禁用并标注，避免重复登记
const expandLayerOptionLabel = (layer) => {
  const effCap = layer.effectiveCapacity ?? layer.capacity ?? 0
  const base = `${layer.layerCode} - ${layer.layerName || ''}（占用 ${layer.padCount || 0}/${effCap}）`
  return layer.activeExpand ? `${base}【扩容中】` : base
}

const loadLayers = async () => {
  layerLoading.value = true
  try {
    layerOptions.value = (await getShelfLayerList()) || []
  } catch (e) {
    console.error(e)
  } finally {
    layerLoading.value = false
  }
}

const openRegisterDialog = async (presetLayerCode) => {
  Object.assign(registerForm, {
    layerCode: presetLayerCode || '',
    expandCapacity: 1,
    expandReason: '',
    period: [],
    operator: '',
    remark: ''
  })
  registerVisible.value = true
  await loadLayers()
  if (registerForm.layerCode) {
    // 预填层位当前已扩容（并发场景）时清空选择，交由后端二次校验拦截
    if (selectedLayer.value?.activeExpand) {
      ElMessage.warning(`层位【${registerForm.layerCode}】已处于扩容中，请勿重复登记`)
      registerForm.layerCode = ''
    } else {
      registerForm.expandCapacity = minExpandCapacity.value
    }
  }
}

const submitRegister = async () => {
  await registerFormRef.value?.validate()
  submitting.value = true
  try {
    await registerLayerExpand({
      layerCode: registerForm.layerCode,
      expandReason: registerForm.expandReason,
      expandCapacity: registerForm.expandCapacity,
      startTime: registerForm.period?.[0] || null,
      endTime: registerForm.period?.[1] || null,
      operator: registerForm.operator,
      remark: registerForm.remark || null
    })
    ElMessage.success('扩容登记成功，扩容期内该层按新配额校验')
    registerVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

// ---------------- 提前结束 ----------------
const finishVisible = ref(false)
const finishFormRef = ref(null)
const currentRecord = ref(null)
const finishForm = reactive({
  id: null,
  finishConclusion: '',
  finishOperator: '',
  finishTime: ''
})
const finishRules = {
  finishConclusion: [{ required: true, message: '提前结束扩容必须填写结论', trigger: 'blur' }]
}

const openFinishDialog = (row) => {
  currentRecord.value = row
  Object.assign(finishForm, {
    id: row.id,
    finishConclusion: '',
    finishOperator: '',
    finishTime: ''
  })
  finishVisible.value = true
}

const submitFinish = async () => {
  await finishFormRef.value?.validate()
  submitting.value = true
  try {
    await finishLayerExpand({
      id: finishForm.id,
      finishConclusion: finishForm.finishConclusion,
      finishOperator: finishForm.finishOperator || null,
      finishTime: finishForm.finishTime || null
    })
    ElMessage.success('扩容已提前结束，层位已回到原配额')
    finishVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // 从层位管理卡片跳转：带 status 时按条件回看台账，否则直接打开登记弹窗并预填层位
  if (route.query.status) {
    searchForm.status = String(route.query.status)
  }
  if (route.query.layerCode && route.query.status) {
    searchForm.layerCode = String(route.query.layerCode)
  }
  loadData()
  loadStats()
  if (route.query.layerCode && !route.query.status) {
    openRegisterDialog(String(route.query.layerCode))
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

  &.card-active {
    background: linear-gradient(135deg, #f6a23c 0%, #e6890e 100%);
  }
  &.card-pending {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }
  &.card-expired {
    background: linear-gradient(135deg, #11998e 0%, #38b87a 100%);
  }
  &.card-ended {
    background: linear-gradient(135deg, #909399 0%, #606266 100%);
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

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>
