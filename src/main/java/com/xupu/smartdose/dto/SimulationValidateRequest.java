package com.xupu.smartdose.dto;

import lombok.Data;

@Data
public class SimulationValidateRequest {
    /** 服务器文件路径 */
    private String tempPath;
    /** 时间列列名（必填） */
    private String timeColumn;
    /** NH3-N 列列名（可空） */
    private String nh3nColumn;
    /** COD 列列名（可空） */
    private String codColumn;
    /** TP 列列名（可空） */
    private String tpColumn;
    /** TN 列列名（可空） */
    private String tnColumn;
    /** 流量列列名（可空） */
    private String flowColumn;
}
