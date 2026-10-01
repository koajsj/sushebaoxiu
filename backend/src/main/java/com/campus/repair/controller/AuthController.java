package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.dto.LoginRequest;
import com.campus.repair.security.UserAuthenticationService;
import com.campus.repair.vo.AuthVO;
import com.campus.repair.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAuthenticationService authentication;

    public AuthController(UserAuthenticationService authentication) { this.authentication = authentication; }

    @PostMapping("/login")
    public Result<AuthVO> login(@Valid @RequestBody LoginRequest request,HttpServletRequest servletRequest) {
        return Result.success(authentication.login(request,servletRequest.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal UserVO user) {
        authentication.logout(user.id());
        return Result.success(null);
    }
}
