package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.BindLayerDTO;
import com.stamping.pad.dto.PadInfoDTO;
import com.stamping.pad.dto.PadQueryDTO;
import com.stamping.pad.dto.UnbindLayerDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PadInfoService {

    private final PadInfoMapper padInfoMapper;
    private final ShelfLayerMapper shelfLayerMapper;
    private final LayerAdjustRecordMapper recordMapper;

    public Page<PadInfo> pageList(PadQueryDTO query) {
        Page<PadInfo> page = new Page<>(query.getPageNum(), query.getPageSize());
        return padInfoMapper.selectPageList(page, query);
    }

    public PadInfo getById(Long id) {
        return padInfoMapper.selectById(id);
    }

    public List<PadInfo> listByLayerCode(String layerCode) {
        return padInfoMapper.selectByLayerCode(layerCode);
    }

    public List<PadInfo> listByShelfCode(String shelfCode) {
        return padInfoMapper.selectByShelfCode(shelfCode);
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
        padInfo.setCreateTime(LocalDateTime.now());
        padInfo.setUpdateTime(LocalDateTime.now());

        if (dto.getShelfLayerCode() != null && !dto.getShelfLayerCode().isEmpty()) {
            ShelfLayer layer = shelfLayerMapper.selectByLayerCode(dto.getShelfLayerCode());
            if (layer == null) {
                throw new RuntimeException("货架分层不存在");
            }
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
        PadInfo existing = padInfoMapper.selectById(dto.getId());
        if (existing == null) {
            throw new RuntimeException("垫板不存在");
        }

        if (!existing.getPadCode().equals(dto.getPadCode())) {
            LambdaQueryWrapper<PadInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PadInfo::getPadCode, dto.getPadCode());
            Long count = padInfoMapper.selectCount(wrapper);
            if (count > 0) {
                throw new RuntimeException("垫板编号已存在");
            }
        }

        BeanUtils.copyProperties(dto, existing, "id", "createTime", "bindTime", "shelfLayerCode");
        existing.setUpdateTime(LocalDateTime.now());
        padInfoMapper.updateById(existing);
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PadInfo padInfo = padInfoMapper.selectById(id);
        if (padInfo == null) {
            throw new RuntimeException("垫板不存在");
        }
        if (padInfo.getShelfLayerCode() != null && !padInfo.getShelfLayerCode().isEmpty()) {
            throw new RuntimeException("请先解绑货架分层后再删除垫板");
        }
        padInfoMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindLayer(BindLayerDTO dto) {
        PadInfo padInfo = padInfoMapper.selectById(dto.getPadId());
        if (padInfo == null) {
            throw new RuntimeException("垫板不存在");
        }

        ShelfLayer layer = shelfLayerMapper.selectByLayerCode(dto.getLayerCode());
        if (layer == null) {
            throw new RuntimeException("货架分层不存在");
        }

        String oldLayerCode = padInfo.getShelfLayerCode();
        String adjustType;

        if (oldLayerCode == null || oldLayerCode.isEmpty()) {
            adjustType = "BIND";
        } else if (oldLayerCode.equals(dto.getLayerCode())) {
            throw new RuntimeException("垫板已绑定在该分层");
        } else {
            adjustType = "REBIND";
        }

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
        PadInfo padInfo = padInfoMapper.selectById(dto.getPadId());
        if (padInfo == null) {
            throw new RuntimeException("垫板不存在");
        }

        String oldLayerCode = padInfo.getShelfLayerCode();
        if (oldLayerCode == null || oldLayerCode.isEmpty()) {
            throw new RuntimeException("垫板未绑定任何分层");
        }

        padInfo.setShelfLayerCode(null);
        padInfo.setBindTime(null);
        padInfo.setUpdateTime(LocalDateTime.now());
        padInfoMapper.updateById(padInfo);

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

    public Long countTotal() {
        return padInfoMapper.selectCount(null);
    }

    public Long countUnbound() {
        LambdaQueryWrapper<PadInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(PadInfo::getShelfLayerCode).or().eq(PadInfo::getShelfLayerCode, "");
        return padInfoMapper.selectCount(wrapper);
    }
}
