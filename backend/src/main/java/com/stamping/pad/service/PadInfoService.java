package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.BindLayerDTO;
import com.stamping.pad.dto.PadInfoDTO;
import com.stamping.pad.dto.PadQueryDTO;
import com.stamping.pad.dto.UnbindLayerDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadBorrowRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PadInfoService {

    private final PadInfoMapper padInfoMapper;
    private final LayerAdjustRecordMapper recordMapper;
    private final PadBorrowRecordMapper borrowRecordMapper;
    private final ShelfLayerService shelfLayerService;
    private final PadMoldReserveService padMoldReserveService;

    /** 领用中的垫板已离架，层位调整需在归还后进行，防止在架状态与领用闭环冲突 */
    private void assertNotBorrowed(Long padId) {
        Long count = borrowRecordMapper.selectCount(
                new LambdaQueryWrapper<PadBorrowRecord>()
                        .eq(PadBorrowRecord::getPadId, padId)
                        .eq(PadBorrowRecord::getStatus, "BORROWED"));
        if (count > 0) {
            throw new RuntimeException("垫板领用中（已离架），请先归还后再操作层位");
        }
    }

    /** 待检/停用/已报废垫板不允许上架（绑定/换绑/编辑换层），登记保养时已自动离架，须先恢复为可用 */
    private void assertPadBindable(PadInfo pad) {
        String status = pad.getMaintenanceStatus();
        if ("PENDING".equals(status)) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】处于待检状态，不可上架，请先在保养台账恢复为可用");
        }
        if ("DISABLED".equals(status)) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已停用，不可上架，请先在保养台账恢复为可用");
        }
        if ("SCRAPPED".equals(status)) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已报废出库，禁止重新上架");
        }
    }

    public Page<PadInfo> pageList(PadQueryDTO query) {
        Page<PadInfo> page = new Page<>(query.getPageNum(), query.getPageSize());
        return padInfoMapper.selectPageList(page, query);
    }

    public PadInfo getById(Long id) {
        return padInfoMapper.selectById(id);
    }

    public List<PadInfo> listByLayerCode(String layerCode) {
        String code = normalizeCode(layerCode);
        if (code == null) {
            return List.of();
        }
        return padInfoMapper.selectByLayerCode(code);
    }

    public List<PadInfo> listByShelfCode(String shelfCode) {
        String code = normalizeCode(shelfCode);
        if (code == null) {
            return List.of();
        }
        return padInfoMapper.selectByShelfCode(code);
    }

    /** 编号仅做查询条件：去除首尾空白，空白或长度非法时返回 null，由调用方按无结果处理 */
    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            return null;
        }
        return trimmed;
    }

    @Transactional(rollbackFor = Exception.class)
    public PadInfo create(PadInfoDTO dto) {
        LambdaQueryWrapper<PadInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PadInfo::getPadCode, dto.getPadCode());
        Long count = padInfoMapper.selectCount(wrapper);
        if (count > 0) {
            throw new RuntimeException("垫板编号已存在");
        }

        PadInfo padInfo = new PadInfo();
        BeanUtils.copyProperties(dto, padInfo);
        // 新建档案默认“可用”，避免保养状态字段为空导致领用/归还判断异常
        padInfo.setMaintenanceStatus("AVAILABLE");
        padInfo.setCreateTime(LocalDateTime.now());
        padInfo.setUpdateTime(LocalDateTime.now());

        if (dto.getShelfLayerCode() != null && !dto.getShelfLayerCode().isEmpty()) {
            // 建档即上架：层位须存在、未封锁且占用未达配额
            shelfLayerService.lockAndAssertCapacity(dto.getShelfLayerCode());
            padInfo.setBindTime(LocalDateTime.now());
            padInfoMapper.insert(padInfo);

            LayerAdjustRecord record = new LayerAdjustRecord();
            record.setPadId(padInfo.getId());
            record.setPadCode(padInfo.getPadCode());
            record.setOldLayerCode(null);
            record.setNewLayerCode(dto.getShelfLayerCode());
            record.setAdjustType("BIND");
            record.setOperator(dto.getRemark() != null ? "系统" : "系统");
            record.setAdjustReason("建档初始绑定");
            record.setAdjustTime(LocalDateTime.now());
            recordMapper.insert(record);
        } else {
            padInfoMapper.insert(padInfo);
        }

        return padInfo;
    }

    @Transactional(rollbackFor = Exception.class)
    public PadInfo update(PadInfoDTO dto) {
        PadInfo existing = padInfoMapper.lockById(dto.getId());
        if (existing == null) {
            throw new RuntimeException("垫板不存在");
        }
        // 已报废出库的档案冻结，禁止编辑（报废台账留存）
        if ("SCRAPPED".equals(existing.getMaintenanceStatus())) {
            throw new RuntimeException("垫板【" + existing.getPadCode() + "】已报废出库，档案冻结，禁止编辑");
        }

        if (!existing.getPadCode().equals(dto.getPadCode())) {
            LambdaQueryWrapper<PadInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PadInfo::getPadCode, dto.getPadCode());
            Long count = padInfoMapper.selectCount(wrapper);
            if (count > 0) {
                throw new RuntimeException("垫板编号已存在");
            }
        }

        // 层位变更按绑定/重分配/解绑规则处理：校验目标层位、更新绑定时间并写入调整记录
        String oldLayerCode = normalizeLayerCode(existing.getShelfLayerCode());
        String newLayerCode = normalizeLayerCode(dto.getShelfLayerCode());
        boolean layerChanged = !Objects.equals(oldLayerCode, newLayerCode);
        if (layerChanged) {
            assertNotBorrowed(existing.getId());
            // 换模预留生效期内禁止解绑/换层（在架板不能动）
            padMoldReserveService.assertNotEffectivelyReserved(existing);
        }
        String adjustType = null;

        if (layerChanged) {
            if (newLayerCode != null) {
                // 待检/停用垫板不可上架
                assertPadBindable(existing);
                // 换绑/新绑目标层位须存在、未封锁且占用未达配额
                shelfLayerService.lockAndAssertCapacity(newLayerCode);
            }
            if (oldLayerCode == null) {
                adjustType = "BIND";
            } else if (newLayerCode == null) {
                adjustType = "UNBIND";
            } else {
                adjustType = "REBIND";
            }
        }

        BeanUtils.copyProperties(dto, existing, "id", "createTime", "bindTime", "shelfLayerCode");
        LocalDateTime now = LocalDateTime.now();
        existing.setUpdateTime(now);
        padInfoMapper.updateById(existing);

        if (layerChanged) {
            // updateById 默认忽略 null 字段，层位与绑定时间需显式 set，保证解绑时能真正置空
            LocalDateTime bindTime = "UNBIND".equals(adjustType) ? null : now;
            LambdaUpdateWrapper<PadInfo> layerUpdate = new LambdaUpdateWrapper<>();
            layerUpdate.eq(PadInfo::getId, existing.getId())
                    .set(PadInfo::getShelfLayerCode, newLayerCode)
                    .set(PadInfo::getBindTime, bindTime);
            padInfoMapper.update(null, layerUpdate);
            existing.setShelfLayerCode(newLayerCode);
            existing.setBindTime(bindTime);

            LayerAdjustRecord record = new LayerAdjustRecord();
            record.setPadId(existing.getId());
            record.setPadCode(existing.getPadCode());
            record.setOldLayerCode(oldLayerCode);
            record.setNewLayerCode(newLayerCode);
            record.setAdjustType(adjustType);
            record.setOperator("管理员");
            record.setAdjustReason("档案编辑调整层位");
            record.setAdjustTime(now);
            recordMapper.insert(record);
        }
        return existing;
    }

    private String normalizeLayerCode(String layerCode) {
        return (layerCode == null || layerCode.isEmpty()) ? null : layerCode;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PadInfo padInfo = padInfoMapper.lockById(id);
        if (padInfo == null) {
            throw new RuntimeException("垫板不存在");
        }
        assertNotBorrowed(id);
        // 存在未释放换模预留的垫板不能删除，避免预留台账留下无主明细
        padMoldReserveService.assertNotReservedForDelete(id, padInfo.getPadCode());
        if ("SCRAPPED".equals(padInfo.getMaintenanceStatus())) {
            throw new RuntimeException("垫板已报废出库，档案随报废台账留存，禁止删除");
        }
        if (padInfo.getShelfLayerCode() != null && !padInfo.getShelfLayerCode().isEmpty()) {
            throw new RuntimeException("请先解绑货架分层后再删除垫板");
        }
        padInfoMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindLayer(BindLayerDTO dto) {
        PadInfo padInfo = padInfoMapper.lockById(dto.getPadId());
        if (padInfo == null) {
            throw new RuntimeException("垫板不存在");
        }
        assertNotBorrowed(padInfo.getId());
        assertPadBindable(padInfo);

        String oldLayerCode = padInfo.getShelfLayerCode();
        String adjustType;

        if (oldLayerCode == null || oldLayerCode.isEmpty()) {
            adjustType = "BIND";
        } else if (oldLayerCode.equals(dto.getLayerCode())) {
            throw new RuntimeException("垫板已绑定在该分层");
        } else {
            adjustType = "REBIND";
            // 换模预留生效期内禁止换层
            padMoldReserveService.assertNotEffectivelyReserved(padInfo);
        }

        // 绑定/换绑目标层位须存在、未封锁且占用未达配额
        shelfLayerService.lockAndAssertCapacity(dto.getLayerCode());

        padInfo.setShelfLayerCode(dto.getLayerCode());
        padInfo.setBindTime(LocalDateTime.now());
        padInfo.setUpdateTime(LocalDateTime.now());
        padInfoMapper.updateById(padInfo);

        LayerAdjustRecord record = new LayerAdjustRecord();
        record.setPadId(padInfo.getId());
        record.setPadCode(padInfo.getPadCode());
        record.setOldLayerCode(oldLayerCode);
        record.setNewLayerCode(dto.getLayerCode());
        record.setAdjustType(adjustType);
        record.setOperator(dto.getOperator() != null ? dto.getOperator() : "管理员");
        record.setAdjustReason(dto.getAdjustReason());
        record.setAdjustTime(LocalDateTime.now());
        recordMapper.insert(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbindLayer(UnbindLayerDTO dto) {
        PadInfo padInfo = padInfoMapper.lockById(dto.getPadId());
        if (padInfo == null) {
            throw new RuntimeException("垫板不存在");
        }
        assertNotBorrowed(padInfo.getId());
        if ("SCRAPPED".equals(padInfo.getMaintenanceStatus())) {
            throw new RuntimeException("垫板【" + padInfo.getPadCode() + "】已报废出库，禁止解绑/回架操作");
        }
        // 换模预留生效期内禁止解绑，预留板须留在原层位待换模上线
        padMoldReserveService.assertNotEffectivelyReserved(padInfo);

        String oldLayerCode = padInfo.getShelfLayerCode();
        if (oldLayerCode == null || oldLayerCode.isEmpty()) {
            throw new RuntimeException("垫板未绑定任何分层");
        }

        // 垫板离架：显式 set null，避免 updateById 忽略空字段
        LambdaUpdateWrapper<PadInfo> unbind = new LambdaUpdateWrapper<>();
        unbind.eq(PadInfo::getId, padInfo.getId())
                .set(PadInfo::getShelfLayerCode, null)
                .set(PadInfo::getBindTime, null)
                .set(PadInfo::getUpdateTime, LocalDateTime.now());
        padInfoMapper.update(null, unbind);

        LayerAdjustRecord record = new LayerAdjustRecord();
        record.setPadId(padInfo.getId());
        record.setPadCode(padInfo.getPadCode());
        record.setOldLayerCode(oldLayerCode);
        record.setNewLayerCode(null);
        record.setAdjustType("UNBIND");
        record.setOperator(dto.getOperator() != null ? dto.getOperator() : "管理员");
        record.setAdjustReason(dto.getAdjustReason());
        record.setAdjustTime(LocalDateTime.now());
        recordMapper.insert(record);
    }

    /**
     * 批量导入场景下单行建档，独立事务：单行失败只回滚该行，不影响其他合格数据。
     * 调用方需提前完成必填、重复编号、层位与尺寸格式等校验。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadInfo importRow(PadInfo padInfo) {
        LambdaQueryWrapper<PadInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PadInfo::getPadCode, padInfo.getPadCode());
        if (padInfoMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("垫板编号已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        if (padInfo.getMaintenanceStatus() == null || padInfo.getMaintenanceStatus().isEmpty()) {
            padInfo.setMaintenanceStatus("AVAILABLE");
        }
        padInfo.setCreateTime(now);
        padInfo.setUpdateTime(now);

        String layerCode = padInfo.getShelfLayerCode();
        if (layerCode != null && !layerCode.isEmpty()) {
            // 导入即上架：层位须存在、未封锁且占用未达配额（逐行独立事务，占用数实时统计）
            shelfLayerService.lockAndAssertCapacity(layerCode);
            padInfo.setBindTime(now);
            padInfoMapper.insert(padInfo);

            LayerAdjustRecord record = new LayerAdjustRecord();
            record.setPadId(padInfo.getId());
            record.setPadCode(padInfo.getPadCode());
            record.setOldLayerCode(null);
            record.setNewLayerCode(layerCode);
            record.setAdjustType("BIND");
            record.setOperator("系统");
            record.setAdjustReason("批量导入初始绑定");
            record.setAdjustTime(now);
            recordMapper.insert(record);
        } else {
            padInfoMapper.insert(padInfo);
        }
        return padInfo;
    }

    public Long countTotal() {
        return padInfoMapper.selectCount(null);
    }

    public Long countUnbound() {
        LambdaQueryWrapper<PadInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(PadInfo::getShelfLayerCode).or().eq(PadInfo::getShelfLayerCode, "");
        return padInfoMapper.selectCount(wrapper);
    }
}
