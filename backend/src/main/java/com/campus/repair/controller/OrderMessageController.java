package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.dto.MessageRequest;
import com.campus.repair.service.OrderMessageService;
import com.campus.repair.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/{orderId}/messages")
@lombok.RequiredArgsConstructor
public class OrderMessageController {
    private final OrderMessageService service;
    @GetMapping public Result<List<MessageVO>> list(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long orderId,
            @RequestParam(required=false) @Positive Long beforeId,@RequestParam(required=false) @Positive Long afterId,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int size){return Result.success(service.list(user,orderId,beforeId,afterId,size));}
    @GetMapping("/context") public Result<OrderMessageService.Context> context(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long orderId){return Result.success(service.context(user,orderId));}
    @PostMapping public Result<MessageVO> send(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long orderId,@Valid @RequestBody MessageRequest input){return Result.success(service.send(user,orderId,input));}
}
