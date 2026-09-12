package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.PadQueryDTO;
import com.stamping.pad.dto.PadScrapDTO;
import com.stamping.pad.dto.ScrapRecordQueryDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadBorrowRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.PadScrapRecord;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.PadMaintenanceRecordMapper;
import com.stamping.pad.mapper.PadScrapRecordMapper;
import com.stamping.pad.entity.PadMaintenanceRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 垫板报废出库：保养给出“报废建议”后，库房单独做出库登记。
 * 出库登记批准人、去向、时间与照片，垫板状态置为 SCRAPPED；
 * 出库后不再占用层位（在架则自动离架并写 SCRAP 调整记录），
 * 也不能再被领用、归还或重新上架。
 */
@Service
@RequiredArgsConstructor
public class PadScrapService {

    /** 报废终态：报废出库后不可恢复、不可领用/归还/上架 */
    public static final String SCRAPPED = "SCRAPPED";

    private final PadScrapRecordMapper scrapRecordMapper;
    private final PadInfoMapper padInfoMapper;
    private final PadBorrowRecordMapper borrowRecordMapper;
    private final PadMaintenanceRecordMapper maintenanceRecordMapper;
    private final LayerAdjustRecordMapper adjustRecordMapper;
    private final PadMoldReserveService padMoldReserveService;

    public Page<PadScrapRecord> pageList(ScrapRecordQueryDTO query) {
        Page<PadScrapRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<PadScrapRecord> result = scrapRecordMapper.selectPageList(page, query);
        result.getRecords().forEach(this::fillPhotos);
        return result;
    }

    public PadScrapRecord getById(Long id) {
        PadScrapRecord record = scrapRecordMapper.selectDetailById(id);
        if (record != null) {
            fillPhotos(record);
        }
        return record;
    }

    public List<PadScrapRecord> listByPadId(Long padId) {
        List<PadScrapRecord> records = scrapRecordMapper.selectList(
                new LambdaQueryWrapper<PadScrapRecord>()
                        .eq(PadScrapRecord::getPadId, padId)
                        .orderByDesc(PadScrapRecord::getScrapTime));
        records.forEach(this::fillPhotos);
        return records;
    }

