package com.xupu.smartdose.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xupu.smartdose.entity.AlarmRecord;
import com.xupu.smartdose.mapper.AlarmRecordMapper;
import com.xupu.smartdose.service.AlarmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService {

    private final AlarmRecordMapper alarmRecordMapper;

    @Override
    public IPage<AlarmRecord> getAlarmPage(int page, int size, Integer isHandled, Integer alarmLevel) {
        Page<AlarmRecord> pageObj = new Page<>(page, size);
        return alarmRecordMapper.selectAlarmPage(pageObj, isHandled, alarmLevel);
    }

    @Override
    public void handleAlarm(Long alarmId, String note) {
        AlarmRecord record = alarmRecordMapper.selectById(alarmId);
        if (record != null) {
            record.setIsHandled(1);
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
}
