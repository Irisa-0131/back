package com.xupu.smartdose.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 加药参数实体
 */
@Data
@TableName("dosage_param")
public class DosageParam {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 药剂类型：ty=碳源, pac=PAC, fecl3=Fecl3, pam_neg=PAM-, pam_pos=PAM+
     */
    private String chemicalType;

    /** 储罐高位 (m) */
    private BigDecimal highLevel;

    /** 储罐低位 (m) */
    private BigDecimal lowLevel;

    /** 配药比例 */
    private BigDecimal dilutionRatio;

    /** 加药系数 */
    private BigDecimal dosingCoefficient;

    /** 泵标定系数（L/h/Hz）：推荐流量 ÷ 此系数 = 推荐运行频率 */
    private BigDecimal flowCoefficient;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
