-- ============================================================
-- 换模垫板预留 - 增量迁移脚本（已有部署使用，幂等可重复执行）
-- 全新部署由 init.sql 自动建表，无需执行本脚本
-- 背景：换模前库房把指定在架垫板预留给即将上线的模具，登记模具、预留板清单、
--      生效时段和经办人；预留期内这些板不能被其他产线领用，也不能解绑换层；
--      到期或手工释放后恢复可领。
-- 用法：mysql -u pad_user -p pad_stamping < V9__pad_mold_reserve.sql
-- ============================================================

SET NAMES utf8mb4;

USE pad_stamping;

-- 换模垫板预留主表：一张预留单登记一个模具、生效时段与经办人
CREATE TABLE IF NOT EXISTS pad_mold_reserve_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    mold_code VARCHAR(64) NOT NULL COMMENT '模具编码（即将上线的模具）',
    mold_name VARCHAR(128) DEFAULT NULL COMMENT '模具名称',
    production_line VARCHAR(128) DEFAULT NULL COMMENT '上线产线/工位',
    start_time DATETIME NOT NULL COMMENT '预留生效开始时间',
    end_time DATETIME NOT NULL COMMENT '预留生效结束时间（到期自动释放）',
    operator VARCHAR(64) NOT NULL COMMENT '经办人',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：PENDING-待生效、ACTIVE-生效中、EXPIRED-已到期、RELEASED-已释放',
    release_time DATETIME DEFAULT NULL COMMENT '实际释放时间（手工释放时间或到期时间）',
    release_conclusion VARCHAR(512) DEFAULT NULL COMMENT '释放结论（手工释放必填，到期由系统补写）',
    release_operator VARCHAR(64) DEFAULT NULL COMMENT '释放经办人',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_pmrr_mold_code (mold_code),
    KEY idx_pmrr_status (status),
    KEY idx_pmrr_start_time (start_time),
    KEY idx_pmrr_end_time (end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换模垫板预留记录表';

-- 换模垫板预留明细表：预留单内的垫板清单（登记时在架，层位做快照）
CREATE TABLE IF NOT EXISTS pad_mold_reserve_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    reserve_id BIGINT NOT NULL COMMENT '预留记录ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编码（登记时快照）',
    mold_type VARCHAR(128) DEFAULT NULL COMMENT '适配模具（登记时快照）',
    layer_code VARCHAR(64) DEFAULT NULL COMMENT '预留时所在层位（登记时快照）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_pmri_reserve_id (reserve_id),
    KEY idx_pmri_pad_id (pad_id),
    KEY idx_pmri_pad_code (pad_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换模垫板预留明细表';
