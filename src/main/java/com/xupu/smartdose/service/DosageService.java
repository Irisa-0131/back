package com.xupu.smartdose.service;

import com.xupu.smartdose.dto.DosageParamDTO;
import com.xupu.smartdose.dto.DosageRecommendationDTO;

public interface DosageService {

    /** 获取所有药剂参数 */
    DosageParamDTO getAllParams();

    /** 保存所有药剂参数 */
    void saveAllParams(DosageParamDTO dto);

    /** 基于下一小时预测水质值计算各药剂推荐投加量 */
    DosageRecommendationDTO getRecommendation();

    /** 获取当前运行模式，返回 "auto" 或 "smart" */
    String getRunMode();

    /** 持久化运行模式，mode 取值 "auto" 或 "smart" */
    void setRunMode(String mode);

    /** 获取泵控延时参数（起泵/停泵，单位分钟） */
    java.util.Map<String, Integer> getPumpControlParams();

    /** 持久化泵控延时参数 */
    void savePumpControlParams(int startDelay, int stopDelay);

    /** 获取全部 5 个区域的独立延时参数，返回长度为 5 的列表，索引 0~4 对应区域 1~5 */
    java.util.List<java.util.Map<String, Integer>> getPumpAreaDelays();

    /** 持久化指定区域的延时参数（area 取值 1~5） */
    void savePumpAreaDelay(int area, int startDelay, int stopDelay);
}
