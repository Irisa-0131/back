package com.xupu.smartdose.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xupu.smartdose.dto.AlarmConfigDTO;
import com.xupu.smartdose.entity.AlarmRecord;

public interface AlarmService {

    /** 分页查询报警记录 */
    IPage<AlarmRecord> getAlarmPage(int page, int size, Integer isHandled, Integer alarmLevel);

    /** 标记报警已处理 */
    void handleAlarm(Long alarmId, String handledBy, String note);

    /** 未处理报警数量 */
    long countUnhandled();

    /** 获取报警配置 */
    AlarmConfigDTO getAlarmConfig();

    /** 保存报警配置 */
    void saveAlarmConfig(AlarmConfigDTO dto);

    /**
     * 去重写入报警记录：若该 source 在 intervalMin 分钟内已有未处理同类告警则跳过
     */
    void createAlarmIfNotDuplicate(AlarmRecord record, int intervalMin);
}
