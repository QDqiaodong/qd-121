<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">垫板报废出库</h2>
      <el-button type="danger" @click="openOutbound">
        <el-icon><Delete /></el-icon>报废出库登记
      </el-button>
    </div>

    <el-alert
      class="flow-tip"
      type="warning"
      :closable="false"
      show-icon
      title="流程：保养台账给出“报废建议” → 库房在此登记批准人、去向、时间与照片 → 出库后档案不再占层位，也不能再被领用或回架"
    />

    <el-row :gutter="16" class="stat-row">
      <el-col :span="8">
        <div class="stat-card card-scrapped">
          <div class="stat-icon"><el-icon :size="30"><Delete /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">累计报废出库</div>
            <div class="stat-value">{{ stats.scrapRecordCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card card-suggestion">
          <div class="stat-icon"><el-icon :size="30"><WarnTriangleFilled /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">待出库（报废建议）</div>
            <div class="stat-value">{{ pendingCount }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card card-month">
          <div class="stat-icon"><el-icon :size="30"><Calendar /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">本月报废出库</div>
            <div class="stat-value">{{ monthCount }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" @submit.prevent="handleSearch">
        <el-form-item label="垫板编号">
          <el-input
            v-model="searchForm.padCode"
            placeholder="请输入垫板编号"
            clearable
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="去向">
          <el-input
            v-model="searchForm.destination"
            placeholder="如：废品仓 / 回收商"
            clearable
            style="width: 170px"
          />
        </el-form-item>
        <el-form-item label="批准人">
          <el-input
            v-model="searchForm.approver"
            placeholder="请输入批准人"
            clearable
            style="width: 130px"
          />
        </el-form-item>
        <el-form-item label="出库日期">
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
      <el-table-column prop="moldType" label="适配模具" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.moldType || '-' }}</template>
      </el-table-column>
      <el-table-column label="档案状态" width="100">
        <template #default>
          <el-tag type="danger" effect="dark">已报废</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="destination" label="报废去向" width="150" show-overflow-tooltip />
      <el-table-column prop="approver" label="批准人" width="100" />
      <el-table-column label="出库前层位" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.originLayerCode" size="small" type="info" effect="plain">
            {{ row.originLayerCode }}
          </el-tag>
          <span v-else style="color: #909399">离架中/未上架</span>
        </template>
      </el-table-column>
      <el-table-column label="报废照片" width="150" align="center">
        <template #default="{ row }">
          <el-image
            v-for="(url, idx) in (row.photos || []).slice(0, 3)"
            :key="idx"
            :src="url"
            :preview-src-list="row.photos"
            :initial-index="idx"
            preview-teleported
            fit="cover"
            class="photo-thumb"
          />
          <span v-if="!(row.photos && row.photos.length)" style="color: #c0c4cc">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="scrapTime" label="出库时间" width="160">
        <template #default="{ row }">{{ formatTime(row.scrapTime) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
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

    <!-- 报废出库登记弹窗 -->
    <el-dialog v-model="outboundVisible" title="垫板报废出库登记" width="640px" destroy-on-close>
      <el-form
        ref="outboundFormRef"
        :model="outboundForm"
        :rules="outboundRules"
        label-width="100px"
      >
        <el-form-item label="选择垫板" prop="padId">
          <el-select
            v-model="outboundForm.padId"
            filterable
            placeholder="请选择有“报废建议”的在库垫板"
            style="width: 100%"
            :loading="candidateLoading"
          >
            <el-option
              v-for="pad in candidatePads"
              :key="pad.id"
              :label="padOptionLabel(pad)"
              :value="pad.id"
              :disabled="!isPadScrappable(pad)"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedPad" label="垫板现状">
          <el-tag :type="getStatusTagType(selectedPad.maintenanceStatus)" size="small">
            {{ getStatusLabel(selectedPad.maintenanceStatus) }}
          </el-tag>
          <el-tag
            v-if="selectedPad.shelfLayerCode"
            size="small"
            type="info"
            effect="plain"
            style="margin-left: 6px"
          >
            在架 {{ selectedPad.shelfLayerCode }}
          </el-tag>
          <el-tag v-else size="small" type="warning" effect="plain" style="margin-left: 6px">
            未占层位
          </el-tag>
          <div v-if="!hasSuggestion(selectedPad.id)" class="form-tip error">
            该垫板尚无保养“报废建议”，请先到保养台账登记
          </div>
          <div v-else-if="selectedPad.borrowStatus === 'BORROWED'" class="form-tip error">
            垫板领用离架中，请先归还后再办理报废出库
          </div>
          <div v-else-if="selectedPad.shelfLayerCode" class="form-tip warning">
            确认出库后垫板将从 {{ selectedPad.shelfLayerCode }} 离架，层位占用立即释放
          </div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="批准人" prop="approver">
              <el-input v-model="outboundForm.approver" placeholder="请输入批准人姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出库时间" prop="scrapTime">
              <el-date-picker
                v-model="outboundForm.scrapTime"
                type="datetime"
                placeholder="默认当前时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="报废去向" prop="destination">
          <el-select
            v-model="outboundForm.destination"
            placeholder="请选择或填写报废去向"
            style="width: 100%"
            allow-create
            filterable
          >
            <el-option label="废品仓暂存" value="废品仓暂存" />
            <el-option label="回收商回收" value="回收商回收" />
            <el-option label="就地销毁" value="就地销毁" />
            <el-option label="返厂处理" value="返厂处理" />
          </el-select>
        </el-form-item>
        <el-form-item label="报废照片" prop="photoPaths">
          <el-upload
            list-type="picture-card"
            :file-list="photoFileList"
            :before-upload="beforePhotoUpload"
            :http-request="handlePhotoUpload"
            :on-remove="handlePhotoRemove"
            accept="image/*"
            multiple
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="upload-tip">至少上传 1 张报废出库照片，支持多张，前端自动压缩</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="outboundForm.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="outboundVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitOutbound">
          确认报废出库
        </el-button>
      </template>
    </el-dialog>

    <!-- 报废出库详情弹窗 -->
    <el-dialog v-model="detailVisible" title="报废出库详情" width="720px" destroy-on-close>
      <div v-loading="detailLoading">
      <el-descriptions v-if="detail" :column="2" border size="small" class="detail-desc">
        <el-descriptions-item label="垫板编号">{{ detail.padCode }}</el-descriptions-item>
        <el-descriptions-item label="适配模具">{{ detail.moldType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="档案状态">
          <el-tag type="danger" effect="dark">已报废（不可领用/回架）</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="报废去向">{{ detail.destination }}</el-descriptions-item>
        <el-descriptions-item label="批准人">{{ detail.approver }}</el-descriptions-item>
        <el-descriptions-item label="出库时间">{{ formatTime(detail.scrapTime) }}</el-descriptions-item>
        <el-descriptions-item label="出库前层位">{{ detail.originLayerCode || '离架中/未上架' }}</el-descriptions-item>
        <el-descriptions-item label="登记时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="detail-section-title">报废照片（{{ detail?.photos?.length || 0 }}）</div>
      <div v-if="detail && detail.photos.length" class="photo-wall">
        <el-image
          v-for="(url, idx) in detail.photos"
          :key="idx"
          :src="url"
          :preview-src-list="detail.photos"
          :initial-index="idx"
          preview-teleported
          fit="cover"
          class="photo-wall-item"
        />
      </div>
      <el-empty v-else description="暂无照片" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
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
  getScrapPage,
  getScrapById,
  getScrapStatistics,
  getOutboundCandidates,
  outboundScrap
} from '@/api/scrap'
import { uploadImage } from '@/api/file'
import { compressImage } from '@/utils/imageCompress'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const dateRange = ref([])
const stats = reactive({ scrappedCount: 0, scrapRecordCount: 0, monthScrapCount: 0 })

const searchForm = reactive({ padCode: '', destination: '', approver: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

// 本月报废数取后端统计，翻页/筛选不影响卡片口径
const monthCount = computed(() => stats.monthScrapCount || 0)
// 待出库：候选中仍有报废建议且未报废的数量，加载候选时计算
const pendingCount = ref(0)

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
    const data = await getScrapPage(buildQuery())
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
    Object.assign(stats, await getScrapStatistics())
  } catch (e) {
    console.error(e)
  }
}

const loadPendingCount = async () => {
  try {
    const data = await getOutboundCandidates()
    const ids = new Set(data.suggestionPadIds || [])
    // 候选列表已排除已报废：有报废建议且未领用中的即待出库
    pendingCount.value = (data.pads || []).filter(
      (p) => ids.has(p.id) && p.borrowStatus !== 'BORROWED'
    ).length
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { padCode: '', destination: '', approver: '' })
  dateRange.value = []
  handleSearch()
}

// ---------------- 出库登记 ----------------
const outboundVisible = ref(false)
const outboundFormRef = ref(null)
const candidateLoading = ref(false)
const candidatePads = ref([])
const suggestionPadIds = ref([])
const photoFileList = ref([])

const outboundForm = reactive({
  padId: null,
  approver: '',
  destination: '',
  scrapTime: '',
  photoPaths: [],
  remark: ''
})
const outboundRules = {
  padId: [{ required: true, message: '请选择垫板', trigger: 'change' }],
  approver: [{ required: true, message: '请输入批准人', trigger: 'blur' }],
  destination: [{ required: true, message: '请选择或填写报废去向', trigger: 'change' }],
  photoPaths: [
    {
      required: true,
      type: 'array',
      min: 1,
      message: '请至少上传一张报废照片',
      trigger: 'change'
    }
  ]
}

const STATUS_LABELS = {
  AVAILABLE: '可用',
  PENDING: '待检',
  DISABLED: '停用',
  SCRAPPED: '已报废'
}
const getStatusLabel = (status) => STATUS_LABELS[status] || status
const getStatusTagType = (status) => {
  if (status === 'AVAILABLE') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'DISABLED') return 'info'
  if (status === 'SCRAPPED') return 'danger'
  return 'info'
}

const hasSuggestion = (padId) => suggestionPadIds.value.includes(padId)
const isPadScrappable = (pad) =>
  hasSuggestion(pad.id) && pad.borrowStatus !== 'BORROWED' && !pad.reserveId

const padOptionLabel = (pad) => {
  const parts = [`${pad.padCode}（${pad.moldType || '无模具'} / ${getStatusLabel(pad.maintenanceStatus)}`]
  if (pad.borrowStatus === 'BORROWED') parts.push('领用中')
  if (pad.shelfLayerCode) parts.push('层位:' + pad.shelfLayerCode)
  let label = parts.join(' / ') + '）'
  if (!hasSuggestion(pad.id)) label += '【无报废建议】'
  else if (pad.reserveId) label += `【已预留给模具 ${pad.reserveMoldCode || ''}】`
  else if (pad.borrowStatus === 'BORROWED') label += '【领用中】'
  return label
}

const selectedPad = computed(() => candidatePads.value.find((p) => p.id === outboundForm.padId))

const loadCandidates = async () => {
  candidateLoading.value = true
  try {
    const data = await getOutboundCandidates()
    candidatePads.value = data.pads || []
    suggestionPadIds.value = data.suggestionPadIds || []
  } catch (e) {
    console.error(e)
  } finally {
    candidateLoading.value = false
  }
}

const openOutbound = async () => {
  Object.assign(outboundForm, {
    padId: route.query.padId ? Number(route.query.padId) : null,
    approver: '',
    destination: '',
    scrapTime: '',
    photoPaths: [],
    remark: ''
  })
  photoFileList.value = []
  outboundVisible.value = true
  await loadCandidates()
}

const beforePhotoUpload = async (file) => {
  try {
    return await compressImage(file, {
      maxWidth: 1280,
      maxHeight: 1280,
      quality: 0.8,
      maxSize: 1024 * 1024
    })
  } catch (e) {
    ElMessage.error(e.message)
    return false
  }
}

const handlePhotoUpload = async ({ file, onSuccess, onError }) => {
  try {
    const res = await uploadImage(file)
    photoFileList.value.push({ name: res.name || file.name, url: res.url })
    outboundForm.photoPaths = photoFileList.value.map((f) => f.url)
    outboundFormRef.value?.validateField('photoPaths').catch(() => {})
    onSuccess?.(res)
    ElMessage.success('照片上传成功')
  } catch (e) {
    onError?.(e)
    console.error(e)
  }
}

const handlePhotoRemove = (_file, fileList) => {
  photoFileList.value = fileList
  outboundForm.photoPaths = fileList.map((f) => f.url)
  outboundFormRef.value?.validateField('photoPaths').catch(() => {})
}

const submitOutbound = async () => {
  await outboundFormRef.value?.validate()
  const photoPaths = outboundForm.photoPaths.filter(Boolean)
  if (!photoPaths.length) {
    ElMessage.warning('请至少上传一张报废出库照片')
    return
  }
  const pad = selectedPad.value
  if (pad && !hasSuggestion(pad.id)) {
    ElMessage.warning('该垫板暂无保养“报废建议”，请先到保养台账登记')
    return
  }
  if (pad && pad.borrowStatus === 'BORROWED') {
    ElMessage.warning('垫板领用离架中，请先归还后再办理报废出库')
    return
  }
  submitting.value = true
  try {
    await outboundScrap({
      padId: outboundForm.padId,
      approver: outboundForm.approver,
      destination: outboundForm.destination,
      scrapTime: outboundForm.scrapTime || null,
      photoPaths,
      remark: outboundForm.remark || null
    })
    ElMessage.success('报废出库成功，垫板已离架且不可再领用/回架')
    outboundVisible.value = false
    loadData()
    loadStats()
    loadPendingCount()
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

const openDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getScrapById(row.id)
  } catch (e) {
    console.error(e)
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  // 从档案页“报废记录”入口跳转：按垫板编号预筛选
  if (route.query.padCode) {
    searchForm.padCode = String(route.query.padCode)
  }
  loadData()
  loadStats()
  loadPendingCount()
  // 从保养台账/档案页携带 padId 跳转时直接打开登记弹窗
  if (route.query.padId) {
    openOutbound()
  }
})
</script>

<style lang="scss" scoped>
.flow-tip {
  margin-bottom: 16px;
}

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
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s;

  &.card-scrapped {
    background: linear-gradient(135deg, #6b7280 0%, #4b5563 100%);
  }
  &.card-suggestion {
    background: linear-gradient(135deg, #f6a84c 0%, #f5851f 100%);
  }
  &.card-month {
    background: linear-gradient(135deg, #8e5ea2 0%, #6d4886 100%);
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

.photo-thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  margin: 0 2px;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;

  &.warning {
    color: #e6a23c;
  }
  &.error {
    color: #f56c6c;
  }
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
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

.photo-wall {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;

  .photo-wall-item {
    width: 140px;
    height: 140px;
    border-radius: 6px;
    cursor: zoom-in;
  }
}
</style>
