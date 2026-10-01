package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.service.NotificationService;
import com.campus.repair.vo.UserVO;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@lombok.RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;
    @GetMapping public Result<Map<String,Object>> list(@AuthenticationPrincipal UserVO user,
            @RequestParam(defaultValue="1") @Min(1) long page,@RequestParam(defaultValue="20") @Min(1) @Max(100) long size,
            @RequestParam(defaultValue="false") boolean unread){return Result.success(service.list(user,page,size,unread));}
    @PutMapping("/{id}/read") public Result<Void> read(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long id){service.markRead(user,id);return Result.success(null);}
    @PutMapping("/read-all") public Result<Void> readAll(@AuthenticationPrincipal UserVO user,@RequestParam @Positive long throughId){service.markAllRead(user,throughId);return Result.success(null);}
}
