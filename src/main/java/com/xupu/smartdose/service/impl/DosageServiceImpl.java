package com.xupu.smartdose.service.impl;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.xupu.smartdose.dto.DosageParamDTO;
import com.xupu.smartdose.dto.DosageRecommendationDTO;
import com.xupu.smartdose.entity.DosageParam;
import com.xupu.smartdose.entity.SystemConfig;
import com.xupu.smartdose.mapper.DosageParamMapper;
import com.xupu.smartdose.mapper.SystemConfigMapper;
import com.xupu.smartdose.service.DosageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DosageServiceImpl implements DosageService {

    private final DosageParamMapper dosageParamMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final InfluxDBClient influxDBClient;

    private static final String RUN_MODE_KEY        = "run_mode";
    private static final String RUN_MODE_DEFAULT    = "auto";
    private static final String START_DELAY_KEY     = "pump_start_delay";
    private static final String STOP_DELAY_KEY      = "pump_stop_delay";

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String org;

    @Override
    public DosageParamDTO getAllParams() {
        List<DosageParam> list = dosageParamMapper.selectList(null);
        DosageParamDTO dto = new DosageParamDTO();
        for (DosageParam p : list) {
            fillDto(dto, p);
        }
        return dto;
    }

    @Override
    @Transactional
    public void saveAllParams(DosageParamDTO dto) {
        upsert("ty",      dto.getTyHigh(),      dto.getTyLow(),      dto.getTyScale(),      dto.getTyRatio(),      dto.getTyFlowCoeff());
        upsert("pac",     dto.getPacHigh(),     dto.getPacLow(),     dto.getPacScale(),     dto.getPacRatio(),     dto.getPacFlowCoeff());
        upsert("fecl3",   dto.getFecl3High(),   dto.getFecl3Low(),   dto.getFecl3Scale(),   dto.getFecl3Ratio(),   dto.getFecl3FlowCoeff());
        upsert("pam_neg", dto.getPamNegHigh(),  dto.getPamNegLow(),  dto.getPamNegScale(),  dto.getPamNegRatio(),  dto.getPamNegFlowCoeff());
        upsert("pam_pos", dto.getPamPosHigh(),  dto.getPamPosLow(),  dto.getPamPosScale(),  dto.getPamPosRatio(),  dto.getPamPosFlowCoeff());
    }

    @Override
    public DosageRecommendationDTO getRecommendation() {
        // 1. 从 InfluxDB 查下一小时的预测水质值（time > now，取最近一条预测记录）
        String flux = String.format("""
                from(bucket: "%s")
                  |> range(start: now())
                  |> filter(fn: (r) => r._measurement == "water_quality")
                  |> filter(fn: (r) => r.water_type == "out")
                  |> filter(fn: (r) => r.is_predicted == "true")
                  |> filter(fn: (r) => r._field == "nh3n" or r._field == "tp")
                  |> first()
                """, bucket);

        Map<String, BigDecimal> predictedMap = new HashMap<>();
        String basedOnTime = null;

        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            for (FluxTable table : queryApi.query(flux, org)) {
                for (FluxRecord r : table.getRecords()) {
                    String field = r.getField();
                    BigDecimal val = toBigDecimal(r.getValue());
                    if (field != null && val != null) {
                        predictedMap.put(field, val);
                        if (basedOnTime == null && r.getTime() != null) {
                            LocalDateTime t = LocalDateTime.ofInstant(r.getTime(), ZoneId.systemDefault());
                            basedOnTime = t.format(DateTimeFormatter.ofPattern("d日 HH:mm"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // InfluxDB 不可用或无预测数据时，返回空推荐值
        }

        BigDecimal predictedNh3n = predictedMap.getOrDefault("nh3n", BigDecimal.ZERO);
        BigDecimal predictedTp   = predictedMap.getOrDefault("tp",   BigDecimal.ZERO);

        // 2. 从 MySQL 读取各药剂加药系数和泵标定系数
        List<DosageParam> params = dosageParamMapper.selectList(null);
        Map<String, BigDecimal> coeffMap     = new HashMap<>();
        Map<String, BigDecimal> flowCoeffMap = new HashMap<>();
        for (DosageParam p : params) {
            coeffMap.put(p.getChemicalType(),
                    p.getDosingCoefficient() != null ? p.getDosingCoefficient() : BigDecimal.ZERO);
            flowCoeffMap.put(p.getChemicalType(), p.getFlowCoefficient());
        }

        // 3. 计算推荐量：碳源/PAM 基于 NH3-N，PAC/Fecl3 基于 TP
        DosageRecommendationDTO dto = new DosageRecommendationDTO();
        BigDecimal ty     = calc(predictedNh3n, coeffMap.get("ty"));
        BigDecimal pac    = calc(predictedTp,   coeffMap.get("pac"));
        BigDecimal fecl3  = calc(predictedTp,   coeffMap.get("fecl3"));
        BigDecimal pamNeg = calc(predictedNh3n, coeffMap.get("pam_neg"));
        BigDecimal pamPos = calc(predictedNh3n, coeffMap.get("pam_pos"));
        dto.setTy(ty); dto.setPac(pac); dto.setFecl3(fecl3);
        dto.setPamNeg(pamNeg); dto.setPamPos(pamPos);

        // 4. 推荐频率 = 推荐流量 / 泵标定系数
        dto.setTyHz    (toHz(ty,     flowCoeffMap.get("ty")));
        dto.setPacHz   (toHz(pac,    flowCoeffMap.get("pac")));
        dto.setFecl3Hz (toHz(fecl3,  flowCoeffMap.get("fecl3")));
        dto.setPamNegHz(toHz(pamNeg, flowCoeffMap.get("pam_neg")));
        dto.setPamPosHz(toHz(pamPos, flowCoeffMap.get("pam_pos")));

        dto.setPredictedNh3n(predictedNh3n);
        dto.setPredictedTp(predictedTp);
        dto.setBasedOnTime(basedOnTime);
        return dto;
    }

    private BigDecimal calc(BigDecimal predicted, BigDecimal coeff) {
        if (coeff == null || coeff.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return predicted.multiply(coeff).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toHz(BigDecimal flow, BigDecimal flowCoeff) {
        if (flow == null || flowCoeff == null || flowCoeff.compareTo(BigDecimal.ZERO) == 0) return null;
        return flow.divide(flowCoeff, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    private void upsert(String type, BigDecimal high, BigDecimal low,
                        BigDecimal scale, BigDecimal ratio, BigDecimal flowCoeff) {
        DosageParam existing = dosageParamMapper.selectByChemicalType(type);
        DosageParam param = existing != null ? existing : new DosageParam();
        param.setChemicalType(type);
        param.setHighLevel(high);
        param.setLowLevel(low);
        param.setDilutionRatio(scale);
        param.setDosingCoefficient(ratio);
        if (flowCoeff != null) param.setFlowCoefficient(flowCoeff);
        if (existing == null) {
            dosageParamMapper.insert(param);
        } else {
            dosageParamMapper.updateById(param);
        }
    }

    private void fillDto(DosageParamDTO dto, DosageParam p) {
        switch (p.getChemicalType()) {
            case "ty":
                dto.setTyHigh(p.getHighLevel()); dto.setTyLow(p.getLowLevel());
                dto.setTyScale(p.getDilutionRatio()); dto.setTyRatio(p.getDosingCoefficient());
                dto.setTyFlowCoeff(p.getFlowCoefficient()); break;
            case "pac":
                dto.setPacHigh(p.getHighLevel()); dto.setPacLow(p.getLowLevel());
                dto.setPacScale(p.getDilutionRatio()); dto.setPacRatio(p.getDosingCoefficient());
                dto.setPacFlowCoeff(p.getFlowCoefficient()); break;
            case "fecl3":
                dto.setFecl3High(p.getHighLevel()); dto.setFecl3Low(p.getLowLevel());
                dto.setFecl3Scale(p.getDilutionRatio()); dto.setFecl3Ratio(p.getDosingCoefficient());
                dto.setFecl3FlowCoeff(p.getFlowCoefficient()); break;
            case "pam_neg":
                dto.setPamNegHigh(p.getHighLevel()); dto.setPamNegLow(p.getLowLevel());
                dto.setPamNegScale(p.getDilutionRatio()); dto.setPamNegRatio(p.getDosingCoefficient());
                dto.setPamNegFlowCoeff(p.getFlowCoefficient()); break;
            case "pam_pos":
                dto.setPamPosHigh(p.getHighLevel()); dto.setPamPosLow(p.getLowLevel());
                dto.setPamPosScale(p.getDilutionRatio()); dto.setPamPosRatio(p.getDosingCoefficient());
                dto.setPamPosFlowCoeff(p.getFlowCoefficient()); break;
        }
    }

    @Override
    public String getRunMode() {
        SystemConfig cfg = systemConfigMapper.selectById(RUN_MODE_KEY);
        return cfg != null ? cfg.getConfigValue() : RUN_MODE_DEFAULT;
    }

    @Override
    public void setRunMode(String mode) {
        upsertConfig(RUN_MODE_KEY, mode);
    }

    @Override
    public java.util.Map<String, Integer> getPumpControlParams() {
        SystemConfig startCfg = systemConfigMapper.selectById(START_DELAY_KEY);
        SystemConfig stopCfg  = systemConfigMapper.selectById(STOP_DELAY_KEY);
        int startDelay = startCfg != null ? Integer.parseInt(startCfg.getConfigValue()) : 10;
        int stopDelay  = stopCfg  != null ? Integer.parseInt(stopCfg.getConfigValue())  : 30;
        java.util.Map<String, Integer> result = new java.util.HashMap<>();
        result.put("startDelay", startDelay);
        result.put("stopDelay", stopDelay);
        return result;
    }

    @Override
    public void savePumpControlParams(int startDelay, int stopDelay) {
        upsertConfig(START_DELAY_KEY, String.valueOf(startDelay));
        upsertConfig(STOP_DELAY_KEY,  String.valueOf(stopDelay));
    }

    @Override
    public java.util.List<java.util.Map<String, Integer>> getPumpAreaDelays() {
        java.util.List<java.util.Map<String, Integer>> result = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            SystemConfig startCfg = systemConfigMapper.selectById("pump_start_delay_" + i);
            SystemConfig stopCfg  = systemConfigMapper.selectById("pump_stop_delay_" + i);
            int startDelay = startCfg != null ? Integer.parseInt(startCfg.getConfigValue()) : 10;
            int stopDelay  = stopCfg  != null ? Integer.parseInt(stopCfg.getConfigValue())  : 30;
            java.util.Map<String, Integer> area = new java.util.HashMap<>();
            area.put("startDelay", startDelay);
            area.put("stopDelay",  stopDelay);
            result.add(area);
        }
        return result;
    }

    @Override
    public void savePumpAreaDelay(int area, int startDelay, int stopDelay) {
        upsertConfig("pump_start_delay_" + area, String.valueOf(startDelay));
        upsertConfig("pump_stop_delay_"  + area, String.valueOf(stopDelay));
    }

    private void upsertConfig(String key, String value) {
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
