<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">交班盘点</h2>
      <el-button type="primary" @click="openCreateDialog()">
        <el-icon><DocumentAdd /></el-icon>新建盘点单
      </el-button>
    </div>

    <el-alert
      class="flow-tip"
      type="info"
      :closable="false"
      show-icon
      title="交班时按货架层清点在架垫板：开单快照账目清单 → 逐块标记相符/缺失、补录多出 → 提交（有差异须登记差异原因）→ 差异处理完毕后闭环。未闭环层位禁止归还上架。"
    />

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <div class="stat-card card-progress" @click="quickFilter('IN_PROGRESS')">
          <div class="stat-icon"><el-icon :size="28"><Loading /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">盘点中</div>
            <div class="stat-value">{{ stats.inProgressCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-unclosed" @click="quickFilter('SUBMITTED')">
          <div class="stat-icon"><el-icon :size="28"><Warning /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">待闭环（未平账）</div>
            <div class="stat-value">{{ stats.unclosedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-closed" @click="quickFilter('CLOSED')">
          <div class="stat-icon"><el-icon :size="28"><CircleCheck /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">已闭环</div>
            <div class="stat-value">{{ stats.closedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-month" @click="quickFilterMonth">
          <div class="stat-icon"><el-icon :size="28"><Calendar /></el-icon></div>
          <div class="stat-text">
            <div class="stat-label">本月盘点</div>
            <div class="stat-value">{{ stats.monthSheetCount || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" @submit.prevent="handleSearch">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 150px">
            <el-option label="盘点中" value="IN_PROGRESS" />
            <el-option label="待闭环（未平账）" value="SUBMITTED" />
            <el-option label="已闭环" value="CLOSED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="班次">
          <el-select v-model="searchForm.shift" placeholder="全部班次" clearable style="width: 120px">
            <el-option label="白班" value="DAY" />
            <el-option label="中班" value="MIDDLE" />
            <el-option label="夜班" value="NIGHT" />
          </el-select>
        </el-form-item>
        <el-form-item label="层位编码">
          <el-input
            v-model="searchForm.layerCode"
            placeholder="按覆盖层位查询"
            clearable
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="盘点单号">
          <el-input
            v-model="searchForm.sheetNo"
            placeholder="请输入单号"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="开单日期">
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
      <el-table-column type="index" label="序号" width="55" align="center" />
      <el-table-column prop="sheetNo" label="盘点单号" width="185">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">{{ row.sheetNo }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="盘点范围" min-width="200">
        <template #default="{ row }">
          <el-tag size="small" :type="row.scopeType === 'SHELF' ? 'primary' : 'info'" effect="plain">
            {{ row.scopeType === 'SHELF' ? '按货架' : '按层位' }}
          </el-tag>
          <span style="margin-left: 6px">{{ (row.coveredLayerList || []).join('、') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="班次" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ getShiftLabel(row.shift) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="inspector" label="盘点人" width="90" />
      <el-table-column label="盘点进度" width="170" align="center">
        <template #default="{ row }">
          <span>已标记 {{ row.markedCount || 0 }}/{{ ledgerTotal(row) }}</span>
          <div class="diff-counts">
            <el-tag v-if="(row.missingCount || 0) > 0" type="danger" size="small" effect="dark">
              缺失 {{ row.missingCount }}
            </el-tag>
            <el-tag v-if="(row.extraCount || 0) > 0" type="warning" size="small" effect="dark">
              多出 {{ row.extraCount }}
            </el-tag>
            <el-tag
              v-if="!(row.missingCount || 0) && !(row.extraCount || 0) && row.status !== 'IN_PROGRESS'"
              type="success" size="small" effect="plain"
            >
              账实相符
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'IN_PROGRESS'" type="primary" effect="light">盘点中</el-tag>
          <el-tag v-else-if="row.status === 'SUBMITTED'" type="danger" effect="dark">待闭环·未平账</el-tag>
          <el-tag v-else-if="row.status === 'CLOSED'" type="success" effect="light">已闭环</el-tag>
          <el-tag v-else type="info" effect="plain">已取消</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="差异原因" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.diffReason" class="diff-reason-text">{{ row.diffReason }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="开单时间" width="160">
        <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'IN_PROGRESS'"
            link
            type="primary"
            size="small"
            @click="openDetail(row.id)"
          >
            继续盘点
          </el-button>
          <el-button
            v-else-if="row.status === 'SUBMITTED'"
            link
            type="danger"
            size="small"
            @click="openCloseDialog(row)"
          >
            闭环
          </el-button>
          <el-button v-else link type="primary" size="small" @click="openDetail(row.id)">
            查看
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

    <!-- 新建盘点单：按层位/货架开单，登记班次与盘点人 -->
    <el-dialog v-model="createVisible" title="新建交班盘点单" width="560px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="盘点范围" prop="scopeType">
          <el-radio-group v-model="createForm.scopeType">
            <el-radio value="LAYER">按层位</el-radio>
            <el-radio value="SHELF">按货架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="createForm.scopeType === 'LAYER'" label="盘点层位" prop="layerCode">
          <el-select
            v-model="createForm.layerCode"
            filterable
            placeholder="请选择要盘点的层位"
            style="width: 100%"
            :loading="layerLoading"
          >
            <el-option
              v-for="layer in layerOptions"
              :key="layer.layerCode"
              :label="`${layer.layerCode} - ${layer.layerName || ''}（在架 ${layer.padCount || 0} 块）`"
              :value="layer.layerCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="盘点货架" prop="shelfCode">
          <el-select
            v-model="createForm.shelfCode"
            filterable
            placeholder="请选择要盘点的货架（覆盖全部层位）"
            style="width: 100%"
            :loading="layerLoading"
          >
            <el-option
              v-for="shelf in shelfOptions"
              :key="shelf.shelfCode"
              :label="`${shelf.shelfCode}（${shelf.layerCount} 层 · 在架 ${shelf.padCount} 块）`"
              :value="shelf.shelfCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="coveredPreview" label="覆盖范围">
          <el-tag type="info">{{ coveredPreview }}</el-tag>
          <div class="form-tip">开单后快照这些层位的在架垫板为账目清单；同一层位仅允许一张盘点中单据</div>
        </el-form-item>
        <el-form-item label="班次" prop="shift">
          <el-radio-group v-model="createForm.shift">
            <el-radio value="DAY">白班</el-radio>
            <el-radio value="MIDDLE">中班</el-radio>
            <el-radio value="NIGHT">夜班</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="盘点人" prop="inspector">
          <el-input v-model="createForm.inspector" placeholder="请输入盘点人姓名" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="盘点备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">
          开单并快照账目
        </el-button>
      </template>
    </el-dialog>

    <!-- 盘点单详情：逐块标记、补录多出、提交/闭环 -->
    <el-dialog
      v-model="detailVisible"
      :title="`盘点单 ${detail?.sheet?.sheetNo || ''}`"
      width="1020px"
      destroy-on-close
      top="4vh"
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-alert
            v-if="detail.sheet.status === 'SUBMITTED'"
            class="unbalanced-alert"
            type="error"
            :closable="false"
            show-icon
            :title="`盘点差异未平账：${detail.sheet.diffReason}`"
            :description="`该单覆盖层位未闭环前禁止归还上架；差异处理完毕后请点击下方“闭环”登记处理结论。`"
          />
          <el-alert
            v-else-if="detail.sheet.status === 'CLOSED'"
            class="unbalanced-alert"
            type="success"
            :closable="false"
            show-icon
            :title="`已闭环：${detail.sheet.closeConclusion || '账实相符'}`"
            :description="`闭环时间 ${formatTime(detail.sheet.closeTime)} · 闭环人 ${detail.sheet.closeOperator || '-'}`"
          />

          <el-descriptions :column="4" border size="small" class="detail-desc">
            <el-descriptions-item label="盘点范围">
              {{ detail.sheet.scopeType === 'SHELF' ? '按货架' : '按层位' }}：
              {{ (detail.sheet.coveredLayerList || []).join('、') }}
            </el-descriptions-item>
            <el-descriptions-item label="班次">{{ getShiftLabel(detail.sheet.shift) }}</el-descriptions-item>
            <el-descriptions-item label="盘点人">{{ detail.sheet.inspector }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag v-if="detail.sheet.status === 'IN_PROGRESS'" type="primary" effect="light">盘点中</el-tag>
              <el-tag v-else-if="detail.sheet.status === 'SUBMITTED'" type="danger" effect="dark">待闭环·未平账</el-tag>
              <el-tag v-else-if="detail.sheet.status === 'CLOSED'" type="success" effect="light">已闭环</el-tag>
              <el-tag v-else type="info" effect="plain">已取消</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="开单时间">{{ formatTime(detail.sheet.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatTime(detail.sheet.submitTime) }}</el-descriptions-item>
            <el-descriptions-item label="盘点统计">
              相符 {{ detail.sheet.matchCount || 0 }} · 缺失 {{ detail.sheet.missingCount || 0 }} ·
              多出 {{ detail.sheet.extraCount || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="备注">{{ detail.sheet.remark || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.sheet.diffReason" label="差异原因" :span="4">
              <span class="diff-reason-text">{{ detail.sheet.diffReason }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="layer-occupancy">
            <span class="section-label">覆盖层位实时占用：</span>
            <el-tag
              v-for="layer in detail.layers"
              :key="layer.layerCode"
              :type="layer.unbalanced ? 'danger' : 'info'"
              effect="plain"
              style="margin-right: 8px"
            >
              {{ layer.layerCode }} 在架 {{ layer.padCount }}/{{ layer.effectiveCapacity }}
              <template v-if="layer.unbalanced">（未平账）</template>
            </el-tag>
          </div>

          <div class="detail-section-title">
            盘点明细（账目 {{ ledgerItems.length }} 块<template v-if="extraItems.length"> · 多出 {{ extraItems.length }} 块</template>）
          </div>
          <el-table :data="detail.items" border size="small" max-height="380">
            <el-table-column type="index" label="序号" width="55" align="center" />
            <el-table-column label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.itemType === 'EXTRA'" type="warning" size="small" effect="dark">多出</el-tag>
                <el-tag v-else type="info" size="small" effect="plain">账目</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="padCode" label="垫板编号" width="130" />
            <el-table-column prop="moldType" label="适配模具" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.moldType || '-' }}</template>
            </el-table-column>
            <el-table-column label="账目层位" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info" effect="plain">{{ row.layerCode }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="实时层位" width="130" align="center">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.itemType === 'LEDGER' && row.currentLayerCode !== row.layerCode"
                  content="开单后层位已变化，请以现场清点为准"
                  placement="top"
                >
                  <el-tag size="small" type="warning" effect="plain">
                    {{ row.currentLayerCode || '离架中' }}
                  </el-tag>
                </el-tooltip>
                <span v-else-if="row.itemType === 'LEDGER'">{{ row.currentLayerCode || '离架中' }}</span>
                <span v-else>{{ row.currentLayerCode || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="盘点结果" width="200" align="center">
              <template #default="{ row }">
                <template v-if="row.itemType === 'EXTRA'">
                  <el-tag type="warning" size="small" effect="dark">多出</el-tag>
                </template>
                <template v-else-if="detail.sheet.status === 'IN_PROGRESS'">
                  <el-button
                    size="small"
                    :type="row.checkResult === 'MATCH' ? 'success' : 'default'"
                    :plain="row.checkResult !== 'MATCH'"
                    @click="markItem(row, 'MATCH')"
                  >
                    相符
                  </el-button>
                  <el-button
                    size="small"
                    :type="row.checkResult === 'MISSING' ? 'danger' : 'default'"
                    :plain="row.checkResult !== 'MISSING'"
                    @click="markItem(row, 'MISSING')"
                  >
                    缺失
                  </el-button>
                </template>
                <template v-else>
                  <el-tag v-if="row.checkResult === 'MATCH'" type="success" size="small" effect="light">
                    账实相符
                  </el-tag>
                  <el-tag v-else-if="row.checkResult === 'MISSING'" type="danger" size="small" effect="dark">
                    缺失
                  </el-tag>
                  <el-tag v-else type="info" size="small" effect="plain">未标记</el-tag>
                </template>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
            <el-table-column v-if="detail.sheet.status === 'IN_PROGRESS'" label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.itemType === 'EXTRA'"
                  link
                  type="danger"
                  size="small"
                  @click="removeExtra(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 补录现场多出垫板：仅盘点中可补录 -->
          <div v-if="detail.sheet.status === 'IN_PROGRESS'" class="extra-form">
            <span class="section-label">补录多出垫板：</span>
            <el-select
              v-model="extraForm.layerCode"
              placeholder="所在层位"
              size="small"
              style="width: 140px"
            >
              <el-option
                v-for="code in detail.sheet.coveredLayerList || []"
                :key="code"
                :label="code"
                :value="code"
              />
            </el-select>
            <el-input
              v-model="extraForm.padCode"
              placeholder="垫板编号"
              size="small"
              style="width: 150px"
            />
            <el-input
              v-model="extraForm.moldType"
              placeholder="适配模具（可选）"
              size="small"
              style="width: 150px"
            />
            <el-button type="warning" size="small" :loading="submitting" @click="submitExtra">
              <el-icon><Plus /></el-icon>补录多出
            </el-button>
          </div>
        </template>
      </div>
      <template #footer>
        <template v-if="detail">
          <el-button
            v-if="detail.sheet.status === 'IN_PROGRESS'"
            type="info"
            plain
            :loading="submitting"
            @click="cancelSheet"
          >
            取消单据
          </el-button>
          <el-button
            v-if="detail.sheet.status === 'IN_PROGRESS'"
            type="primary"
            :loading="submitting"
            @click="openSubmitDialog"
          >
            提交盘点
          </el-button>
          <el-button
            v-if="detail.sheet.status === 'SUBMITTED'"
            type="danger"
            :loading="submitting"
            @click="openCloseDialog(detail.sheet)"
          >
            闭环
          </el-button>
          <el-button @click="detailVisible = false">关闭</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 提交盘点：存在差异时必须登记差异原因 -->
    <el-dialog v-model="submitVisible" title="提交盘点" width="520px" destroy-on-close>
      <el-alert
        v-if="detail"
        type="warning"
        :closable="false"
        show-icon
        :title="`本次盘点存在差异：缺失 ${detail.sheet.missingCount || 0} 块、多出 ${detail.sheet.extraCount || 0} 块`"
        description="提交后单据进入“待闭环”，覆盖层位在概览与层位页标出未平账，未闭环前禁止归还上架。"
      />
      <el-form ref="submitFormRef" :model="submitForm" :rules="submitRules" label-width="90px" style="margin-top: 14px">
        <el-form-item label="差异原因" prop="diffReason">
          <el-input
            v-model="submitForm.diffReason"
            type="textarea"
            :rows="3"
            placeholder="必填：如 A-01-02 缺失 1 块疑似随模具带出未登记；多出 1 块为退库未上架"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitSheet">
          确认提交
        </el-button>
      </template>
    </el-dialog>

    <!-- 闭环：差异处理完毕后必须登记处理结论与处理人 -->
    <el-dialog v-model="closeVisible" title="盘点闭环" width="560px" destroy-on-close>
      <template v-if="closeTarget">
        <el-alert
          type="error"
          :closable="false"
          show-icon
          :title="`未平账原因：${closeTarget.diffReason}`"
          :description="`单号 ${closeTarget.sheetNo} · ${getShiftLabel(closeTarget.shift)} · 盘点人 ${closeTarget.inspector} · 提交于 ${formatTime(closeTarget.submitTime)}`"
        />
        <el-form ref="closeFormRef" :model="closeForm" :rules="closeRules" label-width="90px" style="margin-top: 14px">
          <el-form-item label="处理结论" prop="closeConclusion">
            <el-input
              v-model="closeForm.closeConclusion"
              type="textarea"
              :rows="3"
              placeholder="必填：差异如何处理完毕，如缺失板已找回并归还上架/多出板已登记退库"
            />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="闭环时间">
                <el-date-picker
                  v-model="closeForm.closeTime"
                  type="datetime"
                  placeholder="默认当前时间"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="处理人" prop="closeOperator">
                <el-input v-model="closeForm.closeOperator" placeholder="必填：处理闭环的经办人" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitClose">
          确认闭环
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  getInventoryPage,
  getInventoryStatistics,
  getInventoryDetail,
  createInventorySheet,
  markInventoryItem,
  addInventoryExtraItem,
  removeInventoryExtraItem,
  submitInventorySheet,
  closeInventorySheet,
  cancelInventorySheet
} from '@/api/inventory'
import { getShelfLayerList } from '@/api/shelf'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const dateRange = ref([])
const stats = reactive({ inProgressCount: 0, unclosedCount: 0, closedCount: 0, monthSheetCount: 0 })

const searchForm = reactive({ status: '', shift: '', layerCode: '', sheetNo: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const formatTime = (time) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-')

const SHIFT_LABELS = { DAY: '白班', MIDDLE: '中班', NIGHT: '夜班' }
const getShiftLabel = (shift) => SHIFT_LABELS[shift] || shift

// 账目明细数 = 明细总数 - 多出数（多出明细不参与逐块标记）
const ledgerTotal = (row) => (row.totalCount || 0) - (row.extraCount || 0)

const buildQuery = () => {
  const query = { ...searchForm, ...pagination }
  if (typeof query.layerCode === 'string') {
    query.layerCode = query.layerCode.trim()
    if (!query.layerCode) delete query.layerCode
  }
  if (typeof query.sheetNo === 'string') {
    query.sheetNo = query.sheetNo.trim()
    if (!query.sheetNo) delete query.sheetNo
  }
  if (!query.status) delete query.status
  if (!query.shift) delete query.shift
  if (dateRange.value && dateRange.value.length === 2) {
    query.startTime = dateRange.value[0]
    query.endTime = dateRange.value[1]
  }
  return query
}

const loadData = async () => {
  loading.value = true
  try {
    const data = await getInventoryPage(buildQuery())
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
    Object.assign(stats, await getInventoryStatistics())
  } catch (e) {
    console.error(e)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { status: '', shift: '', layerCode: '', sheetNo: '' })
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

// ---------------- 新建盘点单 ----------------
const createVisible = ref(false)
const createFormRef = ref(null)
const layerLoading = ref(false)
const layerOptions = ref([])
const createForm = reactive({
  scopeType: 'LAYER',
  layerCode: '',
  shelfCode: '',
  shift: 'DAY',
  inspector: '',
  remark: ''
})
const createRules = {
  scopeType: [{ required: true, message: '请选择盘点范围', trigger: 'change' }],
  layerCode: [{ required: true, message: '请选择要盘点的层位', trigger: 'change' }],
  shelfCode: [{ required: true, message: '请选择要盘点的货架', trigger: 'change' }],
  shift: [{ required: true, message: '请选择班次', trigger: 'change' }],
  inspector: [{ required: true, message: '请输入盘点人', trigger: 'blur' }]
}

// 货架选项：由层位列表聚合，展示层数与在架总数
const shelfOptions = computed(() => {
  const map = {}
  layerOptions.value.forEach((layer) => {
    if (!map[layer.shelfCode]) {
      map[layer.shelfCode] = { shelfCode: layer.shelfCode, layerCount: 0, padCount: 0 }
    }
    map[layer.shelfCode].layerCount += 1
    map[layer.shelfCode].padCount += layer.padCount || 0
  })
  return Object.values(map)
})

const coveredPreview = computed(() => {
  if (createForm.scopeType === 'LAYER') {
    const layer = layerOptions.value.find((l) => l.layerCode === createForm.layerCode)
    return layer ? `${layer.layerCode}（在架 ${layer.padCount || 0} 块）` : ''
  }
  const shelf = shelfOptions.value.find((s) => s.shelfCode === createForm.shelfCode)
  return shelf ? `${shelf.shelfCode} 全部 ${shelf.layerCount} 层（在架 ${shelf.padCount} 块）` : ''
})

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

const openCreateDialog = async (presetLayerCode) => {
  Object.assign(createForm, {
    scopeType: 'LAYER',
    layerCode: presetLayerCode || '',
    shelfCode: '',
    shift: 'DAY',
    inspector: '',
    remark: ''
  })
  createVisible.value = true
  await loadLayers()
}

const submitCreate = async () => {
  await createFormRef.value?.validate()
  submitting.value = true
  try {
    const sheet = await createInventorySheet({
      scopeType: createForm.scopeType,
      layerCode: createForm.scopeType === 'LAYER' ? createForm.layerCode : null,
      shelfCode: createForm.scopeType === 'SHELF' ? createForm.shelfCode : null,
      shift: createForm.shift,
      inspector: createForm.inspector,
      remark: createForm.remark || null
    })
    ElMessage.success(`盘点单 ${sheet.sheetNo} 已开单，请逐块清点标记`)
    createVisible.value = false
    loadData()
    loadStats()
    openDetail(sheet.id)
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

// ---------------- 盘点单详情 ----------------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const extraForm = reactive({ layerCode: '', padCode: '', moldType: '' })

const ledgerItems = computed(() => (detail.value?.items || []).filter((i) => i.itemType === 'LEDGER'))
const extraItems = computed(() => (detail.value?.items || []).filter((i) => i.itemType === 'EXTRA'))

const openDetail = async (id) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getInventoryDetail(id)
    Object.assign(extraForm, {
      layerCode: (detail.value.sheet.coveredLayerList || [])[0] || '',
      padCode: '',
      moldType: ''
    })
  } catch (e) {
    console.error(e)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const reloadDetail = async () => {
  if (!detail.value?.sheet?.id) return
  detail.value = await getInventoryDetail(detail.value.sheet.id)
}

const markItem = async (row, checkResult) => {
  try {
    await markInventoryItem({ itemId: row.id, checkResult })
    await reloadDetail()
  } catch (e) {
    console.error(e)
  }
}

const submitExtra = async () => {
  if (!extraForm.layerCode) {
    ElMessage.warning('请选择多出垫板所在层位')
    return
  }
  if (!extraForm.padCode || !extraForm.padCode.trim()) {
    ElMessage.warning('请输入多出垫板编号')
    return
  }
  submitting.value = true
  try {
    await addInventoryExtraItem(detail.value.sheet.id, {
      layerCode: extraForm.layerCode,
      padCode: extraForm.padCode.trim(),
      moldType: extraForm.moldType || null
    })
    ElMessage.success('多出垫板已补录')
    Object.assign(extraForm, { padCode: '', moldType: '' })
    await reloadDetail()
    loadData()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

const removeExtra = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除补录的多出垫板【${row.padCode}】吗？`, '提示', { type: 'warning' })
    await removeInventoryExtraItem(row.id)
    ElMessage.success('已删除')
    await reloadDetail()
    loadData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// ---------------- 提交盘点 ----------------
const submitVisible = ref(false)
const submitFormRef = ref(null)
const submitForm = reactive({ diffReason: '' })
const submitRules = {
  diffReason: [{ required: true, message: '存在差异，提交时必须登记差异原因', trigger: 'blur' }]
}

const openSubmitDialog = async () => {
  if (!detail.value) return
  const sheet = detail.value.sheet
  const ledger = ledgerItems.value.length
  const marked = sheet.markedCount || 0
  if (marked < ledger) {
    ElMessage.warning(`还有 ${ledger - marked} 块账目垫板未标记盘点结果，全部标记后才能提交`)
    return
  }
  const hasDiff = (sheet.missingCount || 0) > 0 || (sheet.extraCount || 0) > 0
  if (!hasDiff) {
    // 账实相符：提交即自动闭环
    try {
      await ElMessageBox.confirm('本次盘点账实相符，提交后单据自动闭环，确认提交？', '提交盘点', {
        type: 'success',
        confirmButtonText: '确认提交',
        cancelButtonText: '再核一遍'
      })
    } catch (e) {
      return
    }
    submitting.value = true
    try {
      await submitInventorySheet({ sheetId: sheet.id, diffReason: null })
      ElMessage.success('账实相符，盘点单已闭环')
      detailVisible.value = false
      loadData()
      loadStats()
    } catch (e) {
      console.error(e)
    } finally {
      submitting.value = false
    }
    return
  }
  submitForm.diffReason = ''
  submitVisible.value = true
}

const submitSheet = async () => {
  await submitFormRef.value?.validate()
  submitting.value = true
  try {
    await submitInventorySheet({ sheetId: detail.value.sheet.id, diffReason: submitForm.diffReason })
    ElMessage.warning('盘点差异已登记，层位进入未平账状态，闭环前禁止归还上架')
    submitVisible.value = false
    detailVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

// ---------------- 闭环 ----------------
const closeVisible = ref(false)
const closeFormRef = ref(null)
const closeTarget = ref(null)
const closeForm = reactive({ closeConclusion: '', closeTime: '', closeOperator: '' })
const closeRules = {
  closeConclusion: [{ required: true, message: '闭环必须填写处理结论', trigger: 'blur' }],
  closeOperator: [{ required: true, message: '闭环必须填写处理人', trigger: 'blur' }]
}

const openCloseDialog = (sheet) => {
  closeTarget.value = sheet
  Object.assign(closeForm, { closeConclusion: '', closeTime: '', closeOperator: '' })
  closeVisible.value = true
}

const submitClose = async () => {
  await closeFormRef.value?.validate()
  submitting.value = true
  try {
    await closeInventorySheet({
      sheetId: closeTarget.value.id,
      closeConclusion: closeForm.closeConclusion,
      closeTime: closeForm.closeTime || null,
      closeOperator: closeForm.closeOperator || null
    })
    ElMessage.success('盘点单已闭环，层位恢复可归还上架')
    closeVisible.value = false
    detailVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

// ---------------- 取消单据 ----------------
const cancelSheet = async () => {
  try {
    await ElMessageBox.confirm(
      `确定取消盘点单【${detail.value.sheet.sheetNo}】吗？取消后已标记结果作废，可重新开单。`,
      '取消盘点单',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await cancelInventorySheet(detail.value.sheet.id)
    ElMessage.success('盘点单已取消')
    detailVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // 从层位管理卡片跳转：带 status 时按条件回看台账（如待闭环单据），否则直接打开开单弹窗并预填层位
  if (route.query.status) {
    searchForm.status = String(route.query.status)
  }
  if (route.query.layerCode && route.query.status) {
    searchForm.layerCode = String(route.query.layerCode)
  }
  loadData()
  loadStats()
  if (route.query.layerCode && !route.query.status) {
    openCreateDialog(String(route.query.layerCode))
  }
})
</script>

<style lang="scss" scoped>
.flow-tip {
  margin-bottom: 14px;
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
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-2px);
  }

  &.card-progress {
    background: linear-gradient(135deg, #4facfe 0%, #2563eb 100%);
  }
  &.card-unclosed {
    background: linear-gradient(135deg, #f5576c 0%, #d9363e 100%);
  }
  &.card-closed {
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

.diff-counts {
  display: flex;
  gap: 4px;
  justify-content: center;
  margin-top: 4px;
}

.diff-reason-text {
  color: #f56c6c;
  font-weight: 600;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  width: 100%;
}

.detail-desc {
  margin-bottom: 14px;
}

.unbalanced-alert {
  margin-bottom: 14px;
}

.layer-occupancy {
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.section-label {
  font-size: 13px;
  color: #606266;
  font-weight: 600;
}

.detail-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 6px 0 10px;
}

.extra-form {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 12px;
  background: #fdf6ec;
  border-radius: 6px;
}
</style>
