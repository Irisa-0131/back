package com.xupu.smartdose.service.impl;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.xupu.smartdose.dto.WaterOverviewDTO;
import com.xupu.smartdose.dto.WaterQualityDTO;
import com.xupu.smartdose.dto.WaterStandardDTO;
import com.xupu.smartdose.entity.SystemConfig;
import com.xupu.smartdose.entity.WaterQualityRecord;
import com.xupu.smartdose.mapper.SystemConfigMapper;
import com.xupu.smartdose.plc.PlcDataService;
import com.xupu.smartdose.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 出水水质标准值（mg/L）——超出为红色
 */
@Service
@RequiredArgsConstructor
public class RealtimeServiceImpl implements RealtimeService {

    private static final String KEY_STD_NH3N = "standard_nh3n_max";
    private static final String KEY_STD_COD  = "standard_cod_max";
    private static final String KEY_STD_TP   = "standard_tp_max";
    private static final String KEY_STD_TN   = "standard_tn_max";

    private final InfluxDBClient    influxDBClient;
    private final PlcDataService    plcDataService;
    private final SystemConfigMapper systemConfigMapper;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String org;

    @Override
    public WaterQualityDTO getOutWaterData() {
        WaterQualityDTO dto = new WaterQualityDTO();
        QueryApi queryApi = influxDBClient.getQueryApi();

        // 1. 最新实测值：不限时间范围，取全库最新一条出水实测记录
        String latestFlux = String.format("""
                from(bucket: "%s")
                  |> range(start: 2020-01-01T00:00:00Z)
                  |> filter(fn: (r) => r._measurement == "water_quality")
                  |> filter(fn: (r) => r.water_type == "out")
                  |> filter(fn: (r) => r.is_predicted == "false")
                  |> filter(fn: (r) => r._field == "nh3n" or r._field == "cod" or r._field == "tp" or r._field == "tn" or r._field == "flow" or r._field == "ph")
                  |> last()
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                """, bucket);

        LocalDateTime dataDay = null;   // 最新数据所在的日期，用于图表基准

        List<FluxTable> latestTables = queryApi.query(latestFlux, org);
        if (!latestTables.isEmpty() && !latestTables.get(0).getRecords().isEmpty()) {
            FluxRecord record = latestTables.get(0).getRecords().get(0);
            WaterQualityDTO.LatestValues lv = new WaterQualityDTO.LatestValues();
            lv.setNh3n(toBigDecimal(record.getValueByKey("nh3n")));
            lv.setCod(toBigDecimal(record.getValueByKey("cod")));
            lv.setTp(toBigDecimal(record.getValueByKey("tp")));
            lv.setTn(toBigDecimal(record.getValueByKey("tn")));
            lv.setFlow(toBigDecimal(record.getValueByKey("flow")));
            lv.setPh(toBigDecimal(record.getValueByKey("ph")));
            if (record.getTime() != null) {
                LocalDateTime t = LocalDateTime.ofInstant(record.getTime(), ZoneId.systemDefault());
                lv.setRecordTime(t);
                dataDay = t;  // 以最新数据的时间作为图表基准
            }
            BigDecimal stdNh3n = getCfgDecimal(KEY_STD_NH3N, "5.00");
            BigDecimal stdCod  = getCfgDecimal(KEY_STD_COD,  "75.00");
            BigDecimal stdTp   = getCfgDecimal(KEY_STD_TP,   "0.50");
            BigDecimal stdTn   = getCfgDecimal(KEY_STD_TN,   "15.00");
            lv.setNh3nDiff(lv.getNh3n() != null ? stdNh3n.subtract(lv.getNh3n()) : null);
            lv.setCodDiff(lv.getCod()   != null ? stdCod.subtract(lv.getCod())   : null);
            lv.setTpDiff(lv.getTp()     != null ? stdTp.subtract(lv.getTp())     : null);
            lv.setTnDiff(lv.getTn()     != null ? stdTn.subtract(lv.getTn())     : null);

            // 查最近 6 条读数，用于卡片折线图（tail 按时间升序，最新在末尾）
            String historyFlux = String.format("""
                    from(bucket: "%s")
                      |> range(start: 2020-01-01T00:00:00Z)
                      |> filter(fn: (r) => r._measurement == "water_quality")
                      |> filter(fn: (r) => r.water_type == "out")
                      |> filter(fn: (r) => r.is_predicted == "false")
                      |> filter(fn: (r) => r._field == "nh3n" or r._field == "cod" or r._field == "tp" or r._field == "tn")
                      |> tail(n: 6)
                    """, bucket);

            Map<String, List<BigDecimal>> historyMap = new HashMap<>();
            for (FluxTable t : queryApi.query(historyFlux, org)) {
                for (FluxRecord r : t.getRecords()) {
                    String field = r.getField();
                    if (field == null) continue;
                    historyMap.computeIfAbsent(field, k -> new ArrayList<>())
                              .add(toBigDecimal(r.getValue()));
                }
            }
            lv.setNh3nHistory(historyMap.getOrDefault("nh3n", List.of()));
            lv.setCodHistory(historyMap.getOrDefault("cod",  List.of()));
            lv.setTpHistory(historyMap.getOrDefault("tp",   List.of()));
            lv.setTnHistory(historyMap.getOrDefault("tn",   List.of()));

            dto.setLatestValues(lv);
        }

        // 2. 图表数据：以最新数据所在的那天为基准（无数据时退化为今天）
        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime refTime  = (dataDay != null) ? dataDay : now;
        LocalDateTime dayStart = refTime.toLocalDate().atStartOfDay();
        int currentHour        = refTime.getHour();

        // 查今天的出水数据（实测+预测），按小时聚合 NH3-N
        String chartFlux = String.format("""
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r._measurement == "water_quality")
                  |> filter(fn: (r) => r.water_type == "out")
                  |> filter(fn: (r) => r._field == "nh3n")
                  |> aggregateWindow(every: 1h, fn: mean, createEmpty: false)
                """, bucket,
                dayStart.atZone(ZoneId.systemDefault()).toInstant(),
                dayStart.plusDays(1).atZone(ZoneId.systemDefault()).toInstant());

        // 按小时和is_predicted分组存储
        Map<Integer, BigDecimal> actualMap    = new HashMap<>();
        Map<Integer, BigDecimal> predictedMap = new HashMap<>();

        List<FluxTable> chartTables = queryApi.query(chartFlux, org);
        for (FluxTable table : chartTables) {
            String isPredicted = (String) table.getRecords().stream()
                    .findFirst().map(r -> r.getValueByKey("is_predicted")).orElse("false");
            for (FluxRecord r : table.getRecords()) {
                if (r.getTime() == null) continue;
                int hour = LocalDateTime.ofInstant(r.getTime(), ZoneId.systemDefault()).getHour();
                BigDecimal val = toBigDecimal(r.getValue());
                if ("true".equals(isPredicted)) {
                    predictedMap.put(hour, val);
                } else {
                    actualMap.put(hour, val);
                }
            }
        }

        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("d日 HH:mm");
        List<String>       timeLabels = new ArrayList<>();
        List<BigDecimal>   actual     = new ArrayList<>();
        List<BigDecimal>   predicted  = new ArrayList<>();

        for (int h = 0; h <= 24; h++) {
            timeLabels.add(dayStart.plusHours(h).format(labelFmt));
            if (h <= currentHour) {
                actual.add(actualMap.get(h % 24));
                predicted.add(h == currentHour ? predictedMap.get(h % 24) : null);
            } else {
                actual.add(null);
                predicted.add(predictedMap.get(h % 24));
            }
        }

        WaterQualityDTO.ChartData chart = new WaterQualityDTO.ChartData();
        chart.setTimeLabels(timeLabels);
        chart.setActualData(actual);
        chart.setPredictedData(predicted);
        chart.setCurrentTimeLabel(now.format(labelFmt));
        dto.setChartData(chart);

        return dto;
    }

