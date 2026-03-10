package com.xupu.smartdose.service;

import com.xupu.smartdose.dto.ModelInferRequest;
import com.xupu.smartdose.dto.ModelInferResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 模型推理服务 HTTP 客户端
 *
 * 连接到 Python FastAPI 服务（model_service/main.py）。
 * 当 model-service.url 未配置或为空时，isAvailable() 返回 false，
 * predict() 将自动降级为 V1.0 趋势外推。
 */
@Slf4j
@Component
public class ModelInferClient {

    /** Python 模型服务地址，如 http://localhost:8090 */
    @Value("${model-service.url:}")
    private String modelServiceUrl;

    private final RestTemplate restTemplate;

    public ModelInferClient(RestTemplateBuilder builder,
                            @Value("${model-service.timeout-ms:5000}") int timeoutMs) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    /**
     * 模型服务是否已配置。
     * 未配置时 predict() 侧会自动降级为 V1.0 趋势外推。
     */
    public boolean isAvailable() {
        return modelServiceUrl != null && !modelServiceUrl.isBlank();
    }

    /**
     * 调用 POST /infer，返回多步预测结果。
     * 超时或网络异常会抛出 RuntimeException，由调用方捕获并降级。
     */
    public ModelInferResponse infer(ModelInferRequest req) {
        String url = modelServiceUrl.stripTrailing() + "/infer";
        log.debug("调用模型服务: {} task_id={}", url, req.getTaskId());
        ModelInferResponse resp = restTemplate.postForObject(url, req, ModelInferResponse.class);
        if (resp == null) throw new RuntimeException("模型服务返回空响应");
        return resp;
    }

    /**
     * 健康检查（可选，用于启动时探测）
     */
    public boolean ping() {
        try {
            String url = modelServiceUrl.stripTrailing() + "/health";
            restTemplate.getForObject(url, String.class);
            return true;
        } catch (Exception e) {
            log.warn("模型服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }
}
