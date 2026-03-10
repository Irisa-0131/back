package com.xupu.smartdose.dto;

import lombok.Data;

@Data
public class SimulationPredictStoredResult {

    /** 预测时使用的参数 */
    private SimulationPredictRequest params;

    /** 预测结果 */
    private SimulationPredictResult result;

    /** 预测执行时间 */
    private String predictTime;
}
