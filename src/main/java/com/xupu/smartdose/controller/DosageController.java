package com.xupu.smartdose.controller;

import com.xupu.smartdose.common.Result;
import com.xupu.smartdose.dto.DosageParamDTO;
import com.xupu.smartdose.dto.DosageRecommendationDTO;
import com.xupu.smartdose.service.DosageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dosage")
@RequiredArgsConstructor
public class DosageController {

    private final DosageService dosageService;

    /**
     * GET /api/dosage/params
     * 获取所有药剂参数（页面打开时加载表单默认值）
     */
    @GetMapping("/params")
    public Result<DosageParamDTO> getParams() {
        return Result.success(dosageService.getAllParams());
    }

    /**
     * PUT /api/dosage/params
     * 保存参数设置表单
     */
    @PutMapping("/params")
    public Result<Void> saveParams(@RequestBody DosageParamDTO dto) {
        dosageService.saveAllParams(dto);
        return Result.success();
    }

    /**
     * GET /api/dosage/recommendation
     * 基于下一小时预测水质值返回各药剂推荐投加量
     */
    @GetMapping("/recommendation")
    public Result<DosageRecommendationDTO> getRecommendation() {
        return Result.success(dosageService.getRecommendation());
    }
}
