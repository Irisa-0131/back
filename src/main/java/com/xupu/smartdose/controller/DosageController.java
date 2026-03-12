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

    /**
     * GET /api/dosage/run-mode
     * 获取当前运行模式（"auto" | "smart"），页面加载时调用
     */
    @GetMapping("/run-mode")
    public Result<String> getRunMode() {
        return Result.success(dosageService.getRunMode());
    }

    /**
     * PUT /api/dosage/run-mode
     * 切换运行模式，body: { "mode": "auto" } 或 { "mode": "smart" }
     */
    @PutMapping("/run-mode")
    public Result<Void> setRunMode(@RequestBody java.util.Map<String, String> body) {
        String mode = body.get("mode");
        if (!"auto".equals(mode) && !"smart".equals(mode)) {
            return Result.fail(400, "mode 取值必须为 auto 或 smart");
        }
        dosageService.setRunMode(mode);
        return Result.success();
    }

    /**
     * GET /api/dosage/pump-control-params
     * 获取起泵/停泵延时参数（单位：分钟）
     */
    @GetMapping("/pump-control-params")
    public Result<java.util.Map<String, Integer>> getPumpControlParams() {
        return Result.success(dosageService.getPumpControlParams());
    }

    /**
     * PUT /api/dosage/pump-control-params
     * 保存起泵/停泵延时参数，body: { "startDelay": 10, "stopDelay": 30 }
     */
    @PutMapping("/pump-control-params")
    public Result<Void> savePumpControlParams(@RequestBody java.util.Map<String, Integer> body) {
        Integer startDelay = body.get("startDelay");
        Integer stopDelay  = body.get("stopDelay");
        if (startDelay == null || stopDelay == null || startDelay < 0 || stopDelay < 0) {
            return Result.fail(400, "延时参数不合法，须为非负整数（分钟）");
        }
        dosageService.savePumpControlParams(startDelay, stopDelay);
        return Result.success();
    }

    /**
     * GET /api/dosage/pump-area-delays
     * 获取 5 个区域各自的起泵/停泵延时参数
     */
    @GetMapping("/pump-area-delays")
    public Result<java.util.List<java.util.Map<String, Integer>>> getPumpAreaDelays() {
        return Result.success(dosageService.getPumpAreaDelays());
    }

    /**
     * PUT /api/dosage/pump-area-delays/{area}
     * 保存指定区域（1~5）的起泵/停泵延时参数
     */
    @PutMapping("/pump-area-delays/{area}")
    public Result<Void> savePumpAreaDelay(
            @PathVariable int area,
            @RequestBody java.util.Map<String, Integer> body) {
        if (area < 1 || area > 5) {
            return Result.fail(400, "区域编号须在 1~5 之间");
        }
        Integer startDelay = body.get("startDelay");
        Integer stopDelay  = body.get("stopDelay");
        if (startDelay == null || stopDelay == null || startDelay < 0 || stopDelay < 0) {
            return Result.fail(400, "延时参数不合法，须为非负整数（分钟）");
        }
        dosageService.savePumpAreaDelay(area, startDelay, stopDelay);
        return Result.success();
    }
}
