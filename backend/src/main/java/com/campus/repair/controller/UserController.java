package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.vo.UserVO;
import com.campus.repair.service.UserService;
import com.campus.repair.dto.ChangePasswordRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    private final UserService users;
    public UserController(UserService users){this.users=users;}
    @GetMapping({"/api/users/me", "/api/student/me", "/api/worker/me", "/api/admin/me"})
    public Result<UserVO> currentUser(@AuthenticationPrincipal UserVO user) {
        return Result.success(user);
    }
    @PutMapping("/api/users/me/password")
    public Result<Void> password(@AuthenticationPrincipal UserVO user,@Valid @RequestBody ChangePasswordRequest input){
        users.changePassword(user.id(),input);return Result.success(null);
    }
}
