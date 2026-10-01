package com.campus.repair.controller;

import com.campus.repair.common.*;
import com.campus.repair.dto.*;
import com.campus.repair.security.UserRole;
import com.campus.repair.service.AdminManagementService;
import com.campus.repair.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/manage")
@lombok.RequiredArgsConstructor
public class AdminManagementController {
    private final AdminManagementService service;
    @GetMapping("/users")
    public Result<PageResult<AdminAccountVO>> users(@AuthenticationPrincipal UserVO actor,@RequestParam(required=false) UserRole role,
            @RequestParam(defaultValue="1") @Min(1) long page,@RequestParam(defaultValue="20") @Min(1) @Max(100) long size){
        return Result.success(service.accounts(actor,role,page,size));
    }
    @PostMapping("/users")
    public Result<Long> create(@AuthenticationPrincipal UserVO actor,@Valid @RequestBody AccountCreateRequest input){return Result.success(service.create(actor,input));}
    @PutMapping("/users/{id}")
    public Result<Void> update(@AuthenticationPrincipal UserVO actor,@PathVariable @Positive long id,@Valid @RequestBody AccountUpdateRequest input){
        service.update(actor,id,input);return Result.success(null);
    }
    @PutMapping("/users/{id}/status")
    public Result<Void> status(@AuthenticationPrincipal UserVO actor,@PathVariable @Positive long id,@Valid @RequestBody StatusRequest input){
        service.setStatus(actor,id,input.status());return Result.success(null);
    }
    @PutMapping("/workers/{id}/status")
    public Result<Void> workerStatus(@AuthenticationPrincipal UserVO actor,@PathVariable @Positive long id,@Valid @RequestBody StatusRequest input){
        service.setWorkerStatus(actor,id,input.status());return Result.success(null);
    }
    @PostMapping("/buildings")
    public Result<Long> createBuilding(@AuthenticationPrincipal UserVO actor,@Valid @RequestBody BuildingRequest input){return Result.success(service.saveBuilding(actor,null,input));}
    @PutMapping("/buildings/{id}")
    public Result<Long> updateBuilding(@AuthenticationPrincipal UserVO actor,@PathVariable @Positive long id,@Valid @RequestBody BuildingRequest input){return Result.success(service.saveBuilding(actor,id,input));}
    @PostMapping("/types")
    public Result<Long> createType(@AuthenticationPrincipal UserVO actor,@Valid @RequestBody RepairTypeRequest input){return Result.success(service.saveType(actor,null,input));}
    @PutMapping("/types/{id}")
    public Result<Long> updateType(@AuthenticationPrincipal UserVO actor,@PathVariable @Positive long id,@Valid @RequestBody RepairTypeRequest input){return Result.success(service.saveType(actor,id,input));}
}
