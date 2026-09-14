-- H2 (MODE=MySQL) 测试建表脚本，结构与 mysql/init/init.sql 保持一致
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
    maintenance_status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE' COMMENT '保养状态：AVAILABLE-可用、PENDING-待检、DISABLED-停用、SCRAPPED-已报废',
    stock_status VARCHAR(16) NOT NULL DEFAULT 'OFFICIAL' COMMENT '库存状态：QUARANTINE-到货待检、OFFICIAL-正式在库、REJECTED-判退离库',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pad_code (pad_code)
);

CREATE TABLE IF NOT EXISTS shelf_layer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    layer_code VARCHAR(64) NOT NULL COMMENT '分层编码',
    shelf_code VARCHAR(64) NOT NULL COMMENT '货架编码',
    layer_name VARCHAR(128) DEFAULT NULL COMMENT '分层名称',
    layer_order INT DEFAULT 0 COMMENT '层序号',
    capacity INT NOT NULL DEFAULT 10 COMMENT '容量配额：该层最多可存放的在架垫板数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_layer_code (layer_code)
);
CREATE INDEX IF NOT EXISTS idx_shelf_code ON shelf_layer (shelf_code);

CREATE TABLE IF NOT EXISTS layer_adjust_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    old_layer_code VARCHAR(64) DEFAULT NULL COMMENT '原分层编码',
    new_layer_code VARCHAR(64) DEFAULT NULL COMMENT '新分层编码',
    adjust_type VARCHAR(32) NOT NULL COMMENT '调整类型',
    operator VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    adjust_reason VARCHAR(512) DEFAULT NULL COMMENT '调整原因',
    adjust_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
    PRIMARY KEY (id)
);

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
    origin_layer_code VARCHAR(64) DEFAULT NULL COMMENT '领用时所在层位',
    return_layer_code VARCHAR(64) DEFAULT NULL COMMENT '归还层位',
    status VARCHAR(16) NOT NULL DEFAULT 'BORROWED' COMMENT '状态',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_pbr_status ON pad_borrow_record (status);

CREATE TABLE IF NOT EXISTS pad_maintenance_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    maintenance_type VARCHAR(64) NOT NULL COMMENT '保养类型',
    handler VARCHAR(64) NOT NULL COMMENT '处理人',
    maintenance_time DATETIME NOT NULL COMMENT '保养时间',
    maintenance_result VARCHAR(64) NOT NULL COMMENT '保养结果',
    status_before VARCHAR(24) DEFAULT NULL COMMENT '保养前状态',
    status_after VARCHAR(24) NOT NULL COMMENT '保养后状态',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_pmr_pad_id ON pad_maintenance_record (pad_id);
CREATE INDEX IF NOT EXISTS idx_pmr_status_after ON pad_maintenance_record (status_after);
CREATE INDEX IF NOT EXISTS idx_pmr_maintenance_time ON pad_maintenance_record (maintenance_time);

CREATE TABLE IF NOT EXISTS pad_scrap_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    approver VARCHAR(64) NOT NULL COMMENT '批准人',
    destination VARCHAR(128) NOT NULL COMMENT '报废去向',
    scrap_time DATETIME NOT NULL COMMENT '报废出库时间',
    photo_paths VARCHAR(2048) DEFAULT NULL COMMENT '报废照片路径，多张以英文逗号分隔',
    origin_layer_code VARCHAR(64) DEFAULT NULL COMMENT '报废时所在层位',
    maintenance_record_id BIGINT DEFAULT NULL COMMENT '关联的报废建议保养记录ID',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_psr_pad_id (pad_id)
);
CREATE INDEX IF NOT EXISTS idx_psr_pad_code ON pad_scrap_record (pad_code);
CREATE INDEX IF NOT EXISTS idx_psr_destination ON pad_scrap_record (destination);
CREATE INDEX IF NOT EXISTS idx_psr_scrap_time ON pad_scrap_record (scrap_time);

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
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_lbr_layer_code ON layer_block_record (layer_code);
CREATE INDEX IF NOT EXISTS idx_lbr_status ON layer_block_record (status);
CREATE INDEX IF NOT EXISTS idx_lbr_start_time ON layer_block_record (start_time);

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
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_lcer_layer_code ON layer_capacity_expand_record (layer_code);
CREATE INDEX IF NOT EXISTS idx_lcer_status ON layer_capacity_expand_record (status);
CREATE INDEX IF NOT EXISTS idx_lcer_start_time ON layer_capacity_expand_record (start_time);
CREATE INDEX IF NOT EXISTS idx_lcer_end_time ON layer_capacity_expand_record (end_time);

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
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_pmrr_mold_code ON pad_mold_reserve_record (mold_code);
CREATE INDEX IF NOT EXISTS idx_pmrr_status ON pad_mold_reserve_record (status);
CREATE INDEX IF NOT EXISTS idx_pmrr_start_time ON pad_mold_reserve_record (start_time);
CREATE INDEX IF NOT EXISTS idx_pmrr_end_time ON pad_mold_reserve_record (end_time);

