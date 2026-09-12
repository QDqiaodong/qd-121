<template>
  <div class="shelf-page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">货架层位管理</h2>
        <div class="header-actions">
          <el-button @click="loadData">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
          <el-button type="primary" @click="handleAddLayer">
            <el-icon><Plus /></el-icon>新增分层
          </el-button>
        </div>
      </div>

      <div class="toolbar" v-if="shelfGroups.length > 0">
        <el-tabs v-model="activeShelf" @tab-change="handleShelfChange">
          <el-tab-pane
            v-for="shelf in shelfGroups"
            :key="shelf.shelfCode"
            :label="shelf.shelfCode + ' 货架（' + shelf.totalCount + '块）'"
            :name="shelf.shelfCode"
          />
        </el-tabs>
      </div>

      <div v-loading="loading" class="layer-card-grid">
        <div
          v-for="layer in currentLayers"
          :key="layer.layerCode"
          class="layer-card"
          :class="{ 'layer-blocked': layer.activeBlock }"
        >
          <div class="layer-card-header">
            <div class="layer-title">
              <el-icon :size="20" color="#1e3a8a"><Collection /></el-icon>
              <div>
                <div class="layer-code">
                  {{ layer.layerCode }}
                  <el-tag v-if="layer.activeBlock" type="danger" size="small" effect="dark">
                    封锁中
                  </el-tag>
                </div>
                <div class="layer-name">{{ layer.layerName }}</div>
              </div>
            </div>
            <el-dropdown @command="(cmd) => handleCardAction(cmd, layer)">
              <el-button type="primary" link size="small">
                操作 <el-icon><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="view">查看垫板详情</el-dropdown-item>
                  <el-dropdown-item command="export">导出本层清单</el-dropdown-item>
                  <el-dropdown-item command="edit">编辑分层</el-dropdown-item>
                  <el-dropdown-item v-if="!layer.activeBlock" command="block" divided>
                    登记封锁
                  </el-dropdown-item>
                  <el-dropdown-item v-else command="release" divided>
                    解除封锁
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除分层</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <div v-if="layer.activeBlock" class="block-banner">
            <el-icon><Lock /></el-icon>
            <span>
              {{ getBlockTypeLabel(layer.activeBlock.blockType) }}：{{ layer.activeBlock.blockReason }}
              （{{ formatTime(layer.activeBlock.startTime) }} 起 · 经办人 {{ layer.activeBlock.operator }}）
            </span>
          </div>

          <div class="layer-card-body">
            <div class="stat-row">
              <div class="stat-item">
                <span class="stat-num">{{ layer.padCount || 0 }}</span>
                <span class="stat-label">/ 容量 {{ layer.capacity ?? 0 }}</span>
              </div>
              <div class="stat-item">
                <el-tag v-if="layer.activeBlock" type="danger" size="large" effect="dark">
                  封锁中
                </el-tag>
                <el-tag v-else :type="getCapacityType(layer)" size="large">
                  {{ getCapacityLabel(layer) }}
                </el-tag>
              </div>
            </div>
            <el-progress
              :percentage="getUsagePercent(layer)"
              :status="getUsageStatus(layer)"
              :stroke-width="8"
              class="capacity-progress"
            />

            <div class="pad-preview">
              <div v-if="layer.padList && layer.padList.length > 0" class="pad-grid">
                <div
                  v-for="pad in layer.padList.slice(0, 6)"
                  :key="pad.id"
                  class="pad-item"
                  @click="handleViewPad(pad)"
                >
                  <div class="pad-image">
                    <el-image
                      v-if="pad.imagePath"
                      :src="pad.imagePath"
                      fit="cover"
                      style="width: 100%; height: 100%"
                    />
                    <div v-else class="pad-no-image">
                      <el-icon :size="20"><Picture /></el-icon>
                    </div>
                  </div>
                  <div class="pad-code">{{ pad.padCode }}</div>
                </div>
                <div
                  v-if="(layer.padCount || 0) > 6"
                  class="pad-item pad-more"
                  @click="handleViewLayer(layer)"
                >
                  <span>+{{ (layer.padCount || 0) - 6 }}</span>
                </div>
              </div>
              <div v-else class="empty-layer">
                <el-icon :size="40" color="#dcdfe6"><Box /></el-icon>
                <div>暂无垫板</div>
                <el-tooltip
                  v-if="layer.activeBlock"
                  content="层位封锁中，解除封锁后才能上架垫板"
                  placement="top"
                >
                  <el-button type="info" link size="small" disabled>
                    添加垫板到此层
                  </el-button>
                </el-tooltip>
                <el-button v-else type="primary" link size="small" @click="goToAddPad(layer)">
                  添加垫板到此层
                </el-button>
              </div>
            </div>
          </div>

          <div class="layer-card-footer">
            <el-button size="small" @click="handleViewLayer(layer)">
              <el-icon><View /></el-icon>查看全部
            </el-button>
            <el-button type="success" size="small" @click="handleExportLayer(layer)">
              <el-icon><Download /></el-icon>导出清单
            </el-button>
          </div>
        </div>

        <el-empty
          v-if="!loading && shelfGroups.length === 0"
          description="暂无货架分层数据"
          class="shelf-empty"
        >
          <el-button type="primary" @click="handleAddLayer">
            <el-icon><Plus /></el-icon>新增分层
          </el-button>
        </el-empty>
        <el-empty
          v-else-if="!loading && currentLayers.length === 0"
          description="当前货架下暂无层位"
          class="shelf-empty"
        />
      </div>
    </div>

    <el-dialog v-model="layerDialogVisible" :title="layerDialogTitle" width="480px" destroy-on-close>
      <el-form :model="layerForm" label-width="100px" ref="layerFormRef">
        <el-form-item label="分层编码" required>
          <el-input v-model="layerForm.layerCode" :disabled="isLayerEdit" placeholder="如：A-01-01" />
        </el-form-item>
        <el-form-item label="货架编码" required>
          <el-input v-model="layerForm.shelfCode" placeholder="如：A-01" />
        </el-form-item>
        <el-form-item label="分层名称">
          <el-input v-model="layerForm.layerName" placeholder="如：A区01货架第1层" />
        </el-form-item>
        <el-form-item label="层序号">
          <el-input-number v-model="layerForm.layerOrder" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="容量配额" required>
          <el-input-number v-model="layerForm.capacity" :min="0" :max="9999" controls-position="right" />
          <div class="form-tip">该层最多可存放的在架垫板数；下调时不得低于当前在架数</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="layerForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="layerDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleLayerSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="padListDialogVisible"
      :title="`${currentViewLayer?.layerCode} - 垫板清单`"
      width="900px"
    >
      <div class="dialog-toolbar">
        <div>
          <el-tag type="info">
            在架 {{ currentViewLayer?.padCount || 0 }} / 容量 {{ currentViewLayer?.capacity ?? 0 }}
          </el-tag>
        </div>
        <el-button type="success" size="small" @click="handleExportLayer(currentViewLayer)">
          <el-icon><Download /></el-icon>导出本层
        </el-button>
      </div>
      <el-table :data="currentViewLayer?.padList || []" border size="default">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="实物图" width="80" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.imagePath"
              :src="row.imagePath"
              :preview-src-list="[row.imagePath]"
              fit="cover"
              style="width: 44px; height: 44px; border-radius: 4px"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="padCode" label="垫板编号" width="130" />
        <el-table-column prop="moldType" label="适配模具" show-overflow-tooltip />
        <el-table-column label="规格(mm)" width="180">
          <template #default="{ row }">
            长{{ row.length }} × 宽{{ row.width }} × 厚{{ row.thickness }}
          </template>
        </el-table-column>
        <el-table-column prop="bindTime" label="绑定时间" width="160">
          <template #default="{ row }">{{ formatTime(row.bindTime) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getShelfLayerGroup, saveShelfLayer, deleteShelfLayer } from '@/api/shelf'
