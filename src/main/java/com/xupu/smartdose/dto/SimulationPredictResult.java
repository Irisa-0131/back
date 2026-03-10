package com.xupu.smartdose.dto;

import lombok.Data;
import java.util.List;

@Data
public class SimulationPredictResult {

    /** 历史数据结束时刻（预测起点） */
    private String splitTime;

    /** 每个指标的历史 + 预测序列 */
    private List<IndicatorSeries> series;

    @Data
    public static class IndicatorSeries {
        /** 指标名称，如 "COD" */
        private String indicator;
        /** 历史观测点 */
        private List<TimeValue> history;
        /** 预测点 */
        private List<TimeValue> predicted;
        /** 末端预测值（最后一个预测点） */
        private Double endValue;
        /** 趋势描述：上升/下降/平稳 */
        private String trend;
    }

    @Data
    public static class TimeValue {
        private String time;
        private Double value;

        public TimeValue(String time, Double value) {
            this.time = time;
            this.value = value;
        }
    }
}