CREATE TABLE IF NOT EXISTS pad_mold_reserve_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    reserve_id BIGINT NOT NULL COMMENT '预留记录ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编码（登记时快照）',
    mold_type VARCHAR(128) DEFAULT NULL COMMENT '适配模具（登记时快照）',
    layer_code VARCHAR(64) DEFAULT NULL COMMENT '预留时所在层位（登记时快照）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_pmri_reserve_id ON pad_mold_reserve_item (reserve_id);
CREATE INDEX IF NOT EXISTS idx_pmri_pad_id ON pad_mold_reserve_item (pad_id);
CREATE INDEX IF NOT EXISTS idx_pmri_pad_code ON pad_mold_reserve_item (pad_code);

CREATE TABLE IF NOT EXISTS pad_inventory_sheet (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    sheet_no VARCHAR(40) NOT NULL COMMENT '盘点单号：PD + 日期时间 + 随机串',
    scope_type VARCHAR(16) NOT NULL COMMENT '盘点范围：LAYER-按层位、SHELF-按货架',
    shelf_code VARCHAR(64) DEFAULT NULL COMMENT '货架编码',
    layer_code VARCHAR(64) DEFAULT NULL COMMENT '层位编码（按层位盘点时的目标层）',
    covered_layers VARCHAR(1024) NOT NULL COMMENT '覆盖层位编码，逗号包围分隔',
    shift VARCHAR(16) NOT NULL COMMENT '班次：DAY-白班、MIDDLE-中班、NIGHT-夜班',
    inspector VARCHAR(64) NOT NULL COMMENT '盘点人',
    status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态：IN_PROGRESS-盘点中、SUBMITTED-待闭环、CLOSED-已闭环、CANCELLED-已取消',
    diff_reason VARCHAR(512) DEFAULT NULL COMMENT '差异原因（有差异提交时必填）',
    start_time DATETIME NOT NULL COMMENT '开单时间',
    submit_time DATETIME DEFAULT NULL COMMENT '提交时间',
    close_time DATETIME DEFAULT NULL COMMENT '闭环时间',
    close_conclusion VARCHAR(512) DEFAULT NULL COMMENT '闭环结论',
    close_operator VARCHAR(64) DEFAULT NULL COMMENT '闭环人',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pis_sheet_no ON pad_inventory_sheet (sheet_no);
CREATE INDEX IF NOT EXISTS idx_pis_status ON pad_inventory_sheet (status);
CREATE INDEX IF NOT EXISTS idx_pis_shift ON pad_inventory_sheet (shift);
CREATE INDEX IF NOT EXISTS idx_pis_start_time ON pad_inventory_sheet (start_time);

CREATE TABLE IF NOT EXISTS pad_inventory_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    sheet_id BIGINT NOT NULL COMMENT '盘点单ID',
    item_type VARCHAR(16) NOT NULL COMMENT '明细类型：LEDGER-账目在架清单、EXTRA-现场多出补录',
    pad_id BIGINT DEFAULT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    mold_type VARCHAR(128) DEFAULT NULL COMMENT '适配模具（快照）',
    layer_code VARCHAR(64) NOT NULL COMMENT '明细所属层位',
    check_result VARCHAR(16) DEFAULT NULL COMMENT '点检结果：MATCH-相符、MISSING-缺失、EXTRA-多出',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_pii_sheet_id ON pad_inventory_item (sheet_id);
CREATE INDEX IF NOT EXISTS idx_pii_pad_code ON pad_inventory_item (pad_code);

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
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pab_batch_no ON pad_arrival_batch (batch_no);
CREATE INDEX IF NOT EXISTS idx_pab_status ON pad_arrival_batch (status);
CREATE INDEX IF NOT EXISTS idx_pab_arrival_time ON pad_arrival_batch (arrival_time);

CREATE TABLE IF NOT EXISTS pad_arrival_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_id BIGINT NOT NULL COMMENT '到货批次ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号（登记时快照）',
    mold_type VARCHAR(128) DEFAULT NULL COMMENT '适配模具（登记时快照）',
    target_layer_code VARCHAR(64) DEFAULT NULL COMMENT '质检通过转入的正式层位（判定时记录）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_pai_batch_id ON pad_arrival_item (batch_id);
CREATE INDEX IF NOT EXISTS idx_pai_pad_id ON pad_arrival_item (pad_id);
CREATE INDEX IF NOT EXISTS idx_pai_pad_code ON pad_arrival_item (pad_code);
