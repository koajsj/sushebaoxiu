package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.dto.MessageRequest;
import com.campus.repair.service.OrderMessageService;
import com.campus.repair.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/{orderId}/messages")
@lombok.RequiredArgsConstructor
public class OrderMessageController {
    private final OrderMessageService service;
    @GetMapping public Result<List<MessageVO>> list(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long orderId){return Result.success(service.list(user,orderId));}
    @PostMapping public Result<MessageVO> send(@AuthenticationPrincipal UserVO user,@PathVariable @Positive long orderId,@Valid @RequestBody MessageRequest input){return Result.success(service.send(user,orderId,input));}
}
