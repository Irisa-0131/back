package com.xupu.smartdose.dto;

import lombok.Data;

/**
 * PAM 加药装置单组设备状态 DTO
 * 用于 SSE 推送；0=待机，1=运行/报警
 */
@Data
public class PamStatusDTO {

    /** 系统程控（0=待机，1=运行） */
    private int sysctrl;

    /** 震动器运行（0=待机，1=运行） */
    private int vibrator;

    /** 电加热运行（0=待机，1=运行） */
    private int heater;

    /** 无水报警（0=正常，1=报警） */
    private int noWater;

    /** 压力高报警（0=正常，1=报警） */
    private int pressureHigh;

    /** 压力低报警（0=正常，1=报警） */
    private int pressureLow;
}
