package com.xupu.smartdose.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 报警配置 DTO
 */
@Data
public class AlarmConfigDTO {

    /** 报警总开关 */
    private boolean alarmEnabled;

    /** NH₃-N 上限 (mg/L) */
    private BigDecimal nh3nMax;

    /** TP 上限 (mg/L) */
    private BigDecimal tpMax;

    /** COD 上限 (mg/L) */
    private BigDecimal codMax;

    /** TN 上限 (mg/L) */
    private BigDecimal tnMax;

    /** pH 下限 */
    private BigDecimal phMin;

    /** pH 上限 */
    private BigDecimal phMax;

    /** 同一来源重复报警最短间隔（分钟） */
    private int intervalMin;
}