    private void fillPhotos(PadScrapRecord record) {
        if (record.getPhotoPaths() == null || record.getPhotoPaths().isEmpty()) {
            record.setPhotos(List.of());
        } else {
            record.setPhotos(Arrays.stream(record.getPhotoPaths().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList());
        }
    }

    /**
     * 报废出库登记。
     * 前置条件：垫板存在、未报废、无领用中记录（须先归还）、已有保养“报废建议”；
     * 出库后置为 SCRAPPED，在架则释放层位并写 SCRAP 调整记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadScrapRecord outbound(PadScrapDTO dto) {
        // 行锁垫板：与换模预留登记串行化，预留期内报废离架直接拦截
        PadInfo pad = padInfoMapper.lockById(dto.getPadId());
        if (pad == null) {
            throw new RuntimeException("垫板不存在");
        }
        if (SCRAPPED.equals(pad.getMaintenanceStatus())) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已报废出库，禁止重复报废");
        }
        // 存在未释放换模预留的垫板须先释放预留，避免预留台账无主、换模上线无板
        padMoldReserveService.assertNotReservedForOffShelf(pad.getId(), pad.getPadCode());
        Long existCount = scrapRecordMapper.selectCount(
                new LambdaQueryWrapper<PadScrapRecord>().eq(PadScrapRecord::getPadId, pad.getId()));
        if (existCount > 0) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已存在报废出库记录，禁止重复报废");
        }
        // 领用中的垫板实物不在库房，须先完成归还闭环再报废出库
        Long openBorrowCount = borrowRecordMapper.selectCount(
                new LambdaQueryWrapper<PadBorrowRecord>()
                        .eq(PadBorrowRecord::getPadId, pad.getId())
                        .eq(PadBorrowRecord::getStatus, "BORROWED"));
        if (openBorrowCount > 0) {
            throw new RuntimeException("垫板领用中（已离架），请先归还后再办理报废出库");
        }
        // 报废出库前置：保养台账须先给出“报废建议”
        Long suggestionCount = maintenanceRecordMapper.selectCount(
                new LambdaQueryWrapper<PadMaintenanceRecord>()
                        .eq(PadMaintenanceRecord::getPadId, pad.getId())
                        .eq(PadMaintenanceRecord::getMaintenanceResult, "SCRAPPED"));
        if (suggestionCount == 0) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】暂无保养“报废建议”，请先在保养台账登记报废建议");
        }

        String approver = trim(dto.getApprover());
        if (approver.isEmpty()) {
            throw new RuntimeException("批准人不能为空");
        }
        String destination = trim(dto.getDestination());
        if (destination.isEmpty()) {
            throw new RuntimeException("报废去向不能为空");
        }
        List<String> photos = dto.getPhotoPaths() == null ? List.of()
                : dto.getPhotoPaths().stream().filter(p -> p != null && !p.trim().isEmpty()).toList();
        if (photos.isEmpty()) {
            throw new RuntimeException("请至少上传一张报废出库照片");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scrapTime = dto.getScrapTime() != null ? dto.getScrapTime() : now;

        String originLayerCode = pad.getShelfLayerCode();

        // 垫板置为报废终态；在架则同步释放层位（显式 set null，避免 updateById 忽略空字段）
        LambdaUpdateWrapper<PadInfo> padUpdate = new LambdaUpdateWrapper<>();
        padUpdate.eq(PadInfo::getId, pad.getId())
                .set(PadInfo::getMaintenanceStatus, SCRAPPED)
                .set(PadInfo::getShelfLayerCode, null)
                .set(PadInfo::getBindTime, null)
                .set(PadInfo::getUpdateTime, now);
        padInfoMapper.update(null, padUpdate);

        PadScrapRecord record = new PadScrapRecord();
        record.setPadId(pad.getId());
        record.setPadCode(pad.getPadCode());
        record.setApprover(approver);
        record.setDestination(destination);
        record.setScrapTime(scrapTime);
        record.setPhotoPaths(String.join(",", photos));
        record.setOriginLayerCode(originLayerCode);
        record.setMaintenanceRecordId(dto.getMaintenanceRecordId());
        record.setRemark(dto.getRemark());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        scrapRecordMapper.insert(record);
        fillPhotos(record);

        // 在架报废：写 SCRAP 调整记录，层位占用随即释放；已离架（待检/停用/未绑定）不产生层位变更
        if (originLayerCode != null && !originLayerCode.isEmpty()) {
            LayerAdjustRecord adjustRecord = new LayerAdjustRecord();
            adjustRecord.setPadId(pad.getId());
            adjustRecord.setPadCode(pad.getPadCode());
            adjustRecord.setOldLayerCode(originLayerCode);
            adjustRecord.setNewLayerCode(null);
            adjustRecord.setAdjustType("SCRAP");
            adjustRecord.setOperator(approver);
            adjustRecord.setAdjustReason("报废出库，去向：" + destination);
            adjustRecord.setAdjustTime(scrapTime);
            adjustRecordMapper.insert(adjustRecord);
        }
        return record;
    }

    /**
     * 报废出库候选：未报废垫板（含联表的领用状态）+ 已有“报废建议”的垫板ID集合。
     * 前端下拉据此禁用无报废建议或领用中的垫板并提示原因，后端出库时二次校验。
     */
    public Map<String, Object> outboundCandidates() {
        Page<PadInfo> page = padInfoMapper.selectPageList(
                new Page<>(1, 100000), new PadQueryDTO());
        List<PadInfo> pads = page.getRecords().stream()
                .filter(p -> !SCRAPPED.equals(p.getMaintenanceStatus()))
                .toList();

        List<Long> suggestionPadIds = maintenanceRecordMapper.selectList(
                        new LambdaQueryWrapper<PadMaintenanceRecord>()
                                .select(PadMaintenanceRecord::getPadId)
                                .eq(PadMaintenanceRecord::getMaintenanceResult, "SCRAPPED"))
                .stream().map(PadMaintenanceRecord::getPadId).distinct().toList();

        Map<String, Object> result = new HashMap<>();
        result.put("pads", pads);
        result.put("suggestionPadIds", suggestionPadIds);
        return result;
    }

    public Map<String, Object> statistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("scrappedCount", padInfoMapper.selectCount(
                new LambdaQueryWrapper<PadInfo>().eq(PadInfo::getMaintenanceStatus, SCRAPPED)));
        stats.put("scrapRecordCount", scrapRecordMapper.selectCount(null));
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1)
                .toLocalDate().atStartOfDay();
        stats.put("monthScrapCount", scrapRecordMapper.selectCount(
                new LambdaQueryWrapper<PadScrapRecord>().ge(PadScrapRecord::getScrapTime, monthStart)));
        return stats;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
