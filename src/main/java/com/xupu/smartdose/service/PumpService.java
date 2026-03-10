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
}
