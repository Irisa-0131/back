package com.xupu.smartdose.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水质记录实体（进水/出水实测值及预测值）
 */
@Data
@TableName("water_quality_record")
public class WaterQualityRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 记录时间 */
    private LocalDateTime recordTime;

    /** NH3-N 氨氮 (mg/L) */
    private BigDecimal nh3n;

    /** COD 化学需氧量 (mg/L) */
    private BigDecimal cod;

    /** TP 总磷 (mg/L) */
    private BigDecimal tp;

    /** TN 总氮 (mg/L) */
    private BigDecimal tn;

    /** 流量 (m³/d) */
    private BigDecimal flow;

    /** 水质类型：0=进水 1=出水 */
    private Integer waterType;

    /** 数据类型：0=实测值 1=预测值 */
    private Integer isPredicted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
