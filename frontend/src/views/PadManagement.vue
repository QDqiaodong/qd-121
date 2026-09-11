<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">垫板档案管理</h2>
      <div>
        <el-button type="success" @click="handleExportAll">
          <el-icon><Download /></el-icon>导出全部
        </el-button>
        <el-button type="warning" @click="goImport">
          <el-icon><UploadFilled /></el-icon>批量导入
        </el-button>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新建垫板档案
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
        <el-form-item label="适配模具">
          <el-input
            v-model="searchForm.moldType"
            placeholder="请输入模具类型"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="所属货架">
          <el-select
            v-model="searchForm.shelfCode"
            placeholder="选择货架"
            clearable
            style="width: 160px"
            @change="handleShelfFilterChange"
          >
            <el-option
              v-for="shelf in shelfOptions"
              :key="shelf"
              :label="shelf + ' 货架'"
              :value="shelf"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分层编码">
          <el-select
            v-model="searchForm.shelfLayerCode"
            placeholder="选择分层"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="layer in filterLayerOptions"
              :key="layer.layerCode"
              :label="layer.layerCode + ' - ' + layer.layerName"
              :value="layer.layerCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="保养状态">
          <el-select
            v-model="searchForm.maintenanceStatus"
            placeholder="全部状态"
            clearable
            style="width: 140px"
          >
            <el-option label="可用" value="AVAILABLE" />
            <el-option label="待检" value="PENDING" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
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
      <el-table-column prop="moldType" label="适配模具" width="160" show-overflow-tooltip />
      <el-table-column label="保养状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getMaintenanceTagType(row.maintenanceStatus)" effect="light">
            {{ getMaintenanceLabel(row.maintenanceStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="规格尺寸(mm)" width="200">
        <template #default="{ row }">
          <span>长{{ row.length }} × 宽{{ row.width }} × 厚{{ row.thickness }}</span>
        </template>
      </el-table-column>
      <el-table-column label="实物图" width="100" align="center">
        <template #default="{ row }">
          <el-image
            v-if="row.imagePath"
            :src="row.imagePath"
            :preview-src-list="[row.imagePath]"
            fit="cover"
            style="width: 50px; height: 50px; border-radius: 4px"
          />
          <span v-else style="color: #c0c4cc">无图</span>
        </template>
      </el-table-column>
      <el-table-column label="层位状态" width="180">
        <template #default="{ row }">
          <template v-if="row.borrowStatus === 'BORROWED'">
            <el-tag type="warning" effect="dark">领用离架中</el-tag>
            <div style="font-size: 12px; color: #e6a23c; margin-top: 2px">
              {{ row.borrower }} · {{ row.productionLine }}
            </div>
          </template>
          <template v-else-if="row.shelfLayerCode">
            <el-tag type="success" effect="light">
              {{ row.shelfLayerCode }}
            </el-tag>
            <div style="font-size: 12px; color: #909399; margin-top: 2px">
              {{ row.layerName }}
            </div>
          </template>
          <el-tag v-else type="danger" effect="plain">未绑定</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bindTime" label="绑定时间" width="160">
        <template #default="{ row }">
          {{ row.bindTime ? formatTime(row.bindTime) : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="建档时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.shelfLayerCode && row.borrowStatus !== 'BORROWED' && isPadAvailable(row)"
            link
            type="success"
            size="small"
            @click="handleCheckout(row)"
          >
            领用
          </el-button>
          <el-tooltip
            v-else-if="row.shelfLayerCode && row.borrowStatus !== 'BORROWED'"
            content="待检/停用垫板不可领用"
            placement="top"
          >
            <el-button link type="success" size="small" disabled>领用</el-button>
          </el-tooltip>
          <el-button link type="primary" size="small" @click="handleMaintenance(row)">
            保养
          </el-button>
          <el-button link type="primary" size="small" @click="handleViewRecord(row)">
            调整记录
          </el-button>
          <el-button
            link
            type="primary"
            size="small"
            :disabled="row.borrowStatus === 'BORROWED'"
            @click="handleBind(row)"
          >
            {{ row.shelfLayerCode ? '重分配' : '绑定层位' }}
          </el-button>
          <el-button
            v-if="row.shelfLayerCode"
            link
            type="warning"
            size="small"
            :disabled="row.borrowStatus === 'BORROWED'"
            @click="handleUnbind(row)"
          >
            解绑
          </el-button>
          <el-button
            link
            type="primary"
            size="small"
            :disabled="row.borrowStatus === 'BORROWED'"
            @click="handleEdit(row)"
          >编辑</el-button>
          <el-button
            link
            type="danger"
            size="small"
            :disabled="row.borrowStatus === 'BORROWED'"
            @click="handleDelete(row)"
          >删除</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="640px"
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="垫板编号" prop="padCode">
              <el-input v-model="formData.padCode" placeholder="请输入垫板编号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适配模具" prop="moldType">
              <el-input v-model="formData.moldType" placeholder="请输入适配模具" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="规格模板">
              <el-select v-model="selectedSpec" placeholder="选择模板快速填充" @change="applySpec" clearable>
                <el-option
                  v-for="(spec, key) in specOptions"
                  :key="key"
                  :label="JSON.parse(spec).name"
                  :value="key"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="长度(mm)" prop="length">
              <el-input-number
                v-model="formData.length"
                :min="0"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="宽度(mm)" prop="width">
              <el-input-number
                v-model="formData.width"
                :min="0"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="厚度(mm)" prop="thickness">
              <el-input-number
                v-model="formData.thickness"
                :min="0"
                :precision="2"
                :step="0.5"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="isEdit ? '货架层位' : '初始分层'">
              <el-select
                v-model="formData.shelfLayerCode"
                :placeholder="isEdit ? '修改层位将按绑定规则记录调整' : '建档时可直接绑定分层（可选）'"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="layer in layerOptions"
                  :key="layer.layerCode"
                  :label="layer.layerCode + ' - ' + layer.layerName + layerCapacityLabel(layer)"
                  :value="layer.layerCode"
                  :disabled="isLayerFull(layer) && layer.layerCode !== formData.shelfLayerCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="实物图片">
          <el-upload
            class="image-uploader"
            :show-file-list="false"
            :before-upload="beforeImageUpload"
            :http-request="handleImageUpload"
            accept="image/*"
          >
            <el-image
              v-if="formData.imagePath"
              :src="formData.imagePath"
              fit="cover"
              class="upload-preview"
            />
            <div v-else class="upload-placeholder">
              <el-icon :size="28"><Plus /></el-icon>
              <div>点击上传</div>
            </div>
          </el-upload>
          <div class="upload-tip">支持 JPG/PNG，前端自动压缩，建议尺寸 ≤ 1280px</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="bindDialogVisible"
      :title="bindDialogTitle"
      width="500px"
      destroy-on-close
    >
      <el-form :model="bindForm" label-width="100px">
        <el-form-item label="垫板编号">
          <el-input :model-value="currentPad?.padCode || ''" disabled />
        </el-form-item>
        <el-form-item label="当前层位">
          <el-input
            :value="currentPad?.shelfLayerCode || '未绑定'"
            disabled
          />
        </el-form-item>
        <el-form-item label="新分层" required>
          <el-select
            v-model="bindForm.layerCode"
            placeholder="请选择目标货架分层"
            style="width: 100%"
          >
            <el-option
              v-for="layer in layerOptions"
              :key="layer.layerCode"
              :label="layer.layerCode + ' - ' + layer.layerName + layerCapacityLabel(layer)"
              :value="layer.layerCode"
              :disabled="isLayerFull(layer) && layer.layerCode !== currentPad?.shelfLayerCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="bindForm.operator" placeholder="请输入操作人姓名" />
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input
            v-model="bindForm.adjustReason"
            type="textarea"
            :rows="2"
            placeholder="请输入调整原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleBindSubmit">确定绑定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recordDialogVisible" title="层位调整记录" width="760px">
      <el-table :data="padRecords" border size="small">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="调整类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getAdjustTagType(row.adjustType)" size="small">
              {{ getAdjustTypeLabel(row.adjustType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="oldLayerCode" label="原层位" width="120">
          <template #default="{ row }">{{ row.oldLayerCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="newLayerCode" label="新层位" width="120">
          <template #default="{ row }">{{ row.newLayerCode || '已解绑' }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="adjustReason" label="原因" show-overflow-tooltip />
        <el-table-column prop="adjustTime" label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.adjustTime) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPadPage,
  createPad,
  updatePad,
  deletePad,
  bindLayer,
  unbindLayer,
  getPadRecords,
  exportPadsByLayer,
  exportAllPads
} from '@/api/pad'
import { getShelfLayerList } from '@/api/shelf'
import { getAllSpecs } from '@/api/spec'
import { uploadImage } from '@/api/file'
import { compressImage } from '@/utils/imageCompress'
import { downloadBlob, getFileNameFromDisposition } from '@/utils/download'
import dayjs from 'dayjs'

const loading = ref(false)
const tableData = ref([])
const layerOptions = ref([])
const specOptions = ref({})
const selectedSpec = ref('')
const shelfOptions = ref([])

const searchForm = reactive({
  padCode: '',
  moldType: '',
  shelfCode: '',
  shelfLayerCode: '',
  maintenanceStatus: ''
})

// 查询区的层位选项按所选货架级联，避免货架编号与层位编码矛盾导致恒空结果；
// 表单内的建档/编辑/重分配仍使用完整 layerOptions
const filterLayerOptions = computed(() => {
  if (!searchForm.shelfCode) return layerOptions.value
  return layerOptions.value.filter((l) => l.shelfCode === searchForm.shelfCode)
})

const handleShelfFilterChange = () => {
  // 切换货架后，清空不属于该货架的层位条件，保证货架编号、层位编码与垫板列表一致
  if (
    searchForm.shelfLayerCode &&
    !filterLayerOptions.value.some((l) => l.layerCode === searchForm.shelfLayerCode)
  ) {
    searchForm.shelfLayerCode = ''
  }
}

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const isEdit = ref(false)
const formData = reactive({
  id: null,
  padCode: '',
  moldType: '',
  length: null,
  width: null,
  thickness: null,
  imagePath: '',
  shelfLayerCode: '',
  remark: ''
})

const formRules = {
  padCode: [{ required: true, message: '请输入垫板编号', trigger: 'blur' }]
}

const bindDialogVisible = ref(false)
const bindDialogTitle = ref('')
const currentPad = ref(null)
const bindForm = reactive({
  padId: null,
  layerCode: '',
  operator: '',
  adjustReason: ''
})

const recordDialogVisible = ref(false)
const padRecords = ref([])

const router = useRouter()
const goImport = () => router.push('/pad/import')

const handleCheckout = (row) => {
  router.push({ path: '/borrow', query: { padId: row.id } })
}

// 保养台账：可用/待检/停用，仅可用垫板允许领用
const MAINTENANCE_LABELS = { AVAILABLE: '可用', PENDING: '待检', DISABLED: '停用' }
const getMaintenanceLabel = (status) => MAINTENANCE_LABELS[status] || '可用'
const getMaintenanceTagType = (status) => {
  if (status === 'AVAILABLE' || !status) return 'success'
  if (status === 'PENDING') return 'warning'
  return 'danger'
}
const isPadAvailable = (pad) => !pad.maintenanceStatus || pad.maintenanceStatus === 'AVAILABLE'

const handleMaintenance = (row) => {
  router.push({ path: '/maintenance', query: { padId: row.id, padCode: row.padCode } })
}

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

// 层位容量：达到配额视为已满，不可再作为绑定/建档目标（当前已绑定层位除外，便于编辑时保留原值）
const isLayerFull = (layer) => (layer.padCount || 0) >= (layer.capacity ?? 0)
const layerCapacityLabel = (layer) => {
  const used = layer.padCount || 0
  const capacity = layer.capacity ?? 0
  return isLayerFull(layer) ? `（已满 ${used}/${capacity}）` : `（占用 ${used}/${capacity}）`
}

const getAdjustTypeLabel = (type) => {
  const map = { BIND: '初始绑定', REBIND: '变更绑定', UNBIND: '解除绑定' }
  return map[type] || type
}

const getAdjustTagType = (type) => {
  const map = { BIND: 'success', REBIND: 'warning', UNBIND: 'danger' }
  return map[type] || 'info'
}

const loadData = async () => {
  loading.value = true
  try {
    const params = { ...searchForm, ...pagination }
    // 清理空白查询条件，避免空串/空格造成无结果
    ;['padCode', 'moldType', 'shelfCode', 'shelfLayerCode'].forEach((key) => {
      if (typeof params[key] === 'string') {
        params[key] = params[key].trim()
        if (!params[key]) delete params[key]
      }
    })
    if (!params.maintenanceStatus) delete params.maintenanceStatus
    const data = await getPadPage(params)
    tableData.value = data.records || []
    pagination.total = data.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const loadLayers = async () => {
  try {
    const data = await getShelfLayerList()
    layerOptions.value = data
    const set = new Set(data.map((l) => l.shelfCode))
    shelfOptions.value = [...set]
  } catch (e) {
    console.error(e)
  }
}

const loadSpecs = async () => {
  try {
    specOptions.value = await getAllSpecs()
  } catch (e) {
    console.error(e)
  }
}

const applySpec = (key) => {
  const specStr = specOptions.value[key]
  if (specStr) {
    try {
      const spec = JSON.parse(specStr)
      if (spec.length) formData.length = spec.length
      if (spec.width) formData.width = spec.width
      if (spec.thickness) formData.thickness = spec.thickness
    } catch (e) {}
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, {
    padCode: '',
    moldType: '',
    shelfCode: '',
    shelfLayerCode: '',
    maintenanceStatus: ''
  })
  handleSearch()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新建垫板档案'
  Object.assign(formData, {
    id: null,
    padCode: '',
    moldType: '',
    length: null,
    width: null,
    thickness: null,
    imagePath: '',
    shelfLayerCode: '',
    remark: ''
  })
  selectedSpec.value = ''
  loadLayers()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑垫板档案'
  Object.assign(formData, JSON.parse(JSON.stringify(row)))
  selectedSpec.value = ''
  loadLayers()
  dialogVisible.value = true
}

const handleDialogClosed = () => {
  formRef.value?.resetFields()
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  try {
    if (isEdit.value) {
      await updatePad({ ...formData })
      ElMessage.success('更新成功')
    } else {
      await createPad({ ...formData })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
    loadLayers()
  } catch (e) {
    console.error(e)
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除垫板【${row.padCode}】吗？`, '提示', {
      type: 'warning'
    })
    await deletePad(row.id)
    ElMessage.success('删除成功')
    loadData()
    loadLayers()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleBind = (row) => {
  currentPad.value = row
  bindDialogTitle.value = row.shelfLayerCode ? '层位重分配' : '绑定货架分层'
  Object.assign(bindForm, {
    padId: row.id,
    layerCode: '',
    operator: '',
    adjustReason: ''
  })
  loadLayers()
  bindDialogVisible.value = true
}

const handleUnbind = async (row) => {
  try {
    const { value: formValues } = await ElMessageBox.prompt(
      '请输入调整原因',
      `解绑【${row.padCode}】层位`,
      {
        confirmButtonText: '确定解绑',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入调整原因',
        type: 'warning'
      }
    )
    await unbindLayer({
      padId: row.id,
      operator: '管理员',
      adjustReason: formValues || '车间布局调整'
    })
    ElMessage.success('解绑成功')
    loadData()
    loadLayers()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleBindSubmit = async () => {
  if (!bindForm.layerCode) {
    ElMessage.warning('请选择目标分层')
    return
  }
  try {
    await bindLayer({ ...bindForm })
    ElMessage.success('绑定成功')
    bindDialogVisible.value = false
    loadData()
    loadLayers()
  } catch (e) {
    console.error(e)
  }
}

const handleViewRecord = async (row) => {
  try {
    padRecords.value = await getPadRecords(row.id)
    recordDialogVisible.value = true
  } catch (e) {
    console.error(e)
  }
}

const beforeImageUpload = async (file) => {
  try {
    const compressed = await compressImage(file, {
      maxWidth: 1280,
      maxHeight: 1280,
      quality: 0.8,
      maxSize: 1024 * 1024
    })
    return compressed
  } catch (e) {
    ElMessage.error(e.message)
    return false
  }
}

const handleImageUpload = async ({ file }) => {
  try {
    const res = await uploadImage(file)
    formData.imagePath = res.url
    ElMessage.success('图片上传成功')
  } catch (e) {
    console.error(e)
  }
}

const handleExportAll = async () => {
  try {
    const res = await exportAllPads()
    const filename = getFileNameFromDisposition(res.headers['content-disposition'])
    downloadBlob(res.data, filename || '全部垫板档案清单.xlsx')
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  loadData()
  loadLayers()
  loadSpecs()
})
</script>

<style lang="scss" scoped>
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.image-uploader {
  :deep(.el-upload) {
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    width: 120px;
    height: 120px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: border-color 0.2s;

    &:hover {
      border-color: #409eff;
    }
  }

  .upload-preview {
    width: 120px;
    height: 120px;
  }

  .upload-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    color: #8c939d;
    font-size: 12px;
  }
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
