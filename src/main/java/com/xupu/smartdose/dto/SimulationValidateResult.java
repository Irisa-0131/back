package com.xupu.smartdose.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SimulationValidateResult {
    /**
     * 校验等级：pass / warn / fail
     * pass = 通过；warn = 通过但有警告；fail = 失败（阻断预测）
     */
    private String level;

    /** 兼容旧字段：pass/warn → true，fail → false */
    private boolean passed;

    /** 失败原因（level=fail 时有值） */
    private String message;

    /** 有效数据条数 */
    private int dataCount;

    /** 检测到的采样频率，如 5min / 15min / 1h */
    private String sampleRate;

    /** 缺失率(%) */
    private BigDecimal missingRate;

    /** 数据时间范围描述，如 "2024年1月1日 至 2024年12月31日" */
    private String timeRange;

    /** 识别到的指标列，如 ["COD","TN","NH3-N"] */
    private List<String> indicators;

    /** 警告信息列表（level=warn 时有值） */
    private List<String> warnings;
}
