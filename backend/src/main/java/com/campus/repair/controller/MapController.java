package com.campus.repair.controller;

import com.campus.repair.common.*;
import com.campus.repair.entity.BuildingEntity;
import com.campus.repair.service.MapService;
import com.campus.repair.vo.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
@RequestMapping("/api/map")
public class MapController {
    private final MapService service;
    @GetMapping("/orders")
    public Result<PageResult<MapOrderVO>> orders(@AuthenticationPrincipal UserVO user, @RequestParam(required=false) String status) {
        return Result.success(service.orders(user,status));
    }
    @GetMapping("/workers")
    public Result<List<MapWorkerVO>> workers(@AuthenticationPrincipal UserVO user) { return Result.success(service.workers(user)); }
    @GetMapping("/buildings")
    public Result<List<BuildingEntity>> buildings(@AuthenticationPrincipal UserVO user) { return Result.success(service.buildings(user)); }
}
