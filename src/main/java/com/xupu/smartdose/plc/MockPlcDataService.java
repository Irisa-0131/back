package com.xupu.smartdose.plc;

import com.xupu.smartdose.entity.PumpStatus;
import com.xupu.smartdose.entity.WaterQualityRecord;
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

    public MockPlcDataService(ExcelWaterDataLoader excelLoader) {
        this.excelLoader = excelLoader;
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
        log.debug("[MockPLC] 读取所有泵状态（模拟数据）");
        return Arrays.asList(
                buildPump("PUMP_01", "碳源投加泵A", 1, 0, 35.0),
                buildPump("PUMP_02", "碳源投加泵B", 1, 0, 35.0),
                buildPump("PUMP_03", "PAC投加泵",   1, 1, 42.5),
                buildPump("PUMP_04", "Fecl3投加泵", 0, 0, 0.0),
                buildPump("PUMP_05", "PAM-投加泵",  1, 0, 30.0)
        );
    }

    @Override
    public PumpStatus readPumpStatus(String pumpCode) {
        log.debug("[MockPLC] 读取泵状态: {}", pumpCode);
        return buildPump(pumpCode, "模拟泵-" + pumpCode, 1, 0, 40.0);
    }

    @Override
    public void writePumpCommand(String pumpCode, String command) {
        // TODO: 对接真实 PLC 时，在此处通过 Modbus/OPC UA/S7 协议写入寄存器
        log.info("[MockPLC] 写入指令 -> 泵:{} 指令:{} （模拟，未写入真实设备）", pumpCode, command);
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
            } else {                // 进水
                r.setNh3n(bd(row[0]));
                r.setCod( bd(row[1]));
                r.setTp(  bd(row[3]));
                r.setTn(  bd(row[2]));
                r.setFlow(bd(row[4]));
            }
        } else {
            // Excel 未加载时回退到随机数
            if (waterType == 1) {
                r.setNh3n(jitter(0.82,  0.10));
                r.setCod( jitter(76.42, 0.10));
                r.setTp(  jitter(0.18,  0.10));
                r.setTn(  jitter(12.90, 0.10));
                r.setFlow(jitter(3000,  0.05));
            } else {
                r.setNh3n(jitter(18.50, 0.10));
                r.setCod( jitter(220.0, 0.10));
                r.setTp(  jitter(2.80,  0.10));
                r.setTn(  jitter(35.0,  0.10));
                r.setFlow(jitter(3000,  0.05));
            }
        }

        r.setWaterType(waterType);
        r.setIsPredicted(0);
        return r;
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
        return p;
    }
}
