package com.xupu.smartdose.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xupu.smartdose.entity.PumpStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PumpStatusMapper extends BaseMapper<PumpStatus> {

    @Select("SELECT * FROM pump_status WHERE pump_code = #{pumpCode}")
    PumpStatus selectByPumpCode(@Param("pumpCode") String pumpCode);
}
