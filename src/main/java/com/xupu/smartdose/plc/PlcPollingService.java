package com.xupu.smartdose.plc;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.xupu.smartdose.entity.WaterQualityRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZoneId;

/**
 * PLC 轮询服务：定时从 PlcDataService 采集水质数据并写入 InfluxDB
 *
 * 当前使用 MockPlcDataService（每次带随机波动），对接真实 PLC 后将
 * @Primary 移至真实实现类即可，本服务无需修改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlcPollingService {

    private final PlcDataService plcDataService;
    private final InfluxDBClient influxDBClient;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String influxOrg;

    /** 启动时立即写一次，避免等第一个 60 秒才有数据 */
    @PostConstruct
    public void initWrite() {
        pollAndWrite();
    }

    /** 每 60 秒采集一次出水 + 进水数据写入 InfluxDB */
    @Scheduled(fixedRate = 60_000)
    public void pollAndWrite() {
        try {
            writePoint(plcDataService.readWaterQuality(1), "out", "false");
            writePoint(plcDataService.readWaterQuality(0), "in",  "false");
            log.debug("[PlcPolling] 水质数据写入 InfluxDB 完成");
        } catch (Exception e) {
            log.error("[PlcPolling] 写入 InfluxDB 失败: {}", e.getMessage());
        }
    }

    private void writePoint(WaterQualityRecord record, String waterType, String isPredicted) {
        Instant time = record.getRecordTime() != null
                ? record.getRecordTime().atZone(ZoneId.systemDefault()).toInstant()
                : Instant.now();

        Point point = Point.measurement("water_quality")
                .addTag("water_type",   waterType)
                .addTag("is_predicted", isPredicted)
                .time(time, WritePrecision.MS);

        if (record.getNh3n() != null) point.addField("nh3n", record.getNh3n().doubleValue());
        if (record.getCod()  != null) point.addField("cod",  record.getCod().doubleValue());
        if (record.getTp()   != null) point.addField("tp",   record.getTp().doubleValue());
        if (record.getTn()   != null) point.addField("tn",   record.getTn().doubleValue());
        if (record.getFlow() != null) point.addField("flow", record.getFlow().doubleValue());

        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        writeApi.writePoint(bucket, influxOrg, point);
    }
}
