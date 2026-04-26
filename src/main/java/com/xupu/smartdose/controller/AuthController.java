package com.xupu.smartdose.controller;

import com.xupu.smartdose.common.Result;
import com.xupu.smartdose.dto.LoginDTO;
import com.xupu.smartdose.dto.LoginVO;
import com.xupu.smartdose.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    /**
     * 用户退出登录
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return Result.success(null);
    }
}

