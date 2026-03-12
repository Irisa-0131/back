package com.xupu.smartdose.service;

import com.xupu.smartdose.entity.PumpStatus;

import java.util.List;

public interface PumpService {

    /** 获取所有泵设备状态 */
    List<PumpStatus> getAllPumpStatus();

    /** 获取单台泵状态 */
    PumpStatus getPumpStatus(String pumpCode);

    /**
     * 向泵发送控制指令
     * @param pumpCode 泵编号
     * @param command  指令：START=启动, STOP=停止, AUTO=切自动, MANUAL=切手动
     */
    void sendCommand(String pumpCode, String command);

    /**
     * 手动设定泵的运行频率（仅手动模式下允许）
     * @param pumpCode  泵编号
     * @param frequency 目标频率 Hz，范围 [0, 50]
     */
    void setFrequency(String pumpCode, java.math.BigDecimal frequency);

    /**
     * 取消指定泵当前的延时等待任务（若存在）
     * @param pumpCode 泵编号
     */
    void cancelPending(String pumpCode);
}
