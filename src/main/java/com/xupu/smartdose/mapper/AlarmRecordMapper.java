package com.xupu.smartdose.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xupu.smartdose.entity.AlarmRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlarmRecordMapper extends BaseMapper<AlarmRecord> {

    /**
     * 分页查询报警记录（支持按处理状态、报警级别过滤）
     */
    @Select("<script>" +
            "SELECT * FROM alarm_record " +
            "<where>" +
            "  <if test='isHandled != null'> AND is_handled = #{isHandled} </if>" +
            "  <if test='alarmLevel != null'> AND alarm_level = #{alarmLevel} </if>" +
            "</where>" +
            "ORDER BY alarm_time DESC" +
            "</script>")
    IPage<AlarmRecord> selectAlarmPage(Page<AlarmRecord> page,
                                       @Param("isHandled") Integer isHandled,
                                       @Param("alarmLevel") Integer alarmLevel);
}
