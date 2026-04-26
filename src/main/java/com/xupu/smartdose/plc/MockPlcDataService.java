package com.xupu.smartdose.plc;

import com.xupu.smartdose.dto.PamStatusDTO;
import com.xupu.smartdose.dto.TankLevelDTO;
import com.xupu.smartdose.entity.PumpStatus;
import com.xupu.smartdose.entity.WaterQualityRecord;
import com.xupu.smartdose.mapper.PumpStatusMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * PLC 模拟实现（Mock）
 *
 * 在对接真实 PLC 前，该类作为占位实现返回模拟数据。
 * 对接真实 PLC 后：
 *   1. 新建 RealPlcDataService implements PlcDataService
 *   2. 将 @Primary 注解移至 RealPlcDataService
 *   3. 此 Mock 类可保留用于单元测试
 */
@Slf4j
@Primary
@Component
public class MockPlcDataService implements PlcDataService {

    private static final Random RNG = new Random();

    /** 注入 Excel 数据加载器（Spring 自动注入） */
    private final ExcelWaterDataLoader excelLoader;
    /** 注入泵状态 Mapper，读取数据库中经指令更新后的最新状态 */
    private final PumpStatusMapper pumpStatusMapper;

    public MockPlcDataService(ExcelWaterDataLoader excelLoader, PumpStatusMapper pumpStatusMapper) {
        this.excelLoader = excelLoader;
        this.pumpStatusMapper = pumpStatusMapper;
    }

    /** 在基准值上叠加 ±ratio 范围内的随机波动，保留2位小数 */
    private static BigDecimal jitter(double base, double ratio) {
        double delta = base * ratio * (RNG.nextDouble() * 2 - 1);
        return BigDecimal.valueOf(base + delta).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }

    @Override
    public List<PumpStatus> readAllPumpStatus() {
        // 优先读数据库，确保指令更新后状态一致；数据库为空时回退到硬编码初始值
        List<PumpStatus> dbList = pumpStatusMapper.selectList(null);
        if (dbList != null && !dbList.isEmpty()) {
            log.debug("[MockPLC] 读取所有泵状态（来自数据库，共{}台）", dbList.size());
            dbList.forEach(this::computeFlow);
            return dbList;
        }
        log.debug("[MockPLC] 读取所有泵状态（数据库为空，使用初始默认值）");
        return Arrays.asList(
                buildPump("PUMP_01",  "投加泵01",  1, 0, 35.0),
                buildPump("PUMP_02",  "投加泵02",  1, 0, 35.0),
                buildPump("PUMP_03",  "投加泵03",  1, 0, 35.0),
                buildPump("PUMP_04",  "投加泵04",  1, 1, 42.5),
                buildPump("PUMP_05",  "投加泵05",  1, 0, 30.0),
                buildPump("PUMP_06",  "投加泵06",  0, 0,  0.0),
                buildPump("PUMP_07",  "投加泵07",  1, 0, 38.0),
                buildPump("PUMP_08",  "投加泵08",  1, 0, 38.0),
                buildPump("PUMP_09",  "投加泵09",  0, 0,  0.0),
                buildPump("PUMP_10",  "投加泵10",  1, 0, 32.0),
                buildPump("PUMP_11",  "投加泵11",  1, 0, 40.0),
                buildPump("PUMP_12",  "投加泵12",  0, 0,  0.0),
                buildPump("PUMP_13",  "投加泵13",  1, 0, 28.0),
                buildPump("PUMP_14",  "投加泵14",  1, 0, 28.0),
                buildPump("PUMP_15",  "投加泵15",  1, 0, 36.0),
                buildPump("PUMP_16",  "投加泵16",  0, 0,  0.0),
                buildPump("PUMP_17",  "投加泵17",  1, 0, 34.0),
                buildPump("PUMP_18",  "投加泵18",  1, 0, 34.0)
        );
    }

    @Override
    public PumpStatus readPumpStatus(String pumpCode) {
        PumpStatus dbStatus = pumpStatusMapper.selectByPumpCode(pumpCode);
        if (dbStatus != null) {
            log.debug("[MockPLC] 读取泵状态: {}（来自数据库）", pumpCode);
            computeFlow(dbStatus);
            return dbStatus;
        }
        log.debug("[MockPLC] 读取泵状态: {}（数据库无记录，使用默认值）", pumpCode);
        return buildPump(pumpCode, "模拟泵-" + pumpCode, 1, 0, 40.0);
    }

    @Override
    public void writePumpCommand(String pumpCode, String command) {
        // TODO: 对接真实 PLC 时，在此处通过 Modbus/OPC UA/S7 协议写入寄存器
        log.info("[MockPLC] 写入指令 -> 泵:{} 指令:{} （模拟，未写入真实设备）", pumpCode, command);
    }

    @Override
    public void writeFrequency(String pumpCode, java.math.BigDecimal frequency) {
        // TODO: 对接真实 PLC 时，在此处写入变频器频率寄存器
        log.info("[MockPLC] 写入频率 -> 泵:{} 频率:{} Hz（模拟，未写入真实设备）", pumpCode, frequency);
    }

