package com.xupu.smartdose.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xupu.smartdose.common.Result;
import com.xupu.smartdose.dto.*;
import com.xupu.smartdose.entity.SimulationTask;
import com.xupu.smartdose.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * POST /api/simulation/upload
     * 上传文件，返回列名列表和临时路径
     */
    @PostMapping("/upload")
    public Result<SimulationUploadResult> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(simulationService.uploadFile(file));
    }

    /**
     * POST /api/simulation/validate
     * 按列映射校验文件格式
     */
    @PostMapping("/validate")
    public Result<SimulationValidateResult> validate(@RequestBody SimulationValidateRequest req) {
        return Result.success(simulationService.validateFile(req));
    }

    /**
     * POST /api/simulation/create
     * 创建模拟任务
     */
    @PostMapping("/create")
    public Result<Void> create(@RequestBody SimulationCreateDTO dto) {
        simulationService.createTask(dto);
        return Result.success();
    }

    /**
     * POST /api/simulation/predict
     * 运行离线预测（V1.0 趋势外推），结果自动持久化
     */
    @PostMapping("/predict")
    public Result<SimulationPredictResult> predict(@RequestBody SimulationPredictRequest req) {
        return Result.success(simulationService.predict(req));
    }

    /**
     * GET /api/simulation/predict/{taskId}
     * 获取任务最近一次持久化的预测结果
     */
    @GetMapping("/predict/{taskId}")
    public Result<SimulationPredictStoredResult> getPredict(@PathVariable Long taskId) {
        return Result.success(simulationService.getPredict(taskId));
    }

    /**
     * GET /api/simulation/list?page=1&size=10&taskNo=&validationStatus=&operator=
     * 分页查询任务列表
     */
    @GetMapping("/list")
    public Result<IPage<SimulationTask>> list(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String taskNo,
            @RequestParam(required = false)    Integer validationStatus,
            @RequestParam(required = false)    String operator) {
        return Result.success(simulationService.getTaskPage(page, size, taskNo, validationStatus, operator));
    }
}