import { exportPadsByLayer } from '@/api/pad'
import { downloadBlob, getFileNameFromDisposition } from '@/utils/download'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const allLayers = ref([])
const activeShelf = ref('')

const layerDialogVisible = ref(false)
const layerDialogTitle = ref('')
const isLayerEdit = ref(false)
const layerFormRef = ref(null)
const layerForm = reactive({
  id: null,
  layerCode: '',
  shelfCode: '',
  layerName: '',
  layerOrder: 1,
  capacity: 10,
  remark: ''
})

const padListDialogVisible = ref(false)
const currentViewLayer = ref(null)

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

// 层位封锁类型：与封锁台账字典一致
const BLOCK_TYPE_LABELS = { DAMAGE: '层位破损', CLEANING: '待清扫', MAINTENANCE: '检修中', OTHER: '其他' }
const getBlockTypeLabel = (type) => BLOCK_TYPE_LABELS[type] || type

const shelfGroups = computed(() => {
  const map = {}
  allLayers.value.forEach((layer) => {
    if (!map[layer.shelfCode]) {
      map[layer.shelfCode] = { shelfCode: layer.shelfCode, totalCount: 0 }
    }
    map[layer.shelfCode].totalCount += layer.padCount || 0
  })
  return Object.values(map)
})

const currentLayers = computed(() => {
  if (!activeShelf.value) return []
  return allLayers.value.filter((l) => l.shelfCode === activeShelf.value)
})

