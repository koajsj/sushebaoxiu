package com.campus.repair.service;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.mapper.DatabaseProbeMapper;
import com.campus.repair.vo.HealthVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private static final Logger LOG = LoggerFactory.getLogger(HealthService.class);
    private final DatabaseProbeMapper mapper;

    public HealthService(DatabaseProbeMapper mapper) {
        this.mapper = mapper;
    }

    public HealthVO check() {
        try {
            if (mapper.checkConnection() != 1) {
                throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
            }
            return new HealthVO("UP", "UP");
        } catch (DataAccessException exception) {
            LOG.warn("Database probe failed ({})", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
        }
    }
}
