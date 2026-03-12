package com.xupu.smartdose.service.impl;

import com.xupu.smartdose.entity.PumpStatus;
import com.xupu.smartdose.entity.SystemConfig;
import com.xupu.smartdose.mapper.PumpStatusMapper;
import com.xupu.smartdose.mapper.SystemConfigMapper;
import com.xupu.smartdose.plc.PlcDataService;
import com.xupu.smartdose.service.PumpDelayScheduler;
import com.xupu.smartdose.service.PumpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PumpServiceImpl implements PumpService {

    private static final String START_DELAY_KEY = "pump_start_delay";
    private static final String STOP_DELAY_KEY  = "pump_stop_delay";

    private final PumpStatusMapper    pumpStatusMapper;
    private final SystemConfigMapper  systemConfigMapper;
    private final PlcDataService      plcDataService;
    private final PumpDelayScheduler  pumpDelayScheduler;

    // ── 查询 ──────────────────────────────────────────────

    @Override
    public List<PumpStatus> getAllPumpStatus() {
        List<PumpStatus> list;
        try {
            list = plcDataService.readAllPumpStatus();
        } catch (Exception e) {
            log.warn("PLC读取失败，降级至数据库缓存: {}", e.getMessage());
            list = pumpStatusMapper.selectList(null);
        }
        // 补充每台泵的待执行指令
        list.forEach(p -> p.setPendingCommand(pumpDelayScheduler.getPendingCommand(p.getPumpCode())));
        return list;
    }

    @Override
    public PumpStatus getPumpStatus(String pumpCode) {
        PumpStatus status;
        try {
            status = plcDataService.readPumpStatus(pumpCode);
        } catch (Exception e) {
            log.warn("PLC读取失败: {}", e.getMessage());
            status = pumpStatusMapper.selectByPumpCode(pumpCode);
        }
        if (status != null) {
            status.setPendingCommand(pumpDelayScheduler.getPendingCommand(pumpCode));
        }
        return status;
    }

    // ── 指令 ──────────────────────────────────────────────

    @Override
    public void sendCommand(String pumpCode, String command) {
        // 1. 从 DB 读取当前状态做安全校验
        PumpStatus status = pumpStatusMapper.selectByPumpCode(pumpCode);
        if (status == null) {
            throw new RuntimeException("泵编号不存在: " + pumpCode);
        }

        // 2. 安全前置校验
        if (status.getFaultStatus() != null && status.getFaultStatus() == 1) {
            throw new RuntimeException("设备故障中，禁止操作，请先排除故障");
        }
        if (status.getRemoteMode() != null && status.getRemoteMode() == 2) {
            throw new RuntimeException("设备处于就地模式，请先将控制权切换至远程");
        }

        // AUTO / MANUAL：立即执行，不走延时
        if ("AUTO".equals(command) || "MANUAL".equals(command)) {
            plcDataService.writePumpCommand(pumpCode, command);
            status.setAutoMode("AUTO".equals(command) ? 1 : 2);
            pumpStatusMapper.updateById(status);
            log.info("模式切换完成 -> 泵:{} 指令:{}", pumpCode, command);
            return;
        }

        // START / STOP：走延时通道
        if ("START".equals(command)) {
            if (status.getRunStatus() != null && status.getRunStatus() == 1
                    && pumpDelayScheduler.getPendingCommand(pumpCode) == null) {
                throw new RuntimeException("设备已在运行中，无需重复启动");
            }
            int delayMinutes = getDelayConfig(START_DELAY_KEY, 10);
            log.info("收到启泵指令 -> 泵:{} 延时:{}分钟后执行", pumpCode, delayMinutes);
            pumpDelayScheduler.schedule(pumpCode, delayMinutes, "START", () -> {
                plcDataService.writePumpCommand(pumpCode, "START");
                PumpStatus s = pumpStatusMapper.selectByPumpCode(pumpCode);
                if (s != null) {
                    s.setRunStatus(1);
                    pumpStatusMapper.updateById(s);
                }
            });

        } else if ("STOP".equals(command)) {
            if (status.getRunStatus() != null && status.getRunStatus() == 0
                    && pumpDelayScheduler.getPendingCommand(pumpCode) == null) {
                throw new RuntimeException("设备已停止，无需重复停止");
            }
            int delayMinutes = getDelayConfig(STOP_DELAY_KEY, 30);
            log.info("收到停泵指令 -> 泵:{} 延时:{}分钟后执行", pumpCode, delayMinutes);
            pumpDelayScheduler.schedule(pumpCode, delayMinutes, "STOP", () -> {
                plcDataService.writePumpCommand(pumpCode, "STOP");
                PumpStatus s = pumpStatusMapper.selectByPumpCode(pumpCode);
                if (s != null) {
                    s.setRunStatus(0);
                    pumpStatusMapper.updateById(s);
                }
            });
        }
    }

    // ── 频率 ──────────────────────────────────────────────

    @Override
    public void setFrequency(String pumpCode, BigDecimal frequency) {
        PumpStatus status = pumpStatusMapper.selectByPumpCode(pumpCode);
        if (status == null) {
            throw new RuntimeException("泵编号不存在: " + pumpCode);
        }
        if (status.getFaultStatus() != null && status.getFaultStatus() == 1) {
            throw new RuntimeException("设备故障中，禁止调节频率");
        }
        if (status.getAutoMode() == null || status.getAutoMode() != 2) {
            throw new RuntimeException("请先切换为手动模式，再调节频率");
        }
        log.info("设定频率 -> 泵:{} 频率:{} Hz", pumpCode, frequency);
        plcDataService.writeFrequency(pumpCode, frequency);
        status.setFrequency(frequency);
        pumpStatusMapper.updateById(status);
    }

    // ── 取消延时 ───────────────────────────────────────────

    @Override
    public void cancelPending(String pumpCode) {
        if (pumpDelayScheduler.getPendingCommand(pumpCode) == null) {
            throw new RuntimeException("当前无待执行的延时任务");
        }
        pumpDelayScheduler.cancel(pumpCode);
        log.info("操作员取消延时任务 -> 泵:{}", pumpCode);
    }

    // ── 内部工具 ───────────────────────────────────────────

    private int getDelayConfig(String key, int defaultValue) {
        try {
            SystemConfig cfg = systemConfigMapper.selectById(key);
            return cfg != null ? Integer.parseInt(cfg.getConfigValue()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