// 容量占用按配额计算：0 空闲，<80% 有余量，<100% 将满，达到配额为已满
const getUsagePercent = (layer) => {
  const capacity = layer.capacity || 0
  if (capacity <= 0) return (layer.padCount || 0) > 0 ? 100 : 0
  return Math.min(Math.round(((layer.padCount || 0) / capacity) * 100), 100)
}

const getUsageStatus = (layer) => {
  const percent = getUsagePercent(layer)
  if (percent >= 100) return 'exception'
  if (percent >= 80) return 'warning'
  return 'success'
}

const getCapacityType = (layer) => {
  const count = layer.padCount || 0
  if (count === 0) return 'info'
  const percent = getUsagePercent(layer)
  if (percent >= 100) return 'danger'
  if (percent >= 80) return 'warning'
  return 'success'
}

const getCapacityLabel = (layer) => {
  const count = layer.padCount || 0
  if (count === 0) return '空闲'
  const percent = getUsagePercent(layer)
  if (percent >= 100) return '已满'
  if (percent >= 80) return '将满'
  return '有余量'
}

const loadData = async () => {
  loading.value = true
  try {
    allLayers.value = (await getShelfLayerGroup()) || []
    // 数据刷新后校正选中货架：当前选中不存在（货架被删/领用离架空架）时回退到第一个货架
    const codes = shelfGroups.value.map((g) => g.shelfCode)
    if (!activeShelf.value || !codes.includes(activeShelf.value)) {
      activeShelf.value = codes[0] || ''
    }
  } catch (e) {
    console.error(e)
    allLayers.value = []
    activeShelf.value = ''
  } finally {
    loading.value = false
  }
}

const handleShelfChange = () => {
  // tab 切换由 v-model 驱动 currentLayers 自动筛选，此处保留用于后续扩展
}

const handleAddLayer = () => {
  isLayerEdit.value = false
  layerDialogTitle.value = '新增货架分层'
  Object.assign(layerForm, {
    id: null,
    layerCode: '',
    shelfCode: activeShelf.value || '',
    layerName: '',
    layerOrder: 1,
    capacity: 10,
    remark: ''
  })
  layerDialogVisible.value = true
}

const handleLayerSubmit = async () => {
  if (layerForm.capacity == null || layerForm.capacity < 0) {
    ElMessage.warning('容量配额必须为不小于 0 的整数')
    return
  }
  if (isLayerEdit.value) {
    const used = allLayers.value.find((l) => l.id === layerForm.id)?.padCount || 0
    if (layerForm.capacity < used) {
      ElMessage.warning(`容量配额不能低于当前在架数（${used} 块）`)
      return
    }
  }
  try {
    await saveShelfLayer({ ...layerForm })
    ElMessage.success(isLayerEdit.value ? '更新成功' : '新增成功')
    layerDialogVisible.value = false
    loadData()
  } catch (e) {
    console.error(e)
  }
}

const handleViewLayer = (layer) => {
  currentViewLayer.value = layer
  padListDialogVisible.value = true
}

const handleViewPad = (pad) => {
  ElMessage.info(`垫板编号：${pad.padCode}，适配模具：${pad.moldType || '无'}`)
}

const goToAddPad = (layer) => {
  router.push('/pad')
}

const handleExportLayer = async (layer) => {
  if (!layer) return
  try {
    const res = await exportPadsByLayer(layer.layerCode)
    const filename = getFileNameFromDisposition(res.headers['content-disposition'])
    downloadBlob(res.data, filename || `层位_${layer.layerCode}_垫板清单.xlsx`)
  } catch (e) {
    console.error(e)
  }
}

