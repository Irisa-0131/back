package com.xupu.smartdose.dto;

import lombok.Data;
import java.util.List;

@Data
public class SimulationPredictRequest {
    /** 任务ID */
    private Long taskId;
    /** 选择的预测指标，如 ["COD","TN","TP","NH3-N"] */
    private List<String> indicators;
    /** 历史窗口点数，如 6/12/24 */
    private int historyWindow;
    /** 预测跨度，如 "1h"/"2h"/"6h"/"24h" */
    private String predictionSpan;
}
