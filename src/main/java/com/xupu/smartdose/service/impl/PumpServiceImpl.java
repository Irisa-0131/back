package com.xupu.smartdose.service.impl;

import com.xupu.smartdose.entity.PumpStatus;
import com.xupu.smartdose.mapper.PumpStatusMapper;
import com.xupu.smartdose.plc.PlcDataService;
import com.xupu.smartdose.service.PumpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PumpServiceImpl implements PumpService {

    private final PumpStatusMapper pumpStatusMapper;
    private final PlcDataService plcDataService;  // PLC 接口

    @Override
    public List<PumpStatus> getAllPumpStatus() {
        // 优先从 PLC 实时读取；若 PLC 未连接则从数据库读取缓存状态
        try {
            return plcDataService.readAllPumpStatus();
        } catch (Exception e) {
            log.warn("PLC读取失败，降级至数据库缓存: {}", e.getMessage());
            return pumpStatusMapper.selectList(null);
        }
    }

    @Override
    public PumpStatus getPumpStatus(String pumpCode) {
        try {
            return plcDataService.readPumpStatus(pumpCode);
        } catch (Exception e) {
            log.warn("PLC读取失败: {}", e.getMessage());
            return pumpStatusMapper.selectByPumpCode(pumpCode);
        }
    }

    @Override
    public void sendCommand(String pumpCode, String command) {
        log.info("发送指令 -> 泵:{} 指令:{}", pumpCode, command);
        // 写入 PLC
        plcDataService.writePumpCommand(pumpCode, command);
        // 同步更新数据库状态
        PumpStatus status = pumpStatusMapper.selectByPumpCode(pumpCode);
        if (status != null) {
            switch (command) {
                case "START":  status.setRunStatus(1); break;
                case "STOP":   status.setRunStatus(0); break;
                case "AUTO":   status.setAutoMode(1);  break;
                case "MANUAL": status.setAutoMode(2);  break;
            }
            pumpStatusMapper.updateById(status);
        }
    }
}
