-- ============================================================
-- 垫板保养台账 - 增量迁移脚本（已有部署使用，幂等可重复执行）
-- 全新部署由 init.sql 自动建表，无需执行本脚本
-- 用法：mysql -u pad_user -p pad_stamping < V3__pad_maintenance.sql
-- ============================================================

SET NAMES utf8mb4;

USE pad_stamping;

-- 垫板档案新增保养状态列（存量垫板默认“可用”，列已存在时跳过）
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'pad_info'
      AND column_name = 'maintenance_status'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE pad_info
        ADD COLUMN maintenance_status VARCHAR(24) NOT NULL DEFAULT ''AVAILABLE''
        COMMENT ''保养状态：AVAILABLE-可用、PENDING-待检、DISABLED-停用''
        AFTER bind_time',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 保养状态索引（重复执行时若索引已存在则跳过）
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'pad_info'
      AND index_name = 'idx_maintenance_status'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE pad_info ADD INDEX idx_maintenance_status (maintenance_status)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 垫板保养记录表
CREATE TABLE IF NOT EXISTS pad_maintenance_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    maintenance_type VARCHAR(64) NOT NULL COMMENT '保养类型：如日常保养、定期保养、维修、送检',
    handler VARCHAR(64) NOT NULL COMMENT '处理人',
    maintenance_time DATETIME NOT NULL COMMENT '保养时间',
    maintenance_result VARCHAR(64) NOT NULL COMMENT '保养结果：NORMAL-正常、REPAIRED-已修复、ABNORMAL-异常待处理、SCRAPPED-报废建议',
    status_before VARCHAR(24) DEFAULT NULL COMMENT '保养前状态：AVAILABLE/PENDING/DISABLED',
    status_after VARCHAR(24) NOT NULL COMMENT '保养后状态：AVAILABLE-可用、PENDING-待检、DISABLED-停用',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_pmr_pad_id (pad_id),
    KEY idx_pmr_pad_code (pad_code),
    KEY idx_pmr_status_after (status_after),
    KEY idx_pmr_maintenance_time (maintenance_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='垫板保养记录表';
