package com.xupu.smartdose.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 实时监测页数据 DTO
 */
@Data
public class WaterQualityDTO {

    /** 最新实测值（用于指标卡片） */
    private LatestValues latestValues;

    /** 图表数据（24小时实测+预测） */
    private ChartData chartData;

    @Data
    public static class LatestValues {
        private BigDecimal nh3n;
        private BigDecimal cod;
        private BigDecimal tp;
        private BigDecimal tn;
        private BigDecimal flow;
        private LocalDateTime recordTime;

        /** 较标准值差值（正=低于标准/绿色，负=超标/红色） */
        private BigDecimal nh3nDiff;
        private BigDecimal codDiff;
        private BigDecimal tpDiff;
        private BigDecimal tnDiff;

        /** 最近6条历史读数（从旧到新），用于卡片折线图 */
        private List<BigDecimal> nh3nHistory;
        private List<BigDecimal> codHistory;
        private List<BigDecimal> tpHistory;
        private List<BigDecimal> tnHistory;
    }

    @Data
    public static class ChartData {
        /** X轴时间标签 */
        private List<String> timeLabels;
        /** 实测值序列（超出当前时刻为null） */
        private List<BigDecimal> actualData;
        /** 预测值序列（当前时刻之前为null） */
        private List<BigDecimal> predictedData;
        /** 当前时刻标签 */
        private String currentTimeLabel;
    }
}
