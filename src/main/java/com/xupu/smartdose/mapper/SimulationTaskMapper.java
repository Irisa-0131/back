package com.xupu.smartdose.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xupu.smartdose.entity.SimulationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SimulationTaskMapper extends BaseMapper<SimulationTask> {

    // 列表不含 predict_result（体积大），仅返回 predict_time 供前端判断是否已预测
    @Select("<script>" +
            "SELECT id, task_no, file_name, file_path, data_count, sample_rate, missing_rate, time_range," +
            "       validation_status, validation_msg, is_preprocessed, preprocess_config, column_mapping," +
            "       operator, create_time, predict_params, predict_time" +
            " FROM simulation_task" +
            "<where>" +
            "  <if test='taskNo != null and taskNo != \"\"'> AND task_no LIKE CONCAT('%', #{taskNo}, '%') </if>" +
            "  <if test='validationStatus != null'> AND validation_status = #{validationStatus} </if>" +
            "  <if test='operator != null and operator != \"\"'> AND operator LIKE CONCAT('%', #{operator}, '%') </if>" +
            "</where>" +
            " ORDER BY create_time DESC" +
            "</script>")
    IPage<SimulationTask> selectTaskPage(Page<SimulationTask> page,
                                         @Param("taskNo") String taskNo,
                                         @Param("validationStatus") Integer validationStatus,
                                         @Param("operator") String operator);

    // 专门读取预测结果（含 predict_result 大字段）
    @Select("SELECT id, predict_params, predict_result, predict_time FROM simulation_task WHERE id = #{id}")
    SimulationTask selectPredictById(Long id);
}
