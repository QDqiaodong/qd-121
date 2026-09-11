-- ============================================================
-- 货架分层容量配额 - 增量迁移脚本（已有部署使用，幂等可重复执行）
-- 全新部署由 init.sql 自动建表，无需执行本脚本
-- 用法：mysql -u pad_user -p pad_stamping < V4__shelf_layer_capacity.sql
-- ============================================================

SET NAMES utf8mb4;

USE pad_stamping;

-- 货架分层新增容量配额列（存量层位默认配额 10，列已存在时跳过）
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'shelf_layer'
      AND column_name = 'capacity'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE shelf_layer
        ADD COLUMN capacity INT NOT NULL DEFAULT 10
        COMMENT ''容量配额：该层最多可存放的在架垫板数''
        AFTER layer_order',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
