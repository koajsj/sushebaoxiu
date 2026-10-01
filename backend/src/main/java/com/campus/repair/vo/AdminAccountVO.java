package com.campus.repair.vo;

import com.campus.repair.security.UserRole;
import java.math.BigDecimal;

public record AdminAccountVO(Long id,String username,String realName,String phone,UserRole role,Integer status,
        String studentNo,String college,String className,Long buildingId,String roomNo,
        Long workerId,String skillType,Integer workerStatus,BigDecimal longitude,BigDecimal latitude) {}
