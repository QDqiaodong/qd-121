-- ============================================================
-- 垫板报废出库 - 增量迁移脚本（已有部署使用，幂等可重复执行）
-- 全新部署由 init.sql 自动建表，无需执行本脚本
-- 背景：保养给出“报废建议”后，库房单独做报废出库登记（批准人、去向、时间、照片），
--      出库后垫板状态置为 SCRAPPED，不再占用层位，也不能再被领用或回架。
-- 用法：mysql -u pad_user -p pad_stamping < V6__pad_scrap_outbound.sql
-- ============================================================

SET NAMES utf8mb4;

USE pad_stamping;

-- 垫板保养状态扩展报废态：AVAILABLE-可用、PENDING-待检、DISABLED-停用、SCRAPPED-已报废
-- 存量状态保持不变，仅放开列长度约束（幂等：MySQL 8 可重复执行 MODIFY COLUMN）
ALTER TABLE pad_info
    MODIFY COLUMN maintenance_status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE'
    COMMENT '保养状态：AVAILABLE-可用、PENDING-待检、DISABLED-停用、SCRAPPED-已报废';

-- 垫板报废出库记录表
CREATE TABLE IF NOT EXISTS pad_scrap_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    approver VARCHAR(64) NOT NULL COMMENT '批准人',
    destination VARCHAR(128) NOT NULL COMMENT '报废去向：如废品仓、回收商、就地销毁',
    scrap_time DATETIME NOT NULL COMMENT '报废出库时间',
    photo_paths VARCHAR(2048) DEFAULT NULL COMMENT '报废照片路径，多张以英文逗号分隔',
    origin_layer_code VARCHAR(64) DEFAULT NULL COMMENT '报废时所在层位（出库前原层位）',
    maintenance_record_id BIGINT DEFAULT NULL COMMENT '关联的报废建议保养记录ID',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_psr_pad_id (pad_id),
    KEY idx_psr_pad_code (pad_code),
    KEY idx_psr_destination (destination),
    KEY idx_psr_scrap_time (scrap_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='垫板报废出库记录表';
