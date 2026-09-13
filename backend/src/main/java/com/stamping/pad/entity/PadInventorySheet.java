package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Data
@TableName("pad_inventory_sheet")
public class PadInventorySheet {

    /** 盘点中：开单后逐块标记账实，可补录多出垫板 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 待闭环：已提交且存在差异（缺失/多出），未闭环前覆盖层位禁止归还上架 */
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    /** 已闭环：账实相符自动闭环，或差异处理完毕手工闭环 */
    public static final String STATUS_CLOSED = "CLOSED";
    /** 已取消：盘点中单据作废，不影响层位 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单号：PD + 日期时间 + 随机串 */
    private String sheetNo;

    /** LAYER-按层位、SHELF-按货架 */
    private String scopeType;

    /** 货架编码（按货架盘点时覆盖该货架全部层位） */
    private String shelfCode;

    /** 层位编码（按层位盘点时的目标层） */
    private String layerCode;

    /** 覆盖层位编码，逗号包围分隔（如 ,A-01-01,A-01-02,），便于按层精确匹配与展示 */
    private String coveredLayers;

    /** 班次：DAY-白班、MIDDLE-中班、NIGHT-夜班 */
    private String shift;

    /** 盘点人 */
    private String inspector;

    /** IN_PROGRESS-盘点中、SUBMITTED-待闭环、CLOSED-已闭环、CANCELLED-已取消 */
    private String status;

    /** 差异原因：有差异（缺失/多出）提交时必填，未闭环前在概览/层位页/归还弹窗展示 */
    private String diffReason;

    /** 开单时间 */
    private LocalDateTime startTime;

    /** 提交时间（取消的单据不留痕，始终为空） */
    private LocalDateTime submitTime;

    /** 闭环时间 */
    private LocalDateTime closeTime;

    /** 闭环结论（手工闭环必填；账实相符提交时由系统补写） */
    private String closeConclusion;

    /** 闭环处理人（差异闭环必填；账实相符提交时由系统补写为盘点人） */
    private String closeOperator;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ---------------- 联表/聚合字段（非本表字段） ----------------

    /** 覆盖层位数 */
    @TableField(exist = false)
    private Integer layerCount;

    /** 明细总数 */
    @TableField(exist = false)
    private Integer totalCount;

    /** 账实相符数 */
    @TableField(exist = false)
    private Integer matchCount;

    /** 缺失数 */
    @TableField(exist = false)
    private Integer missingCount;

    /** 多出数 */
    @TableField(exist = false)
    private Integer extraCount;

    /** 已标记数（多出明细不参与逐块标记） */
    @TableField(exist = false)
    private Integer markedCount;

    /** 覆盖层位编码列表（由 coveredLayers 派生，便于前端展示与层位匹配） */
    public List<String> getCoveredLayerList() {
        if (coveredLayers == null || coveredLayers.isBlank()) {
            return List.of();
        }
        return Arrays.stream(coveredLayers.split(","))
                .filter(code -> !code.isBlank())
                .toList();
    }
}
