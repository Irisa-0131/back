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

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
