package com.campus.repair.controller;

import com.campus.repair.common.*;
import com.campus.repair.dto.*;
import com.campus.repair.service.RepairOrderService;
import com.campus.repair.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
public class RepairOrderController {
    private final RepairOrderService service;

    @PostMapping("/api/student/orders")
    public Result<OrderVO> create(@AuthenticationPrincipal UserVO user, @Valid @RequestBody CreateOrderRequest input) {
        return Result.success(service.create(user,input));
    }
    @GetMapping({"/api/student/orders","/api/worker/orders","/api/admin/orders"})
    public Result<PageResult<OrderVO>> list(@AuthenticationPrincipal UserVO user, @Valid @ModelAttribute OrderQuery query) {
        return Result.success(service.list(user,query));
    }
    @GetMapping("/api/orders/{id}")
    public Result<OrderDetailVO> detail(@AuthenticationPrincipal UserVO user, @PathVariable @Positive long id) {
        return Result.success(service.detail(user,id));
    }
    @GetMapping("/api/catalog")
    public Result<Map<String,Object>> catalog() { return Result.success(service.catalog()); }
    @GetMapping("/api/admin/workers")
    public Result<List<Map<String,Object>>> workers(@AuthenticationPrincipal UserVO user) { return Result.success(service.availableWorkers(user)); }
    @GetMapping({"/api/student/summary","/api/worker/summary"})
    public Result<Map<String,Long>> summary(@AuthenticationPrincipal UserVO user) { return Result.success(service.summary(user)); }
    @PutMapping("/api/admin/orders/{id}/audit")
    public Result<Void> audit(@AuthenticationPrincipal UserVO user, @PathVariable @Positive long id) {
        service.audit(user,id); return Result.success(null);
    }
    @PutMapping("/api/admin/orders/{id}/assign")
    public Result<Void> assign(@AuthenticationPrincipal UserVO user, @PathVariable @Positive long id, @Valid @RequestBody AssignOrderRequest input) {
        service.assign(user,id,input.workerId()); return Result.success(null);
    }
    @PutMapping("/api/worker/orders/{id}/accept")
    public Result<Void> accept(@AuthenticationPrincipal UserVO user, @PathVariable @Positive long id) {
        service.accept(user,id); return Result.success(null);
    }
    @PutMapping("/api/worker/orders/{id}/start")
    public Result<Void> start(@AuthenticationPrincipal UserVO user, @PathVariable @Positive long id) {
        service.start(user,id); return Result.success(null);
    }
    @PostMapping("/api/worker/repair-record")
    public Result<Void> record(@AuthenticationPrincipal UserVO user, @Valid @RequestBody RepairRecordRequest input) {
        service.record(user,input); return Result.success(null);
    }
    @PutMapping("/api/worker/orders/{id}/finish")
    public Result<Void> finish(@AuthenticationPrincipal UserVO user, @PathVariable @Positive long id) {
        service.finish(user,id); return Result.success(null);
    }
    @PutMapping("/api/student/orders/{id}/confirm")
    public Result<Void> confirm(@AuthenticationPrincipal UserVO user, @PathVariable @Positive long id) {
        service.confirm(user,id); return Result.success(null);
    }
    @PostMapping("/api/student/evaluation")
    public Result<Void> evaluate(@AuthenticationPrincipal UserVO user, @Valid @RequestBody EvaluationRequest input) {
        service.evaluate(user,input); return Result.success(null);
    }
}
