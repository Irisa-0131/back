package com.xupu.smartdose.controller;

import com.xupu.smartdose.common.Result;
import com.xupu.smartdose.dto.TankLevelDTO;
import com.xupu.smartdose.dto.WaterOverviewDTO;
import com.xupu.smartdose.dto.WaterQualityDTO;
import com.xupu.smartdose.dto.WaterStandardDTO;
import com.xupu.smartdose.plc.PlcDataService;
import com.xupu.smartdose.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private final RealtimeService realtimeService;
    private final PlcDataService plcDataService;

    /**
     * GET /api/realtime/water-quality
     * 返回出水水质实时值（指标卡片）+ 24小时图表数据
     */
    @GetMapping("/water-quality")
    public Result<WaterQualityDTO> getWaterQuality() {
        return Result.success(realtimeService.getOutWaterData());
    }

    /**
     * GET /api/realtime/water-overview
     * 加药控制页面进/出水实时监测面板数据（直接读 PLC，轻量接口）
     */
    @GetMapping("/water-overview")
    public Result<WaterOverviewDTO> getWaterOverview() {
        return Result.success(realtimeService.getWaterOverview());
    }

    /**
     * GET /api/realtime/tank-levels
     * 获取所有储罐当前液位（前端轮询兜底，SSE 为主推送通道）
     */
    @GetMapping("/tank-levels")
    public Result<List<TankLevelDTO>> getTankLevels() {
        return Result.success(plcDataService.readAllTankLevels());
    }

    /**
     * GET /api/realtime/chart?param=nh3n&startTime=...&endTime=...
     * 按指定参数和时间范围返回图表数据
     */
    @GetMapping("/chart")
    public Result<WaterQualityDTO.ChartData> getChart(
            @RequestParam(defaultValue = "nh3n") String param,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(realtimeService.getChartData(param, startTime, endTime));
    }

    /**
     * GET /api/realtime/standards
     * 获取出水水质标准值
     */
    @GetMapping("/standards")
    public Result<WaterStandardDTO> getStandards() {
        return Result.success(realtimeService.getWaterStandards());
    }

    /**
     * PUT /api/realtime/standards
     * 保存出水水质标准值
     */
    @PutMapping("/standards")
    public Result<Void> saveStandards(@RequestBody WaterStandardDTO dto) {
        realtimeService.saveWaterStandards(dto);
        return Result.success();
    }
}
