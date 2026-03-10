package com.xupu.smartdose.dto;

import lombok.Data;

import java.util.List;

@Data
public class SimulationUploadResult {
    /** 原始文件名 */
    private String fileName;
    /** 解析出的列名列表 */
    private List<String> columns;
    /** 服务器临时存储路径（用于后续校验） */
    private String tempPath;
}
