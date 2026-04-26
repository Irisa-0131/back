package com.xupu.smartdose.service;

import com.xupu.smartdose.dto.WaterOverviewDTO;
import com.xupu.smartdose.dto.WaterQualityDTO;
import com.xupu.smartdose.dto.WaterStandardDTO;

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
     * 查询24小时图表数据：左侧 (24-predHours) 小时为实测值，右侧 predHours 小时为预测值。
     * 每个坐标点为1小时均值（15分钟采集频率下取4次平均）。
     * @param param     指标字段名（nh3n / cod / tp / tn）
     * @param predHours 预测小时数（2 / 4 / 8 / 12）
     */
    WaterQualityDTO.ChartData getChartData(String param, int predHours);

    /** 获取出水水质标准值 */
    WaterStandardDTO getWaterStandards();

    /** 保存出水水质标准值 */
    void saveWaterStandards(WaterStandardDTO dto);
}