const handleCardAction = (cmd, layer) => {
  switch (cmd) {
    case 'view':
      handleViewLayer(layer)
      break
    case 'export':
      handleExportLayer(layer)
      break
    case 'edit':
      isLayerEdit.value = true
      layerDialogTitle.value = '编辑分层'
      Object.assign(layerForm, JSON.parse(JSON.stringify(layer)))
      layerDialogVisible.value = true
      break
    case 'block':
      // 跳转封锁台账并预填本层，登记原因/开始时间/经办人
      router.push({ path: '/layer-block', query: { layerCode: layer.layerCode } })
      break
    case 'release':
      // 跳转折锁台账查看本层封锁记录并解除（解除必须填写结论）
      router.push({ path: '/layer-block', query: { layerCode: layer.layerCode, status: 'BLOCKED' } })
      break
    case 'delete':
      handleDeleteLayer(layer)
      break
  }
}

const handleDeleteLayer = async (layer) => {
  try {
    await ElMessageBox.confirm(
      `确定删除分层【${layer.layerCode}】吗？若该层下有垫板或处于封锁中将无法删除。`,
      '提示',
      { type: 'warning' }
    )
    await deleteShelfLayer(layer.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.shelf-page {
  .header-actions {
    display: flex;
    gap: 8px;
  }

  .toolbar {
    margin-bottom: 20px;
  }

  .layer-card-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 16px;
  }

  .shelf-empty {
    grid-column: 1 / -1;
    background: #fff;
    border-radius: 10px;
    padding: 40px 0;
  }

  .layer-card {
    border: 1px solid #ebeef5;
    border-radius: 10px;
    overflow: hidden;
    background: #fff;
    transition: all 0.2s;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
      border-color: #409eff40;
    }

    &.layer-blocked {
      border-color: #f56c6c80;

      .layer-card-header {
        background: linear-gradient(135deg, #fef0f0 0%, #fde2e2 100%);
      }
    }

    .block-banner {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      background: #fef0f0;
      color: #f56c6c;
      font-size: 12px;
      line-height: 1.5;
      border-bottom: 1px solid #fde2e2;
    }

    .layer-card-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      padding: 14px 16px;
      background: linear-gradient(135deg, #f0f7ff 0%, #e6f0ff 100%);
      border-bottom: 1px solid #ebeef5;

      .layer-title {
        display: flex;
        align-items: center;
        gap: 10px;

        .layer-code {
          font-size: 16px;
          font-weight: 700;
          color: #1e3a8a;
        }
        .layer-name {
          font-size: 12px;
          color: #606266;
          margin-top: 2px;
        }
      }
    }

    .layer-card-body {
      padding: 14px 16px;

      .capacity-progress {
        margin-bottom: 12px;
      }

      .stat-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 14px;

        .stat-item {
          display: flex;
          align-items: center;
          gap: 8px;

          .stat-num {
            font-size: 28px;
            font-weight: 700;
            color: #2563eb;
            line-height: 1;
          }
          .stat-label {
            font-size: 12px;
            color: #909399;
          }
        }
      }

      .pad-preview {
        .pad-grid {
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          gap: 8px;

          .pad-item {
            cursor: pointer;
            border: 1px solid #ebeef5;
            border-radius: 6px;
            overflow: hidden;
            transition: all 0.15s;

            &:hover {
              border-color: #409eff;
              background: #f0f7ff;
            }

            .pad-image {
              width: 100%;
              height: 60px;
              background: #f5f7fa;
              display: flex;
              align-items: center;
              justify-content: center;

              .pad-no-image {
                color: #c0c4cc;
              }
            }

            .pad-code {
              font-size: 11px;
              text-align: center;
              padding: 4px;
              color: #606266;
              background: #fafafa;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
            }

            &.pad-more {
              display: flex;
              align-items: center;
              justify-content: center;
              background: #f5f7fa;
              color: #909399;
              font-size: 14px;
              font-weight: 600;
              min-height: 90px;
            }
          }
        }

        .empty-layer {
          padding: 20px 0;
          text-align: center;
          color: #c0c4cc;
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 8px;
          font-size: 13px;
        }
      }
    }

    .layer-card-footer {
      display: flex;
      gap: 8px;
      padding: 10px 16px;
      border-top: 1px solid #ebeef5;
      background: #fafafa;
      justify-content: flex-end;
    }
  }

  .dialog-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
  }

  .form-tip {
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
    width: 100%;
  }
}
</style>
