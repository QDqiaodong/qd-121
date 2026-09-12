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
