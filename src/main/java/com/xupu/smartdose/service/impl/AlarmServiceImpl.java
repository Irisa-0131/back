package com.xupu.smartdose.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xupu.smartdose.dto.AlarmConfigDTO;
import com.xupu.smartdose.entity.AlarmRecord;
import com.xupu.smartdose.entity.SystemConfig;
import com.xupu.smartdose.mapper.AlarmRecordMapper;
import com.xupu.smartdose.mapper.SystemConfigMapper;
import com.xupu.smartdose.service.AlarmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService {

    private final AlarmRecordMapper alarmRecordMapper;
    private final SystemConfigMapper systemConfigMapper;

    @Override
    public IPage<AlarmRecord> getAlarmPage(int page, int size, Integer isHandled, Integer alarmLevel) {
        Page<AlarmRecord> pageObj = new Page<>(page, size);
        return alarmRecordMapper.selectAlarmPage(pageObj, isHandled, alarmLevel);
    }

    @Override
    public void handleAlarm(Long alarmId, String handledBy, String note) {
        AlarmRecord record = alarmRecordMapper.selectById(alarmId);
        if (record != null) {
            record.setIsHandled(1);
            record.setHandledBy(handledBy);
            record.setHandleTime(LocalDateTime.now());
            record.setHandleNote(note);
            alarmRecordMapper.updateById(record);
        }
    }

    @Override
    public long countUnhandled() {
        return alarmRecordMapper.selectCount(
                new LambdaQueryWrapper<AlarmRecord>().eq(AlarmRecord::getIsHandled, 0)
        );
    }

    @Override
    public AlarmConfigDTO getAlarmConfig() {
        AlarmConfigDTO dto = new AlarmConfigDTO();
        dto.setAlarmEnabled(!"false".equalsIgnoreCase(getCfg("alarm_enabled", "true")));
        dto.setNh3nMax(new BigDecimal(getCfg("alarm_nh3n_max", "2.0")));
        dto.setTpMax(new BigDecimal(getCfg("alarm_tp_max", "0.5")));
        dto.setCodMax(new BigDecimal(getCfg("alarm_cod_max", "50.0")));
        dto.setTnMax(new BigDecimal(getCfg("alarm_tn_max", "15.0")));
        dto.setPhMin(new BigDecimal(getCfg("alarm_ph_min", "6.0")));
        dto.setPhMax(new BigDecimal(getCfg("alarm_ph_max", "9.0")));
        dto.setIntervalMin(Integer.parseInt(getCfg("alarm_interval_min", "30")));
        return dto;
    }

    @Override
    public void saveAlarmConfig(AlarmConfigDTO dto) {
        upsertCfg("alarm_enabled",      String.valueOf(dto.isAlarmEnabled()));
        upsertCfg("alarm_nh3n_max",     dto.getNh3nMax().toPlainString());
        upsertCfg("alarm_tp_max",       dto.getTpMax().toPlainString());
        upsertCfg("alarm_cod_max",      dto.getCodMax().toPlainString());
        upsertCfg("alarm_tn_max",       dto.getTnMax().toPlainString());
        upsertCfg("alarm_ph_min",       dto.getPhMin().toPlainString());
        upsertCfg("alarm_ph_max",       dto.getPhMax().toPlainString());
        upsertCfg("alarm_interval_min", String.valueOf(dto.getIntervalMin()));
    }

    @Override
    public void createAlarmIfNotDuplicate(AlarmRecord record, int intervalMin) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(intervalMin);
        long count = alarmRecordMapper.selectCount(
                new LambdaQueryWrapper<AlarmRecord>()
                        .eq(AlarmRecord::getAlarmSource, record.getAlarmSource())
                        .eq(AlarmRecord::getIsHandled, 0)
                        .ge(AlarmRecord::getAlarmTime, cutoff)
        );
        if (count == 0) {
            alarmRecordMapper.insert(record);
        }
    }

    // ---- helpers ----

    private String getCfg(String key, String defaultVal) {
        SystemConfig cfg = systemConfigMapper.selectById(key);
        return (cfg != null && cfg.getConfigValue() != null) ? cfg.getConfigValue() : defaultVal;
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
