-- ============================================================
-- V10 交班盘点：pad_inventory_sheet 盘点单表 + pad_inventory_item 盘点明细表
-- 交班时库房按货架层清点在架垫板，登记班次、盘点人；提交时逐块标记账实，
-- 有差异（缺失/多出）必须登记差异原因，单据进入“待闭环”（未平账）：
-- 未闭环前该层禁止归还上架，闭环（填写处理结论）后自动恢复。
-- 幂等：可重复执行
-- ============================================================

USE pad_stamping;

-- ------------------------------------------------------------
-- 交班盘点单表（幂等）
-- covered_layers 以逗号包围分隔（如 ,A-01-01,A-01-02,），便于按层精确匹配未平账单据
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pad_inventory_sheet (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    sheet_no VARCHAR(40) NOT NULL COMMENT '盘点单号：PD + 日期时间 + 随机串',
    scope_type VARCHAR(16) NOT NULL COMMENT '盘点范围：LAYER-按层位、SHELF-按货架',
    shelf_code VARCHAR(64) DEFAULT NULL COMMENT '货架编码（按货架盘点时覆盖该货架全部层位）',
    layer_code VARCHAR(64) DEFAULT NULL COMMENT '层位编码（按层位盘点时的目标层）',
    covered_layers VARCHAR(1024) NOT NULL COMMENT '覆盖层位编码，逗号包围分隔（如 ,A-01-01,A-01-02,）',
    shift VARCHAR(16) NOT NULL COMMENT '班次：DAY-白班、MIDDLE-中班、NIGHT-夜班',
    inspector VARCHAR(64) NOT NULL COMMENT '盘点人',
    status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态：IN_PROGRESS-盘点中、SUBMITTED-待闭环（有差异未平账）、CLOSED-已闭环、CANCELLED-已取消',
    diff_reason VARCHAR(512) DEFAULT NULL COMMENT '差异原因（有差异提交时必填；未闭环前在概览/层位页/归还弹窗展示）',
    start_time DATETIME NOT NULL COMMENT '开单时间',
    submit_time DATETIME DEFAULT NULL COMMENT '提交时间（取消的单据不留痕，始终为空）',
    close_time DATETIME DEFAULT NULL COMMENT '闭环时间',
    close_conclusion VARCHAR(512) DEFAULT NULL COMMENT '闭环结论（闭环时必填；账实相符由系统补写）',
    close_operator VARCHAR(64) DEFAULT NULL COMMENT '闭环人（缺省取盘点人）',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pis_sheet_no (sheet_no),
    KEY idx_pis_status (status),
    KEY idx_pis_shift (shift),
    KEY idx_pis_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交班盘点单表';

-- ------------------------------------------------------------
-- 交班盘点明细表（幂等）
-- LEDGER 明细开单时由档案在架清单快照生成，逐块标记相符/缺失；
-- EXTRA 明细为现场多出补录，固定“多出”结果
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pad_inventory_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    sheet_id BIGINT NOT NULL COMMENT '盘点单ID',
    item_type VARCHAR(16) NOT NULL COMMENT '明细类型：LEDGER-账目在架清单、EXTRA-现场多出补录',
    pad_id BIGINT DEFAULT NULL COMMENT '垫板ID（多出明细能匹配到档案时回填，否则为空）',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    mold_type VARCHAR(128) DEFAULT NULL COMMENT '适配模具（快照）',
    layer_code VARCHAR(64) NOT NULL COMMENT '明细所属层位：账目明细取开单时在架层，多出明细为现场指定层',
    check_result VARCHAR(16) DEFAULT NULL COMMENT '点检结果：MATCH-账实相符、MISSING-缺失、EXTRA-多出（多出明细固定）',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_pii_sheet_id (sheet_id),
    KEY idx_pii_pad_code (pad_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交班盘点明细表';
