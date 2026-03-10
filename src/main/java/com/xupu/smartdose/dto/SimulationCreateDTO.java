package com.xupu.smartdose.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SimulationCreateDTO {
    /** 服务器文件路径 */
    private String tempPath;
    /** 原始文件名 */
    private String fileName;
    /** 操作人员 */
    private String operator;
    /** 校验结果 */
    private SimulationValidateResult validateResult;
    /** 是否需要预处理 */
    private boolean needPreprocess;
    /** 预处理：对齐粒度 5min/15min/1h/1d */
    private String granularity;
    /** 预处理：缺失值策略 forward/linear/none */
    private String missingStrategy;
    /** 预处理：异常值策略 delete/median/none */
    private String outlierStrategy;
    /** 列映射关系 key=参数名(time/nh3n/cod/tp/tn/flow), value=文件列名 */
    private Map<String, String> columnMapping;
}
