package com.xupu.smartdose.plc;

import com.xupu.smartdose.entity.PumpStatus;
import com.xupu.smartdose.entity.WaterQualityRecord;

import java.util.List;

/**
 * PLC 数据接口（抽象层）
 *
 * 实际项目中根据 PLC 品牌实现该接口：
 *   - 西门子 S7 系列 → 使用 S7connector 或 Moka7
 *   - 欧姆龙 → 使用 FINS 协议
 *   - 施耐德 → 使用 Modbus TCP
 *   - OPC UA → 使用 Eclipse Milo
 *
 * 当前注入的是 MockPlcDataService（模拟实现），
 * 对接真实 PLC 时将 @Primary 注解移至真实实现类即可。
 */
public interface PlcDataService {

    /**
     * 读取所有泵的实时状态
     */
    List<PumpStatus> readAllPumpStatus();

    /**
     * 读取指定泵的实时状态
     * @param pumpCode 泵编号，如 PUMP_01
     */
    PumpStatus readPumpStatus(String pumpCode);

    /**
     * 向指定泵写入控制指令
     * @param pumpCode 泵编号
     * @param command  START=启动 | STOP=停止 | AUTO=切自动 | MANUAL=切手动
     */
    void writePumpCommand(String pumpCode, String command);

    /**
     * 读取实时水质数据（传感器直采）
     * @param waterType 0=进水 1=出水
     */
    WaterQualityRecord readWaterQuality(int waterType);
}