    @Override
    public WaterQualityDTO.ChartData getChartData(String param, LocalDateTime startTime, LocalDateTime endTime) {
        // 参数名 -> InfluxDB field 名映射
        String field = switch (param == null ? "" : param.toLowerCase().replace("-", "")) {
            case "cod"  -> "cod";
            case "tp"   -> "tp";
            case "tn"   -> "tn";
            default     -> "nh3n";
        };

        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime rangeStart = (startTime != null) ? startTime : now.toLocalDate().atStartOfDay();
        LocalDateTime rangeEnd   = (endTime   != null) ? endTime   : now.toLocalDate().atTime(23, 59, 59);

        QueryApi queryApi = influxDBClient.getQueryApi();

        String chartFlux = String.format("""
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r._measurement == "water_quality")
                  |> filter(fn: (r) => r.water_type == "out")
                  |> filter(fn: (r) => r._field == "%s")
                  |> aggregateWindow(every: 1h, fn: mean, createEmpty: false)
                """, bucket,
                rangeStart.atZone(ZoneId.systemDefault()).toInstant(),
                rangeEnd.atZone(ZoneId.systemDefault()).toInstant(),
                field);

        Map<Integer, BigDecimal> actualMap    = new HashMap<>();
        Map<Integer, BigDecimal> predictedMap = new HashMap<>();

        for (FluxTable table : queryApi.query(chartFlux, org)) {
            String isPredicted = (String) table.getRecords().stream()
                    .findFirst().map(r -> r.getValueByKey("is_predicted")).orElse("false");
            for (FluxRecord r : table.getRecords()) {
                if (r.getTime() == null) continue;
                int hour = LocalDateTime.ofInstant(r.getTime(), ZoneId.systemDefault()).getHour();
                BigDecimal val = toBigDecimal(r.getValue());
                if ("true".equals(isPredicted)) {
                    predictedMap.put(hour, val);
                } else {
                    actualMap.put(hour, val);
                }
            }
        }

        // 以 rangeStart 当天为基准构造时间轴（0~24h）
        LocalDateTime dayStart    = rangeStart.toLocalDate().atStartOfDay();
        int           currentHour = now.getHour();
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("d日 HH:mm");

        List<String>     timeLabels = new ArrayList<>();
        List<BigDecimal> actual     = new ArrayList<>();
        List<BigDecimal> predicted  = new ArrayList<>();

        for (int h = 0; h <= 24; h++) {
            timeLabels.add(dayStart.plusHours(h).format(labelFmt));
            if (h <= currentHour) {
                actual.add(actualMap.get(h % 24));
                predicted.add(h == currentHour ? predictedMap.get(h % 24) : null);
            } else {
                actual.add(null);
                predicted.add(predictedMap.get(h % 24));
            }
        }

        WaterQualityDTO.ChartData chart = new WaterQualityDTO.ChartData();
        chart.setTimeLabels(timeLabels);
        chart.setActualData(actual);
        chart.setPredictedData(predicted);
        chart.setCurrentTimeLabel(now.format(labelFmt));
        return chart;
    }

