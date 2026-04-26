package com.xupu.smartdose.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xupu.smartdose.service.impl.AuthServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Token 鉴权过滤器：校验请求头中的 Bearer token，无效则返回 401
 * 放行路径：OPTIONS 预检、/auth/login、/sse/（EventSource 不支持自定义 header）
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        return "OPTIONS".equalsIgnoreCase(method)
                || uri.endsWith("/auth/login")
                || uri.contains("/sse/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (AuthServiceImpl.getUsernameByToken(token) != null) {
                chain.doFilter(request, response);
                return;
            }
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        MAPPER.writeValue(response.getWriter(),
                Map.of("code", 401, "message", "登录已过期，请重新登录"));
    }
}
