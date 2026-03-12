package com.xupu.smartdose.service;

import com.xupu.smartdose.dto.WaterOverviewDTO;
import com.xupu.smartdose.dto.WaterQualityDTO;
import com.xupu.smartdose.dto.WaterStandardDTO;

import java.time.LocalDateTime;

public interface RealtimeService {

    /**
     * 获取出水实时监测数据（最新值 + 24小时图表数据）
     */
    WaterQualityDTO getOutWaterData();

    /**
     * 获取加药控制页面进/出水实时监测面板数据
     * 直接从 PLC 读取最新值，不涉及 InfluxDB 图表数据
     */
    WaterOverviewDTO getWaterOverview();

    /**
     * 按指定参数和时间范围查询图表数据
     * @param param     指标字段名（nh3n / cod / tp / tn）
     * @param startTime 开始时间，null 则默认今天零点
     * @param endTime   结束时间，null 则默认今天末尾
     */
    WaterQualityDTO.ChartData getChartData(String param, LocalDateTime startTime, LocalDateTime endTime);

    /** 获取出水水质标准值 */
    WaterStandardDTO getWaterStandards();

    /** 保存出水水质标准值 */
    void saveWaterStandards(WaterStandardDTO dto);
}
