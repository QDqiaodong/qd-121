<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">层位封锁管理</h2>
      <el-button type="primary" @click="openBlockDialog()">
        <el-icon><Lock /></el-icon>登记封锁
      </el-button>
    </div>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="8">
        <div class="stat-card card-blocked" @click="quickFilter('BLOCKED')">
          <div class="stat-icon"><el-icon :size="28"><Lock /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">封锁中</div>
            <div class="stat-value">{{ stats.blockedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card card-released" @click="quickFilter('RELEASED')">
          <div class="stat-icon"><el-icon :size="28"><Unlock /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">已解除</div>
            <div class="stat-value">{{ stats.releasedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card card-month" @click="quickFilterMonth">
          <div class="stat-icon"><el-icon :size="28"><Calendar /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">本月封锁</div>
            <div class="stat-value">{{ stats.monthBlockCount || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" @submit.prevent="handleSearch">
        <el-form-item label="封锁状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="封锁中" value="BLOCKED" />
            <el-option label="已解除" value="RELEASED" />
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
        <el-form-item label="封锁类型">
          <el-select v-model="searchForm.blockType" placeholder="全部类型" clearable style="width: 140px">
            <el-option label="层位破损" value="DAMAGE" />
            <el-option label="待清扫" value="CLEANING" />
            <el-option label="检修中" value="MAINTENANCE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="封锁日期">
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
      <el-table-column label="层位" width="180">
        <template #default="{ row }">
          <el-tag type="info" effect="plain">{{ row.layerCode }}</el-tag>
          <div style="font-size: 12px; color: #909399; margin-top: 2px">{{ row.layerName || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="封锁类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="getBlockTypeTagType(row.blockType)" size="small">
            {{ getBlockTypeLabel(row.blockType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="blockReason" label="封锁原因" min-width="180" show-overflow-tooltip />
      <el-table-column label="开始时间" width="160">
        <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column prop="operator" label="经办人" width="100" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'BLOCKED'" type="danger" effect="dark">封锁中</el-tag>
          <el-tag v-else type="success" effect="light">已解除</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="解除时间" width="160">
        <template #default="{ row }">{{ row.releaseTime ? formatTime(row.releaseTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="解除结论" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.releaseConclusion || '-' }}</template>
      </el-table-column>
      <el-table-column label="解除经办人" width="110">
        <template #default="{ row }">{{ row.releaseOperator || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'BLOCKED'"
            link
            type="primary"
            size="small"
            @click="openReleaseDialog(row)"
          >
            解除封锁
          </el-button>
          <el-button v-else link type="info" size="small" disabled>已解除</el-button>
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

    <!-- 登记封锁弹窗：破损/待清扫/检修时登记原因、开始时间与经办人 -->
    <el-dialog v-model="blockVisible" title="层位封锁登记" width="560px" destroy-on-close>
      <el-form ref="blockFormRef" :model="blockForm" :rules="blockRules" label-width="100px">
        <el-form-item label="选择层位" prop="layerCode">
          <el-select
            v-model="blockForm.layerCode"
            filterable
            placeholder="请选择要封锁的层位（已封锁层位不可重复登记）"
            style="width: 100%"
            :loading="layerLoading"
          >
            <el-option
              v-for="layer in layerOptions"
              :key="layer.layerCode"
              :label="blockLayerOptionLabel(layer)"
              :value="layer.layerCode"
              :disabled="!!layer.activeBlock"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedLayer" label="当前占用">
          <el-tag type="info">
            在架 {{ selectedLayer.padCount || 0 }} / 配额 {{ selectedLayer.effectiveCapacity ?? selectedLayer.capacity ?? 0 }}
          </el-tag>
          <span class="form-tip">封锁期间该层禁止绑定、换绑、归还上架与导入占位</span>
        </el-form-item>
        <el-form-item label="封锁类型" prop="blockType">
          <el-radio-group v-model="blockForm.blockType">
            <el-radio value="DAMAGE">层位破损</el-radio>
            <el-radio value="CLEANING">待清扫</el-radio>
            <el-radio value="MAINTENANCE">检修中</el-radio>
            <el-radio value="OTHER">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="封锁原因" prop="blockReason">
          <el-input
            v-model="blockForm.blockReason"
            type="textarea"
            :rows="2"
            placeholder="如：层板变形破损，需更换；或：定期清扫，暂停使用"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker
                v-model="blockForm.startTime"
                type="datetime"
                placeholder="默认当前时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经办人" prop="operator">
              <el-input v-model="blockForm.operator" placeholder="请输入经办人姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input
            v-model="blockForm.remark"
            type="textarea"
            :rows="2"
            placeholder="封锁备注（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="blockVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBlock">
          确认封锁
        </el-button>
      </template>
    </el-dialog>

    <!-- 解除封锁弹窗：必须填写解除结论 -->
    <el-dialog v-model="releaseVisible" title="解除层位封锁" width="520px" destroy-on-close>
      <el-form ref="releaseFormRef" :model="releaseForm" :rules="releaseRules" label-width="100px">
        <el-form-item label="层位">
          <el-tag type="info">{{ currentRecord?.layerCode }}</el-tag>
          <span class="form-tip">{{ currentRecord?.layerName || '' }}</span>
        </el-form-item>
        <el-form-item label="封锁原因">
          <span>{{ currentRecord?.blockReason }}</span>
        </el-form-item>
        <el-form-item label="开始时间">
          <span>{{ formatTime(currentRecord?.startTime) }} · 经办人：{{ currentRecord?.operator }}</span>
        </el-form-item>
        <el-form-item label="解除结论" prop="releaseConclusion">
          <el-input
            v-model="releaseForm.releaseConclusion"
            type="textarea"
            :rows="3"
            placeholder="必填：如破损已修复/清扫检修完成，层位恢复可用"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="解除时间">
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
            <el-form-item label="解除经办人">
              <el-input
                v-model="releaseForm.releaseOperator"
                placeholder="缺省取登记经办人"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="releaseVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRelease">
          确认解除
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
  getLayerBlockPage,
  getLayerBlockStatistics,
  blockLayer,
  releaseLayerBlock
} from '@/api/block'
import { getShelfLayerList } from '@/api/shelf'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const dateRange = ref([])
const stats = reactive({ blockedCount: 0, releasedCount: 0, monthBlockCount: 0 })

const searchForm = reactive({ status: '', layerCode: '', blockType: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

const BLOCK_TYPE_LABELS = { DAMAGE: '层位破损', CLEANING: '待清扫', MAINTENANCE: '检修中', OTHER: '其他' }
const getBlockTypeLabel = (type) => BLOCK_TYPE_LABELS[type] || type
const getBlockTypeTagType = (type) => {
  const map = { DAMAGE: 'danger', CLEANING: 'warning', MAINTENANCE: 'primary', OTHER: 'info' }
  return map[type] || 'info'
}

const buildQuery = () => {
  const query = { ...searchForm, ...pagination }
  if (typeof query.layerCode === 'string') {
    query.layerCode = query.layerCode.trim()
    if (!query.layerCode) delete query.layerCode
  }
  if (!query.status) delete query.status
  if (!query.blockType) delete query.blockType
  if (dateRange.value && dateRange.value.length === 2) {
    query.startTime = dateRange.value[0]
    query.endTime = dateRange.value[1]
  }
  return query
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getLayerBlockPage(buildQuery())
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
    Object.assign(stats, await getLayerBlockStatistics())
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { status: '', layerCode: '', blockType: '' })
  dateRange.value = []
  handleSearch()
}

const quickFilter = (status) => {
  searchForm.status = status
  handleSearch()
}

const quickFilterMonth = () => {
  const start = dayjs().startOf('month').format('YYYY-MM-DD HH:mm:ss')
  const end = dayjs().endOf('month').format('YYYY-MM-DD HH:mm:ss')
  dateRange.value = [start, end]
  handleSearch()
}

// ---------------- 登记封锁 ----------------
const blockVisible = ref(false)
const blockFormRef = ref(null)
const layerLoading = ref(false)
const layerOptions = ref([])
const blockForm = reactive({
  layerCode: '',
  blockType: 'DAMAGE',
  blockReason: '',
  startTime: '',
  operator: '',
  remark: ''
})
const blockRules = {
  layerCode: [{ required: true, message: '请选择要封锁的层位', trigger: 'change' }],
  blockType: [{ required: true, message: '请选择封锁类型', trigger: 'change' }],
  blockReason: [{ required: true, message: '请填写封锁原因', trigger: 'blur' }],
  operator: [{ required: true, message: '请输入经办人', trigger: 'blur' }]
}

const selectedLayer = computed(() =>
  layerOptions.value.find((l) => l.layerCode === blockForm.layerCode)
)

// 已封锁层位禁用并标注，避免重复登记；占用按实际配额展示（扩容期内取扩容后配额）
const blockLayerOptionLabel = (layer) => {
  const effCap = layer.effectiveCapacity ?? layer.capacity ?? 0
  const base = `${layer.layerCode} - ${layer.layerName || ''}（占用 ${layer.padCount || 0}/${effCap}）`
  return layer.activeBlock ? `${base}【已封锁中】` : base
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

const openBlockDialog = async (presetLayerCode) => {
  Object.assign(blockForm, {
    layerCode: presetLayerCode || '',
    blockType: 'DAMAGE',
    blockReason: '',
    startTime: '',
    operator: '',
    remark: ''
  })
  blockVisible.value = true
  await loadLayers()
  // 预填层位当前已封锁（并发场景）时清空选择，交由后端二次校验拦截
  if (blockForm.layerCode && selectedLayer.value?.activeBlock) {
    ElMessage.warning(`层位【${blockForm.layerCode}】已处于封锁中，请勿重复登记`)
    blockForm.layerCode = ''
  }
}

const submitBlock = async () => {
  await blockFormRef.value?.validate()
  submitting.value = true
  try {
    await blockLayer({
      layerCode: blockForm.layerCode,
      blockType: blockForm.blockType,
      blockReason: blockForm.blockReason,
      startTime: blockForm.startTime || null,
      operator: blockForm.operator,
      remark: blockForm.remark || null
    })
    ElMessage.success('封锁登记成功，该层位已禁止上架占位')
    blockVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

// ---------------- 解除封锁 ----------------
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
  releaseConclusion: [{ required: true, message: '解除封锁必须填写结论', trigger: 'blur' }]
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
    await releaseLayerBlock({
      id: releaseForm.id,
      releaseConclusion: releaseForm.releaseConclusion,
      releaseOperator: releaseForm.releaseOperator || null,
      releaseTime: releaseForm.releaseTime || null
    })
    ElMessage.success('封锁已解除，层位恢复可上架')
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
  // 从层位管理卡片跳转：带 status 时按条件回看台账（如解除封锁入口），否则直接打开登记弹窗并预填层位
  if (route.query.status) {
    searchForm.status = String(route.query.status)
  }
  if (route.query.layerCode && route.query.status) {
    searchForm.layerCode = String(route.query.layerCode)
  }
  loadData()
  loadStats()
  if (route.query.layerCode && !route.query.status) {
    openBlockDialog(String(route.query.layerCode))
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

  &.card-blocked {
    background: linear-gradient(135deg, #f5576c 0%, #d9363e 100%);
  }
  &.card-released {
    background: linear-gradient(135deg, #11998e 0%, #38b87a 100%);
  }
  &.card-month {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
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
}
</style>
