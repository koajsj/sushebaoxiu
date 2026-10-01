package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.dto.*;
import com.campus.repair.service.OrderWorkflowService;
import com.campus.repair.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.RequiredArgsConstructor
public class OrderWorkflowController {
    private final OrderWorkflowService service;
    @PutMapping("/api/admin/orders/{id}/recall")
    public Result<Void> recall(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long id,@Valid @RequestBody RecallRequest input) {
        service.recall(user,id,input);return Result.success(null);
    }
    @PutMapping("/api/admin/orders/{id}/reject")
    public Result<Void> reject(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long id,@Valid @RequestBody ReasonRequest input) {
        service.rejectAudit(user,id,input.reason());return Result.success(null);
    }
    @PutMapping("/api/student/orders/{id}/resubmit")
    public Result<Void> resubmit(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long id,@Valid @RequestBody CreateOrderRequest input) {
        service.resubmit(user,id,input);return Result.success(null);
    }
    @PutMapping("/api/worker/orders/{id}/reject")
    public Result<Void> refuse(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long id,@Valid @RequestBody ReasonRequest input) {
        service.refuse(user,id,input.reason());return Result.success(null);
    }
    @PutMapping("/api/student/orders/{id}/acceptance-fail")
    public Result<Void> fail(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long id,@Valid @RequestBody ReasonRequest input) {
        service.failAcceptance(user,id,input.reason());return Result.success(null);
    }
    @PutMapping("/api/admin/orders/{id}/rework")
    public Result<Void> rework(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long id,@Valid @RequestBody ReworkRequest input) {
        service.rework(user,id,input.mode());return Result.success(null);
    }
    @PutMapping("/api/worker/orders/{id}/appointment")
    public Result<Void> propose(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long id,@Valid @RequestBody AppointmentRequest input) {
        service.propose(user,id,input);return Result.success(null);
    }
    @PutMapping("/api/student/orders/{id}/appointment")
    public Result<Void> respond(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long id,@Valid @RequestBody AppointmentResponse input) {
        service.respond(user,id,input);return Result.success(null);
    }
}
