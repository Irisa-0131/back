package com.xupu.smartdose.service;

import com.xupu.smartdose.dto.AlarmConfigDTO;
import com.xupu.smartdose.dto.WaterQualityDTO;
import com.xupu.smartdose.entity.AlarmRecord;
import com.xupu.smartdose.entity.PumpStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时报警检测服务，每 5 分钟执行一次：
 *   1. 出水水质超标（NH₃-N / TP / COD / TN / pH）
 *   2. 泵设备故障（faultStatus == 1）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmCheckService {

    private final RealtimeService realtimeService;
    private final AlarmService    alarmService;
    private final PumpService     pumpService;

    @Scheduled(fixedRate = 300_000)
    public void check() {
        AlarmConfigDTO cfg = alarmService.getAlarmConfig();
        if (!cfg.isAlarmEnabled()) {
            return;
        }
        checkWaterQuality(cfg);
        checkPumpFaults(cfg.getIntervalMin());
    }

    // ── 出水水质超标 ──────────────────────────────────────────

    private void checkWaterQuality(AlarmConfigDTO cfg) {
        WaterQualityDTO wq;
        try {
            wq = realtimeService.getOutWaterData();
        } catch (Exception e) {
            log.warn("AlarmCheckService: 获取出水数据失败 - {}", e.getMessage());
            return;
        }
        if (wq == null || wq.getLatestValues() == null) return;

        WaterQualityDTO.LatestValues v = wq.getLatestValues();
        int interval = cfg.getIntervalMin();

        checkUpperLimit("NH3-N", v.getNh3n(), cfg.getNh3nMax(), interval);
        checkUpperLimit("TP",    v.getTp(),   cfg.getTpMax(),   interval);
        checkUpperLimit("COD",   v.getCod(),  cfg.getCodMax(),  interval);
        checkUpperLimit("TN",    v.getTn(),   cfg.getTnMax(),   interval);
        checkPh(v.getPh(), cfg.getPhMin(), cfg.getPhMax(), interval);
    }

    /**
     * 检查单个指标是否超过上限：超标 ≤30% → 警告(2)，>30% → 严重(3)
     */
    private void checkUpperLimit(String source, BigDecimal value, BigDecimal max, int interval) {
        if (value == null || max == null) return;
        if (value.compareTo(max) <= 0) return;

        double ratio = value.subtract(max).doubleValue() / max.doubleValue();
        int level = ratio <= 0.3 ? 2 : 3;

        AlarmRecord record = new AlarmRecord();
        record.setAlarmTime(LocalDateTime.now());
        record.setAlarmType("water_quality");
        record.setAlarmLevel(level);
        record.setAlarmSource(source);
        record.setAlarmContent(String.format(
                "%s 超标：当前值 %.3f，阈值 %.3f（超标 %.1f%%）",
                source, value, max, ratio * 100));
        record.setIsHandled(0);

        alarmService.createAlarmIfNotDuplicate(record, interval);
    }

    /**
     * pH 双向检测：低于下限 或 高于上限均报警
     */
    private void checkPh(BigDecimal ph, BigDecimal phMin, BigDecimal phMax, int interval) {
        if (ph == null) return;

        if (phMin != null && ph.compareTo(phMin) < 0) {
            double ratio = phMin.subtract(ph).doubleValue() / phMin.doubleValue();
            int level = ratio <= 0.3 ? 2 : 3;
            AlarmRecord record = new AlarmRecord();
            record.setAlarmTime(LocalDateTime.now());
            record.setAlarmType("water_quality");
            record.setAlarmLevel(level);
            record.setAlarmSource("pH");
            record.setAlarmContent(String.format(
                    "pH 偏低：当前值 %.2f，下限 %.1f（偏差 %.1f%%）",
                    ph, phMin, ratio * 100));
            record.setIsHandled(0);
            alarmService.createAlarmIfNotDuplicate(record, interval);
        }

        if (phMax != null && ph.compareTo(phMax) > 0) {
            double ratio = ph.subtract(phMax).doubleValue() / phMax.doubleValue();
            int level = ratio <= 0.3 ? 2 : 3;
            AlarmRecord record = new AlarmRecord();
            record.setAlarmTime(LocalDateTime.now());
            record.setAlarmType("water_quality");
            record.setAlarmLevel(level);
            record.setAlarmSource("pH");
            record.setAlarmContent(String.format(
                    "pH 偏高：当前值 %.2f，上限 %.1f（偏差 %.1f%%）",
                    ph, phMax, ratio * 100));
            record.setIsHandled(0);
            alarmService.createAlarmIfNotDuplicate(record, interval);
        }
    }

    // ── 泵设备故障 ────────────────────────────────────────────

    private void checkPumpFaults(int interval) {
        List<PumpStatus> pumps;
        try {
            pumps = pumpService.getAllPumpStatus();
        } catch (Exception e) {
            log.warn("AlarmCheckService: 获取泵状态失败 - {}", e.getMessage());
            return;
        }
        if (pumps == null) return;

        for (PumpStatus p : pumps) {
            if (p.getFaultStatus() != null && p.getFaultStatus() == 1) {
                AlarmRecord record = new AlarmRecord();
                record.setAlarmTime(LocalDateTime.now());
                record.setAlarmType("device");
                record.setAlarmLevel(3);
                record.setAlarmSource(p.getPumpCode());
                record.setAlarmContent(String.format(
                        "设备故障：%s（%s）发生故障，请立即检查",
                        p.getPumpCode(), p.getPumpName()));
                record.setIsHandled(0);
                alarmService.createAlarmIfNotDuplicate(record, interval);
            }
        }
    }
}
