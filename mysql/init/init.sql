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
    maintenance_status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE' COMMENT '保养状态：AVAILABLE-可用、PENDING-待检、DISABLED-停用、SCRAPPED-已报废',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pad_code (pad_code),
    KEY idx_shelf_layer_code (shelf_layer_code),
    KEY idx_maintenance_status (maintenance_status)
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
    capacity INT NOT NULL DEFAULT 10 COMMENT '容量配额：该层最多可存放的在架垫板数',
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
    adjust_type VARCHAR(32) NOT NULL COMMENT '调整类型：BIND-初始绑定、REBIND-变更绑定、UNBIND-解绑、CHECKOUT-领用离架、RETURN-归还上架、SCRAP-报废出库',
    operator VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    adjust_reason VARCHAR(512) DEFAULT NULL COMMENT '调整原因',
    adjust_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
    PRIMARY KEY (id),
    KEY idx_pad_id (pad_id),
    KEY idx_pad_code (pad_code),
    KEY idx_adjust_time (adjust_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='层位调整记录表';

-- ------------------------------------------------------------
-- 4.1 垫板领用归还记录表（幂等）
-- 领用后垫板从所在层位离架（shelf_layer_code 置空），归还时必须选择未被占用的层位重新上架
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 4.2 垫板保养记录表（幂等）
-- 每次登记保养可同时维护垫板保养状态（可用/待检/停用），并记录状态变更前后值
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 4.3 垫板报废出库记录表（幂等）
-- 保养给出“报废建议”后，库房单独做报废出库：登记批准人、去向、时间与照片；
-- 出库后垫板状态置为 SCRAPPED，层位立即释放，不能再被领用或回架
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pad_scrap_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    pad_id BIGINT NOT NULL COMMENT '垫板ID',
    pad_code VARCHAR(64) NOT NULL COMMENT '垫板编号',
    approver VARCHAR(64) NOT NULL COMMENT '批准人',
    destination VARCHAR(128) NOT NULL COMMENT '报废去向：如废品仓、回收商、就地销毁',
    scrap_time DATETIME NOT NULL COMMENT '报废出库时间',
    photo_paths VARCHAR(2048) DEFAULT NULL COMMENT '报废照片路径，多张以英文逗号分隔',
    origin_layer_code VARCHAR(64) DEFAULT NULL COMMENT '报废时所在层位（出库前原层位）',
    maintenance_record_id BIGINT DEFAULT NULL COMMENT '关联的报废建议保养记录ID',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_psr_pad_id (pad_id),
    KEY idx_psr_pad_code (pad_code),
    KEY idx_psr_destination (destination),
    KEY idx_psr_scrap_time (scrap_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='垫板报废出库记录表';

-- ------------------------------------------------------------
-- 4.4 层位封锁记录表（幂等）
-- 层位破损、待清扫或检修时单独登记封锁（原因、开始时间、经办人）；
-- 封锁期间该层禁止绑定/换绑/归还上架/导入占位，解除时必须填写结论
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 4.5 层位临时扩容记录表（幂等）
-- 旺季到货时把某层配额临时加大，登记原因、新配额、生效时段和经办人；
-- 扩容期内绑定/换绑/归还上架/导入按新配额校验，到期自动回到原配额，未到期可提前结束并写结论
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 4.6 换模垫板预留记录表（幂等）
-- 换模前库房把指定在架垫板预留给即将上线的模具，登记模具、生效时段与经办人；
-- 预留期内这些板不能被其他产线领用，也不能解绑换层；到期或手工释放后恢复可领
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 4.7 换模垫板预留明细表（幂等）
-- 预留单内的垫板清单：登记时垫板须在架且可用，层位做快照便于回看
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 4.8 交班盘点单表（幂等）
-- 交班时按货架层清点在架垫板，登记班次、盘点人；有差异（缺失/多出）必须登记差异原因，
-- 单据进入“待闭环”（未平账）：未闭环前该层禁止归还上架，闭环（填处理结论）后恢复
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
-- 4.9 交班盘点明细表（幂等）
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

-- ------------------------------------------------------------
-- 5. 预置货架分层数据（幂等，INSERT IGNORE）
-- capacity 为层位容量配额：绑定/换绑/建档/归还/导入均不得让该层超过配额
-- ------------------------------------------------------------
INSERT IGNORE INTO shelf_layer (layer_code, shelf_code, layer_name, layer_order, capacity, remark) VALUES
    ('A-01-01', 'A-01', 'A区01货架第1层', 1, 20, '重型垫板存放区'),
    ('A-01-02', 'A-01', 'A区01货架第2层', 2, 20, '重型垫板存放区'),
    ('A-01-03', 'A-01', 'A区01货架第3层', 3, 20, '重型垫板存放区'),
    ('A-02-01', 'A-02', 'A区02货架第1层', 1, 15, '中型垫板存放区'),
    ('A-02-02', 'A-02', 'A区02货架第2层', 2, 15, '中型垫板存放区'),
    ('A-02-03', 'A-02', 'A区02货架第3层', 3, 15, '中型垫板存放区'),
    ('B-01-01', 'B-01', 'B区01货架第1层', 1, 10, '轻型垫板存放区'),
    ('B-01-02', 'B-01', 'B区01货架第2层', 2, 10, '轻型垫板存放区'),
    ('B-01-03', 'B-01', 'B区01货架第3层', 3, 10, '轻型垫板存放区'),
    ('B-02-01', 'B-02', 'B区02货架第1层', 1, 5, '特殊规格存放区'),
    ('B-02-02', 'B-02', 'B区02货架第2层', 2, 5, '特殊规格存放区'),
    ('B-02-03', 'B-02', 'B区02货架第3层', 3, 5, '特殊规格存放区');

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
