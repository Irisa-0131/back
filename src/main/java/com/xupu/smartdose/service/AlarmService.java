package com.xupu.smartdose.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xupu.smartdose.entity.AlarmRecord;

public interface AlarmService {

    /** 分页查询报警记录 */
    IPage<AlarmRecord> getAlarmPage(int page, int size, Integer isHandled, Integer alarmLevel);

    /** 标记报警已处理 */
    void handleAlarm(Long alarmId, String note);

    /** 未处理报警数量 */
    long countUnhandled();
}
