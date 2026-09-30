package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.vo.UserVO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    @GetMapping({"/api/users/me", "/api/student/me", "/api/worker/me", "/api/admin/me"})
    public Result<UserVO> currentUser(@AuthenticationPrincipal UserVO user) {
        return Result.success(user);
    }
}
