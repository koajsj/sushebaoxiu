package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.service.StatisticsService;
import com.campus.repair.vo.UserVO;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/statistics")
@lombok.RequiredArgsConstructor
public class StatisticsController {
    private final StatisticsService service;
    @GetMapping("/overview") public Result<Map<String,Object>> overview(@AuthenticationPrincipal UserVO user) { return Result.success(service.overview(user)); }
    @GetMapping("/trend") public Result<List<Map<String,Object>>> trend(@AuthenticationPrincipal UserVO user) { return Result.success(service.trend(user)); }
    @GetMapping("/types") public Result<List<Map<String,Object>>> types(@AuthenticationPrincipal UserVO user) { return Result.success(service.types(user)); }
    @GetMapping("/workers") public Result<List<Map<String,Object>>> workers(@AuthenticationPrincipal UserVO user) { return Result.success(service.workers(user)); }
}
