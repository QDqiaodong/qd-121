-- ============================================================
-- 垫板领用归还闭环 - 增量迁移脚本（已有部署使用，幂等可重复执行）
-- 全新部署由 init.sql 自动建表，无需执行本脚本
-- 用法：mysql -u pad_user -p pad_stamping < V2__pad_borrow_record.sql
-- ============================================================

SET NAMES utf8mb4;

USE pad_stamping;

-- 领用归还记录表
CREATE TABLE IF NOT EXISTS pad_borrow_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    borrower VARCHAR(64) NOT NULL COMMENT '领用人',
    production_line VARCHAR(128) NOT NULL COMMENT '产线/工位',
    purpose VARCHAR(512) DEFAULT NULL COMMENT '用途',
    checkout_time DATETIME NOT NULL COMMENT '领用时间',
    expected_return_time DATETIME DEFAULT NULL COMMENT '预计归还时间',
    return_time DATETIME DEFAULT NULL COMMENT '实际归还时间',
    origin_layer_code VARCHAR(64) DEFAULT NULL COMMENT '领用时所在层位（原层位）',
    return_layer_code VARCHAR(64) DEFAULT NULL COMMENT '归还层位',
    status VARCHAR(16) NOT NULL DEFAULT 'BORROWED' COMMENT '状态：BORROWED-领用中、RETURNED-已归还',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_pbr_pad_id (pad_id),
    KEY idx_pbr_pad_code (pad_code),
    KEY idx_pbr_status (status),
    KEY idx_pbr_checkout_time (checkout_time),
    KEY idx_pbr_return_time (return_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='垫板领用归还记录表';

-- 调整记录类型注释补充 CHECKOUT/RETURN（仅改注释，不影响存量数据）
ALTER TABLE layer_adjust_record
    MODIFY COLUMN adjust_type VARCHAR(32) NOT NULL
    COMMENT '调整类型：BIND-初始绑定、REBIND-变更绑定、UNBIND-解绑、CHECKOUT-领用离架、RETURN-归还上架';
