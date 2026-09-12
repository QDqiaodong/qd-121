package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.LayerBlockDTO;
import com.stamping.pad.dto.LayerBlockRecordQueryDTO;
import com.stamping.pad.dto.LayerBlockReleaseDTO;
import com.stamping.pad.entity.LayerBlockRecord;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LayerBlockService {

    /** 封锁类型：层位破损、待清扫、检修中、其他 */
    private static final Set<String> BLOCK_TYPE_SET = Set.of("DAMAGE", "CLEANING", "MAINTENANCE", "OTHER");

    private final LayerBlockRecordMapper blockRecordMapper;
    private final ShelfLayerMapper shelfLayerMapper;

    public Page<LayerBlockRecord> pageList(LayerBlockRecordQueryDTO query) {
        Page<LayerBlockRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        return blockRecordMapper.selectPageList(page, query);
    }

    public List<LayerBlockRecord> listActiveBlocks() {
        return blockRecordMapper.selectActiveBlocks();
    }

    /**
     * 登记封锁：层位破损/待清扫/检修时单独登记原因、开始时间与经办人。
     * 同一层位同一时间仅允许一条“封锁中”记录；行锁层位（与上架占用同一把锁），
     * 防止封锁登记与绑定/归还/导入并发交错导致封锁层仍被占位。
     */
    @Transactional(rollbackFor = Exception.class)
    public LayerBlockRecord block(LayerBlockDTO dto) {
        String layerCode = dto.getLayerCode() == null ? "" : dto.getLayerCode().trim();
        if (layerCode.isEmpty()) {
            throw new RuntimeException("请选择要封锁的层位");
        }
        String blockType = dto.getBlockType() == null ? "" : dto.getBlockType().trim();
        if (!BLOCK_TYPE_SET.contains(blockType)) {
            throw new RuntimeException("封锁类型非法，仅支持：层位破损、待清扫、检修中、其他");
        }
        String blockReason = dto.getBlockReason() == null ? "" : dto.getBlockReason().trim();
        if (blockReason.isEmpty()) {
            throw new RuntimeException("封锁原因不能为空");
        }
        String operator = dto.getOperator() == null ? "" : dto.getOperator().trim();
        if (operator.isEmpty()) {
            throw new RuntimeException("经办人不能为空");
        }

        // 行锁层位：与 lockAndAssertCapacity 串行化，封锁生效后该层立即不可再上架
        ShelfLayer layer = shelfLayerMapper.lockByLayerCode(layerCode);
        if (layer == null) {
            throw new RuntimeException("货架分层不存在");
        }
        Long activeCount = blockRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerBlockRecord>()
                        .eq(LayerBlockRecord::getLayerCode, layerCode)
                        .eq(LayerBlockRecord::getStatus, LayerBlockRecord.STATUS_BLOCKED));
        if (activeCount > 0) {
            throw new RuntimeException("层位【" + layerCode + "】已处于封锁中，请勿重复登记");
        }

        LocalDateTime now = LocalDateTime.now();
        LayerBlockRecord record = new LayerBlockRecord();
        record.setLayerId(layer.getId());
        record.setLayerCode(layerCode);
        record.setBlockType(blockType);
        record.setBlockReason(blockReason);
        record.setStartTime(dto.getStartTime() != null ? dto.getStartTime() : now);
        record.setOperator(operator);
        record.setStatus(LayerBlockRecord.STATUS_BLOCKED);
        record.setRemark(dto.getRemark());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        blockRecordMapper.insert(record);
        return record;
    }

    /**
     * 解除封锁：必须填写解除结论；仅“封锁中”记录可解除，条件更新防止并发重复解除。
     */
    @Transactional(rollbackFor = Exception.class)
    public LayerBlockRecord release(LayerBlockReleaseDTO dto) {
        if (dto.getId() == null) {
            throw new RuntimeException("封锁记录不存在");
        }
        LayerBlockRecord record = blockRecordMapper.selectById(dto.getId());
        if (record == null) {
            throw new RuntimeException("封锁记录不存在");
        }
        if (!LayerBlockRecord.STATUS_BLOCKED.equals(record.getStatus())) {
            throw new RuntimeException("该封锁记录已解除，禁止重复操作");
        }
        String conclusion = dto.getReleaseConclusion() == null ? "" : dto.getReleaseConclusion().trim();
        if (conclusion.isEmpty()) {
            throw new RuntimeException("解除封锁必须填写结论");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime releaseTime = dto.getReleaseTime() != null ? dto.getReleaseTime() : now;
        if (releaseTime.isBefore(record.getStartTime())) {
            throw new RuntimeException("解除时间不能早于封锁开始时间");
        }
        String releaseOperator = dto.getReleaseOperator() == null ? "" : dto.getReleaseOperator().trim();
        if (releaseOperator.isEmpty()) {
            releaseOperator = record.getOperator();
        }

        // 条件更新：仅封锁中记录可被解除，并发下重复解除直接失败
        LambdaUpdateWrapper<LayerBlockRecord> update = new LambdaUpdateWrapper<>();
        update.eq(LayerBlockRecord::getId, record.getId())
                .eq(LayerBlockRecord::getStatus, LayerBlockRecord.STATUS_BLOCKED)
                .set(LayerBlockRecord::getStatus, LayerBlockRecord.STATUS_RELEASED)
                .set(LayerBlockRecord::getReleaseTime, releaseTime)
                .set(LayerBlockRecord::getReleaseConclusion, conclusion)
                .set(LayerBlockRecord::getReleaseOperator, releaseOperator)
                .set(LayerBlockRecord::getUpdateTime, now);
        int rows = blockRecordMapper.update(null, update);
        if (rows == 0) {
            throw new RuntimeException("该封锁记录已解除，禁止重复操作");
        }
        return blockRecordMapper.selectById(record.getId());
    }

    public Map<String, Object> statistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("blockedCount", blockRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerBlockRecord>()
                        .eq(LayerBlockRecord::getStatus, LayerBlockRecord.STATUS_BLOCKED)));
        stats.put("releasedCount", blockRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerBlockRecord>()
                        .eq(LayerBlockRecord::getStatus, LayerBlockRecord.STATUS_RELEASED)));
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        stats.put("monthBlockCount", blockRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerBlockRecord>()
                        .ge(LayerBlockRecord::getStartTime, monthStart)));
        return stats;
    }
}
