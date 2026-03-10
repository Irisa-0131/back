package com.xupu.smartdose.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预测加药推荐值 DTO
 */
@Data
public class DosageRecommendationDTO {

    /** 碳源推荐投加量（mg/L） */
    private BigDecimal ty;

    /** PAC 推荐投加量（mg/L） */
    private BigDecimal pac;

    /** Fecl3 推荐投加量（mg/L） */
    private BigDecimal fecl3;

    /** PAM- 推荐投加量（mg/L） */
    private BigDecimal pamNeg;

    /** PAM+ 推荐投加量（mg/L） */
    private BigDecimal pamPos;

    /** 推荐所基于的预测 NH3-N 值 */
    private BigDecimal predictedNh3n;

    /** 推荐所基于的预测 TP 值 */
    private BigDecimal predictedTp;

    /** 预测值对应的时间标签（如"10日 14:00"） */
    private String basedOnTime;
}