    @Override
    public WaterQualityRecord readWaterQuality(int waterType) {
        log.debug("[MockPLC] 读取水质数据: waterType={}", waterType);
        WaterQualityRecord r = new WaterQualityRecord();
        r.setRecordTime(LocalDateTime.now());

        if (excelLoader.hasData()) {
            // 从 Excel 逐行循环读取，同一次调用出水/进水共享同一行数据
            // [0]=NH3_in [1]=COD_in [2]=TN_in [3]=TP_in [4]=Flow_out
            // [5]=NH3_out [6]=COD_out [7]=TP_out [8]=TN_out
            double[] row = excelLoader.nextRow();
            if (waterType == 1) {   // 出水
                r.setNh3n(bd(row[5]));
                r.setCod( bd(row[6]));
                r.setTp(  bd(row[7]));
                r.setTn(  bd(row[8]));
                r.setFlow(bd(row[4]));
                r.setPh(  jitter(7.2, 0.04));
            } else {                // 进水
                r.setNh3n(bd(row[0]));
                r.setCod( bd(row[1]));
                r.setTp(  bd(row[3]));
                r.setTn(  bd(row[2]));
                r.setFlow(bd(row[4]));
                r.setPh(  jitter(7.0, 0.04));
            }
        } else {
            // Excel 未加载时回退到随机数
            if (waterType == 1) {
                r.setNh3n(jitter(0.82,  0.10));
                r.setCod( jitter(76.42, 0.10));
                r.setTp(  jitter(0.18,  0.10));
                r.setTn(  jitter(12.90, 0.10));
                r.setFlow(jitter(3000,  0.05));
                r.setPh(  jitter(7.2,   0.04));
            } else {
                r.setNh3n(jitter(18.50, 0.10));
                r.setCod( jitter(220.0, 0.10));
                r.setTp(  jitter(2.80,  0.10));
                r.setTn(  jitter(35.0,  0.10));
                r.setFlow(jitter(3000,  0.05));
                r.setPh(  jitter(7.0,   0.04));
            }
        }

        r.setWaterType(waterType);
        r.setIsPredicted(0);
        return r;
    }

    @Override
    public List<TankLevelDTO> readAllTankLevels() {
        // TODO: 对接真实 PLC 时从液位传感器寄存器读取
        return Arrays.asList(
                buildTank("TANK_01", "药剂罐1", 1.95, 0.08, 2.5, 0.5),
                buildTank("TANK_02", "药剂罐2", 1.44, 0.08, 2.5, 0.5),
                buildTank("TANK_03", "药剂罐3", 2.16, 0.08, 2.5, 0.5),
                buildTank("TANK_04", "药剂罐4", 1.05, 0.08, 2.5, 0.5),
                buildTank("TANK_05", "药剂罐5", 1.65, 0.08, 2.5, 0.5)
        );
    }

    @Override
    public List<PamStatusDTO> readPamStatus() {
        // TODO: 对接真实 PLC 时从对应寄存器读取震动器/电加热/压力等状态
        PamStatusDTO yin = new PamStatusDTO();
        yin.setSysctrl(0); yin.setVibrator(0); yin.setHeater(0);
        yin.setNoWater(0); yin.setPressureHigh(0); yin.setPressureLow(0);

        PamStatusDTO yang = new PamStatusDTO();
        yang.setSysctrl(0); yang.setVibrator(0); yang.setHeater(0);
        yang.setNoWater(0); yang.setPressureHigh(0); yang.setPressureLow(0);

        return Arrays.asList(yin, yang);
    }

    private TankLevelDTO buildTank(String id, String name,
                                   double baseLevel, double ratio,
                                   double highThreshold, double lowThreshold) {
        TankLevelDTO t = new TankLevelDTO();
        t.setTankId(id);
        t.setTankName(name);
        t.setLevel(jitter(baseLevel, ratio).setScale(1, RoundingMode.HALF_UP));
        t.setHighThreshold(BigDecimal.valueOf(highThreshold));
        t.setLowThreshold(BigDecimal.valueOf(lowThreshold));
        t.setReadTime(LocalDateTime.now());
        return t;
    }

    private void computeFlow(PumpStatus p) {
        // 运行中：flow (L/h) ≈ frequency × 2.0，加 ±5% 随机波动；停止时为 0
        if (p.getRunStatus() != null && p.getRunStatus() == 1 && p.getFrequency() != null) {
            p.setFlow(jitter(p.getFrequency().doubleValue() * 2.0, 0.05)
                    .setScale(1, RoundingMode.HALF_UP));
        } else {
            p.setFlow(BigDecimal.ZERO);
        }
    }

    private PumpStatus buildPump(String code, String name,
                                  int runStatus, int faultStatus, double freq) {
        PumpStatus p = new PumpStatus();
        p.setPumpCode(code);
        p.setPumpName(name);
        p.setRemoteMode(1);
        p.setAutoMode(1);
        p.setRunStatus(runStatus);
        p.setFaultStatus(faultStatus);
        p.setFrequency(BigDecimal.valueOf(freq));
        p.setUpdateTime(LocalDateTime.now());
        computeFlow(p);
        return p;
    }
}
