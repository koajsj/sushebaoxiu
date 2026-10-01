package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.dto.DispatchRequest;
import com.campus.repair.service.DispatchService;
import com.campus.repair.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
public class DispatchController {
    private final DispatchService service;
    @GetMapping("/api/admin/dispatch/recommend/{orderId}")
    public Result<List<WorkerRecommendationVO>> recommend(@AuthenticationPrincipal UserVO user, @PathVariable @Positive long orderId) {
        return Result.success(service.recommend(user,orderId));
    }
    @PostMapping("/api/admin/dispatch/recommend/{orderId}")
    public Result<List<WorkerRecommendationVO>> generate(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long orderId,
            @RequestParam(defaultValue="false") boolean refresh) {
        return Result.success(service.generate(user,orderId,refresh));
    }
    @PostMapping("/api/admin/dispatch")
    public Result<WorkerRecommendationVO> confirm(@AuthenticationPrincipal UserVO user, @Valid @RequestBody DispatchRequest input) {
        return Result.success(service.confirm(user,input));
    }
}