    @Override
    public WaterOverviewDTO getWaterOverview() {
        WaterOverviewDTO dto = new WaterOverviewDTO();
        try {
            WaterQualityRecord inRecord  = plcDataService.readWaterQuality(0);
            WaterQualityRecord outRecord = plcDataService.readWaterQuality(1);
            dto.setInWater(toWaterInfo("实时进水监测", inRecord));
            dto.setOutWater(toWaterInfo("实时出水监测", outRecord));
        } catch (Exception e) {
            // PLC 不可用时返回空结构，前端显示 "--"
            dto.setInWater(new WaterOverviewDTO.WaterInfo());
            dto.setOutWater(new WaterOverviewDTO.WaterInfo());
        }
        return dto;
    }

    private WaterOverviewDTO.WaterInfo toWaterInfo(String title, WaterQualityRecord r) {
        WaterOverviewDTO.WaterInfo info = new WaterOverviewDTO.WaterInfo();
        info.setTitle(title);
        if (r != null) {
            info.setFlow(r.getFlow());
            info.setNh3n(r.getNh3n());
            info.setCod(r.getCod());
            info.setTn(r.getTn());
            info.setTp(r.getTp());
            // pH 传感器暂未接入，保留字段返回 null
            info.setPh(null);
        }
        return info;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── 标准值 ────────────────────────────────────────────────

    @Override
    public WaterStandardDTO getWaterStandards() {
        WaterStandardDTO dto = new WaterStandardDTO();
        dto.setNh3nMax(getCfgDecimal(KEY_STD_NH3N, "5.00"));
        dto.setCodMax(getCfgDecimal(KEY_STD_COD,   "75.00"));
        dto.setTpMax(getCfgDecimal(KEY_STD_TP,     "0.50"));
        dto.setTnMax(getCfgDecimal(KEY_STD_TN,     "15.00"));
        return dto;
    }

    @Override
    public void saveWaterStandards(WaterStandardDTO dto) {
        upsertCfg(KEY_STD_NH3N, dto.getNh3nMax().toPlainString());
        upsertCfg(KEY_STD_COD,  dto.getCodMax().toPlainString());
        upsertCfg(KEY_STD_TP,   dto.getTpMax().toPlainString());
        upsertCfg(KEY_STD_TN,   dto.getTnMax().toPlainString());
    }

    private BigDecimal getCfgDecimal(String key, String defaultVal) {
        SystemConfig cfg = systemConfigMapper.selectById(key);
        String val = (cfg != null && cfg.getConfigValue() != null) ? cfg.getConfigValue() : defaultVal;
        try { return new BigDecimal(val); } catch (NumberFormatException e) { return new BigDecimal(defaultVal); }
    }

    private void upsertCfg(String key, String value) {
        SystemConfig cfg = systemConfigMapper.selectById(key);
        if (cfg == null) {
            cfg = new SystemConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            systemConfigMapper.insert(cfg);
        } else {
            cfg.setConfigValue(value);
            systemConfigMapper.updateById(cfg);
        }
    }
}
