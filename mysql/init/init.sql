-- ============================================================
-- 冲压车间垫板货架层位绑定管理系统 - 数据库初始化脚本
-- 幂等迁移：支持重复执行，不会报错或产生脏数据
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. 创建数据库（幂等）
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS pad_stamping
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE pad_stamping;

-- ------------------------------------------------------------
-- 2. 垫板基础档案表（幂等）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pad_info (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    mold_type VARCHAR(128) DEFAULT NULL COMMENT '适配模具',
    length DECIMAL(10,2) DEFAULT NULL COMMENT '长度(mm)',
    width DECIMAL(10,2) DEFAULT NULL COMMENT '宽度(mm)',
    thickness DECIMAL(10,2) DEFAULT NULL COMMENT '厚度(mm)',
    image_path VARCHAR(512) DEFAULT NULL COMMENT '实物图片路径',
    shelf_layer_code VARCHAR(64) DEFAULT NULL COMMENT '当前绑定货架分层编码',
    bind_time DATETIME DEFAULT NULL COMMENT '绑定时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pad_code (pad_code),
    KEY idx_shelf_layer_code (shelf_layer_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='垫板基础档案表';

-- ------------------------------------------------------------
-- 3. 货架分层表（幂等）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shelf_layer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    layer_code VARCHAR(64) NOT NULL COMMENT '分层编码',
    shelf_code VARCHAR(64) NOT NULL COMMENT '货架编码',
    layer_name VARCHAR(128) DEFAULT NULL COMMENT '分层名称',
    layer_order INT DEFAULT 0 COMMENT '层序号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_layer_code (layer_code),
    KEY idx_shelf_code (shelf_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='货架分层表';

-- ------------------------------------------------------------
-- 4. 层位调整记录表（幂等）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS layer_adjust_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    old_layer_code VARCHAR(64) DEFAULT NULL COMMENT '原分层编码',
    new_layer_code VARCHAR(64) DEFAULT NULL COMMENT '新分层编码',
    adjust_type VARCHAR(32) NOT NULL COMMENT '调整类型：BIND-初始绑定、REBIND-变更绑定、UNBIND-解绑',
    operator VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    adjust_reason VARCHAR(512) DEFAULT NULL COMMENT '调整原因',
    adjust_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
    PRIMARY KEY (id),
    KEY idx_pad_id (pad_id),
    KEY idx_pad_code (pad_code),
    KEY idx_adjust_time (adjust_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='层位调整记录表';

-- ------------------------------------------------------------
-- 5. 预置货架分层数据（幂等，INSERT IGNORE）
-- ------------------------------------------------------------
INSERT IGNORE INTO shelf_layer (layer_code, shelf_code, layer_name, layer_order, remark) VALUES
    ('A-01-01', 'A-01', 'A区01货架第1层', 1, '重型垫板存放区'),
    ('A-01-02', 'A-01', 'A区01货架第2层', 2, '重型垫板存放区'),
    ('A-01-03', 'A-01', 'A区01货架第3层', 3, '重型垫板存放区'),
    ('A-02-01', 'A-02', 'A区02货架第1层', 1, '中型垫板存放区'),
    ('A-02-02', 'A-02', 'A区02货架第2层', 2, '中型垫板存放区'),
    ('A-02-03', 'A-02', 'A区02货架第3层', 3, '中型垫板存放区'),
    ('B-01-01', 'B-01', 'B区01货架第1层', 1, '轻型垫板存放区'),
    ('B-01-02', 'B-01', 'B区01货架第2层', 2, '轻型垫板存放区'),
    ('B-01-03', 'B-01', 'B区01货架第3层', 3, '轻型垫板存放区'),
    ('B-02-01', 'B-02', 'B区02货架第1层', 1, '特殊规格存放区'),
    ('B-02-02', 'B-02', 'B区02货架第2层', 2, '特殊规格存放区'),
    ('B-02-03', 'B-02', 'B区02货架第3层', 3, '特殊规格存放区');

-- ------------------------------------------------------------
-- 6. 给数据库用户授权（幂等）
-- ------------------------------------------------------------
-- 注意：以下授权在 MySQL 8.0 中通过 docker-compose 环境变量创建用户后执行
-- 若用户已存在则授权，若不存在则创建并授权
SET @sql = IF(
    EXISTS(SELECT 1 FROM mysql.user WHERE user = 'pad_user' AND host = '%'),
    'GRANT ALL PRIVILEGES ON pad_stamping.* TO ''pad_user''@''%'';',
    'CREATE USER ''pad_user''@''%'' IDENTIFIED BY ''pad123456''; GRANT ALL PRIVILEGES ON pad_stamping.* TO ''pad_user''@''%'';'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

FLUSH PRIVILEGES;

SET FOREIGN_KEY_CHECKS = 1;
