-- ============================================================
-- 到货待检（待检层）- 增量迁移脚本（已有部署使用，幂等可重复执行）
-- 全新部署由 init.sql 自动建表，无需执行本脚本
-- 用法：mysql -u pad_user -p pad_stamping < V11__pad_arrival_inspection.sql
-- ============================================================

SET NAMES utf8mb4;

USE pad_stamping;

-- ------------------------------------------------------------
-- 1. 垫板档案新增库存状态列（存量垫板默认“正式在库”，列已存在时跳过）
-- QUARANTINE-到货待检（待检层，不可领用/换绑/计入可用库存）、
-- OFFICIAL-正式在库、REJECTED-判退离库（档案冻结留存）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'pad_info'
      AND column_name = 'stock_status'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE pad_info
        ADD COLUMN stock_status VARCHAR(16) NOT NULL DEFAULT ''OFFICIAL''
        COMMENT ''库存状态：QUARANTINE-到货待检、OFFICIAL-正式在库、REJECTED-判退离库''
        AFTER maintenance_status',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 库存状态索引（重复执行时若索引已存在则跳过）
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'pad_info'
      AND index_name = 'idx_stock_status'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE pad_info ADD INDEX idx_stock_status (stock_status)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 到货批次主表
-- 新到垫板先落待检层：登记到货批次、到货时间与经办人；
-- 质检通过整批转正式层，判退整批离库，判定结论必填
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pad_arrival_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_no VARCHAR(40) NOT NULL COMMENT '到货批次号：DH + 日期时间 + 随机串',
    supplier VARCHAR(128) DEFAULT NULL COMMENT '供应商/来源',
    arrival_time DATETIME NOT NULL COMMENT '到货时间',
    operator VARCHAR(64) NOT NULL COMMENT '经办人',
    pad_count INT NOT NULL DEFAULT 0 COMMENT '批次垫板块数（登记时快照）',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待检中、PASSED-质检通过已转正式、REJECTED-判退离库',
    inspect_time DATETIME DEFAULT NULL COMMENT '质检判定时间',
    inspector VARCHAR(64) DEFAULT NULL COMMENT '质检人（缺省取经办人）',
    inspect_conclusion VARCHAR(512) DEFAULT NULL COMMENT '质检结论（通过/判退时必填）',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pab_batch_no (batch_no),
    KEY idx_pab_status (status),
    KEY idx_pab_arrival_time (arrival_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='到货批次主表';

-- ------------------------------------------------------------
-- 3. 到货明细表
-- 批内垫板清单：登记时创建垫板档案（待检层，无层位）；
-- 质检通过时记录每块垫板转入的正式层位
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pad_arrival_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_id BIGINT NOT NULL COMMENT '到货批次ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号（登记时快照）',
    mold_type VARCHAR(128) DEFAULT NULL COMMENT '适配模具（登记时快照）',
    target_layer_code VARCHAR(64) DEFAULT NULL COMMENT '质检通过转入的正式层位（判定时记录）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_pai_batch_id (batch_id),
    KEY idx_pai_pad_id (pad_id),
    KEY idx_pai_pad_code (pad_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='到货明细表';
