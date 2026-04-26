package com.xupu.smartdose.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 泵设备状态实体
 */
@Data
@TableName("pump_status")
public class PumpStatus {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 泵编号，如 PUMP_01 */
    private String pumpCode;

    /** 泵名称 */
    private String pumpName;

    /** 远程/就地模式：1=远程 2=就地 */
    private Integer remoteMode;

    /** 自动/手动模式：1=自动 2=手动 */
    private Integer autoMode;

    /** 运行状态：0=停止 1=运行 */
    private Integer runStatus;

    /** 故障状态：0=正常 1=故障 */
    private Integer faultStatus;

    /** 运行频率 (Hz) */
    private BigDecimal frequency;

    /** 实时流量 (L/h)，由 PLC 读取或根据频率估算，不持久化 */
    @TableField(exist = false)
    private BigDecimal flow;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 待执行指令（不持久化到数据库）：
     * "START" = 启泵延时倒计时中，"STOP" = 停泵延时倒计时中，null = 无待执行任务
     */
    @TableField(exist = false)
    private String pendingCommand;
}
