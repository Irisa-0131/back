package com.xupu.smartdose.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xupu.smartdose.entity.WaterQualityRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WaterQualityMapper extends BaseMapper<WaterQualityRecord> {

    /**
     * 查询指定时间范围内的水质记录（用于预测图表）
     */
    @Select("SELECT * FROM water_quality_record " +
            "WHERE water_type = #{waterType} " +
            "AND record_time BETWEEN #{startTime} AND #{endTime} " +
            "ORDER BY record_time ASC")
    List<WaterQualityRecord> selectByTimeRange(
            @Param("waterType") Integer waterType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 查询最新一条出水水质记录
     */
    @Select("SELECT * FROM water_quality_record " +
            "WHERE water_type = #{waterType} AND is_predicted = 0 " +
            "ORDER BY record_time DESC LIMIT 1")
    WaterQualityRecord selectLatest(@Param("waterType") Integer waterType);
}
