package com.xupu.smartdose.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 加药参数 DTO（与前端表单字段对应）
 */
@Data
public class DosageParamDTO {

    // 碳源 (ty)
    private BigDecimal tyHigh;
    private BigDecimal tyLow;
    private BigDecimal tyScale;
    private BigDecimal tyRatio;

    // PAC
    private BigDecimal pacHigh;
    private BigDecimal pacLow;
    private BigDecimal pacScale;
    private BigDecimal pacRatio;

    // Fecl3
    private BigDecimal fecl3High;
    private BigDecimal fecl3Low;
    private BigDecimal fecl3Scale;
    private BigDecimal fecl3Ratio;

    // PAM-
    private BigDecimal pamNegHigh;
    private BigDecimal pamNegLow;
    private BigDecimal pamNegScale;
    private BigDecimal pamNegRatio;

    // PAM+
    private BigDecimal pamPosHigh;
    private BigDecimal pamPosLow;
    private BigDecimal pamPosScale;
    private BigDecimal pamPosRatio;

    // 泵标定系数（L/h/Hz），各药剂对应泵的流量-频率线性系数
    private BigDecimal tyFlowCoeff;
    private BigDecimal pacFlowCoeff;
    private BigDecimal fecl3FlowCoeff;
    private BigDecimal pamNegFlowCoeff;
    private BigDecimal pamPosFlowCoeff;
}
