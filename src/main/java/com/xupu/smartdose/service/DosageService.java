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
}
