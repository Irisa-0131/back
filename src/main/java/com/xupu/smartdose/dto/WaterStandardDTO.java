package com.xupu.smartdose.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 出水水质标准值 DTO
 */
@Data
public class WaterStandardDTO {

    /** NH₃-N 标准上限 mg/L */
    private BigDecimal nh3nMax;

    /** COD 标准上限 mg/L */
    private BigDecimal codMax;

    /** TP 标准上限 mg/L */
    private BigDecimal tpMax;

    /** TN 标准上限 mg/L */
    private BigDecimal tnMax;
}
