package com.xupu.smartdose.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xupu.smartdose.dto.*;
import com.xupu.smartdose.entity.SimulationTask;
import org.springframework.web.multipart.MultipartFile;

public interface SimulationService {

    /**
     * 上传文件并解析列名
     */
    SimulationUploadResult uploadFile(MultipartFile file);

    /**
     * 按列映射校验文件格式
     */
    SimulationValidateResult validateFile(SimulationValidateRequest req);

    /**
     * 创建模拟任务
     */
    void createTask(SimulationCreateDTO dto);

    /**
     * 分页查询任务列表
     */
    IPage<SimulationTask> getTaskPage(int page, int size,
                                      String taskNo,
                                      Integer validationStatus,
                                      String operator);

    /**
     * 执行离线预测并将结果持久化到 DB
     */
    SimulationPredictResult predict(SimulationPredictRequest req);

    /**
     * 获取任务最近一次预测结果（含参数）
     */
    SimulationPredictStoredResult getPredict(Long taskId);
}
