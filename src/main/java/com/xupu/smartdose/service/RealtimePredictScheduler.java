package com.xupu.smartdose.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.xupu.smartdose.dto.ModelInferRequest;
import com.xupu.smartdose.dto.ModelInferResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 实时预测调度器：每小时从 InfluxDB 读取近期实测数据，
 * 调用模型服务（或降级为趋势外推），将未来24小时预测值
 * 以 is_predicted=true 写回 InfluxDB，供前端图表展示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimePredictScheduler {

    private final InfluxDBClient influxDBClient;
    private final ModelInferClient modelInferClient;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String influxOrg;

    /** 预测指标（顺序固定，与 ModelInferRequest.targets 对应） */
    private static final List<String> INDICATORS = List.of("nh3n", "cod", "tp", "tn");

    /** 观测窗口：用过去多少小时的实测数据作为输入 */
    private static final int HISTORY_HOURS = 24;

    /** 预测步数（小时） */
    private static final int PREDICT_HOURS = 24;

    /** 每步时间间隔（秒） */
    private static final int STEP_SECONDS = 3600;

    /** 启动时立即运行一次，避免等到下一个整点才有预测数据 */
    @PostConstruct
    public void initPredict() {
        runPredict();
    }

    /** 每小时执行一次（与 PlcPollingService 的 60s 轮询错开，在整点后约 5 分钟更新预测） */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 300_000)
    public void runPredict() {
        try {
            log.debug("[RealtimePredict] 开始更新实时预测...");

            // 1. 查询过去 HISTORY_HOURS 小时出水实测数据（1h 聚合均值）
            LocalDateTime now = LocalDateTime.now();
            Instant rangeStart = now.minusHours(HISTORY_HOURS)
                    .atZone(ZoneId.systemDefault()).toInstant();
            Instant rangeEnd = now.atZone(ZoneId.systemDefault()).toInstant();

            String flux = String.format("""
                    from(bucket: "%s")
                      |> range(start: %s, stop: %s)
                      |> filter(fn: (r) => r._measurement == "water_quality")
                      |> filter(fn: (r) => r.water_type == "out")
                      |> filter(fn: (r) => r.is_predicted == "false")
                      |> filter(fn: (r) => r._field == "nh3n" or r._field == "cod" or r._field == "tp" or r._field == "tn")
                      |> aggregateWindow(every: 1h, fn: mean, createEmpty: false)
                      |> sort(columns: ["_time"])
                    """, bucket, rangeStart, rangeEnd);

            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, influxOrg);

            // 2. 整理成 time → {indicator → value} 有序 Map
            TreeMap<Instant, Map<String, Double>> timeMap = new TreeMap<>();
            for (FluxTable table : tables) {
                for (FluxRecord r : table.getRecords()) {
                    if (r.getTime() == null || r.getField() == null) continue;
                    timeMap.computeIfAbsent(r.getTime(), k -> new LinkedHashMap<>())
                           .put(r.getField(), toDouble(r.getValue()));
                }
            }

            if (timeMap.isEmpty()) {
                log.warn("[RealtimePredict] 无历史实测数据，跳过本次预测");
                return;
            }

            // 3. 构造观测窗口（时间戳列表 + [T, K] 数值矩阵）
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
            List<String> timestamps = new ArrayList<>();
            List<List<Double>> xMatrix = new ArrayList<>();

            for (Map.Entry<Instant, Map<String, Double>> entry : timeMap.entrySet()) {
                timestamps.add(fmt.format(entry.getKey()));
                List<Double> row = new ArrayList<>();
                for (String ind : INDICATORS) {
                    row.add(entry.getValue().getOrDefault(ind, null));
                }
                xMatrix.add(row);
            }

            // 4. 调用模型服务或降级到趋势外推
            LocalDateTime lastDataTime = LocalDateTime.ofInstant(
                    timeMap.lastKey(), ZoneId.systemDefault());

            List<Map<String, Double>> predictions;
            if (modelInferClient.isAvailable()) {
                try {
                    predictions = callModelService(timestamps, xMatrix);
                } catch (Exception e) {
                    log.warn("[RealtimePredict] 模型服务调用失败，降级为趋势外推: {}", e.getMessage());
                    predictions = fallbackPredict(xMatrix);
                }
            } else {
                predictions = fallbackPredict(xMatrix);
            }

            // 5. 将预测值写入 InfluxDB（is_predicted=true），从 lastDataTime+1h 开始
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            List<Point> points = new ArrayList<>();
            for (int i = 0; i < predictions.size(); i++) {
                Instant predTime = lastDataTime.plusHours(i + 1)
                        .atZone(ZoneId.systemDefault()).toInstant();
                Map<String, Double> vals = predictions.get(i);
                Point p = Point.measurement("water_quality")
                        .addTag("water_type", "out")
                        .addTag("is_predicted", "true")
                        .time(predTime, WritePrecision.MS);
                for (String ind : INDICATORS) {
                    Double v = vals.get(ind);
                    if (v != null && !Double.isNaN(v) && !Double.isInfinite(v)) {
                        p.addField(ind, v);
                    }
                }
                points.add(p);
            }
            writeApi.writePoints(bucket, influxOrg, points);
            log.info("[RealtimePredict] 已写入 {} 个预测点（基准时间: {}，预测到 {}小时后）",
                    points.size(),
                    lastDataTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    PREDICT_HOURS);

        } catch (Exception e) {
            log.error("[RealtimePredict] 实时预测异常: {}", e.getMessage(), e);
        }
    }

    // ─── 模型服务调用 ────────────────────────────────────────────

    private List<Map<String, Double>> callModelService(
            List<String> timestamps, List<List<Double>> xMatrix) {

        ModelInferRequest req = new ModelInferRequest();
        req.setTaskId("realtime-" + System.currentTimeMillis());
        req.setHorizonSteps(PREDICT_HOURS);
        req.setStepSeconds(STEP_SECONDS);
        req.setTargets(INDICATORS);

        ModelInferRequest.PredictWindow window = new ModelInferRequest.PredictWindow();
        window.setTimestamps(timestamps);
        window.setX(xMatrix);
        req.setWindow(window);

        ModelInferResponse resp = modelInferClient.infer(req);
        if (resp == null || resp.getPred() == null) {
            throw new RuntimeException("模型服务返回空预测");
        }

        int steps = resp.getPredTimestamps() != null
                ? resp.getPredTimestamps().size() : PREDICT_HOURS;
        List<Map<String, Double>> result = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            Map<String, Double> row = new LinkedHashMap<>();
            for (String ind : INDICATORS) {
                List<Double> vals = resp.getPred().get(ind);
                row.put(ind, (vals != null && i < vals.size()) ? vals.get(i) : null);
            }
            result.add(row);
        }
        return result;
    }

    // ─── V1.0 趋势外推降级 ────────────────────────────────────────

    /**
     * 最后5点线性趋势外推 + 衰减系数0.3 + 3%随机扰动
     * 与 SimulationServiceImpl.buildSeriesV1() 逻辑一致
     */
    private List<Map<String, Double>> fallbackPredict(List<List<Double>> xMatrix) {
        Random rng = new Random(System.currentTimeMillis());

        // 按列（指标）拆分历史序列
        Map<String, List<Double>> histByInd = new LinkedHashMap<>();
        for (int k = 0; k < INDICATORS.size(); k++) {
            List<Double> vals = new ArrayList<>();
            for (List<Double> row : xMatrix) {
                vals.add(k < row.size() ? row.get(k) : null);
            }
            histByInd.put(INDICATORS.get(k), vals);
        }

        // 各指标独立计算斜率和末值
        Map<String, double[]> predByInd = new LinkedHashMap<>();
        for (String ind : INDICATORS) {
            List<Double> hist = histByInd.get(ind);
            double lastVal = 0, sum = 0;
            int cnt = 0;
            for (Double v : hist) {
                if (v != null) { lastVal = v; sum += v; cnt++; }
            }
            double avg = cnt > 0 ? sum / cnt : lastVal;

            double slope = 0;
            int trendLen = Math.min(5, hist.size());
            if (trendLen >= 2) {
                Double first = null, last2 = null;
                for (int i = hist.size() - trendLen; i < hist.size(); i++) {
                    Double v = hist.get(i);
                    if (v != null) {
                        if (first == null) first = v;
                        last2 = v;
                    }
                }
                if (first != null && last2 != null) {
                    slope = (last2 - first) / trendLen * 0.3;
                }
            }

            double[] preds = new double[PREDICT_HOURS];
            double cur = lastVal;
            for (int i = 0; i < PREDICT_HOURS; i++) {
                cur += slope + (rng.nextDouble() - 0.5) * avg * 0.03;
                cur = Math.max(0, cur);
                preds[i] = Math.round(cur * 100.0) / 100.0;
            }
            predByInd.put(ind, preds);
        }

        // 组装结果
        List<Map<String, Double>> result = new ArrayList<>();
        for (int i = 0; i < PREDICT_HOURS; i++) {
            Map<String, Double> row = new LinkedHashMap<>();
            for (String ind : INDICATORS) {
                row.put(ind, predByInd.get(ind)[i]);
            }
            result.add(row);
        }
        return result;
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
