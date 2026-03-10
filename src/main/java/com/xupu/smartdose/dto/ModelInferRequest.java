package com.xupu.smartdose.dto;

import lombok.Data;
import java.util.List;

@Data
public class ModelInferRequest {

    /** 任务标识（日志追踪用） */
    private String taskId;

    /** 预测步数 */
    private int horizonSteps;

    /** 每步时间间隔（秒） */
    private int stepSeconds;

    /** 指标名称列表，顺序与 window.x 的列对应 */
    private List<String> targets;

    /** 历史观测窗口 */
    private PredictWindow window;

    /** 归一化参数（可选） */
    private NormalizeParams normalize;

    @Data
    public static class PredictWindow {
        /** 时间戳字符串列表 */
        private List<String> timestamps;
        /** [T, K] 矩阵，null 表示缺失 */
        private List<List<Double>> x;
    }

    @Data
    public static class NormalizeParams {
        private List<Double> mean;
        private List<Double> std;
    }
}
