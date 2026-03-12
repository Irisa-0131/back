package com.xupu.smartdose.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 储罐液位 DTO
 * 用于 SSE 推送和前端展示
 */
@Data
public class TankLevelDTO {

    /** 罐体编号，如 TANK_01 */
    private String tankId;

    /** 罐体名称，如 药剂罐A */
    private String tankName;

    /** 液位，单位 m，范围 0~3 */
    private BigDecimal level;

    /** 高液位阈值，单位 m（超过则报警），默认 2.5 */
    private BigDecimal highThreshold;

    /** 低液位阈值，单位 m（低于则报警），默认 0.5 */
    private BigDecimal lowThreshold;

    /** 采集时间 */
    private LocalDateTime readTime;
}
