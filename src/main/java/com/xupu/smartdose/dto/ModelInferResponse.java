package com.xupu.smartdose.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ModelInferResponse {

    private String taskId;

    /** 未来各步时间戳 */
    private List<String> predTimestamps;

    /** {指标名: [v1, v2, ...]} */
    private Map<String, List<Double>> pred;

    /** 模型版本（日志/报告用） */
    private String modelVersion;
}
