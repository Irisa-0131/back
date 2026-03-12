package com.xupu.smartdose.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 加药控制页面进/出水实时监测面板数据
 *
 * 对应前端 monitorCard 组件所需字段：
 *   flow / cod / tn / tp / ph
 */
@Data
public class WaterOverviewDTO {

    /** 实时进水监测 */
    private WaterInfo inWater;

    /** 实时出水监测 */
    private WaterInfo outWater;

    @Data
    public static class WaterInfo {
        /** 标题（用于卡片标题显示，由前端自行管理，此处仅做参考） */
        private String title;
        /** 流量 (m³/d) */
        private BigDecimal flow;
        /** NH3-N 氨氮 (mg/L) */
        private BigDecimal nh3n;
        /** COD 化学需氧量 (mg/L) */
        private BigDecimal cod;
        /** 总氮 TN (mg/L) */
        private BigDecimal tn;
        /** 总磷 TP (mg/L) */
        private BigDecimal tp;
        /** pH（当前传感器暂无，返回 null，前端展示"--"） */
        private BigDecimal ph;
    }
}
