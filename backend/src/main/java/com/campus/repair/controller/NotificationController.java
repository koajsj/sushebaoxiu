package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.service.NotificationService;
import com.campus.repair.vo.UserVO;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@lombok.RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;
    @GetMapping public Result<Map<String,Object>> list(@AuthenticationPrincipal UserVO user){return Result.success(service.list(user));}
    @PutMapping("/{id}/read") public Result<Void> read(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long id){service.markRead(user,id);return Result.success(null);}
}
