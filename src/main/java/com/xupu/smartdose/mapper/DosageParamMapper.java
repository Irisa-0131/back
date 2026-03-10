package com.xupu.smartdose.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xupu.smartdose.entity.DosageParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DosageParamMapper extends BaseMapper<DosageParam> {

    @Select("SELECT * FROM dosage_param WHERE chemical_type = #{chemicalType}")
    DosageParam selectByChemicalType(@Param("chemicalType") String chemicalType);
}
