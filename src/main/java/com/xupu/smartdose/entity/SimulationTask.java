package com.xupu.smartdose.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// predict_result 体积较大，列表查询时排除，仅通过专用接口获取

@Data
@TableName("simulation_task")
public class SimulationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号 */
    private String taskNo;

    /** 原始文件名 */
    private String fileName;

    /** 服务器存储路径 */
    private String filePath;

    /** 数据条数 */
    private Integer dataCount;

    /** 采样频率，如 5min / 15min / 1h */
    private String sampleRate;

    /** 缺失率(%) */
    private BigDecimal missingRate;

    /** 数据时间范围描述 */
    private String timeRange;

    /** 校验状态：1=通过 2=失败 */
    private Integer validationStatus;

    /** 校验失败原因 */
    private String validationMsg;

    /** 是否已预处理：0=否 1=是 */
    private Integer isPreprocessed;

    /** JSON 预处理配置 */
    private String preprocessConfig;

    /** JSON 列映射关系 */
    private String columnMapping;

    /** 操作人员 */
    private String operator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** JSON 预测参数（指标/窗口/跨度） */
    private String predictParams;

    /** JSON 预测结果（体积较大，列表查询时不返回） */
    @TableField(select = false)
    private String predictResult;

    /** 最近一次预测时间（非空即已预测） */
    private LocalDateTime predictTime;
}
