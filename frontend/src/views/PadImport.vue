<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">垫板档案批量导入</h2>
    </div>

    <el-steps :active="activeStep" align-center finish-status="success" class="import-steps">
      <el-step title="上传 Excel" />
      <el-step title="预览校验" />
      <el-step title="导入结果" />
    </el-steps>

    <!-- 第一步：上传文件 -->
    <div v-show="activeStep === 0" class="step-panel">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>第一步：上传垫板档案 Excel</span>
            <el-button link type="primary" :loading="templateLoading" @click="handleDownloadTemplate">
              <el-icon><Download /></el-icon>下载导入模板
            </el-button>
          </div>
        </template>

        <el-upload
          ref="uploadRef"
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept=".xlsx,.xls"
          :on-change="handleFileChange"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            将 Excel 文件拖到此处，或<em>点击选择文件</em>
          </div>
          <template #tip>
            <div class="upload-tip">
              仅支持 .xlsx / .xls 文件，单次最多 {{ MAX_ROWS }} 行；列顺序请与模板一致：
              垫板编号、适配模具、长度(mm)、宽度(mm)、厚度(mm)、初始层位、备注
            </div>
          </template>
        </el-upload>

        <div v-if="selectedFile" class="selected-file">
          <el-icon color="#409eff"><Document /></el-icon>
          <span class="file-name">{{ selectedFile.name }}</span>
          <el-tag type="info" size="small">{{ formatFileSize(selectedFile.size) }}</el-tag>
          <el-button link type="danger" size="small" @click="clearFile">重新选择</el-button>
        </div>

        <div class="step-actions">
          <el-button @click="goPadList">返回档案列表</el-button>
          <el-button type="primary" :loading="previewLoading" :disabled="!selectedFile" @click="handlePreview">
            上传并预览校验
          </el-button>
        </div>
      </el-card>

      <el-card shadow="never" class="rule-card">
        <template #header>
          <span>校验与导入规则</span>
        </template>
        <ul class="rule-list">
          <li><b>垫板编号</b>为必填项，且不可与系统已有编号或文件内其他行重复。</li>
          <li><b>长、宽、厚</b>为选填，填写时必须为大于 0 的数字，最多保留 2 位小数。</li>
          <li><b>初始层位</b>为选填，填写时必须是系统中已存在的货架分层编码（如 A-01-01），导入合格后自动建立初始绑定记录。</li>
          <li>初始层位受<b>容量配额</b>限制：目标层位在架数达到配额后，对应行将导入失败并提示层位已满，可调整层位后重新导入。</li>
          <li><b>封锁中</b>的层位（破损/清扫/检修）不可导入占位，对应行将校验失败，解除封锁后可重新导入。</li>
          <li>预览阶段逐行标记错误，<b>仅合格行会被导入</b>；不合格行修正后可重新上传。</li>
          <li>确认导入时后端会再次校验，单行导入失败只影响该行，不影响其他合格数据。</li>
        </ul>
      </el-card>
    </div>

    <!-- 第二步：预览校验 -->
    <div v-show="activeStep === 1" class="step-panel">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>第二步：预览与校验结果（共 {{ previewRows.length }} 行）</span>
            <el-radio-group v-model="previewFilter" size="small">
              <el-radio-button label="all">全部 {{ previewRows.length }}</el-radio-button>
              <el-radio-button label="valid">合格 {{ validCount }}</el-radio-button>
              <el-radio-button label="invalid">异常 {{ invalidCount }}</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <el-alert
          class="preview-alert"
          :title="`校验通过 ${validCount} 行，校验失败 ${invalidCount} 行，将仅导入合格行`"
          :type="invalidCount > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
        />

        <el-table
          :data="filteredPreviewRows"
          border
          stripe
          height="460"
          :row-class-name="previewRowClassName"
        >
          <el-table-column label="行号" width="70" align="center">
            <template #default="{ row }">{{ row.rowNum }}</template>
          </el-table-column>
          <el-table-column label="校验" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.valid ? 'success' : 'danger'" size="small">
                {{ row.valid ? '合格' : '异常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="padCode" label="垫板编号" width="150" show-overflow-tooltip />
          <el-table-column prop="moldType" label="适配模具" width="160" show-overflow-tooltip />
          <el-table-column label="长度(mm)" width="100" align="center">
            <template #default="{ row }">{{ row.length || '-' }}</template>
          </el-table-column>
          <el-table-column label="宽度(mm)" width="100" align="center">
            <template #default="{ row }">{{ row.width || '-' }}</template>
          </el-table-column>
          <el-table-column label="厚度(mm)" width="100" align="center">
            <template #default="{ row }">{{ row.thickness || '-' }}</template>
          </el-table-column>
          <el-table-column prop="shelfLayerCode" label="初始层位" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.shelfLayerCode || '不绑定' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" width="160" show-overflow-tooltip />
          <el-table-column label="错误信息" min-width="220">
            <template #default="{ row }">
              <span :class="{ 'error-text': !row.valid }">{{ row.valid ? '-' : row.errorMessage }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="step-actions">
          <el-button @click="backToUpload">上一步</el-button>
          <el-button type="primary" :loading="importLoading" :disabled="validCount === 0" @click="handleConfirmImport">
            确认导入 {{ validCount }} 行合格数据
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 第三步：导入结果 -->
    <div v-show="activeStep === 2" class="step-panel">
      <el-card shadow="never">
        <template #header>
          <span>第三步：导入完成</span>
        </template>

        <el-result
          :icon="importResult.failCount > 0 ? 'warning' : 'success'"
          :title="importResult.failCount > 0 ? '导入完成（存在失败行）' : '全部导入成功'"
          :subTitle="`成功 ${importResult.successCount} 行，失败 ${importResult.failCount} 行`"
        />

        <el-tabs v-model="resultTab">
          <el-tab-pane :label="`成功明细 (${importResult.successCount})`" name="success">
            <el-table :data="importResult.successRows" border stripe height="360">
              <el-table-column label="行号" width="70" align="center">
                <template #default="{ row }">{{ row.rowNum }}</template>
              </el-table-column>
              <el-table-column prop="padCode" label="垫板编号" width="150" />
              <el-table-column prop="moldType" label="适配模具" width="180" show-overflow-tooltip />
              <el-table-column label="长×宽×厚(mm)" min-width="200">
                <template #default="{ row }">
                  {{ row.length || '-' }} × {{ row.width || '-' }} × {{ row.thickness || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="shelfLayerCode" label="初始层位" width="140">
                <template #default="{ row }">{{ row.shelfLayerCode || '不绑定' }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`失败明细 (${importResult.failCount})`" name="fail">
            <el-table
              :data="importResult.failRows"
              border
              stripe
              height="360"
              :row-class-name="previewRowClassName"
            >
              <el-table-column label="行号" width="70" align="center">
                <template #default="{ row }">{{ row.rowNum }}</template>
              </el-table-column>
              <el-table-column prop="padCode" label="垫板编号" width="150" />
              <el-table-column prop="moldType" label="适配模具" width="160" show-overflow-tooltip />
              <el-table-column prop="shelfLayerCode" label="初始层位" width="130" />
              <el-table-column label="失败原因" min-width="240">
                <template #default="{ row }">
                  <span class="error-text">{{ row.errorMessage }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>

        <div class="step-actions">
          <el-button @click="resetAll">继续导入</el-button>
          <el-button type="primary" @click="goPadList">查看档案列表</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { previewImportPads, confirmImportPads, downloadImportTemplate } from '@/api/pad'
import { downloadBlob } from '@/utils/download'

const router = useRouter()

const MAX_ROWS = 2000

const activeStep = ref(0)
const templateLoading = ref(false)
const previewLoading = ref(false)
const importLoading = ref(false)
const uploadRef = ref(null)

const selectedFile = ref(null)
const previewRows = ref([])
const previewFilter = ref('all')

const resultTab = ref('success')
const importResult = ref({
  successCount: 0,
  failCount: 0,
  successRows: [],
  failRows: []
})

const validCount = computed(() => previewRows.value.filter((row) => row.valid).length)
const invalidCount = computed(() => previewRows.value.filter((row) => !row.valid).length)

const filteredPreviewRows = computed(() => {
  if (previewFilter.value === 'valid') {
    return previewRows.value.filter((row) => row.valid)
  }
  if (previewFilter.value === 'invalid') {
    return previewRows.value.filter((row) => !row.valid)
  }
  return previewRows.value
})

const previewRowClassName = ({ row }) => (row.valid ? '' : 'error-row')

const formatFileSize = (size) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

const handleFileChange = (uploadFile) => {
  const file = uploadFile.raw
  if (!file) return
  const name = file.name.toLowerCase()
  if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
    ElMessage.error('仅支持 .xlsx / .xls 格式的 Excel 文件')
    uploadRef.value?.clearFiles()
    return
  }
  selectedFile.value = file
  uploadRef.value?.clearFiles()
}

const clearFile = () => {
  selectedFile.value = null
  uploadRef.value?.clearFiles()
}

const handlePreview = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  previewLoading.value = true
  try {
    const data = await previewImportPads(selectedFile.value)
    previewRows.value = data || []
    previewFilter.value = 'all'
    activeStep.value = 1
  } catch (e) {
    console.error(e)
  } finally {
    previewLoading.value = false
  }
}

const handleConfirmImport = async () => {
  const validRows = previewRows.value.filter((row) => row.valid)
  if (validRows.length === 0) {
    ElMessage.warning('没有可导入的合格数据')
    return
  }
  importLoading.value = true
  try {
    const result = await confirmImportPads(validRows)
    importResult.value = result
    resultTab.value = result.failCount > 0 ? 'fail' : 'success'
    activeStep.value = 2
  } catch (e) {
    console.error(e)
  } finally {
    importLoading.value = false
  }
}

const handleDownloadTemplate = async () => {
  templateLoading.value = true
  try {
    const res = await downloadImportTemplate()
    downloadBlob(res.data, '垫板档案批量导入模板.xlsx')
  } catch (e) {
    console.error(e)
  } finally {
    templateLoading.value = false
  }
}

const backToUpload = () => {
  activeStep.value = 0
}

const resetAll = () => {
  selectedFile.value = null
  previewRows.value = []
  previewFilter.value = 'all'
  importResult.value = { successCount: 0, failCount: 0, successRows: [], failRows: [] }
  activeStep.value = 0
}

const goPadList = () => {
  router.push('/pad')
}
</script>

<style lang="scss" scoped>
.import-steps {
  max-width: 720px;
  margin: 8px auto 24px;
}

.step-panel {
  max-width: 1200px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  line-height: 1.6;
}

.selected-file {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 14px;
  background: #f5f7fa;
  border-radius: 6px;

  .file-name {
    font-weight: 500;
    color: #303133;
  }
}

.rule-card {
  margin-top: 16px;
}

.rule-list {
  margin: 0;
  padding-left: 20px;
  color: #606266;
  font-size: 13px;
  line-height: 2;
}

.preview-alert {
  margin-bottom: 12px;
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}

.error-text {
  color: #f56c6c;
}

:deep(.el-table .error-row) {
  background-color: #fef0f0;

  &:hover > td {
    background-color: #fde2e2 !important;
  }
}
</style>
