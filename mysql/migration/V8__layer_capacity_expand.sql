-- ============================================================
-- 层位临时扩容 - 增量迁移脚本（已有部署使用，幂等可重复执行）
-- 全新部署由 init.sql 自动建表，无需执行本脚本
-- 背景：旺季到货时库房把某层配额临时加大，登记原因、新配额、生效时段和经办人；
--      扩容期内绑定/换绑/归还上架/导入按新配额校验，到期自动回到原配额，
--      未到期也可提前结束并写结论。
-- 用法：mysql -u pad_user -p pad_stamping < V8__layer_capacity_expand.sql
-- ============================================================

SET NAMES utf8mb4;

USE pad_stamping;

-- 层位临时扩容记录表：同一层位同一时间仅允许一条“待生效/生效中”记录（服务层事务内校验）
CREATE TABLE IF NOT EXISTS layer_capacity_expand_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    layer_id BIGINT NOT NULL COMMENT '货架分层ID',
    layer_code VARCHAR(64) NOT NULL COMMENT '分层编码',
    expand_reason VARCHAR(512) NOT NULL COMMENT '扩容原因',
    original_capacity INT NOT NULL COMMENT '原配额（登记时快照）',
    expand_capacity INT NOT NULL COMMENT '扩容后配额（须大于原配额）',
    start_time DATETIME NOT NULL COMMENT '生效开始时间',
    end_time DATETIME NOT NULL COMMENT '生效结束时间（到期自动恢复原配额）',
    operator VARCHAR(64) NOT NULL COMMENT '经办人',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：PENDING-待生效、ACTIVE-生效中、EXPIRED-已到期、ENDED-已结束',
    finish_time DATETIME DEFAULT NULL COMMENT '实际结束时间（提前结束时间或到期时间）',
    finish_conclusion VARCHAR(512) DEFAULT NULL COMMENT '结束结论（提前结束必填，到期由系统补写）',
    finish_operator VARCHAR(64) DEFAULT NULL COMMENT '结束经办人',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_lcer_layer_code (layer_code),
    KEY idx_lcer_status (status),
    KEY idx_lcer_start_time (start_time),
    KEY idx_lcer_end_time (end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='层位临时扩容记录表';
