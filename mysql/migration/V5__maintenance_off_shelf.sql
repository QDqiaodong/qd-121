-- ============================================================
-- 待检/停用垫板自动离架 - 存量数据清理脚本（已有部署使用，幂等可重复执行）
-- 全新部署由 init.sql 自动建表，无需执行本脚本
-- 背景：待检（PENDING）/停用（DISABLED）垫板不再计入层位占用与配额校验，
--      登记保养时系统会自动离架；本脚本清理此前仍占用层位的存量数据，
--      避免已满层无法归还上架、下调配额被卡住，并消除无主占用。
-- 用法：mysql -u pad_user -p pad_stamping < V5__maintenance_off_shelf.sql
-- ============================================================

SET NAMES utf8mb4;

USE pad_stamping;

-- 为被清理的垫板补写解绑调整记录，保证调整台账可追溯（重复执行时无匹配行，自然幂等）
INSERT INTO layer_adjust_record
    (pad_id, pad_code, old_layer_code, new_layer_code, adjust_type, operator, adjust_reason, adjust_time)
SELECT p.id, p.pad_code, p.shelf_layer_code, NULL, 'UNBIND', '系统',
       '保养状态为待检/停用，迁移自动离架', NOW()
FROM pad_info p
WHERE p.maintenance_status IN ('PENDING', 'DISABLED')
  AND p.shelf_layer_code IS NOT NULL
  AND p.shelf_layer_code <> '';

-- 待检/停用垫板离架：清空层位绑定与绑定时间，层位占用数随即释放
UPDATE pad_info
SET shelf_layer_code = NULL,
    bind_time = NULL,
    update_time = NOW()
WHERE maintenance_status IN ('PENDING', 'DISABLED')
  AND shelf_layer_code IS NOT NULL
  AND shelf_layer_code <> '';
