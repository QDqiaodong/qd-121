-- ============================================================
-- 层位封锁管理 - 增量迁移脚本（已有部署使用，幂等可重复执行）
-- 全新部署由 init.sql 自动建表，无需执行本脚本
-- 背景：库房发现层位破损、待清扫或正在检修时，单独登记封锁（原因、开始时间、经办人）；
--      封锁期间该层禁止绑定/换绑/归还上架/导入占位，解除封锁必须填写结论。
-- 用法：mysql -u pad_user -p pad_stamping < V7__layer_block_record.sql
-- ============================================================

SET NAMES utf8mb4;

USE pad_stamping;

-- 层位封锁记录表：同一层位同一时间仅允许一条“封锁中”记录（服务层事务内校验）
CREATE TABLE IF NOT EXISTS layer_block_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    layer_id BIGINT NOT NULL COMMENT '货架分层ID',
    layer_code VARCHAR(64) NOT NULL COMMENT '分层编码',
    block_type VARCHAR(32) NOT NULL COMMENT '封锁类型：DAMAGE-层位破损、CLEANING-待清扫、MAINTENANCE-检修中、OTHER-其他',
    block_reason VARCHAR(512) NOT NULL COMMENT '封锁原因',
    start_time DATETIME NOT NULL COMMENT '封锁开始时间',
    operator VARCHAR(64) NOT NULL COMMENT '经办人',
    status VARCHAR(16) NOT NULL DEFAULT 'BLOCKED' COMMENT '状态：BLOCKED-封锁中、RELEASED-已解除',
    release_time DATETIME DEFAULT NULL COMMENT '解除时间',
    release_conclusion VARCHAR(512) DEFAULT NULL COMMENT '解除结论（解除时必填）',
    release_operator VARCHAR(64) DEFAULT NULL COMMENT '解除经办人',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_lbr_layer_code (layer_code),
    KEY idx_lbr_status (status),
    KEY idx_lbr_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='层位封锁记录表';
