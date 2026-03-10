package com.xupu.smartdose.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报警记录实体
 */
@Data
@TableName("alarm_record")
public class AlarmRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报警时间 */
    private LocalDateTime alarmTime;

    /** 报警类型：water_quality=水质, device=设备, parameter=参数 */
    private String alarmType;

    /** 报警级别：1=提示 2=警告 3=严重 */
    private Integer alarmLevel;

    /** 报警来源，如 NH3-N, PUMP_01 */
    private String alarmSource;

    /** 报警内容 */
    private String alarmContent;

    /** 是否已处理：0=未处理 1=已处理 */
    private Integer isHandled;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 处理备注 */
    private String handleNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
