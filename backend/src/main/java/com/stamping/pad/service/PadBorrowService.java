package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.BorrowRecordQueryDTO;
import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadReturnDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadBorrowRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.vo.BorrowRecordExcelVO;
import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PadBorrowService {

    private final PadBorrowRecordMapper borrowRecordMapper;
    private final PadInfoMapper padInfoMapper;
    private final LayerAdjustRecordMapper recordMapper;
    private final ShelfLayerService shelfLayerService;
    private final PadMoldReserveService padMoldReserveService;

    public Page<PadBorrowRecord> pageList(BorrowRecordQueryDTO query) {
        Page<PadBorrowRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        return borrowRecordMapper.selectPageList(page, query);
    }

    public PadBorrowRecord getById(Long id) {
        return borrowRecordMapper.selectDetailById(id);
    }

    public List<PadBorrowRecord> listByPadId(Long padId) {
        LambdaQueryWrapper<PadBorrowRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PadBorrowRecord::getPadId, padId).orderByDesc(PadBorrowRecord::getCheckoutTime);
        return borrowRecordMapper.selectList(wrapper);
    }

    /**
     * 领用登记：仅在架垫板可领用；同一块垫板存在未归还记录时禁止重复领用。
     * 换模预留生效期内的垫板禁止被其他产线领用。
     * 领用后垫板 shelf_layer_code/bind_time 置空（离架），并写入 CHECKOUT 调整记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadBorrowRecord checkout(PadCheckoutDTO dto) {
        PadInfo pad = padInfoMapper.lockById(dto.getPadId());
        if (pad == null) {
            throw new RuntimeException("垫板不存在");
        }
        assertPadUsable(pad);
        // 行锁内校验换模预留：预留期内禁止领用，与预留登记并发串行化
        padMoldReserveService.assertNotEffectivelyReserved(pad);
        Long openCount = borrowRecordMapper.selectCount(
                new LambdaQueryWrapper<PadBorrowRecord>()
                        .eq(PadBorrowRecord::getPadId, pad.getId())
                        .eq(PadBorrowRecord::getStatus, "BORROWED"));
        if (openCount > 0) {
            throw new RuntimeException("该垫板已被领用且尚未归还，禁止重复领用");
        }

        String originLayerCode = pad.getShelfLayerCode();
        if (originLayerCode == null || originLayerCode.isEmpty()) {
            throw new RuntimeException("垫板当前未在货架层位上，无法领用，请先绑定层位");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkoutTime = dto.getCheckoutTime() != null ? dto.getCheckoutTime() : now;
        if (dto.getExpectedReturnTime() != null
                && dto.getExpectedReturnTime().isBefore(checkoutTime)) {
            throw new RuntimeException("预计归还时间不能早于领用时间");
        }

        // 垫板离架：显式 set null，避免 updateById 忽略空字段
        LambdaUpdateWrapper<PadInfo> padUpdate = new LambdaUpdateWrapper<>();
        padUpdate.eq(PadInfo::getId, pad.getId())
                .set(PadInfo::getShelfLayerCode, null)
                .set(PadInfo::getBindTime, null)
                .set(PadInfo::getUpdateTime, now);
        padInfoMapper.update(null, padUpdate);

        PadBorrowRecord record = new PadBorrowRecord();
        record.setPadId(pad.getId());
        record.setPadCode(pad.getPadCode());
        record.setBorrower(dto.getBorrower());
        record.setProductionLine(dto.getProductionLine());
        record.setPurpose(dto.getPurpose());
        record.setCheckoutTime(checkoutTime);
        record.setExpectedReturnTime(dto.getExpectedReturnTime());
        record.setOriginLayerCode(originLayerCode);
        record.setStatus("BORROWED");
        record.setRemark(dto.getRemark());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        borrowRecordMapper.insert(record);

        insertAdjustRecord(pad, originLayerCode, null, "CHECKOUT",
                dto.getBorrower(), buildCheckoutReason(record), checkoutTime);
        return record;
    }

    /**
     * 归还登记：必须选择可用（未被占用）层位；禁止对已归还记录重复操作。
     * 归还后恢复垫板与层位的绑定，并写入 RETURN 调整记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadBorrowRecord doReturn(PadReturnDTO dto) {
        PadBorrowRecord record = borrowRecordMapper.selectById(dto.getId());
        if (record == null) {
            throw new RuntimeException("领用记录不存在");
        }
        if ("RETURNED".equals(record.getStatus())) {
            throw new RuntimeException("该领用记录已归还，禁止重复归还操作");
        }

        PadInfo pad = padInfoMapper.selectById(record.getPadId());
        if (pad == null) {
            throw new RuntimeException("垫板档案不存在或已删除");
        }
        // 待检/停用垫板不允许作为归还目标重新上架，须先在保养台账恢复为“可用”
        assertPadUsable(pad);
        String returnLayerCode = dto.getReturnLayerCode() == null ? null : dto.getReturnLayerCode().trim();
        if (returnLayerCode == null || returnLayerCode.isEmpty()) {
            throw new RuntimeException("归还层位不能为空");
        }
        // 归还层位须存在、未封锁且未满（容量配额为 0 的层位同样不可作为归还目标）
        shelfLayerService.lockAndAssertCapacity(returnLayerCode);
        // 盘点差异未闭环的层位禁止归还上架，闭环后自动恢复
        shelfLayerService.assertNotUnbalancedForReturn(returnLayerCode);
        // 归还层位必须可用：层位上不存在任何在架垫板
        List<PadInfo> occupied = padInfoMapper.selectByLayerCode(returnLayerCode);
        if (!occupied.isEmpty()) {
            throw new RuntimeException("归还层位【" + returnLayerCode + "】已被占用，请选择其他可用层位");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime returnTime = dto.getReturnTime() != null ? dto.getReturnTime() : now;
        if (returnTime.isBefore(record.getCheckoutTime())) {
            throw new RuntimeException("归还时间不能早于领用时间");
        }

        // 恢复垫板与层位的绑定
        LambdaUpdateWrapper<PadInfo> padUpdate = new LambdaUpdateWrapper<>();
        padUpdate.eq(PadInfo::getId, pad.getId())
                .set(PadInfo::getShelfLayerCode, returnLayerCode)
                .set(PadInfo::getBindTime, returnTime)
                .set(PadInfo::getUpdateTime, now);
        padInfoMapper.update(null, padUpdate);

        record.setStatus("RETURNED");
        record.setReturnTime(returnTime);
        record.setReturnLayerCode(returnLayerCode);
        if (dto.getRemark() != null && !dto.getRemark().isEmpty()) {
            record.setRemark(dto.getRemark());
        }
        record.setUpdateTime(now);
        borrowRecordMapper.updateById(record);

        String operator = dto.getOperator() != null && !dto.getOperator().isEmpty()
                ? dto.getOperator() : record.getBorrower();
        insertAdjustRecord(pad, record.getOriginLayerCode(), record.getReturnLayerCode(), "RETURN",
                operator, buildReturnReason(record), returnTime);
        return borrowRecordMapper.selectDetailById(record.getId());
    }

    /** 待检/停用/已报废垫板不可领用，归还时也不可作为目标重新上架 */
    private void assertPadUsable(PadInfo pad) {
        String status = pad.getMaintenanceStatus();
        if ("PENDING".equals(status)) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】处于待检状态，暂不可领用/归还，请先在保养台账处理");
        }
        if ("DISABLED".equals(status)) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已停用，禁止领用/归还，请先在保养台账恢复");
        }
        if ("SCRAPPED".equals(status)) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已报废出库，禁止领用/归还");
        }
    }

    /** 可归还层位：返回全部层位及其容量配额、当前占用数与封锁状态，前端按占用/配额/封锁放行选择，后端归还时二次校验 */
    public List<ShelfLayer> listAvailableReturnLayers() {
        return shelfLayerService.listAll();
    }

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void exportRecords(BorrowRecordQueryDTO query, HttpServletResponse response) throws IOException {
        query.setPageNum(1L);
        query.setPageSize(10000L);
        List<PadBorrowRecord> records = borrowRecordMapper.selectPageList(
                new Page<>(1, 10000), query).getRecords();

        List<BorrowRecordExcelVO> voList = new ArrayList<>();
        for (PadBorrowRecord r : records) {
            BorrowRecordExcelVO vo = new BorrowRecordExcelVO();
            vo.setPadCode(r.getPadCode());
            if ("RETURNED".equals(r.getStatus())) {
                vo.setStatusStr("已归还");
            } else if (Boolean.TRUE.equals(r.getOverdue())) {
                vo.setStatusStr("领用中(已逾期)");
            } else {
                vo.setStatusStr("领用中");
            }
            vo.setBorrower(r.getBorrower());
            vo.setProductionLine(r.getProductionLine());
            vo.setPurpose(r.getPurpose());
            vo.setOriginLayerCode(r.getOriginLayerCode() != null ? r.getOriginLayerCode() : "-");
            vo.setReturnLayerCode(r.getReturnLayerCode() != null ? r.getReturnLayerCode() : "未归还");
            vo.setCheckoutTimeStr(r.getCheckoutTime() != null ? r.getCheckoutTime().format(TIME_FORMATTER) : "-");
            vo.setExpectedReturnTimeStr(r.getExpectedReturnTime() != null
                    ? r.getExpectedReturnTime().format(TIME_FORMATTER) : "-");
            vo.setReturnTimeStr(r.getReturnTime() != null ? r.getReturnTime().format(TIME_FORMATTER) : "-");
            voList.add(vo);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("垫板领用归还台账", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), BorrowRecordExcelVO.class)
                .sheet("领用归还记录")
                .doWrite(voList);
    }

    public Map<String, Object> statistics() {
        Map<String, Object> stats = new HashMap<>();
        Long borrowedCount = borrowRecordMapper.selectCount(
                new LambdaQueryWrapper<PadBorrowRecord>().eq(PadBorrowRecord::getStatus, "BORROWED"));
        Long returnedCount = borrowRecordMapper.selectCount(
                new LambdaQueryWrapper<PadBorrowRecord>().eq(PadBorrowRecord::getStatus, "RETURNED"));
        Long overdueCount = borrowRecordMapper.selectCount(
                new LambdaQueryWrapper<PadBorrowRecord>()
                        .eq(PadBorrowRecord::getStatus, "BORROWED")
                        .isNotNull(PadBorrowRecord::getExpectedReturnTime)
                        .lt(PadBorrowRecord::getExpectedReturnTime, LocalDateTime.now()));
        stats.put("borrowedCount", borrowedCount);
        stats.put("returnedCount", returnedCount);
        stats.put("overdueCount", overdueCount);
        return stats;
    }

    private String buildCheckoutReason(PadBorrowRecord record) {
        StringBuilder sb = new StringBuilder("领用离架，领用人：").append(record.getBorrower())
                .append("，产线/工位：").append(record.getProductionLine());
        if (record.getPurpose() != null && !record.getPurpose().isEmpty()) {
            sb.append("，用途：").append(record.getPurpose());
        }
        return sb.toString();
    }

    private String buildReturnReason(PadBorrowRecord record) {
        return "领用归还上架，领用人：" + record.getBorrower()
                + "，产线/工位：" + record.getProductionLine();
    }

    private void insertAdjustRecord(PadInfo pad, String oldLayerCode, String newLayerCode,
                                    String adjustType, String operator, String reason,
                                    LocalDateTime time) {
        LayerAdjustRecord adjustRecord = new LayerAdjustRecord();
        adjustRecord.setPadId(pad.getId());
        adjustRecord.setPadCode(pad.getPadCode());
        adjustRecord.setOldLayerCode(oldLayerCode);
        adjustRecord.setNewLayerCode(newLayerCode);
        adjustRecord.setAdjustType(adjustType);
        adjustRecord.setOperator(operator);
        adjustRecord.setAdjustReason(reason);
        adjustRecord.setAdjustTime(time);
        recordMapper.insert(adjustRecord);
    }
}
