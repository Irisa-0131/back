package com.xupu.smartdose.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xupu.smartdose.dto.*;
import com.xupu.smartdose.entity.SimulationTask;
import com.xupu.smartdose.mapper.SimulationTaskMapper;
import com.xupu.smartdose.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.util.Random;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationServiceImpl implements SimulationService {

    private final SimulationTaskMapper simulationTaskMapper;
    private final ObjectMapper objectMapper;

    @Value("${simulation.upload-dir}")
    private String uploadDir;

    /** 文件大小上限：20 MB */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    // 支持的时间格式（补零版优先，无补零版兜底）
    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d")
    );

    // =========== 上传 ===========

    @Override
    public SimulationUploadResult uploadFile(MultipartFile file) {
        try {
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new RuntimeException("文件超过 20MB 上限，请分割后上传");
            }

            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) Files.createDirectories(dirPath);

            String originalName = file.getOriginalFilename();
            String ext = getExtension(originalName);
            String savedName = System.currentTimeMillis() + "_" + originalName;
            Path savedPath = dirPath.resolve(savedName);
            file.transferTo(savedPath.toFile());

            List<String> columns;
            if ("csv".equalsIgnoreCase(ext)) {
                columns = readCsvHeaders(savedPath.toFile());
            } else {
                columns = readExcelHeaders(savedPath.toFile());
            }

            if (columns.isEmpty()) {
                throw new RuntimeException("文件为空或仅包含表头，请检查");
            }

            SimulationUploadResult result = new SimulationUploadResult();
            result.setFileName(originalName);
            result.setColumns(columns);
            result.setTempPath(savedPath.toString());
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败：" + e.getMessage());
        }
    }

    // =========== 四级校验 ===========

    @Override
    public SimulationValidateResult validateFile(SimulationValidateRequest req) {

        // ---- A. 文件级校验 ----
        File file = new File(req.getTempPath());
        if (!file.exists()) {
            return fail("文件不存在，请重新上传");
        }
        if (file.length() > MAX_FILE_SIZE) {
            return fail("文件超过 20MB 上限");
        }

        // ---- B. 结构级校验 ----
        if (req.getTimeColumn() == null || req.getTimeColumn().isBlank()) {
            return fail("请选择时间列");
        }

        List<Map<String, String>> rows;
        try {
            String ext = getExtension(file.getName());
            rows = "csv".equalsIgnoreCase(ext) ? readCsvRows(file) : readExcelRows(file);
        } catch (Exception e) {
            return fail("文件无法读取，可能已损坏或被加密：" + e.getMessage());
        }

        if (rows.isEmpty()) {
            return fail("文件为空或仅包含表头，无可校验数据");
        }
        if (!rows.get(0).containsKey(req.getTimeColumn())) {
            return fail("未找到时间列：" + req.getTimeColumn() + "，请检查列映射");
        }

        // 识别已映射的指标列
        List<String> indicators = new ArrayList<>();
        Map<String, String> colToIndicator = new LinkedHashMap<>();
        addIndicator(colToIndicator, indicators, req.getCodColumn(),  "COD");
        addIndicator(colToIndicator, indicators, req.getTnColumn(),   "TN");
        addIndicator(colToIndicator, indicators, req.getTpColumn(),   "TP");
        addIndicator(colToIndicator, indicators, req.getNh3nColumn(), "NH3-N");
        addIndicator(colToIndicator, indicators, req.getFlowColumn(), "流量");

        // ---- C. 内容级校验（时间列解析） ----
        List<LocalDateTime> times = new ArrayList<>();
        int parseFailCount = 0;
        Set<LocalDateTime> seenTimes = new LinkedHashSet<>();
        int dupCount = 0;

        for (Map<String, String> row : rows) {
            String timeStr = row.get(req.getTimeColumn());
            LocalDateTime t = parseTime(timeStr);
            if (t != null) {
                if (!seenTimes.add(t)) dupCount++;
                times.add(t);
            } else {
                parseFailCount++;
            }
        }

        if (times.isEmpty()) {
            return fail("时间列无法解析，请使用标准格式（如 yyyy-MM-dd HH:mm:ss）");
        }
        if (parseFailCount > rows.size() * 0.5) {
            return fail("超过 50% 的时间值无法解析，请检查时间列格式");
        }
        if (times.size() < 10) {
            return fail("有效数据不足 10 条，无法进行预测分析");
        }

        times.sort(Comparator.naturalOrder());

        // ---- D. 质量级校验 ----
        List<String> warnings = new ArrayList<>();

        int totalCount = rows.size();
        double missingRateVal = totalCount > 0 ? (double) parseFailCount / totalCount * 100 : 0;

        // 缺失率分级
        if (missingRateVal > 30) {
            return fail(String.format("数据缺失率过高（%.1f%%），超过 30%% 阈值，不建议预测", missingRateVal));
        }
        if (missingRateVal > 10) {
            warnings.add(String.format("数据缺失率 %.1f%%（10%%~30%%），预测可信度可能降低", missingRateVal));
        }

        // 重复时间戳警告
        if (dupCount > 0) {
            warnings.add("发现 " + dupCount + " 个重复时间戳，建议数据预处理去重");
        }

        // 数值列异常检查（抽样前 500 行）
        for (Map.Entry<String, String> entry : colToIndicator.entrySet()) {
            String col = entry.getKey();
            String indicator = entry.getValue();
            int negCount = 0, nanCount = 0, checked = 0;
            for (Map<String, String> row : rows) {
                if (checked++ >= 500) break;
                String val = row.getOrDefault(col, "");
                if (val.isBlank()) { nanCount++; continue; }
                try {
                    double d = Double.parseDouble(val);
                    if (d < 0) negCount++;
                } catch (NumberFormatException e) {
                    nanCount++;
                }
            }
            if (negCount > 0) {
                warnings.add(indicator + " 列存在 " + negCount + " 个负值，请确认是否合法");
            }
            if (checked > 0) {
                double colMissing = (double) nanCount / checked * 100;
                if (colMissing > 30) {
                    warnings.add(indicator + " 列缺失率 " + String.format("%.1f%%", colMissing) + "，建议数据预处理补全");
                }
            }
        }

        // 汇总
        String sampleRate = detectSampleRate(times);
        DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("yyyy年M月d日");
        String timeRange = times.get(0).format(displayFmt) + " 至 " + times.get(times.size() - 1).format(displayFmt);
        BigDecimal missingRateDec = BigDecimal.valueOf(missingRateVal).setScale(1, RoundingMode.HALF_UP);

        SimulationValidateResult result = new SimulationValidateResult();
        result.setDataCount(times.size());
        result.setSampleRate(sampleRate);
        result.setMissingRate(missingRateDec);
        result.setTimeRange(timeRange);
        result.setIndicators(indicators);
        result.setWarnings(warnings);
        result.setPassed(true);
        result.setLevel(warnings.isEmpty() ? "pass" : "warn");
        return result;
    }

    // =========== 创建任务 ===========

    @Override
    public void createTask(SimulationCreateDTO dto) {
        SimulationTask task = new SimulationTask();
        String ts = String.valueOf(System.currentTimeMillis());
        task.setTaskNo(LocalDate.now().getYear() % 100 + ts.substring(ts.length() - 6));
        task.setFileName(dto.getFileName());
        task.setFilePath(dto.getTempPath());
        task.setOperator(dto.getOperator());

        SimulationValidateResult vr = dto.getValidateResult();
        if (vr != null) {
            task.setDataCount(vr.getDataCount());
            task.setSampleRate(vr.getSampleRate());
            task.setMissingRate(vr.getMissingRate());
            task.setTimeRange(vr.getTimeRange());
            // 1=通过  2=失败  3=警告
            int status = "pass".equals(vr.getLevel()) ? 1 : "warn".equals(vr.getLevel()) ? 3 : 2;
            task.setValidationStatus(status);
            task.setValidationMsg(vr.getMessage());
        }

        task.setIsPreprocessed(dto.isNeedPreprocess() ? 1 : 0);

        try {
            if (dto.getColumnMapping() != null) {
                task.setColumnMapping(objectMapper.writeValueAsString(dto.getColumnMapping()));
            }
            if (dto.isNeedPreprocess()) {
                Map<String, String> pc = new LinkedHashMap<>();
                pc.put("granularity", dto.getGranularity());
                pc.put("missingStrategy", dto.getMissingStrategy());
                pc.put("outlierStrategy", dto.getOutlierStrategy());
                task.setPreprocessConfig(objectMapper.writeValueAsString(pc));
            }
        } catch (Exception e) {
            log.warn("JSON序列化失败", e);
        }

        simulationTaskMapper.insert(task);
    }

    // =========== 分页查询 ===========

    @Override
    public IPage<SimulationTask> getTaskPage(int page, int size,
                                              String taskNo,
                                              Integer validationStatus,
                                              String operator) {
        Page<SimulationTask> pageObj = new Page<>(page, size);
        return simulationTaskMapper.selectTaskPage(pageObj, taskNo, validationStatus, operator);
    }

    // =========== 离线预测（V1.0 趋势外推 + 预处理支持） ===========

    /** 一个时刻的所有指标值（null = 缺失） */
    private static class DataPoint {
        final LocalDateTime time;
        final Map<String, Double> values = new LinkedHashMap<>();
        DataPoint(LocalDateTime time) { this.time = time; }
    }

    @Override
    public SimulationPredictResult predict(SimulationPredictRequest req) {
        SimulationTask task = simulationTaskMapper.selectById(req.getTaskId());
        if (task == null) throw new RuntimeException("任务不存在");
        if (task.getValidationStatus() == 2) throw new RuntimeException("校验失败的任务无法运行预测");

        // 解析列映射：indicator → 原始列名 + 时间列名
        Map<String, String> indicatorToCol = new LinkedHashMap<>();
        String timeColName = null;
        if (task.getColumnMapping() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> cm = objectMapper.readValue(task.getColumnMapping(), Map.class);
                timeColName = cm.get("time");
                Map<String, String> labelMap = Map.of(
                        "cod", "COD", "tn", "TN", "tp", "TP", "nh3n", "NH3-N");
                for (Map.Entry<String, String> e : labelMap.entrySet()) {
                    if (cm.containsKey(e.getKey()) && req.getIndicators().contains(e.getValue())) {
                        indicatorToCol.put(e.getValue(), cm.get(e.getKey()));
                    }
                }
            } catch (Exception ignored) {}
        }
        if (indicatorToCol.isEmpty()) throw new RuntimeException("未找到可预测的指标列，请检查列映射");
        if (timeColName == null)      throw new RuntimeException("未找到时间列映射");

        // 读取原始文件
        File file = new File(task.getFilePath());
        if (!file.exists()) throw new RuntimeException("数据文件已被清理，无法运行预测");
        List<Map<String, String>> rawRows;
        try {
            String ext = getExtension(file.getName());
            rawRows = "csv".equalsIgnoreCase(ext) ? readCsvRows(file) : readExcelRows(file);
        } catch (Exception e) {
            throw new RuntimeException("读取数据文件失败：" + e.getMessage());
        }

        // 转换为 DataPoint 列表（解析时间 + 各指标数值）
        final String timeCol = timeColName;
        List<DataPoint> dataPoints = new ArrayList<>();
        for (Map<String, String> row : rawRows) {
            LocalDateTime t = parseTime(row.getOrDefault(timeCol, ""));
            if (t == null) continue;
            DataPoint dp = new DataPoint(t);
            for (Map.Entry<String, String> e : indicatorToCol.entrySet()) {
                try   { dp.values.put(e.getKey(), Double.parseDouble(row.getOrDefault(e.getValue(), "").trim())); }
                catch (Exception ignored) { dp.values.put(e.getKey(), null); }
            }
            dataPoints.add(dp);
        }
        dataPoints.sort(Comparator.comparing(dp -> dp.time));

        // 应用预处理（仅当任务勾选了预处理）
        if (task.getIsPreprocessed() != null && task.getIsPreprocessed() == 1
                && task.getPreprocessConfig() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> cfg = objectMapper.readValue(task.getPreprocessConfig(), Map.class);
                dataPoints = applyPreprocessing(dataPoints, indicatorToCol.keySet(), cfg);
                log.debug("预处理完成，剩余 {} 个数据点", dataPoints.size());
            } catch (Exception e) {
                log.warn("预处理失败，降级为原始数据: {}", e.getMessage());
            }
        }

        // 取最后 historyWindow 个点
        int window = Math.max(req.getHistoryWindow(), 6);
        if (dataPoints.size() > window) {
            dataPoints = dataPoints.subList(dataPoints.size() - window, dataPoints.size());
        }
        if (dataPoints.isEmpty()) throw new RuntimeException("数据不足，无法运行预测");

        int predSteps = spanToSteps(req.getPredictionSpan(), task.getSampleRate());
        DateTimeFormatter fmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String     splitTime    = dataPoints.get(dataPoints.size() - 1).time.format(fmt);
        LocalDateTime lastTime  = dataPoints.get(dataPoints.size() - 1).time;
        long stepMinutes        = sampleRateToMinutes(task.getSampleRate());

        List<SimulationPredictResult.IndicatorSeries> seriesList = new ArrayList<>();
        Random rng = new Random(42);

        for (String indicator : indicatorToCol.keySet()) {
            SimulationPredictResult.IndicatorSeries s = new SimulationPredictResult.IndicatorSeries();
            s.setIndicator(indicator);

            // 历史点
            List<SimulationPredictResult.TimeValue> history = new ArrayList<>();
            double lastVal = 0, sum = 0; int cnt = 0;
            for (DataPoint dp : dataPoints) {
                Double v = dp.values.get(indicator);
                history.add(new SimulationPredictResult.TimeValue(dp.time.format(fmt), v));
                if (v != null) { lastVal = v; sum += v; cnt++; }
            }
            s.setHistory(history);
            double avg = cnt > 0 ? sum / cnt : lastVal;

            // 趋势斜率（最后5点线性斜率 × 衰减系数0.3）
            double slope = 0;
            int trendLen = Math.min(5, history.size());
            if (trendLen >= 2) {
                double first = 0, last2 = 0;
                for (int i = history.size() - trendLen; i < history.size(); i++) {
                    Double v = history.get(i).getValue();
                    if (v != null) { if (i == history.size() - trendLen) first = v; last2 = v; }
                }
                slope = (last2 - first) / trendLen * 0.3;
            }

            // 预测点
            List<SimulationPredictResult.TimeValue> predicted = new ArrayList<>();
            double cur = lastVal;
            for (int i = 1; i <= predSteps; i++) {
                cur += slope + (rng.nextDouble() - 0.5) * avg * 0.03;
                cur = Math.max(0, cur);
                predicted.add(new SimulationPredictResult.TimeValue(
                        lastTime.plusMinutes(stepMinutes * i).format(fmt),
                        Math.round(cur * 100.0) / 100.0));
            }
            s.setPredicted(predicted);
            s.setEndValue(Math.round(cur * 100.0) / 100.0);

            if (slope > avg * 0.005)       s.setTrend("上升");
            else if (slope < -avg * 0.005) s.setTrend("下降");
            else                           s.setTrend("平稳");

            seriesList.add(s);
        }

        SimulationPredictResult result = new SimulationPredictResult();
        result.setSplitTime(splitTime);
        result.setSeries(seriesList);

        // 持久化预测结果到 DB
        try {
            SimulationTask update = new SimulationTask();
            update.setId(task.getId());
            update.setPredictParams(objectMapper.writeValueAsString(req));
            update.setPredictResult(objectMapper.writeValueAsString(result));
            update.setPredictTime(LocalDateTime.now());
            simulationTaskMapper.updateById(update);
        } catch (Exception e) {
            log.warn("预测结果持久化失败", e);
        }

        return result;
    }

    @Override
    public SimulationPredictStoredResult getPredict(Long taskId) {
        SimulationTask row = simulationTaskMapper.selectPredictById(taskId);
        if (row == null || row.getPredictResult() == null) {
            throw new RuntimeException("该任务尚未执行预测");
        }
        try {
            SimulationPredictStoredResult stored = new SimulationPredictStoredResult();
            stored.setParams(objectMapper.readValue(row.getPredictParams(), SimulationPredictRequest.class));
            stored.setResult(objectMapper.readValue(row.getPredictResult(), SimulationPredictResult.class));
            stored.setPredictTime(row.getPredictTime() != null
                    ? row.getPredictTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            return stored;
        } catch (Exception e) {
            throw new RuntimeException("预测结果解析失败：" + e.getMessage());
        }
    }

    // =========== 预处理管道 ===========

    /** 主入口：按配置顺序执行粒度对齐→缺口填充→异常值处理→缺失值填充 */
    private List<DataPoint> applyPreprocessing(List<DataPoint> points,
                                               Set<String> indicators,
                                               Map<String, String> config) {
        String granularity   = config.getOrDefault("granularity",    "15min");
        String missingStrat  = config.getOrDefault("missingStrategy","forward");
        String outlierStrat  = config.getOrDefault("outlierStrategy","none");
        long   targetMinutes = granularityToMinutes(granularity);

        points = alignGranularity(points, indicators, targetMinutes);  // 1. 桶化取均值
        points = fillTimeGaps(points, indicators, targetMinutes);       // 2. 填充缺口时刻
        if (!"none".equals(outlierStrat))                               // 3. 异常值处理
            for (String ind : indicators) applyOutlierStrategy(points, ind, outlierStrat);
        if (!"none".equals(missingStrat))                              // 4. 缺失值填充
            for (String ind : indicators) applyMissingStrategy(points, ind, missingStrat);
        return points;
    }

    /** 粒度对齐：将原始点按目标粒度分桶，桶内取均值 */
    private List<DataPoint> alignGranularity(List<DataPoint> points,
                                             Set<String> indicators,
                                             long targetMinutes) {
        Map<LocalDateTime, List<DataPoint>> buckets = new TreeMap<>();
        for (DataPoint dp : points) {
            LocalDateTime bucket = truncateToMinutes(dp.time, targetMinutes);
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(dp);
        }
        List<DataPoint> result = new ArrayList<>();
        for (Map.Entry<LocalDateTime, List<DataPoint>> entry : buckets.entrySet()) {
            DataPoint avg = new DataPoint(entry.getKey());
            for (String ind : indicators) {
                List<Double> vals = entry.getValue().stream()
                        .map(p -> p.values.get(ind))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                avg.values.put(ind, vals.isEmpty() ? null
                        : vals.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            }
            result.add(avg);
        }
        return result;
    }

    /** 补全时间序列：在相邻时刻之间插入缺失点（值为 null） */
    private List<DataPoint> fillTimeGaps(List<DataPoint> points,
                                         Set<String> indicators,
                                         long targetMinutes) {
        if (points.isEmpty()) return points;
        Map<LocalDateTime, DataPoint> byTime = new LinkedHashMap<>();
        for (DataPoint dp : points) byTime.put(dp.time, dp);
        LocalDateTime start = points.get(0).time;
        LocalDateTime end   = points.get(points.size() - 1).time;
        List<DataPoint> result = new ArrayList<>();
        LocalDateTime cur = start;
        while (!cur.isAfter(end)) {
            if (byTime.containsKey(cur)) {
                result.add(byTime.get(cur));
            } else {
                DataPoint missing = new DataPoint(cur);
                for (String ind : indicators) missing.values.put(ind, null);
                result.add(missing);
            }
            cur = cur.plusMinutes(targetMinutes);
        }
        return result;
    }

    /**
     * 异常值处理（IQR 检测）
     * delete  → 置 null，由后续 fillMissing 处理
     * median  → 用滑动窗口 [-2,+2] 内非异常值的中位数替换
     */
    private void applyOutlierStrategy(List<DataPoint> points, String indicator, String strategy) {
        List<Double> sorted = points.stream()
                .map(dp -> dp.values.get(indicator))
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
        if (sorted.size() < 4) return;
        double q1    = sorted.get(sorted.size() / 4);
        double q3    = sorted.get(sorted.size() * 3 / 4);
        double iqr   = q3 - q1;
        if (iqr == 0) return;
        double lower = q1 - 1.5 * iqr;
        double upper = q3 + 1.5 * iqr;

        for (int i = 0; i < points.size(); i++) {
            Double v = points.get(i).values.get(indicator);
            if (v == null || (v >= lower && v <= upper)) continue;
            if ("delete".equals(strategy)) {
                points.get(i).values.put(indicator, null);
            } else if ("median".equals(strategy)) {
                List<Double> window = new ArrayList<>();
                for (int j = Math.max(0, i - 2); j <= Math.min(points.size() - 1, i + 2); j++) {
                    if (j == i) continue;
                    Double wv = points.get(j).values.get(indicator);
                    if (wv != null && wv >= lower && wv <= upper) window.add(wv);
                }
                if (!window.isEmpty()) {
                    window.sort(null);
                    points.get(i).values.put(indicator, window.get(window.size() / 2));
                } else {
                    points.get(i).values.put(indicator, null);
                }
            }
        }
    }

    /**
     * 缺失值填充
     * forward → 前向填充（用前一个有效值）
     * linear  → 两端有效值之间线性插值，首部/尾部退化为后/前向填充
     */
    private void applyMissingStrategy(List<DataPoint> points, String indicator, String strategy) {
        int n = points.size();
        if ("forward".equals(strategy)) {
            Double last = null;
            for (DataPoint dp : points) {
                Double v = dp.values.get(indicator);
                if (v != null) { last = v; }
                else if (last != null) { dp.values.put(indicator, last); }
            }
        } else if ("linear".equals(strategy)) {
            int i = 0;
            while (i < n) {
                if (points.get(i).values.get(indicator) != null) { i++; continue; }
                int segStart = i;
                while (i < n && points.get(i).values.get(indicator) == null) i++;
                int segEnd = i; // 第一个非 null 的索引（或 n）
                Double before = segStart > 0  ? points.get(segStart - 1).values.get(indicator) : null;
                Double after  = segEnd   < n  ? points.get(segEnd).values.get(indicator)        : null;
                for (int j = segStart; j < segEnd; j++) {
                    if (before != null && after != null) {
                        int gap = segEnd - segStart + 1;
                        double ratio = (double)(j - segStart + 1) / gap;
                        points.get(j).values.put(indicator, before + (after - before) * ratio);
                    } else if (before != null) {
                        points.get(j).values.put(indicator, before); // 尾部前向填充
                    } else if (after != null) {
                        points.get(j).values.put(indicator, after);  // 首部后向填充
                    }
                }
            }
        }
    }

    /** 将时间戳向下截断到目标粒度的整数倍 */
    private LocalDateTime truncateToMinutes(LocalDateTime t, long targetMinutes) {
        if (targetMinutes >= 1440) return t.toLocalDate().atStartOfDay();
        long totalMins  = t.getHour() * 60L + t.getMinute();
        long bucketMins = (totalMins / targetMinutes) * targetMinutes;
        return t.toLocalDate().atStartOfDay()
                .plusHours(bucketMins / 60)
                .plusMinutes(bucketMins % 60);
    }

    private long granularityToMinutes(String granularity) {
        return switch (granularity == null ? "15min" : granularity) {
            case "5min" -> 5;
            case "1h"   -> 60;
            case "1d"   -> 1440;
            default     -> 15;
        };
    }

    private int spanToSteps(String span, String sampleRate) {
        long mins = sampleRateToMinutes(sampleRate);
        return switch (span == null ? "1h" : span) {
            case "2h"  -> (int) (120 / Math.max(mins, 1));
            case "6h"  -> (int) (360 / Math.max(mins, 1));
            case "24h" -> (int) (1440 / Math.max(mins, 1));
            default    -> (int) (60  / Math.max(mins, 1));
        };
    }

    private long sampleRateToMinutes(String sampleRate) {
        if (sampleRate == null) return 15;
        return switch (sampleRate) {
            case "5min" -> 5;
            case "1h"   -> 60;
            case "1d"   -> 1440;
            default     -> 15;
        };
    }

    // =========== 私有工具方法 ===========

    private SimulationValidateResult fail(String message) {
        SimulationValidateResult r = new SimulationValidateResult();
        r.setLevel("fail");
        r.setPassed(false);
        r.setMessage(message);
        return r;
    }

    private void addIndicator(Map<String, String> colToIndicator, List<String> indicators,
                               String col, String label) {
        if (col != null && !col.isBlank()) {
            colToIndicator.put(col, label);
            indicators.add(label);
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    private List<String> readCsvHeaders(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), detectCharset(file)))) {
            String line = br.readLine();
            if (line == null) return List.of();
            return Arrays.asList(line.split(","));
        }
    }

    private List<String> readExcelHeaders(File file) throws Exception {
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) return List.of();
            List<String> cols = new ArrayList<>();
            for (Cell cell : header) cols.add(cell.toString().trim());
            return cols;
        }
    }

    private List<Map<String, String>> readCsvRows(File file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        String charset = detectCharset(file);
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset))) {
            String headerLine = br.readLine();
            if (headerLine == null) return rows;
            String[] headers = headerLine.split(",");
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = line.split(",", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i].trim(), i < values.length ? values[i].trim() : "");
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> readExcelRows(File file) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return rows;
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) headers.add(cell.toString().trim());
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> map = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    map.put(headers.get(j), cell != null ? cellToString(cell) : "");
                }
                rows.add(map);
            }
        }
        return rows;
    }

    private String cellToString(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case STRING  -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    private LocalDateTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        for (DateTimeFormatter fmt : TIME_FORMATTERS) {
            try {
                if (fmt.toString().contains("HH")) {
                    return LocalDateTime.parse(s, fmt);
                } else {
                    return LocalDate.parse(s, fmt).atStartOfDay();
                }
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private String detectSampleRate(List<LocalDateTime> sortedTimes) {
        if (sortedTimes.size() < 2) return "未知";
        List<Long> diffs = new ArrayList<>();
        for (int i = 1; i < Math.min(sortedTimes.size(), 100); i++) {
            long minutes = java.time.Duration.between(sortedTimes.get(i - 1), sortedTimes.get(i)).toMinutes();
            if (minutes > 0) diffs.add(minutes);
        }
        if (diffs.isEmpty()) return "未知";
        diffs.sort(Comparator.naturalOrder());
        long median = diffs.get(diffs.size() / 2);
        if (median <= 5)  return "5min";
        if (median <= 15) return "15min";
        if (median <= 60) return "1h";
        return "1d";
    }

    private String detectCharset(File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] bom = new byte[3];
            int n = is.read(bom, 0, 3);
            if (n >= 3 && bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF) return "UTF-8";
            if (n >= 2 && bom[0] == (byte)0xFF && bom[1] == (byte)0xFE) return "UTF-16LE";
        } catch (IOException ignored) {}
        return "UTF-8";
    }
}
